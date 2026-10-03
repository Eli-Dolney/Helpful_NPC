package com.example.helpfulworkers;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(HelpfulWorkers.ID)
@PrefixGameTestTemplate(false)
public final class Site05GameTests {
    @GameTest(template="site_test",skyAccess=true,timeoutTicks=1200)
    public static void assignWalkMineDepositRepeat(GameTestHelper h) {
        SiteGameTests.floor(h,20); var p=h.makeMockServerPlayerInLevel();
        var s=SiteGameTests.site(h,p,"iron",h.absolutePos(new BlockPos(3,1,3)));
        var w=SiteGameTests.worker(h,p,"miner",s.at(3,1,10));
        w.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        SiteActions.assign(p,w,s); s.oreDelay=100;
        h.assertTrue(w.working && w.first!=null && w.output.equals(s.output()),"Assignment starts work with automatic bounds/output");
        h.succeedWhen(()->h.assertTrue(StorageOps.at(h.getLevel(),s.output()).countItem(Items.RAW_IRON)>=3,"Miner must walk from outside the entrance, harvest repeatedly, and deposit: "+w.status+" pos="+w.position()+" ticks="+w.tickCount+" AI="+w.isNoAi()+" onGround="+w.onGround()+" path="+w.getNavigation().getPath()));
    }
    @GameTest(template="site_test",skyAccess=true)
    public static void randomCountdownPersistenceAndNoCatchup(GameTestHelper h) {
        Set<Integer> samples=new HashSet<>();
        for(int i=0;i<100;i++) {int n=SiteJobs.sampleDelay(h.getLevel());samples.add(n);h.assertTrue(n>=100&&n<=500,"Random delay between five and twenty-five seconds");}
        h.assertTrue(samples.size()>1,"Fresh delays vary");
        var p=h.makeMockServerPlayerInLevel();var s=SiteGameTests.site(h,p,"iron",h.absolutePos(new BlockPos(2,1,2)));
        s.oreDelay=340;s.production=120;s.lastActive=9999;
        var copy=SiteData.load(SiteData.get(h.getLevel()).save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).sites.get(s.id);
        h.assertTrue(copy.oreDelay==340&&copy.production==120&&copy.lastActive==-1,"Restart retains sampled countdown without wall-clock catchup");h.succeed();
    }
    @GameTest(template="site_test",skyAccess=true)
    public static void everyRoleSiteAssignsWithoutZone(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();int i=0;
        for(String type:SiteCatalog.EXTRA) {
            var s=SiteGameTests.site(h,p,type,h.absolutePos(new BlockPos(2,1,2)));
            for(String role:SiteCatalog.roles(type)) {
                var w=SiteGameTests.worker(h,p,role,s.entrance());SiteActions.assign(p,w,s);
                h.assertTrue(w.working && w.siteId.equals(s.id) && w.supply.equals(s.supply()),"Ready role site: "+role);
                if(type.equals("smelter"))h.assertTrue(w.furnaces.size()>0,"Furnaces assigned");
                SiteActions.release(h.getLevel(),w);w.discard();
            }
            SiteData.get(h.getLevel()).sites.remove(s.id);
            for(BlockPos q:BlockPos.betweenClosed(s.origin,s.origin.offset(14,12,14)))h.getLevel().setBlock(q,Blocks.AIR.defaultBlockState(),3);
        }
        h.succeed();
    }
    @GameTest(template="site_test",skyAccess=true)
    public static void vanillaTreeGrowthIsTrackedAndContained(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();var s=SiteGameTests.site(h,p,"logging",h.absolutePos(new BlockPos(1,1,1)));
        var w=SiteGameTests.worker(h,p,"forester",s.entrance());w.siteId=s.id;s.worker=w.getUUID();w.working=true;w.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
        NaturalTrees.ensure(h.getLevel(),s);
        for(var slot:s.treeSlots) {
            for(var q:NaturalTrees.planting(slot))h.getLevel().setBlock(q,NaturalTrees.sapling(slot.species).defaultBlockState(),3);
            for(int attempt=0;attempt<100 && slot.logs.isEmpty();attempt++) {
                var state=h.getLevel().getBlockState(slot.base);
                if(state.getBlock() instanceof SaplingBlock sapling)sapling.advanceTree(h.getLevel(),slot.base,state,h.getLevel().random);
            }
            h.assertTrue(!slot.logs.isEmpty(),"Vanilla growth succeeds: "+slot.species+" "+s.status);
            h.assertTrue(slot.logs.stream().allMatch(slot::contains),"Growth stays within reserved space");
            h.assertTrue(slot.logs.stream().noneMatch(q->h.getLevel().getBlockState(q).getBlock() instanceof SiteBlocks.ManagedBlock),"Uses vanilla logs");
        }
        var copy=SiteData.load(SiteData.get(h.getLevel()).save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).sites.get(s.id);
        h.assertTrue(copy.treeSlots.get(0).logs.equals(s.treeSlots.get(0).logs),"Tracked trees survive restart");h.succeed();
    }
    @GameTest(template="site_test",skyAccess=true)
    public static void customTemplateSnapshotAndRotation(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();var s=new SiteData.Site();s.owner=p.getUUID();s.type="iron";s.origin=h.absolutePos(new BlockPos(2,1,2));s.plotFirst=s.origin;s.plotSecond=s.origin.offset(8,0,10);
        s.template.putInt("Width",9);s.template.putInt("Depth",11);s.template.putInt("Height",6);
        var anchors=new CompoundTag();anchors.putLong("Core",new BlockPos(2,1,3).asLong());anchors.putLong("Supply",new BlockPos(3,1,3).asLong());anchors.putLong("Output",new BlockPos(4,1,3).asLong());s.template.put("Anchors",anchors);
        for(int r=0;r<4;r++){s.rotation=r;h.assertTrue(s.core().equals(s.at(2,1,3)),"Functional anchors rotate");}
        SiteData.get(h.getLevel()).add(s);
        var copy=SiteData.load(SiteData.get(h.getLevel()).save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).sites.get(s.id);
        h.assertTrue(copy.width()==9 && copy.depth()==11 && copy.core().equals(s.core()),"Placed design persisted independently of catalog");h.succeed();
    }
    @GameTest(template="site_test",skyAccess=true,timeoutTicks=1600)
    public static void courierWalksSuppliesAndConservesStock(GameTestHelper h) {
        SiteGameTests.floor(h,40);var p=h.makeMockServerPlayerInLevel();
        var s=SiteGameTests.site(h,p,"warehouse",h.absolutePos(new BlockPos(1,1,1)));WarehouseJobs.initialize(h.getLevel(),s);
        var miner=SiteGameTests.worker(h,p,"miner",s.origin.offset(28,1,23));
        miner.supply=s.origin.offset(27,1,23);miner.output=s.origin.offset(29,1,23);
        h.getLevel().setBlock(miner.supply,Blocks.CHEST.defaultBlockState(),3);h.getLevel().setBlock(miner.output,Blocks.CHEST.defaultBlockState(),3);WorkerRegistry.upsert(miner);
        var courier=SiteGameTests.worker(h,p,"courier",s.entrance());SiteActions.assign(p,courier,s);
        var chest=StorageOps.at(h.getLevel(),s.storage.get(0).pos);chest.setItem(0,new ItemStack(Items.IRON_PICKAXE));s.storage.get(0).filter="equipment";
        h.succeedWhen(()->{
            h.assertTrue(StorageOps.at(h.getLevel(),miner.supply).countItem(Items.IRON_PICKAXE)==1,"Courier must walk to warehouse stock and worker supply chest: "+courier.status+" pos="+courier.position()+" ticks="+courier.tickCount+" path="+courier.getNavigation().getPath());
            int total=courier.bag.countItem(Items.IRON_PICKAXE)+StorageOps.at(h.getLevel(),miner.supply).countItem(Items.IRON_PICKAXE);
            for(var st:s.storage)total+=StorageOps.at(h.getLevel(),st.pos).countItem(Items.IRON_PICKAXE);
            h.assertTrue(total==1,"Supply delivery conserves item");
        });
    }

