package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Knock-out, no fall damage, ignore owner friendly fire. */
final class WorkerCombat {
    private WorkerCombat() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(WorkerCombat::onIncoming);
        NeoForge.EVENT_BUS.addListener(WorkerCombat::onDamage);
        NeoForge.EVENT_BUS.addListener(WorkerCombat::onDeath);
    }

    private static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Worker worker)) return;
        if (event.getSource().is(DamageTypes.FALL)) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof Player player && worker.owns(player)) {
            event.setCanceled(true);
        }
    }

    private static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Worker worker)) return;
        if (event.getSource().is(DamageTypes.FALL)) {
            event.setNewDamage(0);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Worker worker)) return;
        if (!(worker.level() instanceof ServerLevel level)) return;
        event.setCanceled(true);
        knockOut(level, worker);
    }

    static void knockOut(ServerLevel level, Worker worker) {
        worker.setHealth(Math.max(1f, worker.getMaxHealth() * 0.5f));
        worker.working = false;
        worker.target = null;
        worker.getNavigation().stop();
        worker.recoverUntil = level.getGameTime() + 20L * 60L;
        worker.setStatus("Recovering at home");
        BlockPos dest = worker.bed;
        if (dest != null && level.isLoaded(dest)) {
            if (!worker.hopNear(dest)) {
                worker.teleportTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
            }
        } else {
            var owner = worker.findOwner();
            if (owner != null) {
                BlockPos near = owner.blockPosition();
                if (!worker.hopNear(near)) {
                    worker.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                }
            }
        }
        WorkerActions.sync(worker);
        WorkerRegistry.upsert(worker);
    }
}
