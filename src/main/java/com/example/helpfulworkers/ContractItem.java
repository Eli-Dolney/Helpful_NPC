package com.example.helpfulworkers;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public final class ContractItem extends Item {
    public ContractItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        if (context.getLevel().getBlockState(context.getClickedPos()).is(HelpfulWorkers.TOWN_HALL.get())) {
            return InteractionResult.PASS; // the town hall recruits beside itself
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
        if (recruit(level, player, pos, player.getYRot()) == null) return InteractionResult.FAIL;
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    /**
     * Spawn a worker owned by player at pos, respecting the per-player and per-world caps (the
     * player is told why when a cap is hit). Returns the worker, or null when nothing was spawned.
     * The caller uses up the contract.
     */
    static Worker recruit(ServerLevel level, ServerPlayer player, BlockPos pos, float yaw) {
        int worldCount = WorkerRegistry.countAlive(level);
        if (worldCount >= WorkerConfig.maxPerWorld()) {
            player.displayClientMessage(Component.literal("This world already has " + worldCount
                + " workers (max " + WorkerConfig.maxPerWorld() + ")."), false);
            return null;
        }
        int owned = WorkerRegistry.countForOwner(level, player.getUUID());
        if (owned >= WorkerConfig.maxPerPlayer()) {
            player.displayClientMessage(Component.literal("You already own " + owned
                + " workers (max " + WorkerConfig.maxPerPlayer() + ")."), false);
            return null;
        }
        Worker worker = HelpfulWorkers.WORKER.get().create(level);
        if (worker == null) return null;
        worker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        worker.setOwner(player.getUUID());
        worker.setPersistenceRequired();
        worker.setBaseName("Worker " + (level.random.nextInt(900) + 100));
        worker.pickTraitIfMissing();
        level.addFreshEntity(worker);
        WorkerRegistry.upsert(worker);
        return worker;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Places a helpful worker you own."));
        tooltip.add(Component.literal("Limit: " + WorkerConfig.maxPerPlayer() + " per player, "
            + WorkerConfig.maxPerWorld() + " per world."));
    }
}
