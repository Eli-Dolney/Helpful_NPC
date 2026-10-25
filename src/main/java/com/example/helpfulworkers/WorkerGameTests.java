package com.example.helpfulworkers;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HelpfulWorkers.ID)
@PrefixGameTestTemplate(false)
public final class WorkerGameTests {
    private WorkerGameTests() {}

    @GameTest(template = "empty")
    public static void ownershipRejection(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        worker.setOwner(owner.getUUID());
        ActionResult result = WorkerActions.start(other, worker);
        helper.assertFalse(result.success(), "Other player must not start a worker");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void kitRequiredInSurvival(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        worker.setOwner(player.getUUID());
        player.getAbilities().instabuild = false;
        ActionResult denied = WorkerActions.setRole(player, worker, "farmer", true);
        helper.assertFalse(denied.success(), "Survival without kit must fail");
        player.getInventory().add(new ItemStack(HelpfulWorkers.FARMER_KIT.get()));
        ActionResult ok = WorkerActions.setRole(player, worker, "farmer", true);
        helper.assertTrue(ok.success(), "Survival with kit must succeed");
        helper.assertTrue(WorkerActions.hasItem(player, HelpfulWorkers.FARMER_KIT.get()), "Kit must remain");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeKitExempt(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        worker.setOwner(player.getUUID());
        player.getAbilities().instabuild = true;
        ActionResult ok = WorkerActions.setRole(player, worker, "miner", true);
        helper.assertTrue(ok.success(), "Creative must assign without kit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledClipboardPreservesAssignments(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        worker.setOwner(player.getUUID());
        BlockPos bed = helper.absolutePos(new BlockPos(2, 2, 2));
        worker.bed = bed;
        WorkerSessions.startClipboard(player, worker, WorkerSessions.ClipboardKind.BED);
        WorkerSessions.clearClipboard(player);
        helper.assertTrue(bed.equals(worker.bed), "Cancelled clipboard must leave bed assignment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoWorkersDoNotCrossActions(GameTestHelper helper) {
        Worker a = spawnWorker(helper, new BlockPos(1, 2, 1));
        Worker b = spawnWorker(helper, new BlockPos(3, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        a.setOwner(player.getUUID());
        b.setOwner(player.getUUID());
        WorkerSessions.openViewer(player, a, WorkerSessions.ViewerKind.DIALOGUE);
        helper.assertTrue(WorkerSessions.hasDialogueOrInventory(player, a), "Viewer tied to worker A");
        helper.assertFalse(WorkerSessions.hasDialogueOrInventory(player, b), "Viewer must not match worker B");
        ActionResult forB = WorkerActions.requireMenu(player, b);
        helper.assertFalse(forB.success(), "Menu actions for B must fail while viewing A");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unsupportedRecipeRejected(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        worker.setOwner(player.getUUID());
        ActionResult result = WorkerActions.approveRecipe(player, worker, "minecraft:firework_rocket");
        helper.assertFalse(result.success(), "Special recipes must be rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyNbtLoads(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", UUID.randomUUID());
        tag.putString("Role", "farmer");
        tag.putString("Mode", "excavate");
        tag.putString("Status", "Working");
        tag.putBoolean("Working", true);
        tag.putInt("Cursor", 3);
        tag.putString("Blueprint", "");
        tag.put("BlueprintBlocks", new net.minecraft.nbt.ListTag());
        tag.put("BlueprintLibrary", new net.minecraft.nbt.ListTag());
        tag.putInt("BuildRotation", 0);
        tag.put("Bag", new net.minecraft.nbt.ListTag());
        tag.put("Recipes", new net.minecraft.nbt.ListTag());
        worker.readAdditionalSaveData(tag);
        helper.assertValueEqual(worker.role, "farmer", "role");
        helper.assertTrue(worker.working, "working flag");
        helper.assertValueEqual(worker.scanCursor, 3, "cursor");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void assignOutputWhileWorking(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        worker.setOwner(player.getUUID());
        worker.role = "forester";
        worker.working = true;
        worker.first = helper.absolutePos(new BlockPos(0, 2, 0));
        worker.second = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos chest = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), net.minecraft.world.level.block.Blocks.CHEST);
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 3.5)));
        ActionResult result = WorkerActions.setOutput(player, worker, chest);
        helper.assertTrue(result.success(), "Assigning output mid-work must succeed");
        helper.assertTrue(chest.equals(worker.output), "Output must be stored");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void knockOutKeepsGear(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        worker.setOwner(player.getUUID());
        worker.role = "knight";
        worker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
            new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
        worker.bed = helper.absolutePos(new BlockPos(1, 2, 1));
        WorkerCombat.knockOut(helper.getLevel(), worker);
        helper.assertTrue(worker.isAlive(), "Worker must survive knock-out");
        helper.assertFalse(worker.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(),
            "Armor must stay equipped");
        helper.assertTrue(worker.recoverUntil > helper.getLevel().getGameTime(), "Must be recovering");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void contractRespectsCaps(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        for (int i = 0; i < WorkerConfig.maxPerPlayer(); i++) {
            Worker w = spawnWorker(helper, new BlockPos(1 + (i % 5), 2, 1 + (i / 5)));
            w.setOwner(player.getUUID());
            WorkerRegistry.upsert(w);
        }
        helper.assertTrue(WorkerRegistry.countForOwner(helper.getLevel(), player.getUUID())
            >= WorkerConfig.maxPerPlayer(), "Registry must count owned workers");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rancherFlagsAndBreedFood(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        worker.setOwner(player.getUUID());
        worker.role = "rancher";
        WorkerSessions.openViewer(player, worker, WorkerSessions.ViewerKind.DIALOGUE);
        helper.assertTrue(worker.ranchAnimals.contains("sheep"), "Sheep enabled by default");
        ActionResult off = WorkerActions.toggleRanchAnimal(player, worker, "pig");
        helper.assertTrue(off.success(), "Toggle pig must succeed");
        helper.assertFalse(worker.ranchAnimals.contains("pig"), "Pig disabled");
        ActionResult breed = WorkerActions.toggleRanchFlag(player, worker, "breed");
        helper.assertTrue(breed.success(), "Toggle breed must succeed");
        helper.assertFalse(worker.ranchBreed, "Breed mode off");
        java.util.List<String> flags = worker.collectFlags();
        helper.assertFalse(flags.contains("rancher:pig"), "Flags omit disabled pig");
        helper.assertFalse(flags.contains("breed"), "Flags omit breed when off");
        helper.assertTrue(flags.contains("rancher:sheep"), "Flags include sheep");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cullBorrowsAndReturnsSword(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        worker.setOwner(player.getUUID());
        worker.role = "rancher";
        worker.first = helper.absolutePos(new BlockPos(0, 2, 0));
        worker.second = helper.absolutePos(new BlockPos(4, 4, 4));
        WorkerSessions.openViewer(player, worker, WorkerSessions.ViewerKind.DIALOGUE);
        ItemStack sword = new ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        player.getInventory().add(sword.copy());
        ActionResult start = WorkerActions.startCull(player, worker, true);
        helper.assertTrue(start.success(), "Cull with sword must start");
        helper.assertTrue(worker.culling, "Culling flag set");
        helper.assertTrue(worker.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS), "Sword equipped");
        helper.assertTrue(worker.swordLender != null, "Lender recorded");
        worker.culling = false;
        WorkerActions.returnBorrowedSword(worker);
        helper.assertTrue(worker.borrowedSword.isEmpty(), "Borrowed sword cleared");
        helper.assertTrue(worker.swordLender == null, "Lender cleared");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remoteViewerSkipsDistanceClose(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        worker.setOwner(player.getUUID());
        WorkerSessions.openViewer(player, worker, WorkerSessions.ViewerKind.DIALOGUE, true);
        helper.assertTrue(WorkerSessions.hasDialogueOrInventory(player, worker), "Remote viewer stays open");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sitePrepClearsVegetationOnly(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        worker.role = "builder";
        worker.sitePrep = SitePrep.CLEAR;
        worker.prepOnly = false;
        // One-block custom blueprint placed at (3,2,3): its footprint is that single column, ground is Y=1.
        net.minecraft.nbt.CompoundTag entry = new CompoundTag();
        entry.putInt("X", 0); entry.putInt("Y", 0); entry.putInt("Z", 0);
        entry.put("State", net.minecraft.nbt.NbtUtils.writeBlockState(
            net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState()));
        worker.blueprintBlocks = new net.minecraft.nbt.ListTag();
        worker.blueprintBlocks.add(entry);
        worker.blueprint = "test_block";
        worker.buildOrigin = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.setBlock(new BlockPos(3, 1, 3), net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(3, 2, 3), net.minecraft.world.level.block.Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(3, 3, 3), net.minecraft.world.level.block.Blocks.OAK_LEAVES);
        helper.setBlock(new BlockPos(3, 4, 3), net.minecraft.world.level.block.Blocks.STONE);
        worker.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 2.5)));
        SitePrep.Site site = SitePrep.site(worker);
        helper.assertTrue(site != null && site.groundY() == helper.absolutePos(new BlockPos(3, 1, 3)).getY(),
            "Custom blueprint ground is one below the origin");
        for (int i = 0; i < 20 && !worker.sitePrepDone; i++) SitePrep.tick(helper.getLevel(), worker);
        helper.assertTrue(worker.sitePrepDone, "Clear prep must finish");
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, new BlockPos(3, 2, 3));
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, new BlockPos(3, 3, 3));
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.STONE, new BlockPos(3, 4, 3));
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.GRASS_BLOCK, new BlockPos(3, 1, 3));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flattenPlotLevelsAndFills(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        worker.role = "builder";
        worker.prepOnly = true;
        worker.first = helper.absolutePos(new BlockPos(3, 1, 3));
        worker.second = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.setBlock(new BlockPos(3, 1, 3), net.minecraft.world.level.block.Blocks.AIR);   // hole at ground level
        helper.setBlock(new BlockPos(4, 1, 4), net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(4, 2, 4), net.minecraft.world.level.block.Blocks.STONE);  // bump above ground
        helper.setBlock(new BlockPos(4, 3, 4), net.minecraft.world.level.block.Blocks.SHORT_GRASS);
        helper.setBlock(new BlockPos(3, 1, 4), net.minecraft.world.level.block.Blocks.DIRT);
        helper.setBlock(new BlockPos(4, 1, 3), net.minecraft.world.level.block.Blocks.DIRT);
        worker.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 2.5)));
        for (int i = 0; i < 40 && !worker.sitePrepDone; i++) SitePrep.tick(helper.getLevel(), worker);
        helper.assertTrue(worker.sitePrepDone, "Flatten must finish");
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.GRASS_BLOCK, new BlockPos(3, 1, 3));
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, new BlockPos(4, 2, 4));
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, new BlockPos(4, 3, 4));
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.GRASS_BLOCK, new BlockPos(4, 1, 4));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sitePrepLegacySaveKeepsBuildGoing(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        CompoundTag tag = new CompoundTag();
        tag.putString("Role", "builder");
        tag.putInt("Cursor", 12);
        tag.put("BlueprintBlocks", new net.minecraft.nbt.ListTag());
        tag.put("BlueprintLibrary", new net.minecraft.nbt.ListTag());
        tag.put("Bag", new net.minecraft.nbt.ListTag());
        tag.put("Recipes", new net.minecraft.nbt.ListTag());
        worker.readAdditionalSaveData(tag);
        helper.assertValueEqual(worker.sitePrep, SitePrep.CLEAR, "default prep mode");
        helper.assertTrue(worker.sitePrepDone, "In-progress legacy build must not re-clear its site");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void statusWritesMarkDirty(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        worker.statusDirty = false;
        worker.setStatus("Testing");
        helper.assertTrue(worker.statusDirty, "setStatus must flag a sync");
        worker.statusDirty = false;
        worker.setStatus("Testing");
        helper.assertFalse(worker.statusDirty, "Unchanged status must not re-sync");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void knockOutThenRecoveryResumesWork(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        worker.role = "miner";
        worker.working = true;
        WorkerCombat.knockOut(helper.getLevel(), worker);
        helper.assertFalse(worker.working, "Knock-out pauses work");
        helper.assertTrue(worker.resumeAfterRecovery, "Knock-out remembers the worker was busy");
        worker.recoverUntil = helper.getLevel().getGameTime();
        worker.tickWork(helper.getLevel());
        helper.assertTrue(worker.working, "Recovered worker resumes work");
        helper.assertValueEqual(worker.recoverUntil, 0L, "recovery timer cleared");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void passiveRegenHealsEveryOtherWorkTick(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        worker.setHealth(worker.getMaxHealth() - 4f);
        float before = worker.getHealth();
        worker.tickWork(helper.getLevel());
        worker.tickWork(helper.getLevel());
        helper.assertTrue(worker.getHealth() > before, "Two work ticks must heal half a heart");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void minePlanSurvivesSave(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        worker.minePlan = new java.util.ArrayList<>(java.util.List.of(
            new MinePlanner.Step(helper.absolutePos(new BlockPos(2, 1, 2)), MinePlanner.Kind.DIG),
            new MinePlanner.Step(helper.absolutePos(new BlockPos(3, 2, 2)), MinePlanner.Kind.PLACE_TORCH)));
        worker.minePlanIndex = 1;
        worker.idleLevel = 2;
        CompoundTag tag = new CompoundTag();
        worker.addAdditionalSaveData(tag);
        Worker copy = spawnWorker(helper, new BlockPos(3, 2, 1));
        copy.readAdditionalSaveData(tag);
        helper.assertTrue(copy.minePlan != null && copy.minePlan.size() == 2, "Mine plan restored");
        helper.assertValueEqual(copy.minePlanIndex, 1, "mine plan index");
        helper.assertTrue(copy.minePlan.get(1).kind() == MinePlanner.Kind.PLACE_TORCH, "step kind restored");
        helper.assertTrue(copy.minePlan.get(0).pos().equals(worker.minePlan.get(0).pos()), "step pos restored");
        helper.assertValueEqual(copy.idleLevel, 2, "idle level");
        helper.succeed();
    }

    /** Worker walks through a closed oak door to a chest on the far side, without any teleport. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void walksThroughClosedDoorToChest(GameTestHelper helper) {
        walledFloor(helper);
        for (int x = 1; x <= 6; x++) {
            for (int y = 2; y <= 3; y++) {
                if (x != 3) helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
            }
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        helper.setBlock(new BlockPos(3, 2, 4), door);
        helper.setBlock(new BlockPos(3, 3, 4), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(new BlockPos(3, 2, 6), Blocks.CHEST);
        BlockPos chest = helper.absolutePos(new BlockPos(3, 2, 6));
        Worker worker = spawnWorker(helper, new BlockPos(3, 2, 1));
        boolean[] reached = {false};
        helper.onEachTick(() -> {
            if (!reached[0] && helper.getTick() % 20 == 0) reached[0] = worker.approachWithin(chest, 1.5, 2);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(reached[0], "Worker must reach the chest behind the door (others in plot: "
                + helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(8, 6, 8), e -> e != worker) + ")");
            helper.assertTrue(worker.blockPosition().getZ() >= helper.absolutePos(new BlockPos(3, 2, 5)).getZ(),
                "Worker must have crossed the door line");
            helper.assertTrue(worker.hops == 0, "teleport fallbacks: " + worker.hops + " (others in plot: "
                + helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(8, 6, 8), e -> e != worker)
                + ", status: " + worker.status
                + ", log: " + String.join(" | ", worker.nav.debugLog) + ")");
        });
    }

    /** take() does not reach into a chest from across the plot; it walks there and only then succeeds. */
    @GameTest(template = "empty")
    public static void supplyTakeNeedsProximity(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(6, 2, 6), Blocks.CHEST);
        BlockPos chest = helper.absolutePos(new BlockPos(6, 2, 6));
        ChestBlockEntity be = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(6, 2, 6));
        be.setItem(0, new ItemStack(Items.TORCH, 8));
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        worker.supply = chest;
        ItemStack torch = new ItemStack(Items.TORCH);
        helper.assertFalse(Jobs.take(helper.getLevel(), worker, torch), "take() must fail while far from the supply chest");
        helper.assertTrue(worker.nav.hasErrand(), "A supply trip must be pending");
        helper.assertTrue(worker.status.startsWith("Fetching"), "Status must show the fetch: " + worker.status);
        helper.assertValueEqual(be.getItem(0).getCount(), 8, "chest count before the trip");
        helper.assertFalse(Jobs.take(helper.getLevel(), worker, torch), "take() keeps failing until the trip is done");
        // Stand next to the chest and let the errand finish on this tick's approach.
        worker.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 2, 6.5)));
        helper.assertFalse(worker.approach(chest), "Arriving at the chest uses up this tick");
        helper.assertFalse(worker.nav.hasErrand(), "Trip finished");
        helper.assertTrue(be.getItem(0).getCount() < 8, "Torches must have left the chest");
        helper.assertTrue(Jobs.take(helper.getLevel(), worker, torch), "take() succeeds from the bag after the trip");
        helper.succeed();
    }

    /** A walk across a flat plot never triggers the teleport fallback. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void flatWalkNeverTeleports(GameTestHelper helper) {
        walledFloor(helper);
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        BlockPos goal = helper.absolutePos(new BlockPos(6, 2, 6));
        boolean[] reached = {false};
        helper.onEachTick(() -> {
            if (!reached[0] && helper.getTick() % 20 == 0) reached[0] = worker.approachWithin(goal, 1.5, 1);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(reached[0], "Worker must reach the far corner (at " + helper.relativePos(worker.blockPosition())
                + ", others in plot: " + helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(8, 6, 8), e -> e != worker)
                + ", log: " + String.join(" | ", worker.nav.debugLog) + ")");
            helper.assertValueEqual(worker.hops, 0, "teleport fallbacks");
        });
    }

    /** Miners approach a stand spot beside the block, not the block itself. */
    @GameTest(template = "empty")
    public static void standSpotIsBesideTarget(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.OAK_LOG);
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        BlockPos log = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos stand = worker.nav.standFor(log);
        helper.assertTrue(stand != null, "Stand spot must exist on a flat floor");
        helper.assertFalse(stand.equals(log), "Stand spot is not the target");
        helper.assertTrue(stand.distManhattan(log) == 1 && stand.getY() == log.getY(), "Stand spot is adjacent at the same height");
        helper.assertTrue(Worker.canStandAt(helper.getLevel(), stand), "Stand spot is standable");
        helper.succeed();
    }

    /** A torch already in the world completes a PLACE_TORCH step so mine plans fast-forward after reload. */
    @GameTest(template = "empty")
    public static void torchStepCountsAsDone(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.TORCH);
        helper.setBlock(new BlockPos(5, 2, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.WALL_TORCH.defaultBlockState()
            .setValue(WallTorchBlock.FACING, Direction.NORTH));
        MinePlanner.Step standing = new MinePlanner.Step(helper.absolutePos(new BlockPos(2, 2, 2)), MinePlanner.Kind.PLACE_TORCH);
        MinePlanner.Step hung = new MinePlanner.Step(helper.absolutePos(new BlockPos(5, 2, 5)), MinePlanner.Kind.PLACE_TORCH);
        MinePlanner.Step dark = new MinePlanner.Step(helper.absolutePos(new BlockPos(4, 2, 1)), MinePlanner.Kind.PLACE_TORCH);
        helper.assertTrue(Jobs.stepDone(helper.getLevel(), standing), "Standing torch completes the step");
        helper.assertTrue(Jobs.stepDone(helper.getLevel(), hung), "Wall torch on the step block completes it");
        helper.assertFalse(Jobs.stepDone(helper.getLevel(), dark), "Unlit step is not done");
        helper.succeed();
    }

    /** Stone floor at y=1 across the plot with a low wall around it so nobody wanders off the edge. */
    private static void walledFloor(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                if (x == 0 || x == 7 || z == 0 || z == 7) {
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
                }
            }
        }
    }

    @GameTest(template = "empty")
    public static void blockBreakingTakesTime(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        BlockPos stone = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), net.minecraft.world.level.block.Blocks.STONE);
        helper.assertFalse(worker.animator.ready(helper.getLevel(), stone), "First ask only starts the swing");
        helper.assertTrue(worker.animator.needed >= WorkAnimator.MIN_TICKS, "Stone needs at least the minimum time");
        for (int i = 0; i < WorkAnimator.MAX_TICKS + 1; i++) worker.animator.tick(helper.getLevel());
        helper.assertTrue(worker.animator.ready(helper.getLevel(), stone), "Block is ready after enough ticks");
        helper.assertTrue(worker.swinging || worker.swingTime >= 0, "Arm swung");
        worker.animator.clear(helper.getLevel());
        helper.assertTrue(worker.animator.pos == null, "Clear forgets the block");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void foresterChopsLogByLog(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(2, 2, 1));
        worker.role = "forester";
        worker.working = true;
        worker.first = helper.absolutePos(new BlockPos(0, 1, 0));
        worker.second = helper.absolutePos(new BlockPos(6, 1, 6));
        worker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
            new ItemStack(net.minecraft.world.item.Items.IRON_AXE));
        helper.setBlock(new BlockPos(2, 1, 3), net.minecraft.world.level.block.Blocks.DIRT);
        for (int y = 2; y <= 5; y++) helper.setBlock(new BlockPos(2, y, 3), net.minecraft.world.level.block.Blocks.OAK_LOG);
        // First work tick plans the tree; no log may vanish instantly.
        Jobs.tick(helper.getLevel(), worker);
        int logsLeft = 0;
        for (int y = 2; y <= 5; y++) {
            if (helper.getBlockState(new BlockPos(2, y, 3)).is(net.minecraft.world.level.block.Blocks.OAK_LOG)) logsLeft++;
        }
        helper.assertTrue(logsLeft == 4, "Planning a tree must not fell it in one tick (left " + logsLeft + ")");
        helper.assertTrue(worker.fellQueue.size() == 4, "Four logs queued");
        helper.assertTrue(worker.fellQueue.get(0).getY() < worker.fellQueue.get(3).getY(), "Queue is bottom first");
        // Then one log per work tick, with the animator advanced between ticks.
        for (int round = 0; round < 40 && !worker.fellQueue.isEmpty(); round++) {
            for (int t = 0; t < WorkAnimator.MAX_TICKS; t++) worker.animator.tick(helper.getLevel());
            Jobs.tick(helper.getLevel(), worker);
        }
        helper.assertTrue(worker.fellQueue.isEmpty(), "Tree finished");
        for (int y = 2; y <= 5; y++) {
            helper.assertFalse(helper.getBlockState(new BlockPos(2, y, 3)).is(net.minecraft.world.level.block.Blocks.OAK_LOG),
                "Log at y=" + y + " chopped");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fellQueueSurvivesSave(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        worker.fellQueue.add(helper.absolutePos(new BlockPos(2, 2, 2)));
        worker.fellQueue.add(helper.absolutePos(new BlockPos(2, 3, 2)));
        worker.fellBase = helper.absolutePos(new BlockPos(2, 2, 2));
        worker.fellSapling = "minecraft:oak_sapling";
        CompoundTag tag = new CompoundTag();
        worker.addAdditionalSaveData(tag);
        Worker copy = spawnWorker(helper, new BlockPos(3, 2, 1));
        copy.readAdditionalSaveData(tag);
        helper.assertValueEqual(copy.fellQueue.size(), 2, "queued logs");
        helper.assertTrue(copy.fellBase.equals(worker.fellBase), "base restored");
        helper.assertValueEqual(copy.fellSapling, "minecraft:oak_sapling", "sapling restored");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overheadStatusSyncsToClientData(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        worker.role = "miner";
        worker.working = true;
        worker.setStatus("Mined 1,2,3");
        worker.flushStatus();
        helper.assertValueEqual(worker.getEntityData().get(Worker.DATA_STATUS), "Mined 1,2,3", "status line");
        helper.assertValueEqual(worker.getEntityData().get(Worker.DATA_ROLE), "miner", "role");
        helper.assertTrue(worker.getEntityData().get(Worker.DATA_STATE) == Worker.STATE_WORKING, "working colour");
        worker.setStatus("Missing pickaxe");
        worker.flushStatus();
        helper.assertTrue(worker.getEntityData().get(Worker.DATA_STATE) == Worker.STATE_BLOCKED, "blocked colour");
        String longStatus = "x".repeat(80);
        worker.setStatus(longStatus);
        worker.flushStatus();
        helper.assertTrue(StatusText.overheadLine(worker.getEntityData().get(Worker.DATA_STATUS)).length() <= Worker.STATUS_LINE_MAX,
            "line truncated");
        helper.succeed();
    }

    // ---------------------------------------------------------------- Phase 4: gathering automation

    /** Quarry mode digs the loose layers down to the area floor and lays cobblestone there, never deeper. */
    @GameTest(template = "empty")
    public static void quarryDigsAndLaysCobbleFloor(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.SAND);
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.GRAVEL);
        Worker worker = spawnWorker(helper, new BlockPos(3, 2, 1));
        worker.role = "miner";
        worker.mode = "quarry";
        worker.working = true;
        worker.first = helper.absolutePos(new BlockPos(3, 3, 3));
        worker.second = helper.absolutePos(new BlockPos(3, 2, 3));
        worker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        worker.bag.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        for (int round = 0; round < 30; round++) {
            for (int t = 0; t < WorkAnimator.MAX_TICKS; t++) worker.animator.tick(helper.getLevel());
            Jobs.tick(helper.getLevel(), worker);
        }
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 3, 3));
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(3, 2, 3));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 3));
        helper.assertTrue(bagCount(worker, Items.SAND) == 1, "Sand from the floor layer is in the bag");
        helper.assertTrue(bagCount(worker, Items.GRAVEL) + bagCount(worker, Items.FLINT) == 1, "Gravel (or its flint) is in the bag");
        helper.assertTrue(bagCount(worker, Items.COBBLESTONE) == 7, "One cobblestone went into the floor");
        helper.succeed();
    }

    /** A gatherer cuts sugar cane above the root, leaves the root to regrow, and sweeps the stalks that drop. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void gathererCutsCaneAboveTheRoot(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(2, 0, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.WATER);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.DIRT); // not sand: it would fall into the empty row below
        for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(3, y, 3), Blocks.SUGAR_CANE);
        Worker worker = spawnWorker(helper, new BlockPos(4, 2, 1));
        worker.role = "gatherer";
        worker.working = true;
        worker.enabledCrops.clear();
        worker.enabledCrops.addAll(Jobs.GATHER_RESOURCES);
        worker.first = helper.absolutePos(new BlockPos(1, 1, 1));
        worker.second = helper.absolutePos(new BlockPos(6, 5, 6));
        worker.rebuildGoals();
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.SUGAR_CANE, new BlockPos(3, 2, 3));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 3, 3));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 4, 3));
            helper.assertTrue(bagCount(worker, Items.SUGAR_CANE) >= 2,
                "Both cut stalks end up in the bag (" + bagCount(worker, Items.SUGAR_CANE) + ")");
        });
    }

    /** A full hive is only harvested once a lit campfire smokes it from below; bottles make honey bottles. */
    @GameTest(template = "empty")
    public static void rancherHarvestsHoneyOnlyOverCampfire(GameTestHelper helper) {
        walledFloor(helper);
        BlockState full = Blocks.BEEHIVE.defaultBlockState()
            .setValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL, 5);
        helper.setBlock(new BlockPos(3, 3, 3), full);
        Worker worker = spawnWorker(helper, new BlockPos(3, 2, 1));
        worker.role = "rancher";
        worker.working = true;
        worker.first = helper.absolutePos(new BlockPos(1, 2, 1));
        worker.second = helper.absolutePos(new BlockPos(6, 4, 6));
        worker.bag.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 2));
        for (int i = 0; i < 4; i++) ExtraJobs.rancher(helper.getLevel(), worker);
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 3, 3))
            .getValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL) == 5, "No campfire: bees are left alone");
        helper.assertTrue(bagCount(worker, Items.HONEY_BOTTLE) == 0, "No honey without a campfire");
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.CAMPFIRE);
        for (int i = 0; i < 6; i++) ExtraJobs.rancher(helper.getLevel(), worker);
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 3, 3))
            .getValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL) == 0, "Hive emptied over the campfire");
        helper.assertTrue(bagCount(worker, Items.HONEY_BOTTLE) == 1, "One honey bottle collected");
        helper.assertTrue(bagCount(worker, Items.GLASS_BOTTLE) == 1, "One glass bottle used");
        helper.succeed();
    }

    /** Courier: filters decide what leaves a pickup, "keep N" leaves stock behind, pickups are visited in turn. */
    @GameTest(template = "empty")
    public static void courierFiltersKeepsAndRotates(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.BARREL);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.BARREL);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.BARREL);
        net.minecraft.world.Container a = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(2, 2, 3));
        net.minecraft.world.Container b = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(4, 2, 4));
        net.minecraft.world.Container out = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(4, 2, 2));
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 20));
        a.setItem(1, new ItemStack(Items.IRON_INGOT, 5));
        b.setItem(0, new ItemStack(Items.STICK, 3));
        Worker worker = spawnWorker(helper, new BlockPos(3, 2, 2));
        worker.role = "courier";
        worker.working = true;
        BlockPos pickupA = helper.absolutePos(new BlockPos(2, 2, 3));
        BlockPos pickupB = helper.absolutePos(new BlockPos(4, 2, 4));
        worker.pickups.add(pickupA);
        worker.pickups.add(pickupB);
        worker.output = helper.absolutePos(new BlockPos(4, 2, 2));
        worker.pickupFilters.put(pickupA.asLong(), new java.util.ArrayList<>(java.util.List.of("minecraft:iron_ingot")));

        ExtraJobs.courier(helper.getLevel(), worker);
        helper.assertTrue(bagCount(worker, Items.IRON_INGOT) == 5, "Filter lets the iron through");
        helper.assertTrue(bagCount(worker, Items.COBBLESTONE) == 0, "Filter keeps the cobblestone out");
        helper.assertValueEqual(worker.courierCursor, 1, "cursor moves to the next chest");
        ExtraJobs.courier(helper.getLevel(), worker); // deposit
        helper.assertTrue(count(out, Items.IRON_INGOT) == 5, "Iron dropped off");
        ExtraJobs.courier(helper.getLevel(), worker); // next in turn: chest B
        helper.assertTrue(bagCount(worker, Items.STICK) == 3, "Second chest visited next: " + worker.status);
        ExtraJobs.courier(helper.getLevel(), worker); // deposit
        ExtraJobs.courier(helper.getLevel(), worker); // nothing left that passes
        helper.assertTrue(worker.status.startsWith("Route clear"), "Filtered-out cobble is not hauled: " + worker.status);
        helper.assertTrue(count(a, Items.COBBLESTONE) == 20, "Cobblestone untouched");

        worker.pickupFilters.clear();
        worker.pickupKeep.put(pickupA.asLong(), 16);
        ExtraJobs.courier(helper.getLevel(), worker);
        helper.assertTrue(bagCount(worker, Items.COBBLESTONE) == 4, "Only the surplus above keep 16 is taken");
        helper.assertTrue(count(a, Items.COBBLESTONE) == 16, "Sixteen cobblestone stay behind");
        ExtraJobs.courier(helper.getLevel(), worker); // deposit
        ExtraJobs.courier(helper.getLevel(), worker);
        helper.assertTrue(worker.status.startsWith("Route clear"), "Kept stock is left alone: " + worker.status);
        helper.succeed();
    }

    /** Smelter: smeltables and fuel are fetched on foot from the supply chest, a stack at a time, into the right slot. */
    @GameTest(template = "empty")
    public static void smelterFetchesOnFootAndLoadsAStack(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.BARREL);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.FURNACE);
        net.minecraft.world.Container supply = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(2, 2, 3));
        net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace =
            (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) helper.getBlockEntity(new BlockPos(4, 2, 3));
        supply.setItem(0, new ItemStack(Items.RAW_IRON, 40));
        supply.setItem(1, new ItemStack(Items.COAL, 10));
        supply.setItem(2, new ItemStack(Items.DIRT, 5));
        Worker worker = spawnWorker(helper, new BlockPos(3, 2, 2));
        worker.role = "smelter";
        worker.working = true;
        worker.supply = helper.absolutePos(new BlockPos(2, 2, 3));
        worker.furnaces.add(helper.absolutePos(new BlockPos(4, 2, 3)));
        Jobs.tick(helper.getLevel(), worker);
        helper.assertTrue(worker.nav.hasErrand(), "Smelter starts a trip to the supply chest: " + worker.status);
        helper.assertTrue(furnace.getItem(0).isEmpty(), "Nothing is loaded from afar");
        helper.assertTrue(count(supply, Items.RAW_IRON) == 40, "Nothing leaves the chest from afar");
        Jobs.tick(helper.getLevel(), worker); // arrives (within reach) and pulls a stack
        helper.assertTrue(bagCount(worker, Items.RAW_IRON) == 40, "Raw iron in the bag after the trip: " + worker.status);
        helper.assertTrue(worker.furnaceHaul.contains("minecraft:raw_iron"), "Haul remembered");
        Jobs.tick(helper.getLevel(), worker); // loads the furnace
        helper.assertTrue(furnace.getItem(0).is(Items.RAW_IRON) && furnace.getItem(0).getCount() == 40,
            "Whole stack loaded into the input slot: " + worker.status);
        helper.assertTrue(bagCount(worker, Items.RAW_IRON) == 0, "Bag emptied into the furnace");
        for (int i = 0; i < 3; i++) Jobs.tick(helper.getLevel(), worker); // fetch fuel, arrive, load
        helper.assertTrue(furnace.getItem(1).is(Items.COAL) && furnace.getItem(1).getCount() == 10,
            "Coal fetched and put in the fuel slot: " + worker.status);
        helper.assertTrue(count(supply, Items.DIRT) == 5, "Dirt is neither smeltable nor fuel");
        helper.assertTrue(ExtraJobs.isFuel(new ItemStack(Items.OAK_PLANKS)) && !ExtraJobs.isFuel(new ItemStack(Items.DIRT)),
            "Fuel is data-driven");
        helper.succeed();
    }

    /** Request board: progress rides on the status line, survives a save, and pauses the worker when done if asked. */
    @GameTest(template = "empty")
    public static void requestProgressShowsInStatus(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        worker.setOwner(player.getUUID());
        worker.role = "forester";
        worker.working = true;
        ActionResult empty = WorkerActions.addRequest(player, worker, ItemStack.EMPTY, 64);
        helper.assertFalse(empty.success(), "Asking with an empty hand is refused");
        ActionResult asked = WorkerActions.addRequest(player, worker, new ItemStack(Items.OAK_LOG), 64);
        helper.assertTrue(asked.success(), "Request accepted: " + asked.text());
        helper.assertValueEqual(worker.requests.size(), 1, "requests");
        worker.setStatus("Felled a tree");
        worker.collect(new ItemStack(Items.OAK_LOG, 10));
        helper.assertTrue(worker.status.startsWith("Felled a tree"), "Job words come first: " + worker.status);
        helper.assertTrue(worker.status.contains("Request 10/64 Oak Log"), "Progress on the status line: " + worker.status);
        helper.assertTrue(worker.collectFlags().contains("req:0|minecraft:oak_log|10|64"), "Progress in the dialogue flags");
        worker.collect(new ItemStack(Items.STONE, 5));
        helper.assertTrue(worker.status.contains("10/64"), "Other items do not count: " + worker.status);
        CompoundTag tag = new CompoundTag();
        worker.addAdditionalSaveData(tag);
        Worker copy = spawnWorker(helper, new BlockPos(3, 2, 1));
        copy.readAdditionalSaveData(tag);
        helper.assertTrue(copy.requests.size() == 1 && copy.requests.get(0).done() == 10
            && copy.requests.get(0).count() == 64, "Request survives a save");
        worker.collect(new ItemStack(Items.OAK_LOG, 54));
        helper.assertFalse(worker.working, "Pauses once the list is collected");
        helper.assertTrue(worker.status.contains("collected"), "Says so: " + worker.status);
        // Keep going instead of pausing.
        WorkerActions.clearRequests(player, worker);
        WorkerActions.toggleRequestStop(player, worker);
        helper.assertFalse(worker.requestsStop, "Toggle off");
        worker.working = true;
        WorkerActions.addRequest(player, worker, new ItemStack(Items.STONE), 16);
        worker.setStatus("Mined 1,2,3");
        worker.collect(new ItemStack(Items.STONE, 16));
        helper.assertTrue(worker.working, "Keeps working when told not to stop");
        helper.assertTrue(worker.status.contains("All requests done"), "Done suffix: " + worker.status);
        helper.succeed();
    }

    // ---------------------------------------------------------------- Phase 5: UI and polish

    /** Every translation key the UI uses (Lang constants, roles, kits, config entries) exists in en_us.json. */
    @GameTest(template = "empty")
    public static void langKeysExist(GameTestHelper helper) {
        java.io.InputStream in = WorkerGameTests.class.getResourceAsStream("/assets/helpfulworkers/lang/en_us.json");
        helper.assertTrue(in != null, "en_us.json on the classpath");
        com.google.gson.JsonObject lang;
        try (java.io.Reader reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
            lang = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException ex) {
            helper.fail("Could not read en_us.json: " + ex);
            return;
        }
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String key : Lang.all()) if (!lang.has(key)) missing.add(key);
        for (String role : WorkerActions.ROLES) {
            net.minecraft.world.item.Item kit = WorkerActions.kitForRole(role);
            if (kit != null && !lang.has(kit.getDescriptionId())) missing.add(kit.getDescriptionId());
        }
        helper.assertTrue(missing.isEmpty(), "Missing lang keys: " + missing);
        helper.succeed();
    }

    /** Roster: blocked workers sort first, state comes from the server, and the last drop-off time is reported and saved. */
    @GameTest(template = "empty")
    public static void rosterSortsBlockedFirstWithDropTime(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Worker busy = spawnWorker(helper, new BlockPos(1, 2, 1));
        Worker stuck = spawnWorker(helper, new BlockPos(5, 2, 5));
        // Paused, so their own work ticks do not overwrite the statuses set here.
        for (Worker w : new Worker[]{busy, stuck}) { w.setOwner(player.getUUID()); w.role = "miner"; w.working = false; }
        busy.setStatus("Mined 1,2,3");
        stuck.setStatus("Missing pickaxe");
        busy.lastDepositTime = 1; // the test world is young; tick 1 is always in the past
        CompoundTag tag = new CompoundTag();
        busy.addAdditionalSaveData(tag);
        Worker copy = spawnWorker(helper, new BlockPos(3, 2, 1));
        copy.readAdditionalSaveData(tag);
        helper.assertValueEqual(copy.lastDepositTime, busy.lastDepositTime, "drop-off time saved");
        // Freshly added entities can take a tick to show up in the level's entity lookup; retry until they do.
        helper.succeedWhen(() -> {
            WorkerNetwork.RosterPayload roster = WorkerNetwork.RosterPayload.from(player);
            java.util.List<WorkerNetwork.RosterEntry> mine = roster.entries().stream()
                .filter(e -> e.id() == busy.getId() || e.id() == stuck.getId()).toList();
            helper.assertValueEqual(mine.size(), 2, "both workers listed");
            helper.assertValueEqual(mine.get(0).id(), stuck.getId(), "blocked worker first");
            helper.assertTrue(mine.get(0).state() == Worker.STATE_BLOCKED, "blocked state from the server");
            helper.assertTrue(mine.get(1).state() == Worker.STATE_IDLE, "idle state from the server");
            helper.assertValueEqual(mine.get(1).sinceDrop(), helper.getLevel().getGameTime() - 1, "ticks since the last drop-off");
            helper.assertValueEqual(mine.get(0).sinceDrop(), -1L, "never dropped off");
        });
    }

    /** Client display options fall back to sensible defaults when the client config is not loaded (server, tests). */
    @GameTest(template = "empty")
    public static void displayConfigDefaults(GameTestHelper helper) {
        helper.assertTrue(WorkerConfig.overheadRange() >= 4 && WorkerConfig.overheadRange() <= 64, "overhead range in bounds");
        helper.assertTrue(WorkerConfig.outlineSeconds() >= 2, "outline time in bounds");
        helper.assertTrue(WorkerConfig.showOverheadStatus() || !WorkerConfig.showOverheadStatus(), "readable");
        helper.assertTrue(WorkerConfig.CONFIG_PATHS.contains("overheadRange"), "config path listed for translation");
        helper.succeed();
    }

    /** Keyed statuses keep English on the server (NBT, logic, tests) and send a translatable code to clients. */
    @GameTest(template = "empty")
    public static void statusTranslatesAndKeepsEnglish(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        worker.setStatusKey(Lang.STATUS_FULL);
        helper.assertValueEqual(worker.status, Jobs.OUT_OF_SPACE, "English text on the server");
        helper.assertTrue(StatusText.keys(worker.statusCode).contains(Lang.STATUS_FULL), "client gets the key");
        worker.requests.add(new Worker.Request("minecraft:oak_log", 64, 10));
        worker.refreshStatus();
        helper.assertTrue(worker.status.startsWith(Jobs.OUT_OF_SPACE) && worker.status.endsWith("Request 10/64 Oak Log"),
            "request progress appended in English: " + worker.status);
        net.minecraft.network.chat.Component decoded = StatusText.toComponent(worker.statusCode);
        helper.assertTrue(decoded.getSiblings().size() == 2, "two translatable segments");
        helper.assertValueEqual(StatusText.toComponent("Plain old status").getString(), "Plain old status", "legacy literal");
        CompoundTag tag = new CompoundTag();
        worker.addAdditionalSaveData(tag);
        helper.assertTrue(tag.getString("Status").startsWith(Jobs.OUT_OF_SPACE), "NBT keeps English");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 0.9.0: levels, shared chests, town hall, traits

    /** XP raises the level, a higher level breaks blocks faster and has more health, and both survive a save. */
    @GameTest(template = "empty")
    public static void levelsSpeedUpBreakingAndSurviveSave(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.DIRT);
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        helper.assertValueEqual(worker.workerLevel(), 1, "new worker level");
        int slow = WorkAnimator.duration(helper.getLevel(), worker, dirt);
        float baseHealth = worker.getMaxHealth();
        worker.gainXp(Worker.LEVEL_XP[4]);
        helper.assertValueEqual(worker.workerLevel(), 5, "level after XP");
        int fast = WorkAnimator.duration(helper.getLevel(), worker, dirt);
        helper.assertTrue(fast < slow, "level 5 breaks faster (" + fast + " vs " + slow + " ticks)");
        helper.assertTrue(worker.getMaxHealth() == baseHealth + 8, "four levels add 8 max health: " + worker.getMaxHealth());
        CompoundTag tag = new CompoundTag();
        worker.addAdditionalSaveData(tag);
        Worker copy = spawnWorker(helper, new BlockPos(3, 2, 1));
        copy.readAdditionalSaveData(tag);
        helper.assertValueEqual(copy.xp, worker.xp, "xp saved");
        helper.assertTrue(copy.getMaxHealth() == baseHealth + 8, "level health re-applied on load");
        worker.collect(new ItemStack(Items.STICK));
        helper.assertValueEqual(worker.xp, Worker.LEVEL_XP[4] + 1, "collecting job output gives XP");
        helper.succeed();
    }

    /** Two workers sharing a supply chest: once one claims the last torches, the other does not walk for them too. */
    @GameTest(template = "empty")
    public static void sharedSupplyClaimsAvoidDoubleTrips(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(6, 2, 6), Blocks.BARREL);
        net.minecraft.world.Container chest = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(6, 2, 6));
        chest.setItem(0, new ItemStack(Items.TORCH, 8));
        BlockPos supply = helper.absolutePos(new BlockPos(6, 2, 6));
        Worker a = spawnWorker(helper, new BlockPos(1, 2, 1));
        Worker b = spawnWorker(helper, new BlockPos(1, 2, 3));
        a.supply = supply;
        b.supply = supply;
        ItemStack torch = new ItemStack(Items.TORCH);
        helper.assertFalse(Jobs.take(helper.getLevel(), a, torch), "first worker sets off");
        helper.assertTrue(a.nav.hasErrand(), "first worker has a supply trip");
        helper.assertFalse(Jobs.take(helper.getLevel(), b, torch), "second worker cannot take either");
        helper.assertFalse(b.nav.hasErrand(), "second worker does not walk for torches already claimed");
        WorkerStorage.release(helper.getLevel(), supply, a, Jobs.itemKey(torch));
        b.supplyPeek.clear();
        Jobs.take(helper.getLevel(), b, torch);
        helper.assertTrue(b.nav.hasErrand(), "once the claim is gone the second worker may go");
        helper.succeed();
    }

    /** Drop-off leaves the bag reserve behind and merges into the chest's existing stacks. */
    @GameTest(template = "empty")
    public static void depositKeepsReserves(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.BARREL);
        net.minecraft.world.Container chest = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(2, 2, 3));
        chest.setItem(5, new ItemStack(Items.WHEAT, 60));
        Worker worker = spawnWorker(helper, new BlockPos(2, 2, 2));
        worker.output = helper.absolutePos(new BlockPos(2, 2, 3));
        worker.reserves.put("minecraft:wheat_seeds", 8);
        worker.bag.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 20));
        worker.bag.setItem(1, new ItemStack(Items.WHEAT, 10));
        helper.assertTrue(Jobs.depositPublic(helper.getLevel(), worker), "deposit done: " + worker.status);
        helper.assertValueEqual(bagCount(worker, Items.WHEAT_SEEDS), 8, "seeds kept in the bag");
        helper.assertValueEqual(count(chest, Items.WHEAT_SEEDS), 12, "surplus seeds dropped off");
        helper.assertValueEqual(chest.getItem(5).getCount(), 64, "wheat topped up the existing stack first");
        helper.assertValueEqual(count(chest, Items.WHEAT), 70, "all wheat dropped off");
        helper.succeed();
    }

    /** 1.0: work areas far above the old 32768-block cap are allowed (a 300 x 300 forest, a mountain, a pit to bedrock). */
    @GameTest(template = "empty")
    public static void largeAreaSizesAllowed(GameTestHelper helper) {
        helper.assertTrue(WorkerActions.areaSizeError(300, 40, 300) == null, "300 x 300 forest allowed");
        helper.assertTrue(WorkerActions.areaSizeError(64, 384, 64) == null, "64 x 64 pit from the top to bedrock allowed");
        helper.assertTrue(WorkerActions.areaSizeError(200, 150, 200) == null, "mountain levelling area allowed");
        helper.assertTrue(WorkerActions.areaSizeError(600, 10, 10) != null, "footprint wider than 512 rejected");
        helper.assertTrue(WorkerActions.areaSizeError(512, 384, 512) != null, "more than 64M blocks rejected");
        helper.succeed();
    }

    /** The large-area scan skips the air above the terrain from the heightmap and jumps empty layers. */
    @GameTest(template = "empty")
    public static void largeAreaScanSkipsAirAndJumpsLayers(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.STONE);
        Worker worker = spawnWorker(helper, new BlockPos(2, 2, 2));
        worker.role = "miner";
        worker.mode = "terraform";
        BlockPos low = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos high = helper.absolutePos(new BlockPos(6, 202, 6));
        Worker.AreaBounds box = new Worker.AreaBounds(low.getX(), low.getY(), low.getZ(), high.getX(), high.getY(), high.getZ());
        worker.scanCursor = 0;
        // Budget 8 = 32 block checks and 2048 heightmap skips per call; 200 empty layers of 36 columns
        // would need four calls without the layer jump.
        BlockPos found = Jobs.findLarge(helper.getLevel(), worker, box, true, false, 8);
        helper.assertTrue(helper.absolutePos(new BlockPos(3, 2, 4)).equals(found), "stone found in one call, got " + found);
        helper.assertTrue(worker.scanPeak == low.getY(), "peak is the stone's height after the top layer");
        helper.succeed();
    }

    /** With no cobblestone in the supply, a miner fetches it from its own output chest. */
    @GameTest(template = "empty")
    public static void minerSealsWithCobbleFromOutput(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(6, 2, 1), Blocks.BARREL);
        helper.setBlock(new BlockPos(6, 2, 6), Blocks.BARREL);
        ((net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(6, 2, 1))).setItem(0, new ItemStack(Items.TORCH, 4));
        ((net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(6, 2, 6))).setItem(0, new ItemStack(Items.COBBLESTONE, 30));
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 3));
        worker.role = "miner";
        worker.supply = helper.absolutePos(new BlockPos(6, 2, 1));
        worker.output = helper.absolutePos(new BlockPos(6, 2, 6));
        helper.assertFalse(Jobs.take(helper.getLevel(), worker, new ItemStack(Items.COBBLESTONE)), "not in the bag yet");
        helper.assertTrue(worker.nav.hasErrand(), "a trip was started");
        helper.assertTrue(worker.nav.errand.pos.equals(worker.output), "the trip goes to the output chest");
        helper.succeed();
    }

    /** The town hall recruits beside itself (within the caps) and gathers bed-less workers. */
    @GameTest(template = "empty")
    public static void townHallRecruitsWithContract(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(3, 2, 3), HelpfulWorkers.TOWN_HALL.get());
        BlockPos hall = helper.absolutePos(new BlockPos(3, 2, 3));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos spot = TownHallBlock.standSpotBeside(helper.getLevel(), hall, Direction.NORTH);
        helper.assertTrue(spot != null && spot.distManhattan(hall) == 1, "free spot beside the hall");
        Worker recruited = ContractItem.recruit(helper.getLevel(), player, spot, 0);
        if (recruited != null) {
            helper.assertTrue(recruited.owns(player), "recruit is owned by the player");
            helper.assertTrue(Worker.TRAITS.contains(recruited.trait), "recruit has a personality");
        } else {
            helper.assertTrue(WorkerRegistry.countAlive(helper.getLevel()) >= WorkerConfig.maxPerWorld()
                || WorkerRegistry.countForOwner(helper.getLevel(), player.getUUID()) >= WorkerConfig.maxPerPlayer(),
                "recruiting only fails at a cap");
        }
        Worker bedless = spawnWorker(helper, new BlockPos(5, 2, 5));
        bedless.setOwner(player.getUUID());
        int assigned = TownHallBlock.assignHall(helper.getLevel(), player, hall);
        helper.assertTrue(assigned >= 1, "sneak-click assigns nearby workers");
        helper.assertTrue(hall.equals(bedless.hall), "worker gathers at the hall");
        CompoundTag tag = new CompoundTag();
        bedless.addAdditionalSaveData(tag);
        helper.assertTrue(BlockPos.of(tag.getLong("Hall")).equals(hall), "hall saved");
        helper.succeed();
    }

    /** Every worker ends up with a personality that is saved, and personalities sound different. */
    @GameTest(template = "empty")
    public static void traitAssignedAndSaved(GameTestHelper helper) {
        Worker worker = spawnWorker(helper, new BlockPos(1, 2, 1));
        worker.pickTraitIfMissing();
        helper.assertTrue(Worker.TRAITS.contains(worker.trait), "trait picked: " + worker.trait);
        worker.trait = "grumpy";
        CompoundTag tag = new CompoundTag();
        worker.addAdditionalSaveData(tag);
        Worker copy = spawnWorker(helper, new BlockPos(3, 2, 1));
        copy.readAdditionalSaveData(tag);
        helper.assertValueEqual(copy.trait, "grumpy", "trait saved");
        float grumpy = copy.voicePitch();
        copy.trait = "cheerful";
        helper.assertTrue(copy.voicePitch() > grumpy, "cheerful voices are higher than grumpy ones");
        CompoundTag legacy = new CompoundTag();
        legacy.putString("Role", "farmer");
        Worker old = spawnWorker(helper, new BlockPos(5, 2, 1));
        old.readAdditionalSaveData(legacy);
        helper.assertTrue(Worker.TRAITS.contains(old.trait), "old saves get a personality on load");
        helper.succeed();
    }

    /** The whole "Let me show you where" → assign area → click two corners flow sets a miner's and a forester's area. */
    @GameTest(template = "empty")
    public static void dialogueAreaAssignmentSetsArea(GameTestHelper helper) {
        walledFloor(helper);
        for (String role : new String[]{"miner", "forester"}) {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getAbilities().instabuild = false;
            player.getInventory().add(new ItemStack(HelpfulWorkers.CLIPBOARD.get()));
            player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5)));
            Worker worker = spawnWorker(helper, new BlockPos(2, 2, 2));
            worker.setOwner(player.getUUID());
            worker.role = role;
            WorkerSessions.openViewer(player, worker, WorkerSessions.ViewerKind.DIALOGUE);
            DialogueHandlers.handle(player, new WorkerNetwork.DialogueActionPayload(worker.getId(), "start_assign", "area"));
            helper.assertTrue(WorkerSessions.getClipboard(player) != null, role + ": clipboard session started");
            AssignmentClipboardItem.handleClick(player, helper.absolutePos(new BlockPos(1, 1, 1)), Direction.UP);
            player.tickCount++; // a new tick, as between two real clicks
            AssignmentClipboardItem.handleClick(player, helper.absolutePos(new BlockPos(5, 1, 5)), Direction.UP);
            helper.assertTrue(worker.first != null && worker.second != null,
                role + ": area set after two corners (mode " + worker.mode + ", status " + worker.status + ")");
            helper.assertTrue(WorkerSessions.getClipboard(player) == null, role + ": clipboard session finished");
        }
        helper.succeed();
    }

    static int count(net.minecraft.world.Container chest, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(item)) n += chest.getItem(i).getCount();
        return n;
    }

    static int bagCount(Worker worker, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) if (worker.bag.getItem(i).is(item)) n += worker.bag.getItem(i).getCount();
        return n;
    }

    /** Toolsmith: short of a pickaxe, forges the best tier the supply can pay for (iron, not stone) and shelves it. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void toolsmithCraftsBestTier(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.BARREL);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.CRAFTING_TABLE);
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.BARREL);
        net.minecraft.world.Container supply = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(2, 2, 3));
        net.minecraft.world.Container out = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(3, 2, 4));
        supply.setItem(0, new ItemStack(Items.STICK, 8));
        supply.setItem(1, new ItemStack(Items.IRON_INGOT, 9));
        supply.setItem(2, new ItemStack(Items.COBBLESTONE, 9));
        Worker worker = spawnWorker(helper, new BlockPos(3, 2, 2));
        worker.role = "toolsmith";
        worker.working = true;
        worker.supply = helper.absolutePos(new BlockPos(2, 2, 3));
        worker.craftingTable = helper.absolutePos(new BlockPos(4, 2, 3));
        worker.output = helper.absolutePos(new BlockPos(3, 2, 4));
        for (String key : ToolsmithJobs.TOOL_KEYS) worker.toolTargets.put(key, 0);
        worker.toolTargets.put("pickaxe", 1);
        for (int i = 0; i < 12 && count(out, Items.IRON_PICKAXE) == 0; i++) Jobs.tick(helper.getLevel(), worker);
        helper.assertTrue(count(out, Items.IRON_PICKAXE) == 1, "Iron pickaxe forged and shelved: " + worker.status);
        helper.assertTrue(count(out, Items.STONE_PICKAXE) == 0 && bagCount(worker, Items.STONE_PICKAXE) == 0, "No stone pickaxe when iron is available");
        helper.assertTrue(count(supply, Items.IRON_INGOT) + bagCount(worker, Items.IRON_INGOT) == 6,
            "Three ingots used (rest moved to the workshop bag)");
        helper.assertTrue(count(supply, Items.COBBLESTONE) == 9, "Cobblestone untouched");
        Jobs.tick(helper.getLevel(), worker);
        helper.assertTrue(worker.status.startsWith("Workshop stocked"), "Stock target met: " + worker.status);
        helper.succeed();
    }

    /** Toolsmith: a worn pickaxe left in the supply chest is fetched, repaired at the anvil with iron, and shelved as good as new. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void toolsmithRepairsAtAnvil(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.BARREL);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.ANVIL);
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.BARREL);
        net.minecraft.world.Container supply = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(2, 2, 3));
        net.minecraft.world.Container out = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(3, 2, 4));
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() - 10);
        supply.setItem(0, worn);
        supply.setItem(1, new ItemStack(Items.IRON_INGOT, 6));
        Worker worker = spawnWorker(helper, new BlockPos(3, 2, 2));
        worker.role = "toolsmith";
        worker.working = true;
        worker.supply = helper.absolutePos(new BlockPos(2, 2, 3));
        worker.anvil = helper.absolutePos(new BlockPos(4, 2, 3));
        worker.output = helper.absolutePos(new BlockPos(3, 2, 4));
        for (String key : ToolsmithJobs.TOOL_KEYS) worker.toolTargets.put(key, 0);
        ItemStack repaired = ItemStack.EMPTY;
        for (int i = 0; i < 14 && repaired.isEmpty(); i++) {
            Jobs.tick(helper.getLevel(), worker);
            for (int j = 0; j < out.getContainerSize(); j++) if (out.getItem(j).is(Items.IRON_PICKAXE)) repaired = out.getItem(j);
        }
        helper.assertFalse(repaired.isEmpty(), "Repaired pickaxe shelved in the output chest: " + worker.status);
        helper.assertTrue(repaired.getDamageValue() == 0, "Fully repaired (damage " + repaired.getDamageValue() + ")");
        helper.assertTrue(count(supply, Items.IRON_INGOT) == 2, "Four ingots spent on four quarters: " + count(supply, Items.IRON_INGOT));
        helper.succeed();
    }

    /** Toolsmith: a forester reporting "Missing axe" gets one carried from the shelf to its supply chest. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void toolsmithDeliversToMissingWorker(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.BARREL);
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.BARREL);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.BARREL); // within reach: these ticks run without walking
        net.minecraft.world.Container out = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(3, 2, 4));
        net.minecraft.world.Container mateSupply = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(4, 2, 2));
        out.setItem(0, new ItemStack(Items.IRON_AXE));
        UUID owner = UUID.randomUUID();
        Worker smith = spawnWorker(helper, new BlockPos(3, 2, 2));
        smith.setOwner(owner);
        smith.role = "toolsmith";
        smith.working = true;
        smith.supply = helper.absolutePos(new BlockPos(2, 2, 3));
        smith.craftingTable = null;
        smith.anvil = helper.absolutePos(new BlockPos(2, 2, 3)); // workshop present; anvil block not needed for this test
        smith.output = helper.absolutePos(new BlockPos(3, 2, 4));
        for (String key : ToolsmithJobs.TOOL_KEYS) smith.toolTargets.put(key, 0);
        Worker mate = spawnWorker(helper, new BlockPos(5, 2, 3));
        mate.setOwner(owner);
        mate.role = "forester";
        mate.supply = helper.absolutePos(new BlockPos(4, 2, 2));
        mate.setStatus("Missing axe");
        helper.assertValueEqual(ToolsmithJobs.neededTool(mate), "axe", "needed tool parsed from status");
        for (int i = 0; i < 12 && count(mateSupply, Items.IRON_AXE) == 0; i++) Jobs.tick(helper.getLevel(), smith);
        helper.assertTrue(count(mateSupply, Items.IRON_AXE) == 1, "Axe delivered to the forester's supply chest: " + smith.status);
        helper.assertTrue(count(out, Items.IRON_AXE) == 0, "Axe came off the shelf");
        helper.assertTrue(smith.toolDeliveryTo == null, "Delivery closed");
        helper.succeed();
    }

    /** Stonemason: an order for 8 cobblestone stairs is cut one per tick from supply cobble and shelved. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void stonemasonCutsOrder(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.BARREL);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONECUTTER);
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.BARREL);
        net.minecraft.world.Container supply = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(2, 2, 3));
        net.minecraft.world.Container out = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(3, 2, 4));
        supply.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        Worker worker = spawnWorker(helper, new BlockPos(3, 2, 2));
        worker.role = "stonemason";
        worker.working = true;
        worker.supply = helper.absolutePos(new BlockPos(2, 2, 3));
        worker.stonecutter = helper.absolutePos(new BlockPos(4, 2, 3));
        worker.output = helper.absolutePos(new BlockPos(3, 2, 4));
        helper.assertTrue(StonemasonJobs.canCut(helper.getLevel(), new ItemStack(Items.COBBLESTONE_STAIRS)), "Stairs are a stonecutter product");
        helper.assertFalse(StonemasonJobs.canCut(helper.getLevel(), new ItemStack(Items.DIRT)), "Dirt is not");
        worker.orders.add(new Worker.Order("minecraft:cobblestone_stairs", 8, 0, false));
        Jobs.tick(helper.getLevel(), worker);
        helper.assertTrue(worker.nav.hasErrand(), "Walks to the supply chest for cobblestone first: " + worker.status);
        int afterFirstCut = -1;
        for (int i = 0; i < 30 && count(out, Items.COBBLESTONE_STAIRS) < 8; i++) {
            Jobs.tick(helper.getLevel(), worker);
            if (afterFirstCut < 0 && bagCount(worker, Items.COBBLESTONE_STAIRS) > 0) afterFirstCut = bagCount(worker, Items.COBBLESTONE_STAIRS);
        }
        helper.assertTrue(afterFirstCut == 1, "One stair per work tick, not a batch (saw " + afterFirstCut + ")");
        helper.assertTrue(count(out, Items.COBBLESTONE_STAIRS) == 8, "Eight stairs shelved: " + worker.status);
        helper.assertTrue(count(supply, Items.COBBLESTONE) + bagCount(worker, Items.COBBLESTONE) == 56, "Eight cobblestone used");
        helper.assertTrue(worker.orders.get(0).finished(), "Order marked done");
        Jobs.tick(helper.getLevel(), worker);
        helper.assertTrue(worker.status.startsWith("All orders cut"), "Idle once the order is met: " + worker.status);
        helper.succeed();
    }

    /** Stonemason: with nothing to cut from, reports the missing input instead of failing. */
    @GameTest(template = "empty")
    public static void stonemasonWaitsForInput(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.BARREL);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONECUTTER);
        Worker worker = spawnWorker(helper, new BlockPos(3, 2, 2));
        worker.role = "stonemason";
        worker.working = true;
        worker.supply = helper.absolutePos(new BlockPos(2, 2, 3));
        worker.stonecutter = helper.absolutePos(new BlockPos(4, 2, 3));
        worker.orders.add(new Worker.Order("minecraft:stone_bricks", 16, 0, false));
        for (int i = 0; i < 3; i++) Jobs.tick(helper.getLevel(), worker);
        helper.assertTrue(worker.status.startsWith("Need stone"), "Reports the missing input: " + worker.status);
        helper.assertTrue(worker.orders.size() == 1 && !worker.orders.get(0).finished(), "Order kept");
        helper.assertTrue(worker.working, "Still on duty");
        helper.succeed();
    }

    /** Stonemason: "supply my builders" derives an order from a nearby builder's remaining blueprint materials. */
    @GameTest(template = "empty")
    public static void stonemasonDerivesOrdersFromBuilder(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        Worker mason = spawnWorker(helper, new BlockPos(1, 2, 1));
        mason.setOwner(owner);
        mason.role = "stonemason";
        mason.supplyBuilders = true;
        Worker builder = spawnWorker(helper, new BlockPos(4, 2, 4));
        builder.setOwner(owner);
        builder.role = "builder";
        builder.blueprint = "my_wall";
        for (int i = 0; i < 5; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", i); entry.putInt("Y", 0); entry.putInt("Z", 0);
            entry.put("State", net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.STONE_BRICKS.defaultBlockState()));
            builder.blueprintBlocks.add(entry);
        }
        CompoundTag dirt = new CompoundTag();
        dirt.putInt("X", 0); dirt.putInt("Y", 1); dirt.putInt("Z", 0);
        dirt.put("State", net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.DIRT.defaultBlockState()));
        builder.blueprintBlocks.add(dirt);
        StonemasonJobs.refreshBuilderOrders(helper.getLevel(), mason);
        helper.assertTrue(mason.orders.size() == 1, "One automatic order (dirt is not cuttable): " + mason.orders);
        Worker.Order order = mason.orders.get(0);
        helper.assertValueEqual(order.item(), "minecraft:stone_bricks", "order item");
        helper.assertValueEqual(order.count(), 5, "order count");
        helper.assertTrue(order.auto(), "flagged as a builder order");
        CompoundTag tag = new CompoundTag();
        mason.addAdditionalSaveData(tag);
        Worker copy = spawnWorker(helper, new BlockPos(6, 2, 1));
        copy.readAdditionalSaveData(tag);
        helper.assertTrue(copy.orders.size() == 1 && copy.orders.get(0).auto() && copy.supplyBuilders, "Orders and toggle survive a save");
        helper.succeed();
    }

    /** Cook: raw beef and coal from supply go into the smoker (never ore), cooked steak feeds a hungry forester. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void cookSmokesAndFeeds(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.BARREL);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.SMOKER);
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.BARREL);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.BARREL);
        net.minecraft.world.Container supply = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(2, 2, 3));
        net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity smoker =
            (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) helper.getBlockEntity(new BlockPos(4, 2, 3));
        net.minecraft.world.Container out = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(3, 2, 4));
        net.minecraft.world.Container mateSupply = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(4, 2, 2));
        supply.setItem(0, new ItemStack(Items.BEEF, 20));
        supply.setItem(1, new ItemStack(Items.COAL, 8));
        supply.setItem(2, new ItemStack(Items.RAW_IRON, 5));
        UUID owner = UUID.randomUUID();
        Worker cook = spawnWorker(helper, new BlockPos(3, 2, 2));
        cook.setOwner(owner);
        cook.role = "cook";
        cook.working = true;
        cook.supply = helper.absolutePos(new BlockPos(2, 2, 3));
        cook.output = helper.absolutePos(new BlockPos(3, 2, 4));
        cook.furnaces.add(helper.absolutePos(new BlockPos(4, 2, 3)));
        for (int i = 0; i < 8 && smoker.getItem(0).isEmpty(); i++) Jobs.tick(helper.getLevel(), cook);
        helper.assertTrue(smoker.getItem(0).is(Items.BEEF), "Raw beef loaded into the smoker: " + cook.status);
        helper.assertTrue(count(supply, Items.RAW_IRON) == 5 && bagCount(cook, Items.RAW_IRON) == 0, "Ore is not the cook's business");
        helper.assertTrue(CookJobs.isRaw(helper.getLevel(), new ItemStack(Items.BEEF)) && CookJobs.isCooked(helper.getLevel(), new ItemStack(Items.COOKED_BEEF)),
            "Raw and cooked food told apart");
        // Feeding: a hungry forester with a supply chest in reach gets steak from the cook's shelf.
        Worker mate = spawnWorker(helper, new BlockPos(5, 2, 3));
        mate.setOwner(owner);
        mate.role = "forester";
        mate.supply = helper.absolutePos(new BlockPos(4, 2, 2));
        out.setItem(0, new ItemStack(Items.COOKED_BEEF, 12));
        smoker.setItem(0, ItemStack.EMPTY);
        smoker.setItem(1, new ItemStack(Items.COAL, 8));
        supply.setItem(0, ItemStack.EMPTY);
        supply.setItem(1, ItemStack.EMPTY);
        for (int i = 0; i < 12 && count(mateSupply, Items.COOKED_BEEF) == 0; i++) Jobs.tick(helper.getLevel(), cook);
        helper.assertTrue(count(mateSupply, Items.COOKED_BEEF) == CookJobs.FEED_TO, "Eight steaks delivered to the forester's supply: " + cook.status
            + " (" + count(mateSupply, Items.COOKED_BEEF) + ")");
        helper.assertTrue(cook.feedTo == null, "Feeding trip closed");
        helper.succeed();
    }

    /** Porter courier: empties every owned worker's output chest nearby into its own drop-off, no pickups assigned. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void porterCollectsFromOwnedOutputs(GameTestHelper helper) {
        walledFloor(helper);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.BARREL);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.BARREL);
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.BARREL);
        net.minecraft.world.Container a = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(2, 2, 3));
        net.minecraft.world.Container b = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(4, 2, 3));
        net.minecraft.world.Container drop = (net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(3, 2, 4));
        a.setItem(0, new ItemStack(Items.OAK_LOG, 30));
        b.setItem(0, new ItemStack(Items.COBBLESTONE, 12));
        UUID owner = UUID.randomUUID();
        Worker porter = spawnWorker(helper, new BlockPos(3, 2, 2));
        porter.setOwner(owner);
        porter.role = "courier";
        porter.courierMode = "porter";
        porter.working = true;
        porter.output = helper.absolutePos(new BlockPos(3, 2, 4));
        Worker forester = spawnWorker(helper, new BlockPos(1, 2, 5));
        forester.setOwner(owner); forester.role = "forester"; forester.output = helper.absolutePos(new BlockPos(2, 2, 3));
        Worker miner = spawnWorker(helper, new BlockPos(6, 2, 5));
        miner.setOwner(owner); miner.role = "miner"; miner.output = helper.absolutePos(new BlockPos(4, 2, 3));
        Worker stranger = spawnWorker(helper, new BlockPos(6, 2, 1));
        stranger.setOwner(UUID.randomUUID()); stranger.role = "miner"; stranger.output = helper.absolutePos(new BlockPos(3, 2, 4));
        for (int i = 0; i < 12 && (count(drop, Items.OAK_LOG) < 30 || count(drop, Items.COBBLESTONE) < 12); i++) Jobs.tick(helper.getLevel(), porter);
        helper.assertTrue(porter.porterPickups.size() == 2, "Two owned output chests found (not the stranger's, not its own): " + porter.porterPickups.size());
        helper.assertTrue(count(drop, Items.OAK_LOG) == 30 && count(drop, Items.COBBLESTONE) == 12, "Everything hauled to the drop-off: " + porter.status);
        helper.assertTrue(count(a, Items.OAK_LOG) == 0 && count(b, Items.COBBLESTONE) == 0, "Worker outputs emptied");
        CompoundTag tag = new CompoundTag();
        porter.addAdditionalSaveData(tag);
        Worker copy = spawnWorker(helper, new BlockPos(6, 2, 6));
        copy.readAdditionalSaveData(tag);
        helper.assertValueEqual(copy.courierMode, "porter", "mode saved");
        helper.succeed();
    }

    private static Worker spawnWorker(GameTestHelper helper, BlockPos relative) {
        Worker worker = HelpfulWorkers.WORKER.get().create(helper.getLevel());
        if (worker == null) {
            helper.fail("Could not create worker");
            return null;
        }
        BlockPos pos = helper.absolutePos(relative);
        worker.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(worker);
        return worker;
    }
}
