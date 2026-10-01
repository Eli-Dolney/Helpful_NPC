package com.example.helpfulworkers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = HelpfulWorkers.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(HelpfulWorkers.WORKER_MENU.get(), WorkerInventoryScreen::new);
    }
}
