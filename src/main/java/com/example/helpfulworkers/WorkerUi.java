package com.example.helpfulworkers;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

/** Shared inventory-inspired chrome and vanilla item artwork. */
final class WorkerUi {
    static final int GOLD=0xffe1bc72, TEXT=0xffeee8da, MUTED=0xffb8b4aa;
    static void frame(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x-2,y-2,x+w+2,y+h+2,0xff191816);
        g.fill(x,y,x+w,y+h,0xffb8b2a3);
        g.fill(x+2,y+2,x+w-2,y+h-2,0xff373631);
        g.fill(x+4,y+4,x+w-4,y+h-4,0xff262722);
        g.fill(x,y,x+w-1,y+1,0xffeee9dc);
        g.fill(x,y,x+1,y+h-1,0xffeee9dc);
        g.fill(x+4,y+4,x+w-4,y+48,0xff343028);
        g.fill(x+8,y+48,x+w-8,y+49,0xff6a5b40);
    }
    static Item roleItem(String role) {
        return switch(role) {
            case "farmer"->Items.IRON_HOE;case "forester"->Items.IRON_AXE;case "miner"->Items.IRON_PICKAXE;
            case "builder"->Items.BRICKS;case "knight"->Items.IRON_SWORD;case "archer"->Items.BOW;
            case "rancher"->Items.WHEAT;case "fisher"->Items.FISHING_ROD;case "smelter"->Items.FURNACE;
            case "courier"->Items.CHEST_MINECART;default->Items.COMPASS;
        };
    }
    static Item icon(String label) {
        String s=label.toLowerCase(Locale.ROOT);
        if(s.contains("pause"))return Items.REDSTONE_TORCH;
        if(s.contains("resume") || s.contains("start work"))return Items.EMERALD;
        if(s.contains("construction") || s.contains("blueprint"))return Items.BRICKS;
        if(s.equals("back") || s.contains("return"))return Items.COMPASS;
        if(s.contains("profession"))return Items.WRITABLE_BOOK;
        if(s.contains("depth") || s.contains("target y"))return Items.LADDER;
        if(s.contains("guard") || s.contains("protect"))return Items.SHIELD;
        for(String role:new String[]{"farmer","forester","miner","builder","knight","archer","rancher","fisher","smelter","courier"})
            if(s.contains(role))return roleItem(role);
        if(s.contains("bed") || s.contains("home"))return Items.RED_BED;
        if(s.contains("supply") || s.contains("supplies") || s.contains("incoming"))return Items.HOPPER;
        if(s.contains("output") || s.contains("drop-off") || s.contains("overflow"))return Items.CHEST;
        if(s.contains("chest") || s.contains("storage") || s.contains("warehouse") || s.contains("container"))return Items.BARREL;
        if(s.contains("appearance") || s.contains("name"))return Items.NAME_TAG;
        if(s.contains("equipment") || s.contains("tool"))return Items.IRON_CHESTPLATE;
        if(s.contains("tree") || s.contains("forest") || s.contains("logging"))return Items.OAK_SAPLING;
        if(s.contains("crop") || s.contains("farm") || s.contains("wheat"))return Items.WHEAT;
        if(s.contains("ranch") || s.contains("herd") || s.contains("animal"))return Items.LEAD;
        if(s.contains("fish") || s.contains("pond"))return Items.FISHING_ROD;
        if(s.contains("mine") || s.contains("pit") || s.contains("dig"))return Items.IRON_PICKAXE;
        if(s.contains("build") || s.contains("house") || s.contains("site"))return Items.BRICKS;
        if(s.contains("guard") || s.contains("protect"))return Items.SHIELD;
        if(s.contains("area") || s.contains("plot") || s.contains("outline") || s.contains("preview"))return Items.MAP;
        if(s.contains("table") || s.contains("recipe"))return Items.CRAFTING_TABLE;
        if(s.contains("clear") || s.contains("cancel") || s.contains("release") || s.contains("dismantle"))return Items.BARRIER;
        return Items.BOOK;
    }
    static Button button(int x,int y,int width,int height,String label,Item icon,boolean selected,Button.OnPress action) {
        Button button=new Button(x,y,width,height,Component.literal(label),action,supplier->supplier.get()) {
            @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt) {
                boolean hover=isHoveredOrFocused();
                int fill=!active?0xff3b3b36:selected?0xff71603e:hover?0xff666458:0xff4b4b42;
                g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),0xff171813);
                g.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,fill);
                g.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+2,hover||selected?GOLD:0xff858477);
                g.fill(getX()+1,getY()+2,getX()+2,getY()+getHeight()-1,0xff858477);
                int inset=icon==null?6:26;
                if(icon!=null)g.renderItem(new ItemStack(icon),getX()+5,getY()+(getHeight()-16)/2);
                var font=Minecraft.getInstance().font;
                String caption=font.plainSubstrByWidth(label,Math.max(1,getWidth()-inset-6));
                g.drawString(font,caption,getX()+inset,getY()+(getHeight()-8)/2,active?TEXT:0xff88877d,false);
                if(isFocused())WorkerIcons.outline(g,getX(),getY(),getWidth(),getHeight(),GOLD);
            }
        };
        button.setTooltip(Tooltip.create(Component.literal(label)));
        return button;
    }
}
