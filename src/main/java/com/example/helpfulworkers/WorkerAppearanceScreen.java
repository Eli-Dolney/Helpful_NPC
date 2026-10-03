package com.example.helpfulworkers;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** Draft edits are local until Save; entity data drives all other previews after acknowledgment. */
final class WorkerAppearanceScreen extends Screen {
    private final Worker worker;
    private final Screen parent;
    private final List<String> choices;
    private String selected, draftName, message = "Choose a look. Save applies both name and skin.";
    private boolean slim, saving;
    private int page;
    private EditBox name;
    private Button save;
    private final List<Button> tiles = new ArrayList<>();

    WorkerAppearanceScreen(Worker worker, Screen parent) {
        super(Component.literal("Name & appearance"));
        this.worker = worker;
        this.parent = parent;
        selected = worker.skinKey();
        slim = worker.slimSkin();
        draftName = worker.personalName();
        choices = WorkerSkins.choices();
        if (!choices.contains(selected)) choices.add(selected);
        page = Math.max(0, choices.indexOf(selected) / 6);
    }

    private int left() {
        return (width - 320) / 2;
    }

    private int top() {
        return Math.max(4, (height - 232) / 2);
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        tiles.clear();
        name = new EditBox(font, x + 12, y + 39, 210, 20, Component.literal("Worker name"));
        name.setMaxLength(32);
        name.setValue(draftName);
        name.setResponder(v -> draftName = v);
        addRenderableWidget(name);
        addRenderableWidget(
                Button.builder(
                                Component.literal("Original"),
                                b -> {
                                    selected = "auto";
                                    slim = false;
                                    rebuildWidgets();
                                })
                        .bounds(x + 230, y + 39, 78, 20)
                        .build());
        for (int i = 0; i < 6 && page * 6 + i < choices.size(); i++) {
            String key = choices.get(page * 6 + i);
            int tx = x + 98 + (i % 2) * 108, ty = y + 70 + (i / 2) * 28;
            Button tile = new Button(tx,ty,104,26,Component.literal(WorkerSkins.label(key)),b->{selected=key;rebuildWidgets();},supplier->supplier.get()) {
                @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt) { }
            };
            // Faces and captions are rendered together after buttons, keeping hit targets
            // accessible.
            addRenderableWidget(tile);
            tiles.add(tile);
        }
        Button previous = addRenderableWidget(
                Button.builder(
                                Component.literal("< Previous"),
                                b -> {
                                    page = Math.max(0, page - 1);
                                    rebuildWidgets();
                                })
                        .bounds(x + 12, y + 156, 74, 20)
                        .build());
        previous.active = page > 0;
        Button model =
                Button.builder(
                                Component.literal(slim ? "Arms: slim" : "Arms: classic"),
                                b -> {
                                    slim = !slim;
                                    rebuildWidgets();
                                })
                        .bounds(x + 93, y + 156, 134, 20)
                        .build();
        model.active = !selected.equals("auto") && !selected.equals("owner");
        addRenderableWidget(model);
        Button next = addRenderableWidget(
                Button.builder(
                                Component.literal("Next >"),
                                b -> {
                                    page = Math.min((choices.size() - 1) / 6, page + 1);
                                    rebuildWidgets();
                                })
                        .bounds(x + 234, y + 156, 74, 20)
                        .build());
        next.active = page < (choices.size()-1)/6;
        addRenderableWidget(
                Button.builder(Component.literal("Cancel"), b -> back())
                        .bounds(x + 12, y + 208, 144, 20)
                        .build());
        save =
                Button.builder(
                                Component.literal("Save changes"),
                                b -> {
                                    saving = true;
                                    save.active = false;
                                    message = "Saving…";
                                    PacketDistributor.sendToServer(
                                            new WorkerAppearanceNetwork.Save(
                                                    worker.getId(), draftName, selected, slim));
                                })
                        .bounds(x + 164, y + 208, 144, 20)
                        .build();
        save.active = !saving;
        addRenderableWidget(save);
    }

    void forwardStatus(WorkerNetwork.WorkerStatusPayload p) {
        if (parent instanceof WorkerDialogueScreen dialogue) dialogue.applyStatus(p);
    }

    void result(WorkerAppearanceNetwork.Result r) {
        if (r.workerId() != worker.getId()) return;
        saving = false;
        save.active = true;
        message = r.message();
    }

    private void back() {
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        back();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (!worker.isAlive()
                || minecraft.player == null
                || minecraft.player.distanceToSqr(worker) > 64) {
            PacketDistributor.sendToServer(
                    new WorkerNetwork.DialogueActionPayload(worker.getId(), "close", ""));
            minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        renderBackground(g, mx, my, dt);
        int x = left(), y = top();
        WorkerUi.frame(g,x,y,320,232);
        WorkerIcons.draw(g, WorkerIcons.Kind.WORKER, x + 12, y + 10, 0xffe5edf5, 0xffd7b773);
        g.drawString(font, "Name & appearance", x + 35, y + 13, 0xfff1f5f9, false);
        g.drawString(font, "Worker name", x + 12, y + 28, 0xff9eafc1, false);
        for (var widget : renderables) widget.render(g, mx, my, dt);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        for (int i = 0; i < tiles.size(); i++) {
            var tile = tiles.get(i);
            String key = choices.get(page * 6 + i);
            var look = WorkerSkins.resolve(worker, key, slim);
            g.fill(
                    tile.getX() + 1,
                    tile.getY() + 1,
                    tile.getX() + 103,
                    tile.getY() + 25,
                    key.equals(selected) ? 0xff71603e : 0xff34382b);
            if (tile.isHoveredOrFocused() || key.equals(selected))
                WorkerIcons.outline(g, tile.getX(), tile.getY(), 104, 26, 0xffd7b773);
            face(g, look, tile.getX() + 4, tile.getY() + 4, 18);
            String label = font.plainSubstrByWidth(WorkerSkins.label(key), 75);
            g.drawString(font, label, tile.getX() + 26, tile.getY() + 9, 0xffeef4fa, false);
        }
        g.pose().popPose();
        g.drawString(font,"Looks " + (page+1) + " / " + Math.max(1,(choices.size()+5)/6),x+12,y+148,WorkerUi.MUTED,false);
        var look = WorkerSkins.resolve(worker, selected, slim);
        paperDoll(g, look, x + 34, y + 68);
        g.drawString(font,font.plainSubstrByWidth(WorkerSkins.label(selected),80),x+12,y+136,WorkerUi.GOLD,false);
        String feedback =
                look.missing() ? "Skin unavailable here; original look is shown." : message;
        g.drawWordWrap(
                font,
                Component.literal(feedback),
                x + 12,
                y + 181,
                296,
                look.missing() ? 0xffffc879 : 0xffa8bfd0);
    }

    private static void paperDoll(GuiGraphics g, WorkerSkins.Look look, int x, int y) {
        int arm = look.slim() ? 3 : 4;
        // Front-facing skin layout at 2x, including jacket, sleeves, trousers, and hat.
        part(g, look, x + 8, y, 8, 8, 8, 8);
        part(g, look, x + 8, y, 40, 8, 8, 8);
        part(g, look, x + 8, y + 16, 20, 20, 8, 12);
        part(g, look, x + 8, y + 16, 20, 36, 8, 12);
        part(g, look, x + 8 - arm * 2, y + 16, 44, 20, arm, 12);
        part(g, look, x + 8 - arm * 2, y + 16, 44, 36, arm, 12);
        part(g, look, x + 24, y + 16, 36, 52, arm, 12);
        part(g, look, x + 24, y + 16, 52, 52, arm, 12);
        part(g, look, x + 8, y + 40, 4, 20, 4, 12);
        part(g, look, x + 8, y + 40, 4, 36, 4, 12);
        part(g, look, x + 16, y + 40, 20, 52, 4, 12);
        part(g, look, x + 16, y + 40, 4, 52, 4, 12);
    }

    private static void part(
            GuiGraphics g, WorkerSkins.Look look, int x, int y, int u, int v, int w, int h) {
        g.blit(look.texture(), x, y, w * 2, h * 2, u, v, w, h, 64, 64);
    }

    static void face(GuiGraphics g, WorkerSkins.Look look, int x, int y, int size) {
        g.blit(look.texture(), x, y, size, size, 8, 8, 8, 8, 64, 64);
        g.blit(look.texture(), x, y, size, size, 40, 8, 8, 8, 64, 64);
    }
}
