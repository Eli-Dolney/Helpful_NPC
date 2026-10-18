package com.example.helpfulworkers;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Server-owned identity settings. Skin keys are local resources, never URLs or file paths. */
final class WorkerAppearance {
    static final List<String> PRESETS =
            List.of("steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri");
    // Resource-manager directory queries must not end with a slash.
    static final String CUSTOM_PREFIX = "textures/entity/worker_skins";

    static boolean validSkin(String key) {
        if ("auto".equals(key) || "owner".equals(key) || (key != null && PRESETS.contains(key)))
            return true;
        if (key == null || key.length() > 160) return false;
        ResourceLocation id = ResourceLocation.tryParse(key);
        return id != null
                && id.getNamespace().equals(HelpfulWorkers.ID)
                && id.getPath().matches("textures/entity/worker_skins/[a-z0-9_/-]+\\.png")
                && !id.getPath().contains("//");
    }

    static ActionResult apply(
            ServerPlayer player, Worker worker, String name, String skin, boolean slim) {
        ActionResult check = WorkerActions.requireMenu(player, worker);
        if (check != null) return check;
        if (!worker.isAlive() || player.distanceToSqr(worker) > 64)
            return ActionResult.fail("Stand within eight blocks of your worker");
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty()
                || clean.length() > 32
                || clean.codePoints()
                        .anyMatch(
                                c ->
                                        Character.isISOControl(c)
                                                || Character.getType(c) == Character.FORMAT
                                                || c == 0xa7))
            return ActionResult.fail("Use a name with 1–32 characters and no formatting codes");
        if (!validSkin(skin))
            return ActionResult.fail(
                    "Choose a built-in look or a worker skin from a resource pack");
        worker.setBaseName(clean);
        worker.setAppearance(skin, slim);
        WorkerActions.sync(worker);
        return ActionResult.ok("Name and appearance saved");
    }
}
