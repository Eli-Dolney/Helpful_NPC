package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Per-dimension site state. Transient caches never grant inventory access. */
final class SiteData extends SavedData {
    final Map<UUID, Site> sites = new LinkedHashMap<>();
    final Map<String, CompoundTag> templates = new LinkedHashMap<>();
    final Map<BlockPos, UUID> cores = new HashMap<>();

    static final class Storage {
        BlockPos pos;
        String mode = "sorted", filter = "miscellaneous";

        Storage(BlockPos p) {
            pos = p.immutable();
        }
    }

    static final class TreeSlot {
        String species; BlockPos base; boolean enabled=true, legacy, building;
        int cursor;
        final Set<BlockPos> logs=new LinkedHashSet<>();
        final List<BlockPos> path=new ArrayList<>();
        TreeSlot(String species,BlockPos base) { this.species=species; this.base=base.immutable(); }
        boolean contains(BlockPos p) { return Math.abs(p.getX()-base.getX())<=4 && Math.abs(p.getZ()-base.getZ())<=4 && p.getY()>=base.getY()-1 && p.getY()<base.getY()+19; }
    }

    static final class Site {
        UUID id = UUID.randomUUID(), owner, worker, builder;
        String type, phase = "level", status = "Preparing ground";
        BlockPos origin, plotFirst, plotSecond;
        CompoundTag template = new CompoundTag();
        List<SiteCatalog.Piece> templatePieces;
        int width() { return template.isEmpty()?SiteCatalog.width(type):template.getInt("Width"); }
        int depth() { return template.isEmpty()?SiteCatalog.depth(type):template.getInt("Depth"); }
        int height() { return template.isEmpty()?SiteCatalog.height(type):template.getInt("Height"); }
        BlockPos anchor(String name,BlockPos fallback) { return template.getCompound("Anchors").contains(name)?local(BlockPos.of(template.getCompound("Anchors").getLong(name))):fallback; }
        BlockPos local(BlockPos pos) { return at(pos.getX(),pos.getY(),pos.getZ()); }
        BlockPos entrance() { return anchor("Entrance",at(width()/2,1,depth()-1)); }
        BlockPos station() { return anchor("Work",at(4,1,4)); }
        List<BlockPos> ores() { return List.of(anchor("Ore1",at(2,1,1)),anchor("Ore2",at(3,1,1)),anchor("Ore3",at(4,1,1))); }
        int rotation, cursor, production, treeCursor;
        boolean paid, paused, instant, starterStock;
        int starterTreeCursor;
        int oreDelay;
        int[] treeTimers = new int[8];
        boolean[] enabled = {true, true, true, true, true, true, true, true};
        final List<TreeSlot> treeSlots = new ArrayList<>();
        final Set<BlockPos> generated = new HashSet<>();
        final List<Storage> storage = new ArrayList<>();
        final Set<UUID> linkedWorkers = new LinkedHashSet<>(), linkedSites = new LinkedHashSet<>();
        final Set<UUID> excluded = new HashSet<>();
        final Map<UUID, Map<String,Integer>> supplyTargets = new HashMap<>();
        final Set<UUID> noRestock = new HashSet<>();
        final Set<UUID> couriers = new LinkedHashSet<>();
        // Active-time accounting: reset by unload/restart, never persist wall clock timestamps.
        long lastActive = -1;

        boolean active() {
            return "active".equals(phase);
        }

        BlockPos at(int x, int y, int z) {
            return SiteCatalog.at(this, x, y, z);
        }

        BlockPos core() {
            return anchor("Core",at(width() / 2, 1, depth() / 2));
        }

        BlockPos supply() { return anchor("Supply",at(width() / 2 - 1, 1, depth() / 2)); }

        BlockPos output() {
            return anchor("Output",at(width() / 2 + 1, 1, depth() / 2));
        }
    }

