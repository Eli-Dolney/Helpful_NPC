package com.example.helpfulworkers;

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
            WorkerNetwork.toast(player, "Worker not found — they may be in an unloaded chunk");
            WorkerNetwork.sendRoster(player);
            return;
        }
        ActionResult result = switch (action) {
            case "pause" -> WorkerActions.pause(player, worker);
            case "resume" -> {
                worker.unreachable.clear();
                worker.failedRoutes = 0;
                yield WorkerActions.start(player, worker);
            }
            case "recall" -> {
                if (worker.bed == null) {
                    worker.comeTo(player);
                    yield ActionResult.ok(worker.getName().getString() + " has no bed, so they're coming to you");
                }
                ActionResult home = WorkerActions.home(player, worker);
                if (worker.distanceToSqr(worker.bed.getX() + 0.5, worker.bed.getY(), worker.bed.getZ() + 0.5) > 40 * 40) {
                    worker.hopNear(worker.bed);
                    worker.status = "Back at bed (paused)";
                }
                yield home;
            }
            case "come" -> {
                worker.comeTo(player);
                yield ActionResult.ok(worker.getName().getString() + " is coming to you");
            }
            case "outline" -> {
                WorkerNetwork.sendAreaOutline(player, worker, 200);
                yield ActionResult.ok(worker.first == null ? "No work area set" : "Showing work area");
            }
            default -> ActionResult.fail("Unknown action");
        };
        WorkerActions.sync(worker);
        WorkerNetwork.toast(player, result.text());
        WorkerNetwork.sendRoster(player);
    }
}
