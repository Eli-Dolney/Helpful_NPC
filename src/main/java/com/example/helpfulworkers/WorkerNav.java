package com.example.helpfulworkers;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Walking for one worker. Approaches a position, notices when a route is stuck and climbs a
 * recovery ladder (repath → alternate stand spot → place a step → hop) before giving up, so the
 * teleport fallback only fires as a last resort. Also carries the worker's current errand (fetch
 * from the supply chest, craft at the table) which is finished before the job target is resumed.
 * Called once per work tick (every second); none of the state here is saved to NBT.
 */
final class WorkerNav {
    /** Teleport fallback: never farther than this sideways / up-down. */
    static final int HOP_MAX_HORIZONTAL = 16;
    static final int HOP_MAX_VERTICAL = 4;
    /** Routes that must fail, and time "Can't reach" must have been visible, before a hop. */
    static final int HOP_MIN_FAILURES = 3;
    static final long HOP_MIN_STUCK_TICKS = 200;
    /** Errands that take longer than this are dropped so a stale fetch cannot pin the worker. */
    static final long ERRAND_TIMEOUT_TICKS = 1200;
    /** Vanilla block reach, squared, measured from the eyes to the block centre. */
    static final double REACH_SQR = 4.5 * 4.5;
    /** Reach to a chest or crafting table. */
    static final double CONTAINER_REACH = 2.5;
    static final double CONTAINER_REACH_Y = 2;
    private static final long UNREACHABLE_TICKS = 2400;

    /** Something to do on arrival at an errand position. */
    interface Arrival { void arrive(ServerLevel level, Worker worker); }

    /** A side trip taken before the job target: where, what to show, and what happens there. */
    static final class Errand {
        final BlockPos pos;
        final String label;
        final long started;
        final Arrival onArrive;

        Errand(BlockPos pos, String label, long started, Arrival onArrive) {
            this.pos = pos.immutable();
            this.label = label;
            this.started = started;
            this.onArrive = onArrive;
        }
    }

    private final Worker worker;

    // Current route
    BlockPos navigationTarget;
    double lastDistance = Double.MAX_VALUE;
    int noProgressTicks;
    int stuckTicks;
    Vec3 lastCheckPos;
    /** Consecutive failed routes; reset whenever any target is reached. */
    int failedRoutes;

    // Stuck-recovery ladder for the target that keeps failing
    BlockPos stuckTarget;
    int rung;
    long cantReachSince;
    BlockPos altStand;

    // Cached stand spot next to a job block
    private BlockPos standTarget;
    private BlockPos standPos;

    Errand errand;

    WorkerNav(Worker worker) {
        this.worker = worker;
    }

    // ---------------------------------------------------------------- approach

    /**
     * Walk until within {@code horizontal} blocks sideways and {@code vertical} blocks up/down of
     * pos. A pending errand is walked first; while it lasts this returns false.
     */
    boolean approachWithin(BlockPos pos, double horizontal, double vertical, String travelStatus) {
        if (tickErrand()) return false;
        return approachRaw(pos, horizontal, vertical, travelStatus);
    }

