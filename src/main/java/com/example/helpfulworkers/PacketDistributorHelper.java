package com.example.helpfulworkers;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Avoids referencing client classes from WorkerSessions. */
final class PacketDistributorHelper {
    private PacketDistributorHelper() {}

    static void closeDialogue(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new WorkerNetwork.CloseDialoguePayload());
        WorkerSessions.closeViewer(player);
    }
}
