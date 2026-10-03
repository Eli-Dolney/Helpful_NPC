package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Persists owned workers so caps and the monitor work across unloaded chunks. */
final class WorkerRegistry extends SavedData {
    private static final String DATA_NAME = "helpfulworkers_registry";

    record Entry(UUID entityId, UUID owner, String role, String name, ResourceKey<Level> dimension,
                 BlockPos pos, boolean alive, BlockPos bed, BlockPos supply, BlockPos output) {}

    private final Map<UUID, Entry> byEntity = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(WorkerRegistry::onJoin);
        NeoForge.EVENT_BUS.addListener(WorkerRegistry::onLeave);
        NeoForge.EVENT_BUS.addListener(WorkerRegistry::onServerTick);
    }

    static WorkerRegistry get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(WorkerRegistry::new, WorkerRegistry::load), DATA_NAME);
    }

    static int countForOwner(ServerLevel level, UUID owner) {
        int n = 0;
        for (Entry e : get(level).byEntity.values()) {
            if (e.alive && owner.equals(e.owner)) n++;
        }
        return n;
    }

    static int countAlive(ServerLevel level) {
        int n = 0;
        for (Entry e : get(level).byEntity.values()) if (e.alive) n++;
        return n;
    }

    static List<Entry> forOwner(ServerLevel level, UUID owner) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : get(level).byEntity.values()) {
            if (owner.equals(e.owner)) out.add(e);
        }
        return out;
    }

    static void upsert(Worker worker) {
        if (!(worker.level() instanceof ServerLevel level) || worker.owner == null) return;
        WorkerRegistry reg = get(level);
        reg.byEntity.put(worker.getUUID(), new Entry(
            worker.getUUID(), worker.owner, worker.role == null ? "idle" : worker.role,
            worker.getName().getString(), level.dimension(), worker.blockPosition().immutable(), true, worker.bed, worker.supply, worker.output));
        reg.setDirty();
    }

    static void markGone(ServerLevel level, UUID entityId) {
        WorkerRegistry reg = get(level);
        Entry old = reg.byEntity.get(entityId);
        if (old == null) return;
        reg.byEntity.put(entityId, new Entry(old.entityId, old.owner, old.role, old.name,
            old.dimension, old.pos, false, old.bed, old.supply, old.output));
        reg.setDirty();
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof Worker worker) upsert(worker);
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof Worker worker)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        WorkerRegistry reg = get(level);
        Entry old = reg.byEntity.get(worker.getUUID());
        if (old != null) {
            reg.byEntity.put(worker.getUUID(), new Entry(old.entityId, old.owner, worker.role,
                worker.getName().getString(), level.dimension(), worker.blockPosition().immutable(), worker.isAlive(), worker.bed, worker.supply, worker.output));
            reg.setDirty();
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 200 != 0) return;
        ServerLevel overworld = event.getServer().overworld();
        WorkerRegistry reg = get(overworld);
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Worker worker : level.getEntities(HelpfulWorkers.WORKER.get(), w -> true)) {
                upsert(worker);
            }
        }
        // Drop stale dead entries older than nothing tracked — keep last known for monitor.
        if (reg.byEntity.size() > 256) {
            Iterator<Map.Entry<UUID, Entry>> it = reg.byEntity.entrySet().iterator();
            while (it.hasNext() && reg.byEntity.size() > 200) {
                if (!it.next().getValue().alive) it.remove();
            }
            reg.setDirty();
        }
    }

    static BlockPos readPos(CompoundTag t,String key) { return t.contains(key)?BlockPos.of(t.getLong(key)):null; }
    WorkerRegistry() {}

    private static WorkerRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        WorkerRegistry reg = new WorkerRegistry();
        ListTag list = tag.getList("Workers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (!e.hasUUID("Id") || !e.hasUUID("Owner")) continue;
            ResourceKey<Level> dim = Level.OVERWORLD;
            try {
                dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(e.getString("Dim")));
            } catch (Exception ignored) {}
            BlockPos pos = e.contains("Pos") ? BlockPos.of(e.getLong("Pos")) : BlockPos.ZERO;
            UUID id = e.getUUID("Id");
            reg.byEntity.put(id, new Entry(id, e.getUUID("Owner"), e.getString("Role"), e.getString("Name"),
                dim, pos, e.getBoolean("Alive"), readPos(e,"Bed"), readPos(e,"Supply"), readPos(e,"Output")));
        }
        return reg;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Entry e : byEntity.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", e.entityId);
            t.putUUID("Owner", e.owner);
            t.putString("Role", e.role == null ? "idle" : e.role);
            t.putString("Name", e.name == null ? "Worker" : e.name);
            t.putString("Dim", e.dimension.location().toString());
            t.putLong("Pos", e.pos.asLong());
            t.putBoolean("Alive", e.alive);
            if(e.bed!=null) t.putLong("Bed",e.bed.asLong());
            if(e.supply!=null) t.putLong("Supply",e.supply.asLong());
            if(e.output!=null) t.putLong("Output",e.output.asLong());
            list.add(t);
        }
        tag.put("Workers", list);
        return tag;
    }
}
