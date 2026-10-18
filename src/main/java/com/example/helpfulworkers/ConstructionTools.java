package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Tool-assisted clearing of approved site terrain; never tunnels outside the footprint. */
final class ConstructionTools {
    static boolean clear(ServerLevel level, Worker worker, SiteData.Site site, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (!SiteConstruction.terrain(state) || state.hasBlockEntity()
                || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0)
            throw new IllegalArgumentException("Protected " + state.getBlock().getName().getString()
                    + " at " + pos.toShortString());
        if (!site.instant) {
            Class<?> kind = state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? PickaxeItem.class
                    : state.is(BlockTags.LOGS) ? AxeItem.class
                    : state.is(BlockTags.MINEABLE_WITH_SHOVEL) && !SiteConstruction.soft(state) ? ShovelItem.class : null;
            if (kind != null && !equip(level, worker, state, kind)) {
                site.status = worker.status;
                return false;
            }
            if (!worker.approachWithin(pos, 4, 8)) return false;
            Block.dropResources(state, level, pos, null, worker, worker.getMainHandItem());
            if (kind != null) worker.getMainHandItem().hurtAndBreak(1, worker, EquipmentSlot.MAINHAND);
            worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        worker.setStatus("Clearing " + state.getBlock().getName().getString() + " at " + pos.toShortString());
        return true;
    }

    private static boolean suitable(ItemStack stack, BlockState state, Class<?> kind) {
        return !stack.isEmpty() && kind.isInstance(stack.getItem())
                && (!state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state));
    }

    private static boolean equip(ServerLevel level, Worker worker, BlockState state, Class<?> kind) {
        if (suitable(worker.getMainHandItem(), state, kind)) return true;
        if (take(worker, worker.bag, state, kind)) return true;
        Container supply = StorageOps.at(level, worker.supply);
        if (supply != null) {
            for (int i = 0; i < supply.getContainerSize(); i++) {
                if (!suitable(supply.getItem(i), state, kind)) continue;
                worker.setStatus("Fetching tool from supply chest");
                if (!worker.approach(worker.supply)) return false;
                return take(worker, supply, state, kind);
            }
        }
        String name = kind == PickaxeItem.class ? "pickaxe" : kind == AxeItem.class ? "axe" : "shovel";
        worker.setStatus("Needs suitable " + name + " for " + state.getBlock().getName().getString()
                + (worker.supply == null ? " — assign a supply chest" : " — stock my supply chest"));
        return false;
    }

    private static boolean take(Worker worker, Container source, BlockState state, Class<?> kind) {
        for (int i = 0; i < source.getContainerSize(); i++) {
            if (!suitable(source.getItem(i), state, kind)) continue;
            ItemStack held = worker.getMainHandItem();
            // A bag tool frees its own slot. Swap directly even when the bag is full.
            if (source == worker.bag && source.getItem(i).getCount() == 1) {
                ItemStack tool = source.removeItem(i, 1);
                source.setItem(i, held);
                worker.setItemSlot(EquipmentSlot.MAINHAND, tool);
            } else {
                if (!held.isEmpty() && StorageOps.capacity(worker.bag, held) < held.getCount()) {
                    worker.setStatus("Bag full — make room to swap construction tools");
                    return false;
                }
                ItemStack tool = source.removeItem(i, 1);
                if (!held.isEmpty()) worker.bag.addItem(held);
                worker.setItemSlot(EquipmentSlot.MAINHAND, tool);
            }
            source.setChanged();
            return true;
        }
        return false;
    }
}
