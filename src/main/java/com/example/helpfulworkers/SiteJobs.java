package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

import java.util.*;

final class SiteJobs {
    static boolean tick(ServerLevel l, Worker w) {
        SiteData data = SiteData.get(l);
        if (w.constructionId != null) {
            var s = data.sites.get(w.constructionId);
            if (s == null) {
                w.constructionId = null;
                w.working = false;
                w.setStatus("Construction site missing");
            } else SiteConstruction.tick(l, w, s);
            return true;
        }
        if (w.siteId == null) return false;
        var s = data.sites.get(w.siteId);
        if (s == null || !s.owner.equals(w.owner)) {
            SiteActions.release(l, w);
            w.setStatus("Site unavailable — use zone assignment");
            return true;
        }
        if ("warehouse".equals(s.type)) {
            WarehouseJobs.tick(l, w, s);
            return true;
        }
        if (!w.getUUID().equals(s.worker)) {
            SiteActions.release(l, w);
            return true;
        }
        long now = l.getGameTime();
        int elapsed =
                s.lastActive < 0
                        ? 0
                        : (int)
                                Math.min(
                                        Math.max(20, WorkerConfig.workInterval()),
                                        Math.max(0, now - s.lastActive));
        s.lastActive = now;
        if (!w.working) {
            s.lastActive = -1;
            return true;
        }
        if (!s.active() || s.paused) {
            w.setStatus("Site paused");
            return true;
        }
        if (!loaded(l, s)) {
            w.setStatus("Site chunk unloaded");
            s.lastActive = -1;
            return true;
        }
        if (!l.getBlockState(s.core()).is(SiteBlocks.CORE.get())
                || StorageOps.at(l, s.output()) == null) {
            w.setStatus("Site damaged — repair core/output");
            return true;
        }
        if(s.type.equals("builder") && (w.buildOrigin==null || w.blueprintBlocks.isEmpty())) { if(w.approach(s.station()))w.setStatus("Builder’s yard ready — choose a construction job"); return true; }
        if (s.type.equals("logging")) { NaturalTrees.tick(l,w,s); data.setDirty(); return true; }
        if (!s.type.equals("logging") && !Arrays.asList(SiteCatalog.ORES).contains(s.type)) return false;
        boolean logging = "logging".equals(s.type);
        if (!logging && s.oreDelay <= 0) { s.oreDelay = sampleDelay(l); s.production = 0; }
        ItemStack tool = w.getMainHandItem();
        if (!pickaxe(tool,s.type) && w.supply != null) {
            Container supply=StorageOps.at(l,w.supply);
            if(supply!=null) for(int i=0;i<supply.getContainerSize();i++) if(pickaxe(supply.getItem(i),s.type)) {
                if(!w.approach(w.supply)) return true;
                if(!tool.isEmpty() && StorageOps.capacity(w.bag,tool)<tool.getCount()) break;
                w.bag.addItem(tool.copy()); w.setItemSlot(EquipmentSlot.MAINHAND,supply.removeItem(i,1));
                supply.setChanged(); tool=w.getMainHandItem(); break;
            }
        }
        if (logging ? !(tool.getItem() instanceof AxeItem) : !pickaxe(tool, s.type)) {
            for (int i = 0; i < w.bag.getContainerSize(); i++) {
                ItemStack spare = w.bag.getItem(i);
                if (logging ? spare.getItem() instanceof AxeItem : pickaxe(spare, s.type)) {
                    ItemStack replacement = spare.copy();
                    w.bag.setItem(i, tool.copy());
                    w.setItemSlot(EquipmentSlot.MAINHAND, replacement);
                    tool = replacement;
                    break;
                }
            }
        }
        if (logging ? !(tool.getItem() instanceof AxeItem) : !pickaxe(tool, s.type)) {
            w.setStatus(logging ? "Missing axe" : "Missing suitable pickaxe");
            return true;
        }
        Container output = StorageOps.at(l, s.output());
        if (logging) {
            for (int i = 0; i < 8; i++) {
                if (!l.getBlockState(s.at(SiteCatalog.PLOTS[i][0], 0, SiteCatalog.PLOTS[i][1]))
                        .is(net.minecraft.tags.BlockTags.DIRT)) {
                    w.setStatus("Repair tree plot foundation");
                    return true;
                }
                if (s.enabled[i]
                        && StorageOps.capacity(
                                        output,
                                        new ItemStack(
                                                net.minecraft.core.registries.BuiltInRegistries.ITEM
                                                        .get(
                                                                net.minecraft.resources
                                                                        .ResourceLocation
                                                                        .withDefaultNamespace(
                                                                                SiteCatalog.TREES[i]
                                                                                        + "_log"))))
                                < 1) {
                    w.setStatus("Logging storage full");
                    return true;
                }
            }
            for (int i = 0; i < 8; i++)
                if (s.enabled[i] && s.treeTimers[i] >= 0)
                    s.treeTimers[i] = Math.min(treeInterval(), s.treeTimers[i] + elapsed);
        } else {
            ItemStack result = new ItemStack(SiteCatalog.output(s.type), SiteCatalog.yield(s.type));
            if (StorageOps.capacity(output, result) < result.getCount()) {
                w.needsUnload = true;
                w.setStatus("Output storage full");
                return true;
            }
            s.production = Math.min(s.oreDelay, s.production + elapsed);
        }
        data.setDirty();
        for (int slot = 0; slot < w.bag.getContainerSize(); slot++)
            if (StorageOps.exportable(w, slot) > 0) {
                if (StorageOps.capacity(output, w.bag.getItem(slot)) == 0) {
                    w.needsUnload = true;
                    w.setStatus("Output storage full");
                    return true;
                }
                if (!w.approach(s.output())) return true;
                StorageOps.move(w.bag, slot, output, StorageOps.exportable(w, slot));
                w.needsUnload = false;
                w.setStatus("Depositing site output");
                data.setDirty();
                return true;
            }
        if (logging) logging(l, w, s, elapsed);
        else mining(l, w, s, elapsed);
        data.setDirty();
        return true;
    }

