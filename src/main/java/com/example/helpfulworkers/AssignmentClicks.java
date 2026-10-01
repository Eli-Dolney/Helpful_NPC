package com.example.helpfulworkers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Captures block clicks during an assignment, including when the clipboard is not in hand. */
final class AssignmentClicks {
    private AssignmentClicks() {}

    static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (WorkerSessions.getClipboard(player) == null) return;
        event.setCanceled(true);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        if (player.level().isClientSide) return;
        AssignmentClipboardItem.handleClick(player, event.getPos(), event.getFace());
    }

    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (WorkerSessions.getClipboard(player) == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        if (player.level().isClientSide) return;
        AssignmentClipboardItem.handleClick(player, event.getPos(), event.getFace());
    }
}
