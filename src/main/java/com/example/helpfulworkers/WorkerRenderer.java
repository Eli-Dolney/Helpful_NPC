package com.example.helpfulworkers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = HelpfulWorkers.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WorkerRenderer extends HumanoidMobRenderer<Worker, PlayerModel<Worker>> {
    /** Overhead status is drawn within this distance (squared) so a crowd stays readable. */

    private final PlayerModel<Worker> wideModel;
    private final PlayerModel<Worker> slimModel;

    public WorkerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wideModel = this.getModel();
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new WorkerOutfitLayer(this));
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

    /** Name on top, then a colour-coded "Role: what I'm doing" line beneath it. */
    @Override
    protected void renderNameTag(Worker worker, Component displayName, PoseStack poseStack,
                                 MultiBufferSource buffer, int packedLight, float partialTick) {
        super.renderNameTag(worker, displayName, poseStack, buffer, packedLight, partialTick);
        if (!WorkerConfig.showOverheadStatus()) return;
        double distance = this.entityRenderDispatcher.distanceToSqr(worker);
        double range = WorkerConfig.overheadRange();
        if (distance > range * range || worker.isDiscrete()) return;
        String status = worker.getEntityData().get(Worker.DATA_STATUS);
        String role = worker.getEntityData().get(Worker.DATA_ROLE);
        if (status == null || status.isEmpty()) return;
        int level = worker.getEntityData().get(Worker.DATA_LEVEL);
        String lv = level > 1 ? Component.translatable(Lang.LEVEL_SHORT, level).getString() + " " : "";
        String line = ("idle".equals(role) || role == null ? ""
            : lv + Component.translatable(Lang.roleKey(role)).getString() + ": ") + StatusText.overheadLine(status);
        int color = switch (worker.getEntityData().get(Worker.DATA_STATE)) {
            case Worker.STATE_WORKING -> 0xFF7CE07C;
            case Worker.STATE_BLOCKED -> 0xFFFFC94D;
            case Worker.STATE_SLEEPING -> 0xFF9DA8FF;
            default -> 0xFFB8B8B8;
        };
        Vec3 attach = worker.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, worker.getViewYRot(partialTick));
        if (attach == null) return;
        poseStack.pushPose();
        poseStack.translate(attach.x, attach.y + 0.5, attach.z);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.025f, -0.025f, 0.025f);
        Matrix4f matrix = poseStack.last().pose();
        Font font = getFont();
        float x = -font.width(line) / 2.0f;
        int y = 10; // one text line below the name
        float opacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25f);
        int background = (int) (opacity * 255.0f) << 24;
        font.drawInBatch(line, x, y, color, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, background, packedLight);
        font.drawInBatch(line, x, y, color, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, packedLight);
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HelpfulWorkers.WORKER.get(), WorkerRenderer::new);
    }
}
