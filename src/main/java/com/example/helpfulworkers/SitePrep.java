package com.example.helpfulworkers;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builder ground preparation: clears trees and plants from the build site, or flattens the whole
 * plot (removes everything above ground level and fills holes below it) before blocks are placed.
 */
final class SitePrep {
    private SitePrep() {}

    static final String NONE = "none";
    static final String CLEAR = "clear";
    static final String FLATTEN = "flatten";

    /** Tallest column the builder will clear above ground (covers big jungle/dark oak trees). */
    private static final int MAX_CLEAR_HEIGHT = 48;
    /** Deepest hole the builder fills under the ground layer. */
    private static final int MAX_FILL_DEPTH = 6;
    /** Blocks changed per tick while preparing; matches the free-preset placement pace. */
    private static final int BLOCK_BUDGET = 6;
    /** Columns inspected per tick when nothing needs doing (keeps big plots cheap). */
    private static final int SCAN_BUDGET = 96;

    /** Inclusive horizontal bounds plus the ground level that stays solid. */
    record Site(int minX, int maxX, int minZ, int maxZ, int groundY) {
        int width() { return maxX - minX + 1; }
        int depth() { return maxZ - minZ + 1; }
        int columns() { return width() * depth(); }
        boolean containsColumn(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
    }

    static boolean isMode(String mode) {
        return NONE.equals(mode) || CLEAR.equals(mode) || FLATTEN.equals(mode);
    }

    static String label(String mode) {
        return switch (mode == null ? "" : mode) {
            case FLATTEN -> "Flatten plot";
            case NONE -> "Off";
            default -> "Clear trees & plants";
        };
    }

    /** Forget prep progress so the next build re-inspects the site. */
    static void reset(Worker worker) {
        worker.sitePrepDone = false;
        worker.prepCursor = 0;
        worker.prepPlanned = null;
    }

    /**
     * The ground to prepare. A village house fitted into a marked plot prepares the whole plot;
     * a custom blueprint prepares only its rotated footprint, since the marked area is its copy source.
     */
    static Site site(Worker worker) {
        boolean plotSet = worker.first != null && worker.second != null;
        if (plotSet) {
            int minX = Math.min(worker.first.getX(), worker.second.getX());
            int maxX = Math.max(worker.first.getX(), worker.second.getX());
            int minZ = Math.min(worker.first.getZ(), worker.second.getZ());
            int maxZ = Math.max(worker.first.getZ(), worker.second.getZ());
            int groundY = Math.min(worker.first.getY(), worker.second.getY());
            Site plot = new Site(minX, maxX, minZ, maxZ, groundY);
            if (worker.prepOnly) return plot;
            if (Blueprints.isFreePreset(worker.blueprint) && worker.buildOrigin != null
                && plot.containsColumn(worker.buildOrigin.getX(), worker.buildOrigin.getZ())) {
                return plot;
            }
        }
        if (worker.buildOrigin == null || worker.blueprintBlocks.isEmpty()) return null;
        int[] size = Blueprints.footprint(worker);
        int w = size[0], d = size[2];
        int ox = worker.buildOrigin.getX(), oz = worker.buildOrigin.getZ();
        // Rotation maps local (x, z) to: 0 -> (x, z); 1 -> (-z, x); 2 -> (-x, -z); 3 -> (z, -x).
        int minX, maxX, minZ, maxZ;
        switch (worker.buildRotation) {
            case 1 -> { minX = ox - (d - 1); maxX = ox; minZ = oz; maxZ = oz + (w - 1); }
            case 2 -> { minX = ox - (w - 1); maxX = ox; minZ = oz - (d - 1); maxZ = oz; }
            case 3 -> { minX = ox; maxX = ox + (d - 1); minZ = oz - (w - 1); maxZ = oz; }
            default -> { minX = ox; maxX = ox + (w - 1); minZ = oz; maxZ = oz + (d - 1); }
        }
        // Village templates carry their own ground layer at Y=0; custom captures start one block above the ground.
        int groundY = Blueprints.isFreePreset(worker.blueprint) ? worker.buildOrigin.getY() : worker.buildOrigin.getY() - 1;
        return new Site(minX, maxX, minZ, maxZ, groundY);
    }

