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
        int worldCount = WorkerRegistry.countAlive(level);
        if (worldCount >= WorkerConfig.maxPerWorld()) {
            player.displayClientMessage(Component.literal("This world already has " + worldCount
                + " workers (max " + WorkerConfig.maxPerWorld() + ")."), false);
            return InteractionResult.FAIL;
        }
        int owned = WorkerRegistry.countForOwner(level, player.getUUID());
        if (owned >= WorkerConfig.maxPerPlayer()) {
            player.displayClientMessage(Component.literal("You already own " + owned
                + " workers (max " + WorkerConfig.maxPerPlayer() + ")."), false);
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
        Worker worker = HelpfulWorkers.WORKER.get().create(level);
        if (worker == null) return InteractionResult.FAIL;
        worker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), 0);
        worker.setOwner(player.getUUID());
        worker.setPersistenceRequired();
        worker.setBaseName("Worker " + (level.random.nextInt(900) + 100));
        level.addFreshEntity(worker);
        WorkerRegistry.upsert(worker);
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Places a helpful worker you own."));
        tooltip.add(Component.literal("Limit: " + WorkerConfig.maxPerPlayer() + " per player, "
            + WorkerConfig.maxPerWorld() + " per world."));
    }
}
