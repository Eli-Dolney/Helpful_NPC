package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    static boolean tryCraft(ServerLevel level, Worker worker, Class<?> wanted) {
        if (worker.craftingTable == null || !level.isLoaded(worker.craftingTable)
            || !level.getBlockState(worker.craftingTable).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)) return false;
        Container supply = worker.supply != null && level.getBlockEntity(worker.supply) instanceof Container chest ? chest : null;
        for (String id : worker.recipes) {
            if (!isSupported(level, id).success()) continue;
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null) continue;
            var holder = level.getRecipeManager().byKey(key);
            if (holder.isEmpty() || !(holder.get().value() instanceof CraftingRecipe recipe)) continue;
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty() || !wanted.isInstance(result.getItem())) continue;
            List<Source> chosen = new ArrayList<>();
            boolean possible = true;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                Source source = find(ingredient, worker, supply, chosen);
                if (source == null) { possible = false; break; }
                chosen.add(source);
            }
            if (!possible || chosen.isEmpty()) continue;
            for (Source source : chosen) { source.inventory.removeItem(source.slot,1); source.inventory.setChanged(); }
            worker.collect(result.copy());
            worker.status = "Crafted " + result.getHoverName().getString();
            return true;
        }
        return false;
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
