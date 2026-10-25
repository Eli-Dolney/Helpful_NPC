package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Makes block work take time the way it does for a player: the worker faces the block, swings its
 * arm, the block shows cracks and plays hit sounds, and only after a tool-dependent duration may
 * the job break it. {@link #ready} is asked by job code once per work tick; {@link #tick} advances
 * the animation every game tick. Nothing here is saved; an interrupted block just starts over.
 */
final class WorkAnimator {
    /** Shortest and longest time a block takes, in game ticks (0.5 s .. 3 s) before the pace factor. */
    static final int MIN_TICKS = 10;
    static final int MAX_TICKS = 60;
    private static final int SWING_EVERY = 6;
    private static final int HIT_SOUND_EVERY = 4;

    private final Worker worker;
    BlockPos pos;
    int progress;
    int needed;
    private int lastStage = -1;

    WorkAnimator(Worker worker) {
        this.worker = worker;
    }

    /** True once {@code pos} has been worked long enough to break. Starts the animation on first ask. */
    boolean ready(ServerLevel level, BlockPos pos) {
        if (!pos.equals(this.pos)) {
            start(level, pos);
            return false;
        }
        return progress >= needed;
    }

    boolean isWorking(BlockPos pos) {
        return pos != null && pos.equals(this.pos);
    }

    private void start(ServerLevel level, BlockPos pos) {
        clear(level);
        this.pos = pos.immutable();
        this.progress = 0;
        this.needed = duration(level, worker, pos);
        this.lastStage = -1;
        worker.swing(InteractionHand.MAIN_HAND, true);
    }

    /** Advance the swing / crack animation; called from the entity tick on the server. */
    void tick(ServerLevel level) {
        if (pos == null) return;
        if (!level.isLoaded(pos)) { clear(level); return; }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) { clear(level); return; }
        if (progress >= needed) return; // waiting for the job tick to break it
        progress++;
        worker.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        if (progress % SWING_EVERY == 1) worker.swing(InteractionHand.MAIN_HAND, true);
        if (progress % HIT_SOUND_EVERY == 0) {
            var sound = state.getSoundType();
            level.playSound(null, pos, sound.getHitSound(), SoundSource.NEUTRAL,
                (sound.getVolume() + 1.0f) / 8.0f, sound.getPitch() * 0.5f);
        }
        int stage = Math.min(9, (int) (progress * 10f / Math.max(1, needed)));
        if (stage != lastStage) {
            level.destroyBlockProgress(worker.getId(), pos, stage);
            lastStage = stage;
        }
    }

    /** Stop animating (block broke, target changed, job paused). */
    void clear(ServerLevel level) {
        if (pos != null && lastStage >= 0) level.destroyBlockProgress(worker.getId(), pos, -1);
        pos = null;
        progress = 0;
        needed = 0;
        lastStage = -1;
    }

    /** Game ticks the block at pos takes with the worker's current tool, scaled by the configured pace. */
    static int duration(ServerLevel level, Worker worker, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0) return paced(MIN_TICKS);
        ItemStack tool = worker.getMainHandItem();
        float speed = tool.isEmpty() ? 1.0f : Math.max(1.0f, tool.getDestroySpeed(state));
        boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        float damagePerTick = speed / hardness / (correct ? 30f : 100f);
        int ticks = damagePerTick <= 0 ? MAX_TICKS : Mth.ceil(1.0f / damagePerTick);
        return paced(ticks, worker.workerLevel());
    }

    /** Each level above 1 works 8% faster. */
    static float levelSpeed(int level) {
        return 1.0f - 0.08f * (Math.max(1, level) - 1);
    }

    private static int paced(int ticks) {
        return paced(ticks, 1);
    }

    private static int paced(int ticks, int level) {
        int scaled = Math.round(ticks * (float) WorkerConfig.workPace() * levelSpeed(level));
        return Mth.clamp(scaled, MIN_TICKS, MAX_TICKS);
    }
}
