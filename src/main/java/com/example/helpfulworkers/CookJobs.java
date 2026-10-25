package com.example.helpfulworkers;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The cook keeps smokers (or furnaces) full of raw food from the supply chest, shelves what comes
 * out, bakes bread when asked, and every minute looks for workmates running low on food and brings
 * them a few cooked meals. The furnace handling is the smelter's loop with a food-only filter.
 */
final class CookJobs {
    private CookJobs() {}

    static final int FEED_RADIUS = 64;
    static final long FEED_INTERVAL = 1200;
    /** A workmate with fewer food items than this gets fed, up to FEED_TO. */
    static final int FEED_LOW = 4;
    static final int FEED_TO = 8;
    /** Cooked food the cook keeps in the bag for feeding; the rest is shelved. */
    static final int KEEP_FOOD = 16;

    static boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.FOOD);
    }

    /** Raw food: something a smoker turns into food. */
    static boolean isRaw(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return level.getRecipeManager().getRecipeFor(RecipeType.SMOKING, new SingleRecipeInput(stack), level).isPresent();
    }

    static boolean isCooked(ServerLevel level, ItemStack stack) {
        return isFood(stack) && !isRaw(level, stack);
    }

    /** Only loads a furnace with things that cook into food (so a cook's plain furnace never smelts ore). */
    static boolean cooksIntoFood(ServerLevel level, ItemStack stack, BlockState furnaceState) {
        var recipe = level.getRecipeManager().getRecipeFor(ExtraJobs.cookingType(furnaceState), new SingleRecipeInput(stack), level);
        return recipe.isPresent() && recipe.get().value().getResultItem(level.registryAccess()).has(DataComponents.FOOD);
    }

    /** Drop-off rule: fuel, raw food and tools stay; cooked food beyond the feeding reserve is shelved. */
    static int exportable(ServerLevel level, Worker worker, ItemStack stack) {
        if (stack.isEmpty() || ToolsmithJobs.isTool(stack) || ExtraJobs.isFuel(stack) || isRaw(level, stack)) return 0;
        if (isFood(stack)) return Math.min(stack.getCount(), Math.max(0, cookedCount(level, worker) - KEEP_FOOD));
        return stack.getCount();
    }

    static int cookedCount(ServerLevel level, Worker worker) {
        int n = 0;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack s = worker.bag.getItem(i);
            if (isCooked(level, s)) n += s.getCount();
        }
        return n;
    }

    static int foodCount(Worker worker) {
        int n = 0;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) if (isFood(worker.bag.getItem(i))) n += worker.bag.getItem(i).getCount();
        return n;
    }

    // ---------------------------------------------------------------- loop

    static void tick(ServerLevel level, Worker worker) {
        if (worker.furnaces.isEmpty()) { worker.setStatusKey(Lang.STATUS_ASSIGN_SMOKER); worker.markIdle(); return; }
        if (worker.supply == null) { worker.setStatusKey(Lang.STATUS_ASSIGN_SUPPLY); worker.markIdle(); return; }
        if (feed(level, worker)) return;
        ExtraJobs.smelter(level, worker);
        if (worker.nav.hasErrand() || !worker.status.startsWith("Furnaces idle")) return;
        if (worker.craftMeals && bakeBread(level, worker)) return;
        findHungry(level, worker);
    }

    private static boolean bakeBread(ServerLevel level, Worker worker) {
        if (!Crafting.tableReady(level, worker)) return false;
        Container out = worker.output != null && level.isLoaded(worker.output) ? StorageOps.at(level, worker.output) : null;
        int bread = StonemasonJobs.countId(worker.bag, "minecraft:bread") + (out == null ? 0 : StonemasonJobs.countId(out, "minecraft:bread"));
        if (bread >= KEEP_FOOD) return false;
        CraftingRecipe recipe = Crafting.recipe(level, "minecraft:bread");
        if (recipe == null) return false;
        Crafting.Result result = Crafting.attempt(level, worker, recipe);
        if (result == Crafting.Result.MISSING) return false;
        worker.markWorkFound();
        return true;
    }

    // ---------------------------------------------------------------- feeding

    private static void findHungry(ServerLevel level, Worker worker) {
        if (!worker.feedWorkers || worker.owner == null) return;
        long now = level.getGameTime();
        if (worker.lastFeedScan != 0 && now - worker.lastFeedScan < FEED_INTERVAL) return;
        worker.lastFeedScan = now;
        List<Worker> mates = level.getEntitiesOfClass(Worker.class, worker.getBoundingBox().inflate(FEED_RADIUS),
            m -> m != worker && worker.owner.equals(m.owner) && !"cook".equals(m.role) && foodCount(m) < FEED_LOW);
        if (mates.isEmpty()) return;
        Worker mate = mates.get(0);
        worker.feedTo = mate.getUUID();
        worker.setStatusKey(Lang.STATUS_FEEDING, mate.getName().getString());
        worker.markWorkFound();
    }

    /** Carry cooked food to the hungry workmate's supply chest, or hand it over in person. */
    private static boolean feed(ServerLevel level, Worker worker) {
        if (worker.feedTo == null) return false;
        UUID to = worker.feedTo;
        if (!(level.getEntity(to) instanceof Worker mate) || !mate.isAlive()) { worker.feedTo = null; return false; }
        if (cookedCount(level, worker) == 0) {
            if (worker.output != null && Jobs.fetchFrom(level, worker, worker.output, "cooked-food", s -> isCooked(level, s),
                    1, FEED_TO, "cooked food", null, false)) {
                worker.markWorkFound();
                return true;
            }
            worker.feedTo = null;
            worker.setStatusKey(Lang.STATUS_NO_COOKED_FOOD);
            return false;
        }
        String name = mate.getName().getString();
        if (mate.supply != null && level.isLoaded(mate.supply)) {
            BlockPos chestPos = mate.supply;
            worker.nav.requestErrand(chestPos, "Bringing food to " + name, (lvl, w) -> {
                Container chest = StorageOps.at(lvl, chestPos);
                if (chest == null) return;
                int moved = handOver(lvl, w, portion -> WorkerStorage.insert(chest, portion));
                if (moved > 0) { mate.supplyPeek.clear(); w.gainXp(2); w.setStatusKey(Lang.STATUS_FED, name); }
                w.feedTo = null;
            });
            worker.setStatusKey(Lang.STATUS_FEEDING, name);
            worker.markWorkFound();
            return true;
        }
        if (!worker.nav.approachWithin(mate.blockPosition(), 3, 3, "Bringing food to " + name)) {
            worker.markWorkFound();
            return true;
        }
        int moved = handOver(level, worker, portion -> { int n = portion.getCount(); mate.collect(portion); return n; });
        if (moved > 0) { worker.gainXp(2); worker.setStatusKey(Lang.STATUS_FED, name); }
        worker.feedTo = null;
        worker.markWorkFound();
        return true;
    }

    private interface Receiver { int take(ItemStack portion); }

    /** Give up to FEED_TO cooked food items from the bag; returns how many were taken. */
    private static int handOver(ServerLevel level, Worker worker, Receiver receiver) {
        int left = FEED_TO;
        int moved = 0;
        for (int i = 0; i < worker.bag.getContainerSize() && left > 0; i++) {
            ItemStack s = worker.bag.getItem(i);
            if (!isCooked(level, s)) continue;
            ItemStack portion = s.split(Math.min(left, s.getCount()));
            int taken = receiver.take(portion);
            if (!portion.isEmpty()) s.grow(portion.getCount());
            moved += taken;
            left -= taken;
        }
        if (moved > 0) worker.bag.setChanged();
        return moved;
    }
}
