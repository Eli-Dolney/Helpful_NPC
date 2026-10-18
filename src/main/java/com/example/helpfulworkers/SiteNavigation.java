package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Work targets are blocks; navigation targets must be walkable feet positions. */
final class SiteNavigation {
    record Route(BlockPos target, BlockPos stand, Vec3 previous, long retry, int stalled) {}
    private static final Map<UUID, Route> ROUTES = new HashMap<>();
    static void clear(UUID id) { ROUTES.remove(id); }
    static boolean visible(Worker w, Vec3 eyes, BlockPos target) {
        var hit = w.level().clip(new ClipContext(eyes, target.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, w));
        if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target)) return true;
        if (w.level() instanceof ServerLevel l && l.getBlockState(target).getBlock() instanceof net.minecraft.world.level.block.ChestBlock)
            return WarehouseJobs.canonical(l,target).equals(WarehouseJobs.canonical(l,hit.getBlockPos()));
        if (w.role.equals("forester") && w.siteId!=null && w.level() instanceof ServerLevel l) {
            var site=SiteData.get(l).sites.get(w.siteId);
            if(site!=null) for(var slot:site.treeSlots) if(slot.base.equals(target) && slot.contains(hit.getBlockPos()) && (slot.logs.contains(hit.getBlockPos()) || l.getBlockState(hit.getBlockPos()).is(net.minecraft.tags.BlockTags.LEAVES) || l.getBlockState(hit.getBlockPos()).getBlock() instanceof SiteBlocks.ManagedBlock))return true;
        }
        return false;
    }
    static boolean approach(Worker w, BlockPos target, double horizontal, double vertical) {
        if (!(w.level() instanceof ServerLevel l) || !l.hasChunkAt(target)) { w.setStatus("Target chunk unloaded"); return false; }
        double dx = w.getX()-target.getX()-.5, dz=w.getZ()-target.getZ()-.5;
        if (dx*dx+dz*dz<=horizontal*horizontal && Math.abs(w.getY()-target.getY())<=vertical
                && visible(w,w.getEyePosition(),target)) {
            w.getNavigation().stop(); w.unreachable.remove(target.asLong()); clear(w.getUUID()); return true;
        }
        long now=l.getGameTime();
        Route old=ROUTES.get(w.getUUID());
        if (old!=null && old.target.equals(target)) {
            if(now<old.retry) { w.setStatus("Blocked route — retrying automatically"); return false; }
            int stalled=w.position().distanceToSqr(old.previous)<.1 ? old.stalled+1:0;
            if (!w.getNavigation().isDone() && stalled<5) {
                ROUTES.put(w.getUUID(),new Route(target,old.stand,w.position(),0,stalled));
                w.setStatus("Walking to work position"); return false;
            }
            w.getNavigation().stop();
        }
        List<BlockPos> candidates=new ArrayList<>();
        int radius=(int)Math.min(4,Math.ceil(horizontal));
        for (BlockPos p:BlockPos.betweenClosed(target.offset(-radius,-(int)Math.min(8,vertical),-radius),target.offset(radius,1,radius))) {
            if(!l.hasChunkAt(p) || !Worker.canStandAt(l,p)) continue;
            double cx=p.getX()-target.getX(), cz=p.getZ()-target.getZ();
            if(cx*cx+cz*cz>Math.max(.5,horizontal-.8)*Math.max(.5,horizontal-.8) || Math.abs(p.getY()-target.getY())>vertical) continue;
            if(!visible(w,Vec3.atBottomCenterOf(p).add(0,w.getEyeHeight(),0),target)) continue;
            candidates.add(p.immutable());
        }
        candidates.sort(Comparator.<BlockPos>comparingDouble(p->p.distToCenterSqr(w.position())).thenComparing(p->p));
        int attempts=0;
        for (BlockPos p:candidates) {
            if(old!=null && old.target.equals(target) && old.stand.equals(p))continue;
            if(attempts++>=8) break;
            var path=w.getNavigation().createPath(p,0);
            if(path!=null && path.canReach() && w.getNavigation().moveTo(path,1)) {
                ROUTES.put(w.getUUID(),new Route(target,p,w.position(),0,0));
                w.setStatus("Walking to work position"); return false;
            }
        }
        ROUTES.put(w.getUUID(),new Route(target,target,w.position(),now+60,0));
        w.unreachable.put(target.asLong(),now+60);
        w.setStatus("Blocked route at "+target.toShortString()+" — retrying automatically");
        SiteJobs.suspend(w);
        return false;
    }
}
