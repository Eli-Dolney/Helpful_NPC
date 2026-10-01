package com.example.helpfulworkers;

import net.neoforged.neoforge.common.ModConfigSpec;

final class WorkerConfig {
    private WorkerConfig() {}

    static final ModConfigSpec.IntValue MAX_PER_PLAYER;
    static final ModConfigSpec.IntValue MAX_PER_WORLD;
    static final ModConfigSpec.IntValue WORK_INTERVAL_TICKS;
    static final ModConfigSpec.IntValue SCAN_BUDGET;
    static final ModConfigSpec.IntValue RANCHER_CAP;
    static final ModConfigSpec SPEC;

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
        b.pop();
        b.push("rancher");
        RANCHER_CAP = b.comment("Max animals of each species in a rancher pen")
            .defineInRange("speciesCap", 8, 2, 32);
        b.pop();
        SPEC = b.build();
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
    static int rancherCap() {
        try { return RANCHER_CAP.get(); } catch (Exception e) { return 8; }
    }
}
