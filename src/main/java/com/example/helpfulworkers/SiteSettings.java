package com.example.helpfulworkers;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.*;

final class SiteSettings {
    static final Map<String, ModConfigSpec.IntValue> ORE_INTERVALS = new LinkedHashMap<>();
    static final Map<String, ModConfigSpec.IntValue> ORE_YIELDS = new LinkedHashMap<>();
    static final Map<String, ModConfigSpec.ConfigValue<String>> PICKAXES = new LinkedHashMap<>();
    static ModConfigSpec.IntValue TREE_SECONDS, STORAGE_LIMIT, ORE_MIN, ORE_MAX;

    static void define(ModConfigSpec.Builder b) {
        b.push("sites");
        ORE_MIN = b.defineInRange("oreMinSeconds", 5, 1, 86400);
        ORE_MAX = b.defineInRange("oreMaxSeconds", 25, 1, 86400);
        for (int i = 0; i < SiteCatalog.ORES.length; i++) {
            String ore = SiteCatalog.ORES[i];
            ORE_INTERVALS.put(
                    ore, b.defineInRange(ore + "Seconds", SiteCatalog.SECONDS[i], 1, 86400));
            ORE_YIELDS.put(ore, b.defineInRange(ore + "Yield", defaultYield(ore), 1, 64));
            PICKAXES.put(
                    ore,
                    b.defineInList(
                            ore + "Pickaxe",
                            defaultPickaxe(ore),
                            Arrays.asList("wooden", "stone", "iron", "diamond", "netherite")));
        }
        TREE_SECONDS = b.defineInRange("treeSeconds", 120, 1, 86400);
        STORAGE_LIMIT = b.defineInRange("warehouseInventories", 64, 34, 256);
        b.pop();
    }

    static int defaultYield(String ore) {
        return "redstone".equals(ore) || "lapis".equals(ore) ? 4 : 1;
    }

    static String defaultPickaxe(String ore) {
        return switch (ore) {
            case "coal" -> "wooden";
            case "copper", "iron", "lapis" -> "stone";
            default -> "iron";
        };
    }

    static String minimumPickaxe(String ore) {
        try {
            return PICKAXES.get(ore).get();
        } catch (Exception e) {
            return defaultPickaxe(ore);
        }
    }

    static int yield(String ore) {
        try {
            return ORE_YIELDS.get(ore).get();
        } catch (Exception e) {
            return defaultYield(ore);
        }
    }

    static int storageLimit() {
        try {
            return STORAGE_LIMIT.get();
        } catch (Exception e) {
            return 64;
        }
    }
}
