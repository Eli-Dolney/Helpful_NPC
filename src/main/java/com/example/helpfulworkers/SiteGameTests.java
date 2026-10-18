package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(HelpfulWorkers.ID)
@PrefixGameTestTemplate(false)
public final class SiteGameTests {
    static Worker worker(GameTestHelper h, ServerPlayer p, String role, BlockPos pos) {
        Worker w = HelpfulWorkers.WORKER.get().create(h.getLevel());
        w.setOwner(p.getUUID());
        w.role = role;
        w.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        h.getLevel().addFreshEntity(w);
        return w;
    }

    static SiteData.Site site(GameTestHelper h, ServerPlayer p, String type, BlockPos origin) {
        var s = new SiteData.Site();
        s.owner = p.getUUID();
        s.type = type;
        s.origin = origin;
        s.plotFirst = origin;
        s.plotSecond = origin.offset(SiteCatalog.width(type) - 1, 0, SiteCatalog.depth(type) - 1);
        s.phase = "active";
        s.paid = true;
        SiteData.get(h.getLevel()).add(s);
        for (var piece : SiteCatalog.pieces(type)) {
            BlockPos q = s.at(piece.x(), piece.y(), piece.z());
            h.getLevel().setBlock(q, piece.state(), 2 | 16);
            if (piece.state().getBlock() instanceof SiteBlocks.ManagedBlock) s.generated.add(q);
        }
        return s;
    }

