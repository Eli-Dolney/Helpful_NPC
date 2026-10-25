package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class WorkerDialogueScreen extends Screen {
    private enum Page {
        MAIN, WORK, ROLE, LOCATIONS, RECIPES, BUILD, BLUEPRINT, ROLE_CONFIRM, CAPTURE_CONFIRM,
        MESSAGE, DEPTH, PROTECT, CROPS, TARGET_Y, BIOME, RANCH, CULL_CONFIRM, ROUTES, REQUESTS, TOOLS, ORDERS, COOK
    }

    private static final ResourceLocation PORTRAIT =
        ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID, "textures/gui/worker_portrait.png");

    private int workerId;
    private String workerName;
    private String role;
    private String status;
    private boolean working;
    private String mode;
    private String pendingRole;
    private boolean hasArea, hasBed, hasSupply, hasOutput, hasTable, hasOrigin;
    private String blueprint;
    private int rotation;
    private int digDepth;
    private String villageBiome = "plains";
    private Page page = Page.MAIN;
    private Component infoMessage = Component.empty();
    private EditBox searchBox;
    private EditBox nameBox;
    private final List<WorkerNetwork.RecipeEntry> recipes = new ArrayList<>();
    private final List<String> blueprints = new ArrayList<>();
    private final Set<String> flags = new HashSet<>();
    private int recipeScroll;
    private int blueprintScroll;
    private String pendingCaptureName = "";
    private boolean suppressClosePacket;

    /** A screen opened straight on one page, with no server behind it (dev UI screenshots). */
    static WorkerDialogueScreen preview(WorkerNetwork.OpenDialoguePayload payload, String pageName) {
        WorkerDialogueScreen screen = new WorkerDialogueScreen(payload);
        screen.page = Page.valueOf(pageName);
        screen.suppressClosePacket = true;
        return screen;
    }

    public WorkerDialogueScreen(WorkerNetwork.OpenDialoguePayload payload) {
        super(Component.translatable(Lang.D_TITLE));
        applyOpen(payload);
        if (pendingRole != null && !pendingRole.isEmpty()) page = Page.ROLE_CONFIRM;
    }

    static WorkerDialogueScreen depthPicker(WorkerNetwork.OpenDepthPickerPayload payload) {
        WorkerDialogueScreen screen = new WorkerDialogueScreen(payload.workerId(), payload.name());
        screen.page = Page.DEPTH;
        return screen;
    }

    private WorkerDialogueScreen(int workerId, String name) {
        super(Component.translatable(Lang.D_TITLE));
        this.workerId = workerId;
        this.workerName = name;
        this.role = "miner";
        this.mode = "terraform";
        this.status = Component.translatable(Lang.DEPTH_CHOOSE).getString();
        this.page = Page.DEPTH;
    }

    private void applyOpen(WorkerNetwork.OpenDialoguePayload p) {
        workerId = p.workerId();
        workerName = p.name();
        role = p.role();
        status = p.status();
        working = p.working();
        mode = p.mode();
        pendingRole = p.pendingRole();
        hasArea = p.hasArea();
        hasBed = p.hasBed();
        hasSupply = p.hasSupply();
        hasOutput = p.hasOutput();
        hasTable = p.hasTable();
        hasOrigin = p.hasOrigin();
        blueprint = p.blueprint();
        rotation = p.rotation();
        digDepth = p.digDepth();
        flags.clear();
        if (p.flags() != null) flags.addAll(p.flags());
    }

    void applyStatus(WorkerNetwork.WorkerStatusPayload p) {
        if (p.workerId() != workerId) return;
        workerName = p.name();
        role = p.role();
        status = p.status();
        working = p.working();
        mode = p.mode();
        hasArea = p.hasArea();
        hasBed = p.hasBed();
        hasSupply = p.hasSupply();
        hasOutput = p.hasOutput();
        hasTable = p.hasTable();
        hasOrigin = p.hasOrigin();
        blueprint = p.blueprint();
        rotation = p.rotation();
        digDepth = p.digDepth();
        flags.clear();
        if (p.flags() != null) flags.addAll(p.flags());
        rebuild();
    }

    void applyRecipes(WorkerNetwork.RecipeListPayload p) {
        if (p.workerId() != workerId) return;
        recipes.clear();
        for (int i = 0; i < p.ids().size(); i++) {
            recipes.add(new WorkerNetwork.RecipeEntry(p.ids().get(i), p.names().get(i), p.approved().get(i),
                p.supported().get(i), p.reasons().get(i)));
        }
        if (page == Page.RECIPES) rebuild();
    }

    void applyBlueprints(WorkerNetwork.BlueprintListPayload p) {
        if (p.workerId() != workerId) return;
        blueprints.clear();
        blueprints.addAll(p.names());
        blueprint = p.current();
        if (page == Page.BUILD || page == Page.BLUEPRINT) rebuild();
    }

    @Override
    protected void init() {
        rebuild();
    }

    private int panelLeft() { return (this.width - 276) / 2; }
    private int panelWidth() { return 276; }
    private int panelHeight() { return Math.min(this.height - 16, 326); }
    private int panelTop() { return Math.max(8, (this.height - panelHeight()) / 2); }
    private int contentTop() { return panelTop() + 80; }
    private int contentBottom() { return panelTop() + panelHeight() - 30; }

    // ---------------------------------------------------------------- tabs, icons, status strip

    private record Tab(String key, Page page, int x, int width) {}
    private record Icon(ResourceLocation texture, net.minecraft.world.item.ItemStack stack, int x, int y) {}
    private final List<Tab> tabs = new ArrayList<>();
    private final List<Icon> icons = new ArrayList<>();

    /** The role page behind the "Options" tab, or null when the role has none. */
    private Page optionsPage() {
        return switch (role) {
            case "farmer", "gatherer" -> Page.CROPS;
            case "rancher" -> Page.RANCH;
            case "courier" -> Page.ROUTES;
            case "toolsmith" -> Page.TOOLS;
            case "stonemason" -> Page.ORDERS;
            case "cook" -> Page.COOK;
            case "knight", "archer" -> Page.PROTECT;
            default -> null;
        };
    }

    /** Which tab a page belongs to, so sub-pages keep their tab underlined. */
    private Page tabOf(Page p) {
        return switch (p) {
            case ROLE, DEPTH, TARGET_Y -> Page.WORK;
            case PROTECT, CROPS, RANCH, ROUTES, TOOLS, ORDERS, COOK, CULL_CONFIRM -> optionsPage();
            case BIOME, BLUEPRINT, CAPTURE_CONFIRM -> Page.BUILD;
            case MESSAGE, ROLE_CONFIRM -> Page.MAIN;
            default -> p;
        };
    }

    private boolean showsTabs() {
        return page != Page.DEPTH && page != Page.ROLE_CONFIRM && page != Page.CAPTURE_CONFIRM && page != Page.CULL_CONFIRM;
    }

    private void buildTabs(int left, int top) {
        tabs.clear();
        List<String[]> wanted = new ArrayList<>();
        wanted.add(new String[]{Lang.TAB_TALK, Page.MAIN.name()});
        wanted.add(new String[]{Lang.TAB_WORK, Page.WORK.name()});
        wanted.add(new String[]{Lang.TAB_WHERE, Page.LOCATIONS.name()});
        if (optionsPage() != null) wanted.add(new String[]{Lang.TAB_OPTIONS, optionsPage().name()});
        if (!WorkerActions.isCombatRole(role) && !"idle".equals(role)) wanted.add(new String[]{Lang.TAB_NEEDS, Page.REQUESTS.name()});
        if ("builder".equals(role)) wanted.add(new String[]{Lang.TAB_BUILD, Page.BUILD.name()});
        if ("builder".equals(role) || "miner".equals(role) || "farmer".equals(role) || "forester".equals(role)) {
            wanted.add(new String[]{Lang.TAB_CRAFT, Page.RECIPES.name()});
        }
        int width = 252 / wanted.size();
        for (int i = 0; i < wanted.size(); i++) {
            Page target = Page.valueOf(wanted.get(i)[1]);
            int x = left + 12 + i * width;
            tabs.add(new Tab(wanted.get(i)[0], target, x, width));
            addRenderableWidget(Button.builder(Component.translatable(wanted.get(i)[0]), b -> {
                page = target;
                if (target == Page.RECIPES) { recipeScroll = 0; send("request_recipes", ""); }
                rebuild();
            }).bounds(x, top + 50, width - 2, 16).build());
        }
    }

    private void drawTabUnderline(GuiGraphics graphics, int top) {
        Page current = tabOf(page);
        for (Tab tab : tabs) {
            if (tab.page() == current) graphics.fill(tab.x(), top + 66, tab.x() + tab.width() - 2, top + 68, roleColor());
        }
    }

    /** One line of page help between the tabs and the first row, cut with … when it is too long. */
    private void drawHint(GuiGraphics graphics, Component text, int left) {
        String line = text.getString();
        if (font.width(line) > 252) line = font.plainSubstrByWidth(line, 252 - font.width("…")) + "…";
        graphics.drawString(font, line, left + 12, contentTop() - 11, 0xFFE7D7B0, false);
    }

    private void drawStatusStrip(GuiGraphics graphics, int left, int top) {
        int y = top + panelHeight() - 24;
        graphics.fill(left + 8, y, left + panelWidth() - 8, y + 18, 0xFF101318);
        byte state = Worker.STATE_IDLE;
        if (minecraft != null && minecraft.level != null && minecraft.level.getEntity(workerId) instanceof Worker w) {
            state = w.getEntityData().get(Worker.DATA_STATE);
        }
        int color = switch (state) {
            case Worker.STATE_WORKING -> 0xFF7CE07C;
            case Worker.STATE_BLOCKED -> 0xFFFFC94D;
            case Worker.STATE_SLEEPING -> 0xFF9DA8FF;
            default -> 0xFFB8B8B8;
        };
        String stateKey = switch (state) {
            case Worker.STATE_WORKING -> Lang.STATE_WORKING;
            case Worker.STATE_BLOCKED -> Lang.STATE_BLOCKED;
            case Worker.STATE_SLEEPING -> Lang.STATE_SLEEPING;
            default -> Lang.STATE_IDLE;
        };
        graphics.fill(left + 13, y + 6, left + 19, y + 12, color);
        Component label = Component.translatable(stateKey);
        graphics.drawString(font, label, left + 24, y + 5, color, false);
        int tx = left + 30 + font.width(label);
        int room = left + panelWidth() - 14 - tx;
        String line = StatusText.toComponent(status).getString();
        if (font.width(line) > room) line = font.plainSubstrByWidth(line, room - font.width("…")) + "…";
        graphics.drawString(font, line, tx, y + 5, 0xFFE0E0E0, false);
    }

    private void icon(ResourceLocation texture, int x, int y) {
        icons.add(new Icon(texture, null, x, y));
    }

    private void itemIcon(String itemId, int x, int y) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        var item = key == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        if (item != null) icons.add(new Icon(null, new net.minecraft.world.item.ItemStack(item), x, y));
    }

    private static ResourceLocation roleIconOf(String r) {
        return ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID,
            "textures/gui/role_" + (WorkerActions.isRole(r) ? r : "idle") + ".png");
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (sectionPages > 1 && page != Page.RECIPES && page != Page.BLUEPRINT && scrollY != 0) {
            int target = Math.max(0, Math.min(sectionPages - 1, sectionPage - (int) Math.signum(scrollY)));
            if (target != sectionPage) { sectionPage = target; rebuild(); }
            return true;
        }
        if (page == Page.BLUEPRINT && scrollY != 0) {
            blueprintScroll = Math.max(0, blueprintScroll - (int) Math.signum(scrollY));
            rebuild();
            return true;
        }
        if (page == Page.RECIPES && scrollY != 0) {
            recipeScroll = Math.max(0, recipeScroll - (int) Math.signum(scrollY));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void ensureTextBoxes(int left) {
        if (searchBox == null) {
            searchBox = new EditBox(font, left + 12, contentTop() - 2, 252, 16, Component.translatable(Lang.D_SEARCH));
            searchBox.setMaxLength(64);
            searchBox.setResponder(text -> {
                if (page == Page.RECIPES) {
                    recipeScroll = 0;
                    rebuild();
                }
            });
        } else {
            searchBox.setX(left + 12);
            searchBox.setY(contentTop() - 2);
        }
        if (nameBox == null) {
            nameBox = new EditBox(font, left + 12, contentTop() - 2, 252, 16, Component.translatable(Lang.D_NAME));
            nameBox.setMaxLength(32);
        } else {
            nameBox.setX(left + 12);
            nameBox.setY(contentTop() - 2);
        }
    }

    private void rebuild() {
        clearWidgets();
        icons.clear();
        tabs.clear();
        int left = panelLeft();
        int top = panelTop();
        if (showsTabs()) buildTabs(left, top);
        if (page == Page.RECIPES || page == Page.BUILD || page == Page.CAPTURE_CONFIRM || page == Page.BLUEPRINT) {
            ensureTextBoxes(left);
        }

        switch (page) {
            case MAIN -> buildMain(left, top);
            case WORK -> buildWork(left, top);
            case ROLE -> buildRole(left, top);
            case LOCATIONS -> buildLocations(left, top);
            case RECIPES -> buildRecipes(left, top);
            case BUILD -> buildBuild(left, top);
            case BLUEPRINT -> buildBlueprint(left, top);
            case ROLE_CONFIRM -> buildRoleConfirm(left, top);
            case CAPTURE_CONFIRM -> buildCaptureConfirm(left, top);
            case MESSAGE -> buildMessage(left, top);
            case DEPTH -> buildDepth(left, top);
            case PROTECT -> buildProtect(left, top);
            case CROPS -> buildCrops(left, top);
            case TARGET_Y -> buildTargetY(left, top);
            case BIOME -> buildBiome(left, top);
            case RANCH -> buildRanch(left, top);
            case CULL_CONFIRM -> buildCullConfirm(left, top);
            case ROUTES -> buildRoutes(left, top);
            case TOOLS -> buildTools(left, top);
            case ORDERS -> buildOrders(left, top);
            case COOK -> buildCook(left, top);
            case REQUESTS -> buildRequests(left, top);
        }
        paginate(left);
    }

    private int sectionPage, sectionPages = 1;
    private Page lastPage;

    /**
     * Short screens: when a page's rows run past the content area, keep whole rows together on
     * Previous/Next sections (with their icons) instead of letting controls fall off the panel.
     */
    private void paginate(int left) {
        if (lastPage != page) { sectionPage = 0; lastPage = page; }
        sectionPages = 1;
        var rows = new java.util.TreeMap<Integer, List<net.minecraft.client.gui.components.AbstractWidget>>();
        for (var child : children()) {
            // Text boxes keep their place (they are reused across rebuilds); everything else is paged.
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getY() >= contentTop() - 2
                && !(w instanceof net.minecraft.client.gui.components.EditBox)) {
                rows.computeIfAbsent(w.getY(), k -> new ArrayList<>()).add(w);
            }
        }
        if (rows.isEmpty()) return;
        // Later sections start below any fixed text box (name, search) at the top of the content.
        int start = contentTop();
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.EditBox box && box.getY() >= contentTop() - 2) {
                start = Math.max(start, box.getY() + box.getHeight() + 4);
            }
        }
        int last = rows.lastEntry().getValue().stream().mapToInt(w -> w.getY() + w.getHeight()).max().orElse(0);
        if (last <= contentBottom()) return;
        int limit = contentBottom() - 24; // room for the Previous/Next row
        List<Integer> rowYs = new ArrayList<>(rows.keySet());
        int[] pageOf = new int[rowYs.size()];
        int[] newY = new int[rowYs.size()];
        int pageIndex = 0, y = rowYs.get(0);
        for (int i = 0; i < rowYs.size(); i++) {
            int height = rows.get(rowYs.get(i)).stream().mapToInt(net.minecraft.client.gui.components.AbstractWidget::getHeight).max().orElse(20);
            if (i > 0) y += Math.max(height + 2, Math.min(rowYs.get(i) - rowYs.get(i - 1), height + 8));
            if (y + height > limit && i > 0 && y != start) { pageIndex++; y = start; }
            pageOf[i] = pageIndex;
            newY[i] = y;
        }
        sectionPages = pageIndex + 1;
        sectionPage = Math.max(0, Math.min(sectionPage, sectionPages - 1));
        List<Icon> moved = new ArrayList<>();
        for (int i = 0; i < rowYs.size(); i++) {
            int oldY = rowYs.get(i);
            int height = rows.get(oldY).stream().mapToInt(net.minecraft.client.gui.components.AbstractWidget::getHeight).max().orElse(20);
            for (Icon icon : icons) {
                if (icon.y() >= oldY && icon.y() < oldY + height && pageOf[i] == sectionPage) {
                    moved.add(new Icon(icon.texture(), icon.stack(), icon.x(), icon.y() - oldY + newY[i]));
                }
            }
            for (var w : rows.get(oldY)) {
                if (pageOf[i] == sectionPage) w.setY(newY[i]);
                else removeWidget(w);
            }
        }
        icons.removeIf(icon -> icon.y() >= contentTop() - 2);
        icons.addAll(moved);
        int py = contentBottom() - 20;
        Button previous = Button.builder(Component.translatable(Lang.D_PREVIOUS), b -> { sectionPage--; rebuild(); })
            .bounds(left + 12, py, 80, 20).build();
        previous.active = sectionPage > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.translatable(Lang.D_NEXT), b -> { sectionPage++; rebuild(); })
            .bounds(left + panelWidth() - 92, py, 80, 20).build();
        next.active = sectionPage < sectionPages - 1;
        addRenderableWidget(next);
    }

    private void buildMain(int left, int top) {
        int y = contentTop();
        choice(left + 12, y, Component.translatable(Lang.MAIN_WORK), b -> { page = Page.WORK; rebuild(); });
        y += 24;
        if (WorkerActions.isCombatRole(role)) {
            choice(left + 12, y, Component.translatable(Lang.MAIN_PROTECT), b -> { page = Page.PROTECT; rebuild(); });
            y += 24;
        }
        if ("farmer".equals(role)) {
            choice(left + 12, y, Component.translatable(Lang.MAIN_CROPS), b -> { page = Page.CROPS; rebuild(); });
            y += 24;
        }
        if ("gatherer".equals(role)) {
            choice(left + 12, y, Component.translatable(Lang.MAIN_GATHER), b -> { page = Page.CROPS; rebuild(); });
            y += 24;
        }
        if ("courier".equals(role)) {
            choice(left + 12, y, Component.translatable(Lang.MAIN_ROUTE), b -> { page = Page.ROUTES; rebuild(); });
            y += 24;
        }
        if ("rancher".equals(role)) {
            choice(left + 12, y, Component.translatable(Lang.MAIN_RANCH), b -> { page = Page.RANCH; rebuild(); });
            y += 24;
            choice(left + 12, y, Component.translatable(Lang.MAIN_CULL), b -> { page = Page.CULL_CONFIRM; rebuild(); });
            y += 24;
        }
        if (!WorkerActions.isCombatRole(role) && !"idle".equals(role)) {
            choice(left + 12, y, Component.translatable(Lang.MAIN_REQUESTS), b -> { page = Page.REQUESTS; rebuild(); });
            y += 24;
        }
        choice(left + 12, y, Component.translatable(Lang.MAIN_WHERE), b -> { page = Page.LOCATIONS; rebuild(); });
        y += 24;
        half(left + 12, y, Component.translatable(Lang.MAIN_OUTLINE), b -> send("show_outline", ""));
        half(left + 142, y, Component.translatable(Lang.MAIN_EQUIP_SHORT), b -> send("open_inventory", ""));
        y += 24;
        if ("builder".equals(role) || "miner".equals(role) || "farmer".equals(role) || "forester".equals(role)) {
            choice(left + 12, y, Component.translatable(Lang.MAIN_RECIPE), b -> {
                page = Page.RECIPES; recipeScroll = 0; send("request_recipes", ""); rebuild();
            });
            y += 24;
        }
        if ("builder".equals(role)) {
            choice(left + 12, y, Component.translatable(Lang.MAIN_HOUSE), b -> { page = Page.BUILD; rebuild(); });
            y += 24;
            choice(left + 12, y, Component.translatable(Lang.MAIN_BLUEPRINTS), b -> {
                page = Page.BLUEPRINT;
                send("request_recipes", ""); // keep session warm; blueprints come with open
                rebuild();
            });
            y += 24;
        }
        // 0.5.3 worker sites and appearance: opened in their own screens (SiteScreen, WorkerAppearanceScreen).
        if (hasSiteType()) {
            String key = "builder".equals(role) ? Lang.MAIN_SITE_BUILD : flags.contains("site") ? Lang.MAIN_SITE_MANAGE : Lang.MAIN_SITE_JOIN;
            addRenderableWidget(Button.builder(Component.translatable(key), b -> send("sites", ""))
                .tooltip(Tooltip.create(Component.translatable(Lang.MAIN_SITE_TIP))).bounds(left + 12, y, 122, 20).build());
        }
        half(left + 142, y, Component.translatable(Lang.MAIN_APPEARANCE), b -> openAppearance());
        y += 24;
        choice(left + 12, y, Component.translatable(Lang.MAIN_BYE), b -> { send("close", ""); onClose(); });
    }

    /** Roles that have a builder-placed site type (the gatherer has none yet). */
    private boolean hasSiteType() {
        return WorkerActions.isRole(role) && !"gatherer".equals(role);
    }

    private void openAppearance() {
        if (minecraft != null && minecraft.level != null && minecraft.level.getEntity(workerId) instanceof Worker worker) {
            minecraft.setScreen(new WorkerAppearanceScreen(worker, this));
        }
    }

    private void choice(int x, int y, Component label, Button.OnPress press) {
        addRenderableWidget(Button.builder(label, press).bounds(x, y, 252, 20).build());
    }

    private void half(int x, int y, Component label, Button.OnPress press) {
        addRenderableWidget(Button.builder(label, press).bounds(x, y, 122, 20).build());
    }

    /** A real checkbox; the server's reply (status flags) rebuilds the page, so it always shows the saved state. */
    private void checkbox(int x, int y, String flag, Component label, String action, String arg) {
        addRenderableWidget(net.minecraft.client.gui.components.Checkbox.builder(label, font)
            .pos(x, y)
            .selected(flags.contains(flag))
            .onValueChange((box, value) -> send(action, arg))
            .build());
    }

    private void buildWork(int left, int top) {
        int y = contentTop();
        addRenderableWidget(Button.builder(Component.translatable(working ? Lang.WORK_PAUSE : Lang.WORK_START),
            b -> send(working ? "pause" : "start", "")).bounds(left + 12, y, 122, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(Lang.WORK_HOME), b -> send("home", ""))
            .bounds(left + 142, y, 122, 20).build());
        y += 24;
        if (flags.contains("site")) {
            // The site sets the work area; zone drawing comes back once the site is released.
            addRenderableWidget(Button.builder(Component.translatable(Lang.WORK_SITE_MANAGE), b -> send("sites", ""))
                .tooltip(Tooltip.create(Component.translatable(Lang.WORK_SITE_TIP))).bounds(left + 12, y, 252, 20).build());
        } else {
            boolean miner = "miner".equals(role);
            addRenderableWidget(Button.builder(Component.translatable(miner ? Lang.WORK_CANCEL_DIG : Lang.WORK_CLEAR_AREA), b -> send("clear_assign", "area"))
                .bounds(left + 12, y, 122, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(miner ? Lang.WORK_REDRAW_DIG : Lang.WORK_REDRAW_AREA), b -> send("redraw_area", ""))
                .bounds(left + 142, y, 122, 20).build());
        }
        y += 28;
        choice(left + 12, y, Component.translatable(Lang.WORK_CHANGE_JOB), b -> { page = Page.ROLE; rebuild(); });
        y += 28;
        if ("miner".equals(role)) {
            List<String> modes = Lang.MINER_MODES;
            for (int i = 0; i < modes.size(); i++) {
                String m = modes.get(i);
                Component label = Component.literal(m.equals(mode) ? "● " : "").append(Component.translatable(Lang.modeKey(m)));
                int x = left + 12 + (i % 4) * 63;
                int my = y + (i / 4) * 22;
                addRenderableWidget(Button.builder(label, b -> send("set_mode", m))
                    .tooltip(Tooltip.create(Component.translatable(Lang.modeTipKey(m))))
                    .bounds(x, my, 60, 20).build());
            }
            y += 48;
            if ("terraform".equals(mode) || "quarry".equals(mode)) {
                Component depthLabel = digDepth < 0 ? Component.translatable(Lang.DEPTH_BEDROCK)
                    : digDepth > 0 ? Component.translatable(Lang.DEPTH_BLOCKS, digDepth) : Component.translatable(Lang.DEPTH_SET);
                addRenderableWidget(Button.builder(depthLabel, b -> {
                    page = Page.DEPTH; rebuild();
                }).bounds(left + 12, y, 252, 20).build());
                y += 24;
            }
            if ("staircase".equals(mode) || "strip".equals(mode) || "colony".equals(mode)) {
                addRenderableWidget(Button.builder(Component.translatable(Lang.TARGET_Y_SET), b -> {
                    page = Page.TARGET_Y; rebuild();
                }).bounds(left + 12, y, 252, 20).build());
                y += 24;
            }
        }
        addBack(left, y);
    }

    private void buildRole(int left, int top) {
        int y = contentTop();
        java.util.List<String> roles = WorkerActions.ROLES;
        for (int i = 0; i < roles.size(); i++) {
            String r = roles.get(i);
            int x = left + 12 + (i % 2) * 130;
            int ry = y + (i / 2) * 22;
            String mark = r.equals(role) ? "● " : "";
            addRenderableWidget(Button.builder(Component.literal(mark).append(Component.translatable(Lang.roleKey(r))), b -> {
                pendingRole = r; page = Page.ROLE_CONFIRM; rebuild();
            }).bounds(x, ry, 122, 20).build());
            icon(roleIconOf(r), x + 3, ry + 2);
        }
        y += ((roles.size() + 1) / 2) * 22 + 8;
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_BACK), b -> { page = Page.WORK; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
    }

    private void buildProtect(int left, int top) {
        int y = contentTop();
        choice(left + 12, y, Component.translatable(Lang.PROTECT_FOLLOW), b -> send("set_companion", "follow"));
        y += 24;
        choice(left + 12, y, Component.translatable(Lang.PROTECT_GUARD), b -> send("set_companion", "guard"));
        y += 24;
        choice(left + 12, y, Component.translatable(Lang.PROTECT_STAY), b -> send("set_companion", "stay"));
        y += 28;
        addBack(left, y);
    }

    /** Resources page: the farmer's crops or the gatherer's plants, same toggles underneath. */
    private void buildCrops(int left, int top) {
        int y = contentTop();
        boolean gatherer = "gatherer".equals(role);
        java.util.List<String> ids = WorkerActions.resourcesFor(gatherer ? "gatherer" : "farmer");
        for (String id : ids) {
            String cropKey = Lang.cropKey(id);
            Component label = cropKey == null ? Component.literal(id.replace('_', ' ')) : Component.translatable(cropKey);
            checkbox(left + 12, y, "crop:" + id, label, "toggle_crop", id);
            y += 22;
        }
        if (!gatherer) {
            checkbox(left + 12, y, "bonemeal", Component.translatable(Lang.BONEMEAL), "toggle_bonemeal", "");
            y += 22;
        }
        addBack(left, y + 6);
    }

    private void buildRanch(int left, int top) {
        int y = contentTop();
        checkbox(left + 12, y, "rancher:sheep", Component.translatable(Lang.RANCH_SHEEP), "toggle_ranch_animal", "sheep");
        y += 20;
        checkbox(left + 12, y, "rancher:cow", Component.translatable(Lang.RANCH_COW), "toggle_ranch_animal", "cow");
        y += 20;
        checkbox(left + 12, y, "rancher:pig", Component.translatable(Lang.RANCH_PIG), "toggle_ranch_animal", "pig");
        y += 20;
        checkbox(left + 12, y, "rancher:chicken", Component.translatable(Lang.RANCH_CHICKEN), "toggle_ranch_animal", "chicken");
        y += 24;
        checkbox(left + 12, y, "breed", Component.translatable(Lang.RANCH_BREED), "toggle_ranch_flag", "breed");
        y += 20;
        checkbox(left + 12, y, "shear", Component.translatable(Lang.RANCH_SHEAR), "toggle_ranch_flag", "shear");
        y += 20;
        checkbox(left + 12, y, "milk", Component.translatable(Lang.RANCH_MILK), "toggle_ranch_flag", "milk");
        y += 20;
        checkbox(left + 12, y, "eggs", Component.translatable(Lang.RANCH_EGGS), "toggle_ranch_flag", "eggs");
        y += 20;
        checkbox(left + 12, y, "honey", Component.translatable(Lang.RANCH_HONEY), "toggle_ranch_flag", "honey");
        y += 24;
        addBack(left, y);
    }

    /** One pickup chest per row: its filter (click with an item in hand to add it), clear, keep N, remove. */
    private void buildRoutes(int left, int top) {
        int y = contentTop();
        boolean porter = flags.contains("courier:porter");
        addRenderableWidget(Button.builder(Component.translatable(porter ? Lang.ROUTE_MODE_PORTER : Lang.ROUTE_MODE_ZONE),
                b -> send("set_mode", porter ? "zone" : "porter"))
            .tooltip(Tooltip.create(Component.translatable(Lang.ROUTE_MODE_TIP)))
            .bounds(left + 12, y, 252, 20).build());
        y += 24;
        if (porter) {
            addRenderableWidget(Button.builder(Component.translatable(Lang.ROUTE_PORTER_NOTE), b -> {})
                .bounds(left + 12, y, 252, 20).build());
            y += 28;
            addBack(left, Math.min(y, contentBottom()));
            return;
        }
        java.util.List<String[]> pickups = pickupRows();
        for (String[] row : pickups) {
            String index = row[0];
            String pos = row[1];
            int keep = Integer.parseInt(row[2]);
            String filter = row[3];
            String filterLabel = filter.isEmpty() ? Component.translatable(Lang.ROUTE_ANYTHING).getString() : filterNames(filter);
            String firstId = filter.isEmpty() ? "" : filter.split(",")[0];
            if (!firstId.isEmpty()) itemIcon(firstId, left + 14, y + 2);
            addRenderableWidget(Button.builder(Component.literal((firstId.isEmpty() ? "" : "   ")
                    + "#" + (Integer.parseInt(index) + 1) + " " + trim(filterLabel, 10)),
                b -> send("toggle_pickup_filter", index))
                .tooltip(Tooltip.create(Component.translatable(Lang.ROUTE_CHEST_TIP, pos, filterLabel)))
                .bounds(left + 12, y, 118, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(Lang.D_ANY), b -> send("clear_pickup_filter", index))
                .tooltip(Tooltip.create(Component.translatable(Lang.ROUTE_CLEAR_TIP)))
                .bounds(left + 132, y, 34, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(Lang.ROUTE_KEEP, keep),
                b -> send("cycle_pickup_keep", index))
                .tooltip(Tooltip.create(Component.translatable(Lang.ROUTE_KEEP_TIP)))
                .bounds(left + 170, y, 60, 20).build());
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> send("remove_pickup", index))
                .tooltip(Tooltip.create(Component.translatable(Lang.ROUTE_REMOVE_TIP)))
                .bounds(left + 234, y, 30, 20).build());
            y += 22;
        }
        if (pickups.isEmpty()) y += 22;
        addRenderableWidget(Button.builder(Component.translatable(Lang.ROUTE_ADD), b -> send("start_assign", "pickup"))
            .bounds(left + 12, y, 252, 20).build());
        y += 24;
        addBack(left, Math.min(y, contentBottom()));
    }

    /** Cook options: feed hungry workmates, bake bread. */
    private void buildCook(int left, int top) {
        int y = contentTop();
        checkbox(left + 12, y, "feedworkers", Component.translatable(Lang.COOK_FEED), "toggle_cook_flag", "feed");
        y += 20;
        checkbox(left + 12, y, "craftmeals", Component.translatable(Lang.COOK_MEALS), "toggle_cook_flag", "meals");
        y += 26;
        addRenderableWidget(Button.builder(Component.translatable(Lang.COOK_NOTE), b -> {})
            .bounds(left + 12, y, 252, 20).build());
        y += 28;
        addBack(left, y);
    }

    /** Stonemason orders: what to cut and how far along it is; order with the block in hand. */
    private void buildOrders(int left, int top) {
        int y = contentTop() + 12;
        java.util.List<String[]> rows = new ArrayList<>();
        for (String flag : flags) {
            if (!flag.startsWith("order:")) continue;
            String[] parts = flag.substring("order:".length()).split("\\|", -1);
            if (parts.length >= 5) rows.add(parts);
        }
        rows.sort(java.util.Comparator.comparingInt(r -> Integer.parseInt(r[0])));
        int shown = 0;
        for (String[] row : rows) {
            if (shown++ >= 4) break;
            String index = row[0];
            boolean auto = "1".equals(row[4]);
            String label = "   " + filterNames(row[1]) + "  " + row[2] + "/" + row[3];
            itemIcon(row[1], left + 14, y + 2);
            addRenderableWidget(Button.builder(Component.literal(trim(label, 30)).append(auto ? Component.translatable(Lang.ORDERS_AUTO) : Component.empty()), b -> {})
                .tooltip(Tooltip.create(Component.translatable(Lang.ORDERS_CUT_TIP, row[2], row[3], filterNames(row[1]))))
                .bounds(left + 12, y, 216, 20).build());
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> send("remove_order", index))
                .bounds(left + 232, y, 32, 20).build());
            y += 22;
        }
        if (rows.isEmpty()) y += 22;
        y += 4;
        int[] counts = {16, 64, 256};
        for (int i = 0; i < counts.length; i++) {
            String n = String.valueOf(counts[i]);
            addRenderableWidget(Button.builder(Component.translatable(Lang.ORDERS_ADD, n), b -> send("add_order", n))
                .tooltip(Tooltip.create(Component.translatable(Lang.ORDERS_ADD_TIP)))
                .bounds(left + 12 + i * 86, y, 80, 20).build());
        }
        y += 24;
        checkbox(left + 12, y, "supplybuilders", Component.translatable(Lang.ORDERS_SUPPLY_BUILDERS), "toggle_supply_builders", "");
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable(Lang.ORDERS_CLEAR), b -> send("clear_orders", ""))
            .bounds(left + 12, y, 122, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_BACK), b -> { page = Page.MAIN; rebuild(); })
            .bounds(left + 142, y, 122, 20).build());
    }

    /** Toolsmith stock: how many spare tools of each kind to keep in the output chest. */
    private void buildTools(int left, int top) {
        int y = contentTop();
        java.util.Map<String, String> targets = new java.util.HashMap<>();
        for (String flag : flags) {
            if (!flag.startsWith("tool:")) continue;
            String[] parts = flag.substring("tool:".length()).split("\\|", -1);
            if (parts.length == 2) targets.put(parts[0], parts[1]);
        }
        java.util.List<String> keys = ToolsmithJobs.TOOL_KEYS;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String n = targets.getOrDefault(key, "1");
            String sample = switch (key) {
                case "shears", "fishing_rod" -> "minecraft:" + key;
                default -> "minecraft:iron_" + key;
            };
            int x = left + 12 + (i % 2) * 130;
            int ty = y + (i / 2) * 22;
            itemIcon(sample, x + 2, ty + 2);
            addRenderableWidget(Button.builder(Component.literal("   ").append(Component.translatable(Lang.TOOLS_KEEP,
                    Component.translatable(Lang.toolKey(key)), n)), b -> send("cycle_tool_target", key))
                .tooltip(Tooltip.create(Component.translatable(Lang.TOOLS_TIP)))
                .bounds(x, ty, 122, 20).build());
        }
        y += 3 * 22 + 6;
        addRenderableWidget(Button.builder(Component.translatable(Lang.TOOLS_INFO), b -> {})
            .bounds(left + 12, y, 252, 20).build());
        y += 24;
        addBack(left, Math.min(y, contentBottom()));
    }

    /** The request board: what the owner asked for and how far along it is; ask with an item in hand. */
    private void buildRequests(int left, int top) {
        int y = contentTop() + 12;
        java.util.List<String[]> rows = new ArrayList<>();
        for (String flag : flags) {
            if (!flag.startsWith("req:")) continue;
            String[] parts = flag.substring("req:".length()).split("\\|", -1);
            if (parts.length >= 4) rows.add(parts);
        }
        rows.sort(java.util.Comparator.comparingInt(r -> Integer.parseInt(r[0])));
        for (String[] row : rows) {
            String index = row[0];
            String label = "   " + filterNames(row[1]) + "  " + row[2] + "/" + row[3];
            itemIcon(row[1], left + 14, y + 2);
            addRenderableWidget(Button.builder(Component.literal(trim(label, 34)), b -> {})
                .tooltip(Tooltip.create(Component.translatable(Lang.REQ_COLLECTED_TIP, row[2], row[3], filterNames(row[1]))))
                .bounds(left + 12, y, 216, 20).build());
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> send("remove_request", index))
                .bounds(left + 232, y, 32, 20).build());
            y += 22;
        }
        if (rows.isEmpty()) y += 22;
        y += 4;
        int[] counts = {16, 64, 256};
        for (int i = 0; i < counts.length; i++) {
            String n = String.valueOf(counts[i]);
            addRenderableWidget(Button.builder(Component.translatable(Lang.REQ_ADD, n), b -> send("add_request", n))
                .tooltip(Tooltip.create(Component.translatable(Lang.REQ_ADD_TIP)))
                .bounds(left + 12 + i * 86, y, 80, 20).build());
        }
        y += 24;
        checkbox(left + 12, y, "reqstop", Component.translatable(Lang.REQ_STOP), "toggle_request_stop", "");
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable(Lang.REQ_CLEAR), b -> send("clear_requests", ""))
            .bounds(left + 12, y, 122, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_BACK), b -> { page = Page.MAIN; rebuild(); })
            .bounds(left + 142, y, 122, 20).build());
    }

    private java.util.List<String[]> pickupRows() {
        java.util.List<String[]> rows = new ArrayList<>();
        for (String flag : flags) {
            if (!flag.startsWith("pickup:")) continue;
            String[] parts = flag.substring("pickup:".length()).split("\\|", -1);
            if (parts.length < 4) continue;
            rows.add(parts);
        }
        rows.sort(java.util.Comparator.comparingInt(r -> Integer.parseInt(r[0])));
        return rows;
    }

    private static String filterNames(String ids) {
        java.util.List<String> names = new ArrayList<>();
        for (String id : ids.split(",")) {
            if (id.isBlank()) continue;
            ResourceLocation key = ResourceLocation.tryParse(id);
            var item = key == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(key).orElse(null);
            names.add(item == null ? id : new net.minecraft.world.item.ItemStack(item).getHoverName().getString());
        }
        return String.join(", ", names);
    }

    private void buildCullConfirm(int left, int top) {
        int y = contentTop() + 40;
        choice(left + 12, y, Component.translatable(Lang.CULL_SWORD), b -> {
            send("start_cull_sword", "");
            page = Page.MAIN;
            rebuild();
        });
        y += 24;
        choice(left + 12, y, Component.translatable(Lang.CULL_NO_SWORD), b -> {
            send("start_cull", "");
            page = Page.MAIN;
            rebuild();
        });
        y += 24;
        choice(left + 12, y, Component.translatable(Lang.D_CANCEL), b -> { page = Page.MAIN; rebuild(); });
    }

    private void buildTargetY(int left, int top) {
        int y = contentTop() + 8;
        for (int target : new int[]{60, 32, 16, 0, -32, -58}) {
            int t = target;
            addRenderableWidget(Button.builder(Component.translatable(Lang.TARGET_Y_OPTION, t), b -> {
                send("set_target_y", String.valueOf(t)); page = Page.WORK; rebuild();
            }).bounds(left + 12, y, 252, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_BACK), b -> { page = Page.WORK; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
    }

    private void buildBiome(int left, int top) {
        int y = contentTop();
        for (String biome : VillageCatalog.biomes()) {
            String b = biome;
            String mark = b.equals(villageBiome) ? "● " : "";
            addRenderableWidget(Button.builder(Component.literal(mark).append(Component.translatable(Lang.biomeKey(b))), btn -> {
                villageBiome = b; page = Page.BUILD; rebuild();
            }).bounds(left + 12, y, 252, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_BACK), b -> { page = Page.BUILD; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
    }

    private void buildLocations(int left, int top) {
        int y = contentTop();
        if (flags.contains("site")) {
            // Site workers only choose a bed; the site supplies area and storage.
            y = assignRow(left, y, Component.translatable(Lang.LOC_BED), "bed", hasBed);
            choice(left + 12, y, Component.translatable(Lang.LOC_SITE), b -> send("sites", ""));
            addBack(left, y + 28);
            return;
        }
        if ("knight".equals(role) || "archer".equals(role)) {
            y = assignRow(left, y, Component.translatable(Lang.LOC_GUARD), "area", hasArea);
            y = assignRow(left, y, Component.translatable(Lang.LOC_BED), "bed", hasBed);
            addBack(left, y);
            return;
        }
        if ("smelter".equals(role)) {
            y = assignRow(left, y, Component.translatable(Lang.LOC_FURNACE), "furnace", false);
            addRenderableWidget(Button.builder(Component.translatable(Lang.LOC_CLEAR_FURNACES), b -> send("clear_assign", "furnace"))
                .bounds(left + 12, y, 252, 20).build());
            y += 24;
            y = assignRow(left, y, Component.translatable(Lang.LOC_SUPPLY), "supply", hasSupply);
            y = assignRow(left, y, Component.translatable(Lang.LOC_OUTPUT), "output", hasOutput);
            y = assignRow(left, y, Component.translatable(Lang.LOC_BED), "bed", hasBed);
            addBack(left, y);
            return;
        }
        if ("cook".equals(role)) {
            y = assignRow(left, y, Component.translatable(Lang.LOC_SMOKER), "furnace", false);
            addRenderableWidget(Button.builder(Component.translatable(Lang.LOC_CLEAR_FURNACES), b -> send("clear_assign", "furnace"))
                .bounds(left + 12, y, 252, 20).build());
            y += 24;
            y = assignRow(left, y, Component.translatable(Lang.LOC_SUPPLY), "supply", hasSupply);
            y = assignRow(left, y, Component.translatable(Lang.LOC_OUTPUT), "output", hasOutput);
            y = assignRow(left, y, Component.translatable(Lang.LOC_TABLE), "table", hasTable);
            y = assignRow(left, y, Component.translatable(Lang.LOC_BED), "bed", hasBed);
            addBack(left, y);
            return;
        }
        if ("stonemason".equals(role)) {
            y = assignRow(left, y, Component.translatable(Lang.LOC_STONECUTTER), "stonecutter", flags.contains("stonecutter"));
            y = assignRow(left, y, Component.translatable(Lang.LOC_SUPPLY), "supply", hasSupply);
            y = assignRow(left, y, Component.translatable(Lang.LOC_OUTPUT), "output", hasOutput);
            y = assignRow(left, y, Component.translatable(Lang.LOC_BED), "bed", hasBed);
            addBack(left, y);
            return;
        }
        if ("toolsmith".equals(role)) {
            y = assignRow(left, y, Component.translatable(Lang.LOC_TABLE), "table", hasTable);
            y = assignRow(left, y, Component.translatable(Lang.LOC_ANVIL), "anvil", flags.contains("anvil"));
            y = assignRow(left, y, Component.translatable(Lang.LOC_GRINDSTONE), "grindstone", flags.contains("grindstone"));
            y = assignRow(left, y, Component.translatable(Lang.LOC_SUPPLY), "supply", hasSupply);
            y = assignRow(left, y, Component.translatable(Lang.LOC_OUTPUT), "output", hasOutput);
            y = assignRow(left, y, Component.translatable(Lang.LOC_BED), "bed", hasBed);
            addBack(left, y);
            return;
        }
        if ("courier".equals(role)) {
            y = assignRow(left, y, Component.translatable(Lang.LOC_PICKUP), "pickup", false);
            addRenderableWidget(Button.builder(Component.translatable(Lang.LOC_CLEAR_PICKUPS), b -> send("clear_assign", "pickup"))
                .bounds(left + 12, y, 252, 20).build());
            y += 24;
            y = assignRow(left, y, Component.translatable(Lang.LOC_DROPOFF), "output", hasOutput);
            y = assignRow(left, y, Component.translatable(Lang.LOC_SUPPLY_FURNACE), "supply", hasSupply);
            y = assignRow(left, y, Component.translatable(Lang.LOC_FEED), "furnace", false);
            addRenderableWidget(Button.builder(Component.translatable(Lang.LOC_CLEAR_FURNACES), b -> send("clear_assign", "furnace"))
                .bounds(left + 12, y, 252, 20).build());
            y += 24;
            y = assignRow(left, y, Component.translatable(Lang.LOC_BED), "bed", hasBed);
            addBack(left, y);
            return;
        }
        if (!"builder".equals(role)) {
            y = assignRow(left, y, workAreaLabel(), "area", hasArea);
        }
        y = assignRow(left, y, Component.translatable(Lang.LOC_BED), "bed", hasBed);
        y = assignRow(left, y, Component.translatable(Lang.LOC_SUPPLY), "supply", hasSupply);
        y = assignRow(left, y, Component.translatable(Lang.LOC_OUTPUT), "output", hasOutput);
        if ("builder".equals(role) || "miner".equals(role) || "farmer".equals(role) || "forester".equals(role)) {
            y = assignRow(left, y, Component.translatable(Lang.LOC_TABLE), "table", hasTable);
        }
        addBack(left, y);
    }

    private Component workAreaLabel() {
        return Component.translatable(Lang.areaKey(role));
    }

    private int assignRow(int left, int y, Component label, String kind, boolean set) {
        addRenderableWidget(Button.builder(Component.translatable(set ? Lang.LOC_CHANGE : Lang.LOC_ASSIGN, label),
            b -> send("start_assign", kind)).bounds(left + 12, y, 176, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_CLEAR), b -> {
            if (!set) {
                infoMessage = Component.translatable(Lang.LOC_NOT_ASSIGNED, label);
                page = Page.MESSAGE; rebuild();
            } else send("clear_assign", kind);
        }).bounds(left + 194, y, 70, 20).build());
        return y + 24;
    }

    private void buildRecipes(int left, int top) {
        if (searchBox == null) ensureTextBoxes(left);
        addRenderableWidget(searchBox);
        searchBox.setHint(Component.translatable(Lang.RECIPES_HINT));
        String query = searchBox.getValue().toLowerCase(Locale.ROOT);
        int y = contentTop() + 36;
        int shown = 0;
        int skipped = 0;
        // Up to five rows, fewer when the screen is short so the scroll row stays visible.
        int rows = Math.max(1, Math.min(5, (contentBottom() - y - 24) / 22));
        for (WorkerNetwork.RecipeEntry entry : recipes) {
            if (!query.isEmpty() && !entry.outputName().toLowerCase(Locale.ROOT).contains(query)
                && !entry.id().toLowerCase(Locale.ROOT).contains(query)) continue;
            if (skipped++ < recipeScroll) continue;
            if (shown >= rows) break;
            String prefix = entry.approved() ? "[OK] " : entry.supported() ? "[ ] " : "[X] ";
            String text = prefix + entry.outputName();
            WorkerNetwork.RecipeEntry captured = entry;
            addRenderableWidget(Button.builder(Component.literal(trim(text, 34)), b -> {
                if (!captured.supported()) {
                    infoMessage = captured.reason().isEmpty()
                        ? Component.translatable(Lang.RECIPES_CANNOT) : Component.literal(captured.reason());
                    page = Page.MESSAGE; rebuild();
                } else if (captured.approved()) send("remove_recipe", captured.id());
                else send("approve_recipe", captured.id());
            }).bounds(left + 12, y, 252, 20).build());
            y += 22;
            shown++;
        }
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_UP), b -> {
            recipeScroll = Math.max(0, recipeScroll - 5); rebuild();
        }).bounds(left + 12, y, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_DOWN), b -> {
            recipeScroll += 5; rebuild();
        }).bounds(left + 88, y, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_BACK), b -> { page = Page.MAIN; rebuild(); })
            .bounds(left + 164, y, 100, 20).build());
    }

    private void buildBuild(int left, int top) {
        int y = contentTop();
        addRenderableWidget(Button.builder(Component.translatable(Lang.BUILD_SITES), b -> send("sites", ""))
            .tooltip(Tooltip.create(Component.translatable(Lang.BUILD_SITES_TIP))).bounds(left + 12, y, 252, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable(Lang.BUILD_STYLE, Component.translatable(Lang.biomeKey(villageBiome))), b -> {
            page = Page.BIOME; rebuild();
        }).bounds(left + 12, y, 122, 20).build());
        String prep = sitePrep();
        String prepLabel = switch (prep) {
            case "flatten" -> Lang.BUILD_GROUND_FLATTEN;
            case "none" -> Lang.BUILD_GROUND_OFF;
            default -> Lang.BUILD_GROUND_CLEAR;
        };
        String nextPrep = switch (prep) {
            case "clear" -> "flatten";
            case "flatten" -> "none";
            default -> "clear";
        };
        addRenderableWidget(Button.builder(Component.translatable(prepLabel), b -> send("set_siteprep", nextPrep))
            .tooltip(Tooltip.create(Component.translatable(switch (prep) {
                case "flatten" -> Lang.BUILD_TIP_FLATTEN;
                case "none" -> Lang.BUILD_TIP_OFF;
                default -> Lang.BUILD_TIP_CLEAR;
            })))
            .bounds(left + 142, y, 122, 20).build());
        y += 24;
        boolean flattening = flags.contains("flattening");
        addRenderableWidget(Button.builder(Component.translatable(flattening ? Lang.BUILD_FLATTENING
                : hasArea ? Lang.BUILD_PLOT_MARKED : Lang.BUILD_MARK_PLOT),
            b -> {
                if (flattening) { page = Page.WORK; rebuild(); }
                else if (hasArea) send("build_on_plot", "");
                else send("start_village_plot", "");
            }).bounds(left + 12, y, 252, 20).build());
        y += 24;
        if (hasArea) {
            addRenderableWidget(Button.builder(Component.translatable(Lang.BUILD_REMAKE), b -> send("start_village_plot", ""))
                .bounds(left + 12, y, 80, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(Lang.BUILD_CLEAR_PLOT), b -> send("clear_assign", "area"))
                .bounds(left + 98, y, 80, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(Lang.BUILD_FLATTEN), b -> send("flatten_plot", ""))
                .tooltip(Tooltip.create(Component.translatable(Lang.BUILD_FLATTEN_TIP)))
                .bounds(left + 184, y, 80, 20).build());
            y += 24;
        }
        java.util.List<VillageCatalog.Entry> presets = VillageCatalog.forBiome(villageBiome);
        int shown = 0;
        for (int i = 0; i < presets.size() && shown < 6; i++) {
            VillageCatalog.Entry entry = presets.get(i);
            String preset = entry.id();
            String label = (preset.equals(blueprint) ? "● " : "") + trim(entry.label(), 18);
            int x = left + 12 + (shown % 2) * 130;
            int by = y + (shown / 2) * 22;
            addRenderableWidget(Button.builder(Component.literal(label), b -> send("load_preset", preset))
                .bounds(x, by, 122, 20).build());
            shown++;
        }
        y += Math.max(1, (shown + 1) / 2) * 22 + 4;
        addRenderableWidget(Button.builder(Component.translatable(Lang.BUILD_AUTO), b -> {
            send("load_preset", "plains_small_house");
            if (hasArea) send("build_on_plot", "");
            else send("start_village_plot", "");
        }).bounds(left + 12, y, 252, 20).build());
        y += 28;
        addBack(left, y);
    }

    private void buildBlueprint(int left, int top) {
        if (nameBox == null) ensureTextBoxes(left);
        addRenderableWidget(nameBox);
        nameBox.setHint(Component.translatable(Lang.BP_NAME));
        int y = contentTop() + 20;
        addRenderableWidget(Button.builder(Component.translatable(Lang.BP_STEP1), b -> send("start_assign", "copy_area"))
            .bounds(left + 12, y, 252, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable(Lang.BP_STEP2), b -> {
            String name = nameBox.getValue().trim();
            if (name.isEmpty()) {
                infoMessage = Component.translatable(Lang.BP_NEED_NAME);
                page = Page.MESSAGE; rebuild();
                return;
            }
            if (!hasArea) {
                infoMessage = Component.translatable(Lang.BP_NEED_AREA);
                page = Page.MESSAGE; rebuild();
                return;
            }
            pendingCaptureName = name;
            if (blueprints.contains(name)) { page = Page.CAPTURE_CONFIRM; rebuild(); }
            else send("capture_blueprint", name);
        }).bounds(left + 12, y, 252, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable(Lang.BP_MATERIALS), b -> send("materials", ""))
            .bounds(left + 12, y, 122, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(Lang.BP_PREVIEW), b -> send("preview", ""))
            .bounds(left + 142, y, 122, 20).build());
        y += 22;
        for (int rot : new int[]{0, 1, 2, 3}) {
            int degrees = rot * 90;
            String label = (rotation == rot ? "● " : "") + degrees + "°";
            addRenderableWidget(Button.builder(Component.literal(label), b -> send("set_rotation", String.valueOf(rot)))
                .bounds(left + 12 + rot * 64, y, 60, 20).build());
        }
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable(Lang.BP_STEP3), b -> send("start_assign", "origin"))
            .bounds(left + 12, y, 122, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(Lang.BP_CLEAR_SPOT), b -> send("clear_assign", "origin"))
            .bounds(left + 142, y, 122, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable(Lang.BP_STEP4), b -> {
            if (blueprint == null || blueprint.isEmpty() || BuilderPresets.isPreset(blueprint)) {
                infoMessage = Component.translatable(Lang.BP_NEED_CUSTOM);
                page = Page.MESSAGE; rebuild();
            } else if (!hasOrigin) {
                infoMessage = Component.translatable(Lang.BP_NEED_SPOT);
                page = Page.MESSAGE; rebuild();
            } else send("start", "");
        }).bounds(left + 12, y, 252, 20).build());
        y += 22;
        int shown = 0;
        // Up to three rows that fit above Back; the mouse wheel scrolls through every saved blueprint.
        List<String> saved = blueprints.stream().filter(n -> !BuilderPresets.isPreset(n)).toList();
        int rows = Math.max(1, Math.min(3, (contentBottom() - y - 4) / 22));
        blueprintScroll = Math.max(0, Math.min(blueprintScroll, saved.size() - rows));
        for (String name : saved.subList(Math.min(blueprintScroll, saved.size()), saved.size())) {
            if (shown >= rows) break;
            String label = (name.equals(blueprint) ? "● " : "") + name;
            addRenderableWidget(Button.builder(Component.translatable(Lang.BP_LOAD, trim(label, 24)), b -> send("load_blueprint", name))
                .bounds(left + 12, y, 252, 20).build());
            y += 22;
            shown++;
        }
        addBack(left, Math.min(y, contentBottom()));
    }

    private void buildDepth(int left, int top) {
        int y = contentTop() + 8;
        for (int[] opt : new int[][]{{5, 5}, {10, 10}, {20, 20}, {-1, -1}}) {
            int depth = opt[0];
            Component label = depth < 0 ? Component.translatable(Lang.DEPTH_OPT_BEDROCK) : Component.translatable(Lang.DEPTH_OPT_BLOCKS, depth);
            addRenderableWidget(Button.builder(label, b -> {
                send("set_depth", String.valueOf(depth));
                page = Page.WORK;
                rebuild();
            }).bounds(left + 12, y, 252, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_CANCEL), b -> {
            if (hasArea || digDepth != 0) {
                page = Page.WORK;
                rebuild();
            } else {
                send("close", "");
                onClose();
            }
        }).bounds(left + 12, y, 252, 20).build());
    }

    private void buildRoleConfirm(int left, int top) {
        int y = contentTop() + 36;
        addRenderableWidget(Button.builder(Component.translatable(Lang.CONFIRM_ROLE, Component.translatable(Lang.roleKey(pendingRole))),
            b -> { send("confirm_role", pendingRole); page = Page.MAIN; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_CANCEL), b -> {
            pendingRole = ""; page = Page.MAIN; rebuild();
        }).bounds(left + 12, y, 252, 20).build());
    }

    private void buildCaptureConfirm(int left, int top) {
        int y = contentTop() + 36;
        addRenderableWidget(Button.builder(Component.translatable(Lang.CONFIRM_OVERWRITE, trim(pendingCaptureName, 18)),
            b -> { send("overwrite_blueprint", pendingCaptureName); page = Page.BLUEPRINT; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_CANCEL), b -> { page = Page.BLUEPRINT; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
    }

    private void buildMessage(int left, int top) {
        int y = contentTop() + 48;
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_BACK), b -> {
            page = Page.MAIN; rebuild();
        }).bounds(left + 12, y, 252, 20).build());
    }

    private String sitePrep() {
        for (String flag : flags) {
            if (flag.startsWith("siteprep:")) return flag.substring("siteprep:".length());
        }
        return "clear";
    }

    private void addBack(int left, int y) {
        addRenderableWidget(Button.builder(Component.translatable(Lang.D_BACK), b -> { page = Page.MAIN; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
    }

    private static String trim(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }

    private Component greeting() {
        if (status != null && (status.contains("Out of space") || status.contains(Lang.STATUS_FULL))) {
            return Component.translatable(Lang.STATUS_OUT_OF_SPACE);
        }
        // What the worker is doing now lives in the status strip; the speech line stays in character.
        return Component.translatable(Lang.askKey(role));
    }

    private int roleColor() {
        return switch (role) {
            case "farmer" -> 0xFF8BC34A;
            case "forester" -> 0xFF43A047;
            case "miner" -> 0xFF90A4AE;
            case "builder" -> 0xFFE07A3D;
            case "knight" -> 0xFF5C6BC0;
            case "archer" -> 0xFF7CB342;
            case "rancher" -> 0xFFA1887F;
            case "fisher" -> 0xFF4FC3F7;
            case "smelter" -> 0xFFFF7043;
            case "courier" -> 0xFFBA68C8;
            case "gatherer" -> 0xFF66BB6A;
            case "toolsmith" -> 0xFF78909C;
            case "stonemason" -> 0xFFB0A090;
            case "cook" -> 0xFFE0A050;
            default -> 0xFFBCAAA4;
        };
    }

    private ResourceLocation roleIcon() {
        String key = WorkerActions.isRole(role) ? role : "idle";
        return ResourceLocation.fromNamespaceAndPath(HelpfulWorkers.ID, "textures/gui/role_" + key + ".png");
    }

    private void drawWorkerFace(GuiGraphics graphics, int x, int y) {
        ResourceLocation skin = PORTRAIT;
        if (minecraft != null && minecraft.level != null) {
            var entity = minecraft.level.getEntity(workerId);
            if (entity != null) {
                // The look chosen on the Name & appearance screen (or the original default skin).
                skin = entity instanceof Worker worker ? WorkerSkins.resolve(worker).texture()
                    : net.minecraft.client.resources.DefaultPlayerSkin.get(entity.getUUID()).texture();
                graphics.blit(skin, x, y, 36, 36, 8, 8, 8, 8, 64, 64);
                graphics.blit(skin, x, y, 36, 36, 40, 8, 8, 8, 64, 64);
                return;
            }
        }
        graphics.blit(PORTRAIT, x, y, 0, 0, 36, 36, 32, 32);
    }

    private void send(String action, String arg) {
        PacketDistributor.sendToServer(new WorkerNetwork.DialogueActionPayload(workerId, action, arg == null ? "" : arg));
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
        graphics.fill(left, top, right, top + 3, roleColor());
        graphics.fill(left + 8, top + 10, left + 44, top + 46, 0xFF101318);
        drawWorkerFace(graphics, left + 8, top + 10);
        graphics.blit(roleIcon(), left + 46, top + 10, 0, 0, 16, 16, 16, 16);
        graphics.drawString(font, trim(workerName, 26), left + 66, top + 12, 0xFFFFFF, false);
        Component duty = Component.translatable(working ? Lang.DUTY_WORKING : Lang.DUTY_PAUSED, Component.translatable(Lang.roleKey(role)));
        graphics.drawString(font, duty, left + 66, top + 24, roleColor(), false);
        // Level and personality, right-aligned on the name row.
        String levelFlag = null, traitFlag = null;
        for (String f : flags) {
            if (f.startsWith("level:")) levelFlag = f.substring(6);
            else if (f.startsWith("trait:")) traitFlag = f.substring(6);
        }
        if (levelFlag != null) {
            Component tag = Component.translatable(Lang.LEVEL_SHORT, levelFlag.split("\\|")[0]);
            if (traitFlag != null) tag = Component.empty().append(tag).append(" · ").append(Component.translatable(Lang.traitKey(traitFlag)));
            graphics.drawString(font, tag, left + panelWidth() - 10 - font.width(tag), top + 12, 0xFFB0BEC5, false);
        }
        String line = greeting().getString();
        if (font.width(line) > 190) line = font.plainSubstrByWidth(line, 190) + "…";
        graphics.drawString(font, "“" + line + "”", left + 50, top + 36, 0xFFE7D7B0, false);
        if (page == Page.ROLE_CONFIRM) {
            graphics.drawWordWrap(font, Component.translatable(Lang.CONFIRM_ROLE_TEXT, Component.translatable(Lang.roleKey(pendingRole))), left + 12, contentTop(), 252, 0xFFFFFFFF);
        } else if (page == Page.CAPTURE_CONFIRM) {
            graphics.drawWordWrap(font, Component.translatable(Lang.CONFIRM_OVERWRITE_TEXT, pendingCaptureName),
                left + 12, contentTop(), 252, 0xFFFFFFFF);
        } else if (page == Page.MESSAGE) {
            graphics.drawWordWrap(font, infoMessage, left + 12, contentTop(), 252, 0xFFFF8A80);
        } else if (page == Page.DEPTH) {
            graphics.drawWordWrap(font, Component.translatable(Lang.DEPTH_TEXT),
                left + 12, contentTop() - 8, 252, 0xFFE7D7B0);
        } else if (page == Page.CULL_CONFIRM) {
            graphics.drawWordWrap(font, Component.translatable(Lang.CULL_TEXT),
                left + 12, contentTop(), 252, 0xFFE7D7B0);
        } else if (page == Page.ROLE) {
            graphics.drawWordWrap(font, Component.translatable(Lang.ROLE_TEXT),
                left + 12, contentTop() - 14, 252, 0xFFE7D7B0);
        } else if (page == Page.BUILD) {
            drawHint(graphics, Component.translatable(Lang.BUILD_TEXT), left);
        } else if (page == Page.BLUEPRINT) {
            drawHint(graphics, Component.translatable(Lang.BP_TEXT), left);
        } else if (page == Page.RECIPES) {
            graphics.drawString(font, Component.translatable(Lang.RECIPES_LEGEND), left + 12, contentTop() + 18, 0xFF90A4AE, false);
        } else if (page == Page.LOCATIONS) {
            graphics.drawString(font, Component.translatable(Lang.LOC_LEGEND), left + 12, contentTop() - 12, 0xFF90A4AE, false);
        } else if (page == Page.REQUESTS) {
            graphics.drawString(font, Component.translatable(Lang.REQ_NOTE), left + 12, contentTop() - 2, 0xFF90A4AE, false);
        } else if (page == Page.ROUTES) {
            graphics.drawString(font, Component.translatable(pickupRows().isEmpty() ? Lang.ROUTE_NONE : Lang.ROUTE_VISIT),
                left + 12, contentTop() - 2, 0xFF90A4AE, false);
        }
        drawStatusStrip(graphics, left, top);
        // Not super.render: that would blur the background again, over the header and status text.
        for (var widget : renderables) widget.render(graphics, mouseX, mouseY, partialTick);
        drawTabUnderline(graphics, top);
        for (Icon icon : icons) {
            if (icon.stack() != null) graphics.renderItem(icon.stack(), icon.x(), icon.y());
            else graphics.blit(icon.texture(), icon.x(), icon.y(), 0, 0, 16, 16, 16, 16);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    void suppressClosePacket() {
        suppressClosePacket = true;
    }

    @Override
    public void onClose() {
        if (!suppressClosePacket) send("close", "");
        super.onClose();
    }
}
