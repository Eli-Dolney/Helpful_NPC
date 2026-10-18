package com.example.helpfulworkers;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;

/** Vanilla growth is staged before committing, so a tree cannot overwrite a neighbouring plot. */
final class NaturalTrees {
    static Block sapling(String species) { return BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(species.equals("mangrove")?"mangrove_propagule":species+"_sapling")); }
    static int count(String species) { return species.equals("dark_oak")?4:1; }
    static List<BlockPos> planting(SiteData.TreeSlot slot) {
        if(count(slot.species)==1) return List.of(slot.base);
        return List.of(slot.base,slot.base.east(),slot.base.south(),slot.base.east().south());
    }
    static void ensure(ServerLevel l,SiteData.Site s) {
        if(!s.treeSlots.isEmpty()) return;
        for(int i=0;i<8;i++) {
            var slot=new SiteData.TreeSlot(SiteCatalog.TREES[i],s.anchor("Tree"+(i+1),s.at(SiteCatalog.PLOTS[i][0],1,SiteCatalog.PLOTS[i][1])));
            slot.enabled=s.enabled[i];
            for(var p:s.generated) if(slot.contains(p) && l.hasChunkAt(p) && l.getBlockState(p).getBlock() instanceof SiteBlocks.ManagedBlock m && m.kind.startsWith("log_")) slot.logs.add(p);
            slot.legacy=!slot.logs.isEmpty(); s.treeSlots.add(slot);
        }
        SiteData.get(l).setDirty();
    }
    static void grow(BlockGrowFeatureEvent e) {
        if(!(e.getLevel() instanceof ServerLevel l)) return;
        for(var site:SiteData.get(l).sites.values()) {
            if(!site.type.equals("logging") || (!site.active() && !site.phase.equals("plant"))) continue;
            ensure(l,site);
            for(var slot:site.treeSlots) {
                if(!planting(slot).contains(e.getPos())) continue;
                String feature=e.getFeature()==null?"":e.getFeature().unwrapKey().map(k->k.location().getPath()).orElse("");
                if(feature.startsWith("mega_") && !List.of(slot.base,slot.base.east(),slot.base.south(),slot.base.east().south()).stream().allMatch(q->l.getBlockState(q).is(sapling(slot.species)))) { e.setFeature((net.minecraft.core.Holder<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?,?>>)null); return; }
                e.setCanceled(true);
                if(site.paused || e.getFeature()==null) return;
                if (!site.phase.equals("plant")) {
                    if(!slot.enabled || slot.building || !(site.worker != null && l.getEntity(site.worker) instanceof Worker worker)
                            || !worker.working || WorkerSessions.isSuspended(worker)) return;
                    if(!(worker.getMainHandItem().getItem() instanceof AxeItem) || StorageOps.capacity(StorageOps.at(l,site.output()),new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(slot.species+"_log"))))<1)return;
                }
                for(BlockPos p:List.of(slot.base.offset(-4,0,-4),slot.base.offset(4,0,4))) if(!l.hasChunkAt(p)) return;
                Map<BlockPos,BlockState> staged=new LinkedHashMap<>();
                for(var p:planting(slot)) staged.put(p,Blocks.AIR.defaultBlockState());
                boolean[] invalid={false};
                WorldGenLevel proxy=(WorldGenLevel)Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),new Class[]{WorldGenLevel.class},(obj,method,args)-> {
                    String name=method.getName();
                    if(args!=null && args.length>0 && args[0] instanceof BlockPos pos) {
                        if(name.equals("setBlock")) {
                            if(!slot.contains(pos) || !l.hasChunkAt(pos)) { invalid[0]=true; return false; }
                            BlockState old=l.getBlockState(pos), next=(BlockState)args[1];
                            if(!old.isAir() && !old.is(BlockTags.SAPLINGS) && !SiteConstruction.soft(old)
                                    && !(pos.getY()==slot.base.getY()-1 && old.is(BlockTags.DIRT))) { invalid[0]=true; return false; }
                            staged.put(pos.immutable(),next); return true;
                        }
                        if(name.equals("getBlockState")) return !slot.contains(pos) || !l.hasChunkAt(pos) ? Blocks.BEDROCK.defaultBlockState():staged.getOrDefault(pos,l.getBlockState(pos));
                        if(name.equals("getFluidState")) return (!slot.contains(pos) || !l.hasChunkAt(pos) ? Blocks.BEDROCK.defaultBlockState():staged.getOrDefault(pos,l.getBlockState(pos))).getFluidState();
                        if(name.equals("isStateAtPosition")) return ((java.util.function.Predicate<BlockState>)args[1]).test(!slot.contains(pos)?Blocks.BEDROCK.defaultBlockState():staged.getOrDefault(pos,l.getBlockState(pos)));
                        if(name.equals("isEmptyBlock")) return slot.contains(pos) && staged.getOrDefault(pos,l.getBlockState(pos)).isAir();
                        if(name.equals("destroyBlock") || name.equals("removeBlock")) { invalid[0]=true; return false; }
                    }
                    try { return method.invoke(l,args); } catch(InvocationTargetException ex) { throw ex.getCause(); }
                });
                boolean grew=e.getFeature().value().place(proxy,l.getChunkSource().getGenerator(),e.getRandom(),slot.base);
                if(!grew || invalid[0]) { site.status="Needs growing space: "+slot.species; return; }
                for(var entry:staged.entrySet()) {
                    l.setBlock(entry.getKey(),entry.getValue(),3);
                    if(entry.getValue().is(BlockTags.LOGS) || entry.getValue().is(Blocks.MANGROVE_ROOTS) || entry.getValue().is(Blocks.MUDDY_MANGROVE_ROOTS)) slot.logs.add(entry.getKey());
                }
                site.status="Growing "+slot.species; SiteData.get(l).setDirty(); return;
            }
        }
    }
    /** One-time construction finish: stock once, then grow one original slot per action. */
    static boolean starter(ServerLevel l, SiteData.Site s) {
        if (!s.phase.equals("plant")) return false;
        ensure(l, s);
        if (!s.starterStock) {
            var chest = StorageOps.at(l, s.supply());
            if (chest == null) { s.status = "Starter saplings need the supply chest restored"; return false; }
            // Check all eight stacks together before changing any existing inventory.
            var staged = new net.minecraft.world.SimpleContainer(chest.getContainerSize());
            for (int i=0; i<chest.getContainerSize(); i++) staged.setItem(i,chest.getItem(i).copy());
            for (String species : SiteCatalog.TREES) {
                if (!staged.addItem(new ItemStack(sapling(species),16)).isEmpty()) {
                    s.status = "Make room in the supply chest for starter saplings"; return false;
                }
            }
            for (int i=0; i<chest.getContainerSize(); i++) chest.setItem(i,staged.getItem(i));
            chest.setChanged(); s.starterStock = true; SiteData.get(l).setDirty();
        }
        if (s.starterTreeCursor >= Math.min(8,s.treeSlots.size())) return true;
        var slot = s.treeSlots.get(s.starterTreeCursor);
        if (slot.logs.isEmpty()) {
            for (var pos : planting(slot)) {
                var state = l.getBlockState(pos);
                boolean marker = state.getBlock() instanceof SiteBlocks.ManagedBlock m && m.kind.startsWith("marker_");
                if (!state.is(sapling(slot.species)) && !state.isAir() && !SiteConstruction.soft(state) && !marker) {
                    s.status = "Needs growing space: " + slot.species + " at " + pos.toShortString(); return false;
                }
            }
            for (var pos : planting(slot)) {
                if (!l.getBlockState(pos).is(sapling(slot.species))) l.setBlock(pos,sapling(slot.species).defaultBlockState(),3);
                s.generated.remove(pos);
            }
            // Bootstrap full-size vanilla trees; later generations use normal random ticks.
            // Bounded retries allow random shapes which do not fit to be tried again safely.
            for (int attempt=0; attempt<4 && slot.logs.isEmpty(); attempt++) {
                var state = l.getBlockState(slot.base);
                if (state.getBlock() instanceof SaplingBlock sapling) sapling.advanceTree(l,slot.base,state,l.random);
            }
            if (slot.logs.isEmpty()) { s.status = "Needs growing space: " + slot.species; return false; }
        }
        s.starterTreeCursor++;
        s.status = "Preparing starter trees " + s.starterTreeCursor + "/8";
        SiteData.get(l).setDirty();
        return s.starterTreeCursor >= 8;
    }

    static void tick(ServerLevel l,Worker w,SiteData.Site s) {
        ensure(l,s);
        if(!Jobs.tool(l,w,AxeItem.class,"axe")) return;
        var output=StorageOps.at(l,s.output());
        for(int n=0;n<w.bag.getContainerSize();n++) if(StorageOps.exportable(w,n)>0) {
            if(StorageOps.capacity(output,w.bag.getItem(n))==0) { w.setStatus("Logging storage full"); return; }
            if(!w.approach(s.output())) return;
            StorageOps.move(w.bag,n,output,StorageOps.exportable(w,n)); return;
        }
        // Collect normal tree drops without touching items beyond camp slots.
        for(var item:l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,w.getBoundingBox().inflate(3))) {
            if(s.treeSlots.stream().noneMatch(slot->slot.contains(item.blockPosition()))) continue;
            var stack=item.getItem();
            if(!stack.is(net.minecraft.tags.ItemTags.SAPLINGS) && !stack.is(net.minecraft.tags.ItemTags.LOGS) && !stack.is(Items.STICK) && !stack.is(Items.APPLE)) continue;
            var remainder=w.bag.addItem(stack.copy()); item.setItem(remainder); if(remainder.isEmpty()) item.discard();
        }
        for(int step=0;step<s.treeSlots.size();step++) {
            int index=Math.floorMod(s.treeCursor+step,s.treeSlots.size()); var slot=s.treeSlots.get(index);
            if(slot.building || !l.hasChunkAt(slot.base)) continue;
            slot.logs.removeIf(p->l.hasChunkAt(p) && !(l.getBlockState(p).is(BlockTags.LOGS) || l.getBlockState(p).is(Blocks.MANGROVE_ROOTS) || l.getBlockState(p).is(Blocks.MUDDY_MANGROVE_ROOTS) || l.getBlockState(p).getBlock() instanceof SiteBlocks.ManagedBlock));
            if(!slot.logs.isEmpty()) {
                var pos=slot.logs.stream().min(Comparator.comparingInt(BlockPos::getY)).orElseThrow();
                ItemStack result=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(slot.species+"_log")));
                if(StorageOps.capacity(w.bag,result)<1 || StorageOps.capacity(output,result)<1) { w.setStatus("Logging storage full"); return; }
                // The base is the tree's work station; upper trunk sections are felled from below.
                if(!w.approachWithin(slot.base,3,3)) return;
                if(slot.legacy) { l.setBlock(pos,Blocks.AIR.defaultBlockState(),3); w.bag.addItem(result); s.generated.remove(pos); }
                else {
                    var state=l.getBlockState(pos);
                    for(var drop:Block.getDrops(state,l,pos,l.getBlockEntity(pos),w,w.getMainHandItem())) {
                        var left=w.bag.addItem(drop); if(!left.isEmpty()) Block.popResource(l,pos,left);
                    }
                    l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                }
                slot.logs.remove(pos); w.getMainHandItem().hurtAndBreak(1,w,EquipmentSlot.MAINHAND); w.setStatus("Chopping "+slot.species); s.treeCursor=(index+1)%s.treeSlots.size(); return;
            }
            if(slot.legacy) {
                int budget=WorkerConfig.scanBudget();
                for(var it=s.generated.iterator();it.hasNext() && budget>0;) {
                    var pos=it.next(); if(!slot.contains(pos)) continue;
                    if(l.getBlockState(pos).getBlock() instanceof SiteBlocks.ManagedBlock) l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                    it.remove(); budget--;
                }
                if(s.generated.stream().anyMatch(slot::contains)) return;
                slot.legacy=false;
            }
            if(!slot.enabled) continue;
            boolean initial=l.getBlockState(slot.base).getBlock() instanceof SiteBlocks.ManagedBlock m && m.kind.startsWith("marker_");
            if(planting(slot).stream().allMatch(p->l.getBlockState(p).is(sapling(slot.species)))) continue;
            if(!w.approachWithin(slot.base,3,3)) return;
            for(var pos:planting(slot)) {
                var state=l.getBlockState(pos);
                if(state.is(sapling(slot.species))) continue;
                if(!state.isAir() && !SiteConstruction.soft(state) && !initial) { w.setStatus("Planting position blocked"); return; }
                if(!initial && w.bag.countItem(sapling(slot.species).asItem())==0 && w.supply!=null) {
                    var chest=StorageOps.at(l,w.supply);
                    if(chest!=null) for(int n=0;n<chest.getContainerSize();n++) if(chest.getItem(n).is(sapling(slot.species).asItem())) {
                        if(!w.approach(w.supply))return;
                        StorageOps.move(chest,n,w.bag,Math.max(4,count(slot.species)));return;
                    }
                }
                if(!initial && !Jobs.take(l,w,new ItemStack(sapling(slot.species)))) { w.setStatus("Missing "+slot.species+" saplings"); s.treeCursor=(index+1)%s.treeSlots.size(); return; }
                l.setBlock(pos,sapling(slot.species).defaultBlockState(),3); s.generated.remove(pos);
            }
            s.treeCursor=(index+1)%s.treeSlots.size(); w.setStatus("Replanting "+slot.species); return;
        }
        w.setStatus(s.status.startsWith("Needs growing")?s.status:"Waiting for natural tree growth");
    }
    static boolean fits(ServerLevel l,SiteData.Site s,BlockPos base) {
        for(var other:SiteData.get(l).sites.values()) {
            if(other==s) continue;
            if(base.getX()+4>=other.origin.getX() && base.getX()-4<other.origin.getX()+(other.rotation%2==0?other.width():other.depth())
                    && base.getZ()+4>=other.origin.getZ() && base.getZ()-4<other.origin.getZ()+(other.rotation%2==0?other.depth():other.width())) return false;
        }
        for(var slot:s.treeSlots) if(Math.abs(slot.base.getX()-base.getX())<10 && Math.abs(slot.base.getZ()-base.getZ())<10) return false;
        for(BlockPos p:BlockPos.betweenClosed(base.offset(-4,-1,-4),base.offset(4,18,4))) {
            if(!l.hasChunkAt(p) || !l.getWorldBorder().isWithinBounds(p)) return false;
            var state=l.getBlockState(p);
            if(p.getY()==base.getY()-1) { if(!state.is(BlockTags.DIRT)) return false; }
            else if(!state.isAir() && !SiteConstruction.soft(state)) return false;
        }
        return true;
    }
    static void menu(ServerPlayer p,SiteUi.Session session,SiteData.Site s) {
        SiteUi.begin(session);
        for(String species:SiteCatalog.TREES) SiteUi.option(session,SiteCatalog.pretty(species),()-> {
            if(s.treeSlots.size()>=32) throw new IllegalArgumentException("Camp limit: 32 slots");
            BlockPos base=null;
            // Search nearest available space, inside the selected plot before adjacent extensions.
            outer:for(int pass=0;pass<2;pass++) for(int radius=0;radius<=48;radius+=2) for(int x=-radius;x<=radius;x+=2) for(int z=-radius;z<=radius;z+=2) {
                if(Math.max(Math.abs(x),Math.abs(z))!=radius) continue;
                var q=s.core().offset(x,0,z);
                boolean inside=q.getX()-4>=Math.min(s.plotFirst.getX(),s.plotSecond.getX()) && q.getX()+4<=Math.max(s.plotFirst.getX(),s.plotSecond.getX()) && q.getZ()-4>=Math.min(s.plotFirst.getZ(),s.plotSecond.getZ()) && q.getZ()+4<=Math.max(s.plotFirst.getZ(),s.plotSecond.getZ());
                if((pass==0)!=inside) continue;
                if(fits(p.serverLevel(),s,q)) { base=q; break outer; }
            }
            if(base==null) throw new IllegalArgumentException("No clear, level growing space nearby");
            BlockPos chosen=base;
            var access=accessPath(p.serverLevel(),s,chosen);
            if(access.isEmpty())throw new IllegalArgumentException("No clear walking connection to the camp entrance");
            SiteUi.begin(session);
            p.serverLevel().sendParticles(p,net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,true,base.getX()+.5,base.getY(),base.getZ()+.5,30,4,.2,4,0);
            SiteUi.option(session,"Confirm new "+species+" slot",()-> {
                if(!fits(p.serverLevel(),s,chosen)) throw new IllegalArgumentException("Growing space changed");
                Worker b=p.serverLevel().getEntities(HelpfulWorkers.WORKER.get(),w->w.owns(p) && w.role.equals("builder") && w.constructionId==null).stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElseThrow(()->new IllegalArgumentException("An available builder must be loaded"));
                var slot=new SiteData.TreeSlot(species,chosen); slot.building=true; slot.path.addAll(access); s.treeSlots.add(slot); s.builder=b.getUUID(); b.constructionId=s.id; b.working=true;
                SiteData.get(p.serverLevel()).setDirty(); SiteUi.coreMenu(p,session);
            });
            SiteUi.option(session,"Cancel",()->SiteUi.coreMenu(p,session));
            SiteUi.send(p,session,"Preview new tree slot",species+" at "+base.toShortString()+"; reserves 9 × 19 × 9. Saplings come from supplies.");
        });
        SiteUi.option(session,"Back",()->SiteUi.coreMenu(p,session));
        SiteUi.send(p,session,"Add tree slot","Choose the tree species.");
    }
    static List<BlockPos> accessPath(ServerLevel l,SiteData.Site s,BlockPos start) {
        BlockPos goal=s.entrance();
        Map<BlockPos,BlockPos> previous=new LinkedHashMap<>(); ArrayDeque<BlockPos> queue=new ArrayDeque<>();
        queue.add(start);previous.put(start,start);
        while(!queue.isEmpty() && previous.size()<4096) {
            var pos=queue.remove();
            if(pos.equals(goal)) {
                List<BlockPos> path=new ArrayList<>();
                for(var q=goal;!q.equals(start);q=previous.get(q))path.add(q);
                path.add(start);Collections.reverse(path);return path;
            }
            for(var direction:Direction.Plane.HORIZONTAL) {
                var q=pos.relative(direction);
                if(previous.containsKey(q) || Math.abs(q.getX()-start.getX())>64 || Math.abs(q.getZ()-start.getZ())>64 || !l.hasChunkAt(q))continue;
                if(s.treeSlots.stream().anyMatch(slot->Math.abs(q.getX()-slot.base.getX())<=4 && Math.abs(q.getZ()-slot.base.getZ())<=4))continue;
                var state=l.getBlockState(q);var below=l.getBlockState(q.below());
                if((!state.isAir() && !SiteConstruction.soft(state)) || !l.getBlockState(q.above()).isAir() || (!below.isSolidRender(l,q.below()) && !below.is(Blocks.DIRT_PATH)))continue;
                previous.put(q,pos);queue.add(q);
            }
        }
        return List.of();
    }

    static void placed(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent e) {
        if(!(e.getLevel() instanceof ServerLevel l))return;
        for(var s:SiteData.get(l).sites.values())for(var slot:s.treeSlots) if(slot.logs.remove(e.getPos()))SiteData.get(l).setDirty();
    }

    static void build(ServerLevel l,Worker b,SiteData.Site s) {
        if(s.paused){b.setStatus("Tree-slot construction paused");return;}
        for(var slot:s.treeSlots) if(slot.building) {
            if(!l.hasChunkAt(slot.base)) { b.setStatus("Tree slot chunk unloaded"); return; }
            if(slot.cursor<slot.path.size()) {
                var pos=slot.path.get(slot.cursor);
                if(!l.hasChunkAt(pos)) { b.setStatus("Tree path chunk unloaded");return; }
                if(!l.getBlockState(pos).isAir() && !SiteConstruction.soft(l.getBlockState(pos))) { b.setStatus("Clear tree path at "+pos.toShortString());return; }
                if(!b.approachWithin(pos,4,3))return;
                if(l.getBlockState(pos.below()).is(BlockTags.DIRT)) {
                    if(SiteConstruction.soft(l.getBlockState(pos)))l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                    l.setBlock(pos.below(),Blocks.DIRT_PATH.defaultBlockState(),3);
                }
                slot.cursor++;SiteData.get(l).setDirty();b.setStatus("Building tree-slot path");return;
            }
            if(!b.approachWithin(slot.base,4,3)) return;
            for(var p:planting(slot)) {
                if(!SiteConstruction.terrain(l.getBlockState(p.below()))) { b.setStatus("Tree slot foundation changed"); return; }
                l.setBlock(p.below(),Blocks.DIRT.defaultBlockState(),3);
            }
            slot.building=false; b.setStatus("Tree slot ready — supply saplings"); SiteData.get(l).setDirty(); return;
        }
        b.constructionId=null; b.working=b.siteId!=null;
    }
}
