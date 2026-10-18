package com.example.helpfulworkers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws a per-role outfit (hat, apron, vest) over the worker's player skin. The outfit textures
 * use the skin's outer-layer regions, so they sit a little proud of the body like a jacket. A role
 * without a texture draws nothing, and the lookup is cached per role.
 */
@OnlyIn(Dist.CLIENT)
final class WorkerOutfitLayer extends RenderLayer<Worker, PlayerModel<Worker>> {
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    WorkerOutfitLayer(RenderLayerParent<Worker, PlayerModel<Worker>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Worker worker,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (worker.isInvisible()) return;
        String role = worker.getEntityData().get(Worker.DATA_ROLE);
        ResourceLocation texture = textureFor(role);
        if (texture == null) return;
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(texture));
        getParentModel().renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
    }

    private static ResourceLocation textureFor(String role) {
        if (role == null || role.isEmpty() || "idle".equals(role)) return null;
        return TEXTURES.computeIfAbsent(role, r -> {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID, "textures/entity/outfit_" + r + ".png");
            return Minecraft.getInstance().getResourceManager().getResource(loc).isPresent() ? loc : null;
        });
    }
}
