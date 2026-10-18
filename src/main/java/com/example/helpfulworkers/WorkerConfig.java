package com.example.helpfulworkers;

import net.neoforged.neoforge.common.ModConfigSpec;

final class WorkerConfig {
    private WorkerConfig() {}

    static final ModConfigSpec.IntValue MAX_PER_PLAYER;
    static final ModConfigSpec.IntValue MAX_PER_WORLD;
    static final ModConfigSpec.IntValue WORK_INTERVAL_TICKS;
    static final ModConfigSpec.IntValue SCAN_BUDGET;
    static final ModConfigSpec.IntValue RANCHER_CAP;
    static final ModConfigSpec.DoubleValue WORK_PACE;
    static final ModConfigSpec SPEC;

    // Client display options (config/helpfulworkers-client.toml)
    static final ModConfigSpec.BooleanValue SHOW_OVERHEAD_STATUS;
    static final ModConfigSpec.IntValue OVERHEAD_RANGE;
    static final ModConfigSpec.IntValue OUTLINE_SECONDS;
    static final ModConfigSpec CLIENT_SPEC;

    /** Config entry and section paths, for the translation lint (see {@link Lang#all()}). */
    static final java.util.List<String> CONFIG_PATHS = java.util.List.of(
        "limits", "performance", "rancher", "display",
        "maxWorkersPerPlayer", "maxWorkersPerWorld", "workIntervalTicks", "scanBudget", "workPace", "speciesCap",
        "showOverheadStatus", "overheadRange", "outlineSeconds");

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("limits");
        MAX_PER_PLAYER = b.comment("Max owned workers per player").defineInRange("maxWorkersPerPlayer", 10, 1, 64);
        MAX_PER_WORLD = b.comment("Max workers per world (all players)").defineInRange("maxWorkersPerWorld", 25, 1, 128);
        b.pop();
        b.push("performance");
        WORK_INTERVAL_TICKS = b.comment("Base ticks between job work for an active worker")
            .defineInRange("workIntervalTicks", 20, 5, 200);
        SCAN_BUDGET = b.comment("Max columns/blocks scanned per work tick")
            .defineInRange("scanBudget", 64, 8, 512);
        WORK_PACE = b.comment("How long breaking a block takes relative to a player (0.5 = twice as fast, 2 = half speed)")
            .defineInRange("workPace", 1.0, 0.25, 4.0);
        b.pop();
        b.push("rancher");
        RANCHER_CAP = b.comment("Max animals of each species in a rancher pen")
            .defineInRange("speciesCap", 8, 2, 32);
        b.pop();
        SPEC = b.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        c.push("display");
        SHOW_OVERHEAD_STATUS = c.comment("Draw the coloured 'Role: what I'm doing' line under worker names")
            .define("showOverheadStatus", true);
        OVERHEAD_RANGE = c.comment("How far away (blocks) the overhead status line is drawn")
            .defineInRange("overheadRange", 20, 4, 64);
        OUTLINE_SECONDS = c.comment("How long a shown work area stays outlined (seconds)")
            .defineInRange("outlineSeconds", 10, 2, 120);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    static boolean showOverheadStatus() {
        try { return SHOW_OVERHEAD_STATUS.get(); } catch (Exception e) { return true; }
    }
    static int overheadRange() {
        try { return OVERHEAD_RANGE.get(); } catch (Exception e) { return 20; }
    }
    static int outlineSeconds() {
        try { return OUTLINE_SECONDS.get(); } catch (Exception e) { return 10; }
    }

    static int maxPerPlayer() {
        try { return MAX_PER_PLAYER.get(); } catch (Exception e) { return 10; }
    }
    static int maxPerWorld() {
        try { return MAX_PER_WORLD.get(); } catch (Exception e) { return 25; }
    }
    static int workInterval() {
        try { return WORK_INTERVAL_TICKS.get(); } catch (Exception e) { return 20; }
    }
    static int scanBudget() {
        try { return SCAN_BUDGET.get(); } catch (Exception e) { return 64; }
    }
    static double workPace() {
        try { return WORK_PACE.get(); } catch (Exception e) { return 1.0; }
    }
    static int rancherCap() {
        try { return RANCHER_CAP.get(); } catch (Exception e) { return 8; }
    }
}
