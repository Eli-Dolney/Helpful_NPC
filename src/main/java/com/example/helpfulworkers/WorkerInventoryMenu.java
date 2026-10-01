package com.example.helpfulworkers;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class WorkerInventoryMenu extends AbstractContainerMenu {
    private static final EquipmentSlot[] EQUIP_ORDER = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private final Worker worker;
    private final EquipmentContainer equipment;

    public WorkerInventoryMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, resolveWorker(playerInv.player, buf.readVarInt()));
    }

    public WorkerInventoryMenu(int id, Inventory playerInv, Worker worker) {
        super(HelpfulWorkers.WORKER_MENU.get(), id);
        this.worker = worker;
        this.equipment = new EquipmentContainer(worker);

        for (int i = 0; i < EQUIP_ORDER.length; i++) {
            final int index = i;
            int x = i < 4 ? 8 : 80;
            int y = i < 4 ? 18 + i * 18 : 36 + (i - 4) * 18;
            addSlot(new Slot(equipment, index, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    EquipmentSlot slot = EQUIP_ORDER[index];
                    if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                        return worker.getEquipmentSlotForItem(stack) == slot;
                    }
                    return true;
                }

                @Override
                public int getMaxStackSize() {
                    return EQUIP_ORDER[index].getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? 1 : 64;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(worker.bag, col + row * 9, 8 + col * 18, 90 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 154 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 212));
        }
    }

    private static Worker resolveWorker(Player player, int id) {
        if (player.level().getEntity(id) instanceof Worker found) return found;
        return new Worker(HelpfulWorkers.WORKER.get(), player.level());
    }

    public Worker worker() {
        return worker;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide) return true;
        return worker != null && worker.isAlive() && worker.owns(player) && player.distanceToSqr(worker) <= 64;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            WorkerSessions.closeViewer(serverPlayer);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack.copy();
        int workerSlots = 6 + 27;
        if (index < workerSlots) {
            if (!moveItemStackTo(stack, workerSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, 0, workerSlots, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    private static final class EquipmentContainer implements Container {
        private final Worker worker;

        EquipmentContainer(Worker worker) {
            this.worker = worker;
        }

        @Override public int getContainerSize() { return EQUIP_ORDER.length; }
        @Override public boolean isEmpty() {
            for (EquipmentSlot slot : EQUIP_ORDER) if (!worker.getItemBySlot(slot).isEmpty()) return false;
            return true;
        }
        @Override public ItemStack getItem(int index) { return worker.getItemBySlot(EQUIP_ORDER[index]); }
        @Override public ItemStack removeItem(int index, int count) {
            ItemStack stack = worker.getItemBySlot(EQUIP_ORDER[index]);
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack split = stack.split(count);
            worker.setItemSlot(EQUIP_ORDER[index], stack);
            setChanged();
            return split;
        }
        @Override public ItemStack removeItemNoUpdate(int index) {
            ItemStack stack = worker.getItemBySlot(EQUIP_ORDER[index]);
            worker.setItemSlot(EQUIP_ORDER[index], ItemStack.EMPTY);
            return stack;
        }
        @Override public void setItem(int index, ItemStack stack) {
            worker.setItemSlot(EQUIP_ORDER[index], stack);
            setChanged();
        }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() {
            for (EquipmentSlot slot : EQUIP_ORDER) worker.setItemSlot(slot, ItemStack.EMPTY);
        }
    }
}
