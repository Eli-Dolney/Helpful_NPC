package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Chests shared by several workers. Workers announce what they are walking to a chest to fetch
 * (a claim, which expires on its own), so a second worker does not set off for the same last few
 * items; and inserts merge into existing stacks before using empty slots.
 */
final class WorkerStorage {
    private WorkerStorage() {}

    /** Claims expire after this long even if the trip never finished (worker died, chunk unloaded). */
    static final long CLAIM_TICKS = 1200;

    private record Claim(UUID worker, String key, int count, long until) {}

    /** Per chest ("dimension|packed pos"), the open claims on it. Server-only, not saved. */
    private static final Map<String, List<Claim>> CLAIMS = new HashMap<>();

    private static String chestKey(ServerLevel level, BlockPos chest) {
        return level.dimension().location() + "|" + chest.asLong();
    }

    /** Record that worker is on its way to take {@code count} items matching key from chest (replaces its earlier claim). */
    static synchronized void claim(ServerLevel level, BlockPos chest, Worker worker, String key, int count) {
        List<Claim> list = CLAIMS.computeIfAbsent(chestKey(level, chest), k -> new ArrayList<>());
        list.removeIf(c -> c.worker().equals(worker.getUUID()) && c.key().equals(key));
        list.add(new Claim(worker.getUUID(), key, count, level.getGameTime() + CLAIM_TICKS));
    }

    static synchronized void release(ServerLevel level, BlockPos chest, Worker worker, String key) {
        List<Claim> list = CLAIMS.get(chestKey(level, chest));
        if (list == null) return;
        list.removeIf(c -> c.worker().equals(worker.getUUID()) && c.key().equals(key));
        if (list.isEmpty()) CLAIMS.remove(chestKey(level, chest));
    }

    /** Items matching key that other workers have claimed from chest and not picked up yet. */
    static synchronized int claimedByOthers(ServerLevel level, BlockPos chest, Worker worker, String key) {
        List<Claim> list = CLAIMS.get(chestKey(level, chest));
        if (list == null) return 0;
        long now = level.getGameTime();
        int total = 0;
        for (Iterator<Claim> it = list.iterator(); it.hasNext(); ) {
            Claim c = it.next();
            if (c.until() <= now) { it.remove(); continue; }
            if (!c.worker().equals(worker.getUUID()) && c.key().equals(key)) total += c.count();
        }
        return total;
    }

    /** Forget every claim (tests). */
    static synchronized void clearClaims() {
        CLAIMS.clear();
    }

    static int count(Container chest, Predicate<ItemStack> match) {
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (!s.isEmpty() && match.test(s)) n += s.getCount();
        }
        return n;
    }

    /**
     * Put {@code moving} into chest: top up matching stacks first, then empty slots. {@code moving}
     * shrinks by what fitted; returns how many items went in.
     */
    static int insert(Container chest, ItemStack moving) {
        int before = moving.getCount();
        for (int j = 0; j < chest.getContainerSize() && !moving.isEmpty(); j++) {
            ItemStack slot = chest.getItem(j);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, moving) && slot.getCount() < slot.getMaxStackSize()
                && chest.canPlaceItem(j, moving)) {
                int n = Math.min(moving.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(n);
                moving.shrink(n);
            }
        }
        for (int j = 0; j < chest.getContainerSize() && !moving.isEmpty(); j++) {
            if (chest.getItem(j).isEmpty() && chest.canPlaceItem(j, moving)) {
                int n = Math.min(moving.getCount(), moving.getMaxStackSize());
                chest.setItem(j, moving.split(n));
            }
        }
        int moved = before - moving.getCount();
        if (moved > 0) chest.setChanged();
        return moved;
    }
}
