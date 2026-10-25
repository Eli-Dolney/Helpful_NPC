package com.example.helpfulworkers;

import net.minecraft.server.level.ServerPlayer;

/** Avoids referencing client classes from WorkerSessions. */
final class PacketDistributorHelper {
    private PacketDistributorHelper() {}

    static void closeDialogue(ServerPlayer player) {
        WorkerNetwork.send(player, new WorkerNetwork.CloseDialoguePayload());
        WorkerSessions.closeViewer(player);
    }
}