    /**
     * Approach a job block the way a player would: stand on a free spot beside it (same or one
     * higher) and stop once the block centre is within vanilla reach of the eyes.
     */
    boolean approachStand(BlockPos target) {
        if (tickErrand()) return false;
        if (!worker.level().isLoaded(target)) { worker.setStatusKey(Lang.STATUS_TARGET_CHUNK_UNLOADED); return false; }
        if (worker.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) <= REACH_SQR) {
            arrived(target);
            return true;
        }
        BlockPos stand = standFor(target);
        if (stand == null) return approachRaw(target, 3, 3, null);
        return approachRaw(stand, 1.5, 1.5, "Walking to " + target.toShortString());
    }

    /** True when the worker is already within the given reach of pos (no walking). */
    boolean isWithin(BlockPos pos, double horizontal, double vertical) {
        double dx = worker.getX() - (pos.getX() + 0.5);
        double dz = worker.getZ() - (pos.getZ() + 0.5);
        double dy = Math.abs(pos.getY() + 0.5 - worker.getY());
        return dx * dx + dz * dz <= horizontal * horizontal && dy <= vertical;
    }

    /** Game time of the last purposeful walk step; idle strolling waits a few seconds after it. */
    private long lastApproachTime = Long.MIN_VALUE / 2;
    private static final long STROLL_QUIET_TICKS = 100;

    boolean walkedRecently() {
        return hasErrand() || worker.level().getGameTime() - lastApproachTime < STROLL_QUIET_TICKS;
    }

    private boolean approachRaw(BlockPos pos, double horizontal, double vertical, String travelStatus) {
        Level level = worker.level();
        lastApproachTime = level.getGameTime();
        if (!level.isLoaded(pos)) { worker.setStatusKey(Lang.STATUS_TARGET_CHUNK_UNLOADED); return false; }
        if (!pos.equals(navigationTarget)) resetRoute(pos);
        if (isWithin(pos, horizontal, vertical)) {
            arrived(pos);
            return true;
        }
        double distance = worker.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        boolean improved = distance < lastDistance - 0.5;
        boolean moved = lastCheckPos != null && worker.position().distanceToSqr(lastCheckPos) > 0.25;
        if (improved) { lastDistance = distance; noProgressTicks = 0; stuckTicks = 0; }
        else {
            noProgressTicks++;
            if (!moved) stuckTicks++;
        }
        lastCheckPos = worker.position();
        if (stuckTicks >= 6 || noProgressTicks >= 30) {
            routeFailed(pos);
            return false;
        }
        // Cache the path: only repath when the target changed or navigation finished.
        BlockPos goal = altStand != null && pos.equals(stuckTarget) ? altStand : pos;
        if (worker.getNavigation().isDone() || worker.getNavigation().getPath() == null) {
            worker.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1);
        }
        if (travelStatus != null) worker.setStatus(travelStatus);
        else worker.setStatusKey(Lang.STATUS_TRAVELING_TO, pos.toShortString());
        return false;
    }

    private void arrived(BlockPos pos) {
        worker.getNavigation().stop();
        resetRoute(pos);
        failedRoutes = 0;
        clearLadder();
    }

    void resetRoute(BlockPos pos) {
        navigationTarget = pos;
        stuckTicks = 0;
        noProgressTicks = 0;
        lastDistance = Double.MAX_VALUE;
        lastCheckPos = null;
    }

    /** Forget route progress and failures (role change, resume, new area). */
    void reset() {
        resetRoute(null);
        failedRoutes = 0;
        clearLadder();
        standTarget = null;
        standPos = null;
        errand = null;
    }

    private void clearLadder() {
        stuckTarget = null;
        rung = 0;
        cantReachSince = 0;
        altStand = null;
    }

    // ---------------------------------------------------------------- stuck recovery

    /**
     * One rung per failed route to the same target: repath, then an alternate stand spot, then a
     * placed step (miners/builders with blocks), then the teleport hop. Each rung shows a distinct
     * "Can't reach" status; only after the ladder is exhausted is the target marked unreachable.
     */
    /** Last few route failures (tick, target, where the worker stood, why), for tests and debugging; not saved. */
    final java.util.ArrayDeque<String> debugLog = new java.util.ArrayDeque<>();

    private void routeFailed(BlockPos pos) {
        if (debugLog.size() >= 8) debugLog.removeFirst();
        debugLog.addLast(worker.level().getGameTime() + ": " + pos.toShortString() + " from " + worker.blockPosition().toShortString()
            + (stuckTicks >= 6 ? " stuck" : " no-progress") + " path=" + (worker.getNavigation().getPath() == null ? "none"
                : worker.getNavigation().getPath().getNodeCount() + (worker.getNavigation().getPath().canReach() ? "" : " partial")));
        worker.getNavigation().stop();
        navigationTarget = null;
        failedRoutes++;
        long now = worker.level().getGameTime();
        if (!pos.equals(stuckTarget)) {
            stuckTarget = pos.immutable();
            rung = 0;
            altStand = null;
            cantReachSince = now;
        }
        String where = pos.toShortString();
        while (true) {
            rung++;
            switch (rung) {
                case 1 -> {
                    worker.setStatusKey(Lang.STATUS_CANT_REACH_RETRY, where);
                    return;
                }
                case 2 -> {
                    altStand = findAlternateStand(pos);
                    if (altStand != null) {
                        worker.setStatusKey(Lang.STATUS_CANT_REACH_ANOTHER_WAY, where);
                        return;
                    }
                }
                case 3 -> {
                    if (placeStep(pos)) {
                        worker.setStatusKey(Lang.STATUS_CANT_REACH_PLACING_STEP, where);
                        return;
                    }
                }
                default -> {
                    altStand = null;
                    boolean mayHop = failedRoutes >= HOP_MIN_FAILURES && now - cantReachSince >= HOP_MIN_STUCK_TICKS;
                    if (mayHop && hopNear(pos)) {
                        failedRoutes = 0;
                        worker.unreachable.remove(pos.asLong());
                        clearLadder();
                        worker.setStatusKey(Lang.STATUS_GOT_UNSTUCK, where);
                        return;
                    }
                    worker.unreachable.put(pos.asLong(), now + UNREACHABLE_TICKS);
                    if (pos.equals(worker.target)) worker.target = null;
                    // Stay on the top rung: a caller that insists on this target goes straight to the hop check.
                    rung = 3;
                    worker.setStatusKey(Lang.STATUS_CANT_REACH_ANOTHER_SPOT, where);
                    return;
                }
            }
        }
    }

    /** Nearest free spot among the 8 neighbours of pos at -1..+1 Y that is not where we stand. */
    private BlockPos findAlternateStand(BlockPos pos) {
        Level level = worker.level();
        BlockPos feet = worker.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (p.equals(feet) || p.equals(altStand)) continue;
                    if (!Worker.canStandAt(level, p)) continue;
                    double d = p.distSqr(feet);
                    if (d < bestDistance) { bestDistance = d; best = p.immutable(); }
                }
            }
        }
        return best;
    }

    /**
     * Miners and builders carrying dirt or cobblestone put one block under their feet and step up
     * onto it when the target is above them. Returns false when the role, the inventory or the
     * ground does not allow it.
     */
    private boolean placeStep(BlockPos target) {
        if (!("miner".equals(worker.role) || "builder".equals(worker.role))) return false;
        if (!(worker.level() instanceof ServerLevel level)) return false;
        BlockPos feet = worker.blockPosition();
        if (target.getY() <= feet.getY()) return false;
        BlockState here = level.getBlockState(feet);
        if (!(here.isAir() || here.canBeReplaced()) || !level.getFluidState(feet).isEmpty()) return false;
        if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return false;
        if (!level.getBlockState(feet.above(2)).getCollisionShape(level, feet.above(2)).isEmpty()) return false;
        ItemStack block = ItemStack.EMPTY;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (stack.is(Items.DIRT) || stack.is(Items.COBBLESTONE)) { block = stack; break; }
        }
        if (block.isEmpty()) return false;
        BlockState state = block.is(Items.DIRT) ? Blocks.DIRT.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
        block.shrink(1);
        worker.getNavigation().stop();
        worker.teleportTo(worker.getX(), feet.getY() + 1, worker.getZ());
        level.setBlock(feet, state, Block.UPDATE_ALL);
        return true;
    }

    /**
     * Teleport fallback: only to a safe spot beside pos within 16 blocks sideways and 4 up/down.
     * Counted per worker and in {@link WorkerPerf}.
     */
    boolean hopNear(BlockPos pos) {
        double dx = worker.getX() - (pos.getX() + 0.5);
        double dz = worker.getZ() - (pos.getZ() + 0.5);
        if (dx * dx + dz * dz > HOP_MAX_HORIZONTAL * HOP_MAX_HORIZONTAL) return false;
        if (Math.abs(pos.getY() - worker.getY()) > HOP_MAX_VERTICAL) return false;
        return teleportBeside(pos);
    }

    /** Teleport to the closest safe standing spot beside pos (within 64 blocks); knock-outs and recalls. */
    boolean teleportBeside(BlockPos pos) {
        if (!(worker.level() instanceof ServerLevel level)) return false;
        if (worker.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 64 * 64) return false;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -3, -2), pos.offset(2, 3, 2))) {
            if (p.equals(pos) || p.above().equals(pos)) continue;
            if (!Worker.canStandAt(level, p)) continue;
            double d = p.distSqr(pos);
            if (d < bestDistance) { bestDistance = d; best = p.immutable(); }
        }
        if (best == null) return false;
        worker.getNavigation().stop();
        worker.teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
        worker.hops++;
        WorkerPerf.recordHop();
        return true;
    }

    // ---------------------------------------------------------------- stand spots

    /**
     * Free block beside target (cardinal neighbours first, then diagonals) at the target's Y or one
     * above, with solid ground below; nearest to the worker. Cached per target. Null when the
     * block is enclosed, in which case callers fall back to approaching the block itself.
     */
    BlockPos standFor(BlockPos target) {
        Level level = worker.level();
        if (target.equals(standTarget) && standPos != null && Worker.canStandAt(level, standPos)) return standPos;
        standTarget = target.immutable();
        standPos = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = 0; dy <= 1; dy++) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos p = target.relative(dir).above(dy);
                if (!Worker.canStandAt(level, p)) continue;
                double d = worker.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                if (d < bestDistance) { bestDistance = d; standPos = p.immutable(); }
            }
            if (standPos != null) return standPos;
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    BlockPos p = target.offset(dx, dy, dz);
                    if (!Worker.canStandAt(level, p)) continue;
                    double d = worker.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                    if (d < bestDistance) { bestDistance = d; standPos = p.immutable(); }
                }
            }
            if (standPos != null) return standPos;
        }
        return null;
    }

    /**
     * Standing where a block is about to go: walk to the nearest free spot within 2 blocks and
     * come back next tick. Teleports up only when there is nowhere to step. True once the worker
     * is clear of the block.
     */
    boolean stepAside(BlockPos block) {
        if (!worker.getBoundingBox().intersects(new AABB(block))) return true;
        Level level = worker.level();
        BlockPos feet = worker.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 1, 2))) {
            if (p.equals(feet) || p.equals(block) || p.above().equals(block)) continue;
            if (!Worker.canStandAt(level, p)) continue;
            double d = p.distSqr(feet);
            if (d < bestDistance) { bestDistance = d; best = p.immutable(); }
        }
        if (best != null) {
            worker.getNavigation().moveTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, 1);
            worker.setStatusKey(Lang.STATUS_STEPPING_ASIDE, block.toShortString());
            return false;
        }
        BlockPos standOn = block.above();
        while (!Worker.canStandAt(level, standOn) && standOn.getY() < block.getY() + 8) standOn = standOn.above();
        worker.getNavigation().stop();
        worker.teleportTo(worker.getX(), standOn.getY(), worker.getZ());
        worker.hops++;
        WorkerPerf.recordHop();
        return true;
    }

    // ---------------------------------------------------------------- errands

    boolean hasErrand() { return errand != null; }

    void cancelErrand() { errand = null; }

    /** Start a side trip unless one is already pending. The label becomes the status while walking. */
    boolean requestErrand(BlockPos pos, String label, Arrival onArrive) {
        if (errand != null) return false;
        if (!(worker.level() instanceof ServerLevel level)) return false;
        errand = new Errand(pos, label, level.getGameTime(), onArrive);
        worker.setStatus(label);
        return true;
    }

    /**
     * Walk the pending errand. True when this tick went into it (walking, or just arrived and
     * acted, so the caller should retry next tick); false when there is nothing pending.
     */
    boolean tickErrand() {
        if (errand == null) return false;
        if (!(worker.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        if (!level.isLoaded(errand.pos) || now - errand.started > ERRAND_TIMEOUT_TICKS) {
            errand = null;
            return false;
        }
        if (worker.isUnreachable(errand.pos)) {
            Errand dropped = errand;
            errand = null;
            worker.setStatusKey(Lang.STATUS_CANT_REACH_SKIPPED, dropped.pos.toShortString(), dropped.label.toLowerCase());
            return false;
        }
        if (!approachRaw(errand.pos, CONTAINER_REACH, CONTAINER_REACH_Y, errand.label)) return true;
        Errand done = errand;
        errand = null;
        done.onArrive.arrive(level, worker);
        return true;
    }

    /** Matching stacks from chest into the worker's bag, up to {@code grab} items. Returns how many moved. */
    static int pull(Worker worker, net.minecraft.world.Container chest, Predicate<ItemStack> match, int grab) {
        int moved = 0;
        for (int i = 0; i < chest.getContainerSize() && moved < grab; i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.isEmpty() || !match.test(stack)) continue;
            ItemStack taking = stack.copyWithCount(Math.min(stack.getCount(), grab - moved));
            ItemStack rest = worker.bag.addItem(taking);
            int n = taking.getCount() - rest.getCount();
            if (n == 0) break; // bag full
            stack.shrink(n);
            moved += n;
        }
        if (moved > 0) chest.setChanged();
        return moved;
    }
}
