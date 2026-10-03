package com.example.helpfulworkers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** One resolver shared by the entity, dialogue portrait, roster, and picker. */
@net.neoforged.fml.common.EventBusSubscriber(
        modid = HelpfulWorkers.ID,
        bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,
        value = net.neoforged.api.distmarker.Dist.CLIENT)
final class WorkerSkins {
    private static final Map<ResourceLocation, Boolean> VALID_IMAGES =
            new java.util.concurrent.ConcurrentHashMap<>();

    @net.neoforged.bus.api.SubscribeEvent
    static void registerReload(
            net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                        manager -> VALID_IMAGES.clear());
    }

    record Look(ResourceLocation texture, boolean slim, boolean missing) {}

    static Look resolve(Worker worker) {
        return resolve(worker, worker.skinKey(), worker.slimSkin());
    }

    static Look resolve(Worker worker, String key, boolean slim) {
        PlayerSkin fallback = DefaultPlayerSkin.get(worker.getUUID());
        if ("auto".equals(key))
            return new Look(fallback.texture(), fallback.model() == PlayerSkin.Model.SLIM, false);
        if ("owner".equals(key)) {
            var connection = Minecraft.getInstance().getConnection();
            var info =
                    connection == null || worker.skinOwner() == null
                            ? null
                            : connection.getPlayerInfo(worker.skinOwner());
            if (info != null) {
                var skin = info.getSkin();
                return new Look(skin.texture(), skin.model() == PlayerSkin.Model.SLIM, false);
            }
            return new Look(fallback.texture(), fallback.model() == PlayerSkin.Model.SLIM, true);
        }
        ResourceLocation id =
                WorkerAppearance.PRESETS.contains(key)
                        ? ResourceLocation.withDefaultNamespace(
                                "textures/entity/player/"
                                        + (slim ? "slim/" : "wide/")
                                        + key
                                        + ".png")
                        : ResourceLocation.tryParse(key);
        if (id == null
                || Minecraft.getInstance().getResourceManager().getResource(id).isEmpty()
                || (!WorkerAppearance.PRESETS.contains(key)
                        && !VALID_IMAGES.computeIfAbsent(
                                id,
                                resource ->
                                        Minecraft.getInstance()
                                                .getResourceManager()
                                                .getResource(resource)
                                                .map(WorkerSkins::validImage)
                                                .orElse(false))))
            return new Look(fallback.texture(), fallback.model() == PlayerSkin.Model.SLIM, true);
        return new Look(id, slim, false);
    }

    static List<String> choices() {
        List<String> keys = new ArrayList<>();
        keys.add("auto");
        keys.add("owner");
        keys.addAll(WorkerAppearance.PRESETS);
        Minecraft.getInstance()
                .getResourceManager()
                .listResources(
                        WorkerAppearance.CUSTOM_PREFIX,
                        id -> WorkerAppearance.validSkin(id.toString()))
                .entrySet()
                .stream()
                .filter(entry -> validImage(entry.getValue()))
                .map(entry -> entry.getKey().toString())
                .sorted()
                .forEach(keys::add);
        return keys;
    }

    private static boolean validImage(net.minecraft.server.packs.resources.Resource resource) {
        try (var stream = resource.open()) {
            byte[] bytes = stream.readNBytes(131073);
            if (bytes.length < 24 || bytes.length > 131072) return false;
            var header = java.nio.ByteBuffer.wrap(bytes);
            if (header.getLong() != 0x89504e470d0a1a0aL
                    || header.getInt(16) != 64
                    || header.getInt(20) != 64) return false;
            try (var image =
                    com.mojang.blaze3d.platform.NativeImage.read(
                            new java.io.ByteArrayInputStream(bytes))) {
                return image.getWidth() == 64 && image.getHeight() == 64;
            }
        } catch (Exception e) {
            return false;
        }
    }

    static String label(String key) {
        if ("auto".equals(key)) return "Original look";
        if ("owner".equals(key)) return "Match owner";
        String name = key;
        if (key.contains(":")) name = key.substring(key.lastIndexOf('/') + 1).replace(".png", "");
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('_', ' ');
    }
}
