package com.example.helpfulworkers;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WorkerInventoryScreen extends AbstractContainerScreen<WorkerInventoryMenu> {
    public WorkerInventoryScreen(WorkerInventoryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 236;
        inventoryLabelY = 143;
        titleLabelY = 6;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF000000);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        for (Slot slot : menu.slots) {
            drawSlotFrame(graphics, x + slot.x - 1, y + slot.y - 1);
        }
    }

    /** Vanilla-style recessed slot: black grid, dark inner edge, light outer edge. */
    private static void drawSlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + 2, y + 17, 0xFF373737);
        graphics.fill(x + 1, y + 16, x + 17, y + 17, 0xFFFFFFFF);
        graphics.fill(x + 16, y + 1, x + 17, y + 17, 0xFFFFFFFF);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