    static void floor(GameTestHelper h, int size) {
        BlockPos base = h.absolutePos(new BlockPos(1, 1, 1));
        for (int x = 0; x < size; x++)
            for (int z = 0; z < size; z++)
                h.getLevel().setBlock(base.offset(x, 0, z), Blocks.DIRT.defaultBlockState(), 3);
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void centersRotationsAndRejectsSmallPlots(GameTestHelper h) {
        floor(h, 40);
        var p = h.makeMockServerPlayerInLevel();
        var b = worker(h, p, "builder", h.absolutePos(new BlockPos(3, 2, 3)));
        var a = h.absolutePos(new BlockPos(1, 1, 1));
        var c = a.offset(38, 0, 36);
        for (int r = 0; r < 4; r++) {
            var s = SiteConstruction.plan(p, b, "warehouse", a, c, r, null);
            int w = r % 2 == 0 ? 25 : 21, d = r % 2 == 0 ? 21 : 25;
            h.assertTrue(s.origin.getX() == a.getX() + (39 - w) / 2, "Centered X");
            h.assertTrue(s.origin.getZ() == a.getZ() + (37 - d) / 2, "Centered Z");
            for (var piece : SiteCatalog.pieces("warehouse")) {
                var q = s.at(piece.x(), piece.y(), piece.z());
                h.assertTrue(
                        q.getX() >= a.getX()
                                && q.getX() <= c.getX()
                                && q.getZ() >= a.getZ()
                                && q.getZ() <= c.getZ(),
                        "Rotated building stays in plot");
            }
        }
        boolean failed = false;
        try {
            SiteConstruction.plan(p, b, "logging", a, a.offset(12, 0, 12), 0, null);
        } catch (IllegalArgumentException e) {
            failed = true;
        }
        h.assertTrue(failed, "Small logging plot rejected");
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void levelingProtectsContainersAndFluids(GameTestHelper h) {
        floor(h, 10);
        var p = h.makeMockServerPlayerInLevel();
        var b = worker(h, p, "builder", h.absolutePos(new BlockPos(2, 2, 2)));
        var a = h.absolutePos(new BlockPos(1, 1, 1));
        for (Block obstruction :
                new Block[] {Blocks.CHEST, Blocks.WATER, Blocks.IRON_ORE, Blocks.BEDROCK}) {
            h.getLevel().setBlock(a.offset(3, 1, 3), obstruction.defaultBlockState(), 3);
            boolean failed = false;
            try {
                SiteConstruction.plan(p, b, "iron", a, a.offset(6, 0, 6), 0, a.getY());
            } catch (IllegalArgumentException e) {
                failed = true;
            }
            h.assertTrue(failed, "Protected terrain rejected: " + obstruction);
            h.getLevel().setBlock(a.offset(3, 1, 3), Blocks.AIR.defaultBlockState(), 3);
        }
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void interruptedConstructionPaysOnceAndCompletes(GameTestHelper h) {
        floor(h, 10);
        var p = h.makeMockServerPlayerInLevel();
        var a = h.absolutePos(new BlockPos(1, 1, 1));
        var b = worker(h, p, "builder", a.above());
        p.getAbilities().instabuild = false;
        p.getInventory().add(new ItemStack(SiteBlocks.coreItem("iron"), 2));
        var session = new SiteUi.Session();
        session.type = "iron";
        session.first = a;
        session.second = a.offset(6, 0, 6);
        session.floor = a.getY();
        SiteActions.construct(p, b, session);
        var s = SiteData.get(h.getLevel()).sites.get(b.constructionId);
        h.assertTrue(
                p.getInventory().countItem(SiteBlocks.coreItem("iron")) == 1, "One core consumed");
        var root =
                SiteData.get(h.getLevel())
                        .save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
        var restored = SiteData.load(root, h.getLevel().registryAccess()).sites.get(s.id);
        h.assertTrue(
                restored.paid && restored.builder.equals(b.getUUID()),
                "Paid construction persists");
        s.instant=true;
        b.teleportTo(a.getX()+8.5,a.getY()+1,a.getZ()+8.5);
        int loops = 0;
        while (!s.active() && loops++ < 300) SiteConstruction.tick(h.getLevel(),b,s);
        h.assertTrue(s.active(), "Construction completed: " + s.status);
        h.assertTrue(
                p.getInventory().countItem(SiteBlocks.coreItem("iron")) == 1,
                "Resume does not charge again");
        h.assertTrue(StorageOps.at(h.getLevel(), s.output()).isEmpty(), "Output initially empty");
        SiteActions.dismantle(p, s);
        h.assertTrue(
                p.getInventory().countItem(SiteBlocks.coreItem("iron")) == 2, "Core refunded once");
        boolean refused = false;
        try {
            SiteActions.dismantle(p, s);
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        h.assertTrue(refused, "Repeated dismantle rejected");
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void allOreYieldsAndToolGates(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        int i = 0;
        for (String ore : SiteCatalog.ORES) {
            var s =
                    site(
                            h,
                            p,
                            ore,
                            h.absolutePos(new BlockPos(2 + (i % 4) * 10, 1, 2 + (i / 4) * 12)));
            var w = worker(h, p, "miner", s.at(3, 1, 2));
            w.siteId = s.id;
            s.worker = w.getUUID();
            w.working = true;
            w.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            s.oreDelay = 100;
            s.production = s.oreDelay;
            SiteJobs.tick(h.getLevel(), w);
            h.assertTrue(
                    w.bag.countItem(SiteCatalog.output(ore)) == SiteCatalog.yield(ore),
                    "Correct " + ore + " yield");
            h.assertTrue(w.getMainHandItem().getDamageValue() == 1, "Tool durability spent");
            h.assertFalse(
                    SiteJobs.pickaxe(new ItemStack(Items.WOODEN_PICKAXE), "diamond"),
                    "Diamond requires iron pickaxe");
            w.bag.clearContent();
            w.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            w.bag.setItem(0, new ItemStack(Items.IRON_PICKAXE));
            s.oreDelay = 100;
            s.production = s.oreDelay;
            SiteJobs.tick(h.getLevel(), w);
            h.assertTrue(w.getMainHandItem().is(Items.IRON_PICKAXE), "Supplied spare equipped");
            i++;
        }
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void fullOutputStopsProductionAndNoCatchup(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        var s = site(h, p, "iron", h.absolutePos(new BlockPos(2, 1, 2)));
        var w = worker(h, p, "miner", s.at(3, 1, 2));
        w.siteId = s.id;
        s.worker = w.getUUID();
        w.working = true;
        w.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        var output = StorageOps.at(h.getLevel(), s.output());
        for (int i = 0; i < output.getContainerSize(); i++)
            output.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        s.oreDelay = 500;
        s.production = 100;
        s.lastActive = h.getLevel().getGameTime() - 20000;
        SiteJobs.tick(h.getLevel(), w);
        h.assertTrue(s.production == 100, "Full output pauses timer");
        output.clearContent();
        s.lastActive = -1;
        SiteJobs.tick(h.getLevel(), w);
        h.assertTrue(s.production == 100, "No unloaded catch-up");
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void eightTreePlotsAndPersistence(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        var s = site(h, p, "logging", h.absolutePos(new BlockPos(1, 1, 1)));
        var w = worker(h, p, "forester", s.at(6, 1, 7));
        w.siteId = s.id;
        s.worker = w.getUUID();
        w.working = true;
        w.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        NaturalTrees.ensure(h.getLevel(),s);
        h.assertTrue(s.treeSlots.size()==8,"Eight distinct natural growing slots");
        for(int i=0;i<8;i++) {
            var slot=s.treeSlots.get(i);
            w.teleportTo(slot.base.getX()+.5,slot.base.getY(),slot.base.getZ()+2.5);
            s.treeCursor=i;
            NaturalTrees.tick(h.getLevel(),w,s);
            for(var q:NaturalTrees.planting(slot)) h.assertTrue(h.getLevel().getBlockState(q).is(NaturalTrees.sapling(slot.species)),"Real sapling planted: "+slot.species+" "+w.status);
        }
        s.treeSlots.get(3).enabled=false;
        var saved=SiteData.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());
        var copy=SiteData.load(saved,h.getLevel().registryAccess()).sites.get(s.id);
        h.assertFalse(copy.treeSlots.get(3).enabled,"Species toggle persists");
        h.assertTrue(copy.treeSlots.size()==8 && copy.lastActive==-1,"Slots persist, active clock resets");
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void warehouseDoubleChestsAndFilterPrecedence(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        var s = site(h, p, "warehouse", h.absolutePos(new BlockPos(1, 1, 1)));
        WarehouseJobs.initialize(h.getLevel(), s);
        h.assertTrue(s.storage.size() == 34, "32 doubles + incoming + overflow");
        for (int i = 0; i < 32; i++)
            h.assertTrue(
                    StorageOps.at(h.getLevel(), s.storage.get(i).pos).getContainerSize() == 54,
                    "Double chest merged");
        var w = worker(h, p, "courier", s.at(12, 1, 11));
        var exact = s.storage.get(0);
        exact.filter = "item:minecraft:iron_ingot";
        var general = s.storage.get(1);
        general.filter = "ores_minerals";
        h.assertTrue(
                WarehouseJobs.destination(h.getLevel(), w, s, new ItemStack(Items.IRON_INGOT), null)
                        == exact,
                "Exact before category");
        var c = StorageOps.at(h.getLevel(), exact.pos);
        for (int i = 0; i < c.getContainerSize(); i++)
            c.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        h.assertTrue(
                WarehouseJobs.destination(h.getLevel(), w, s, new ItemStack(Items.IRON_INGOT), null)
                        != exact,
                "Full exact falls back");
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void transfersConserveItemsAndReserveSupplies(GameTestHelper h) {
        var from = new SimpleContainer(2);
        var to = new SimpleContainer(1);
        from.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        to.setItem(0, new ItemStack(Items.IRON_INGOT, 60));
        h.assertTrue(StorageOps.move(from, 0, to, 64) == 4, "Transfer respects room");
        h.assertTrue(
                from.getItem(0).getCount() + to.getItem(0).getCount() == 124,
                "Partial transfer conserves items");
        var p = h.makeMockServerPlayerInLevel();
        var w = worker(h, p, "farmer", h.absolutePos(new BlockPos(2, 2, 2)));
        w.bag.setItem(0, new ItemStack(Items.IRON_HOE));
        w.bag.setItem(1, new ItemStack(Items.WHEAT_SEEDS, 64));
        w.bag.setItem(2, new ItemStack(Items.WHEAT_SEEDS, 32));
        w.bag.setItem(3, new ItemStack(Items.COBBLESTONE, 64));
        h.assertTrue(StorageOps.exportable(w, 0) == 0, "Tools reserved");
        h.assertTrue(StorageOps.exportable(w, 1) == 32, "Planting reserve kept");
        h.assertTrue(StorageOps.exportable(w, 3) == 64, "Harvest cargo exportable");
        w.role = "builder";
        h.assertTrue(StorageOps.exportable(w, 3) == 0, "Builder materials reserved");
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void ownershipAndZoneCompatibility(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        var other = h.makeMockServerPlayerInLevel();
        var s = site(h, p, "iron", h.absolutePos(new BlockPos(2, 1, 2)));
        var w = worker(h, other, "miner", s.at(3, 1, 2));
        boolean rejected = false;
        try {
            SiteActions.assign(other, w, s);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        h.assertTrue(rejected, "Other owner rejected");
        w.setOwner(p.getUUID());
        SiteActions.assign(p, w, s);
        h.assertTrue(w.siteId.equals(s.id), "Site assigned");
        SiteActions.release(h.getLevel(), w);
        h.assertTrue(w.siteId == null && !w.working, "Released to paused zone work");
        h.assertTrue(
                WorkerActions.setArea(p, w, s.origin, s.origin.offset(3, 1, 3)).success(),
                "Legacy area still works");
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void couriersCollectSortAndReserveWorker(GameTestHelper h) {
        WarehouseJobs.clear();
        var p = h.makeMockServerPlayerInLevel();
        var s = site(h, p, "warehouse", h.absolutePos(new BlockPos(1, 1, 1)));
        WarehouseJobs.initialize(h.getLevel(), s);
        var a = worker(h, p, "courier", s.at(11, 1, 11));
        var b = worker(h, p, "courier", s.at(11, 1, 11));
        SiteActions.assign(p, a, s);
        SiteActions.assign(p, b, s);
        a.working = true;
        b.working = true;
        var incoming = StorageOps.at(h.getLevel(), s.at(11, 1, 10));
        incoming.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        s.storage.get(0).filter = "item:minecraft:iron_ingot";
        WarehouseJobs.tick(h.getLevel(), a, s);
        WarehouseJobs.tick(h.getLevel(), b, s);
        h.assertTrue(
                WarehouseJobs.TASKS.containsKey(a.getUUID()),
                "First courier claims incoming stack");
        h.assertFalse(
                WarehouseJobs.TASKS.containsKey(b.getUUID()),
                "Second courier cannot claim same stack");
        WarehouseJobs.tick(h.getLevel(), a, s);
        h.assertTrue(
                incoming.isEmpty() && a.bag.countItem(Items.IRON_INGOT) == 64,
                "Pickup conserves stack");
        var dest = s.storage.get(0).pos;
        a.teleportTo(dest.getX() + .5, dest.getY(), dest.getZ() + 1.5);
        WarehouseJobs.tick(h.getLevel(), a, s);
        h.assertTrue(
                a.bag.isEmpty()
                        && StorageOps.at(h.getLevel(), dest).countItem(Items.IRON_INGOT) == 64,
                "Courier delivers matching items");
        var source = worker(h, p, "miner", s.at(11, 1, 11));
        source.bag.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        for (int i = 1; i < 24; i++) source.bag.setItem(i, new ItemStack(Items.RAW_IRON, 64));
        source.needsUnload = true;
        source.status = Jobs.OUT_OF_SPACE;
        source.working = false;
        s.linkedWorkers.add(source.getUUID());
        a.teleportTo(source.getX(), source.getY(), source.getZ() + 1);
        b.teleportTo(source.getX(), source.getY(), source.getZ() + 1);
        WarehouseJobs.tick(h.getLevel(), a, s);
        WarehouseJobs.tick(h.getLevel(), b, s);
        h.assertTrue(WarehouseJobs.TASKS.containsKey(a.getUUID()), "Courier claims full worker");
        h.assertFalse(
                WarehouseJobs.TASKS.containsKey(b.getUUID()),
                "Other courier cannot unload another slot of same worker");
        WarehouseJobs.tick(h.getLevel(), a, s);
        h.assertTrue(source.bag.getItem(0).is(Items.IRON_PICKAXE), "Worker keeps tool");
        h.assertTrue(source.working && !source.needsUnload, "Worker resumes after unload");
        h.assertTrue(
                source.bag.countItem(Items.RAW_IRON) + a.bag.countItem(Items.RAW_IRON) == 23 * 64,
                "Unload conserves output");
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void managedResourcesRejectHarvestAndBlueprints(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        var s = site(h, p, "iron", h.absolutePos(new BlockPos(1, 1, 1)));
        BlockPos q = s.at(3, 1, 1);
        var state = SiteBlocks.managed("ore_iron").defaultBlockState();
        h.getLevel().setBlock(q, state, 3);
        var e =
                new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                        h.getLevel(), q, state, p);
        SiteEvents.breakBlock(e);
        h.assertTrue(e.isCanceled(), "Player harvest rejected");
        h.assertTrue(
                state.getPistonPushReaction()
                        == net.minecraft.world.level.material.PushReaction.BLOCK,
                "Pistons cannot move managed ore");
        var w = worker(h, p, "builder", s.at(3, 1, 2));
        w.first = q;
        w.second = q;
        h.assertTrue(
                Blueprints.capture(h.getLevel(), w, "exploit").contains("cannot be copied"),
                "Active site cannot be copied");
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void warehouseSixtyFourInventoriesAndCorrection(GameTestHelper h) {
        WarehouseJobs.clear();
        var p = h.makeMockServerPlayerInLevel();
        var s = site(h, p, "warehouse", h.absolutePos(new BlockPos(1, 1, 1)));
        WarehouseJobs.initialize(h.getLevel(), s);
        for (int i = 0; i < 30; i++) {
            BlockPos q = s.origin.offset(28 + i % 6, 1, 2 + i / 6 * 2);
            h.getLevel().setBlock(q, Blocks.BARREL.defaultBlockState(), 3);
            var st = new SiteData.Storage(q);
            st.filter = "wood";
            s.storage.add(st);
        }
        h.assertTrue(s.storage.size() == 64, "64 inventories registered");
        var a = worker(h, p, "courier", s.at(11, 1, 11));
        var b = worker(h, p, "courier", s.at(11, 1, 11));
        SiteActions.assign(p, a, s);
        SiteActions.assign(p, b, s);
        a.working = true;
        b.working = true;
        var wrong = s.storage.get(0);
        wrong.filter = "wood";
        StorageOps.at(h.getLevel(), wrong.pos).setItem(0, new ItemStack(Items.DIAMOND, 16));
        for (int i = 0; i < 200 && !WarehouseJobs.TASKS.containsKey(a.getUUID()); i++)
            WarehouseJobs.tick(h.getLevel(), a, s);
        h.assertTrue(
                WarehouseJobs.TASKS.containsKey(a.getUUID()),
                "Misplaced stack eventually scheduled among 64 containers");
        a.teleportTo(wrong.pos.getX() + .5, wrong.pos.getY(), wrong.pos.getZ() + 1.5);
        WarehouseJobs.tick(h.getLevel(), a, s);
        h.assertTrue(a.bag.countItem(Items.DIAMOND) == 16, "Correction pickup");
        var dest =
                WarehouseJobs.destination(h.getLevel(), a, s, new ItemStack(Items.DIAMOND), null);
        a.teleportTo(dest.pos.getX() + .5, dest.pos.getY(), dest.pos.getZ() + 1.5);
        WarehouseJobs.tick(h.getLevel(), a, s);
        for (int i = 0; i < 80; i++) {
            WarehouseJobs.tick(h.getLevel(), a, s);
            WarehouseJobs.tick(h.getLevel(), b, s);
        }
        h.assertTrue(
                a.bag.isEmpty() && b.bag.isEmpty(),
                "Correctly sorted items are not moved repeatedly");
        h.assertTrue(
                StorageOps.at(h.getLevel(), dest.pos).countItem(Items.DIAMOND) == 16,
                "Correction conserved items");
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void disabledTreesFinishHarvestAndReplant(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        var s = site(h, p, "logging", h.absolutePos(new BlockPos(1, 1, 1)));
        var w = worker(h, p, "forester", s.at(6, 1, 7));
        w.siteId = s.id;
        s.worker = w.getUUID();
        w.working = true;
        w.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        int expected = 0;
        for (int i = 0; i < 8; i++) {
            for (var e : SiteCatalog.tree(s, i).entrySet()) {
                h.getLevel().setBlock(e.getKey(), e.getValue(), 3);
                s.generated.add(e.getKey());
                if (e.getValue().is(SiteBlocks.managed("log_" + SiteCatalog.TREES[i]))) expected++;
            }
            s.enabled[i] = false;
            s.treeTimers[i] = -1;
        }
        NaturalTrees.ensure(h.getLevel(),s);
        for (int step = 0; step < 500; step++) {
            BlockPos stand = s.output().south();
            if (w.bag.isEmpty())
                for (int n = 0; n < 8; n++) {
                    int i = (s.treeCursor + n) % 8;
                    if (!s.treeSlots.get(i).logs.isEmpty() || s.treeSlots.get(i).legacy) {
                        stand = s.at(SiteCatalog.PLOTS[i][0], 1, SiteCatalog.PLOTS[i][1] + 2);
                        break;
                    }
                }
            w.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
            SiteJobs.tick(h.getLevel(), w);
        }
        int actual = 0;
        var out = StorageOps.at(h.getLevel(), s.output());
        for (int i = 0; i < out.getContainerSize(); i++) actual += out.getItem(i).getCount();
        h.assertTrue(
                actual == expected,
                "All generated logs deposited exactly once: "
                        + actual
                        + "/"
                        + expected
                        + " "
                        + w.status);
        h.assertTrue(s.generated.isEmpty(), "Legacy resources cleaned without regrowth");
        h.assertTrue(s.treeSlots.stream().noneMatch(slot -> slot.enabled || !slot.logs.isEmpty()),"Disabled slots finish harvest without regrowing");
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void blockedSiteRoutePausesWithoutTeleport(GameTestHelper h) {
        floor(h, 12);
        var p = h.makeMockServerPlayerInLevel();
        var s = site(h, p, "iron", h.absolutePos(new BlockPos(1, 1, 1)));
        var w = worker(h, p, "miner", s.at(3, 1, 4));
        w.siteId = s.id;
        w.working = true;
        for (int x = 1; x <= 5; x++)
            for (int y = 1; y <= 3; y++)
                h.getLevel().setBlock(s.at(x, y, 3), Blocks.STONE.defaultBlockState(), 3);
        var before = w.position();
        h.assertFalse(w.approach(s.at(3, 1, 2)), "Cannot transfer through wall");
        h.assertTrue(w.working, "Blocked route keeps worker eligible to retry automatically");
        h.assertTrue(w.position().equals(before), "No teleport around obstruction");
        h.assertFalse(
                SiteActions.canRegister(h.getLevel(), UUID.randomUUID(), s.output()),
                "Other player cannot register site output");
        h.succeed();
    }

    @GameTest(template = "site_test", skyAccess = true)
    public static void unevenEvenPlotsAndFoundationLimit(GameTestHelper h) {
        floor(h, 40);
        var p = h.makeMockServerPlayerInLevel();
        var a = h.absolutePos(new BlockPos(1, 1, 1));
        var b = worker(h, p, "builder", a.above());
        for (int x = 3; x < 12; x++)
            for (int z = 3; z < 30; z++)
                h.getLevel().setBlock(a.offset(x, 1, z), Blocks.DIRT.defaultBlockState(), 3);
        for (int r = 0; r < 4; r++) {
            var s = SiteConstruction.plan(p, b, "warehouse", a, a.offset(39, 0, 37), r, null);
            int w = r % 2 == 0 ? 25 : 21, d = r % 2 == 0 ? 21 : 25;
            h.assertTrue(
                    s.origin.getX() == a.getX() + (40 - w) / 2
                            && s.origin.getZ() == a.getZ() + (38 - d) / 2,
                    "Even plots use deterministic lower corner");
            h.assertTrue(
                    s.origin.getY() == a.getY(),
                    "Median ignores the raised minority of the footprint");
        }
        var s =
                SiteConstruction.plan(
                        p, b, "iron", a.offset(30, 0, 30), a.offset(36, 0, 36), 0, a.getY() + 8);
        h.assertTrue(s.origin.getY() == a.getY() + 8, "Eight-block fill allowed");
        boolean rejected = false;
        try {
            SiteConstruction.plan(
                    p, b, "iron", a.offset(30, 0, 30), a.offset(36, 0, 36), 0, a.getY() + 9);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        h.assertTrue(rejected, "Nine-block fill rejected");
        h.succeed();
    }
}
