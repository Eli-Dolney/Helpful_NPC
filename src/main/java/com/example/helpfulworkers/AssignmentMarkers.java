package com.example.helpfulworkers;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class AssignmentMarkers {
    record Marker(BlockPos pos,String name,int kind) {}
    record Snapshot(String dimension,List<Marker> markers) implements CustomPacketPayload {
        static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID,"assignment_markers"));
        static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->{
            b.writeUtf(p.dimension); b.writeVarInt(p.markers.size());
            for(var m:p.markers) { b.writeBlockPos(m.pos); b.writeUtf(m.name); b.writeVarInt(m.kind); }
        },b->{String dim=b.readUtf(); int n=b.readVarInt(); if(n<0||n>384)throw new IllegalArgumentException("Invalid markers");
            List<Marker> result=new ArrayList<>();for(int i=0;i<n;i++)result.add(new Marker(b.readBlockPos(),b.readUtf(1024),b.readVarInt())); return new Snapshot(dim,result);});
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    static void register(PayloadRegistrar r) { r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(p,c)->c.enqueueWork(()->AssignmentMarkersClient.accept(p))); }
    static void send(ServerPlayer p) {
        if(!net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(p.connection,Snapshot.TYPE.id()))return;
        var l=p.serverLevel();
        for(var w:l.getEntities(HelpfulWorkers.WORKER.get(),w->w.owns(p))) WorkerRegistry.upsert(w);
        Map<String,Marker> markers=new LinkedHashMap<>();
        for(var entry:WorkerRegistry.forOwner(l,p.getUUID())) {
            if(!entry.alive() || !entry.dimension().equals(l.dimension()))continue;
            BlockPos[] positions={entry.bed(),entry.supply(),entry.output()};
            for(int kind=0;kind<positions.length;kind++) {
                var pos=positions[kind]; if(pos==null || p.distanceToSqr(pos.getCenter())>1024 || !l.hasChunkAt(pos))continue;
                if(kind==0 && !(l.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BedBlock))continue;
                if(kind!=0 && StorageOps.at(l,pos)==null)continue;
                if(kind!=0) pos=WarehouseJobs.canonical(l,pos);
                String key=pos.asLong()+":"+kind;
                var old=markers.get(key); String names=old==null?entry.name():old.name+", "+entry.name();
                markers.put(key,new Marker(pos,names.length()>200?names.substring(0,197)+"…":names,kind));
            }
        }
        PacketDistributor.sendToPlayer(p,new Snapshot(l.dimension().location().toString(),List.copyOf(markers.values())));
    }
}
