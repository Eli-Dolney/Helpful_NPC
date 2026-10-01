package com.example.helpfulworkers;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = HelpfulWorkers.ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
final class AreaOutlineClient {
    private static WorkerNetwork.AreaOutlinePayload outline;
    private static int ticksLeft;

    private AreaOutlineClient() {}

    static void show(WorkerNetwork.AreaOutlinePayload payload) {
        outline = payload;
        ticksLeft = Math.max(40, payload.ticks());
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (outline == null || ticksLeft <= 0) {
            outline = null;
            return;
        }
        ticksLeft--;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !outline.hasArea()) return;
        if (ticksLeft % 4 != 0) return;
        drawBox(mc, outline.minX(), outline.minY(), outline.minZ(), outline.maxX(), outline.maxY(), outline.maxZ());
        marker(mc, outline.bed(), 0.2f, 0.6f, 1f);
        marker(mc, outline.supply(), 1f, 0.85f, 0.2f);
        marker(mc, outline.output(), 0.3f, 1f, 0.3f);
        marker(mc, outline.table(), 0.9f, 0.5f, 0.2f);
    }

    private static void drawBox(Minecraft mc, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.2f, 0.85f, 1f), 1f);
        // Vertical edges
        for (int[] corner : new int[][]{{minX, minZ}, {minX, maxZ + 1}, {maxX + 1, minZ}, {maxX + 1, maxZ + 1}}) {
            for (int y = minY; y <= maxY + 1; y += Math.max(1, (maxY - minY + 2) / 8)) {
                mc.level.addParticle(dust, corner[0], y, corner[1], 0, 0, 0);
            }
        }
        // Top and bottom rings
        for (int x = minX; x <= maxX + 1; x++) {
            mc.level.addParticle(dust, x, minY, minZ, 0, 0, 0);
            mc.level.addParticle(dust, x, minY, maxZ + 1, 0, 0, 0);
            mc.level.addParticle(dust, x, maxY + 1, minZ, 0, 0, 0);
            mc.level.addParticle(dust, x, maxY + 1, maxZ + 1, 0, 0, 0);
        }
        for (int z = minZ; z <= maxZ + 1; z++) {
            mc.level.addParticle(dust, minX, minY, z, 0, 0, 0);
            mc.level.addParticle(dust, maxX + 1, minY, z, 0, 0, 0);
            mc.level.addParticle(dust, minX, maxY + 1, z, 0, 0, 0);
            mc.level.addParticle(dust, maxX + 1, maxY + 1, z, 0, 0, 0);
        }
        // Floor grid
        for (int x = minX; x <= maxX; x += Math.max(1, (maxX - minX + 1) / 6)) {
            for (int z = minZ; z <= maxZ; z += Math.max(1, (maxZ - minZ + 1) / 6)) {
                mc.level.addParticle(ParticleTypes.END_ROD, x + 0.5, minY + 0.1, z + 0.5, 0, 0.01, 0);
            }
        }
    }

    private static void marker(Minecraft mc, long packed, float r, float g, float b) {
        if (packed == Long.MIN_VALUE) return;
        BlockPos pos = BlockPos.of(packed);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), 1.2f);
        mc.level.addParticle(dust, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0, 0.02, 0);
    }
}
