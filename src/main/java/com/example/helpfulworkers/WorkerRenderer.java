package com.example.helpfulworkers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = HelpfulWorkers.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WorkerRenderer extends HumanoidMobRenderer<Worker, PlayerModel<Worker>> {
    private final PlayerModel<Worker> wideModel;
    private final PlayerModel<Worker> slimModel;

    public WorkerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wideModel = this.getModel();
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this,
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(Worker worker) {
        return DefaultPlayerSkin.get(worker.getUUID()).texture();
    }

    @Override
    public void render(Worker worker, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        PlayerSkin skin = DefaultPlayerSkin.get(worker.getUUID());
        this.model = skin.model() == PlayerSkin.Model.SLIM ? slimModel : wideModel;
        super.render(worker, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HelpfulWorkers.WORKER.get(), WorkerRenderer::new);
    }
}