    /**
     * Advance ground preparation. Returns true when the site is ready (or prep is off) so the
     * caller can go on placing blocks; false when the builder is still travelling or clearing.
     */
    static boolean tick(ServerLevel level, Worker worker) {
        boolean flatten = FLATTEN.equals(worker.sitePrep) || worker.prepOnly;
        if (!flatten && !CLEAR.equals(worker.sitePrep)) return true;
        if (worker.sitePrepDone) return true;
        Site site = site(worker);
        if (site == null) { worker.sitePrepDone = true; return true; }
        if (!Jobs.handleInventory(level, worker)) return false;

        boolean freeFill = worker.prepOnly || Blueprints.isFreePreset(worker.blueprint);
        Map<BlockPos, BlockState> planned = planned(worker);
        int columns = site.columns();
        for (int scanned = 0; scanned < SCAN_BUDGET && worker.prepCursor < columns; scanned++) {
            int x = site.minX() + worker.prepCursor % site.width();
            int z = site.minZ() + worker.prepCursor / site.width();
            BlockPos ground = new BlockPos(x, site.groundY(), z);
            if (!level.isLoaded(ground)) { worker.setStatusKey(Lang.STATUS_SITE_CHUNK_UNLOADED); return false; }
            BlockPos work = nextInColumn(level, site, planned, x, z, flatten);
            if (work == null) { worker.prepCursor++; continue; }
            if (!worker.approachWithin(ground.above(), 5, 6)) return false;
            // Standing in the hole we are about to fill: step out of it first.
            if (work.getY() <= site.groundY() && !worker.nav.stepAside(work)) return false;
            int changed = 0;
            while (work != null && changed < BLOCK_BUDGET) {
                if (work.getY() <= site.groundY()) {
                    if (!fill(level, worker, work, site, freeFill)) return false;
                } else {
                    remove(level, worker, work);
                }
                changed++;
                work = nextInColumn(level, site, planned, x, z, flatten);
            }
            worker.setStatusKey(flatten ? Lang.STATUS_FLATTENING_SITE : Lang.STATUS_CLEARING_SITE,
                Math.min(worker.prepCursor + 1, columns), columns);
            return false;
        }
        if (worker.prepCursor >= columns) {
            worker.sitePrepDone = true;
            worker.prepCursor = 0;
            worker.prepPlanned = null;
            worker.setStatusKey(flatten ? Lang.STATUS_SITE_FLATTENED : Lang.STATUS_SITE_CLEARED);
            return true;
        }
        return false;
    }

    /** Next block in the column that still needs removing (above ground) or filling (ground and below). */
    private static BlockPos nextInColumn(ServerLevel level, Site site, Map<BlockPos, BlockState> planned,
                                         int x, int z, boolean flatten) {
        int top = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1,
            site.groundY() + MAX_CLEAR_HEIGHT);
        for (int y = top; y > site.groundY(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.equals(planned.get(pos))) continue;
            if (flatten ? removableForFlatten(level, pos, state) : isVegetation(state)) return pos;
        }
        if (!flatten) return null;
        for (int y = site.groundY(); y >= site.groundY() - MAX_FILL_DEPTH; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (y < level.getMinBuildHeight()) break;
            BlockState state = level.getBlockState(pos);
            if (state.equals(planned.get(pos)) && !state.isAir()) break;
            if (needsFill(level, pos, state)) return pos;
            break;
        }
        return null;
    }