    @GameTest(template="site_test",skyAccess=true)
    public static void snowyInstantConstructionAllNewSites(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();p.getAbilities().instabuild=false;var origin=h.absolutePos(new BlockPos(2,2,2));
        var b=SiteGameTests.worker(h,p,"builder",origin.offset(18,1,18));
        for(String type:SiteCatalog.EXTRA) {
            for(BlockPos q:BlockPos.betweenClosed(origin.below(),origin.offset(14,13,14))) {
                h.getLevel().setBlock(q,q.getY()<=origin.getY()?Blocks.DIRT.defaultBlockState():q.getY()==origin.getY()+1?Blocks.SNOW.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
            }
            var session=new SiteUi.Session();session.type=type;session.first=origin;session.second=origin.offset(14,0,14);session.floor=origin.getY();
            p.getInventory().add(new ItemStack(SiteBlocks.coreItem(type)));
            SiteActions.construct(p,b,session);var site=SiteData.get(h.getLevel()).sites.get(b.constructionId);site.instant=true;
            for(int n=0;n<500 && !site.active();n++)SiteConstruction.tick(h.getLevel(),b,site);
            h.assertTrue(site.active(),"Snowy construction completes: "+type+" "+site.status);
            h.assertTrue(StorageOps.at(h.getLevel(),site.output()).isEmpty(),"Storage constructed empty");
            h.assertTrue(p.getInventory().countItem(SiteBlocks.coreItem(type))==0,"One survival core consumed");
            SiteData.get(h.getLevel()).sites.remove(site.id);SiteData.get(h.getLevel()).cores.remove(site.core());
        }
        h.succeed();
    }
    @GameTest(template="site_test",skyAccess=true)
    public static void treeExpansionPathAndRegistryUnloadPersistence(GameTestHelper h) {
        SiteGameTests.floor(h,46);var p=h.makeMockServerPlayerInLevel();var s=SiteGameTests.site(h,p,"logging",h.absolutePos(new BlockPos(1,1,1)));
        NaturalTrees.ensure(h.getLevel(),s);var base=s.origin.offset(38,1,26);
        h.assertTrue(NaturalTrees.fits(h.getLevel(),s,base),"Adjacent extension fits on empty ground");
        var path=NaturalTrees.accessPath(h.getLevel(),s,base);
        h.assertTrue(!path.isEmpty() && path.get(path.size()-1).equals(s.entrance()),"Extension connects through accessible entrance");
        var slot=new SiteData.TreeSlot("oak",base);slot.building=true;slot.path.addAll(path);slot.cursor=2;s.treeSlots.add(slot);
        var copy=SiteData.load(SiteData.get(h.getLevel()).save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).sites.get(s.id);
        h.assertTrue(copy.treeSlots.size()==9 && copy.treeSlots.get(8).cursor==2 && copy.treeSlots.get(8).path.equals(path),"Extension resumes after restart");
        var w=SiteGameTests.worker(h,p,"forester",s.entrance());w.supply=s.supply();w.output=s.output();WorkerRegistry.upsert(w);
        var saved=WorkerRegistry.get(h.getLevel()).save(new CompoundTag(),h.getLevel().registryAccess());
        var entries=saved.getList("Workers",Tag.TAG_COMPOUND);
        CompoundTag entry=new CompoundTag();for(int i=0;i<entries.size();i++)if(entries.getCompound(i).getUUID("Id").equals(w.getUUID()))entry=entries.getCompound(i);
        h.assertTrue(entry.contains("Supply") && entry.contains("Output"),"Marker/storage positions saved");h.succeed();
    }
    @GameTest(template="site_test",skyAccess=true)
    public static void farmerRancherFisherAndSmelterUseSites(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();var origin=h.absolutePos(new BlockPos(2,1,2));
        var farm=SiteGameTests.site(h,p,"farm",origin);var farmer=SiteGameTests.worker(h,p,"farmer",farm.at(3,1,3));SiteActions.assign(p,farmer,farm);
        farmer.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_HOE));farmer.bag.addItem(new ItemStack(Items.WHEAT_SEEDS,8));
        var crop=farm.at(3,1,2);h.getLevel().setBlock(crop,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),3);farmer.target=crop;
        Jobs.tick(h.getLevel(),farmer);h.assertTrue(farmer.bag.countItem(Items.WHEAT)>0,"Farmer harvests a site crop");
        farmer.discard();
        var ranch=SiteGameTests.site(h,p,"ranch",origin);var rancher=SiteGameTests.worker(h,p,"rancher",ranch.at(3,1,3));SiteActions.assign(p,rancher,ranch);
        var egg=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),rancher.getX(),rancher.getY(),rancher.getZ(),new ItemStack(Items.EGG));h.getLevel().addFreshEntity(egg);
        Jobs.tick(h.getLevel(),rancher);h.assertTrue(rancher.bag.countItem(Items.EGG)==1,"Rancher collects player-stocked pen output");rancher.discard();
        var fish=SiteGameTests.site(h,p,"fishing",origin);var fisher=SiteGameTests.worker(h,p,"fisher",fish.at(3,1,1));SiteActions.assign(p,fisher,fish);fisher.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.FISHING_ROD));
        for(int i=0;i<10 && fisher.bag.isEmpty();i++)ExtraJobs.fisher(h.getLevel(),fisher);
        h.assertTrue(!fisher.bag.isEmpty(),"Fisher catches from built pool: "+fisher.status);fisher.discard();
        var smelt=SiteGameTests.site(h,p,"smelter",origin);var smelter=SiteGameTests.worker(h,p,"smelter",smelt.at(4,1,3));SiteActions.assign(p,smelter,smelt);
        StorageOps.at(h.getLevel(),smelt.supply()).setItem(0,new ItemStack(Items.RAW_IRON,8));
        Jobs.tick(h.getLevel(),smelter);h.assertTrue(StorageOps.at(h.getLevel(),smelter.furnaces.get(0)).countItem(Items.RAW_IRON)==8,"Smelter loads site furnace");h.succeed();
    }

    @GameTest(template="site_test",skyAccess=true)
    public static void regenerationDoesNotTrapSomeoneOnPlatform(GameTestHelper h) {
        SiteGameTests.floor(h,20);var p=h.makeMockServerPlayerInLevel();
        var site=SiteGameTests.site(h,p,"iron",h.absolutePos(new BlockPos(2,1,2)));
        var miner=SiteGameTests.worker(h,p,"miner",site.at(3,1,10));SiteActions.assign(p,miner,site);
        miner.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        var pos=site.ores().get(0);var visitor=SiteGameTests.worker(h,p,"idle",pos);
        site.oreDelay=100;site.production=100;SiteJobs.tick(h.getLevel(),miner);
        h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Occupied ore node remains air");
        h.assertTrue(h.getLevel().getBlockState(site.ores().get(1)).is(SiteBlocks.managed("ore_iron")),"An available node regenerates instead");h.succeed();
    }
}
