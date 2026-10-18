package com.example.helpfulworkers;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authored actions retain their original indices across display pages. */
final class SiteScreen extends Screen {
    private SiteNetwork.Menu menu;
    private int page;
    SiteScreen(SiteNetwork.Menu menu) { super(Component.literal(menu.title())); this.menu=menu; }
    void update(SiteNetwork.Menu next) {
        if(!menu.title().equals(next.title()))page=0;
        menu=next; rebuildWidgets();
    }
    private int panelWidth(){return Math.min(360,width-16);}
    private int panelHeight(){return Math.min(340,height-16);}
    private int left(){return (width-panelWidth())/2;}
    private int top(){return (height-panelHeight())/2;}
    private int rows(){return Math.max(1,(panelHeight()-116)/24);}
    private int pages(){return Math.max(1,(menu.choices().size()+rows()-1)/rows());}
    @Override protected void init() {
        page=Math.max(0,Math.min(page,pages()-1));
        int x=left(),y=top(),w=panelWidth();
        for(int row=0;row<rows() && page*rows()+row<menu.choices().size();row++) {
            final int index=page*rows()+row;
            String label=menu.choices().get(index);
            addRenderableWidget(WorkerUi.button(x+12,y+84+row*24,w-24,20,label,WorkerUi.icon(label),false,
                b->PacketDistributor.sendToServer(new SiteNetwork.Action(menu.revision(),index))));
        }
        int footer=y+panelHeight()-26;
        var previous=WorkerUi.button(x+12,footer,78,20,"< Previous",null,false,b->{page--;rebuildWidgets();});
        previous.active=page>0;addRenderableWidget(previous);
        var next=WorkerUi.button(x+w-90,footer,78,20,"Next >",null,false,b->{page++;rebuildWidgets();});
        next.active=page<pages()-1;addRenderableWidget(next);
        addRenderableWidget(WorkerUi.button(x+w-29,y+10,18,18,"×",null,false,b->onClose()));
        addRenderableWidget(WorkerUi.button(x+12,y+29,130,16,"Markers: "+AssignmentMarkersClient.label(),null,false,b->{AssignmentMarkersClient.cycle();rebuildWidgets();}));
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy) {
        int next=Math.max(0,Math.min(pages()-1,page-(int)Math.signum(sy)));
        if(next!=page){page=next;rebuildWidgets();return true;}return super.mouseScrolled(x,y,sx,sy);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt) {
        renderBackground(g,mx,my,dt);
        int x=left(),y=top(),w=panelWidth();
        WorkerUi.frame(g,x,y,w,panelHeight());
        g.drawString(font,font.plainSubstrByWidth(menu.title(),w-52),x+12,y+13,WorkerUi.GOLD,false);
        var lines=font.split(Component.literal(menu.text()),w-24);
        for(int i=0;i<Math.min(3,lines.size());i++)g.drawString(font,lines.get(i),x+12,y+53+i*9,WorkerUi.MUTED,false);
        for (var widget : renderables) widget.render(g,mx,my,dt);
        g.drawCenteredString(font,"Page "+(page+1)+" / "+pages(),width/2,y+panelHeight()-20,WorkerUi.MUTED);
        if(mx>=x+12 && mx<x+w-12 && my>=y+51 && my<y+82 && lines.size()>3)
            g.renderTooltip(font,lines,mx,my);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){PacketDistributor.sendToServer(new SiteNetwork.Action(menu.revision(),-1));super.onClose();}
}
