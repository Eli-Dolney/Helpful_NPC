package com.example.helpfulworkers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;

/** Pull best gear from bag / supply into equipment slots. */
final class WorkerEquip {
    private WorkerEquip() {}

    static void tick(ServerLevel level, Worker worker) {
        if (worker.tickCount % 600 != 0 && !worker.equipDirty) return;
        worker.equipDirty = false;
        equipArmor(level, worker);
        if ("knight".equals(worker.role)) {
            equipBest(level, worker, EquipmentSlot.MAINHAND, s -> s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem);
            equipBest(level, worker, EquipmentSlot.OFFHAND, s -> s.getItem() instanceof ShieldItem);
        } else if ("archer".equals(worker.role)) {
            equipBest(level, worker, EquipmentSlot.MAINHAND, s -> s.getItem() instanceof BowItem);
            // arrows stay in bag for performRangedAttack
        }
    }

    private static void equipArmor(ServerLevel level, Worker worker) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        }) {
            ItemStack current = worker.getItemBySlot(slot);
            int currentScore = armorScore(current, slot);
            ItemStack best = ItemStack.EMPTY;
            int bestScore = currentScore;
            Container supply = supplyOf(level, worker);
            for (Container inv : new Container[]{worker.bag, supply}) {
                if (inv == null) continue;
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack stack = inv.getItem(i);
                    if (!(stack.getItem() instanceof ArmorItem armor)) continue;
                    if (armor.getEquipmentSlot() != slot) continue;
                    int score = armor.getDefense() * 10 + (int) armor.getToughness();
                    if (score > bestScore) {
                        bestScore = score;
                        best = stack;
                    }
                }
            }
            if (!best.isEmpty() && bestScore > currentScore) {
                takeAndEquip(level, worker, slot, best, current);
            }
        }
    }

    private interface Pred { boolean test(ItemStack s); }

    private static void equipBest(ServerLevel level, Worker worker, EquipmentSlot slot, Pred pred) {
        ItemStack current = worker.getItemBySlot(slot);
        int currentScore = weaponScore(current);
        ItemStack best = ItemStack.EMPTY;
        int bestScore = currentScore;
        Container supply = supplyOf(level, worker);
        for (Container inv : new Container[]{worker.bag, supply}) {
            if (inv == null) continue;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!pred.test(stack)) continue;
                int score = weaponScore(stack);
                if (score > bestScore || (best.isEmpty() && current.isEmpty())) {
                    bestScore = Math.max(score, 1);
                    best = stack;
                }
            }
        }
        if (!best.isEmpty() && (current.isEmpty() || bestScore > currentScore)) {
            takeAndEquip(level, worker, slot, best, current);
        }
    }

    private static void takeAndEquip(ServerLevel level, Worker worker, EquipmentSlot slot,
                                    ItemStack fromStack, ItemStack current) {
        ItemStack taken = fromStack.split(1);
        if (!current.isEmpty()) worker.collect(current);
        worker.setItemSlot(slot, taken);
        if (worker.supply != null && level.getBlockEntity(worker.supply) instanceof Container) {
            ((Container) level.getBlockEntity(worker.supply)).setChanged();
        }
    }

    private static int armorScore(ItemStack stack, EquipmentSlot slot) {
        if (!(stack.getItem() instanceof ArmorItem armor)) return -1;
        if (armor.getEquipmentSlot() != slot) return -1;
        return armor.getDefense() * 10 + (int) armor.getToughness();
    }

    private static int weaponScore(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.getItem() instanceof SwordItem sword) return (int) (sword.getTier().getAttackDamageBonus() * 10);
        if (stack.getItem() instanceof AxeItem) return 50;
        if (stack.getItem() instanceof BowItem) return 40;
        if (stack.getItem() instanceof ShieldItem || stack.is(Items.SHIELD)) return 30;
        return 1;
    }

    private static Container supplyOf(ServerLevel level, Worker worker) {
        if (worker.supply == null || !level.isLoaded(worker.supply)) return null;
        return level.getBlockEntity(worker.supply) instanceof Container c ? c : null;
    }
}
