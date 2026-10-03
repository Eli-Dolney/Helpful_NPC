package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.ChestBlock;

final class StorageOps {
    static Container at(ServerLevel l, BlockPos p) {
        if (p == null || !l.hasChunkAt(p)) return null;
        var state = l.getBlockState(p);
        if (state.getBlock() instanceof ChestBlock chest) {
            if (state.getValue(ChestBlock.TYPE)
                            != net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                    && !l.hasChunkAt(p.relative(ChestBlock.getConnectedDirection(state))))
                return null;
            return ChestBlock.getContainer(chest, state, l, p, true);
        }
        return l.getBlockEntity(p) instanceof Container c ? c : null;
    }

    static int capacity(Container c, ItemStack stack) {
        if (c == null || stack.isEmpty()) return 0;
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++)
            if (c.canPlaceItem(i, stack)) {
                ItemStack slot = c.getItem(i);
                int max = Math.min(c.getMaxStackSize(), stack.getMaxStackSize());
                if (slot.isEmpty()) n += max;
                else if (ItemStack.isSameItemSameComponents(slot, stack))
                    n += Math.max(0, max - slot.getCount());
            }
        return n;
    }

    static boolean hasMatching(Container c, ItemStack s) {
        for (int i = 0; i < c.getContainerSize(); i++)
            if (ItemStack.isSameItemSameComponents(c.getItem(i), s)
                    && c.getItem(i).getCount() < Math.min(c.getMaxStackSize(), s.getMaxStackSize()))
                return true;
        return false;
    }

    static int move(Container from, int slot, Container to, int max) {
        if (from == to) return 0;
        ItemStack source = from.getItem(slot);
        int remain = Math.min(Math.min(max, source.getCount()), capacity(to, source)),
                moved = remain;
        for (int pass = 0; pass < 2 && remain > 0; pass++)
            for (int i = 0; i < to.getContainerSize() && remain > 0; i++) {
                if (!to.canPlaceItem(i, source)) continue;
                ItemStack dest = to.getItem(i);
                int limit = Math.min(to.getMaxStackSize(), source.getMaxStackSize());
                if (pass == 0
                        && !dest.isEmpty()
                        && ItemStack.isSameItemSameComponents(dest, source)) {
                    int n = Math.min(remain, Math.max(0, limit - dest.getCount()));
                    dest.grow(n);
                    remain -= n;
                }
                if (pass == 1 && dest.isEmpty()) {
                    int n = Math.min(remain, limit);
                    to.setItem(i, source.copyWithCount(n));
                    remain -= n;
                }
            }
        moved -= remain;
        source.shrink(moved);
        if (source.isEmpty()) from.setItem(slot, ItemStack.EMPTY);
        if (moved > 0) {
            from.setChanged();
            to.setChanged();
        }
        return moved;
    }

    /** Shared export boundary for normal deposits and warehouse pickup. */
    static int exportable(Worker w, int slot) {
        ItemStack s = w.bag.getItem(slot);
        if (s.isEmpty()) return 0;
        Item item = s.getItem();
        if(w.level() instanceof ServerLevel level) for(var warehouse:SiteData.get(level).sites.values()) {
            var targets=warehouse.supplyTargets.get(w.getUUID());
            if(targets!=null && warehouse.owner.equals(w.owner) && warehouse.linkedWorkers.contains(w.getUUID())) {
                Integer keep=targets.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
                if(keep!=null && keep>0)return Math.min(s.getCount(),Math.max(0,total(w,item)-keep));
            }
        }
        if (item instanceof TieredItem
                || item instanceof ArmorItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof ShieldItem
                || item instanceof ShearsItem
                || item instanceof FishingRodItem
                || item instanceof RoleKitItem
                || item instanceof AssignmentClipboardItem
                || SiteBlocks.CORES.values().stream().anyMatch(i -> s.is(i.get()))) return 0;
        // Build inventories and recipe ingredients are intentional work supplies.
        if ("builder".equals(w.role)) return 0;
        if (w.level() instanceof ServerLevel l)
            for (String id : w.recipes) {
                var key = net.minecraft.resources.ResourceLocation.tryParse(id);
                if (key == null) continue;
                var recipe = l.getRecipeManager().byKey(key);
                if (recipe.isPresent())
                    for (var ing : recipe.get().value().getIngredients()) if (ing.test(s)) return 0;
            }
        if (s.is(net.minecraft.tags.ItemTags.SAPLINGS))
            return Math.min(s.getCount(), Math.max(0, total(w, item) - 16));
        if ("farmer".equals(w.role)
                && (s.is(Items.WHEAT_SEEDS)
                        || s.is(Items.BEETROOT_SEEDS)
                        || s.is(Items.CARROT)
                        || s.is(Items.POTATO)
                        || s.is(Items.BONE_MEAL)
                        || s.is(Items.NETHER_WART)))
            return Math.min(s.getCount(), Math.max(0, total(w, item) - 64));
        if ("rancher".equals(w.role)
                && (s.is(Items.WHEAT) || s.is(Items.WHEAT_SEEDS) || s.is(Items.BUCKET))) return 0;
        if ("smelter".equals(w.role)
                && (s.is(Items.COAL) || s.is(Items.CHARCOAL) || s.is(Items.LAVA_BUCKET))) return 0;
        if ("archer".equals(w.role) && s.is(net.minecraft.tags.ItemTags.ARROWS)) return 0;
        if (s.has(net.minecraft.core.component.DataComponents.FOOD))
            return Math.min(s.getCount(), Math.max(0, total(w, item) - 16));
        return s.getCount();
    }

    private static int total(Worker w, Item i) {
        int n = 0;
        for (int s = 0; s < w.bag.getContainerSize(); s++)
            if (w.bag.getItem(s).is(i)) n += w.bag.getItem(s).getCount();
        return n;
    }

    static int used(Worker w) {
        int n = 0;
        for (int i = 0; i < w.bag.getContainerSize(); i++) if (!w.bag.getItem(i).isEmpty()) n++;
        return n;
    }
}
