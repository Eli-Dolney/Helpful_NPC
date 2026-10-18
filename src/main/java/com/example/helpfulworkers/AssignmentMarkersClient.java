package com.example.helpfulworkers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.nio.file.*;

@EventBusSubscriber(modid=HelpfulWorkers.ID,value=Dist.CLIENT)
final class AssignmentMarkersClient {
    static AssignmentMarkers.Snapshot snapshot;
    static int mode=load();
    static Path settings() { return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("helpfulworkers-markers.txt"); }
    static int load() { try{return Math.floorMod(Integer.parseInt(Files.readString(settings()).trim()),3);}catch(Exception e){return 0;} }
    static String label() { return new String[]{"Nearby","Clipboard only","Off"}[mode]; }
    static void cycle() { mode=(mode+1)%3; try{Files.writeString(settings(),Integer.toString(mode));}catch(Exception e){HelpfulWorkers.LOGGER.warn("Cannot save marker preference",e);} }
    static void accept(AssignmentMarkers.Snapshot s) { snapshot=s; }
    @SubscribeEvent static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || snapshot==null || mode==2)return;
        var mc=Minecraft.getInstance(); if(mc.level==null || mc.player==null || !snapshot.dimension().equals(mc.level.dimension().location().toString()))return;
        if(mode==1 && !mc.player.getMainHandItem().is(HelpfulWorkers.CLIPBOARD.get()) && !mc.player.getOffhandItem().is(HelpfulWorkers.CLIPBOARD.get()))return;
        var camera=e.getCamera(); var pose=e.getPoseStack(); var buffers=mc.renderBuffers().bufferSource();
        for(var m:snapshot.markers()) {
            var center=m.pos().getCenter().add(0,1.1+(m.kind()==2?.35:0),0);
            if(camera.getPosition().distanceToSqr(center)>1024)continue;
            var hit=mc.level.clip(new ClipContext(camera.getPosition(),center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()!=HitResult.Type.MISS && !hit.getBlockPos().equals(m.pos()))continue;
            pose.pushPose(); pose.translate(center.x-camera.getPosition().x,center.y-camera.getPosition().y,center.z-camera.getPosition().z);
            pose.mulPose(camera.rotation());
            pose.pushPose(); pose.scale(.3f,.3f,.3f);
            mc.getItemRenderer().renderStatic(new ItemStack(m.kind()==0?Items.RED_BED: m.kind()==1?Items.CHEST:Items.HOPPER),ItemDisplayContext.FIXED,15728880,OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,0);
            pose.popPose(); pose.translate(0,-.22,0); pose.scale(-.018f,-.018f,.018f);
            String label=new String[]{"Bed: ","Supplies: ","Output: "}[Math.floorMod(m.kind(),3)]+m.name();
            mc.font.drawInBatch(label,-mc.font.width(label)/2f,0,0xffeef5ff,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0x80101820,15728880);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
