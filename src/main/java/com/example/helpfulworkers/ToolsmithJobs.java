package com.example.helpfulworkers;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;

/**
 * The toolsmith keeps everyone's tools in order. Each work tick, in order: bring a tool a workmate
 * is missing; repair worn tools at the anvil (with repair material) or grindstone (two of a kind);
 * drop finished tools at the output chest; forge whatever the stock targets are short of, best
 * tier the supply chest allows; then look for workmates who are missing a tool. Walking happens
 * through {@link WorkerNav} errands, so every step is on foot.
 */
final class ToolsmithJobs {
    private ToolsmithJobs() {}

    static final List<String> TOOL_KEYS = List.of("pickaxe", "axe", "shovel", "hoe", "shears", "fishing_rod");
    static final List<String> TIERS = List.of("diamond", "iron", "stone", "wooden");
    static final int[] TARGET_STEPS = {0, 1, 2, 4};
    static final int DEFAULT_TARGET = 1;
    /** A tool with less than this fraction of its durability left gets repaired. */
    static final float REPAIR_BELOW = 0.30f;
    /** A workmate holding a tool below this fraction gets a fresh one. */
    static final float WORN_OUT = 0.15f;
    /** A tool with at least this fraction left counts as stock and may be handed out. */
    static final float GOOD = 0.50f;
    static final int DEMAND_RADIUS = 64;
    static final long DEMAND_INTERVAL = 200;

    static Class<?> toolClass(String key) {
        return switch (key) {
            case "pickaxe" -> PickaxeItem.class;
            case "axe" -> AxeItem.class;
            case "shovel" -> ShovelItem.class;
            case "hoe" -> HoeItem.class;
            case "shears" -> ShearsItem.class;
            case "fishing_rod" -> FishingRodItem.class;
            default -> null;
        };
    }

    /** The stock key for a tool stack, or null when it is not a tool the toolsmith handles. */
    static String toolKey(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (String key : TOOL_KEYS) {
            Class<?> type = toolClass(key);
            if (type != null && type.isInstance(stack.getItem())) return key;
        }
        return null;
    }

    static boolean isTool(ItemStack stack) {
        return toolKey(stack) != null;
    }

