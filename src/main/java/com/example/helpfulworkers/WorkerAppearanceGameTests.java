package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(HelpfulWorkers.ID)
@PrefixGameTestTemplate(false)
public final class WorkerAppearanceGameTests {
    @GameTest(template = "empty")
    public static void namesAndSkinsPersist(GameTestHelper h) {
        var owner = h.makeMockServerPlayerInLevel();
        var w = SiteGameTests.worker(h, owner, "miner", h.absolutePos(new BlockPos(2, 2, 2)));
        owner.teleportTo(w.getX(), w.getY(), w.getZ() + 1);
        WorkerSessions.openViewer(owner, w, WorkerSessions.ViewerKind.DIALOGUE);
        h.assertTrue(
                WorkerAppearance.apply(owner, w, "  Eli's Miner  ", "alex", true).success(),
                "Owner can customize");
        h.assertTrue(w.personalName().equals("Eli's Miner"), "Trimmed personal name synchronizes");
        h.assertTrue(w.getName().getString().contains("Miner"), "Role suffix stays available");
        var tag = new CompoundTag();
        w.addAdditionalSaveData(tag);
        w.setBaseName("Changed");
        w.setAppearance("steve", false);
        w.readAdditionalSaveData(tag);
        h.assertTrue(
                w.personalName().equals("Eli's Miner")
                        && w.skinKey().equals("alex")
                        && w.slimSkin(),
                "Name, skin, and model survive reload");
        h.assertTrue(w.skinOwner().equals(owner.getUUID()), "Owner skin reference persists");
        h.assertTrue(
                WorkerAppearance.apply(owner, w, "Courier Jo", "owner", false).success(),
                "Owner skin option accepted");
        h.assertTrue(
                WorkerAppearance.apply(
                                owner,
                                w,
                                "Camp Jo",
                                "helpfulworkers:textures/entity/worker_skins/camp_jo.png",
                                true)
                        .success(),
                "Pack library key accepted");
        WorkerSessions.closeViewer(owner);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void appearanceRequiresOwnerNearbyMenuAndValidInput(GameTestHelper h) {
        var owner = h.makeMockServerPlayerInLevel();
        var other = h.makeMockServerPlayerInLevel();
        var w = SiteGameTests.worker(h, owner, "farmer", h.absolutePos(new BlockPos(2, 2, 2)));
        w.setBaseName("Original");
        owner.teleportTo(w.getX(), w.getY(), w.getZ() + 1);
        h.assertFalse(
                WorkerAppearance.apply(owner, w, "Changed", "alex", true).success(),
                "Menu required");
        WorkerSessions.openViewer(owner, w, WorkerSessions.ViewerKind.DIALOGUE);
        h.assertFalse(
                WorkerAppearance.apply(other, w, "Changed", "alex", true).success(),
                "Other owner rejected");
        for (String name :
                new String[] {"", "x".repeat(33), "bad\nname", "bad\u00a7a", "bad\u202ename"})
            h.assertFalse(
                    WorkerAppearance.apply(owner, w, name, "alex", true).success(),
                    "Invalid name rejected");
        for (String key :
                new String[] {
                    "https://example.com/skin.png",
                    "helpfulworkers:textures/entity/worker_skins/../secret.png",
                    "minecraft:textures/gui/icons.png"
                })
            h.assertFalse(
                    WorkerAppearance.apply(owner, w, "Changed", key, true).success(),
                    "Invalid skin rejected");
        h.assertTrue(
                w.personalName().equals("Original") && w.skinKey().equals("auto"),
                "Rejected changes are atomic");
        owner.teleportTo(w.getX() + 20, w.getY(), w.getZ());
        h.assertFalse(
                WorkerAppearance.apply(owner, w, "Changed", "alex", true).success(),
                "Distant request rejected");
        WorkerSessions.closeViewer(owner);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyWorkersKeepOriginalLook(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel();
        var w = SiteGameTests.worker(h, p, "miner", h.absolutePos(new BlockPos(2, 2, 2)));
        w.setBaseName("Legacy");
        var saved = new CompoundTag();
        w.addAdditionalSaveData(saved);
        saved.remove("WorkerSkin");
        saved.remove("WorkerSkinSlim");
        w.setAppearance("alex", true);
        w.readAdditionalSaveData(saved);
        h.assertTrue(
                w.skinKey().equals("auto") && !w.slimSkin() && w.personalName().equals("Legacy"),
                "Old saves retain name and UUID-based look");
        saved.putString("WorkerSkin", "bad://skin");
        w.readAdditionalSaveData(saved);
        h.assertTrue(w.skinKey().equals("auto"), "Invalid saved skin falls back safely");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void skinLibraryDirectoryIsAcceptedByMinecraft(GameTestHelper h) {
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.CLIENT_RESOURCES, java.util.List.of())) {
            h.assertTrue(resources.listResources(WorkerAppearance.CUSTOM_PREFIX,
                    id -> WorkerAppearance.validSkin(id.toString())).isEmpty(),
                    "Skin picker can scan when no custom pack is installed");
            boolean rejected = false;
            try { resources.listResources(WorkerAppearance.CUSTOM_PREFIX + "/", id -> true); }
            catch (IllegalArgumentException expected) { rejected = true; }
            h.assertTrue(rejected, "Regression fixture reproduces Minecraft's trailing-slash rejection");
        }
        h.succeed();
    }

}
