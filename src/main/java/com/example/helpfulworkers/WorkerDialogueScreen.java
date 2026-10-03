package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
        MAIN, MANAGE, WORK, ROLE, LOCATIONS, RECIPES, BUILD, BLUEPRINT, ROLE_CONFIRM, CAPTURE_CONFIRM,
        MESSAGE, DEPTH, PROTECT, CROPS, TARGET_Y, BIOME, RANCH, CULL_CONFIRM
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
    private String infoMessage = "";
    private EditBox searchBox;
    private EditBox nameBox;
    private final List<WorkerNetwork.RecipeEntry> recipes = new ArrayList<>();
    private final List<String> blueprints = new ArrayList<>();
    private final Set<String> flags = new HashSet<>();
    private int recipeScroll;
    private int sectionPage, sectionPages = 1;
    private Page lastPage;
    private String pendingCaptureName = "";
    private boolean suppressClosePacket;

    public WorkerDialogueScreen(WorkerNetwork.OpenDialoguePayload payload) {
        super(Component.literal("Worker"));
        applyOpen(payload);
        if (pendingRole != null && !pendingRole.isEmpty()) page = Page.ROLE_CONFIRM;
    }

    static WorkerDialogueScreen depthPicker(WorkerNetwork.OpenDepthPickerPayload payload) {
        WorkerDialogueScreen screen = new WorkerDialogueScreen(payload.workerId(), payload.name());
        screen.page = Page.DEPTH;
        return screen;
    }

    private WorkerDialogueScreen(int workerId, String name) {
        super(Component.literal("Worker"));
        this.workerId = workerId;
        this.workerName = name;
        this.role = "miner";
        this.mode = "terraform";
        this.status = "Choose dig depth";
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

    private int panelLeft() { return (this.width - panelWidth()) / 2; }
    private int panelWidth() { return Math.min(360, this.width - 16); }
    private int panelHeight() { return Math.min(this.height - 16, page == Page.MAIN || page == Page.MANAGE ? 230 : 350); }
    private int panelTop() { return Math.max(8, (this.height - panelHeight()) / 2); }
    private int contentTop() { return panelTop() + 100; }
    private int contentBottom() { return panelTop() + panelHeight() - 28; }

    private void ensureTextBoxes(int left) {
        if (searchBox == null) {
            searchBox = new EditBox(font, left + 12, contentTop() - 2, 252, 16, Component.literal("Search"));
            searchBox.setMaxLength(64);
            searchBox.setResponder(text -> {
                if (page == Page.RECIPES) {
                    recipeScroll = 0;
                    sectionPage = 0;
                    rebuild();
                }
            });
        } else {
            searchBox.setX(left + 12);
            searchBox.setY(contentTop() - 2);
        }
        if (nameBox == null) {
            nameBox = new EditBox(font, left + 12, contentTop() - 2, 252, 16, Component.literal("Name"));
            nameBox.setMaxLength(32);
        } else {
            nameBox.setX(left + 12);
            nameBox.setY(contentTop() - 2);
        }
    }

    private void rebuild() {
        boolean restoreSearchFocus = searchBox != null && searchBox.isFocused();
        clearWidgets();
        if (lastPage != page) { sectionPage = 0; lastPage = page; }
        int left = panelLeft() + (panelWidth() - 276) / 2;
        int top = panelTop();
        if (page == Page.RECIPES || page == Page.BUILD || page == Page.CAPTURE_CONFIRM || page == Page.BLUEPRINT) {
            ensureTextBoxes(left);
        }

        switch (page) {
            case MAIN -> buildMain(left, top);
            case MANAGE -> buildManage(left, top);
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
        }
        finishLayout();
        if (restoreSearchFocus && page == Page.RECIPES) setFocused(searchBox);
    }

    private void buildMain(int left, int top) {
        int y = contentTop();
        choice(left + 12, y, "builder".equals(role) ? "Build a worker site" : flags.contains("site") ? "Manage assigned " + siteName() : "Assign a " + siteName(), b -> send("sites", ""));
        choice(left + 12, y + 24, "builder".equals(role) ? "Construction projects" : jobTab() + " settings", b -> { page=Page.WORK; rebuild(); });
        choice(left + 12, y + 48, "Equipment & inventory", b -> send("open_inventory", ""));
        choice(left + 12, y + 72, working ? "Pause work" : "Resume work", b -> send(working ? "pause" : "start", ""));
    }

    private void buildManage(int left, int top) {
        int y=contentTop();
        choice(left+12,y,"Name & appearance",b->{
            if(minecraft.level != null && minecraft.level.getEntity(workerId) instanceof Worker worker)
                minecraft.setScreen(new WorkerAppearanceScreen(worker,this));
        }); y+=24;
        choice(left+12,y,"Change profession",b->{page=Page.ROLE;rebuild();}); y+=24;
        if (java.util.Set.of("builder","miner","farmer","forester").contains(role)) {
            choice(left+12,y,"Crafting recipes",b->{page=Page.RECIPES;recipeScroll=0;send("request_recipes","");rebuild();});y+=24;
        }
        choice(left+12,y,"Return to assigned bed",b->send("home",""));
    }

    private String jobTab() {
        return switch(role) {
            case "miner"->"Mining";case "forester"->"Logging";case "farmer"->"Farming";
            case "builder"->"Building";case "rancher"->"Ranching";case "fisher"->"Fishing";
            case "smelter"->"Smelting";case "courier"->"Deliveries";default->"Protection";
        };
    }
    private String siteName() {
        return switch(role) {
            case "miner"->"mining pit";case "forester"->"logging camp";case "farmer"->"crop farm";
            case "builder"->"worker site";case "rancher"->"ranch";case "fisher"->"fishing pond";
            case "smelter"->"smelter workshop";case "courier"->"warehouse";default->"guard tower";
        };
    }
    private void choice(int x, int y, String label, Button.OnPress press) {
        addRenderableWidget(Button.builder(Component.literal(label),press).bounds(x,y,252,20).build());
    }

    private void checkbox(int x, int y, String flag, String label, String action, String arg) {
        boolean on = flags.contains(flag);
        String text = (on ? "[✓] " : "[ ] ") + label;
        addRenderableWidget(Button.builder(Component.literal(text), b -> send(action, arg))
            .bounds(x, y, 252, 20).build());
    }

    private void buildWork(int left, int top) {
        int y = contentTop();
        if (flags.contains("site")) {
            choice(left + 12, y, "Manage " + siteName(), b -> send("sites", "")); y+=24;
            choice(left + 12, y, working ? "Pause work" : "Resume work", b -> send(working ? "pause" : "start", "")); y+=24;
            if(role.equals("farmer"))choice(left+12,y,"Crops & bone meal",b->{page=Page.CROPS;rebuild();});
            if(role.equals("rancher"))choice(left+12,y,"Animals & products",b->{page=Page.RANCH;rebuild();});
            if(role.equals("builder")) {
                choice(left+12,y,"Build another worker site",b->send("sites",""));
                choice(left+12,y+24,"Custom blueprints",b->{page=Page.BLUEPRINT;rebuild();});
            }
            return;
        }
        addRenderableWidget(Button.builder(Component.literal(working ? "Pause work" : "Start work"),
            b -> send(working ? "pause" : "start", "")).bounds(left + 12, y, 122, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Return home"), b -> send("home", ""))
            .bounds(left + 142, y, 122, 20).build());
        y += 24;
        if (!java.util.Set.of("builder","courier","smelter").contains(role)) {
            choice(left+12,y,(hasArea ? "Redraw " : "Mark ") + workAreaLabel().toLowerCase(Locale.ROOT),b->send("redraw_area",""));y+=24;
            if(hasArea) { choice(left+12,y,"Show " + workAreaLabel().toLowerCase(Locale.ROOT),b->send("show_outline",""));y+=24; }
        }
        switch(role) {
            case "farmer" -> { choice(left+12,y,"Crops & bone meal",b->{page=Page.CROPS;rebuild();}); y+=24; }
            case "rancher" -> {
                choice(left+12,y,"Animals & products",b->{page=Page.RANCH;rebuild();});y+=24;
                choice(left+12,y,"Manage herd size…",b->{page=Page.CULL_CONFIRM;rebuild();});y+=24;
            }
            case "knight", "archer" -> {choice(left+12,y,"Guard / follow / stay",b->{page=Page.PROTECT;rebuild();});y+=24;}
            case "builder" -> {
                choice(left+12,y,"Build a worker site",b->send("sites",""));y+=24;
                choice(left+12,y,"Village houses",b->{page=Page.BUILD;rebuild();});y+=24;
                choice(left+12,y,"Custom blueprints",b->{page=Page.BLUEPRINT;send("request_recipes","");rebuild();});y+=24;
            }
            case "courier" -> {choice(left+12,y,"Warehouse & delivery settings",b->send("sites",""));y+=24;}
            case "smelter" -> {choice(left+12,y,"Furnaces & storage",b->{page=Page.LOCATIONS;rebuild();});y+=24;}
        }
        if ("miner".equals(role)) {
            String[] modes = {"excavate", "branches", "terraform", "staircase", "strip", "colony"};
            String[] labels = {"Excavate", "Branches", "Terraform", "Staircase", "Strip", "Colony"};
            for (int i = 0; i < modes.length; i++) {
                String m = modes[i];
                String label = (m.equals(mode) ? "● " : "") + labels[i];
                int x = left + 12 + (i % 3) * 84;
                int my = y + (i / 3) * 22;
                addRenderableWidget(Button.builder(Component.literal(label), b -> send("set_mode", m))
                    .bounds(x, my, 80, 20).build());
            }
            y += 48;
            if ("terraform".equals(mode)) {
                String depthLabel = digDepth < 0 ? "Depth: bedrock"
                    : digDepth > 0 ? "Depth: " + digDepth + " blocks" : "Set dig depth…";
                addRenderableWidget(Button.builder(Component.literal(depthLabel), b -> {
                    page = Page.DEPTH; rebuild();
                }).bounds(left + 12, y, 252, 20).build());
                y += 24;
            }
            if ("staircase".equals(mode) || "strip".equals(mode) || "colony".equals(mode)) {
                addRenderableWidget(Button.builder(Component.literal("Set target Y…"), b -> {
                    page = Page.TARGET_Y; rebuild();
                }).bounds(left + 12, y, 252, 20).build());
                y += 24;
            }
        }
        addBack(left, y);
    }

    private void buildRole(int left, int top) {
        int y = contentTop();
        String[] roles = {"farmer", "forester", "miner", "builder", "knight", "archer", "rancher", "fisher", "smelter", "courier"};
        for (int i = 0; i < roles.length; i++) {
            String r = roles[i];
            int x = left + 12 + (i % 2) * 130;
            int ry = y + (i / 2) * 22;
            String mark = r.equals(role) ? "● " : "";
            addRenderableWidget(Button.builder(Component.literal(mark + WorkerActions.roleLabel(r)), b -> {
                pendingRole = r; page = Page.ROLE_CONFIRM; rebuild();
            }).bounds(x, ry, 122, 20).build());
        }
        y += ((roles.length + 1) / 2) * 22 + 8;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> { page = Page.WORK; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
    }

    private void buildProtect(int left, int top) {
        int y = contentTop();
        choice(left + 12, y, "Follow me", b -> send("set_companion", "follow"));
        y += 24;
        choice(left + 12, y, "Guard village (use work area)", b -> send("set_companion", "guard"));
        y += 24;
        choice(left + 12, y, "Stay here", b -> send("set_companion", "stay"));
        y += 28;
        addBack(left, y);
    }

    private void buildCrops(int left, int top) {
        int y = contentTop();
        String[] crops = {"wheat", "carrots", "potatoes", "beetroots", "melon", "pumpkin", "nether_wart"};
        for (String crop : crops) {
            checkbox(left + 12, y, "crop:" + crop, crop.replace('_', ' '), "toggle_crop", crop);
            y += 22;
        }
        checkbox(left + 12, y, "bonemeal", "Use bone meal", "toggle_bonemeal", "");
        y += 28;
        addBack(left, y);
    }

    private void buildRanch(int left, int top) {
        int y = contentTop();
        checkbox(left + 12, y, "rancher:sheep", "Sheep (shear / breed wheat)", "toggle_ranch_animal", "sheep");
        y += 22;
        checkbox(left + 12, y, "rancher:cow", "Cows (milk / breed wheat)", "toggle_ranch_animal", "cow");
        y += 22;
        checkbox(left + 12, y, "rancher:pig", "Pigs (breed carrot/potato/beet)", "toggle_ranch_animal", "pig");
        y += 22;
        checkbox(left + 12, y, "rancher:chicken", "Chickens (eggs / breed seeds)", "toggle_ranch_animal", "chicken");
        y += 26;
        checkbox(left + 12, y, "breed", "Breed mode", "toggle_ranch_flag", "breed");
        y += 22;
        checkbox(left + 12, y, "shear", "Shear sheep", "toggle_ranch_flag", "shear");
        y += 22;
        checkbox(left + 12, y, "milk", "Milk cows", "toggle_ranch_flag", "milk");
        y += 22;
        checkbox(left + 12, y, "eggs", "Collect eggs", "toggle_ranch_flag", "eggs");
        y += 28;
        addBack(left, y);
    }

    private void buildCullConfirm(int left, int top) {
        int y = contentTop() + 40;
        choice(left + 12, y, "Lend a Looting sword", b -> {
            send("start_cull_sword", "");
            page = Page.MAIN;
            rebuild();
        });
        y += 24;
        choice(left + 12, y, "Cull without a sword", b -> {
            send("start_cull", "");
            page = Page.MAIN;
            rebuild();
        });
        y += 24;
        choice(left + 12, y, "Cancel", b -> { page = Page.MAIN; rebuild(); });
    }

    private void buildTargetY(int left, int top) {
        int y = contentTop() + 8;
        for (int target : new int[]{60, 32, 16, 0, -32, -58}) {
            int t = target;
            addRenderableWidget(Button.builder(Component.literal("Mine down to Y=" + t), b -> {
                send("set_target_y", String.valueOf(t)); page = Page.WORK; rebuild();
            }).bounds(left + 12, y, 252, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> { page = Page.WORK; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
    }

    private void buildBiome(int left, int top) {
        int y = contentTop();
        for (String biome : VillageCatalog.biomes()) {
            String b = biome;
            String mark = b.equals(villageBiome) ? "● " : "";
            addRenderableWidget(Button.builder(Component.literal(mark + capitalize(b)), btn -> {
                villageBiome = b; page = Page.BUILD; rebuild();
            }).bounds(left + 12, y, 252, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> { page = Page.BUILD; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
    }

    private void buildLocations(int left, int top) {
        int y = contentTop();
        if(flags.contains("site")) {
            y=assignRow(left,y,"Bed","bed",hasBed);
            choice(left+12,y,"Site settings / release to use zones",b->send("sites",""));
            addBack(left,y+28);return;
        }
        if ("knight".equals(role) || "archer".equals(role)) {
            y = assignRow(left, y, "Guard area", "area", hasArea);
            y = assignRow(left, y, "Bed", "bed", hasBed);
            addBack(left, y);
            return;
        }
        if ("smelter".equals(role)) {
            y = assignRow(left, y, "Furnace", "furnace", false);
            addRenderableWidget(Button.builder(Component.literal("Clear furnaces"), b -> send("clear_assign", "furnace"))
                .bounds(left + 12, y, 252, 20).build());
            y += 24;
            y = assignRow(left, y, "Supply chest", "supply", hasSupply);
            y = assignRow(left, y, "Output chest", "output", hasOutput);
            y = assignRow(left, y, "Bed", "bed", hasBed);
            addBack(left, y);
            return;
        }
        if ("courier".equals(role)) {
            y = assignRow(left, y, "Pickup chest", "pickup", false);
            addRenderableWidget(Button.builder(Component.literal("Clear pickups"), b -> send("clear_assign", "pickup"))
                .bounds(left + 12, y, 252, 20).build());
            y += 24;
            y = assignRow(left, y, "Drop-off chest", "output", hasOutput);
            y = assignRow(left, y, "Bed", "bed", hasBed);
            addBack(left, y);
            return;
        }
        if (!"builder".equals(role)) {
            y = assignRow(left, y, workAreaLabel(), "area", hasArea);
        }
        y = assignRow(left, y, "Bed", "bed", hasBed);
        y = assignRow(left, y, "Supply chest", "supply", hasSupply);
        y = assignRow(left, y, "Output chest", "output", hasOutput);
        if ("builder".equals(role) || "miner".equals(role) || "farmer".equals(role) || "forester".equals(role)) {
            y = assignRow(left, y, "Crafting table", "table", hasTable);
        }
        addBack(left, y);
    }

    private String workAreaLabel() {
        return switch (role) {
            case "forester" -> "Forest area";
            case "farmer" -> "Farm area";
            case "miner" -> "Mine area";
            case "rancher" -> "Animal pen";
            case "fisher" -> "Fishing area";
            case "knight", "archer" -> "Guard area";
            default -> "Work area";
        };
    }

    private int assignRow(int left, int y, String label, String kind, boolean set) {
        WorkerIcons.Kind icon = kind.equals("bed") ? WorkerIcons.Kind.BED
            : kind.equals("area") ? WorkerIcons.Kind.AREA : kind.equals("table") ? WorkerIcons.Kind.TABLE : WorkerIcons.Kind.CHEST;
        addRenderableWidget(WorkerIcons.button(left + 12, y, 176, (set ? "Change " : "Assign ") + label,
            icon, kind.equals("bed") ? 0xff79b8e8 : 0xffd7b773, b -> send("start_assign", kind)));
        var clear=addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            if (!set) {
                infoMessage = label + " is not assigned yet.";
                page = Page.MESSAGE; rebuild();
            } else send("clear_assign", kind);
        }).bounds(left + 194, y, 70, 20).build());
        clear.active=set;
        return y + 24;
    }

    private void buildRecipes(int left, int top) {
        if (searchBox == null) ensureTextBoxes(left);
        addRenderableWidget(searchBox);
        searchBox.setHint(Component.literal("Search by output name"));
        String query = searchBox.getValue().toLowerCase(Locale.ROOT);
        int y = contentTop() + 36;
        int shown = 0;
        int skipped = 0;
        for (WorkerNetwork.RecipeEntry entry : recipes) {
            if (!query.isEmpty() && !entry.outputName().toLowerCase(Locale.ROOT).contains(query)
                && !entry.id().toLowerCase(Locale.ROOT).contains(query)) continue;
            String prefix = entry.approved() ? "[OK] " : entry.supported() ? "[ ] " : "[X] ";
            String text = prefix + entry.outputName();
            WorkerNetwork.RecipeEntry captured = entry;
            addRenderableWidget(Button.builder(Component.literal(trim(text, 34)), b -> {
                if (!captured.supported()) {
                    infoMessage = captured.reason().isEmpty()
                        ? "This recipe cannot be taught." : captured.reason();
                    page = Page.MESSAGE; rebuild();
                } else if (captured.approved()) send("remove_recipe", captured.id());
                else send("approve_recipe", captured.id());
            }).bounds(left + 12, y, 252, 20).build());
            y += 22;
            shown++;
        }
        if (shown == 0) {
            var empty=Button.builder(Component.literal("No matching recipes"),b->{}).bounds(left+12,y,252,20).build();
            empty.active=false;addRenderableWidget(empty);
        }
    }

    private void buildBuild(int left, int top) {
        int y = contentTop();
        addRenderableWidget(Button.builder(Component.literal("Village style: " + capitalize(villageBiome)), b -> {
            page = Page.BIOME; rebuild();
        }).bounds(left + 12, y, 252, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal(hasArea ? "● Plot marked — Build on it" : "Mark a plot (min 10×10)"),
            b -> {
                if (hasArea) send("build_on_plot", "");
                else send("start_village_plot", "");
            }).bounds(left + 12, y, 252, 20).build());
        y += 24;
        if (hasArea) {
            addRenderableWidget(Button.builder(Component.literal("Remake plot"), b -> send("start_village_plot", ""))
                .bounds(left + 12, y, 122, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Clear plot"), b -> send("clear_assign", "area"))
                .bounds(left + 142, y, 122, 20).build());
            y += 24;
        }
        java.util.List<VillageCatalog.Entry> presets = VillageCatalog.forBiome(villageBiome);
        int shown = 0;
        for (int i = 0; i < presets.size(); i++) {
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
        addRenderableWidget(Button.builder(Component.literal("Auto plains house on plot"), b -> {
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
        nameBox.setHint(Component.literal("Blueprint name"));
        int y = contentTop() + 20;
        addRenderableWidget(Button.builder(Component.literal("1. Mark area to copy"), b -> send("start_assign", "copy_area"))
            .bounds(left + 12, y, 252, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("2. Capture as blueprint"), b -> {
            String name = nameBox.getValue().trim();
            if (name.isEmpty()) {
                infoMessage = "Enter a blueprint name first.";
                page = Page.MESSAGE; rebuild();
                return;
            }
            if (!hasArea) {
                infoMessage = "Mark the area to copy first (step 1).";
                page = Page.MESSAGE; rebuild();
                return;
            }
            pendingCaptureName = name;
            if (blueprints.contains(name)) { page = Page.CAPTURE_CONFIRM; rebuild(); }
            else send("capture_blueprint", name);
        }).bounds(left + 12, y, 252, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("Materials needed"), b -> send("materials", ""))
            .bounds(left + 12, y, 122, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Preview"), b -> send("preview", ""))
            .bounds(left + 142, y, 122, 20).build());
        y += 22;
        for (int rot : new int[]{0, 1, 2, 3}) {
            int degrees = rot * 90;
            String label = (rotation == rot ? "● " : "") + degrees + "°";
            addRenderableWidget(Button.builder(Component.literal(label), b -> send("set_rotation", String.valueOf(rot)))
                .bounds(left + 12 + rot * 64, y, 60, 20).build());
        }
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("3. Place build spot"), b -> send("start_assign", "origin"))
            .bounds(left + 12, y, 122, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear spot"), b -> send("clear_assign", "origin"))
            .bounds(left + 142, y, 122, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("4. Start building (uses materials)"), b -> {
            if (blueprint == null || blueprint.isEmpty() || BuilderPresets.isPreset(blueprint)) {
                infoMessage = "Capture or load a custom blueprint first.";
                page = Page.MESSAGE; rebuild();
            } else if (!hasOrigin) {
                infoMessage = "Place the build spot with the clipboard (step 3).";
                page = Page.MESSAGE; rebuild();
            } else send("start", "");
        }).bounds(left + 12, y, 252, 20).build());
        y += 22;
        int shown = 0;
        for (String name : blueprints) {
            if (BuilderPresets.isPreset(name)) continue;
            String label = (name.equals(blueprint) ? "● " : "") + name;
            addRenderableWidget(Button.builder(Component.literal("Load " + trim(label, 24)), b -> send("load_blueprint", name))
                .bounds(left + 12, y, 252, 20).build());
            y += 22;
            shown++;
        }
        addBack(left, y);
    }

    private void buildDepth(int left, int top) {
        int y = contentTop() + 8;
        for (int[] opt : new int[][]{{5, 5}, {10, 10}, {20, 20}, {-1, -1}}) {
            int depth = opt[0];
            String label = depth < 0 ? "Down to bedrock" : depth + " blocks deep";
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                send("set_depth", String.valueOf(depth));
                page = Page.WORK;
                rebuild();
            }).bounds(left + 12, y, 252, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
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
        addRenderableWidget(Button.builder(Component.literal("Confirm " + WorkerActions.roleLabel(pendingRole)),
            b -> { send("confirm_role", pendingRole); page = Page.MAIN; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            pendingRole = ""; page = Page.MAIN; rebuild();
        }).bounds(left + 12, y, 252, 20).build());
    }

    private void buildCaptureConfirm(int left, int top) {
        int y = contentTop() + 36;
        addRenderableWidget(Button.builder(Component.literal("Overwrite \"" + trim(pendingCaptureName, 18) + "\""),
            b -> { send("overwrite_blueprint", pendingCaptureName); page = Page.BLUEPRINT; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> { page = Page.BLUEPRINT; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
    }

    private void buildMessage(int left, int top) {
        int y = contentTop() + 48;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            page = Page.MAIN; rebuild();
        }).bounds(left + 12, y, 252, 20).build());
    }

    private void addBack(int left, int y) {
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> { page = Page.MAIN; rebuild(); })
            .bounds(left + 12, y, 252, 20).build());
    }

    private String sectionHint() {
        return switch(page) {
            case MAIN -> flags.contains("site") ? "Assigned site • bounds and storage are automatic" : "Choose a site, or configure ordinary " + jobTab().toLowerCase(Locale.ROOT) + ".";
            case WORK -> flags.contains("site") ? "Your " + siteName() + " handles the work area." : "Configure " + jobTab().toLowerCase(Locale.ROOT) + " for this worker.";
            case LOCATIONS -> "Use the clipboard to assign beds, storage and work locations.";
            case MANAGE -> "Personalize your worker or change their profession.";
            case ROLE, ROLE_CONFIRM -> page==Page.ROLE ? "Choose a profession. Survival requires its role kit." : "Assign " + WorkerActions.roleLabel(pendingRole) + "? The matching kit is required, but not consumed.";
            case CAPTURE_CONFIRM -> "Overwrite saved blueprint: " + pendingCaptureName + "?";
            case MESSAGE -> infoMessage;
            case DEPTH -> "Choose depth for the marked mining area.";
            case BUILD -> "Select a house style, then mark a plot of at least 10 × 10.";
            case BLUEPRINT -> "Name, capture, preview, then build your custom structure.";
            case RECIPES -> "Select a recipe to teach it; select a taught recipe to forget it.";
            case CROPS -> "Choose which crops to grow and whether to use bone meal.";
            case RANCH -> "Choose animals, breeding and the products to collect.";
            case CULL_CONFIRM -> "Keep two adults of each enabled species; harvest the rest.";
            case PROTECT -> "Choose a guard post, follow mode or a fixed position.";
            case TARGET_Y -> "Set the destination height for your mining operation.";
            case BIOME -> "Choose the architectural style for village houses.";
        };
    }

    /** Keep whole rows together so controls remain reachable at every GUI scale. */
    private void finishLayout() {
        var widgets=children().stream().filter(e->e instanceof net.minecraft.client.gui.components.AbstractWidget)
                .map(e->(net.minecraft.client.gui.components.AbstractWidget)e).toList();
        var rows=new java.util.TreeMap<Integer,List<net.minecraft.client.gui.components.AbstractWidget>>();
        for(var widget:widgets)if(page!=Page.RECIPES || widget!=searchBox)rows.computeIfAbsent(widget.getY(),k->new ArrayList<>()).add(widget);
        var pages=new ArrayList<List<net.minecraft.client.gui.components.AbstractWidget>>();
        var current=new ArrayList<net.minecraft.client.gui.components.AbstractWidget>();pages.add(current);
        int start=contentTop()+(page==Page.RECIPES?24:0), y=start;
        for(var row:rows.values()) {
            int h=row.stream().mapToInt(w->w.getHeight()).max().orElse(20);
            if(y+h>contentBottom() && !current.isEmpty()) {current=new ArrayList<>();pages.add(current);y=start;}
            for(var widget:row) {widget.setY(y);current.add(widget);}y+=h+4;
        }
        sectionPages=pages.size();sectionPage=Math.min(sectionPage,sectionPages-1);
        clearWidgets();
        if(page==Page.RECIPES) {searchBox.setY(contentTop());addRenderableWidget(searchBox);}
        for(var widget:pages.get(sectionPage)) {
            if(widget instanceof Button old) {
                String label=old.getMessage().getString();
                boolean selected=label.startsWith("● ") || label.startsWith("[✓] ") || page==Page.RECIPES && label.startsWith("[OK] ");
                String caption=label.startsWith("● ")?label.substring(2):label.startsWith("[✓] ")?"On · "+label.substring(4):label.startsWith("[ ] ")?"Off · "+label.substring(4):label;
                if(page==Page.RECIPES)caption=label.startsWith("[OK] ")?"Taught: "+label.substring(5):label.startsWith("[ ] ")?"Learn: "+label.substring(4):label.startsWith("[X] ")?"Unavailable: "+label.substring(4):label;
                var button=WorkerUi.button(old.getX(),old.getY(),old.getWidth(),old.getHeight(),caption,
                        old.getWidth()>=170 || page==Page.ROLE?WorkerUi.icon(label):null,selected,b->old.onPress());
                button.active=old.active;addRenderableWidget(button);
            } else addRenderableWidget(widget);
        }
        Page selected=switch(page) {
            case MAIN->Page.MAIN;case LOCATIONS->Page.LOCATIONS;
            case MANAGE,ROLE,ROLE_CONFIRM,RECIPES->Page.MANAGE;default->Page.WORK;
        };
        String[] names={"Overview",jobTab(),"Locations","Manage"};Page[] targets={Page.MAIN,Page.WORK,Page.LOCATIONS,Page.MANAGE};
        int x=panelLeft()+12,tw=(panelWidth()-33)/4;
        for(int i=0;i<4;i++) {
            Page target=targets[i];addRenderableWidget(WorkerUi.button(x+i*(tw+3),panelTop()+56,tw,20,names[i],null,selected==target,b->{page=target;rebuild();}));
        }
        int fy=panelTop()+panelHeight()-24;
        if(sectionPages>1) {
            var previous=WorkerUi.button(x,fy,78,18,"< Previous",null,false,b->{sectionPage--;rebuild();});previous.active=sectionPage>0;addRenderableWidget(previous);
            var next=WorkerUi.button(panelLeft()+panelWidth()-90,fy,78,18,"Next >",null,false,b->{sectionPage++;rebuild();});next.active=sectionPage<sectionPages-1;addRenderableWidget(next);
        }
        addRenderableWidget(WorkerUi.button(panelLeft()+panelWidth()-29,panelTop()+10,18,18,"×",null,false,b->onClose()));
    }

    @Override public boolean mouseScrolled(double x,double y,double sx,double sy) {
        int next=Math.max(0,Math.min(sectionPages-1,sectionPage-(int)Math.signum(sy)));
        if(next!=sectionPage){sectionPage=next;rebuild();return true;}return super.mouseScrolled(x,y,sx,sy);
    }

    private static String trim(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private String greeting() {
        if (status != null && status.contains("Out of space")) {
            return Jobs.OUT_OF_SPACE;
        }
        if (status != null && !status.isBlank()
            && !status.equals("Waiting for an assignment") && !status.equals("Working") && !status.equals("Paused by owner")) {
            return status;
        }
        return switch (role) {
            case "farmer" -> "Where should I tend the crops?";
            case "forester" -> "Which woods should I look after?";
            case "miner" -> "Where should I dig?";
            case "builder" -> "What should we build?";
            case "knight" -> "Who should I protect?";
            case "archer" -> "Where should I keep watch?";
            case "rancher" -> "Where is the animal pen?";
            case "fisher" -> "Where should I fish?";
            case "smelter" -> "Which furnaces should I run?";
            case "courier" -> "What chests should I haul between?";
            default -> "What would you like me to do?";
        };
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
            if (entity instanceof Worker worker) {
                skin = WorkerSkins.resolve(worker).texture();
                graphics.blit(skin, x, y, 36, 36, 8, 8, 8, 8, 64, 64);
                graphics.blit(skin, x, y, 36, 36, 40, 8, 8, 8, 64, 64);
                return;
            }
        }
        graphics.blit(ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"),x,y,36,36,8,8,8,8,64,64);
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
        WorkerUi.frame(graphics,left,top,panelWidth(),panelHeight());
        graphics.fill(left + 8, top + 10, left + 44, top + 46, 0xFF101318);
        drawWorkerFace(graphics, left + 8, top + 10);
        graphics.blit(roleIcon(), left + 46, top + 10, 0, 0, 16, 16, 16, 16);
        graphics.drawString(font, font.plainSubstrByWidth(workerName,panelWidth()-103), left + 66, top + 12, 0xFFFFFF, false);
        String duty = WorkerActions.roleLabel(role) + (working ? "  ·  working" : "  ·  paused");
        graphics.drawString(font, duty, left + 66, top + 24, roleColor(), false);
        String line = greeting();
        if (font.width(line) > panelWidth()-72) line = font.plainSubstrByWidth(line, panelWidth()-78) + "…";
        graphics.drawString(font, "“" + line + "”", left + 50, top + 36, 0xFFE7D7B0, false);
        String hint = sectionHint();
        graphics.drawString(font,font.plainSubstrByWidth(hint,panelWidth()-24),left+12,top+83,WorkerUi.MUTED,false);
        if(mouseX>=left+12 && mouseX<right-12 && mouseY>=top+80 && mouseY<top+96)
            graphics.renderTooltip(font,font.split(Component.literal(hint),260),mouseX,mouseY);
        if(mouseX>=left+50 && mouseX<right-24 && mouseY>=top+34 && mouseY<top+47)
            graphics.renderTooltip(font,font.split(Component.literal(greeting()),260),mouseX,mouseY);
        for (var widget : renderables) widget.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font,sectionPages>1 ? "Page " + (sectionPage+1) + " / " + sectionPages : "Esc to close",width/2,bottom-19,WorkerUi.MUTED);
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