    private static boolean removableForFlatten(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.BEDROCK) || state.hasBlockEntity()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        if (!state.getFluidState().isEmpty()) return false;
        return true;
    }

    /** Trees, leaves, grass, flowers, mushrooms, vines, snow layers and other plant cover. */
    static boolean isVegetation(BlockState state) {
        if (state.isAir()) return false;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS)
            || state.is(BlockTags.FLOWERS) || state.is(BlockTags.REPLACEABLE_BY_TREES)) return true;
        if (state.getBlock() instanceof BushBlock || state.getBlock() instanceof VineBlock) return true;
        if (state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS) || state.is(Blocks.BAMBOO)
            || state.is(Blocks.BAMBOO_SAPLING) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)
            || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
            || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.SNOW) || state.is(Blocks.MANGROVE_ROOTS)
            || state.is(Blocks.MUDDY_MANGROVE_ROOTS) || state.is(Blocks.COCOA) || state.is(Blocks.MOSS_CARPET)
            || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.HANGING_ROOTS)) return true;
        if (!state.getFluidState().isEmpty()) return false;
        return state.canBeReplaced();
    }

    private static boolean needsFill(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return true;
        if (isVegetation(state)) return true;
        return !state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP);
    }

    private static void remove(ServerLevel level, Worker worker, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        java.util.List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), worker,
            worker.getMainHandItem());
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        worker.getLookControl().setLookAt(net.minecraft.world.phys.Vec3.atCenterOf(pos));
        level.destroyBlock(pos, false, worker);
        for (ItemStack drop : drops) worker.collect(drop);
    }

    private static boolean fill(ServerLevel level, Worker worker, BlockPos pos, Site site, boolean free) {
        BlockState current = level.getBlockState(pos);
        if (!current.isAir() && current.getFluidState().isEmpty()) {
            java.util.List<ItemStack> drops = Block.getDrops(current, level, pos, level.getBlockEntity(pos), worker,
                worker.getMainHandItem());
            for (ItemStack drop : drops) worker.collect(drop);
        }
        boolean surface = pos.getY() == site.groundY();
        if (!free) {
            boolean paid = Jobs.take(level, worker, new ItemStack(Items.DIRT))
                || Jobs.take(level, worker, new ItemStack(Items.GRASS_BLOCK))
                || Jobs.take(level, worker, new ItemStack(Items.COBBLESTONE))
                || Jobs.take(level, worker, new ItemStack(Items.COARSE_DIRT));
            if (!paid) {
                if (!worker.nav.hasErrand()) worker.setStatusKey(Lang.STATUS_NEED_DIRT, pos.toShortString());
                return false;
            }
        }
        BlockState fill = surface ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        level.setBlock(pos, fill, Block.UPDATE_ALL);
        var sound = fill.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS,
            (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
        return true;
    }

    /** World positions and states the current blueprint will place, so prep never removes them. */
    private static Map<BlockPos, BlockState> planned(Worker worker) {
        if (worker.buildOrigin == null || worker.blueprintBlocks.isEmpty()) return Map.of();
        if (worker.prepPlanned != null && worker.buildOrigin.equals(worker.prepPlannedOrigin)
            && worker.prepPlannedRotation == worker.buildRotation
            && worker.prepPlannedSize == worker.blueprintBlocks.size()) {
            return worker.prepPlanned;
        }
        Map<BlockPos, BlockState> map = new HashMap<>();
        Rotation rotation = switch (worker.buildRotation) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        for (int i = 0; i < worker.blueprintBlocks.size(); i++) {
            CompoundTag entry = worker.blueprintBlocks.getCompound(i);
            int x = entry.getInt("X"), y = entry.getInt("Y"), z = entry.getInt("Z");
            int rx = switch (worker.buildRotation) { case 1 -> -z; case 2 -> -x; case 3 -> z; default -> x; };
            int rz = switch (worker.buildRotation) { case 1 -> x; case 2 -> -z; case 3 -> -x; default -> z; };
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound("State"))
                .rotate(rotation);
            if (state.isAir()) continue;
            map.put(worker.buildOrigin.offset(rx, y, rz), state);
        }
        worker.prepPlanned = map;
        worker.prepPlannedOrigin = worker.buildOrigin;
        worker.prepPlannedRotation = worker.buildRotation;
        worker.prepPlannedSize = worker.blueprintBlocks.size();
        return map;
    }
}