    /** Fraction of durability left (1 for anything that cannot wear). */
    static float durability(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) return 1f;
        return 1f - stack.getDamageValue() / (float) stack.getMaxDamage();
    }

    static int target(Worker worker, String key) {
        return worker.toolTargets.getOrDefault(key, DEFAULT_TARGET);
    }

    /** Drop-off rule: finished tools leave for the output chest, worn ones and materials stay. */
    static int exportable(Worker worker, ItemStack stack) {
        if (isTool(stack)) return durability(stack) >= GOOD && !stack.equals(worker.getMainHandItem()) ? stack.getCount() : 0;
        return 0;
    }

    /** Finished tools waiting in the bag for the output chest (the generic loot check skips tools). */
    static boolean hasShelvable(Worker worker) {
        for (int i = 0; i < worker.bag.getContainerSize(); i++) if (exportable(worker, worker.bag.getItem(i)) > 0) return true;
        return false;
    }

    static String label(String key) {
        return key.replace('_', ' ');
    }

    // ---------------------------------------------------------------- loop

    static void tick(ServerLevel level, Worker worker) {
        if (worker.craftingTable == null && worker.anvil == null && worker.grindstone == null) {
            worker.setStatusKey(Lang.STATUS_ASSIGN_WORKSHOP);
            worker.markIdle();
            return;
        }
        if (worker.supply == null) { worker.setStatusKey(Lang.STATUS_ASSIGN_SUPPLY); worker.markIdle(); return; }
        if (deliverTool(level, worker)) return;
        if (repairStep(level, worker)) return;
        if (worker.output != null && hasShelvable(worker)) {
            if (!Jobs.depositPublic(level, worker)) return;
            worker.markWorkFound();
            return;
        }
        if (craftStock(level, worker)) return;
        if (findDemand(level, worker)) return;
        worker.setStatusKey(Lang.STATUS_WORKSHOP_STOCKED);
        worker.markIdle();
    }

    // ---------------------------------------------------------------- repairs

    private static boolean repairStep(ServerLevel level, Worker worker) {
        if (worker.anvil == null && worker.grindstone == null) return false;
        // Worn tools workmates dropped in the supply chest come to the workshop first.
        if (Jobs.fetchFromSupply(level, worker, "worn-tool", s -> isTool(s) && durability(s) < REPAIR_BELOW, 1, 4, "worn tools", null)) {
            worker.markWorkFound();
            return true;
        }
        int worn = -1;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack s = worker.bag.getItem(i);
            if (isTool(s) && durability(s) < REPAIR_BELOW) { worn = i; break; }
        }
        if (worn < 0) return false;
        ItemStack tool = worker.bag.getItem(worn);
        // Grindstone: two of a kind become one, enchantments and all are ground off.
        if (worker.grindstone != null && level.isLoaded(worker.grindstone)) {
            for (int j = 0; j < worker.bag.getContainerSize(); j++) {
                ItemStack other = worker.bag.getItem(j);
                if (j == worn || !other.is(tool.getItem()) || !other.isDamaged()) continue;
                int a = worn, b = j;
                worker.nav.requestErrand(worker.grindstone, "Grinding two " + tool.getHoverName().getString(), (lvl, w) -> grind(lvl, w, a, b));
                worker.setStatusKey(Lang.STATUS_REPAIRING, StatusText.name(tool));
                worker.markWorkFound();
                return true;
            }
        }
        if (worker.anvil == null || !level.isLoaded(worker.anvil)) return false;
        int units = Math.max(1, (int) Math.ceil(tool.getDamageValue() / (tool.getMaxDamage() / 4.0)));
        ItemStack material = findRepairMaterial(worker, tool);
        if (material.isEmpty()) {
            ItemStack template = tool.copy();
            if (Jobs.fetchFromSupply(level, worker, "repair:" + Jobs.itemKey(tool), s -> template.getItem().isValidRepairItem(template, s),
                    1, units, "repair material", null)) {
                worker.markWorkFound();
                return true;
            }
            worker.setStatusKey(Lang.STATUS_NEED_REPAIR_MATERIAL, StatusText.name(tool));
            return false;
        }
        int slot = worn;
        worker.nav.requestErrand(worker.anvil, "Repairing " + tool.getHoverName().getString(), (lvl, w) -> anvilRepair(lvl, w, slot));
        worker.setStatusKey(Lang.STATUS_REPAIRING, StatusText.name(tool));
        worker.markWorkFound();
        return true;
    }

    private static ItemStack findRepairMaterial(Worker worker, ItemStack tool) {
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack s = worker.bag.getItem(i);
            if (!s.isEmpty() && !isTool(s) && tool.getItem().isValidRepairItem(tool, s)) return s;
        }
        return ItemStack.EMPTY;
    }

    private static void anvilRepair(ServerLevel level, Worker worker, int slot) {
        ItemStack tool = worker.bag.getItem(slot);
        if (!isTool(tool) || !tool.isDamaged()) return;
        int perUnit = Math.max(1, tool.getMaxDamage() / 4);
        int repaired = 0;
        for (int i = 0; i < worker.bag.getContainerSize() && tool.getDamageValue() > 0; i++) {
            ItemStack s = worker.bag.getItem(i);
            if (s.isEmpty() || isTool(s) || !tool.getItem().isValidRepairItem(tool, s)) continue;
            while (!s.isEmpty() && tool.getDamageValue() > 0) {
                s.shrink(1);
                tool.setDamageValue(Math.max(0, tool.getDamageValue() - perUnit));
                repaired++;
            }
        }
        worker.bag.setChanged();
        if (repaired == 0) { worker.setStatusKey(Lang.STATUS_NEED_REPAIR_MATERIAL, StatusText.name(tool)); return; }
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        level.playSound(null, worker.anvil, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 1.0f);
        worker.gainXp(2);
        worker.setStatusKey(Lang.STATUS_REPAIRED, StatusText.name(tool));
    }

    private static void grind(ServerLevel level, Worker worker, int a, int b) {
        ItemStack first = worker.bag.getItem(a);
        ItemStack second = worker.bag.getItem(b);
        if (first.isEmpty() || !first.is(second.getItem())) return;
        int max = first.getMaxDamage();
        int left = (max - first.getDamageValue()) + (max - second.getDamageValue()) + max * 5 / 100;
        ItemStack merged = first.copy();
        merged.setDamageValue(Math.max(0, max - Math.min(max, left)));
        EnchantmentHelper.setEnchantments(merged, ItemEnchantments.EMPTY);
        worker.bag.setItem(a, merged);
        worker.bag.setItem(b, ItemStack.EMPTY);
        worker.bag.setChanged();
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        level.playSound(null, worker.grindstone, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.7f, 1.0f);
        worker.gainXp(2);
        worker.setStatusKey(Lang.STATUS_REPAIRED, StatusText.name(merged));
    }

    // ---------------------------------------------------------------- stock

    /** Count good tools of a kind in the bag and the output chest. */
    static int stock(ServerLevel level, Worker worker, String key) {
        Class<?> type = toolClass(key);
        int n = 0;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack s = worker.bag.getItem(i);
            if (type.isInstance(s.getItem()) && durability(s) >= GOOD) n += s.getCount();
        }
        Container out = worker.output == null || !level.isLoaded(worker.output) ? null : StorageOps.at(level, worker.output);
        if (out != null) {
            for (int i = 0; i < out.getContainerSize(); i++) {
                ItemStack s = out.getItem(i);
                if (type.isInstance(s.getItem()) && durability(s) >= GOOD) n += s.getCount();
            }
        }
        return n;
    }

    private static boolean craftStock(ServerLevel level, Worker worker) {
        if (!Crafting.tableReady(level, worker)) return false;
        for (String key : TOOL_KEYS) {
            if (stock(level, worker, key) >= target(worker, key)) continue;
            Crafting.Result result = craftOne(level, worker, key);
            if (result == Crafting.Result.DONE) { worker.markWorkFound(); return true; }
            if (result == Crafting.Result.PENDING) { worker.markWorkFound(); return true; }
            worker.setStatusKey(Lang.STATUS_NEED_TOOL_MATERIALS, label(key));
        }
        return false;
    }

    /** Forge one tool of this kind at the best tier the bag and supply can pay for. */
    static Crafting.Result craftOne(ServerLevel level, Worker worker, String key) {
        for (String id : recipeIds(key)) {
            CraftingRecipe recipe = Crafting.recipe(level, id);
            if (recipe == null) continue;
            Crafting.Result result = Crafting.attempt(level, worker, recipe);
            if (result != Crafting.Result.MISSING) return result;
        }
        return Crafting.Result.MISSING;
    }

    static List<String> recipeIds(String key) {
        if ("shears".equals(key) || "fishing_rod".equals(key)) return List.of("minecraft:" + key);
        return TIERS.stream().map(tier -> "minecraft:" + tier + "_" + key).toList();
    }

    // ---------------------------------------------------------------- demand

    /** Which tool a workmate is short of right now, or null. */
    static String neededTool(Worker mate) {
        String status = mate.status == null ? "" : mate.status;
        if (status.startsWith("Missing ")) {
            String name = status.substring("Missing ".length()).replace(' ', '_');
            for (String key : TOOL_KEYS) if (name.startsWith(key)) return key;
        }
        String held = toolKey(mate.getMainHandItem());
        if (held != null && durability(mate.getMainHandItem()) < WORN_OUT && !hasSpare(mate, held)) return held;
        return null;
    }

    private static boolean hasSpare(Worker mate, String key) {
        Class<?> type = toolClass(key);
        for (int i = 0; i < mate.bag.getContainerSize(); i++) {
            ItemStack s = mate.bag.getItem(i);
            if (type.isInstance(s.getItem()) && durability(s) >= GOOD) return true;
        }
        return false;
    }

    private static boolean findDemand(ServerLevel level, Worker worker) {
        long now = level.getGameTime();
        if (worker.lastDemandScan != 0 && now - worker.lastDemandScan < DEMAND_INTERVAL) return false;
        worker.lastDemandScan = now;
        if (worker.owner == null) return false;
        List<Worker> mates = level.getEntitiesOfClass(Worker.class, worker.getBoundingBox().inflate(DEMAND_RADIUS),
            m -> m != worker && worker.owner.equals(m.owner) && !WorkerActions.isCombatRole(m.role));
        for (Worker mate : mates) {
            String key = neededTool(mate);
            if (key == null) continue;
            worker.toolDeliveryTo = mate.getUUID();
            worker.toolDeliveryKey = key;
            worker.setStatusKey(Lang.STATUS_BRINGING_TOOL, label(key), mate.getName().getString());
            worker.markWorkFound();
            return true;
        }
        return false;
    }

    /** Carry the promised tool to the workmate's supply chest, or hand it over in person. */
    private static boolean deliverTool(ServerLevel level, Worker worker) {
        if (worker.toolDeliveryTo == null || worker.toolDeliveryKey == null) return false;
        UUID to = worker.toolDeliveryTo;
        String key = worker.toolDeliveryKey;
        if (!(level.getEntity(to) instanceof Worker mate) || !mate.isAlive()) { clearDelivery(worker); return false; }
        if (neededTool(mate) == null && !carriesGood(worker, key)) { clearDelivery(worker); return false; }
        if (!carriesGood(worker, key)) {
            Class<?> type = toolClass(key);
            if (worker.output != null && Jobs.fetchFrom(level, worker, worker.output, "tool:" + key,
                    s -> type.isInstance(s.getItem()) && durability(s) >= GOOD, 1, 1, label(key), null, false)) {
                worker.markWorkFound();
                return true;
            }
            Crafting.Result result = craftOne(level, worker, key);
            if (result == Crafting.Result.MISSING) {
                worker.setStatusKey(Lang.STATUS_NEED_TOOL_MATERIALS, label(key));
                clearDelivery(worker);
                return false;
            }
            worker.markWorkFound();
            return true;
        }
        ItemStack tool = takeGood(worker, key);
        if (tool.isEmpty()) { clearDelivery(worker); return false; }
        String name = mate.getName().getString();
        if (mate.supply != null && level.isLoaded(mate.supply)) {
            BlockPos chestPos = mate.supply;
            worker.collect(tool); // keep it in the bag until the errand arrives
            worker.nav.requestErrand(chestPos, "Bringing " + label(key) + " to " + name, (lvl, w) -> {
                ItemStack carried = takeGood(w, key);
                if (carried.isEmpty()) return;
                Container chest = StorageOps.at(lvl, chestPos);
                if (chest == null || WorkerStorage.insert(chest, carried) == 0) { w.collect(carried); return; }
                mate.supplyPeek.clear();
                w.gainXp(3);
                w.setStatusKey(Lang.STATUS_DELIVERED_TOOL, label(key), name);
                clearDelivery(w);
            });
            worker.setStatusKey(Lang.STATUS_BRINGING_TOOL, label(key), name);
            worker.markWorkFound();
            return true;
        }
        if (!worker.nav.approachWithin(mate.blockPosition(), 3, 3, "Bringing " + label(key) + " to " + name)) {
            worker.collect(tool);
            worker.markWorkFound();
            return true;
        }
        mate.collect(tool);
        worker.gainXp(3);
        worker.setStatusKey(Lang.STATUS_DELIVERED_TOOL, label(key), name);
        clearDelivery(worker);
        worker.markWorkFound();
        return true;
    }

    private static boolean carriesGood(Worker worker, String key) {
        Class<?> type = toolClass(key);
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack s = worker.bag.getItem(i);
            if (type.isInstance(s.getItem()) && durability(s) >= GOOD) return true;
        }
        return false;
    }

    private static ItemStack takeGood(Worker worker, String key) {
        Class<?> type = toolClass(key);
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack s = worker.bag.getItem(i);
            if (type.isInstance(s.getItem()) && durability(s) >= GOOD) {
                ItemStack taken = s.split(1);
                worker.bag.setChanged();
                return taken;
            }
        }
        return ItemStack.EMPTY;
    }

    static void clearDelivery(Worker worker) {
        worker.toolDeliveryTo = null;
        worker.toolDeliveryKey = null;
    }
}
