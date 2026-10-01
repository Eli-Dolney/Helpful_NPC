package com.example.helpfulworkers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class WorkerSessions {
    enum ViewerKind { DIALOGUE, INVENTORY }

    enum ClipboardKind {
        AREA, BED, SUPPLY, OUTPUT, TABLE, BUILD_ORIGIN, FURNACE, PICKUP,
        CLEAR_AREA, CLEAR_BED, CLEAR_SUPPLY, CLEAR_OUTPUT, CLEAR_TABLE, CLEAR_ORIGIN, CLEAR_FURNACE, CLEAR_PICKUP
    }

    /** remote=true skips the 8-block proximity check (depth picker / roster). */
    record Viewer(UUID playerId, int workerId, ViewerKind kind, boolean remote) {}

    static final class ClipboardSession {
        final UUID playerId;
        final int workerId;
        final ClipboardKind kind;
        BlockPos pendingFirst;
        BlockPos pendingConfirm;
        boolean awaitingConfirm;
        int handledTick = -1;
        BlockPos handledPos;
        /** After terraform footprint clicks, wait for depth before saving. */
        boolean awaitingDepth;
        /** Builder marking a plot to auto-start a free village house. */
        boolean villageBuild;

        ClipboardSession(UUID playerId, int workerId, ClipboardKind kind) {
            this.playerId = playerId;
            this.workerId = workerId;
            this.kind = kind;
        }
    }

    private static final Map<UUID, Viewer> VIEWERS = new HashMap<>();
    private static final Map<UUID, ClipboardSession> CLIPBOARDS = new HashMap<>();
    private static final Set<Integer> SUSPENDED = new HashSet<>();

    private WorkerSessions() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(WorkerSessions::onLogout);
        NeoForge.EVENT_BUS.addListener(WorkerSessions::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(WorkerSessions::onServerTick);
        NeoForge.EVENT_BUS.addListener(AssignmentClicks::onLeftClick);
        NeoForge.EVENT_BUS.addListener(AssignmentClicks::onRightClick);
    }

    static void openViewer(ServerPlayer player, Worker worker, ViewerKind kind) {
        openViewer(player, worker, kind, false);
    }

    static void openViewer(ServerPlayer player, Worker worker, ViewerKind kind, boolean remote) {
        VIEWERS.put(player.getUUID(), new Viewer(player.getUUID(), worker.getId(), kind, remote));
        suspend(worker);
    }

    static void closeViewer(ServerPlayer player) {
        Viewer viewer = VIEWERS.remove(player.getUUID());
        if (viewer != null) {
            Entity entity = player.serverLevel().getEntity(viewer.workerId());
            if (entity instanceof Worker worker) refreshSuspension(worker);
        }
    }

    static boolean hasDialogueOrInventory(ServerPlayer player, Worker worker) {
        Viewer viewer = VIEWERS.get(player.getUUID());
        return viewer != null && viewer.workerId() == worker.getId();
    }

    static boolean isSuspended(Worker worker) {
        return SUSPENDED.contains(worker.getId());
    }

    static void suspend(Worker worker) {
        SUSPENDED.add(worker.getId());
        worker.getNavigation().stop();
    }

    static void refreshSuspension(Worker worker) {
        boolean any = VIEWERS.values().stream().anyMatch(v -> v.workerId() == worker.getId());
        if (any) SUSPENDED.add(worker.getId());
        else SUSPENDED.remove(worker.getId());
    }

    static ClipboardSession getClipboard(ServerPlayer player) {
        return CLIPBOARDS.get(player.getUUID());
    }

    static ClipboardSession startClipboard(ServerPlayer player, Worker worker, ClipboardKind kind) {
        ClipboardSession session = new ClipboardSession(player.getUUID(), worker.getId(), kind);
        CLIPBOARDS.put(player.getUUID(), session);
        return session;
    }

    static void clearClipboard(ServerPlayer player) {
        CLIPBOARDS.remove(player.getUUID());
    }

    static void cancelClipboardForWorker(int workerId) {
        CLIPBOARDS.entrySet().removeIf(e -> e.getValue().workerId == workerId);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        closeViewer(player);
        clearClipboard(player);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Viewer viewer = VIEWERS.get(player.getUUID());
        if (viewer != null) {
            Entity entity = player.serverLevel().getEntity(viewer.workerId());
            boolean deadOrGone = !(entity instanceof Worker worker) || !worker.isAlive()
                || worker.level() != player.level();
            boolean tooFar = !viewer.remote() && entity instanceof Worker w && player.distanceToSqr(w) > 64;
            if (deadOrGone || tooFar) {
                player.closeContainer();
                PacketDistributorHelper.closeDialogue(player);
                closeViewer(player);
            }
        }
        ClipboardSession session = CLIPBOARDS.get(player.getUUID());
        if (session != null) {
            Entity entity = player.level().getEntity(session.workerId);
            if (!(entity instanceof Worker worker) || !worker.isAlive() || worker.level() != player.level()) {
                clearClipboard(player);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Assignment cancelled"), true);
            }
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, ClipboardSession>> it = CLIPBOARDS.entrySet().iterator();
        while (it.hasNext()) {
            ClipboardSession session = it.next().getValue();
            boolean found = false;
            for (var level : event.getServer().getAllLevels()) {
                Entity entity = level.getEntity(session.workerId);
                if (entity instanceof Worker) { found = true; break; }
            }
            if (!found) it.remove();
        }
    }
}
