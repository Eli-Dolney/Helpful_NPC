package com.example.helpfulworkers;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.*;

final class SiteNetwork {
    record Menu(int revision, String title, String text, List<String> choices)
            implements CustomPacketPayload {
        static final Type<Menu> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID, "site_menu"));
        static final StreamCodec<RegistryFriendlyByteBuf, Menu> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeInt(p.revision);
                            b.writeUtf(p.title);
                            b.writeUtf(p.text);
                            b.writeVarInt(p.choices.size());
                            for (String c : p.choices) b.writeUtf(c);
                        },
                        b -> {
                            int id = b.readInt();
                            String title = b.readUtf(), text = b.readUtf();
                            int n = b.readVarInt();
                            if (n < 0 || n > 512)
                                throw new IllegalArgumentException("Invalid site menu");
                            List<String> rows = new ArrayList<>();
                            for (int i = 0; i < n; i++) rows.add(b.readUtf());
                            return new Menu(id, title, text, rows);
                        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    record Action(int revision, int index) implements CustomPacketPayload {
        static final Type<Action> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID, "site_action"));
        static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeInt(p.revision);
                            b.writeInt(p.index);
                        },
                        b -> new Action(b.readInt(), b.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(PayloadRegistrar r) {
        AssignmentMarkers.register(r);
        r.playToClient(
                Menu.TYPE, Menu.CODEC, (p, c) -> c.enqueueWork(() -> ClientHooks.openSite(p)));
        r.playToServer(
                Action.TYPE,
                Action.CODEC,
                (p, c) ->
                        c.enqueueWork(
                                () -> {
                                    if (c.player() instanceof ServerPlayer sp) SiteUi.action(sp, p);
                                }));
    }

    static void send(ServerPlayer p, Menu menu) {
        if (net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(
                p.connection, Menu.TYPE.id())) PacketDistributor.sendToPlayer(p, menu);
    }
}
