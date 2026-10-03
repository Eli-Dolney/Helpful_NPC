package com.example.helpfulworkers;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;

/** Supplies are finite warehouse stock, with one reserved delivery per recipient. */
final class WarehouseSupplies {
    record Request(UUID warehouse,UUID worker,BlockPos source,int slot,BlockPos destination,String item,int amount,long expires) {}
    static final Map<UUID,Request> REQUESTS=new HashMap<>();
    static final Map<UUID,Integer> STOCK_CURSOR=new HashMap<>();
    static final Map<UUID,Integer> CURSORS=new HashMap<>();
    static final Map<UUID,Long> CONNECTED=new HashMap<>();
    static void release(UUID courier) { REQUESTS.remove(courier); WarehouseJobs.LOCKS.entrySet().removeIf(e->e.getValue().equals(courier)); }
    static void linkWorker(ServerLevel l,SiteData.Site site,UUID id) {
        for(var other:SiteData.get(l).sites.values()) other.linkedWorkers.remove(id);
        site.excluded.remove(id); site.linkedWorkers.add(id); SiteData.get(l).setDirty();
    }
    static void linkSite(ServerLevel l,SiteData.Site site,UUID id) {
        for(var other:SiteData.get(l).sites.values()) other.linkedSites.remove(id);
        site.excluded.remove(id); site.linkedSites.add(id); SiteData.get(l).setDirty();
    }
    static void connect(ServerLevel l,SiteData.Site site) {
        if(CONNECTED.getOrDefault(site.id,-100L)+100>l.getGameTime()) return;
        CONNECTED.put(site.id,l.getGameTime());
        var warehouses=SiteData.get(l).sites.values().stream().filter(s->s.active() && s.type.equals("warehouse") && s.owner.equals(site.owner)).toList();
        for(var e:WorkerRegistry.forOwner(l,site.owner)) {
            if(!e.alive() || !e.dimension().equals(l.dimension()) || e.role().equals("courier")) continue;
            if(warehouses.stream().anyMatch(s->s.linkedWorkers.contains(e.entityId()))) continue;
            var chosen=warehouses.stream().filter(s->!s.excluded.contains(e.entityId()) && s.linkedWorkers.size()<64).min(Comparator.<SiteData.Site>comparingDouble(s->s.core().distSqr(e.pos())).thenComparing(s->s.id)).orElse(null);
            if(chosen!=null) { chosen.linkedWorkers.add(e.entityId()); SiteData.get(l).setDirty(); }
        }
        for(var src:SiteData.get(l).sites.values()) {
            if(!src.active() || src.type.equals("warehouse") || !src.owner.equals(site.owner)) continue;
            if(warehouses.stream().anyMatch(s->s.linkedSites.contains(src.id))) continue;
            var chosen=warehouses.stream().filter(s->!s.excluded.contains(src.id) && s.linkedSites.size()<64).min(Comparator.<SiteData.Site>comparingDouble(s->s.core().distSqr(src.core())).thenComparing(s->s.id)).orElse(null);
            if(chosen!=null) { chosen.linkedSites.add(src.id); SiteData.get(l).setDirty(); }
        }
    }
    static Map<String,Integer> defaults(ServerLevel l,WorkerRegistry.Entry entry) {
        Map<String,Integer> out=new LinkedHashMap<>();
        switch(entry.role()) {
            case "miner" -> {
                String tier="iron";
                if(l.getEntity(entry.entityId()) instanceof Worker worker && worker.siteId!=null) {
                    var site=SiteData.get(l).sites.get(worker.siteId);
                    if(site!=null) { String minimum=SiteSettings.minimumPickaxe(site.type); if(minimum.equals("diamond") || minimum.equals("netherite"))tier=minimum; }
                }
                out.put("minecraft:"+tier+"_pickaxe",1);
            }
            case "forester" -> {
                out.put("minecraft:iron_axe",1);
                for(String tree:SiteCatalog.TREES) out.put("minecraft:"+(tree.equals("mangrove")?"mangrove_propagule":tree+"_sapling"),64);
            }
            case "farmer" -> {
                out.put("minecraft:iron_hoe",1);
                var crops=l.getEntity(entry.entityId()) instanceof Worker worker?worker.enabledCrops:List.of("wheat","carrots","potatoes","beetroots");
                for(String crop:crops) {String item=switch(crop){case "carrots"->"carrot";case "potatoes"->"potato";case "beetroots"->"beetroot_seeds";case "nether_wart"->"nether_wart";default->"wheat_seeds";};out.put("minecraft:"+item,64);}
            }
            case "fisher" -> out.put("minecraft:fishing_rod",1);
            case "rancher" -> { out.put("minecraft:shears",1); out.put("minecraft:wheat",64); out.put("minecraft:bucket",16); }
            case "smelter" -> out.put("minecraft:coal",64);
            case "knight" -> { out.put("minecraft:iron_sword",1); out.put("minecraft:shield",1); }
            case "archer" -> { out.put("minecraft:bow",1); out.put("minecraft:arrow",64); }
            case "builder" -> {
                out.put("minecraft:iron_pickaxe",1);
                out.put("minecraft:iron_axe",1);
                out.put("minecraft:iron_shovel",1);
                if(l.getEntity(entry.entityId()) instanceof Worker w && w.constructionId==null && w.blueprintBlocks!=null && !Blueprints.isFreePreset(w.blueprint)) {
                    for(int i=Math.max(0,w.scanCursor);i<w.blueprintBlocks.size();i++) {
                        var state=net.minecraft.nbt.NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),w.blueprintBlocks.getCompound(i).getCompound("State"));
                        if(state.getBlock().asItem()!=Items.AIR) out.merge(BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).toString(),1,Integer::sum);
                    }
                }
            }
        }
        out.put("minecraft:bread",16); return out;
    }
    static int count(Container c,Item item) {
        if(c==null)return 0; int n=0; for(int i=0;i<c.getContainerSize();i++) if(c.getItem(i).is(item)) n+=c.getItem(i).getCount(); return n;
    }
    static boolean collect(ServerLevel l,Worker courier,SiteData.Site site) {
        for(UUID id:new ArrayList<>(REQUESTS.keySet())) if(REQUESTS.get(id).expires<l.getGameTime())release(id);
        Request r=REQUESTS.get(courier.getUUID());
        if(r!=null) {
            var entry=WorkerRegistry.forOwner(l,site.owner).stream().filter(e->e.entityId().equals(r.worker) && e.alive() && e.dimension().equals(l.dimension())).findFirst().orElse(null);
            if(entry==null || !site.linkedWorkers.contains(r.worker) || !r.destination.equals(entry.supply()) || site.noRestock.contains(r.worker)
                    || site.storage.stream().noneMatch(st->st.pos.equals(r.source))) { release(courier.getUUID()); return false; }
            Container source=StorageOps.at(l,r.source), dest=StorageOps.at(l,r.destination);
            Item item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.item));
            if(source==null || dest==null || !source.getItem(r.slot).is(item)) { release(courier.getUUID()); return false; }
            if(!courier.approach(r.source)) return true;
            int target=site.supplyTargets.getOrDefault(r.worker,defaults(l,entry)).getOrDefault(r.item,0);
            int n=Math.min(r.amount,Math.max(0,target-count(dest,item)));
            n=Math.min(n,StorageOps.capacity(dest,source.getItem(r.slot)));
            if(StorageOps.move(source,r.slot,courier.bag,n)>0) {
                courier.supplyDelivery=r.destination; courier.supplyDeliveryWorker=r.worker; courier.setStatus("Carrying supplies to "+entry.name());
            }
            release(courier.getUUID()); return true;
        }
        // Alternate with collections; urgent shortages receive the next opportunity.
        int cursor=CURSORS.merge(courier.getUUID(),1,Integer::sum);
        if(cursor%2==0)return false;
        var entries=WorkerRegistry.forOwner(l,site.owner).stream().filter(e->e.alive() && e.dimension().equals(l.dimension()) && site.linkedWorkers.contains(e.entityId()) && !site.noRestock.contains(e.entityId()) && e.supply()!=null && !e.supply().equals(e.output())).sorted(Comparator.comparing(WorkerRegistry.Entry::entityId)).toList();
        if(entries.isEmpty())return false;
        var entry=entries.get(Math.floorMod(cursor/2,entries.size()));
        if(REQUESTS.values().stream().anyMatch(q->q.worker.equals(entry.entityId())))return false;
        if(l.getEntities(HelpfulWorkers.WORKER.get(),w->entry.entityId().equals(w.supplyDeliveryWorker)).size()>0)return false;
        Container dest=StorageOps.at(l,entry.supply()); if(dest==null)return false;
        Map<String,Integer> targets=site.supplyTargets.getOrDefault(entry.entityId(),defaults(l,entry));
        List<SiteData.Storage> stores=site.storage.stream().filter(st->!st.mode.equals("incoming") && !st.pos.equals(entry.supply())).toList();
        int total=stores.size()*54;
        if(total==0)return false;
        int offset=STOCK_CURSOR.getOrDefault(courier.getUUID(),0);
        for(int step=0;step<WorkerConfig.scanBudget();step++) {
            int index=Math.floorMod(offset++,total); var st=stores.get(index/54);
            Container source=StorageOps.at(l,st.pos); int i=index%54;
            if(source==null || i>=source.getContainerSize())continue;
            var stack=source.getItem(i); if(stack.isEmpty())continue;
            String key=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            int missing=targets.getOrDefault(key,0)-count(dest,stack.getItem());
            if(missing<=0 || StorageOps.capacity(dest,stack)==0)continue;
            var lock=new WarehouseJobs.Lock(l.dimension().location().toString(),null,st.pos,i);
            if(WarehouseJobs.LOCKS.containsKey(lock))continue;
            REQUESTS.put(courier.getUUID(),new Request(site.id,entry.entityId(),st.pos,i,entry.supply(),key,Math.min(missing,stack.getMaxStackSize()),l.getGameTime()+200));
            WarehouseJobs.LOCKS.put(lock,courier.getUUID()); STOCK_CURSOR.put(courier.getUUID(),offset); courier.setStatus("Collecting supplies for "+entry.name()); return true;
        }
        STOCK_CURSOR.put(courier.getUUID(),offset);
        return false;
    }
    static boolean deliver(ServerLevel l,Worker w,SiteData.Site s) {
        if(w.supplyDelivery==null)return false;
        boolean allowed=WorkerRegistry.forOwner(l,s.owner).stream().anyMatch(e->e.alive() && e.dimension().equals(l.dimension()) && e.entityId().equals(w.supplyDeliveryWorker) && w.supplyDelivery.equals(e.supply()) && s.linkedWorkers.contains(e.entityId()));
        if(!allowed) { w.supplyDelivery=null; w.supplyDeliveryWorker=null; return false; }
        Container dest=StorageOps.at(l,w.supplyDelivery);
        for(int i=0;i<w.bag.getContainerSize();i++) if(!w.bag.getItem(i).isEmpty()) {
            if(dest==null || StorageOps.capacity(dest,w.bag.getItem(i))==0) { w.setStatus("Supply chest unavailable/full — keeping delivery"); return true; }
            if(!w.approach(w.supplyDelivery))return true;
            StorageOps.move(w.bag,i,dest,w.bag.getItem(i).getMaxStackSize()); w.setStatus("Delivered worker supplies"); return true;
        }
        w.supplyDelivery=null; w.supplyDeliveryWorker=null; return false;
    }
    static void menu(ServerPlayer p,SiteUi.Session session,SiteData.Site s,UUID id) {
        var entry=WorkerRegistry.forOwner(p.serverLevel(),s.owner).stream().filter(e->e.entityId().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Worker not registered"));
        SiteUi.begin(session);
        SiteUi.option(session,s.noRestock.contains(id)?"Enable restocking":"Disable restocking",()->{ if(!s.noRestock.remove(id))s.noRestock.add(id); SiteData.get(p.serverLevel()).setDirty();menu(p,session,s,id); });
        var targets=s.supplyTargets.getOrDefault(id,defaults(p.serverLevel(),entry));
        for(var item:targets.entrySet()) SiteUi.option(session,item.getKey()+": "+item.getValue()+" (cycle)",()->{
            var edited=s.supplyTargets.computeIfAbsent(id,k->new LinkedHashMap<>(targets));
            int n=item.getValue(); edited.put(item.getKey(), n==0?1:n==1?16:n==16?64:0); SiteData.get(p.serverLevel()).setDirty();menu(p,session,s,id);
        });
        SiteUi.option(session,"Add held item (64)",()-> { if(p.getMainHandItem().isEmpty())throw new IllegalArgumentException("Hold a supply item"); s.supplyTargets.computeIfAbsent(id,k->new LinkedHashMap<>(targets)).put(BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString(),Math.min(64,p.getMainHandItem().getMaxStackSize())); SiteData.get(p.serverLevel()).setDirty();menu(p,session,s,id); });
        SiteUi.option(session,"Restore role defaults",()->{s.supplyTargets.remove(id);SiteData.get(p.serverLevel()).setDirty();menu(p,session,s,id);});
        SiteUi.option(session,"Back",()->SiteUi.coreMenu(p,session));
        SiteUi.send(p,session,"Supplies — "+entry.name(),"Warehouse stock is delivered to the supply chest. Targets cycle 0 / 1 / 16 / 64.");
    }
}
