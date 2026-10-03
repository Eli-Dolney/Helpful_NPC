package com.example.helpfulworkers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

final class SiteEvents {
    static void register() {
        NeoForge.EVENT_BUS.addListener(SiteEvents::breakBlock);
        NeoForge.EVENT_BUS.addListener(NaturalTrees::grow);
        NeoForge.EVENT_BUS.addListener(NaturalTrees::placed);
        NeoForge.EVENT_BUS.addListener(SiteEvents::interact);
        NeoForge.EVENT_BUS.addListener(SiteEvents::rightBlock);
        NeoForge.EVENT_BUS.addListener(SiteEvents::logout);
        NeoForge.EVENT_BUS.addListener(SiteEvents::leave);
        NeoForge.EVENT_BUS.addListener(SiteEvents::stop);
        NeoForge.EVENT_BUS.addListener(SiteEvents::playerTick);
    }

    static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p && p.tickCount % 20 == 0) {
            AssignmentMarkers.send(p);
            SiteTemplates.tick(p);
            var s = SiteUi.SESSIONS.get(p.getUUID());
            if (s != null && !SiteUi.valid(p, s)) {
                SiteUi.close(p);
                SiteNetwork.send(p, new SiteNetwork.Menu(-1, "", "", java.util.List.of()));
            } else if (s != null && s.preview != null && s.pending.equals("preview")) {
                SiteUi.preview(p, s.preview);
            }
        }
    }

    static void breakBlock(BlockEvent.BreakEvent e) {
        if (SiteBlocks.protectedBlock(e.getState())) {
            e.setCanceled(true);
            if (e.getPlayer() instanceof ServerPlayer p)
                SiteUi.tell(
                        p,
                        "Managed site resource — assign a worker, or dismantle through the core.");
        }
    }

    static void rightBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            if (SiteUi.click(p, e.getPos())) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.CONSUME);
            } else if (p.level().getBlockState(e.getPos()).is(SiteBlocks.CORE.get())) {
                SiteUi.openCore(p, e.getPos());
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.CONSUME);
            }
        }
    }

    static void interact(PlayerInteractEvent.EntityInteract e) {
        if (e.getEntity() instanceof ServerPlayer p
                && e.getTarget() instanceof Worker w
                && SiteUi.clickWorker(p, w)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) SiteUi.close(p);
    }

    static void leave(EntityLeaveLevelEvent e) {
        if (e.getEntity() instanceof Worker w) {
            WarehouseJobs.release(w.getUUID());
            SiteNavigation.clear(w.getUUID());
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel l
                    && w.siteId != null) {
                var site = SiteData.get(l).sites.get(w.siteId);
                if (site != null) {
                    site.lastActive = -1;
                    if (!w.isAlive()) {
                        if (w.getUUID().equals(site.worker)) site.worker = null;
                        site.couriers.remove(w.getUUID());
                        SiteData.get(l).setDirty();
                    }
                }
            }
        }
    }

    static void stop(ServerStoppedEvent e) {
        SiteUi.SESSIONS.clear();
        WarehouseJobs.clear();
    }
}
