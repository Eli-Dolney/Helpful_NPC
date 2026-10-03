package com.example.helpfulworkers;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
        player.teleportTo(chest.getX() + 1, chest.getY(), chest.getZ() + 1);
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
