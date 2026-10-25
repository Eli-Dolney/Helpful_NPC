package com.example.helpfulworkers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = HelpfulWorkers.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
final class ClientModEvents {
    private ClientModEvents() {}

    /** Mods list → Helpful Workers → Config: NeoForge's generated screen for the server and client configs. */
    static void registerConfigScreen(net.neoforged.fml.ModContainer container) {
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
            net.neoforged.neoforge.client.gui.ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(HelpfulWorkers.WORKER_MENU.get(), WorkerInventoryScreen::new);
    }
}