    static SiteData get(ServerLevel l) {
        return l.getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(SiteData::new, SiteData::load),
                        "helpfulworkers_sites");
    }

    void add(Site s) {
        sites.put(s.id, s);
        cores.put(s.core(), s.id);
        setDirty();
    }

    Site at(BlockPos p) {
        UUID id = cores.get(p);
        return id == null ? null : sites.get(id);
    }

    static SiteData load(CompoundTag root, HolderLookup.Provider provider) {
        SiteData data = new SiteData();
        var templates=root.getCompound("Templates");
        for(String key:templates.getAllKeys()) data.templates.put(key,templates.getCompound(key));
        ListTag list = root.getList("Sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("Id") || !t.hasUUID("Owner") || !SiteCatalog.valid(t.getString("Type")))
                continue;
            Site s = new Site();
            s.id = t.getUUID("Id");
            s.owner = t.getUUID("Owner");
            s.type = t.getString("Type");
            s.template=t.getCompound("Template");
            s.origin = BlockPos.of(t.getLong("Origin"));
            s.plotFirst = BlockPos.of(t.getLong("First"));
            s.plotSecond = BlockPos.of(t.getLong("Second"));
            s.rotation = Math.floorMod(t.getInt("Rotation"), 4);
            s.phase = t.getString("Phase");
            s.cursor = Math.max(0, t.getInt("Cursor"));
            s.paid = t.getBoolean("Paid");
            s.paused = t.getBoolean("Paused");
            s.status = t.getString("Status");
            s.production = t.getInt("Production");
            s.oreDelay = t.getInt("OreDelay");
            s.instant = t.getBoolean("Instant");
            s.starterStock = t.getBoolean("StarterStock");
            s.starterTreeCursor = Math.max(0, t.getInt("StarterTreeCursor"));
            s.treeCursor = t.getInt("TreeCursor");
            if (t.hasUUID("Worker")) s.worker = t.getUUID("Worker");
            if (t.hasUUID("Builder")) s.builder = t.getUUID("Builder");
            int[] timers = t.getIntArray("Timers"), enabled = t.getIntArray("Enabled");
            for (int j = 0; j < 8; j++) {
                if (j < timers.length) s.treeTimers[j] = timers[j];
                if (j < enabled.length) s.enabled[j] = enabled[j] != 0;
            }
            for (long pos : t.getLongArray("Generated")) s.generated.add(BlockPos.of(pos));
            ListTag stores = t.getList("Storage", Tag.TAG_COMPOUND);
            for (int j = 0; j < stores.size(); j++) {
                CompoundTag a = stores.getCompound(j);
                Storage st = new Storage(BlockPos.of(a.getLong("Pos")));
                st.mode = a.getString("Mode");
                st.filter = a.getString("Filter");
                s.storage.add(st);
            }
            ListTag trees=t.getList("TreeSlots",Tag.TAG_COMPOUND);
            for(int j=0;j<trees.size();j++) {
                var a=trees.getCompound(j); var slot=new TreeSlot(a.getString("Species"),BlockPos.of(a.getLong("Base")));
                slot.enabled=a.getBoolean("Enabled"); slot.legacy=a.getBoolean("Legacy"); slot.building=a.getBoolean("Building"); slot.cursor=a.getInt("Cursor");
                for(long pos:a.getLongArray("Path")) slot.path.add(BlockPos.of(pos));
                for(long pos:a.getLongArray("Logs")) slot.logs.add(BlockPos.of(pos));
                s.treeSlots.add(slot);
            }
            readIds(t, "Links", s.linkedWorkers);
            readIds(t, "Sites", s.linkedSites);
            readIds(t, "Couriers", s.couriers);
            readIds(t,"Excluded",s.excluded); readIds(t,"NoRestock",s.noRestock);
            ListTag requests=t.getList("SupplyTargets",Tag.TAG_COMPOUND);
            for(int j=0;j<requests.size();j++) {
                var a=requests.getCompound(j); if(!a.hasUUID("Worker")) continue;
                Map<String,Integer> targets=new LinkedHashMap<>(); var values=a.getCompound("Items");
                for(String key:values.getAllKeys()) targets.put(key,Math.max(0,Math.min(4096,values.getInt(key))));
                s.supplyTargets.put(a.getUUID("Worker"),targets);
            }
            data.add(s);
        }
        return data;
    }

    private static void readIds(CompoundTag t, String key, Set<UUID> into) {
        ListTag l = t.getList(key, Tag.TAG_INT_ARRAY);
        for (Tag a : l)
            try {
                into.add(NbtUtils.loadUUID(a));
            } catch (Exception ignored) {
            }
    }

    private static void writeIds(CompoundTag t, String key, Collection<UUID> ids) {
        ListTag l = new ListTag();
        for (UUID id : ids) l.add(NbtUtils.createUUID(id));
        t.put(key, l);
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Site s : sites.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", s.id);
            t.putUUID("Owner", s.owner);
            t.putString("Type", s.type);
            t.put("Template",s.template);
            t.putLong("Origin", s.origin.asLong());
            t.putLong("First", s.plotFirst.asLong());
            t.putLong("Second", s.plotSecond.asLong());
            t.putInt("Rotation", s.rotation);
            t.putString("Phase", s.phase);
            t.putInt("Cursor", s.cursor);
            t.putBoolean("Paid", s.paid);
            t.putBoolean("Paused", s.paused);
            t.putString("Status", s.status);
            t.putInt("Production", s.production);
            t.putInt("OreDelay", s.oreDelay);
            t.putBoolean("Instant", s.instant);
            t.putBoolean("StarterStock", s.starterStock);
            t.putInt("StarterTreeCursor", s.starterTreeCursor);
            t.putInt("TreeCursor", s.treeCursor);
            if (s.worker != null) t.putUUID("Worker", s.worker);
            if (s.builder != null) t.putUUID("Builder", s.builder);
            t.putIntArray("Timers", s.treeTimers);
            int[] en = new int[8];
            for (int j = 0; j < 8; j++) en[j] = s.enabled[j] ? 1 : 0;
            t.putIntArray("Enabled", en);
            t.putLongArray("Generated", s.generated.stream().mapToLong(BlockPos::asLong).toArray());
            ListTag stores = new ListTag();
            for (Storage st : s.storage) {
                CompoundTag a = new CompoundTag();
                a.putLong("Pos", st.pos.asLong());
                a.putString("Mode", st.mode);
                a.putString("Filter", st.filter);
                stores.add(a);
            }
            t.put("Storage", stores);
            ListTag trees=new ListTag();
            for(var slot:s.treeSlots) {
                var a=new CompoundTag(); a.putString("Species",slot.species); a.putLong("Base",slot.base.asLong());
                a.putBoolean("Enabled",slot.enabled); a.putBoolean("Legacy",slot.legacy); a.putBoolean("Building",slot.building); a.putInt("Cursor",slot.cursor);
                a.putLongArray("Path",slot.path.stream().mapToLong(BlockPos::asLong).toArray());
                a.putLongArray("Logs",slot.logs.stream().mapToLong(BlockPos::asLong).toArray()); trees.add(a);
            }
            t.put("TreeSlots",trees);
            writeIds(t, "Links", s.linkedWorkers);
            writeIds(t, "Sites", s.linkedSites);
            writeIds(t, "Couriers", s.couriers);
            writeIds(t,"Excluded",s.excluded); writeIds(t,"NoRestock",s.noRestock);
            ListTag requests=new ListTag();
            s.supplyTargets.forEach((id,targets)-> {
                var a=new CompoundTag(); a.putUUID("Worker",id); var values=new CompoundTag();
                targets.forEach(values::putInt); a.put("Items",values); requests.add(a);
            }); t.put("SupplyTargets",requests);
            list.add(t);
        }
        root.put("Sites", list);
        var templates=new CompoundTag(); this.templates.forEach(templates::put); root.put("Templates",templates);
        return root;
    }
}
