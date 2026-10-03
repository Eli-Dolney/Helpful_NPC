package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.*;

final class WarehouseJobs {
    static final String[] CATEGORIES = {
        "ores_minerals", "wood", "food_farming", "stone_building", "equipment", "miscellaneous"
    };

    record Task(
            UUID warehouse,
            UUID sourceWorker,
            BlockPos source,
            int slot,
            BlockPos dest,
            ItemStack item,
            long expires) {}

    record Lock(String dimension, UUID worker, BlockPos pos, int slot) {}

    static final Map<UUID, Task> TASKS = new HashMap<>();
    static final Map<UUID, int[]> SCAN_CURSORS = new HashMap<>();
    static final Map<Lock, UUID> LOCKS = new HashMap<>();

    static BlockPos canonical(ServerLevel l, BlockPos p) {
        var b = l.getBlockState(p);
        if (b.getBlock() instanceof ChestBlock && b.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = p.relative(ChestBlock.getConnectedDirection(b));
            return p.compareTo(other) < 0 ? p : other;
        }
        return p.immutable();
    }

    static void initialize(ServerLevel l, SiteData.Site s) {
        if(!s.template.isEmpty()) {
            Set<BlockPos> seen=new HashSet<>();
            for(var piece:SiteTemplates.pieces(s)) {
                var pos=s.at(piece.x(),piece.y(),piece.z());
                if(!(piece.state().getBlock() instanceof ChestBlock) && !piece.state().is(net.minecraft.world.level.block.Blocks.BARREL))continue;
                pos=canonical(l,pos); if(!seen.add(pos))continue;
                var st=new SiteData.Storage(pos);
                if(pos.equals(canonical(l,s.supply())))st.mode="incoming";
                else if(pos.equals(canonical(l,s.output())))st.mode="overflow";
                else st.filter=CATEGORIES[s.storage.size()%CATEGORIES.length];
                s.storage.add(st);
            }
            return;
        }
        for (int z : new int[] {3, 7, 11, 15})
            for (int x = 1; x <= 22; x += 3) {
                var st = new SiteData.Storage(canonical(l, s.at(x, 1, z)));
                st.filter = CATEGORIES[(s.storage.size()) % CATEGORIES.length];
                s.storage.add(st);
            }
        var in = new SiteData.Storage(s.at(11, 1, 10));
        in.mode = "incoming";
        s.storage.add(in);
        var overflow = new SiteData.Storage(s.output());
        overflow.mode = "overflow";
        s.storage.add(overflow);
    }

    static void release(UUID worker) {
        TASKS.remove(worker);
        WarehouseSupplies.release(worker);
        LOCKS.entrySet().removeIf(e -> e.getValue().equals(worker));
    }

    static void clear() {
        TASKS.clear();
        LOCKS.clear();
        SCAN_CURSORS.clear();
        WarehouseSupplies.REQUESTS.clear(); WarehouseSupplies.CONNECTED.clear(); WarehouseSupplies.CURSORS.clear(); WarehouseSupplies.STOCK_CURSOR.clear();
    }

    static int rank(SiteData.Storage st, ItemStack item) {
        if (st.mode.equals("incoming")) return -1;
        if (st.mode.equals("overflow")) return 2;
        if (st.filter.startsWith("item:"))
            return net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(item.getItem())
                            .toString()
                            .equals(st.filter.substring(5))
                    ? 0
                    : -1;
        if (st.filter.equals("miscellaneous")) {
            if (item.is(
                    TagKey.create(
                            Registries.ITEM,
                            ResourceLocation.fromNamespaceAndPath(
                                    HelpfulWorkers.ID, "warehouse/miscellaneous")))) return 1;
            for (String c : CATEGORIES)
                if (!c.equals("miscellaneous")
                        && item.is(
                                TagKey.create(
                                        Registries.ITEM,
                                        ResourceLocation.fromNamespaceAndPath(
                                                HelpfulWorkers.ID, "warehouse/" + c)))) return -1;
            return 1;
        }
        return item.is(
                        TagKey.create(
                                Registries.ITEM,
                                ResourceLocation.fromNamespaceAndPath(
                                        HelpfulWorkers.ID, "warehouse/" + st.filter)))
                ? 1
                : -1;
    }

    static SiteData.Storage destination(
            ServerLevel l, Worker w, SiteData.Site s, ItemStack stack, BlockPos exclude) {
        return s.storage.stream()
                .filter(
                        st ->
                                !st.pos.equals(exclude)
                                        && rank(st, stack) >= 0
                                        && StorageOps.capacity(StorageOps.at(l, st.pos), stack) > 0
                                        && !w.isUnreachable(st.pos))
                .min(
                        Comparator.<SiteData.Storage>comparingInt(st -> rank(st, stack))
                                .thenComparingInt(
                                        st ->
                                                StorageOps.hasMatching(
                                                                StorageOps.at(l, st.pos), stack)
                                                        ? 0
                                                        : 1)
                                .thenComparingDouble(st -> w.blockPosition().distSqr(st.pos))
                                .thenComparing(st -> st.pos))
                .orElse(null);
    }

