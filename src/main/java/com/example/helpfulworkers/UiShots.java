package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Dev-only UI check: with -Dhelpfulworkers.uishots=true (the uiShots run), once a world is loaded
 * this opens each mod screen with sample data, saves a screenshot at several GUI scales into
 * run/screenshots/hw_*.png, then quits. Does nothing in normal play.
 */
@EventBusSubscriber(modid = HelpfulWorkers.ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
final class UiShots {
    private UiShots() {}

    private static final boolean ENABLED = Boolean.getBoolean("helpfulworkers.uishots");
    private static final int[] SCALES = {2, 4};
    private record Shot(String name, Supplier<Screen> screen) {}
    private static List<Shot> shots;
    private static int index;
    private static int scaleIndex;
    private static int timer = -100; // let the world settle first

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (shots == null) {
            shots = buildShots();
            mc.getWindow().setWindowed(1280, 960);
        }
        if (++timer < 0) return;
        if (scaleIndex >= SCALES.length) { mc.stop(); return; }
        Shot shot = shots.get(index);
        if (timer == 1) {
            mc.options.guiScale().set(SCALES[scaleIndex]);
            mc.resizeDisplay();
            mc.setScreen(shot.screen().get());
        } else if (timer == 15) {
            Screenshot.grab(mc.gameDirectory, "hw_" + shot.name() + "_gui" + SCALES[scaleIndex] + ".png",
                mc.getMainRenderTarget(), msg -> HelpfulWorkers.LOGGER.info("UI shot: {}", msg.getString()));
        } else if (timer >= 20) {
            if (mc.screen instanceof WorkerDialogueScreen d) d.suppressClosePacket();
            mc.setScreen(null);
            timer = 0;
            if (++index >= shots.size()) { index = 0; scaleIndex++; }
        }
    }

    private static List<Shot> buildShots() {
        List<Shot> list = new ArrayList<>();
        String status = StatusText.join(StatusText.encode(Lang.STATUS_WORKING_ON, StatusText.name(Blocks.STONE)),
            StatusText.encode(Lang.STATUS_REQUEST, 10, 64, StatusText.name(Items.OAK_LOG)));
        list.add(new Shot("dialogue_main_farmer", () -> dialogue("farmer", status, "MAIN")));
        list.add(new Shot("dialogue_work_miner", () -> dialogue("miner", status, "WORK")));
        list.add(new Shot("dialogue_where_miner", () -> dialogue("miner", status, "LOCATIONS")));
        list.add(new Shot("dialogue_crops_gatherer", () -> dialogue("gatherer", status, "CROPS")));
        list.add(new Shot("dialogue_ranch", () -> dialogue("rancher", status, "RANCH")));
        list.add(new Shot("dialogue_routes_courier", () -> dialogue("courier", status, "ROUTES")));
        list.add(new Shot("dialogue_needs", () -> dialogue("miner", status, "REQUESTS")));
        list.add(new Shot("dialogue_build", () -> dialogue("builder", status, "BUILD")));
        list.add(new Shot("inventory", UiShots::inventory));
        list.add(new Shot("roster", UiShots::roster));
        list.add(new Shot("config", () -> ModList.get().getModContainerById(HelpfulWorkers.ID)
            .map(c -> (Screen) new net.neoforged.neoforge.client.gui.ConfigurationScreen(c, null)).orElse(null)));
        return list;
    }

    private static Screen dialogue(String role, String status, String page) {
        List<String> flags = new ArrayList<>(List.of("crop:wheat", "crop:carrots", "crop:sugar_cane", "crop:sweet_berries",
            "rancher:sheep", "rancher:cow", "breed", "honey", "siteprep:clear", "level:3|420|700", "trait:cheerful",
            "pickup:0|10,64,-3|16|minecraft:iron_ingot", "pickup:1|14,64,2|0|",
            "req:0|minecraft:oak_log|10|64", "req:1|minecraft:cobblestone|64|64", "reqstop"));
        var payload = new WorkerNetwork.OpenDialoguePayload(-1, "Ada", role, status, true, "quarry", "",
            true, true, true, false, false, false, "", 0, 10, flags);
        return WorkerDialogueScreen.preview(payload, page);
    }

    private static Screen inventory() {
        Minecraft mc = Minecraft.getInstance();
        Worker w = new Worker(HelpfulWorkers.WORKER.get(), mc.level);
        w.getEntityData().set(Worker.DATA_ROLE, "miner");
        w.getEntityData().set(Worker.DATA_STATUS, StatusText.encode(Lang.STATUS_QUARRIED, StatusText.name(Blocks.GRAVEL), "12, 64, -3"));
        w.getEntityData().set(Worker.DATA_STATE, Worker.STATE_WORKING);
        w.getEntityData().set(Worker.DATA_LEVEL, (byte) 3);
        w.getEntityData().set(Worker.DATA_XP, 420);
        w.getEntityData().set(Worker.DATA_TRAIT, "curious");
        w.getEntityData().set(Worker.DATA_RESERVES, "minecraft:torch=16");
        w.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        w.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        w.bag.setItem(0, new ItemStack(Items.TORCH, 24));
        w.bag.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        w.bag.setItem(2, new ItemStack(Items.RAW_IRON, 9));
        var menu = new WorkerInventoryMenu(0, mc.player.getInventory(), w);
        return new WorkerInventoryScreen(menu, mc.player.getInventory(), Component.translatable(Lang.INV_TITLE, "Ada"));
    }

    private static Screen roster() {
        List<WorkerNetwork.RosterEntry> entries = List.of(
            new WorkerNetwork.RosterEntry(1, "Bo", "miner", StatusText.encode(Lang.STATUS_FULL), true, 10, 64, -3, 12, true, true,
                Worker.STATE_BLOCKED, -1, 2),
            new WorkerNetwork.RosterEntry(2, "Ada", "farmer", StatusText.join(StatusText.encode(Lang.STATUS_WORKING_ON,
                StatusText.name(Blocks.WHEAT)), StatusText.encode(Lang.STATUS_REQUEST, 10, 64, StatusText.name(Items.WHEAT))),
                true, 40, 70, 8, 33, true, true, Worker.STATE_WORKING, 2400, 4),
            new WorkerNetwork.RosterEntry(3, "Cy", "knight", "Following you", true, 1, 64, 1, 3, false, false,
                Worker.STATE_IDLE, -1, 1),
            new WorkerNetwork.RosterEntry(-1, "Dee [away]", "courier", "Unloaded chunk — last seen here", false, 300, 64, 90, 320,
                false, false, (byte) -1, -1, 0));
        return new WorkerRosterScreen(new WorkerNetwork.RosterPayload(entries));
    }
}
