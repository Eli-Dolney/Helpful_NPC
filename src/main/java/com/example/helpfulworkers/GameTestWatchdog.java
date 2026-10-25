package com.example.helpfulworkers;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-test server only. Vanilla forces just the chunks under each test plot: a plot at the edge
 * of the grid can then get no path for a walking worker ("path=none"), and a test whose chunks did
 * not become entity ticking never starts and blocks the run without a timeout. This keeps a
 * one-chunk margin forced around every test plot and logs tests that are still waiting for chunks.
 * The runner unforces all forced chunks at the end of each batch.
 */
@EventBusSubscriber(modid = HelpfulWorkers.ID)
final class GameTestWatchdog {
    private static final Set<GameTestInfo> MARGINED = Collections.newSetFromMap(new WeakHashMap<>());

    private GameTestWatchdog() {}

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (!(event.getServer() instanceof GameTestServer)) return;
        boolean report = event.getServer().getTickCount() % 100 == 0;
        for (GameTestInfo info : infos()) {
            if (info.isDone() || info.getStructureBlockEntity() == null) continue;
            AABB box = info.getStructureBounds();
            ServerLevel level = info.getLevel();
            int minX = ((int) Math.floor(box.minX)) >> 4, maxX = ((int) Math.floor(box.maxX)) >> 4;
            int minZ = ((int) Math.floor(box.minZ)) >> 4, maxZ = ((int) Math.floor(box.maxZ)) >> 4;
            if (MARGINED.add(info)) {
                for (int cx = minX - 1; cx <= maxX + 1; cx++)
                    for (int cz = minZ - 1; cz <= maxZ + 1; cz++) level.setChunkForced(cx, cz, true);
            }
            if (!report || info.hasStarted()) continue;
            int waiting = 0;
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    if (level.isPositionEntityTicking(new ChunkPos(cx, cz).getWorldPosition())) continue;
                    waiting++;
                    level.setChunkForced(cx, cz, true);
                }
            }
            if (waiting > 0) HelpfulWorkers.LOGGER.warn("Game test {} waits for {} chunks; forcing them again", info.getTestName(), waiting);
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<GameTestInfo> infos() {
        try {
            var field = GameTestTicker.class.getDeclaredField("testInfos");
            field.setAccessible(true);
            return (Collection<GameTestInfo>) field.get(GameTestTicker.SINGLETON);
        } catch (ReflectiveOperationException ex) {
            return java.util.List.of();
        }
    }
}
