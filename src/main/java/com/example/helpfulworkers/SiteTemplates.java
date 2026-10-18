package com.example.helpfulworkers;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Player-owned structural templates contain states and local anchors, never inventories or identities. */
final class SiteTemplates {
    static CompoundTag selected(ServerPlayer p,String type) {
        var s=SiteUi.SESSIONS.get(p.getUUID());
        return s!=null && s.customTemplate ? SiteData.get(p.serverLevel()).templates.getOrDefault(p.getUUID()+":"+type,new CompoundTag()):new CompoundTag();
    }
    static List<SiteCatalog.Piece> pieces(SiteData.Site s) {
        if(s.template.isEmpty())return SiteCatalog.pieces(s.type);
        if(s.templatePieces!=null)return s.templatePieces;
        List<SiteCatalog.Piece> out=new ArrayList<>();
        var list=s.template.getList("Blocks",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++) {
            var b=list.getCompound(i); var p=BlockPos.of(b.getLong("Pos"));
            out.add(new SiteCatalog.Piece(p.getX(),p.getY(),p.getZ(),NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),b.getCompound("State"))));
        }
        s.templatePieces=List.copyOf(out);return s.templatePieces;
    }
    static List<String> anchors(String type) {
        List<String> names=new ArrayList<>(List.of("Core","Entrance","Supply","Output"));
        if(Arrays.asList(SiteCatalog.ORES).contains(type))names.addAll(List.of("Ore1","Ore2","Ore3"));
        else if(type.equals("logging")) for(int i=1;i<=8;i++)names.add("Tree"+i);
        else names.add("Work");
        return names;
    }
    static void menu(ServerPlayer p,SiteUi.Session s) {
        SiteUi.begin(s);
        List<String> types=new ArrayList<>(Arrays.asList(SiteCatalog.ORES)); types.add("logging");types.add("warehouse");types.addAll(Arrays.asList(SiteCatalog.EXTRA));
        for(String type:types)SiteUi.option(s,SiteCatalog.label(type),()-> {
            s.type=type; s.first=null;s.second=null;s.capture=null;s.anchorCursor=0;
            SiteUi.startSelection(p,s,"capture corners (include the foundation and roof)");
        });
        SiteUi.option(s,"Back",()->SiteUi.catalog(p,s));
        SiteUi.send(p,s,"Save as worker-site template","One personal design per site type. Existing sites keep their original design.");
    }
    static void click(ServerPlayer p,SiteUi.Session s,BlockPos pos) {
        if(s.pending.startsWith("capture corners")) {
            if(s.first==null) {s.first=pos;SiteUi.tell(p,"First corner set; select opposite upper corner.");return;}
            BlockPos a=new BlockPos(Math.min(s.first.getX(),pos.getX()),Math.min(s.first.getY(),pos.getY()),Math.min(s.first.getZ(),pos.getZ()));
            BlockPos b=new BlockPos(Math.max(s.first.getX(),pos.getX()),Math.max(s.first.getY(),pos.getY()),Math.max(s.first.getZ(),pos.getZ()));
            int w=b.getX()-a.getX()+1,h=b.getY()-a.getY()+1,d=b.getZ()-a.getZ()+1;
            if(w<3||d<3||h<2||w>64||d>64||h>32||(long)w*h*d>32768)throw new IllegalArgumentException("Template limit: 64 wide/deep, 32 high, 32768 blocks");
            s.first=a;s.second=b;s.capture=new CompoundTag();s.capture.putInt("Width",w);s.capture.putInt("Height",h);s.capture.putInt("Depth",d);
            s.capture.put("Blocks",new ListTag());s.capture.put("Anchors",new CompoundTag());s.captureCursor=0;s.pending="capture_scan";
            SiteUi.tell(p,"Reading structure; only block states are copied.");return;
        }
        if(!s.pending.equals("capture_anchor"))return;
        String key=anchors(s.type).get(s.anchorCursor);
        if(key.equals("Entrance") || key.equals("Work") || key.startsWith("Tree") || key.startsWith("Ore"))pos=pos.above();
        if(pos.getX()<s.first.getX()||pos.getX()>s.second.getX()||pos.getY()<s.first.getY()||pos.getY()>s.second.getY()||pos.getZ()<s.first.getZ()||pos.getZ()>s.second.getZ())throw new IllegalArgumentException("Anchor must be inside captured structure");
        var l=p.serverLevel();
        if((key.equals("Supply")||key.equals("Output")) && StorageOps.at(l,pos)==null)throw new IllegalArgumentException("Select a chest or barrel");
        var a=s.capture.getCompound("Anchors"); long local=pos.subtract(s.first).asLong();
        if(a.getAllKeys().stream().anyMatch(k->a.getLong(k)==local))throw new IllegalArgumentException("Use a separate position for each anchor");
        a.putLong(key,local); s.anchorCursor++;
        if(s.anchorCursor<anchors(s.type).size()) { prompt(p,s);return; }
        s.pending=""; validate(s.capture,s.type);
        SiteUi.begin(s);
        SiteUi.option(s,"Save this design",()-> {
            SiteData.get(l).templates.put(p.getUUID()+":"+s.type,s.capture.copy());SiteData.get(l).setDirty(); s.capture=null;SiteUi.workerMenu(p,s);
        });
        SiteUi.option(s,"Cancel",()->{s.capture=null;SiteUi.workerMenu(p,s);});
        SiteUi.send(p,s,"Save custom "+SiteCatalog.label(s.type),"Validated anchors. Storage copies empty; each construction uses a fresh core and site identity.");
    }
    static void prompt(ServerPlayer p,SiteUi.Session s) {
        String key=anchors(s.type).get(s.anchorCursor);
        SiteUi.tell(p,"Mark "+key+((key.equals("Entrance")||key.equals("Work")||key.startsWith("Tree")||key.startsWith("Ore"))?": click the floor beneath it.":": click its block."));
    }
    static void tick(ServerPlayer p) {
        var s=SiteUi.SESSIONS.get(p.getUUID()); if(s==null || !s.pending.equals("capture_scan"))return;
        int w=s.capture.getInt("Width"),d=s.capture.getInt("Depth"),h=s.capture.getInt("Height"),budget=WorkerConfig.scanBudget();
        var blocks=s.capture.getList("Blocks",Tag.TAG_COMPOUND);
        try {
            while(s.captureCursor<w*d*h && budget-->0) {
                int index=s.captureCursor; var local=new BlockPos(index%w,index/(w*d),(index/w)%d);var pos=s.first.offset(local);
                if(!p.serverLevel().hasChunkAt(pos))throw new IllegalArgumentException("Load the full captured structure");
                var state=p.serverLevel().getBlockState(pos);
                if(state.is(SiteBlocks.CORE.get()) || state.getBlock() instanceof SiteBlocks.ManagedBlock)state=Blocks.AIR.defaultBlockState();
                if(!state.isAir()) {
                    if(state.hasBlockEntity() && !(p.serverLevel().getBlockEntity(pos) instanceof net.minecraft.world.Container))throw new IllegalArgumentException("Unsupported block entity at "+pos.toShortString());
                    if(!state.getFluidState().isEmpty() && !state.is(Blocks.WATER))throw new IllegalArgumentException("Only water is supported in templates");
                    var b=new CompoundTag();b.putLong("Pos",local.asLong());b.put("State",NbtUtils.writeBlockState(state));blocks.add(b);
                }
                s.captureCursor++;
            }
            if(s.captureCursor>=w*d*h) { s.pending="capture_anchor";prompt(p,s); }
        } catch(IllegalArgumentException ex) {s.pending="";s.capture=null;SiteUi.tell(p,ex.getMessage());SiteUi.workerMenu(p,s);}
    }
    static void validate(CompoundTag template,String type) {
        var blocks=template.getList("Blocks",Tag.TAG_COMPOUND);var a=template.getCompound("Anchors");
        Map<BlockPos,BlockState> states=new LinkedHashMap<>();
        for(int i=0;i<blocks.size();i++) {var b=blocks.getCompound(i);states.put(BlockPos.of(b.getLong("Pos")),NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),b.getCompound("State")));}
        states.put(BlockPos.of(a.getLong("Core")),SiteBlocks.CORE.get().defaultBlockState());
        if(type.equals("logging"))for(int i=1;i<=8;i++) {
            var p=BlockPos.of(a.getLong("Tree"+i));
            if(p.getX()<4||p.getZ()<4||p.getX()+4>=template.getInt("Width")||p.getZ()+4>=template.getInt("Depth"))throw new IllegalArgumentException("Each tree needs a nine-block-wide growing space");
            if(p.getY()+18>=template.getInt("Height"))throw new IllegalArgumentException("Tree slots need nineteen blocks of growing height");
            for(int k=1;k<i;k++) {var q=BlockPos.of(a.getLong("Tree"+k));if(Math.abs(p.getX()-q.getX())<10 && Math.abs(p.getZ()-q.getZ())<10)throw new IllegalArgumentException("Separate tree centers by ten blocks");}
            states.put(p,SiteBlocks.managed("marker_"+SiteCatalog.TREES[i-1]).defaultBlockState());
        }
        if(type.equals("smelter") && states.values().stream().noneMatch(b->b.is(Blocks.FURNACE)))throw new IllegalArgumentException("Add at least one furnace");
        if(type.equals("farm") && states.values().stream().noneMatch(b->b.is(Blocks.FARMLAND)))throw new IllegalArgumentException("Add farmland");
        if(type.equals("fishing") && states.values().stream().noneMatch(b->b.is(Blocks.WATER)))throw new IllegalArgumentException("Add a fishing pool");
        if(type.equals("warehouse") && states.values().stream().filter(b->b.is(Blocks.CHEST)||b.is(Blocks.BARREL)).count()>SiteSettings.storageLimit()*2L)throw new IllegalArgumentException("Too many storage blocks");
        var sorted=states.entrySet().stream().sorted(Comparator.<Map.Entry<BlockPos,BlockState>>comparingInt(e->e.getKey().getY()).thenComparing(e->e.getKey())).toList();
        ListTag result=new ListTag();
        for(var entry:sorted) {var b=new CompoundTag();b.putLong("Pos",entry.getKey().asLong());b.put("State",NbtUtils.writeBlockState(entry.getValue()));result.add(b);}
        template.put("Blocks",result);
    }
}
