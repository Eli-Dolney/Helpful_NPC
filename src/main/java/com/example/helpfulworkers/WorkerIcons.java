package com.example.helpfulworkers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Crisp 16-pixel UI symbols drawn locally, with consistent strokes and no font-glyph dependency.
 */
final class WorkerIcons {
    enum Kind {
        WORKER,
        BED,
        CHEST,
        AREA,
        TABLE
    }

    static void draw(GuiGraphics g, Kind kind, int x, int y, int ink, int accent) {
        switch (kind) {
            case WORKER -> {
                outline(g, x + 5, y + 1, 6, 6, ink);
                g.fill(x + 6, y + 2, x + 10, y + 6, accent);
                g.fill(x + 3, y + 9, x + 13, y + 11, ink);
                g.fill(x + 2, y + 11, x + 4, y + 15, ink);
                g.fill(x + 12, y + 11, x + 14, y + 15, ink);
                g.fill(x + 4, y + 13, x + 12, y + 15, ink);
            }
            case BED -> {
                g.fill(x + 1, y + 3, x + 3, y + 15, ink);
                g.fill(x + 13, y + 7, x + 15, y + 15, ink);
                g.fill(x + 3, y + 8, x + 13, y + 12, accent);
                g.fill(x + 3, y + 5, x + 7, y + 8, ink);
                g.fill(x + 3, y + 11, x + 13, y + 13, ink);
            }
            case CHEST -> {
                outline(g, x + 1, y + 3, 14, 11, ink);
                g.fill(x + 3, y + 5, x + 13, y + 7, accent);
                g.fill(x + 2, y + 7, x + 14, y + 9, ink);
                g.fill(x + 7, y + 7, x + 9, y + 11, accent);
            }
            case AREA -> {
                outline(g, x + 2, y + 2, 12, 12, ink);
                g.fill(x + 5, y + 5, x + 11, y + 11, accent);
            }
            case TABLE -> {
                g.fill(x + 1, y + 3, x + 15, y + 7, ink);
                g.fill(x + 3, y + 7, x + 5, y + 15, ink);
                g.fill(x + 11, y + 7, x + 13, y + 15, ink);
                g.fill(x + 3, y + 4, x + 13, y + 6, accent);
            }
        }
    }

    static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    static Button button(
            int x, int y, int width, String label, Kind icon, int accent, Button.OnPress action) {
        return new Button(
                x, y, width, 20, Component.literal(label), action, supplier -> supplier.get()) {
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
                int ink = active ? 0xffe5edf5 : 0xff73808e;
                g.fill(
                        getX(),
                        getY(),
                        getX() + getWidth(),
                        getY() + getHeight(),
                        isHoveredOrFocused() ? 0xff303f51 : 0xff202c3a);
                outline(
                        g,
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight(),
                        isFocused() ? 0xffd7b773 : 0xff435264);
                draw(g, icon, getX() + 5, getY() + 2, ink, active ? accent : 0xff596675);
                var font = Minecraft.getInstance().font;
                String text = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 30);
                g.drawString(font, text, getX() + 26, getY() + 6, ink, false);
            }
        };
    }
}
