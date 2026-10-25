package com.example.helpfulworkers;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Server side of the clipboard's worker monitor. Works at any distance within the player's dimension. */
final class RosterActions {
    private RosterActions() {}

    static void handle(ServerPlayer player, int workerId, String action) {
        if ("refresh".equals(action)) {
            WorkerNetwork.sendRoster(player);
            return;
        }
        Worker worker = WorkerActions.findOwned(player, workerId);
        if (worker == null) {
            WorkerNetwork.toast(player, Component.translatable(Lang.TOAST_NOT_LOADED));
            WorkerNetwork.sendRoster(player);
            return;
        }
        ActionResult result = switch (action) {
            case "pause" -> WorkerActions.pause(player, worker);
            case "resume" -> {
                worker.unreachable.clear();
                worker.nav.reset();
                yield WorkerActions.start(player, worker);
            }
            case "recall" -> {
                if (worker.bed == null && worker.hall != null) {
                    yield WorkerActions.home(player, worker); // walks to the town hall
                }
                if (worker.bed == null) {
                    worker.comeTo(player);
                    yield ActionResult.ok(worker.getName().getString() + " has no bed, so they're coming to you");
                }
                ActionResult home = WorkerActions.home(player, worker);
                if (worker.distanceToSqr(worker.bed.getX() + 0.5, worker.bed.getY(), worker.bed.getZ() + 0.5) > 40 * 40) {
                    // Explicit recall: a far hop beside the bed is allowed here.
                    worker.nav.teleportBeside(worker.bed);
                    worker.setStatusKey(Lang.STATUS_BACK_AT_BED_PAUSED);
                }
                yield home;
            }
            case "come" -> {
                worker.comeTo(player);
                yield ActionResult.ok(worker.getName().getString() + " is coming to you");
            }
            case "talk" -> {
                WorkerNetwork.openDialogueRemote(player, worker);
                yield ActionResult.ok("Talking to " + worker.getName().getString());
            }
            case "teleport" -> {
                if (!player.isCreative()) yield ActionResult.fail("Go-to is creative mode only");
                player.teleportTo(worker.getX(), worker.getY(), worker.getZ());
                yield ActionResult.ok("Went to " + worker.getName().getString());
            }
            case "outline" -> {
                WorkerNetwork.sendAreaOutline(player, worker, 200);
                yield ActionResult.ok(worker.first == null ? "No work area set" : "Showing work area");
            }
            default -> ActionResult.fail("Unknown action");
        };
        WorkerActions.sync(worker);
        WorkerNetwork.toast(player, result.text());
        if (!"talk".equals(action)) WorkerNetwork.sendRoster(player);
    }
}
