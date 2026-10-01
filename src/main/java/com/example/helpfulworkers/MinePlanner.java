package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

/** Builds dig/place step lists for staircase, strip, and colony mines. */
final class MinePlanner {
    private MinePlanner() {}

    enum Kind { DIG, PLACE_TORCH, PLACE_LADDER, PLACE_COBBLE }

    record Step(BlockPos pos, Kind kind) {}

    static List<Step> build(Worker worker) {
        if (worker.first == null || worker.second == null) return List.of();
        return switch (worker.mode) {
            case "staircase" -> staircase(worker);
            case "strip" -> strip(worker);
            case "colony" -> colony(worker);
            default -> List.of();
        };
    }

    private static Direction facing(Worker worker) {
        int dx = Integer.signum(worker.second.getX() - worker.first.getX());
        int dz = Integer.signum(worker.second.getZ() - worker.first.getZ());
        if (Math.abs(worker.second.getX() - worker.first.getX()) >= Math.abs(worker.second.getZ() - worker.first.getZ())) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static List<Step> staircase(Worker worker) {
        List<Step> steps = new ArrayList<>();
        Direction dir = facing(worker);
        BlockPos pos = worker.first.immutable();
        int y = pos.getY();
        int target = Math.max(worker.targetY, worker.level().getMinBuildHeight() + 1);
        int step = 0;
        while (y > target) {
            BlockPos foot = new BlockPos(pos.getX(), y, pos.getZ());
            steps.add(new Step(foot, Kind.DIG));
            steps.add(new Step(foot.above(), Kind.DIG));
            steps.add(new Step(foot.above(2), Kind.DIG));
            if (step % 8 == 0) steps.add(new Step(foot.relative(dir.getClockWise()).above(), Kind.PLACE_TORCH));
            pos = pos.relative(dir);
            y--;
            step++;
        }
        return steps;
    }

    private static List<Step> strip(Worker worker) {
        List<Step> steps = new ArrayList<>(staircase(worker));
        Direction dir = facing(worker);
        Direction side = dir.getClockWise();
        BlockPos start = worker.first.atY(worker.targetY);
        int length = Math.max(8, Math.max(
            Math.abs(worker.second.getX() - worker.first.getX()),
            Math.abs(worker.second.getZ() - worker.first.getZ())));
        for (int i = 0; i < length; i++) {
            BlockPos here = start.relative(dir, i);
            steps.add(new Step(here, Kind.DIG));
            steps.add(new Step(here.above(), Kind.DIG));
            if (i % 8 == 0) steps.add(new Step(here.relative(side).above(), Kind.PLACE_TORCH));
            if (i % 3 == 0) {
                for (int b = 1; b <= 5; b++) {
                    BlockPos branch = here.relative(side, b);
                    steps.add(new Step(branch, Kind.DIG));
                    steps.add(new Step(branch.above(), Kind.DIG));
                    BlockPos other = here.relative(side.getOpposite(), b);
                    steps.add(new Step(other, Kind.DIG));
                    steps.add(new Step(other.above(), Kind.DIG));
                }
            }
        }
        return steps;
    }

    private static List<Step> colony(Worker worker) {
        List<Step> steps = new ArrayList<>();
        BlockPos origin = worker.first.immutable();
        int target = Math.max(worker.targetY, worker.level().getMinBuildHeight() + 1);
        // 3x3 shaft with ladder on center-north
        for (int y = origin.getY(); y >= target; y--) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                steps.add(new Step(p, Kind.DIG));
            }
            steps.add(new Step(new BlockPos(origin.getX(), y, origin.getZ() - 1), Kind.PLACE_LADDER));
            if ((origin.getY() - y) % 8 == 0) {
                steps.add(new Step(new BlockPos(origin.getX() + 1, y, origin.getZ() + 1), Kind.PLACE_TORCH));
            }
        }
        // Levels every 6 blocks
        for (int y = origin.getY() - 6; y >= target; y -= 6) {
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                for (int i = 2; i <= 16; i++) {
                    BlockPos p = new BlockPos(origin.getX(), y, origin.getZ()).relative(dir, i);
                    steps.add(new Step(p, Kind.DIG));
                    steps.add(new Step(p.above(), Kind.DIG));
                    if (i % 8 == 0) steps.add(new Step(p.relative(dir.getClockWise()).above(), Kind.PLACE_TORCH));
                    if (i % 4 == 0) {
                        Direction side = dir.getClockWise();
                        for (int b = 1; b <= 4; b++) {
                            BlockPos branch = p.relative(side, b);
                            steps.add(new Step(branch, Kind.DIG));
                            steps.add(new Step(branch.above(), Kind.DIG));
                        }
                    }
                }
            }
        }
        return steps;
    }

    static boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.IRON_ORES)
            || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)
            || state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.REDSTONE_ORES)
            || state.is(Blocks.NETHER_QUARTZ_ORE) || state.is(Blocks.NETHER_GOLD_ORE) || state.is(Blocks.ANCIENT_DEBRIS);
    }

    /** Ore touching open air within arm's reach of the worker; skips the block it stands on. */
    static BlockPos findReachableOre(ServerLevel level, Worker worker) {
        BlockPos feet = worker.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-3, -1, -3), feet.offset(3, 3, 3))) {
            if (pos.equals(feet.below())) continue;
            if (!level.isLoaded(pos) || !isOre(level.getBlockState(pos))) continue;
            if (worker.isUnreachable(pos)) continue;
            double d = worker.getEyePosition().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (d > 4.5 * 4.5 || d >= bestDist) continue;
            if (!exposed(level, pos)) continue;
            bestDist = d;
            best = pos.immutable();
        }
        return best;
    }

    private static boolean exposed(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            if (level.isLoaded(n) && level.getBlockState(n).isAir()) return true;
        }
        return false;
    }
}