    static void tick(ServerLevel l, Worker w, SiteData.Site s) {
        if (!s.couriers.contains(w.getUUID()) || !"courier".equals(w.role)) {
            release(w.getUUID());
            w.siteId = null;
            w.working = false;
            return;
        }
        if (s.paused || !s.active()) {
            release(w.getUUID());
            w.setStatus("Warehouse paused");
            return;
        }
        if (!l.hasChunkAt(s.core())) {
            release(w.getUUID());
            w.setStatus("Warehouse chunk unloaded");
            return;
        }
        if (!l.getBlockState(s.core()).is(SiteBlocks.CORE.get())) {
            release(w.getUUID());
            w.setStatus("Warehouse core missing");
            return;
        }
        cleanup(l);
        WarehouseSupplies.connect(l,s);
        if (WarehouseSupplies.deliver(l,w,s)) return;
        for (int slot = 0; slot < w.bag.getContainerSize(); slot++) {
            ItemStack stack = w.bag.getItem(slot);
            if (stack.isEmpty()) continue;
            var dest = destination(l, w, s, stack, null);
            if (dest == null) {
                w.setStatus("No matching warehouse space — carrying items");
                release(w.getUUID());
                return;
            }
            if (!w.approach(dest.pos)) {
                if (!w.working) release(w.getUUID());
                return;
            }
            StorageOps.move(w.bag, slot, StorageOps.at(l, dest.pos), stack.getMaxStackSize());
            w.setStatus("Sorted into " + dest.pos.toShortString());
            return;
        }
        if(WarehouseSupplies.REQUESTS.containsKey(w.getUUID()) && WarehouseSupplies.collect(l,w,s)) return;
        Task task = TASKS.get(w.getUUID());
        if (task != null) {
            perform(l, w, s, task);
            return;
        }
        int budget = WorkerConfig.scanBudget();
        List<Source> sources = new ArrayList<>();
        for (UUID id : s.linkedWorkers) {
            if (l.getEntity(id) instanceof Worker source
                    && source.isAlive()
                    && s.owner.equals(source.owner)
                    && !source.getUUID().equals(w.getUUID())
                    && !s.couriers.contains(id)
                    && (source.needsUnload || StorageOps.used(source) >= 24))
                sources.add(new Source(source.getUUID(), null, source.bag, false));
        }
        int urgentBudget = sources.isEmpty() ? 0 : Math.max(1, budget / 2);
        if (scan(l, w, s, sources, urgentBudget, 0)) return;
        if (WarehouseSupplies.collect(l,w,s)) return;
        int remaining = budget - urgentBudget;
        sources.clear();
        for (UUID id : s.linkedSites) {
            var source = SiteData.get(l).sites.get(id);
            if (source != null
                    && source.active()
                    && source.owner.equals(s.owner)
                    && !"warehouse".equals(source.type)) {
                Container c = StorageOps.at(l, source.output());
                if (c != null) sources.add(new Source(null, source.output(), c, false));
            }
        }
        for (var entry : WorkerRegistry.forOwner(l,s.owner))
            if(entry.alive() && entry.dimension().equals(l.dimension()) && s.linkedWorkers.contains(entry.entityId()) && entry.output()!=null
                    && !entry.output().equals(entry.supply()) && sources.stream().noneMatch(src->entry.output().equals(src.pos))) {
                Container c=StorageOps.at(l,entry.output()); if(c!=null) sources.add(new Source(null,entry.output(),c,false));
            }
        for (var st : s.storage)
            if (st.mode.equals("incoming")) {
                Container c = StorageOps.at(l, st.pos);
                if (c != null) sources.add(new Source(null, st.pos, c, false));
            }
        int collectionBudget = sources.isEmpty() ? 0 : Math.max(1, remaining * 3 / 4);
        if (scan(l, w, s, sources, collectionBudget, 1)) return;
        sources.clear();
        for (var st : s.storage)
            if (st.mode.equals("sorted") || st.mode.equals("overflow")) {
                Container c = StorageOps.at(l, st.pos);
                if (c != null) sources.add(new Source(null, st.pos, c, true));
            }
        if (scan(l, w, s, sources, remaining - collectionBudget, 2)) return;
        w.setStatus("Warehouse route clear / no matching space");
    }

    record Source(UUID worker, BlockPos pos, Container container, boolean correction) {}

