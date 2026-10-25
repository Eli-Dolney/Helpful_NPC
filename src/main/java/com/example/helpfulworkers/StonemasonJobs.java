package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/**
 * The stonemason turns raw stone into building materials at a stonecutter. Orders (item + count)
 * come from the owner, or are derived from linked builders' blueprints when "supply my builders"
 * is on. Inputs are fetched from the supply chest on foot, one output is cut per work tick, and
 * products go to the output chest.
 */
final class StonemasonJobs {
    private StonemasonJobs() {}

    static final int MAX_ORDERS = 8;
    static final int BUILDER_RADIUS = 64;
    static final long BUILDER_SCAN_INTERVAL = 200;

    /** All stonecutter recipes that produce this item. */
    static List<RecipeHolder<StonecutterRecipe>> recipesFor(ServerLevel level, String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        List<RecipeHolder<StonecutterRecipe>> out = new ArrayList<>();
        if (item == null) return out;
        for (RecipeHolder<StonecutterRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING)) {
            if (holder.value().getResultItem(level.registryAccess()).is(item)) out.add(holder);
        }
        return out;
    }

    static boolean canCut(ServerLevel level, ItemStack stack) {
        return !stack.isEmpty() && !recipesFor(level, ExtraJobs.itemId(stack)).isEmpty();
    }

    static Worker.Order nextOpen(Worker worker) {
        for (Worker.Order order : worker.orders) if (!order.finished()) return order;
        return null;
    }

    /** Drop-off rule: products leave, inputs for open orders and tools stay. */
    static int exportable(ServerLevel level, Worker worker, ItemStack stack) {
        if (stack.isEmpty() || ToolsmithJobs.isTool(stack)) return 0;
        if (isInputForOpenOrder(level, worker, stack)) return 0;
        return stack.getCount();
    }

    static boolean hasShelvable(ServerLevel level, Worker worker) {
        for (int i = 0; i < worker.bag.getContainerSize(); i++) if (exportable(level, worker, worker.bag.getItem(i)) > 0) return true;
        return false;
    }

    private static boolean isInputForOpenOrder(ServerLevel level, Worker worker, ItemStack stack) {
        for (Worker.Order order : worker.orders) {
            if (order.finished()) continue;
            for (RecipeHolder<StonecutterRecipe> holder : recipesFor(level, order.item())) {
                for (Ingredient ingredient : holder.value().getIngredients()) if (ingredient.test(stack)) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- loop

    static void tick(ServerLevel level, Worker worker) {
        if (worker.stonecutter == null || !level.isLoaded(worker.stonecutter)) { worker.setStatusKey(Lang.STATUS_ASSIGN_STONECUTTER); worker.markIdle(); return; }
        if (worker.supply == null) { worker.setStatusKey(Lang.STATUS_ASSIGN_SUPPLY); worker.markIdle(); return; }
        if (worker.supplyBuilders) refreshBuilderOrders(level, worker);
        if (worker.output != null && hasShelvable(level, worker)) {
            if (!Jobs.depositPublic(level, worker)) return;
            worker.markWorkFound();
            return;
        }
        Worker.Order order = nextOpen(worker);
        if (order == null) {
            worker.setStatusKey(worker.orders.isEmpty() ? Lang.STATUS_NO_ORDERS : Lang.STATUS_ORDERS_DONE);
            worker.markIdle();
            return;
        }
        List<RecipeHolder<StonecutterRecipe>> recipes = recipesFor(level, order.item());
        if (recipes.isEmpty()) {
            worker.setStatusKey(Lang.STATUS_CANT_CUT, Worker.requestItemName(order.item()));
            worker.orders.remove(order);
            worker.markIdle();
            return;
        }
        // Something to cut already in the bag?
        for (RecipeHolder<StonecutterRecipe> holder : recipes) {
            for (Ingredient ingredient : holder.value().getIngredients()) {
                for (int i = 0; i < worker.bag.getContainerSize(); i++) {
                    ItemStack stack = worker.bag.getItem(i);
                    if (stack.isEmpty() || !ingredient.test(stack)) continue;
                    if (!worker.nav.isWithin(worker.stonecutter, WorkerNav.CONTAINER_REACH, WorkerNav.CONTAINER_REACH_Y)) {
                        worker.nav.requestErrand(worker.stonecutter, "Walking to the stonecutter", (lvl, w) -> {});
                        worker.markWorkFound();
                        return;
                    }
                    cut(level, worker, i, holder.value(), order);
                    worker.markWorkFound();
                    return;
                }
            }
        }
        // Otherwise fetch an input from the supply chest (one trip per batch).
        int remaining = Math.max(1, order.count() - order.done());
        for (RecipeHolder<StonecutterRecipe> holder : recipes) {
            for (Ingredient ingredient : holder.value().getIngredients()) {
                ItemStack sample = ingredient.getItems().length > 0 ? ingredient.getItems()[0] : ItemStack.EMPTY;
                int grab = Math.min(64, Math.max(1, remaining / Math.max(1, holder.value().getResultItem(level.registryAccess()).getCount())));
                if (Jobs.fetchFromSupply(level, worker, "cut:" + order.item(), ingredient::test, 1, grab,
                        sample.isEmpty() ? "stone" : sample.getHoverName().getString(), null)) {
                    worker.markWorkFound();
                    return;
                }
            }
        }
        worker.setStatusKey(Lang.STATUS_NEED_STONE, Worker.requestItemName(order.item()));
        worker.markIdle();
    }

    private static void cut(ServerLevel level, Worker worker, int slot, StonecutterRecipe recipe, Worker.Order order) {
        ItemStack input = worker.bag.getItem(slot);
        ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        input.shrink(1);
        worker.bag.setChanged();
        worker.collect(result.copy());
        int index = worker.orders.indexOf(order);
        if (index >= 0) {
            worker.orders.set(index, new Worker.Order(order.item(), order.count(), order.done() + result.getCount(), order.auto()));
        }
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        worker.getLookControl().setLookAt(net.minecraft.world.phys.Vec3.atCenterOf(worker.stonecutter));
        level.playSound(null, worker.stonecutter, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.7f, 0.9f + level.random.nextFloat() * 0.2f);
        worker.gainXp(1);
        Worker.Order now = index >= 0 ? worker.orders.get(index) : order;
        worker.setStatusKey(Lang.STATUS_CUTTING, StatusText.name(result), Math.min(now.done(), now.count()), now.count());
    }

    // ---------------------------------------------------------------- builder-driven orders

    /** Derive orders from nearby owned builders' remaining blueprint materials that a stonecutter can make. */
    static void refreshBuilderOrders(ServerLevel level, Worker worker) {
        long now = level.getGameTime();
        if (worker.lastOrderScan != 0 && now - worker.lastOrderScan < BUILDER_SCAN_INTERVAL) return;
        worker.lastOrderScan = now;
        if (worker.owner == null) return;
        Map<String, Integer> wanted = new LinkedHashMap<>();
        List<Worker> builders = level.getEntitiesOfClass(Worker.class, worker.getBoundingBox().inflate(BUILDER_RADIUS),
            b -> b != worker && worker.owner.equals(b.owner) && "builder".equals(b.role));
        for (Worker builder : builders) {
            if (builder.blueprintBlocks == null || builder.blueprintBlocks.isEmpty() || Blueprints.isFreePreset(builder.blueprint)) continue;
            Map<String, Integer> remaining = new LinkedHashMap<>();
            for (int i = Math.max(0, builder.scanCursor); i < builder.blueprintBlocks.size(); i++) {
                var state = net.minecraft.nbt.NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                    builder.blueprintBlocks.getCompound(i).getCompound("State"));
                Item item = state.getBlock().asItem();
                if (item == net.minecraft.world.item.Items.AIR) continue;
                remaining.merge(BuiltInRegistries.ITEM.getKey(item).toString(), 1, Integer::sum);
            }
            Container builderSupply = builder.supply != null && level.isLoaded(builder.supply) ? StorageOps.at(level, builder.supply) : null;
            for (Map.Entry<String, Integer> e : remaining.entrySet()) {
                if (recipesFor(level, e.getKey()).isEmpty()) continue;
                int have = builderSupply == null ? 0 : countId(builderSupply, e.getKey());
                int need = e.getValue() - have;
                if (need > 0) wanted.merge(e.getKey(), need, Integer::sum);
            }
        }
        // Replace the automatic orders; the owner's own orders are left alone.
        worker.orders.removeIf(Worker.Order::auto);
        Container out = worker.output != null && level.isLoaded(worker.output) ? StorageOps.at(level, worker.output) : null;
        for (Map.Entry<String, Integer> e : wanted.entrySet()) {
            if (worker.orders.size() >= MAX_ORDERS) break;
            boolean manual = worker.orders.stream().anyMatch(o -> o.item().equals(e.getKey()));
            if (manual) continue;
            int done = (out == null ? 0 : countId(out, e.getKey())) + countId(worker.bag, e.getKey());
            worker.orders.add(new Worker.Order(e.getKey(), e.getValue(), Math.min(done, e.getValue()), true));
        }
    }

    static int countId(Container container, String itemId) {
        int n = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && itemId.equals(ExtraJobs.itemId(s))) n += s.getCount();
        }
        return n;
    }
}
