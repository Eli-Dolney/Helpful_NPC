package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

final class Crafting {
    private Crafting() {}

    /**
     * Craft an approved recipe whose result is of the wanted class. The supply chest is only
     * looked into remotely: ingredients still in it are fetched first (a trip), then the worker
     * walks to the crafting table and crafts from the bag. Returns false while any of that is
     * pending; callers retry next tick and find the result in the bag once it is done.
     */
    static boolean tryCraft(ServerLevel level, Worker worker, Class<?> wanted) {
        if (worker.craftingTable == null || !level.isLoaded(worker.craftingTable)
            || !level.getBlockState(worker.craftingTable).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)) return false;
        if (worker.nav.hasErrand()) return false;
        Container supply = Jobs.supplyContainer(level, worker);
        for (String id : worker.recipes) {
            CraftingRecipe recipe = approved(level, id, wanted);
            if (recipe == null) continue;
            ItemStack result = recipe.getResultItem(level.registryAccess());
            List<Source> chosen = choose(recipe, worker, supply);
            if (chosen == null || chosen.isEmpty()) continue;
            String name = result.getHoverName().getString();

            // Ingredients still in the supply chest: bring every one of them back in one trip.
            Map<Integer, Integer> remote = new LinkedHashMap<>();
            for (Source source : chosen) if (source.inventory == supply) remote.merge(source.slot, 1, Integer::sum);
            if (!remote.isEmpty()) {
                if (worker.isUnreachable(worker.supply)) { worker.setStatusKey(Lang.STATUS_CANT_REACH_SUPPLY); return false; }
                if (!Jobs.hasRoom(worker.bag, null)) { worker.setStatusKey(Lang.STATUS_BAG_FULL_MATERIALS, StatusText.name(result)); return false; }
                List<ItemStack> wants = new ArrayList<>();
                for (Map.Entry<Integer, Integer> e : remote.entrySet()) wants.add(supply.getItem(e.getKey()).copyWithCount(e.getValue()));
                String first = wants.get(0).getHoverName().getString();
                String label = wants.size() == 1 ? first : first + " and " + (wants.size() - 1) + " more";
                BlockPos chestPos = worker.supply;
                worker.nav.requestErrand(chestPos, "Fetching " + label + " from supply", (lvl, w) -> {
                    w.supplyPeek.clear();
                    if (!(lvl.getBlockEntity(chestPos) instanceof Container chest)) { w.setStatusKey(Lang.STATUS_SUPPLY_CHEST_MISSING); return; }
                    int moved = 0;
                    for (ItemStack want : wants) {
                        int grab = Math.max(want.getCount(), Math.min(Jobs.FETCH_BATCH, want.getMaxStackSize()));
                        moved += WorkerNav.pull(w, chest, s -> ItemStack.isSameItemSameComponents(s, want), grab);
                    }
                    w.setStatusKey(moved > 0 ? Lang.STATUS_PICKED_UP_MATERIALS : Lang.STATUS_SUPPLY_NO_MATERIALS, StatusText.name(result));
                });
                return false;
            }

            // Everything is in the bag: craft at the table, walking there first if needed.
            if (worker.nav.isWithin(worker.craftingTable, WorkerNav.CONTAINER_REACH, WorkerNav.CONTAINER_REACH_Y)) {
                return craftNow(level, worker, recipe, result);
            }
            worker.nav.requestErrand(worker.craftingTable, "Walking to the crafting table for " + name,
                (lvl, w) -> craftNow(lvl, w, recipe, result));
            return false;
        }
        return false;
    }

    /** Consume the recipe's ingredients from the bag and collect the result; false if the bag no longer has them. */
    private static boolean craftNow(ServerLevel level, Worker worker, CraftingRecipe recipe, ItemStack result) {
        List<Source> chosen = choose(recipe, worker, null);
        if (chosen == null || chosen.isEmpty()) {
            worker.setStatusKey(Lang.STATUS_MISSING_MATERIALS, StatusText.name(result));
            return false;
        }
        for (Source source : chosen) { source.inventory.removeItem(source.slot, 1); source.inventory.setChanged(); }
        worker.collect(result.copy());
        worker.setStatusKey(Lang.STATUS_CRAFTED, StatusText.name(result));
        return true;
    }

    private static CraftingRecipe approved(ServerLevel level, String id, Class<?> wanted) {
        if (!isSupported(level, id).success()) return null;
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return null;
        var holder = level.getRecipeManager().byKey(key);
        if (holder.isEmpty() || !(holder.get().value() instanceof CraftingRecipe recipe)) return null;
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty() || !wanted.isInstance(result.getItem())) return null;
        return recipe;
    }

    /** One source slot per ingredient from the bag (and the supply when given), or null if any is missing. */
    private static List<Source> choose(CraftingRecipe recipe, Worker worker, Container supply) {
        List<Source> chosen = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            Source source = find(ingredient, worker, supply, chosen);
            if (source == null) return null;
            chosen.add(source);
        }
        return chosen;
    }

    static ActionResult validateRecipe(ServerLevel level, String id) {
        if (level == null) return ActionResult.fail("Level unavailable");
        return isSupported(level, id);
    }

    static ActionResult isSupported(ServerLevel level, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return ActionResult.fail("Invalid recipe id");
        Optional<RecipeHolder<?>> holder = level.getRecipeManager().byKey(key);
        if (holder.isEmpty()) return ActionResult.fail("Unknown recipe " + id);
        if (!(holder.get().value() instanceof CraftingRecipe recipe)) {
            return ActionResult.fail("Only crafting-table recipes are supported");
        }
        if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) {
            return ActionResult.fail("Special crafting recipes are not supported");
        }
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty()) return ActionResult.fail("Recipe has no result");
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.getCraftingRemainingItem().isEmpty()) {
                    return ActionResult.fail("Recipes with crafting remainders are not supported");
                }
            }
        }
        return ActionResult.ok("Supported");
    }

    static List<WorkerNetwork.RecipeEntry> listTeachable(Worker worker) {
        List<WorkerNetwork.RecipeEntry> entries = new ArrayList<>();
        if (!(worker.level() instanceof ServerLevel level)) return entries;
        for (RecipeHolder<?> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            String id = holder.id().toString();
            ItemStack result = holder.value() instanceof CraftingRecipe crafting
                ? crafting.getResultItem(level.registryAccess()) : ItemStack.EMPTY;
            if (result.isEmpty()) continue;
            ActionResult support = isSupported(level, id);
            boolean approved = worker.recipes.contains(id);
            if (!support.success() && !approved) continue;
            entries.add(new WorkerNetwork.RecipeEntry(id, result.getHoverName().getString(), approved,
                support.success(), support.success() ? "" : support.text()));
        }
        entries.sort((a, b) -> {
            if (a.approved() != b.approved()) return a.approved() ? -1 : 1;
            return a.outputName().compareToIgnoreCase(b.outputName());
        });
        if (entries.size() > 200) return new ArrayList<>(entries.subList(0, 200));
        return entries;
    }

    private static Source find(Ingredient ingredient, Worker worker, Container supply, List<Source> chosen) {
        for (Container inventory : new Container[]{worker.bag, supply}) {
            if (inventory == null) continue;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!ingredient.test(stack)) continue;
                int reserved = 0;
                for (Source source : chosen) if (source.inventory == inventory && source.slot == slot) reserved++;
                if (stack.getCount() > reserved) return new Source(inventory, slot);
            }
        }
        return null;
    }

    private record Source(Container inventory, int slot) {}
}
