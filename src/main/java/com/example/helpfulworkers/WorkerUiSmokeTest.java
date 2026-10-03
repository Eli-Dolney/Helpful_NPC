package com.example.helpfulworkers;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in client layout checks. runUiTest uses a separate game directory and no player world. */
@EventBusSubscriber(modid=HelpfulWorkers.ID,value=Dist.CLIENT)
final class WorkerUiSmokeTest {
    private record Case(String role,String page,int scale) {}
    private static final List<Case> CASES=new ArrayList<>();
    private static int index=-1,wait;
    static {
        for(int scale:new int[]{1,2,3,4}) {
            for(String role:new String[]{"miner","forester","farmer","builder","rancher","fisher","smelter","courier","knight","archer"})
                CASES.add(new Case(role,"WORK",scale));
            for(String page:new String[]{"MAIN","LOCATIONS","MANAGE","ROLE","CROPS","RANCH","BUILD","BLUEPRINT","SITE","SITE_ASSIGNED","RECIPES","DEPTH","PROTECT","TARGET_Y","BIOME","ROLE_CONFIRM","CAPTURE_CONFIRM","CULL_CONFIRM","MESSAGE"})
                CASES.add(new Case(page.equals("CROPS")?"farmer":page.equals("RANCH")?"rancher":"builder",page,scale));
        }
    }
    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("helpfulworkers.uiTest"))return;
        var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null || mc.screen==null)return;
        if(wait++<8)return;
        wait=0;
        try {
            if(index>=0) {
                checkPages(mc);
                var c=CASES.get(index);
                Screenshot.grab(mc.gameDirectory,"ui-"+c.scale+"-"+c.role+"-"+c.page+".png",mc.getMainRenderTarget(),ignored->{});
            }
            if(++index>=CASES.size()) {
                System.out.println("WORKER_UI_TEST_PASS: "+CASES.size()+" screens across GUI scales 1, 2, 3, 4");
                mc.stop();return;
            }
            var c=CASES.get(index);mc.options.guiScale().set(c.scale);mc.resizeDisplay();
            if(c.page.equals("SITE")) {
                mc.setScreen(new SiteScreen(new SiteNetwork.Menu(1,"Build a worker site","Choose a building, mark its plot, then review the placement before construction begins.",
                    java.util.stream.Stream.concat(Arrays.stream(SiteCatalog.EXTRA),Arrays.stream(SiteCatalog.ORES)).map(type->"Build "+SiteCatalog.pretty(type)).toList())));
            } else {
                var screen=new WorkerDialogueScreen(new WorkerNetwork.OpenDialoguePayload(0,"Alex • "+c.role,c.role,"Ready for work — supplies are stocked",true,"terraform","farmer",true,true,true,true,true,true,"",0,10,c.page.equals("SITE_ASSIGNED")?List.of("site"):List.of()));
                var field=WorkerDialogueScreen.class.getDeclaredField("page");field.setAccessible(true);
                for(Object value:field.getType().getEnumConstants())if(value.toString().equals(c.page.equals("SITE_ASSIGNED")?"WORK":c.page))field.set(screen,value);
                mc.setScreen(screen);
                if(c.page.equals("RECIPES")) {
                    var names=java.util.stream.IntStream.range(0,25).mapToObj(i->"Test recipe "+i).toList();
                    screen.applyRecipes(new WorkerNetwork.RecipeListPayload(0,names,names,Collections.nCopies(25,false),Collections.nCopies(25,true),Collections.nCopies(25,"")));
                    var search=(net.minecraft.client.gui.components.EditBox)screen.children().stream().filter(e->e instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();
                    screen.setFocused(search);search.setValue("Test recipe");
                    if(screen.getFocused()!=search)throw new IllegalStateException("Search lost keyboard focus while typing");
                }
                if(c.page.equals("BLUEPRINT"))screen.applyBlueprints(new WorkerNetwork.BlueprintListPayload(0,java.util.stream.IntStream.range(0,20).mapToObj(i->"Saved house "+i).toList(),""));
            }
        } catch(Exception error) {throw new IllegalStateException("UI smoke check failed at "+index,error);}
    }
    private static void checkPages(Minecraft mc) {
        int visited=0;
        var labels=new HashSet<String>();
        while(true) {
            check(mc);visited++;
            for(var element:mc.screen.children())if(element instanceof AbstractWidget widget)labels.add(widget.getMessage().getString());
            var next=mc.screen.children().stream().filter(e->e instanceof Button b && b.active && b.getMessage().getString().equals("Next >")).map(e->(Button)e).findFirst();
            if(next.isEmpty())break;
            if(visited>200)throw new IllegalStateException("Pagination did not reach the end");
            next.get().onPress();
        }
        if(CASES.get(index).page.equals("RECIPES") && labels.stream().noneMatch(s->s.contains("Test recipe 24")))throw new IllegalStateException("Last recipe is unreachable");
        if(CASES.get(index).page.equals("BLUEPRINT") && labels.stream().noneMatch(s->s.contains("Saved house 19")))throw new IllegalStateException("Last blueprint is unreachable");
        while(true) {
            var previous=mc.screen.children().stream().filter(e->e instanceof Button b && b.active && b.getMessage().getString().equals("< Previous")).map(e->(Button)e).findFirst();
            if(previous.isEmpty())break;previous.get().onPress();
        }
        mc.screen.keyPressed(258,0,0);
        if(mc.screen.getFocused()==null)throw new IllegalStateException("Keyboard cannot focus menu controls");
    }
    private static void check(Minecraft mc) {
        var screen=mc.screen;
        var widgets=screen.children().stream().filter(e->e instanceof AbstractWidget).map(e->(AbstractWidget)e).filter(w->w.visible).toList();
        for(var w:widgets) {
            if(w.getX()<0 || w.getY()<0 || w.getX()+w.getWidth()>screen.width || w.getY()+w.getHeight()>screen.height)
                throw new IllegalStateException("Off-screen control: "+w.getMessage().getString());
            for(var other:widgets)if(w!=other && w.getX()<other.getX()+other.getWidth() && w.getX()+w.getWidth()>other.getX()
                    && w.getY()<other.getY()+other.getHeight() && w.getY()+w.getHeight()>other.getY())
                throw new IllegalStateException("Overlapping controls: "+w.getMessage().getString()+" / "+other.getMessage().getString());
            if(w instanceof Button && !CASES.get(index).role.equals("miner") && w.getMessage().getString().toLowerCase(Locale.ROOT).contains("dig site"))
                throw new IllegalStateException("Mining label leaked into another role");
        }
    }
}
