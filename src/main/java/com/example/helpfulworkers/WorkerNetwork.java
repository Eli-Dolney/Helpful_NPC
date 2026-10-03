package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = HelpfulWorkers.ID, bus = EventBusSubscriber.Bus.MOD)
final class WorkerNetwork {
    private WorkerNetwork() {}

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("5");
        SiteNetwork.register(registrar);
        WorkerAppearanceNetwork.register(registrar);
        registrar.playToClient(OpenDialoguePayload.TYPE, OpenDialoguePayload.STREAM_CODEC, WorkerNetwork::handleOpenDialogue);
        registrar.playToClient(CloseDialoguePayload.TYPE, CloseDialoguePayload.STREAM_CODEC, WorkerNetwork::handleCloseDialogue);
        registrar.playToClient(WorkerStatusPayload.TYPE, WorkerStatusPayload.STREAM_CODEC, WorkerNetwork::handleStatus);
        registrar.playToClient(RecipeListPayload.TYPE, RecipeListPayload.STREAM_CODEC, WorkerNetwork::handleRecipes);
        registrar.playToClient(BlueprintListPayload.TYPE, BlueprintListPayload.STREAM_CODEC, WorkerNetwork::handleBlueprints);
        registrar.playToClient(ToastPayload.TYPE, ToastPayload.STREAM_CODEC, WorkerNetwork::handleToast);
        registrar.playToClient(AreaOutlinePayload.TYPE, AreaOutlinePayload.STREAM_CODEC, WorkerNetwork::handleOutline);
        registrar.playToClient(OpenDepthPickerPayload.TYPE, OpenDepthPickerPayload.STREAM_CODEC, WorkerNetwork::handleDepthPicker);
        registrar.playToServer(DialogueActionPayload.TYPE, DialogueActionPayload.STREAM_CODEC, WorkerNetwork::handleAction);
        registrar.playToClient(RosterPayload.TYPE, RosterPayload.STREAM_CODEC, WorkerNetwork::handleRoster);
        registrar.playToServer(RosterActionPayload.TYPE, RosterActionPayload.STREAM_CODEC, WorkerNetwork::handleRosterAction);
    }

    static void sendRoster(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, RosterPayload.from(player));
    }

    private static void handleRoster(RosterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.showRoster(payload));
    }

    private static void handleRosterAction(RosterActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            RosterActions.handle(player, payload.workerId(), payload.action());
        });
    }

    record RosterEntry(int id, String name, String role, String status, boolean working,
                       int x, int y, int z, int distance, boolean hasBed, boolean hasArea) {}

    record RosterPayload(List<RosterEntry> entries) implements CustomPacketPayload {
        static final Type<RosterPayload> TYPE = new Type<>(id("roster"));
        static final StreamCodec<RegistryFriendlyByteBuf, RosterPayload> STREAM_CODEC =
            StreamCodec.of(RosterPayload::encode, RosterPayload::decode);

        private static void encode(RegistryFriendlyByteBuf buf, RosterPayload p) {
            buf.writeVarInt(p.entries.size());
            for (RosterEntry e : p.entries) {
                buf.writeVarInt(e.id());
                buf.writeUtf(e.name());
                buf.writeUtf(e.role());
                buf.writeUtf(e.status());
                buf.writeBoolean(e.working());
                buf.writeInt(e.x()); buf.writeInt(e.y()); buf.writeInt(e.z());
                buf.writeVarInt(e.distance());
                buf.writeBoolean(e.hasBed());
                buf.writeBoolean(e.hasArea());
            }
        }

        private static RosterPayload decode(RegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<RosterEntry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(new RosterEntry(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean()));
            }
            return new RosterPayload(entries);
        }

        static RosterPayload from(ServerPlayer player) {
            List<RosterEntry> entries = new ArrayList<>();
            java.util.HashSet<java.util.UUID> seen = new java.util.HashSet<>();
            for (Worker worker : player.serverLevel().getEntities(HelpfulWorkers.WORKER.get(), w -> w.owns(player))) {
                seen.add(worker.getUUID());
                BlockPos pos = worker.blockPosition();
                entries.add(new RosterEntry(worker.getId(), worker.getName().getString(), worker.role,
                    worker.status == null ? "" : worker.status, worker.working, pos.getX(), pos.getY(), pos.getZ(),
                    (int) Math.sqrt(player.distanceToSqr(worker)), worker.bed != null,
                    worker.first != null && worker.second != null));
            }
            // Unloaded workers from the registry (last known position).
            for (WorkerRegistry.Entry e : WorkerRegistry.forOwner(player.serverLevel(), player.getUUID())) {
                if (seen.contains(e.entityId())) continue;
                BlockPos pos = e.pos();
                int dist = (int) Math.sqrt(player.blockPosition().distSqr(pos));
                String status = e.alive() ? "Unloaded chunk — last seen here" : "Missing (may need re-recruit)";
                entries.add(new RosterEntry(-1, e.name() + " [away]", e.role(), status, false,
                    pos.getX(), pos.getY(), pos.getZ(), dist, false, false));
            }
            entries.sort(java.util.Comparator.comparingInt(RosterEntry::distance));
            return new RosterPayload(entries);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record RosterActionPayload(int workerId, String action) implements CustomPacketPayload {
        static final Type<RosterActionPayload> TYPE = new Type<>(id("roster_action"));
        static final StreamCodec<RegistryFriendlyByteBuf, RosterActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RosterActionPayload::workerId,
            ByteBufCodecs.STRING_UTF8, RosterActionPayload::action,
            RosterActionPayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void openDialogue(ServerPlayer player, Worker worker, String pendingRole) {
        WorkerSessions.openViewer(player, worker, WorkerSessions.ViewerKind.DIALOGUE);
        PacketDistributor.sendToPlayer(player, OpenDialoguePayload.from(worker, pendingRole));
        sendRecipeList(player, worker);
        sendBlueprintList(player, worker);
    }

    static void openDepthPicker(ServerPlayer player, Worker worker, BlockPos first, BlockPos second) {
        WorkerSessions.openViewer(player, worker, WorkerSessions.ViewerKind.DIALOGUE, true);
        PacketDistributor.sendToPlayer(player, new OpenDepthPickerPayload(worker.getId(), worker.getName().getString(),
            first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ()));
        // Preview outline at depth 5 while choosing.
        PacketDistributor.sendToPlayer(player, AreaOutlinePayload.preview(worker.getId(), first, second, 5, 120));
    }

    static void sendAreaOutline(ServerPlayer player, Worker worker, int ticks) {
        if (net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(player.connection, AreaOutlinePayload.TYPE.id()))
            PacketDistributor.sendToPlayer(player, AreaOutlinePayload.from(worker, ticks));
    }

    static void toast(ServerPlayer player, String message) {
        PacketDistributor.sendToPlayer(player, new ToastPayload(message));
    }

    static void syncStatusToViewers(Worker worker) {
        WorkerStatusPayload payload = WorkerStatusPayload.from(worker);
        for (Player player : worker.level().players()) {
            if (player instanceof ServerPlayer serverPlayer && WorkerSessions.hasDialogueOrInventory(serverPlayer, worker)
                && net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(serverPlayer.connection, WorkerStatusPayload.TYPE.id())) {
                PacketDistributor.sendToPlayer(serverPlayer, payload);
            }
        }
    }

    static void sendRecipeList(ServerPlayer player, Worker worker) {
        PacketDistributor.sendToPlayer(player, RecipeListPayload.from(worker));
    }

    static void sendBlueprintList(ServerPlayer player, Worker worker) {
        PacketDistributor.sendToPlayer(player, BlueprintListPayload.from(worker));
    }

    private static void handleOpenDialogue(OpenDialoguePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.openDialogue(payload));
    }

    private static void handleCloseDialogue(CloseDialoguePayload payload, IPayloadContext context) {
        context.enqueueWork(ClientHooks::closeDialogue);
    }

    private static void handleStatus(WorkerStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.updateStatus(payload));
    }

    private static void handleRecipes(RecipeListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.updateRecipes(payload));
    }

    private static void handleBlueprints(BlueprintListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.updateBlueprints(payload));
    }

    private static void handleToast(ToastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.toast(payload.message()));
    }

    private static void handleOutline(AreaOutlinePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.showAreaOutline(payload));
    }

    private static void handleDepthPicker(OpenDepthPickerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.openDepthPicker(payload));
    }

    private static void handleAction(DialogueActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            DialogueHandlers.handle(player, payload);
        });
    }

    record OpenDialoguePayload(int workerId, String name, String role, String status, boolean working,
                               String mode, String pendingRole, boolean hasArea, boolean hasBed,
                               boolean hasSupply, boolean hasOutput, boolean hasTable, boolean hasOrigin,
                               String blueprint, int rotation, int digDepth, List<String> flags)
        implements CustomPacketPayload {
        static final Type<OpenDialoguePayload> TYPE = new Type<>(id("open_dialogue"));
        static final StreamCodec<RegistryFriendlyByteBuf, OpenDialoguePayload> STREAM_CODEC =
            StreamCodec.of(OpenDialoguePayload::encode, OpenDialoguePayload::decode);

        private static void encode(RegistryFriendlyByteBuf buf, OpenDialoguePayload p) {
            buf.writeVarInt(p.workerId);
            buf.writeUtf(p.name);
            buf.writeUtf(p.role);
            buf.writeUtf(p.status);
            buf.writeBoolean(p.working);
            buf.writeUtf(p.mode);
            buf.writeUtf(p.pendingRole);
            buf.writeBoolean(p.hasArea);
            buf.writeBoolean(p.hasBed);
            buf.writeBoolean(p.hasSupply);
            buf.writeBoolean(p.hasOutput);
            buf.writeBoolean(p.hasTable);
            buf.writeBoolean(p.hasOrigin);
            buf.writeUtf(p.blueprint);
            buf.writeVarInt(p.rotation);
            buf.writeVarInt(p.digDepth);
            buf.writeVarInt(p.flags.size());
            for (String f : p.flags) buf.writeUtf(f);
        }

        private static OpenDialoguePayload decode(RegistryFriendlyByteBuf buf) {
            int workerId = buf.readVarInt();
            String name = buf.readUtf();
            String role = buf.readUtf();
            String status = buf.readUtf();
            boolean working = buf.readBoolean();
            String mode = buf.readUtf();
            String pendingRole = buf.readUtf();
            boolean hasArea = buf.readBoolean();
            boolean hasBed = buf.readBoolean();
            boolean hasSupply = buf.readBoolean();
            boolean hasOutput = buf.readBoolean();
            boolean hasTable = buf.readBoolean();
            boolean hasOrigin = buf.readBoolean();
            String blueprint = buf.readUtf();
            int rotation = buf.readVarInt();
            int digDepth = buf.readVarInt();
            int flagCount = buf.readVarInt();
            List<String> flags = new ArrayList<>(flagCount);
            for (int i = 0; i < flagCount; i++) flags.add(buf.readUtf());
            return new OpenDialoguePayload(workerId, name, role, status, working, mode, pendingRole,
                hasArea, hasBed, hasSupply, hasOutput, hasTable, hasOrigin, blueprint, rotation, digDepth, flags);
        }

        static OpenDialoguePayload from(Worker worker, String pendingRole) {
            return new OpenDialoguePayload(worker.getId(), worker.getName().getString(), worker.role, worker.status,
                worker.working, worker.mode, pendingRole == null ? "" : pendingRole,
                worker.first != null && worker.second != null, worker.bed != null, worker.supply != null,
                worker.output != null, worker.craftingTable != null, worker.buildOrigin != null,
                worker.blueprint == null ? "" : worker.blueprint, worker.buildRotation, worker.digDepth,
                worker.collectFlags());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record CloseDialoguePayload() implements CustomPacketPayload {
        static final Type<CloseDialoguePayload> TYPE = new Type<>(id("close_dialogue"));
        static final StreamCodec<RegistryFriendlyByteBuf, CloseDialoguePayload> STREAM_CODEC =
            StreamCodec.unit(new CloseDialoguePayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record WorkerStatusPayload(int workerId, String name, String role, String status, boolean working, String mode,
                               boolean hasArea, boolean hasBed, boolean hasSupply, boolean hasOutput,
                               boolean hasTable, boolean hasOrigin, String blueprint, int rotation, int digDepth,
                               List<String> flags)
        implements CustomPacketPayload {
        static final Type<WorkerStatusPayload> TYPE = new Type<>(id("worker_status"));
        static final StreamCodec<RegistryFriendlyByteBuf, WorkerStatusPayload> STREAM_CODEC =
            StreamCodec.of(WorkerStatusPayload::encode, WorkerStatusPayload::decode);

        private static void encode(RegistryFriendlyByteBuf buf, WorkerStatusPayload p) {
            buf.writeVarInt(p.workerId);
            buf.writeUtf(p.name);
            buf.writeUtf(p.role);
            buf.writeUtf(p.status);
            buf.writeBoolean(p.working);
            buf.writeUtf(p.mode);
            buf.writeBoolean(p.hasArea);
            buf.writeBoolean(p.hasBed);
            buf.writeBoolean(p.hasSupply);
            buf.writeBoolean(p.hasOutput);
            buf.writeBoolean(p.hasTable);
            buf.writeBoolean(p.hasOrigin);
            buf.writeUtf(p.blueprint);
            buf.writeVarInt(p.rotation);
            buf.writeVarInt(p.digDepth);
            buf.writeVarInt(p.flags.size());
            for (String f : p.flags) buf.writeUtf(f);
        }

        private static WorkerStatusPayload decode(RegistryFriendlyByteBuf buf) {
            int workerId = buf.readVarInt();
            String name = buf.readUtf();
            String role = buf.readUtf();
            String status = buf.readUtf();
            boolean working = buf.readBoolean();
            String mode = buf.readUtf();
            boolean hasArea = buf.readBoolean();
            boolean hasBed = buf.readBoolean();
            boolean hasSupply = buf.readBoolean();
            boolean hasOutput = buf.readBoolean();
            boolean hasTable = buf.readBoolean();
            boolean hasOrigin = buf.readBoolean();
            String blueprint = buf.readUtf();
            int rotation = buf.readVarInt();
            int digDepth = buf.readVarInt();
            int flagCount = buf.readVarInt();
            List<String> flags = new ArrayList<>(flagCount);
            for (int i = 0; i < flagCount; i++) flags.add(buf.readUtf());
            return new WorkerStatusPayload(workerId, name, role, status, working, mode, hasArea, hasBed,
                hasSupply, hasOutput, hasTable, hasOrigin, blueprint, rotation, digDepth, flags);
        }

        static WorkerStatusPayload from(Worker worker) {
            return new WorkerStatusPayload(worker.getId(), worker.getName().getString(), worker.role, worker.status,
                worker.working, worker.mode, worker.first != null && worker.second != null, worker.bed != null,
                worker.supply != null, worker.output != null, worker.craftingTable != null, worker.buildOrigin != null,
                worker.blueprint == null ? "" : worker.blueprint, worker.buildRotation, worker.digDepth,
                worker.collectFlags());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record RecipeEntry(String id, String outputName, boolean approved, boolean supported, String reason) {}

    record RecipeListPayload(int workerId, List<String> ids, List<String> names, List<Boolean> approved,
                             List<Boolean> supported, List<String> reasons) implements CustomPacketPayload {
        static final Type<RecipeListPayload> TYPE = new Type<>(id("recipe_list"));
        static final StreamCodec<RegistryFriendlyByteBuf, RecipeListPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RecipeListPayload::workerId,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RecipeListPayload::ids,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RecipeListPayload::names,
            ByteBufCodecs.BOOL.apply(ByteBufCodecs.list()), RecipeListPayload::approved,
            ByteBufCodecs.BOOL.apply(ByteBufCodecs.list()), RecipeListPayload::supported,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RecipeListPayload::reasons,
            RecipeListPayload::new
        );

        static RecipeListPayload from(Worker worker) {
            List<RecipeEntry> entries = Crafting.listTeachable(worker);
            List<String> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            List<Boolean> approved = new ArrayList<>();
            List<Boolean> supported = new ArrayList<>();
            List<String> reasons = new ArrayList<>();
            for (RecipeEntry entry : entries) {
                ids.add(entry.id());
                names.add(entry.outputName());
                approved.add(entry.approved());
                supported.add(entry.supported());
                reasons.add(entry.reason());
            }
            return new RecipeListPayload(worker.getId(), ids, names, approved, supported, reasons);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record BlueprintListPayload(int workerId, List<String> names, String current) implements CustomPacketPayload {
        static final Type<BlueprintListPayload> TYPE = new Type<>(id("blueprint_list"));
        static final StreamCodec<RegistryFriendlyByteBuf, BlueprintListPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BlueprintListPayload::workerId,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BlueprintListPayload::names,
            ByteBufCodecs.STRING_UTF8, BlueprintListPayload::current,
            BlueprintListPayload::new
        );

        static BlueprintListPayload from(Worker worker) {
            return new BlueprintListPayload(worker.getId(), WorkerActions.blueprintNames(worker),
                worker.blueprint == null ? "" : worker.blueprint);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record ToastPayload(String message) implements CustomPacketPayload {
        static final Type<ToastPayload> TYPE = new Type<>(id("toast"));
        static final StreamCodec<RegistryFriendlyByteBuf, ToastPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ToastPayload::message, ToastPayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record DialogueActionPayload(int workerId, String action, String arg) implements CustomPacketPayload {
        static final Type<DialogueActionPayload> TYPE = new Type<>(id("dialogue_action"));
        static final StreamCodec<RegistryFriendlyByteBuf, DialogueActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DialogueActionPayload::workerId,
            ByteBufCodecs.STRING_UTF8, DialogueActionPayload::action,
            ByteBufCodecs.STRING_UTF8, DialogueActionPayload::arg,
            DialogueActionPayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record AreaOutlinePayload(int workerId, boolean hasArea, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                              long bed, long supply, long output, long table, int ticks) implements CustomPacketPayload {
        static final Type<AreaOutlinePayload> TYPE = new Type<>(id("area_outline"));
        static final StreamCodec<RegistryFriendlyByteBuf, AreaOutlinePayload> STREAM_CODEC =
            StreamCodec.of(AreaOutlinePayload::encode, AreaOutlinePayload::decode);

        private static void encode(RegistryFriendlyByteBuf buf, AreaOutlinePayload p) {
            buf.writeVarInt(p.workerId);
            buf.writeBoolean(p.hasArea);
            buf.writeInt(p.minX); buf.writeInt(p.minY); buf.writeInt(p.minZ);
            buf.writeInt(p.maxX); buf.writeInt(p.maxY); buf.writeInt(p.maxZ);
            buf.writeLong(p.bed); buf.writeLong(p.supply); buf.writeLong(p.output); buf.writeLong(p.table);
            buf.writeVarInt(p.ticks);
        }

        private static AreaOutlinePayload decode(RegistryFriendlyByteBuf buf) {
            return new AreaOutlinePayload(buf.readVarInt(), buf.readBoolean(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong(), buf.readVarInt());
        }

        static AreaOutlinePayload from(Worker worker, int ticks) {
            Worker.AreaBounds box = worker.areaBounds();
            return new AreaOutlinePayload(worker.getId(), box != null,
                box == null ? 0 : box.minX(), box == null ? 0 : box.minY(), box == null ? 0 : box.minZ(),
                box == null ? 0 : box.maxX(), box == null ? 0 : box.maxY(), box == null ? 0 : box.maxZ(),
                worker.bed == null ? Long.MIN_VALUE : worker.bed.asLong(),
                worker.supply == null ? Long.MIN_VALUE : worker.supply.asLong(),
                worker.output == null ? Long.MIN_VALUE : worker.output.asLong(),
                worker.craftingTable == null ? Long.MIN_VALUE : worker.craftingTable.asLong(),
                ticks);
        }

        static AreaOutlinePayload preview(int workerId, BlockPos first, BlockPos second, int digDepth, int ticks) {
            int minX = Math.min(first.getX(), second.getX());
            int maxX = Math.max(first.getX(), second.getX());
            int minZ = Math.min(first.getZ(), second.getZ());
            int maxZ = Math.max(first.getZ(), second.getZ());
            int top = Math.max(first.getY(), second.getY());
            int bottom = digDepth < 0 ? top - 64 : top - digDepth + 1;
            return new AreaOutlinePayload(workerId, true, minX, bottom, minZ, maxX, top, maxZ,
                Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, ticks);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record OpenDepthPickerPayload(int workerId, String name, int x1, int y1, int z1, int x2, int y2, int z2)
        implements CustomPacketPayload {
        static final Type<OpenDepthPickerPayload> TYPE = new Type<>(id("open_depth"));
        static final StreamCodec<RegistryFriendlyByteBuf, OpenDepthPickerPayload> STREAM_CODEC =
            StreamCodec.of(OpenDepthPickerPayload::encode, OpenDepthPickerPayload::decode);

        private static void encode(RegistryFriendlyByteBuf buf, OpenDepthPickerPayload p) {
            buf.writeVarInt(p.workerId);
            buf.writeUtf(p.name);
            buf.writeInt(p.x1); buf.writeInt(p.y1); buf.writeInt(p.z1);
            buf.writeInt(p.x2); buf.writeInt(p.y2); buf.writeInt(p.z2);
        }

        private static OpenDepthPickerPayload decode(RegistryFriendlyByteBuf buf) {
            return new OpenDepthPickerPayload(buf.readVarInt(), buf.readUtf(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID, path);
    }
}
