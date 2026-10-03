package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

final class SiteConstruction {
    static SiteData.Site plan(
            ServerPlayer p,
            Worker b,
            String type,
            BlockPos a,
            BlockPos c,
            int rotation,
            Integer floor) {
        return plan(p, b, type, a, c, rotation, floor, true);
    }

    static SiteData.Site plan(
            ServerPlayer p,
            Worker b,
            String type,
            BlockPos a,
            BlockPos c,
            int rotation,
            Integer floor,
            boolean checkTerrain) {
        if (!SiteCatalog.valid(type) || !"builder".equals(b.role) || !b.owns(p))
            throw new IllegalArgumentException("Choose an owned builder and a valid site");
        var selected=SiteTemplates.selected(p,type);
        int w = selected.isEmpty()?SiteCatalog.width(type):selected.getInt("Width"), d = selected.isEmpty()?SiteCatalog.depth(type):selected.getInt("Depth");
        int rw = rotation % 2 == 0 ? w : d, rd = rotation % 2 == 0 ? d : w;
        int minX = Math.min(a.getX(), c.getX()), minZ = Math.min(a.getZ(), c.getZ());
        long pw = Math.abs((long) a.getX() - c.getX()) + 1,
                pd = Math.abs((long) a.getZ() - c.getZ()) + 1;
        if (pw < rw || pd < rd)
            throw new IllegalArgumentException(
                    "Plot needs at least " + rw + " x " + rd + " blocks");
        int ox = (int) (minX + (pw - rw) / 2), oz = (int) (minZ + (pd - rd) / 2);
        ServerLevel l = p.serverLevel();
        List<Integer> heights = new ArrayList<>();
        for (int x = 0; x < rw; x++)
            for (int z = 0; z < rd; z++) {
                BlockPos pos = new BlockPos(ox + x, 0, oz + z);
                if (!l.hasChunkAt(pos))
                    throw new IllegalArgumentException("Load the full site footprint first");
                int y =
                        l.getHeight(
                                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                        pos.getX(),
                                        pos.getZ())
                                - 1;
                while (y > l.getMinBuildHeight()
                        && vegetation(l.getBlockState(new BlockPos(pos.getX(), y, pos.getZ()))))
                    y--;
                heights.add(y);
            }
        Collections.sort(heights);
        int y = floor == null ? heights.get(heights.size() / 2) : floor;
        if (y < l.getMinBuildHeight() || y + (selected.isEmpty()?SiteCatalog.height(type):selected.getInt("Height")) >= l.getMaxBuildHeight())
            throw new IllegalArgumentException("Site exceeds world height");
        SiteData.Site s = new SiteData.Site();
        s.owner = p.getUUID();
        s.builder = b.getUUID();
        s.type = type;
        s.template=selected.copy();
        s.rotation = rotation;
        s.origin = new BlockPos(ox, y, oz);
        s.plotFirst = a;
        s.plotSecond = c;
        if (!l.getWorldBorder().isWithinBounds(s.origin)
                || !l.getWorldBorder().isWithinBounds(s.origin.offset(rw - 1, 0, rd - 1)))
            throw new IllegalArgumentException("Site crosses world border");
        for (SiteData.Site other : SiteData.get(l).sites.values())
            if (overlaps(s, other))
                throw new IllegalArgumentException("Plot overlaps another worker site");
        if (checkTerrain)
            for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) validateColumn(l, s, x, z);
        return s;
    }

    static boolean overlaps(SiteData.Site a, SiteData.Site b) {
        int aw = a.rotation % 2 == 0 ? a.width() : a.depth(),
                ad = a.rotation % 2 == 0 ? a.depth() : a.width(),
                bw = b.rotation % 2 == 0 ? b.width() : b.depth(),
                bd = b.rotation % 2 == 0 ? b.depth() : b.width();
        return a.origin.getX() < b.origin.getX() + bw
                && a.origin.getX() + aw > b.origin.getX()
                && a.origin.getZ() < b.origin.getZ() + bd
                && a.origin.getZ() + ad > b.origin.getZ();
    }

    static boolean vegetation(BlockState b) {
        return b.is(BlockTags.LEAVES)
                || b.is(BlockTags.LOGS)
                || b.is(BlockTags.SAPLINGS)
                || b.is(Blocks.SHORT_GRASS)
                || b.is(Blocks.TALL_GRASS)
                || b.is(Blocks.FERN)
                || b.is(Blocks.LARGE_FERN)
                || b.is(Blocks.SNOW)
                || b.is(Blocks.VINE)
                || b.is(BlockTags.FLOWERS);
    }

    static boolean soft(BlockState b) {
        return b.is(Blocks.SNOW) || b.is(Blocks.SHORT_GRASS) || b.is(Blocks.TALL_GRASS)
            || b.is(Blocks.FERN) || b.is(Blocks.LARGE_FERN) || b.is(BlockTags.FLOWERS);
    }

    static boolean terrain(BlockState b) {
        return b.isAir()
                || vegetation(b)
                || b.is(BlockTags.DIRT)
                || b.is(Blocks.FARMLAND)
                || b.is(Blocks.DIRT_PATH)
                || b.is(Blocks.STONE)
                || b.is(Blocks.DEEPSLATE)
                || b.is(Blocks.GRAVEL)
                || b.is(Blocks.SAND)
                || b.is(Blocks.RED_SAND)
                || b.is(Blocks.CLAY)
                || b.is(Blocks.ANDESITE)
                || b.is(Blocks.DIORITE)
                || b.is(Blocks.GRANITE);
    }

    /** Compare structural state, excluding properties maintained by the world itself. */
    static boolean matches(BlockState current, BlockState desired) {
        if (!current.is(desired.getBlock())) return false;
        for (var property : desired.getProperties()) {
            if (property == BlockStateProperties.MOISTURE && desired.is(Blocks.FARMLAND)
                    || property == BlockStateProperties.SNOWY
                    || (desired.getBlock() instanceof FenceBlock || desired.getBlock() instanceof WallBlock
                        || desired.getBlock() instanceof IronBarsBlock)
                        && Set.of("north", "south", "east", "west", "up").contains(property.getName())) continue;
            if (!current.getValue(property).equals(desired.getValue(property))) return false;
        }
        return true;
    }

    static void validateColumn(ServerLevel l, SiteData.Site s, int x, int z) {
        BlockPos floor = s.at(x, 0, z);
        if (!l.hasChunkAt(floor)) throw new IllegalArgumentException("Site chunk unloaded");
        for (int y = 1; y < s.height(); y++) {
            BlockPos p = floor.above(y);
            BlockState state = l.getBlockState(p);
            if (!state.isAir()
                    && (!state.getFluidState().isEmpty()
                            || !terrain(state)
                            || state.hasBlockEntity()
                            || y > 8))
                throw new IllegalArgumentException("Clear obstruction at " + p.toShortString());
        }
        for (int n = 0; n <= 8; n++) {
            BlockPos p = floor.below(n);
            BlockState state = l.getBlockState(p);
            if (!state.getFluidState().isEmpty() || state.hasBlockEntity() || !terrain(state))
                throw new IllegalArgumentException("Protected foundation at " + p.toShortString());
            if (state.isSolidRender(l, p)) return;
        }
        throw new IllegalArgumentException(
                "Foundation deeper than eight blocks at " + floor.toShortString());
    }

    static void tick(ServerLevel l, Worker b, SiteData.Site s) {
        if (s.active() && s.type.equals("logging")) { NaturalTrees.build(l,b,s); return; }
        if (s.paused) {
            b.setStatus("Site construction paused");
            return;
        }
        if (!b.getUUID().equals(s.builder)) {
            b.constructionId = null;
            b.working = false;
            b.setStatus("Construction assigned to another builder");
            return;
        }
        if (!SiteJobs.loaded(l, s)) {
            b.setStatus("Site chunk unloaded");
            return;
        }
        int w = s.width(), d = s.depth();
        try {
            if ("level".equals(s.phase)) {
                if (s.cursor >= w * d) {
                    s.phase = "build";
                    s.cursor = 0;
                    SiteData.get(l).setDirty();
                    return;
                }
                int skipped = 0;
                while (s.cursor < w * d && skipped++ < WorkerConfig.scanBudget()) {
                    int sx = s.cursor % w, sz = s.cursor / w;
                    validateColumn(l,s,sx,sz);
                    BlockPos q = s.at(sx,0,sz);
                    boolean done = l.getBlockState(q).isSolidRender(l,q);
                    for (int sy=1; sy<s.height() && done; sy++) {
                        var state = l.getBlockState(q.above(sy));
                        done = state.isAir() || soft(state);
                    }
                    if (!done) break;
                    s.cursor++;
                    SiteData.get(l).setDirty();
                }
                if (s.cursor >= w*d || skipped > WorkerConfig.scanBudget()) return;
                int x = s.cursor % w, z = s.cursor / w;
                validateColumn(l, s, x, z);
                BlockPos p = s.at(x, 0, z);
                // Clear top down; normal builders mine one block per action with supplied tools.
                for (int y = s.height() - 1; y >= 1; y--) {
                    BlockPos clear = p.above(y);
                    if (!l.getBlockState(clear).isAir()) {
                        if (!ConstructionTools.clear(l, b, s, clear)) return;
                        if (!s.instant) return;
                    }
                }
                if (!s.instant && !b.approachWithin(p.above(), 4, 8)) return;
                int depth = 0;
                while (depth <= 8
                        && !l.getBlockState(p.below(depth)).isSolidRender(l, p.below(depth)))
                    depth++;
                // Build from the supporting ground upward so interrupted columns never hide a void.
                for (int n = depth - 1; n >= 0; n--) {
                    BlockPos q = p.below(n);
                    if (!clearForPlacement(l, b, q, Blocks.DIRT.defaultBlockState())) return;
                    l.setBlock(q, Blocks.DIRT.defaultBlockState(), 3);
                }
                s.cursor++;
                s.status = "Leveling " + s.cursor + "/" + (w * d);
            } else if ("build".equals(s.phase)) {
                var pieces = SiteTemplates.pieces(s);
                int budget = s.instant ? WorkerConfig.scanBudget() : Math.min(6, WorkerConfig.scanBudget());
                while (s.cursor < pieces.size() && budget-- > 0) {
                    var piece = pieces.get(s.cursor);
                    BlockPos p = s.at(piece.x(), piece.y(), piece.z());
                    if (!l.hasChunkAt(p)) {
                        s.status = "Site chunk unloaded";
                        break;
                    }
                    BlockState desired = piece.state().rotate(SiteCatalog.rotation(s.rotation)),
                            current = l.getBlockState(p);
                    if (!matches(current, desired)) {
                        if (current.hasBlockEntity()
                                || !current.getFluidState().isEmpty()
                                || (!current.isAir() && (!terrain(current) || piece.y() > 8)))
                            throw new IllegalArgumentException(
                                    "Protected " + current.getBlock().getName().getString() + " at " + p.toShortString());
                        // Ground surface conversions are placement, not excavation. Solid stone/logs
                        // and raised terrain still require the appropriate tool before replacement.
                        boolean surface = piece.y() <= 0 && (current.is(BlockTags.DIRT)
                                || current.is(Blocks.FARMLAND) || current.is(Blocks.DIRT_PATH));
                        if (!current.isAir() && !soft(current) && !surface) {
                            if (!ConstructionTools.clear(l, b, s, p)) return;
                            if (!s.instant) return;
                        }
                        if (!s.instant && !b.approachWithin(p, 4, 8)) return;
                        if (!clearForPlacement(l, b, p, desired)) return;
                        l.setBlock(p, Block.updateFromNeighbourShapes(desired, l, p), Block.UPDATE_ALL);
                        if (desired.getBlock() instanceof SiteBlocks.ManagedBlock)
                            s.generated.add(p);
                    }
                    s.cursor++;
                    SiteData.get(l).setDirty();
                }
                s.status = "Building " + s.cursor + "/" + pieces.size();
                if (s.cursor >= pieces.size()) {
                    s.phase = "verify";
                    s.cursor = 0;
                }
            } else if ("verify".equals(s.phase)) {
                var pieces = SiteTemplates.pieces(s);
                int budget = WorkerConfig.scanBudget();
                while (s.cursor < pieces.size() && budget-- > 0) {
                    var piece = pieces.get(s.cursor);
                    var p = s.at(piece.x(), piece.y(), piece.z());
                    if (!matches(l.getBlockState(p), piece.state().rotate(SiteCatalog.rotation(s.rotation)))) {
                        s.phase = "build";
                        s.status = "Repairing construction at " + p.toShortString();
                        b.setStatus(s.status);
                        SiteData.get(l).setDirty();
                        return;
                    }
                    // Older jobs placed fences without neighbor updates. Repair their connections
                    // while retaining normal world-maintained properties such as soil moisture.
                    BlockState current = l.getBlockState(p);
                    if (current.getBlock() instanceof FenceBlock || current.getBlock() instanceof WallBlock
                            || current.getBlock() instanceof IronBarsBlock) {
                        BlockState connected = Block.updateFromNeighbourShapes(current, l, p);
                        if (!connected.equals(current)) l.setBlock(p, connected, Block.UPDATE_ALL);
                    }
                    s.cursor++;
                }
                s.status = "Checking construction " + s.cursor + "/" + pieces.size();
                if (s.cursor >= pieces.size()) {
                    if ("logging".equals(s.type)) {
                        s.phase = "plant";
                        s.status = "Preparing mature starter trees";
                    } else finish(l, b, s);
                }
            }
            if ("plant".equals(s.phase) && NaturalTrees.starter(l, s)) finish(l, b, s);
            b.setStatus(s.status);
            SiteData.get(l).setDirty();
        } catch (IllegalArgumentException e) {
            s.status = e.getMessage();
            b.setStatus(s.status);
            b.working = false;
            SiteData.get(l).setDirty();
        }
    }

    private static void finish(ServerLevel l, Worker b, SiteData.Site s) {
        s.phase = "active";
        s.status = "Ready — assign a worker";
        b.constructionId = null;
        b.working = b.siteId != null;
        if ("warehouse".equals(s.type)) WarehouseJobs.initialize(l, s);
    }

    private static boolean clearForPlacement(
            ServerLevel l, Worker builder, BlockPos pos, BlockState desired) {
        var shape = desired.getCollisionShape(l, pos);
        if (shape.isEmpty()) return true;
        var box = shape.bounds().move(pos);
        for (var entity :
                l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
            if (entity == builder) {
                for (BlockPos stand :
                        BlockPos.betweenClosed(pos.offset(-3, 1, -3), pos.offset(3, 1, 3))) {
                    if (!stand.equals(pos.above()) && Worker.canStandAt(l, stand)) {
                        builder.getNavigation()
                                .moveTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5, 1);
                        builder.setStatus("Moving clear of construction");
                        return false;
                    }
                }
            }
            throw new IllegalArgumentException(
                    "Move away from construction at " + pos.toShortString());
        }
        return true;
    }
}
