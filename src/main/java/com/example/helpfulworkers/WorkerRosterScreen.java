package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** Clipboard worker monitor: what every owned worker is doing, with quick pause/recall controls. */
@OnlyIn(Dist.CLIENT)
public final class WorkerRosterScreen extends Screen {
    private static final int PANEL_WIDTH = 348;
    private static final int ROW_HEIGHT = 46;

    private final List<WorkerNetwork.RosterEntry> entries = new ArrayList<>();
    private int scroll;
    private int refreshTimer;

    public WorkerRosterScreen(WorkerNetwork.RosterPayload payload) {
        super(Component.literal("Workers"));
        entries.addAll(payload.entries());
    }

    void apply(WorkerNetwork.RosterPayload payload) {
        entries.clear();
        entries.addAll(payload.entries());
        scroll = Math.min(scroll, Math.max(0, entries.size() - visibleRows()));
        rebuild();
    }

    @Override
    protected void init() {
        rebuild();
    }

    private int panelLeft() { return (width - PANEL_WIDTH) / 2; }
    private int panelHeight() { return Math.min(height - 16, 300); }
    private int panelTop() { return Math.max(8, (height - panelHeight()) / 2); }
    private int listTop() { return panelTop() + 30; }
    private int visibleRows() { return Math.max(1, (panelHeight() - 58) / ROW_HEIGHT); }

    private void rebuild() {
        clearWidgets();
        int left = panelLeft();
        int rows = visibleRows();
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            WorkerNetwork.RosterEntry e = entries.get(scroll + i);
            int y = listTop() + i * ROW_HEIGHT;
            int bx = left + PANEL_WIDTH - 128;
            boolean combat = WorkerActions.isCombatRole(e.role());
            boolean unloaded = e.id() < 0;
            if (!combat && !unloaded) {
                addRenderableWidget(Button.builder(Component.literal(e.working() ? "Pause" : "Resume"),
                    b -> send(e.id(), e.working() ? "pause" : "resume")).bounds(bx, y + 2, 58, 18).build());
            }
            if (!unloaded) {
                addRenderableWidget(Button.builder(Component.literal(e.hasBed() ? "Recall" : "No bed"),
                    b -> send(e.id(), "recall")).bounds(bx + 62, y + 2, 58, 18).build());
                addRenderableWidget(Button.builder(Component.literal("Come here"),
                    b -> send(e.id(), "come")).bounds(bx, y + 22, 58, 18).build());
                Button area = Button.builder(Component.literal("Show area"),
                    b -> send(e.id(), "outline")).bounds(bx + 62, y + 22, 58, 18).build();
                area.active = e.hasArea();
                addRenderableWidget(area);
            }
        }
        int bottomY = panelTop() + panelHeight() - 24;
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - 1); rebuild(); })
            .bounds(left + 12, bottomY, 30, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            scroll = Math.min(Math.max(0, entries.size() - visibleRows()), scroll + 1); rebuild();
        }).bounds(left + 46, bottomY, 30, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> send(-1, "refresh"))
            .bounds(left + 80, bottomY, 60, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(left + PANEL_WIDTH - 72, bottomY, 60, 18).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, entries.size() - visibleRows());
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        if (next != scroll) { scroll = next; rebuild(); }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTimer >= 40) {
            refreshTimer = 0;
            send(-1, "refresh");
        }
    }

    private void send(int workerId, String action) {
        PacketDistributor.sendToServer(new WorkerNetwork.RosterActionPayload(workerId, action));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = panelLeft();
        int top = panelTop();
        int right = left + PANEL_WIDTH;
        int bottom = top + panelHeight();
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF1A1A1A);
        graphics.fill(left, top, right, bottom, 0xF0181C22);
        graphics.fill(left, top, right, top + 3, 0xFFE0B04A);
        graphics.drawString(font, "Your workers (" + entries.size() + ")", left + 12, top + 12, 0xFFFFFF, false);
        graphics.drawString(font, "updates every 2s", right - 12 - font.width("updates every 2s"), top + 12, 0xFF78909C, false);
        if (entries.isEmpty()) {
            graphics.drawWordWrap(font, Component.literal("No workers found nearby. Workers in unloaded chunks don't show up."),
                left + 12, listTop() + 8, PANEL_WIDTH - 24, 0xFFB0BEC5);
        }
        int rows = visibleRows();
        int textWidth = PANEL_WIDTH - 24 - 132 - 20;
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            WorkerNetwork.RosterEntry e = entries.get(scroll + i);
            int y = listTop() + i * ROW_HEIGHT;
            graphics.fill(left + 6, y - 2, right - 6, y + ROW_HEIGHT - 4, (i % 2 == 0) ? 0x18FFFFFF : 0x0CFFFFFF);
            String key = WorkerActions.isRole(e.role()) ? e.role() : "idle";
            graphics.blit(ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID, "textures/gui/role_" + key + ".png"),
                left + 10, y + 2, 0, 0, 16, 16, 16, 16);
            int state = stateColor(e);
            graphics.fill(left + 12, y + 22, left + 24, y + 34, state);
            int tx = left + 32;
            graphics.drawString(font, fit(e.name(), textWidth), tx, y + 2, 0xFFFFFF, false);
            graphics.drawString(font, fit(e.status(), textWidth), tx, y + 14, state, false);
            graphics.drawString(font, e.x() + ", " + e.y() + ", " + e.z() + "  ·  " + e.distance() + "m away",
                tx, y + 26, 0xFF90A4AE, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            WorkerNetwork.RosterEntry e = entries.get(scroll + i);
            int y = listTop() + i * ROW_HEIGHT;
            if (mouseX >= left + 32 && mouseX < left + 32 + textWidth && mouseY >= y + 12 && mouseY < y + 24
                && font.width(e.status()) > textWidth) {
                graphics.renderTooltip(font, font.split(Component.literal(e.status()), 220), mouseX, mouseY);
            }
        }
    }

    private static int stateColor(WorkerNetwork.RosterEntry e) {
        String s = e.status().toLowerCase(Locale.ROOT);
        if (s.contains("can't") || s.contains("missing") || s.contains("out of") || s.contains("too weak")
            || s.contains("unloaded") || s.startsWith("paused:") || s.contains("conflicting") || s.contains("full")
            || s.contains("too small") || s.contains("no reachable") || s.contains("no work")) {
            return 0xFFFF7A6B;
        }
        if (!e.working() || s.contains("complete")) return 0xFFFFC857;
        return 0xFF7BD88F;
    }

    private String fit(String text, int width) {
        if (text == null) return "";
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
