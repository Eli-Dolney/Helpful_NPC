package com.example.helpfulworkers;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class WorkerAppearanceNetwork {
    record Save(int workerId, String name, String skin, boolean slim)
            implements CustomPacketPayload {
        static final Type<Save> TYPE =
                new Type<>(
                        ResourceLocation.fromNamespaceAndPath(
                                HelpfulWorkers.ID, "save_appearance"));
        static final StreamCodec<RegistryFriendlyByteBuf, Save> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeVarInt(p.workerId);
                            b.writeUtf(p.name, 32);
                            b.writeUtf(p.skin, 160);
                            b.writeBoolean(p.slim);
                        },
                        b ->
                                new Save(
                                        b.readVarInt(),
                                        b.readUtf(32),
                                        b.readUtf(160),
                                        b.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    record Result(int workerId, boolean success, String message) implements CustomPacketPayload {
        static final Type<Result> TYPE =
                new Type<>(
                        ResourceLocation.fromNamespaceAndPath(
                                HelpfulWorkers.ID, "appearance_result"));
        static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeVarInt(p.workerId);
                            b.writeBoolean(p.success);
                            b.writeUtf(p.message, 256);
                        },
                        b -> new Result(b.readVarInt(), b.readBoolean(), b.readUtf(256)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(PayloadRegistrar r) {
        r.playToServer(
                Save.TYPE,
                Save.CODEC,
                (p, c) ->
                        c.enqueueWork(
                                () -> {
                                    if (c.player() instanceof ServerPlayer player) {
                                        Worker w = WorkerActions.findOwned(player, p.workerId());
                                        ActionResult result =
                                                WorkerAppearance.apply(
                                                        player, w, p.name(), p.skin(), p.slim());
                                        PacketDistributor.sendToPlayer(
                                                player,
                                                new Result(
                                                        p.workerId(),
                                                        result.success(),
                                                        result.text()));
                                    }
                                }));
        r.playToClient(
                Result.TYPE,
                Result.CODEC,
                (p, c) -> c.enqueueWork(() -> ClientHooks.appearanceResult(p)));
    }
}
