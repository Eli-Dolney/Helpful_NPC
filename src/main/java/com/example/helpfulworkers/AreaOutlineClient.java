package com.example.helpfulworkers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws a worker's work area as a thin line box, with coloured boxes around its bed (blue),
 * supply (yellow), output (green) and crafting table (orange). Shown for the configured time
 * after "Show my work area", and kept up while the owner sneaks with the clipboard in hand.
 */
@EventBusSubscriber(modid = HelpfulWorkers.ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
final class AreaOutlineClient {
    private static WorkerNetwork.AreaOutlinePayload outline;
    private static int ticksLeft;

    private AreaOutlineClient() {}

    static void show(WorkerNetwork.AreaOutlinePayload payload) {
        outline = payload;
        // Full "show area" requests use the configured time; short previews (depth picker) keep theirs.
        int ticks = payload.ticks() >= 200 ? WorkerConfig.outlineSeconds() * 20 : payload.ticks();
        ticksLeft = Math.max(40, ticks);
    }

    private static boolean heldOpen(Minecraft mc) {
        return mc.player != null && mc.player.isShiftKeyDown()
            && (mc.player.getMainHandItem().is(HelpfulWorkers.CLIPBOARD.get())
                || mc.player.getOffhandItem().is(HelpfulWorkers.CLIPBOARD.get()));
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (outline == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (heldOpen(mc)) return; // sneaking with the clipboard keeps the outline up
        if (--ticksLeft <= 0) outline = null;
    }

    @SubscribeEvent
    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        WorkerNetwork.AreaOutlinePayload o = outline;
        Minecraft mc = Minecraft.getInstance();
        if (o == null || mc.level == null) return;
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        if (o.hasArea()) {
            AABB area = new AABB(o.minX(), o.minY(), o.minZ(), o.maxX() + 1, o.maxY() + 1, o.maxZ() + 1);
            LevelRenderer.renderLineBox(pose, lines, area, 0.25f, 0.85f, 1.0f, 1.0f);
            LevelRenderer.renderLineBox(pose, lines, area.inflate(0.03), 0.1f, 0.45f, 0.6f, 0.6f);
        }
        marker(pose, lines, o.bed(), 0.35f, 0.55f, 1.0f);
        marker(pose, lines, o.supply(), 1.0f, 0.85f, 0.2f);
        marker(pose, lines, o.output(), 0.35f, 1.0f, 0.35f);
        marker(pose, lines, o.table(), 1.0f, 0.55f, 0.2f);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /** A box hugging the block plus a short post above it, so the marker reads from a distance. */
    private static void marker(PoseStack pose, VertexConsumer lines, long packed, float r, float g, float b) {
        if (packed == Long.MIN_VALUE) return;
        BlockPos pos = BlockPos.of(packed);
        LevelRenderer.renderLineBox(pose, lines, new AABB(pos).inflate(0.02), r, g, b, 1.0f);
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        LevelRenderer.renderLineBox(pose, lines, new AABB(cx - 0.08, pos.getY() + 1.1, cz - 0.08, cx + 0.08, pos.getY() + 1.7, cz + 0.08),
            r, g, b, 1.0f);
    }
}