    static void suspend(Worker w) {
        if (w.siteId != null && w.level() instanceof ServerLevel l) {
            var site = SiteData.get(l).sites.get(w.siteId);
            if (site != null) site.lastActive = -1;
        }
    }

    static boolean loaded(ServerLevel l, SiteData.Site s) {
        int w = s.rotation % 2 == 0 ? s.width() : s.depth();
        int d = s.rotation % 2 == 0 ? s.depth() : s.width();
        for (int x = s.origin.getX() >> 4; x <= (s.origin.getX() + w - 1) >> 4; x++)
            for (int z = s.origin.getZ() >> 4; z <= (s.origin.getZ() + d - 1) >> 4; z++)
                if (!l.hasChunk(x, z)) return false;
        return true;
    }

    static boolean pickaxe(ItemStack tool, String type) {
        if (!(tool.getItem() instanceof PickaxeItem pick)) return false;
        String tier = SiteSettings.minimumPickaxe(type);
        return switch (tier) {
            case "wooden" -> true;
            case "stone" -> tool.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState());
            case "diamond" -> tool.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState());
            case "netherite" -> pick.getTier() == Tiers.NETHERITE;
            default -> tool.isCorrectToolForDrops(Blocks.GOLD_ORE.defaultBlockState());
        };
    }

    static int sampleDelay(ServerLevel l) {
        int min = 5, max = 25;
        try { min = SiteSettings.ORE_MIN.get(); max = Math.max(min, SiteSettings.ORE_MAX.get()); } catch (Exception ignored) {}
        return (min + l.random.nextInt(max - min + 1)) * 20;
    }

    static int interval(String type) {
        try {
            return SiteSettings.ORE_INTERVALS.get(type).get() * 20;
        } catch (Exception e) {
            return SiteCatalog.SECONDS[SiteCatalog.oreIndex(type)] * 20;
        }
    }

    private static void mining(ServerLevel l, Worker w, SiteData.Site s, int elapsed) {
        ItemStack result = new ItemStack(SiteCatalog.output(s.type), SiteCatalog.yield(s.type));
        if (StorageOps.capacity(w.bag, result) < result.getCount()
                || StorageOps.capacity(StorageOps.at(l, s.output()), result) < result.getCount()) {
            w.needsUnload = true;
            w.setStatus("Site storage full");
            return;
        }
        for (BlockPos node:s.ores())
            if (!l.getBlockState(node.below()).isSolidRender(l,node.below())) {
                w.setStatus("Repair mining platform");
                return;
            }
        for (BlockPos p:s.ores()) {
            var state = l.getBlockState(p);
            if (!state.isAir() && !state.is(SiteBlocks.managed("ore_" + s.type))) {
                w.setStatus("Ore position blocked at " + p.toShortString());
                return;
            }
            if (state.isAir() && s.production >= s.oreDelay
                    && l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(p)).isEmpty()) {
                l.setBlock(p, SiteBlocks.managed("ore_" + s.type).defaultBlockState(), 3);
                s.generated.add(p);
                s.production = 0;
                s.oreDelay = sampleDelay(l);
                break;
            }
        }
        for (BlockPos p:s.ores()) {
            if (l.getBlockState(p).is(SiteBlocks.managed("ore_" + s.type))) {
                if (!w.approach(p)) return;
                l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                s.generated.remove(p);
                w.bag.addItem(result);
                w.getMainHandItem().hurtAndBreak(1, w, EquipmentSlot.MAINHAND);
                w.setStatus("Mined " + s.type);
                return;
            }
        }
        w.setStatus("Ore regenerating: " + ((s.oreDelay - s.production + 19) / 20) + "s");
    }

    static int treeInterval() {
        try {
            return SiteSettings.TREE_SECONDS.get() * 20;
        } catch (Exception e) {
            return 2400;
        }
    }

    private static void logging(ServerLevel l, Worker w, SiteData.Site s, int elapsed) {
        int interval = treeInterval();
        int budget = WorkerConfig.scanBudget();
        for (int step = 0; step < 8; step++) {
            int i = Math.floorMod(s.treeCursor + step, 8);
            String type = SiteCatalog.TREES[i];
            var tree = SiteCatalog.tree(s, i);
            BlockPos base = s.at(SiteCatalog.PLOTS[i][0], 1, SiteCatalog.PLOTS[i][1]);
            if (s.treeTimers[i] >= interval
                    && (s.enabled[i]
                            || tree.keySet().stream()
                                    .anyMatch(
                                            p ->
                                                    l.getBlockState(p)
                                                            .is(
                                                                    SiteBlocks.managed(
                                                                            "log_" + type))))) {
                for (var e : tree.entrySet())
                    if (!l.getBlockState(e.getKey()).isAir()
                            && !l.getBlockState(e.getKey()).is(e.getValue().getBlock())
                            && !l.getBlockState(e.getKey())
                                    .is(SiteBlocks.managed("marker_" + type))) {
                        w.setStatus("Tree plot blocked: " + type);
                        return;
                    }
                boolean complete = true;
                for (var e : tree.entrySet())
                    if (!l.getBlockState(e.getKey()).equals(e.getValue())) {
                        if (budget-- <= 0) {
                            complete = false;
                            break;
                        }
                        l.setBlock(e.getKey(), e.getValue(), 3);
                        s.generated.add(e.getKey());
                    }
                if (complete) s.treeTimers[i] = -1;
                w.setStatus("Growing " + type);
                return;
            }
            if (s.treeTimers[i] != -1) continue;
            Item log =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                            net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                                    type + "_log"));
            ItemStack result = new ItemStack(log);
            if (StorageOps.capacity(w.bag, result) < 1
                    || StorageOps.capacity(StorageOps.at(l, s.output()), result) < 1) {
                w.needsUnload = true;
                w.setStatus("Logging storage full");
                return;
            }
            if (!w.approachWithin(base, 3, 3)) return;
            // Highest logs first; the worker stands at the contained tree plot.
            var logs =
                    tree.keySet().stream()
                            .filter(p -> l.getBlockState(p).is(SiteBlocks.managed("log_" + type)))
                            .sorted(Comparator.<BlockPos>comparingInt(p -> p.getY()).reversed())
                            .toList();
            if (!logs.isEmpty()) {
                BlockPos p = logs.get(0);
                l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                s.generated.remove(p);
                w.bag.addItem(result);
                w.getMainHandItem().hurtAndBreak(1, w, EquipmentSlot.MAINHAND);
                w.setStatus("Chopping " + type);
                s.treeCursor = (i + 1) % 8;
                return;
            }
            boolean clear = true;
            for (BlockPos p : tree.keySet())
                if (s.generated.contains(p)) {
                    if (budget-- <= 0) {
                        clear = false;
                        break;
                    }
                    if (l.getBlockState(p).getBlock() instanceof SiteBlocks.ManagedBlock)
                        l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    s.generated.remove(p);
                }
            if (clear) {
                if (!l.getBlockState(base).isAir()) {
                    w.setStatus("Replanting blocked: " + type);
                    return;
                }
                l.setBlock(base, SiteBlocks.managed("marker_" + type).defaultBlockState(), 3);
                s.generated.add(base);
                s.treeTimers[i] = 0;
                s.treeCursor = (i + 1) % 8;
            }
            w.setStatus("Replanting " + type);
            return;
        }
        w.setStatus("Waiting for enabled tree plots");
    }
}
