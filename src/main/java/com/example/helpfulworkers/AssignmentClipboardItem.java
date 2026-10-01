package com.example.helpfulworkers;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class AssignmentClipboardItem extends Item {
    public AssignmentClipboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        if (WorkerSessions.getClipboard(player) == null) {
            WorkerNetwork.sendRoster(player);
            return InteractionResult.CONSUME;
        }
        handleClick(player, context.getClickedPos(), context.getClickedFace());
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player.isShiftKeyDown() && player instanceof ServerPlayer serverPlayer
            && WorkerSessions.getClipboard(serverPlayer) != null) {
            WorkerSessions.clearClipboard(serverPlayer);
            say(player, "Assignment cancelled");
            return InteractionResultHolder.consume(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
            && WorkerSessions.getClipboard(serverPlayer) == null) {
            WorkerNetwork.sendRoster(serverPlayer);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    static void handleClick(ServerPlayer player, BlockPos clicked, Direction face) {
        WorkerSessions.ClipboardSession session = WorkerSessions.getClipboard(player);
        if (session == null || clicked == null) return;
        if (session.handledTick == player.tickCount && clicked.equals(session.handledPos)) return;
        session.handledTick = player.tickCount;
        session.handledPos = clicked.immutable();

        Worker worker = WorkerActions.findOwned(player, session.workerId);
        if (worker == null) {
            WorkerSessions.clearClipboard(player);
            say(player, "Worker unavailable; assignment cancelled");
            return;
        }
        ActionResult range = WorkerActions.requireNear(player, clicked);
        if (range != null) {
            say(player, range.text());
            return;
        }

        BlockPos target = session.kind == WorkerSessions.ClipboardKind.BUILD_ORIGIN && face != null
            ? clicked.relative(face) : clicked;
        Level level = player.level();
        BlockState state = level.getBlockState(clicked);
        ActionResult validate = validateTarget(worker, session.kind, clicked, state, level);
        if (!validate.success()) {
            say(player, validate.text());
            return;
        }

        if (session.kind == WorkerSessions.ClipboardKind.AREA) {
            if (session.pendingFirst == null) {
                session.pendingFirst = clicked.immutable();
                highlight(level, clicked);
                say(player, worker.getName().getString() + ": first corner " + clicked.toShortString()
                    + ". Click the opposite corner.");
                return;
            }
            session.pendingConfirm = clicked.immutable();
            highlight(level, session.pendingFirst);
            highlight(level, clicked);
            if ("terraform".equals(worker.mode) && "miner".equals(worker.role)) {
                session.awaitingDepth = true;
                WorkerNetwork.openDepthPicker(player, worker, session.pendingFirst, session.pendingConfirm);
                say(player, "Footprint marked. Choose how deep to dig.");
                return;
            }
            ActionResult result = apply(player, worker, session);
            BlockPos first = session.pendingFirst;
            WorkerSessions.clearClipboard(player);
            if (result.success()) {
                if ("builder".equals(worker.role) && session.villageBuild) {
                    ActionResult build = WorkerActions.startVillageOnPlot(player, worker);
                    say(player, build.text());
                } else if (!"builder".equals(worker.role)) {
                    worker.working = true;
                    worker.status = "Working";
                    say(player, result.text() + ". " + worker.getName().getString() + " started.");
                } else {
                    say(player, "Copy area marked from " + first.toShortString() + " to " + clicked.toShortString()
                        + ". Open the builder's Custom blueprints tab and choose Capture.");
                }
            } else {
                say(player, result.text());
            }
            return;
        }

        if (session.awaitingConfirm && target.equals(session.pendingConfirm)) {
            ActionResult result = apply(player, worker, session);
            WorkerSessions.clearClipboard(player);
            say(player, result.text());
            return;
        }
        session.pendingConfirm = target.immutable();
        session.awaitingConfirm = true;
        highlight(level, target);
        say(player, worker.getName().getString() + ": click " + target.toShortString() + " again to save this "
            + session.kind.name().toLowerCase() + ".");
    }

    private static ActionResult validateTarget(Worker worker, WorkerSessions.ClipboardKind kind, BlockPos clicked,
                                               BlockState state, Level level) {
        return switch (kind) {
            case BED -> state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                ? ActionResult.ok("ok") : ActionResult.fail("Look at a bed");
            case SUPPLY, OUTPUT -> level.getBlockEntity(clicked) instanceof net.minecraft.world.Container
                ? ActionResult.ok("ok") : ActionResult.fail("Look at a chest or other inventory");
            case TABLE -> state.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)
                ? ActionResult.ok("ok") : ActionResult.fail("Look at a crafting table");
            case FURNACE -> {
                var b = state.getBlock();
                yield (b instanceof net.minecraft.world.level.block.AbstractFurnaceBlock)
                    ? ActionResult.ok("ok") : ActionResult.fail("Look at a furnace, smoker, or blast furnace");
            }
            case PICKUP -> level.getBlockEntity(clicked) instanceof net.minecraft.world.Container
                ? ActionResult.ok("ok") : ActionResult.fail("Look at a chest or other inventory");
            case BUILD_ORIGIN -> worker.blueprintBlocks.isEmpty()
                ? ActionResult.fail("Capture or load a blueprint first") : ActionResult.ok("ok");
            case AREA -> ActionResult.ok("ok");
            default -> ActionResult.fail("Unknown assignment");
        };
    }

    private static ActionResult apply(ServerPlayer player, Worker worker, WorkerSessions.ClipboardSession session) {
        return switch (session.kind) {
            case AREA -> WorkerActions.setArea(player, worker, session.pendingFirst, session.pendingConfirm);
            case BED -> WorkerActions.setBed(player, worker, session.pendingConfirm);
            case SUPPLY -> WorkerActions.setSupply(player, worker, session.pendingConfirm);
            case OUTPUT -> WorkerActions.setOutput(player, worker, session.pendingConfirm);
            case TABLE -> WorkerActions.setTable(player, worker, session.pendingConfirm);
            case BUILD_ORIGIN -> WorkerActions.setBuildOrigin(player, worker, session.pendingConfirm);
            case FURNACE -> WorkerActions.addFurnace(player, worker, session.pendingConfirm);
            case PICKUP -> WorkerActions.addPickup(player, worker, session.pendingConfirm);
            default -> ActionResult.fail("Unknown assignment");
        };
    }

    private static void highlight(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                16, 0.25, 0.4, 0.25, 0.02);
        }
    }

    private static void say(Player player, String text) {
        player.displayClientMessage(Component.literal(text), false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click: monitor and recall all your workers."));
        tooltip.add(Component.literal("During an assignment, click two corners. Sneak-right-click to cancel."));
    }
}
