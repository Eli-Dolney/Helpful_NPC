package com.example.helpfulworkers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Client-only hooks invoked from network handlers. Do not call from dedicated-server code paths. */
@OnlyIn(Dist.CLIENT)
final class ClientHooks {
    private ClientHooks() {}

    static void appearanceResult(WorkerAppearanceNetwork.Result result) {
        if (Minecraft.getInstance().screen instanceof WorkerAppearanceScreen screen) screen.result(result);
    }

    static void openSite(SiteNetwork.Menu menu) {
        Minecraft mc = Minecraft.getInstance();
        if (menu.revision() < 0) { if (mc.screen instanceof SiteScreen) mc.setScreen(null); return; }
        if (mc.screen instanceof SiteScreen screen) screen.update(menu);
        else mc.setScreen(new SiteScreen(menu));
    }

    static void openDialogue(WorkerNetwork.OpenDialoguePayload payload) {
        Minecraft.getInstance().setScreen(new WorkerDialogueScreen(payload));
    }

    static void openDepthPicker(WorkerNetwork.OpenDepthPickerPayload payload) {
        Minecraft.getInstance().setScreen(WorkerDialogueScreen.depthPicker(payload));
    }

    static void closeDialogue() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof WorkerDialogueScreen dialogue) {
            dialogue.suppressClosePacket();
            Minecraft.getInstance().setScreen(null);
        }
    }

    static void updateStatus(WorkerNetwork.WorkerStatusPayload payload) {
        if (Minecraft.getInstance().screen instanceof WorkerDialogueScreen dialogue) {
            dialogue.applyStatus(payload);
        } else if (Minecraft.getInstance().screen instanceof WorkerAppearanceScreen appearance) {
            appearance.forwardStatus(payload);
        }
    }

    static void updateRecipes(WorkerNetwork.RecipeListPayload payload) {
        if (Minecraft.getInstance().screen instanceof WorkerDialogueScreen dialogue) {
            dialogue.applyRecipes(payload);
        }
    }

    static void updateBlueprints(WorkerNetwork.BlueprintListPayload payload) {
        if (Minecraft.getInstance().screen instanceof WorkerDialogueScreen dialogue) {
            dialogue.applyBlueprints(payload);
        }
    }

    static void showRoster(WorkerNetwork.RosterPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WorkerRosterScreen roster) roster.apply(payload);
        else mc.setScreen(new WorkerRosterScreen(payload));
    }

    static void showAreaOutline(WorkerNetwork.AreaOutlinePayload payload) {
        AreaOutlineClient.show(payload);
    }

    static void toast(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), true);
        }
    }
}
