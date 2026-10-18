package com.example.helpfulworkers;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Worker equipment and bag: vanilla-style panel, live model preview, a status card and slot hints. */
@OnlyIn(Dist.CLIENT)
public final class WorkerInventoryScreen extends AbstractContainerScreen<WorkerInventoryMenu> {
    private static final ResourceLocation BACKGROUND =
        ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID, "textures/gui/worker_inventory.png");
    private static final String[] EQUIP_HINTS = {
        Lang.INV_HEAD, Lang.INV_CHEST, Lang.INV_LEGS, Lang.INV_FEET, Lang.INV_MAINHAND, Lang.INV_OFFHAND};

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
        graphics.blit(BACKGROUND, x, y, 0, 0, imageWidth, imageHeight, 176, 236);
        Worker worker = menu.worker();
        if (worker != null && worker.level() != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + 27, y + 19, x + 76, y + 87, 28,
                0.0625f, mouseX, mouseY, worker);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Worker worker = menu.worker();
        if (worker == null) return;
        String role = worker.getEntityData().get(Worker.DATA_ROLE);
        int cardX = 103;
        int cy = 20;
        graphics.drawString(font, Component.translatable(Lang.roleKey(role)), cardX, cy, 0xFF404040, false);
        cy += 10;
        String trait = worker.getEntityData().get(Worker.DATA_TRAIT);
        if (trait != null && !trait.isEmpty()) {
            graphics.drawString(font, Component.translatable(Lang.traitKey(trait)), cardX, cy, 0xFF6D4C41, false);
            cy += 10;
        }
        int level = worker.getEntityData().get(Worker.DATA_LEVEL);
        int xp = worker.getEntityData().get(Worker.DATA_XP);
        Component lv = Component.translatable(Lang.LEVEL_SHORT, level);
        graphics.drawString(font, lv, cardX, cy, 0xFF1B5E20, false);
        int barX = cardX + font.width(lv) + 3;
        int barW = 166 - barX;
        int from = Worker.LEVEL_XP[Math.min(level, Worker.LEVEL_XP.length) - 1];
        int next = Worker.nextLevelXp(xp);
        float progress = next < 0 ? 1f : (xp - from) / (float) Math.max(1, next - from);
        graphics.fill(barX, cy + 2, barX + barW, cy + 6, 0xFF3A3A3A);
        graphics.fill(barX, cy + 2, barX + Math.round(barW * Math.max(0f, Math.min(1f, progress))), cy + 6, 0xFF7CD25A);
        cy += 10;
        int hearts = Math.round(worker.getHealth());
        int max = Math.round(worker.getMaxHealth());
        graphics.drawString(font, Component.translatable(Lang.INV_HEALTH, hearts, max), cardX, cy, 0xFF8B1A1A, false);
        cy += 11;
        String status = worker.getEntityData().get(Worker.DATA_STATUS);
        int color = switch (worker.getEntityData().get(Worker.DATA_STATE)) {
            case Worker.STATE_WORKING -> 0xFF2E7D32;
            case Worker.STATE_BLOCKED -> 0xFF9A6A00;
            case Worker.STATE_SLEEPING -> 0xFF3949AB;
            default -> 0xFF555555;
        };
        List<FormattedCharSequence> lines = font.split(StatusText.toComponent(status), 64);
        int room = Math.max(1, (88 - cy) / 9);
        for (int i = 0; i < Math.min(room, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), cardX, cy + i * 9, color, false);
        }
    }

    /** Bag items the worker keeps on drop-off say so in their tooltip. */
    @Override
    protected List<Component> getTooltipFromContainerItem(net.minecraft.world.item.ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        Worker worker = menu.worker();
        Slot hovered = hoveredSlot;
        if (worker == null || hovered == null || hovered.index < 6 || hovered.index >= 33) return lines;
        String id = ExtraJobs.itemId(stack);
        for (String entry : worker.getEntityData().get(Worker.DATA_RESERVES).split(",")) {
            int eq = entry.indexOf('=');
            if (eq > 0 && entry.substring(0, eq).equals(id)) {
                lines = new java.util.ArrayList<>(lines);
                lines.add(Component.translatable(Lang.INV_KEPT, entry.substring(eq + 1)).withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        }
        return lines;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        Worker w = menu.worker();
        if (w != null && mouseX >= leftPos + 100 && mouseX < leftPos + 169 && mouseY >= topPos + 17 && mouseY < topPos + 89) {
            int xp = w.getEntityData().get(Worker.DATA_XP);
            int next = Worker.nextLevelXp(xp);
            int level = w.getEntityData().get(Worker.DATA_LEVEL);
            graphics.renderTooltip(font, next < 0 ? Component.translatable(Lang.INV_LEVEL_MAX, level)
                : Component.translatable(Lang.INV_LEVEL, level, xp, next), mouseX, mouseY);
        }
        Slot hovered = hoveredSlot;
        if (hovered != null && !hovered.hasItem() && hovered.index < EQUIP_HINTS.length) {
            graphics.renderTooltip(font, Component.translatable(EQUIP_HINTS[hovered.index]), mouseX, mouseY);
        }
    }
}