    private static boolean scan(
            ServerLevel l,
            Worker w,
            SiteData.Site s,
            List<Source> sources,
            int budget,
            int priority) {
        if (sources.isEmpty()) return false;
        int total = sources.stream().mapToInt(a -> a.container.getContainerSize()).sum();
        if (total == 0) return false;
        for (int n = 0; n < Math.min(budget, total); n++) {
            int[] cursors = SCAN_CURSORS.computeIfAbsent(w.getUUID(), id -> new int[3]);
            int index = Math.floorMod(cursors[priority]++, total);
            Source src = null;
            int slot = 0;
            for (Source candidate : sources) {
                if (index < candidate.container.getContainerSize()) {
                    src = candidate;
                    slot = index;
                    break;
                }
                index -= candidate.container.getContainerSize();
            }
            if (src == null) continue;
            ItemStack stack = src.container.getItem(slot);
            if (stack.isEmpty()) continue;
            if (src.worker != null
                    && (!(l.getEntity(src.worker) instanceof Worker worker)
                            || StorageOps.exportable(worker, slot) <= 0)) continue;
            var dest = destination(l, w, s, stack, src.pos);
            if (dest == null) continue;
            if (src.correction) {
                BlockPos sourcePos = src.pos;
                var current =
                        s.storage.stream()
                                .filter(st -> st.pos.equals(sourcePos))
                                .findFirst()
                                .orElse(null);
                if (current != null
                        && rank(current, stack) >= 0
                        && rank(current, stack) <= rank(dest, stack)) continue;
            }
            Lock lock =
                    new Lock(
                            l.dimension().location().toString(),
                            src.worker,
                            src.pos,
                            src.worker == null ? slot : -1);
            if (LOCKS.containsKey(lock)) continue;
            Task task =
                    new Task(
                            s.id,
                            src.worker,
                            src.pos,
                            slot,
                            dest.pos,
                            stack.copyWithCount(1),
                            l.getGameTime() + 200);
            LOCKS.put(lock, w.getUUID());
            TASKS.put(w.getUUID(), task);
            w.setStatus("Collecting for warehouse");
            return true;
        }
        return false;
    }

    private static void perform(ServerLevel l, Worker w, SiteData.Site s, Task task) {
        Container source;
        BlockPos pos;
        Worker person = null;
        if (task.sourceWorker != null) {
            if (!s.linkedWorkers.contains(task.sourceWorker)) {
                release(w.getUUID());
                return;
            }
            if (!(l.getEntity(task.sourceWorker) instanceof Worker other)
                    || !other.isAlive()
                    || !s.owner.equals(other.owner)) {
                release(w.getUUID());
                return;
            }
            person = other;
            source = other.bag;
            pos = other.blockPosition();
        } else {
            pos = task.source;
            boolean allowed =
                    s.storage.stream().anyMatch(st -> st.pos.equals(pos))
                            || WorkerRegistry.forOwner(l,s.owner).stream().anyMatch(e->e.alive() && e.dimension().equals(l.dimension()) && s.linkedWorkers.contains(e.entityId()) && pos.equals(e.output()) && !pos.equals(e.supply()))
                            || s.linkedSites.stream()
                                    .map(id -> SiteData.get(l).sites.get(id))
                                    .anyMatch(
                                            site ->
                                                    site != null
                                                            && site.owner.equals(s.owner)
                                                            && site.output().equals(pos));
            if (!allowed) {
                release(w.getUUID());
                return;
            }
            source = StorageOps.at(l, pos);
        }
        if (source == null
                || task.slot >= source.getContainerSize()
                || !ItemStack.isSameItemSameComponents(source.getItem(task.slot), task.item)) {
            release(w.getUUID());
            return;
        }
        if (!w.approach(pos)) {
            if (!w.working) release(w.getUUID());
            return;
        }
        ItemStack stack = source.getItem(task.slot);
        var dest = destination(l, w, s, stack, task.source);
        if (dest == null) {
            release(w.getUUID());
            w.setStatus("Warehouse destination full");
            return;
        }
        int count =
                Math.min(
                        stack.getMaxStackSize(),
                        StorageOps.capacity(StorageOps.at(l, dest.pos), stack));
        if (person != null) count = Math.min(count, StorageOps.exportable(person, task.slot));
        int moved = StorageOps.move(source, task.slot, w.bag, count);
        if (person != null && moved > 0 && StorageOps.used(person) < 24) {
            boolean resume = person.needsUnload && person.status.startsWith("Out of space");
            person.needsUnload = false;
            if (resume) {
                person.working = true;
                person.setStatus("Unloaded by courier — resuming");
            }
        }
        release(w.getUUID());
        w.setStatus(moved > 0 ? "Collected warehouse delivery" : "Nothing exportable");
    }

    static void cleanup(ServerLevel l) {
        for (UUID id : new ArrayList<>(TASKS.keySet())) {
            Task t = TASKS.get(id);
            if (l.getEntity(id) instanceof Worker w) {
                if (!w.isAlive() || !w.working || t.expires < l.getGameTime()) release(id);
            }
        }
    }
}
