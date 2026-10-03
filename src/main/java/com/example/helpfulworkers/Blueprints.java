package com.example.helpfulworkers;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class Blueprints {
    private Blueprints() {}

    static boolean hasName(Worker worker, String name) {
        for (int i = 0; i < worker.blueprintLibrary.size(); i++) {
            if (worker.blueprintLibrary.getCompound(i).getString("Name").equals(name)) return true;
        }
        return false;
    }

    static String materialsReadable(ServerLevel level, Worker worker) {
        if (worker.blueprintBlocks.isEmpty()) return "No blueprint";
        if (isFreePreset(worker.blueprint)) {
            return "Free (village preset) — no materials needed for " + BuilderPresets.displayName(worker.blueprint);
        }
        Map<String,Integer> amounts = new HashMap<>();
        for (int i = 0; i < worker.blueprintBlocks.size(); i++) {
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                worker.blueprintBlocks.getCompound(i).getCompound("State"));
            String key = state.getBlock().asItem().getDefaultInstance().getHoverName().getString();
            amounts.merge(key, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder("Total blueprint requirements: ");
        boolean first = true;
        for (Map.Entry<String,Integer> entry : amounts.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(entry.getValue()).append(" x ").append(entry.getKey());
        }
        return sb.toString();
    }

    static boolean isFreePreset(String blueprint) {
        return VillageCatalog.byId(blueprint) != null || BuilderPresets.isPreset(blueprint);
    }

    static String capture(ServerLevel level, Worker worker, String name) {
        if (worker.first == null || worker.second == null) return "Mark an area first";
        int x0=Math.min(worker.first.getX(),worker.second.getX());
        int y0=Math.min(worker.first.getY(),worker.second.getY());
        int z0=Math.min(worker.first.getZ(),worker.second.getZ());
        int dx=Math.abs(worker.first.getX()-worker.second.getX())+1;
        int dy=Math.abs(worker.first.getY()-worker.second.getY())+1;
        int dz=Math.abs(worker.first.getZ()-worker.second.getZ())+1;
        if ((long)dx*dy*dz>4096) return "Blueprint exceeds 4096 blocks";
        ListTag blocks=new ListTag();
        for(int y=0;y<dy;y++) for(int z=0;z<dz;z++) for(int x=0;x<dx;x++) {
            BlockPos pos=new BlockPos(x0+x,y0+y,z0+z);
            if(!level.isLoaded(pos)) return "Blueprint area includes unloaded chunks";
            BlockState state=level.getBlockState(pos);
            if (SiteBlocks.protectedBlock(state)) return "Worker sites cannot be copied";
            if(state.isAir()) continue;
            if(!state.getFluidState().isEmpty() || state.getBlock().asItem()==net.minecraft.world.item.Items.AIR)
                return "Unsupported block at " + pos.toShortString();
            CompoundTag entry=new CompoundTag(); entry.putInt("X",x);entry.putInt("Y",y);entry.putInt("Z",z);
            entry.put("State",NbtUtils.writeBlockState(state));
            if (state.hasBlockEntity()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    CompoundTag beTag = sanitizeBlockEntity(be.saveWithId(level.registryAccess()));
                    if (beTag != null) entry.put("BlockEntity", beTag);
                }
            }
            blocks.add(entry);
        }
        for(int i=worker.blueprintLibrary.size()-1;i>=0;i--) if(worker.blueprintLibrary.getCompound(i).getString("Name").equals(name)) worker.blueprintLibrary.remove(i);
        CompoundTag saved=new CompoundTag(); saved.putString("Name",name);saved.put("Blocks",blocks.copy()); worker.blueprintLibrary.add(saved);
        worker.blueprint=name; worker.blueprintBlocks=blocks; worker.buildOrigin=null; worker.scanCursor=0;
        return "Saved blueprint " + name + " with " + blocks.size() + " blocks";
    }

    /** Keep type id; strip inventory/loot so captured chests place empty. */
    private static CompoundTag sanitizeBlockEntity(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return null;
        CompoundTag copy = tag.copy();
        copy.remove("Items");
        copy.remove("item");
        copy.remove("LootTable");
        copy.remove("LootTableSeed");
        return copy;
    }

    static String load(Worker worker,String name) {
        for(int i=0;i<worker.blueprintLibrary.size();i++) {
            CompoundTag saved=worker.blueprintLibrary.getCompound(i);
            if(saved.getString("Name").equals(name)) {
                worker.blueprint=name;worker.blueprintBlocks=saved.getList("Blocks",10).copy();
                worker.buildOrigin=null;worker.scanCursor=0;return "Loaded blueprint "+name;
            }
        }
        return "Unknown blueprint " + name;
    }

    static String preview(ServerLevel level,Worker worker) {
        if(worker.buildOrigin==null || worker.blueprintBlocks.isEmpty()) return "Choose a blueprint placement first";
        int count=0;
        for(int i=0;i<worker.blueprintBlocks.size();i+=Math.max(1,worker.blueprintBlocks.size()/128)) {
            CompoundTag entry=worker.blueprintBlocks.getCompound(i);
            int x=entry.getInt("X"), y=entry.getInt("Y"), z=entry.getInt("Z");
            int rx=switch(worker.buildRotation){case 1 -> -z;case 2 -> -x;case 3 -> z;default -> x;};
            int rz=switch(worker.buildRotation){case 1 -> x;case 2 -> -z;case 3 -> -x;default -> z;};
            BlockPos pos=worker.buildOrigin.offset(rx,y,rz);
            if(level.isLoaded(pos)) { level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,2,0.15,0.15,0.15,0);count++; }
        }
        return "Previewed "+worker.blueprint+" ("+count+" markers)";
    }

    static String place(Worker worker,BlockPos pos,int rotation) {
        if(worker.blueprintBlocks.isEmpty()) return "Capture a blueprint first";
        worker.buildOrigin=pos; worker.buildRotation=Math.floorMod(rotation,4); worker.scanCursor=0;
        return "Blueprint origin set to " + pos.toShortString() + " (" + worker.buildRotation*90 + " degrees)";
    }

    static String materials(ServerLevel level,Worker worker) {
        if(worker.blueprintBlocks.isEmpty()) return "No blueprint";
        if (isFreePreset(worker.blueprint)) {
            return "Free (village house) — no materials needed. Mark a plot and build.";
        }
        Map<String,Integer> amounts=new HashMap<>();
        for(int i=0;i<worker.blueprintBlocks.size();i++) {
            BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),worker.blueprintBlocks.getCompound(i).getCompound("State"));
            var item = state.getBlock().asItem();
            if (item == net.minecraft.world.item.Items.AIR) continue;
            String key = item.getDefaultInstance().getHoverName().getString();
            amounts.merge(key,1,Integer::sum);
        }
        StringBuilder sb = new StringBuilder("Materials needed: ");
        boolean first = true;
        for (Map.Entry<String,Integer> e : amounts.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(e.getValue()).append("× ").append(e.getKey());
        }
        return sb.toString();
    }

    /** Width/depth/height of the loaded blueprint in blocks. */
    static int[] footprint(Worker worker) {
        int maxX = 0, maxY = 0, maxZ = 0;
        for (int i = 0; i < worker.blueprintBlocks.size(); i++) {
            CompoundTag entry = worker.blueprintBlocks.getCompound(i);
            maxX = Math.max(maxX, entry.getInt("X"));
            maxY = Math.max(maxY, entry.getInt("Y"));
            maxZ = Math.max(maxZ, entry.getInt("Z"));
        }
        return new int[]{maxX + 1, maxY + 1, maxZ + 1};
    }

    /**
     * Place a village (or other) blueprint into the marked work-area plot.
     * Returns null on success, or an error message.
     */
    static String fitToPlot(Worker worker) {
        if (worker.blueprintBlocks.isEmpty()) return "Load a village house or blueprint first";
        if (worker.first == null || worker.second == null) return "Mark a plot with the Assignment Clipboard first";
        int[] size = footprint(worker);
        int minX = Math.min(worker.first.getX(), worker.second.getX());
        int maxX = Math.max(worker.first.getX(), worker.second.getX());
        int minZ = Math.min(worker.first.getZ(), worker.second.getZ());
        int maxZ = Math.max(worker.first.getZ(), worker.second.getZ());
        int minY = Math.min(worker.first.getY(), worker.second.getY());
        int plotW = maxX - minX + 1;
        int plotD = maxZ - minZ + 1;
        // Turn the house 90° automatically when it only fits sideways.
        boolean fitsStraight = size[0] <= plotW && size[2] <= plotD;
        boolean fitsTurned = size[2] <= plotW && size[0] <= plotD;
        if (!fitsStraight && !fitsTurned) {
            return "Area too small — this building needs " + size[0] + "×" + size[2]
                + " (your plot is " + plotW + "×" + plotD + "). Retry with a larger area.";
        }
        worker.buildRotation = fitsStraight ? 0 : 1;
        int needW = fitsStraight ? size[0] : size[2];
        int needD = fitsStraight ? size[2] : size[0];
        int x0 = minX + (plotW - needW) / 2;
        int z0 = minZ + (plotD - needD) / 2;
        // Rotation 1 maps local (x, z) to (-z, x), so shift the origin to keep the house inside the plot.
        worker.buildOrigin = fitsStraight ? new BlockPos(x0, minY, z0) : new BlockPos(x0 + needW - 1, minY, z0);
        worker.scanCursor = 0;
        return null;
    }

    static void tick(ServerLevel level, Worker worker) {
        if (worker.buildOrigin == null || worker.blueprintBlocks.isEmpty()) {
            worker.status = "Set blueprint placement";
            return;
        }
        boolean freePreset = isFreePreset(worker.blueprint);
        int placedThisTick = 0;
        int maxPerTick = freePreset ? 6 : 1;
        for (int i = Math.max(0, worker.scanCursor); i < worker.blueprintBlocks.size(); i++) {
            CompoundTag entry = worker.blueprintBlocks.getCompound(i);
            int x = entry.getInt("X"), y = entry.getInt("Y"), z = entry.getInt("Z");
            int rx = switch (worker.buildRotation) { case 1 -> -z; case 2 -> -x; case 3 -> z; default -> x; };
            int rz = switch (worker.buildRotation) { case 1 -> x; case 2 -> -z; case 3 -> -x; default -> z; };
            BlockPos pos = worker.buildOrigin.offset(rx, y, rz);
            if (!level.isLoaded(pos)) { worker.status = "Build chunk unloaded"; return; }
            Rotation rotation = switch (worker.buildRotation) {
                case 1 -> Rotation.CLOCKWISE_90;
                case 2 -> Rotation.CLOCKWISE_180;
                case 3 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound("State")).rotate(rotation);
            BlockState current = level.getBlockState(pos);
            if (SiteBlocks.protectedBlock(state) || SiteBlocks.protectedBlock(current)) { worker.setStatus("Cannot copy or overwrite a worker site"); worker.working = false; return; }
            if (current.equals(state) || (state.isAir() && current.isAir())) { worker.scanCursor = i + 1; continue; }
            if (freePreset) {
                // Village houses clear the plot as they go, but never destroy chests or other block entities.
                if (current.hasBlockEntity() || current.is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                    worker.scanCursor = i + 1;
                    continue;
                }
            } else if (!current.isAir()) {
                worker.status = "Conflicting block at " + pos.toShortString();
                worker.working = false;
                return;
            }
            if (placedThisTick == 0 && !worker.approachWithin(pos, 4, 7)) return;
            if (!state.getCollisionShape(level, pos).isEmpty()
                && worker.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos))) {
                BlockPos standOn = pos.above();
                while (!Worker.canStandAt(level, standOn) && standOn.getY() < pos.getY() + 8) standOn = standOn.above();
                worker.teleportTo(worker.getX(), standOn.getY(), worker.getZ());
            }
            if (!freePreset) {
                ItemStack needed = new ItemStack(state.getBlock().asItem());
                if (needed.isEmpty() || needed.getItem() == net.minecraft.world.item.Items.AIR) {
                    worker.scanCursor = i + 1;
                    continue;
                }
                if (!Jobs.take(level, worker, needed)) {
                    worker.status = "Missing " + needed.getHoverName().getString();
                    return;
                }
            }
            // Skip neighbor updates so torches, doors and panes aren't knocked off before their walls exist.
            level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
            if (entry.contains("BlockEntity")) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    try {
                        be.loadWithComponents(entry.getCompound("BlockEntity").copy(), level.registryAccess());
                        be.setChanged();
                    } catch (Exception ignored) {
                        // Empty or mismatched BE tags are fine for free presets.
                    }
                }
            }
            worker.scanCursor = i + 1;
            placedThisTick++;
            worker.status = "Building " + worker.blueprint + ": " + worker.scanCursor + "/" + worker.blueprintBlocks.size();
            if (placedThisTick >= maxPerTick) return;
        }
        worker.status = "Blueprint complete";
        worker.working = false;
    }
}
