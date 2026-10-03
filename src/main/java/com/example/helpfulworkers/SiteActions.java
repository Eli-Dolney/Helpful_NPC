package com.example.helpfulworkers;

import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

final class SiteActions {
    static boolean accepts(SiteData.Site s, Worker w) {
        return s.owner.equals(w.owner)
                && s.active()
                && SiteCatalog.roles(s.type).contains(w.role);
    }

    static boolean canRegister(ServerLevel l, UUID owner, net.minecraft.core.BlockPos pos) {
        for (var site : SiteData.get(l).sites.values())
            if (!site.owner.equals(owner)) {
                int w =
                        site.rotation % 2 == 0
                                ? site.width()
                                : site.depth();
                int d =
                        site.rotation % 2 == 0
                                ? site.depth()
                                : site.width();
                if (pos.getX() >= site.origin.getX()
                        && pos.getX() < site.origin.getX() + w
                        && pos.getZ() >= site.origin.getZ()
                        && pos.getZ() < site.origin.getZ() + d) return false;
                if (site.storage.stream().anyMatch(st -> st.pos.equals(pos))) return false;
            }
        return true;
    }

    static void construct(ServerPlayer p, Worker w, SiteUi.Session session) {
        if (w.constructionId != null)
            throw new IllegalArgumentException(
                    "Finish or dismantle the existing construction job first");
        var s =
                SiteConstruction.plan(
                        p,
                        w,
                        session.type,
                        session.first,
                        session.second,
                        session.rotation,
                        session.floor);
        int slot = -1;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(SiteBlocks.coreItem(s.type))) {
                slot = i;
                break;
            }
        if (slot < 0 && !p.getAbilities().instabuild)
            throw new IllegalArgumentException(
                    "Craft and carry the matching " + SiteCatalog.label(s.type) + " core");
        if (slot >= 0 && !p.getAbilities().instabuild) p.getInventory().removeItem(slot, 1);
        s.paid = !p.getAbilities().instabuild;
        SiteData.get(p.serverLevel()).add(s);
        if (w.siteId == null || !"builder".equals(SiteData.get(p.serverLevel()).sites.get(w.siteId).type)) release(p.serverLevel(), w);
        w.constructionId = s.id;
        w.working = true;
        w.nextWorkTick = 0;
        w.idleUntil = 0;
        w.target = null;
        SiteUi.tell(p, "Builder started " + SiteCatalog.label(s.type));
    }

    static void assign(ServerPlayer p, Worker w, SiteData.Site s) {
        if (!w.isAlive()
                || w.level() != p.level()
                || !w.owns(p)
                || !s.owner.equals(p.getUUID())
                || !accepts(s, w))
            throw new IllegalArgumentException("Worker role does not match this site");
        if (w.constructionId != null)
            throw new IllegalArgumentException("Finish construction first");
        if (!s.type.equals("warehouse") && s.worker != null && !s.worker.equals(w.getUUID()))
            throw new IllegalArgumentException("Release the assigned worker first");
        if (w.siteId != null && !w.siteId.equals(s.id))
            throw new IllegalArgumentException("Release the current site before reassigning");
        if (w.siteId == null && w.working && w.first != null)
            throw new IllegalArgumentException("Pause existing zone work before assigning a site");
        release(p.serverLevel(), w);
        w.siteId = s.id;
        w.first = s.origin;
        w.second = s.origin.offset((s.rotation%2==0?s.width():s.depth())-1,s.height()-1,(s.rotation%2==0?s.depth():s.width())-1);
        w.output = s.output();
        w.target = null;
        w.idleUntil = 0;
        w.nextWorkTick = 0;
        w.working = true;
        if (StorageOps.at(p.serverLevel(),s.supply()) != null) w.supply = s.supply();
        if (s.type.equals("smelter")) {
            w.furnaces.clear();
            for (var piece : SiteTemplates.pieces(s))
                if (piece.state().is(Blocks.FURNACE)) w.furnaces.add(s.at(piece.x(),piece.y(),piece.z()));
        }
        if (s.type.equals("builder")) w.craftingTable = s.at(2,1,2);
        if (s.type.equals("guard")) w.companionMode = "guard";
        w.needsUnload = false;
        if (s.type.equals("warehouse")) s.couriers.add(w.getUUID());
        else s.worker = w.getUUID();
        s.lastActive = -1;
        s.status = "Assigned — going to work";
        w.setStatus(s.status);
        SiteData.get(p.serverLevel()).setDirty();
        WorkerRegistry.upsert(w);
        WorkerActions.sync(w);
    }

    static void release(ServerLevel l, Worker w) {
        if (w.siteId != null) {
            var s = SiteData.get(l).sites.get(w.siteId);
            if (s != null) {
                if (w.getUUID().equals(s.worker)) s.worker = null;
                s.couriers.remove(w.getUUID());
                s.lastActive = -1;
                SiteData.get(l).setDirty();
            }
            w.siteId = null;
            w.first = null;
            w.second = null;
            w.output = null;
            w.working = false;
            w.target = null;
            w.idleUntil = 0;
            w.setStatus("Site released — assign a zone");
        }
        WarehouseJobs.release(w.getUUID());
        WorkerRegistry.upsert(w);
    }

    static void dismantle(ServerPlayer p, SiteData.Site s) {
        var l = p.serverLevel();
        var data = SiteData.get(l);
        if (!s.owner.equals(p.getUUID()) || data.sites.get(s.id) != s)
            throw new IllegalArgumentException("Site unavailable");
        if (!SiteJobs.loaded(l, s))
            throw new IllegalArgumentException("Load the whole site before dismantling");
        // Remove from registry before refund: repeat packets cannot return a second core.
        data.sites.remove(s.id);
        data.cores.remove(s.core());
        data.setDirty();
        for (Worker w :
                l.getEntities(
                        HelpfulWorkers.WORKER.get(),
                        a -> s.id.equals(a.siteId) || s.id.equals(a.constructionId))) {
            w.siteId = null;
            w.constructionId = null;
            w.working = false;
            w.target = null;
            w.first = null;
            w.second = null;
            w.output = null;
            w.setStatus("Site dismantled");
            WarehouseJobs.release(w.getUUID());
        WorkerRegistry.upsert(w);
        }
        for (var pos : s.generated)
            if (l.getBlockState(pos).getBlock() instanceof SiteBlocks.ManagedBlock)
                l.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (l.getBlockState(s.core()).is(SiteBlocks.CORE.get()))
            l.setBlock(s.core(), Blocks.AIR.defaultBlockState(), 3);
        if (s.paid) {
            s.paid = false;
            p.getInventory().placeItemBackInInventory(new ItemStack(SiteBlocks.coreItem(s.type)));
        }
        SiteUi.tell(p, "Site dismantled; building and stored items remain");
    }
}
