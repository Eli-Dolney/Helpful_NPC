package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(HelpfulWorkers.ID)
@PrefixGameTestTemplate(false)
public final class ConstructionGameTests {
    @GameTest(template="site_test", skyAccess=true)
    public static void loggingCampStartsMatureAndStocksOnlyOnceAcrossRestart(GameTestHelper h) {
        var player=h.makeMockServerPlayerInLevel();
        var site=SiteGameTests.site(h,player,"logging",h.absolutePos(new BlockPos(1,1,1)));
        var builder=SiteGameTests.worker(h,player,"builder",site.origin.offset(35,1,35));
        site.phase="verify"; site.cursor=0;site.builder=builder.getUUID();builder.constructionId=site.id;builder.working=true;
        for(int i=0;i<1000 && !site.starterStock;i++) SiteConstruction.tick(h.getLevel(),builder,site);
        h.assertTrue(site.starterStock,"Finishing construction grants starter stock");
        var saved=SiteData.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());
        var resumed=SiteData.load(saved,h.getLevel().registryAccess()).sites.get(site.id);
        h.assertTrue(resumed.starterStock && resumed.starterTreeCursor==site.starterTreeCursor,"Starter progress persists");
        SiteData.get(h.getLevel()).sites.put(resumed.id,resumed);
        for(int i=0;i<1000 && !resumed.active();i++)SiteConstruction.tick(h.getLevel(),builder,resumed);
        h.assertTrue(resumed.active() && resumed.worker==null,"Camp is ready before a forester is assigned: "+resumed.status);
        h.assertTrue(resumed.treeSlots.size()==8 && resumed.treeSlots.stream().allMatch(slot->!slot.logs.isEmpty()
                && slot.logs.stream().allMatch(pos->slot.contains(pos) && (h.getLevel().getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)
                        || h.getLevel().getBlockState(pos).is(Blocks.MANGROVE_ROOTS)
                        || h.getLevel().getBlockState(pos).is(Blocks.MUDDY_MANGROVE_ROOTS)))),"All eight slots contain mature tracked vanilla trees");
        var supply=StorageOps.at(h.getLevel(),resumed.supply());
        for(String species:SiteCatalog.TREES)h.assertTrue(supply.countItem(NaturalTrees.sapling(species).asItem())==16,"Exactly 16 starter saplings for "+species);
        h.assertTrue(StorageOps.at(h.getLevel(),resumed.output()).isEmpty(),"Output stays empty");
        supply.removeItem(0,16);
        resumed.phase="plant";NaturalTrees.starter(h.getLevel(),resumed);
        h.assertTrue(supply.getItem(0).isEmpty(),"Re-entering finish cannot restock or regenerate harvested starters");
        h.succeed();
    }

    @GameTest(template="site_test", skyAccess=true)
    public static void loggingStarterStockWaitsForCapacityWithoutOverwriting(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();var s=SiteGameTests.site(h,p,"logging",h.absolutePos(new BlockPos(1,1,1)));
        s.phase="plant";var chest=StorageOps.at(h.getLevel(),s.supply());
        for(int i=0;i<chest.getContainerSize();i++)chest.setItem(i,new ItemStack(Items.DIAMOND,64));
        h.assertTrue(!NaturalTrees.starter(h.getLevel(),s) && !s.starterStock,"Full supply chest waits");
        h.assertTrue(chest.countItem(Items.DIAMOND)==64*chest.getContainerSize(),"Existing stock conserved");
        for(int i=0;i<8;i++)chest.setItem(i,ItemStack.EMPTY);
        NaturalTrees.starter(h.getLevel(),s);
        h.assertTrue(s.starterStock && chest.countItem(Items.DIAMOND)==64*(chest.getContainerSize()-8),"Stock added without overwriting remaining items");h.succeed();
    }

    @GameTest(template="site_test", skyAccess=true)
    public static void farmVerificationAcceptsMoistureAndConnectedFences(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var site = SiteGameTests.site(h, player, "farm", h.absolutePos(new BlockPos(2,2,2)));
        var builder = SiteGameTests.worker(h, player, "builder", site.origin.offset(18,1,18));
        site.builder = builder.getUUID(); site.phase = "verify"; site.cursor = 0;
        builder.constructionId = site.id; builder.working = true;
        for (var piece : SiteTemplates.pieces(site)) {
            var pos = site.at(piece.x(),piece.y(),piece.z());
            var state = h.getLevel().getBlockState(pos);
            if (state.is(Blocks.FARMLAND)) h.getLevel().setBlock(pos,state.setValue(FarmBlock.MOISTURE,0),3);
            // Reproduce old construction's disconnected posts as well as changed moisture.
            if (state.getBlock() instanceof FenceBlock)
                h.getLevel().setBlock(pos,Blocks.OAK_FENCE.defaultBlockState(),Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (int i=0;i<100 && !site.active();i++) SiteConstruction.tick(h.getLevel(),builder,site);
        h.assertTrue(site.active(),"Normal moisture/connection changes must not block completion: "+site.status);
        var fence = h.getLevel().getBlockState(site.at(0,1,3));
        h.assertTrue(fence.getValue(FenceBlock.NORTH) && fence.getValue(FenceBlock.SOUTH),"Old disconnected posts are repaired");
        h.assertTrue(!SiteConstruction.matches(Blocks.DIRT.defaultBlockState(),Blocks.FARMLAND.defaultBlockState()),"Different blocks still require repair");
        h.succeed();
    }

    @GameTest(template="site_test", skyAccess=true, timeoutTicks=1600)
    public static void builderWaitsFetchesToolAndWalksBackToClear(GameTestHelper h) {
        SiteGameTests.floor(h,32);
        var player = h.makeMockServerPlayerInLevel();
        var origin = h.absolutePos(new BlockPos(3,1,3));
        var builder = SiteGameTests.worker(h,player,"builder",origin.offset(9,1,0));
        builder.supply = origin.offset(18,1,0);
        h.getLevel().setBlock(builder.supply,Blocks.CHEST.defaultBlockState(),3);
        var stone = origin.above(); h.getLevel().setBlock(stone,Blocks.STONE.defaultBlockState(),3);
        var session = new SiteUi.Session(); session.type="iron"; session.first=origin; session.second=origin.offset(6,0,6); session.floor=origin.getY();
        SiteActions.construct(player,builder,session);
        var site = SiteData.get(h.getLevel()).sites.get(builder.constructionId);
        builder.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
        SiteConstruction.tick(h.getLevel(),builder,site);
        h.assertTrue(builder.working && builder.status.contains("pickaxe"),"Missing tool waits without pausing");
        h.assertTrue(h.getLevel().getBlockState(stone).is(Blocks.STONE),"Cannot clear with wrong tool");
        h.runAfterDelay(40,()->StorageOps.at(h.getLevel(),builder.supply).setItem(0,new ItemStack(Items.IRON_PICKAXE)));
        h.succeedWhen(()->{
            h.assertTrue(!h.getLevel().getBlockState(stone).is(Blocks.STONE),"Builder must fetch then walk back: "+builder.status+" "+builder.position());
            h.assertTrue(builder.getMainHandItem().is(Items.IRON_PICKAXE) && builder.getMainHandItem().getDamageValue()==1,"Correct tool loses durability");
            h.assertTrue(builder.bag.countItem(Items.IRON_AXE)==1 && StorageOps.at(h.getLevel(),builder.supply).isEmpty(),"Tool swap conserves both tools");
        });
    }

    @GameTest(template="site_test", skyAccess=true)
    public static void clearingSwapsToolsInFullBagAndProtectsContainers(GameTestHelper h) {
        SiteGameTests.floor(h,15); var p=h.makeMockServerPlayerInLevel();
        var origin=h.absolutePos(new BlockPos(3,1,3));
        var b=SiteGameTests.worker(h,p,"builder",origin.above());
        var s=new SiteData.Site();s.owner=p.getUUID();s.builder=b.getUUID();s.origin=origin;s.type="iron";
        b.constructionId=s.id; b.working=true;
        for(int i=0;i<27;i++)b.bag.setItem(i,new ItemStack(Items.DIRT,64));
        b.bag.setItem(0,new ItemStack(Items.IRON_SHOVEL));b.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        var target=origin.offset(1,1,0);h.getLevel().setBlock(target,Blocks.DIRT.defaultBlockState(),3);
        h.assertTrue(ConstructionTools.clear(h.getLevel(),b,s,target),"Dirt cleared using bag shovel");
        h.assertTrue(b.bag.countItem(Items.IRON_PICKAXE)==1 && b.getMainHandItem().is(Items.IRON_SHOVEL),"Full bag swaps without loss");
        b.bag.setItem(1,new ItemStack(Items.IRON_AXE));
        h.getLevel().setBlock(target,Blocks.OAK_LOG.defaultBlockState(),3);
        h.assertTrue(ConstructionTools.clear(h.getLevel(),b,s,target) && b.getMainHandItem().is(Items.IRON_AXE)
                && b.getMainHandItem().getDamageValue()==1,"Logs use an axe and consume durability");
        h.getLevel().setBlock(target,Blocks.CHEST.defaultBlockState(),3);
        boolean protectedBlock=false;try {ConstructionTools.clear(h.getLevel(),b,s,target);}catch(IllegalArgumentException expected){protectedBlock=true;}
        h.assertTrue(protectedBlock && h.getLevel().getBlockState(target).is(Blocks.CHEST),"Containers remain protected");h.succeed();
    }
}
