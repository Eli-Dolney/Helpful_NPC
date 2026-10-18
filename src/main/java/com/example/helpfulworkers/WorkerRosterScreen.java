package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Clipboard overview of every owned worker: state colour from the server (blocked first), status
 * with request progress, last drop-off, and quick controls (pause, recall, talk from afar, show
 * area, come here, and go-to in creative).
 */
@OnlyIn(Dist.CLIENT)
public final class WorkerRosterScreen extends Screen {
    private static final int PANEL_WIDTH = 400;
    private static final int ROW_HEIGHT = 46;
    private static final int BUTTON_W = 58;
    private static final int BUTTONS_W = BUTTON_W * 3 + 8;

    private final List<WorkerNetwork.RosterEntry> entries = new ArrayList<>();
    private int scroll;
    private int refreshTimer;

    public WorkerRosterScreen(WorkerNetwork.RosterPayload payload) {
        super(Component.translatable(Lang.ROSTER_TITLE, payload.entries().size()));
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

    private int panelWidth() { return Math.min(PANEL_WIDTH, width - 16); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelHeight() { return Math.min(height - 16, 320); }
    private int panelTop() { return Math.max(8, (height - panelHeight()) / 2); }
    private int listTop() { return panelTop() + 40; }
    private int visibleRows() { return Math.max(1, (panelHeight() - 68) / ROW_HEIGHT); }

    private void rebuild() {
        clearWidgets();
        int left = panelLeft();
        int rows = visibleRows();
        boolean creative = minecraft != null && minecraft.player != null && minecraft.player.isCreative();
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            WorkerNetwork.RosterEntry e = entries.get(scroll + i);
            int y = listTop() + i * ROW_HEIGHT;
            int bx = left + panelWidth() - 8 - BUTTONS_W;
            if (e.id() < 0) continue; // away: nothing to control
            boolean combat = WorkerActions.isCombatRole(e.role());
            if (!combat) {
                add(Component.translatable(e.working() ? Lang.ROSTER_PAUSE : Lang.ROSTER_RESUME),
                    e.id(), e.working() ? "pause" : "resume", bx, y + 2, true);
            }
            add(Component.translatable(e.hasBed() ? Lang.ROSTER_RECALL : Lang.ROSTER_NO_BED), e.id(), "recall",
                bx + BUTTON_W + 4, y + 2, true);
            add(Component.translatable(Lang.ROSTER_TALK), e.id(), "talk", bx + (BUTTON_W + 4) * 2, y + 2, true);
            add(Component.translatable(Lang.ROSTER_COME), e.id(), "come", bx, y + 22, true);
            add(Component.translatable(Lang.ROSTER_AREA), e.id(), "outline", bx + BUTTON_W + 4, y + 22, e.hasArea());
            if (creative) add(Component.translatable(Lang.ROSTER_TELEPORT), e.id(), "teleport", bx + (BUTTON_W + 4) * 2, y + 22, true);
        }
        int bottomY = panelTop() + panelHeight() - 24;
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - 1); rebuild(); })
            .bounds(left + 12, bottomY, 30, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            scroll = Math.min(Math.max(0, entries.size() - visibleRows()), scroll + 1); rebuild();
        }).bounds(left + 46, bottomY, 30, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(Lang.ROSTER_REFRESH), b -> send(-1, "refresh"))
            .bounds(left + 80, bottomY, 60, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(Lang.ROSTER_CLOSE), b -> onClose())
            .bounds(left + panelWidth() - 72, bottomY, 60, 18).build());
    }

    private void add(Component label, int workerId, String action, int x, int y, boolean active) {
        Button button = Button.builder(label, b -> send(workerId, action)).bounds(x, y, BUTTON_W, 18).build();
        button.active = active;
        if ("talk".equals(action)) button.setTooltip(Tooltip.create(Component.literal("Open this worker's dialogue from here")));
        addRenderableWidget(button);
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
        int right = left + panelWidth();
        int bottom = top + panelHeight();
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF1A1A1A);
        graphics.fill(left, top, right, bottom, 0xF0181C22);
        graphics.fill(left, top, right, top + 3, 0xFFE0B04A);
        graphics.drawString(font, Component.translatable(Lang.ROSTER_TITLE, entries.size()), left + 12, top + 10, 0xFFFFFF, false);
        Component updates = Component.translatable(Lang.ROSTER_UPDATES);
        graphics.drawString(font, updates, right - 12 - font.width(updates), top + 10, 0xFF78909C, false);
        int working = 0, blocked = 0, idle = 0;
        for (WorkerNetwork.RosterEntry e : entries) {
            if (e.state() == Worker.STATE_WORKING) working++;
            else if (e.state() == Worker.STATE_BLOCKED) blocked++;
            else if (e.state() >= 0) idle++;
        }
        graphics.drawString(font, Component.translatable(Lang.ROSTER_SUMMARY, working, blocked, idle),
            left + 12, top + 22, blocked > 0 ? 0xFFFFC94D : 0xFF90A4AE, false);
        if (entries.isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable(Lang.ROSTER_EMPTY), left + 12, listTop() + 8,
                panelWidth() - 24, 0xFFB0BEC5);
        }
        int rows = visibleRows();
        int textWidth = panelWidth() - 24 - BUTTONS_W - 28;
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            WorkerNetwork.RosterEntry e = entries.get(scroll + i);
            int y = listTop() + i * ROW_HEIGHT;
            graphics.fill(left + 6, y - 2, right - 6, y + ROW_HEIGHT - 4, (i % 2 == 0) ? 0x18FFFFFF : 0x0CFFFFFF);
            int state = stateColor(e.state());
            graphics.fill(left + 6, y - 2, left + 8, y + ROW_HEIGHT - 4, state);
            String key = WorkerActions.isRole(e.role()) ? e.role() : "idle";
            graphics.blit(ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID, "textures/gui/role_" + key + ".png"),
                left + 12, y + 2, 0, 0, 16, 16, 16, 16);
            int tx = left + 34;
            String lv = e.level() > 0 ? " · " + Component.translatable(Lang.LEVEL_SHORT, e.level()).getString() : "";
            graphics.drawString(font, fit(e.name() + " · " + Component.translatable(Lang.roleKey(e.role())).getString() + lv, textWidth),
                tx, y + 2, 0xFFFFFF, false);
            graphics.drawString(font, fit(StatusText.toComponent(e.status()).getString(), textWidth), tx, y + 14, state, false);
            String where = Component.translatable(Lang.ROSTER_WHERE, e.x(), e.y(), e.z(), e.distance()).getString();
            String drop = e.state() < 0 ? "" : "  ·  " + (e.sinceDrop() < 0
                ? Component.translatable(Lang.ROSTER_NEVER_DROPPED).getString()
                : Component.translatable(Lang.ROSTER_LAST_DROP, duration(e.sinceDrop())).getString());
            graphics.drawString(font, fit(where + drop, textWidth), tx, y + 26, 0xFF90A4AE, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            WorkerNetwork.RosterEntry e = entries.get(scroll + i);
            int y = listTop() + i * ROW_HEIGHT;
            if (mouseX >= left + 34 && mouseX < left + 34 + textWidth && mouseY >= y + 12 && mouseY < y + 24
                && font.width(StatusText.toComponent(e.status())) > textWidth) {
                graphics.renderTooltip(font, font.split(StatusText.toComponent(e.status()), 240), mouseX, mouseY);
            }
        }
    }

    private static int stateColor(byte state) {
        return switch (state) {
            case Worker.STATE_WORKING -> 0xFF7BD88F;
            case Worker.STATE_BLOCKED -> 0xFFFF7A6B;
            case Worker.STATE_SLEEPING -> 0xFF9DA8FF;
            case Worker.STATE_IDLE -> 0xFFFFC857;
            default -> 0xFF78909C;
        };
    }

    private static String duration(long ticks) {
        long seconds = ticks / 20;
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h";
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
