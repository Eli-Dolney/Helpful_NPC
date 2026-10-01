package com.example.helpfulworkers;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Jobs for rancher, fisher, smelter, courier. */
final class ExtraJobs {
    private ExtraJobs() {}

    static void rancher(ServerLevel level, Worker worker) {
        Worker.AreaBounds box = worker.areaBounds();
        if (box == null) { worker.setStatus("Set a pen area"); worker.markIdle(); return; }
        AABB area = new AABB(box.minX(), box.minY() - 1, box.minZ(), box.maxX() + 1, box.maxY() + 3, box.maxZ() + 1);

        if (worker.culling) {
            if (tryCull(level, worker, area)) return;
            WorkerActions.returnBorrowedSword(worker);
            worker.culling = false;
            worker.setStatus("Herd thinned to 2 each");
            worker.markWorkFound();
            return;
        }

        if (worker.ranchEggs) {
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area)) {
                if (drop.getItem().is(Items.EGG) && worker.ranchAnimals.contains("chicken")) {
                    if (!worker.approach(drop.blockPosition())) return;
                    worker.collect(drop.getItem().copy());
                    drop.discard();
                    worker.setStatus("Collected eggs");
                    worker.markWorkFound();
                    return;
                }
            }
        }

        if (worker.ranchShear && worker.ranchAnimals.contains("sheep")) {
            List<Sheep> sheep = level.getEntitiesOfClass(Sheep.class, area, s -> !s.isBaby() && !s.isSheared());
            for (Sheep s : sheep) {
                if (!Jobs.tool(level, worker, ShearsItem.class, "shears")
                    && !takeOrSupply(level, worker, new ItemStack(Items.SHEARS))) {
                    break;
                }
                if (!worker.approach(s.blockPosition())) return;
                s.setSheared(true);
                int count = 1 + level.random.nextInt(3);
                worker.collect(new ItemStack(woolOf(s.getColor()), count));
                if (worker.getMainHandItem().getItem() instanceof ShearsItem) {
                    worker.getMainHandItem().hurtAndBreak(1, worker, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                }
                level.playSound(null, s.blockPosition(), net.minecraft.sounds.SoundEvents.SHEEP_SHEAR,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
                worker.setStatus("Sheared sheep");
                worker.markWorkFound();
                return;
            }
        }

        if (worker.ranchMilk && worker.ranchAnimals.contains("cow")) {
            List<Cow> cows = level.getEntitiesOfClass(Cow.class, area, c -> !c.isBaby());
            if (!cows.isEmpty() && countItem(worker, Items.BUCKET) > 0) {
                Cow cow = cows.get(0);
                if (!worker.approach(cow.blockPosition())) return;
                if (Jobs.take(level, worker, new ItemStack(Items.BUCKET))) {
                    worker.collect(new ItemStack(Items.MILK_BUCKET));
                    worker.setStatus("Milked cow");
                    worker.markWorkFound();
                    return;
                }
            }
        }

        if (worker.ranchBreed) {
            if ((worker.ranchAnimals.contains("cow") && tryBreed(level, worker, area, Cow.class))
                || (worker.ranchAnimals.contains("sheep") && tryBreed(level, worker, area, Sheep.class))
                || (worker.ranchAnimals.contains("pig") && tryBreed(level, worker, area, Pig.class))
                || (worker.ranchAnimals.contains("chicken") && tryBreed(level, worker, area, Chicken.class))) {
                return;
            }
        }

        worker.setStatus("Pen quiet — watching animals");
        worker.markIdle();
    }

    /** Cull adults of enabled species down to 2. Returns true if still busy. */
    private static boolean tryCull(ServerLevel level, Worker worker, AABB area) {
        int left = 0;
        for (String species : List.of("cow", "sheep", "pig", "chicken")) {
            if (!worker.ranchAnimals.contains(species)) continue;
            Class<? extends Animal> type = switch (species) {
                case "cow" -> Cow.class;
                case "sheep" -> Sheep.class;
                case "pig" -> Pig.class;
                default -> Chicken.class;
            };
            List<? extends Animal> adults = level.getEntitiesOfClass(type, area, a -> !a.isBaby());
            left += Math.max(0, adults.size() - 2);
            if (adults.size() > 2) {
                Animal extra = adults.get(adults.size() - 1);
                if (!worker.approach(extra.blockPosition())) return true;
                extra.hurt(level.damageSources().mobAttack(worker), 40);
                worker.setStatus("Culling: " + (left - 1) + " left");
                worker.markWorkFound();
                return true;
            }
        }
        return false;
    }

    private static <T extends Animal> boolean tryBreed(ServerLevel level, Worker worker, AABB area, Class<T> type) {
        List<T> ready = level.getEntitiesOfClass(type, area,
            a -> !a.isBaby() && a.getAge() == 0 && !a.isInLove() && a.canFallInLove());
        if (ready.size() >= WorkerConfig.rancherCap()) return false;
        if (ready.size() < 2) return false;
        T a = ready.get(0);
        T b = ready.get(1);
        ItemStack foodA = findBreedFood(level, worker, a);
        ItemStack foodB = findBreedFood(level, worker, b);
        if (foodA.isEmpty() || foodB.isEmpty()) return false;
        if (!worker.approach(a.blockPosition())) return true;
        if (!consumeBreedFood(level, worker, foodA) || !consumeBreedFood(level, worker, foodB)) return false;
        a.setInLove(null);
        b.setInLove(null);
        worker.setStatus("Breeding " + type.getSimpleName().toLowerCase());
        worker.markWorkFound();
        return true;
    }

    private static ItemStack findBreedFood(ServerLevel level, Worker worker, Animal animal) {
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (!stack.isEmpty() && animal.isFood(stack)) return stack.copyWithCount(1);
        }
        if (worker.getMainHandItem().isEmpty() == false && animal.isFood(worker.getMainHandItem())) {
            return worker.getMainHandItem().copyWithCount(1);
        }
        if (worker.supply == null || !(level.getBlockEntity(worker.supply) instanceof Container chest)) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (!stack.isEmpty() && animal.isFood(stack)) return stack.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    private static boolean consumeBreedFood(ServerLevel level, Worker worker, ItemStack want) {
        if (Jobs.take(level, worker, want)) return true;
        return takeFromSupply(level, worker, want);
    }

    private static Item woolOf(DyeColor color) {
        return switch (color) {
            case WHITE -> Items.WHITE_WOOL;
            case ORANGE -> Items.ORANGE_WOOL;
            case MAGENTA -> Items.MAGENTA_WOOL;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_WOOL;
            case YELLOW -> Items.YELLOW_WOOL;
            case LIME -> Items.LIME_WOOL;
            case PINK -> Items.PINK_WOOL;
            case GRAY -> Items.GRAY_WOOL;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_WOOL;
            case CYAN -> Items.CYAN_WOOL;
            case PURPLE -> Items.PURPLE_WOOL;
            case BLUE -> Items.BLUE_WOOL;
            case BROWN -> Items.BROWN_WOOL;
            case GREEN -> Items.GREEN_WOOL;
            case RED -> Items.RED_WOOL;
            case BLACK -> Items.BLACK_WOOL;
        };
    }

    static void fisher(ServerLevel level, Worker worker) {
        Worker.AreaBounds box = worker.areaBounds();
        if (box == null) { worker.setStatus("Set a fishing area"); worker.markIdle(); return; }
        BlockPos shore = findShore(level, worker, box);
        if (shore == null) { worker.setStatus("No shore water in area"); worker.markIdle(); return; }
        if (!worker.approachWithin(shore, 2, 2)) return;
        if (!(worker.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem)
            && !Jobs.tool(level, worker, net.minecraft.world.item.FishingRodItem.class, "fishing rod")) {
            worker.setStatus("Missing fishing rod");
            return;
        }
        if (worker.idleUntil > level.getGameTime()) {
            worker.setStatus("Waiting to recast");
            return;
        }
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FISHING,
            shore.getX() + 0.5, shore.getY() + 1.0, shore.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.02);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(shore))
            .withParameter(LootContextParams.TOOL, worker.getMainHandItem())
            .withOptionalParameter(LootContextParams.THIS_ENTITY, worker)
            .create(LootContextParamSets.FISHING);
        List<ItemStack> loot = table.getRandomItems(params);
        for (ItemStack stack : loot) worker.collect(stack);
        worker.getMainHandItem().hurtAndBreak(1, worker, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        worker.idleUntil = level.getGameTime() + 20L * (20 + level.random.nextInt(21));
        worker.setStatus("Caught fish (" + loot.size() + ")");
        worker.markWorkFound();
    }

    private static BlockPos findShore(ServerLevel level, Worker worker, Worker.AreaBounds box) {
        int budget = WorkerConfig.scanBudget();
        for (int i = 0; i < budget; i++) {
            int index = Math.floorMod(worker.scanCursor++, box.dx() * box.dz());
            int x = box.minX() + index % box.dx();
            int z = box.minZ() + index / box.dx();
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, y - 1, z);
            if (!level.isLoaded(ground)) continue;
            if (ground.getY() < box.minY() - 2 || ground.getY() > box.maxY() + 2) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos water = ground.relative(d);
                if (level.getBlockState(water).getFluidState().is(net.minecraft.tags.FluidTags.WATER)
                    && Worker.canStandAt(level, ground.above())) {
                    return ground.above();
                }
            }
        }
        return null;
    }

    static void smelter(ServerLevel level, Worker worker) {
        if (worker.furnaces.isEmpty()) { worker.setStatus("Assign furnaces"); worker.markIdle(); return; }
        if (worker.supply == null) { worker.setStatus("Assign a supply chest"); worker.markIdle(); return; }
        if (!(level.getBlockEntity(worker.supply) instanceof Container supply)) {
            worker.setStatus("Supply chest missing"); return;
        }
        for (BlockPos furnacePos : worker.furnaces) {
            if (!level.isLoaded(furnacePos)) continue;
            if (!(level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace)) continue;
            ItemStack result = furnace.getItem(2);
            if (!result.isEmpty()) {
                if (!worker.approach(furnacePos)) return;
                ItemStack take = result.copy();
                furnace.setItem(2, ItemStack.EMPTY);
                furnace.setChanged();
                worker.collect(take);
                worker.setStatus("Collected smelted items");
                worker.markWorkFound();
                return;
            }
        }
        for (BlockPos furnacePos : worker.furnaces) {
            if (!level.isLoaded(furnacePos)) continue;
            if (!(level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace)) continue;
            BlockState state = level.getBlockState(furnacePos);
            if (!(state.getBlock() instanceof AbstractFurnaceBlock)) continue;

            if (furnace.getItem(0).isEmpty()) {
                ItemStack smeltable = findSmeltable(level, supply, state);
                if (!smeltable.isEmpty()) {
                    if (!worker.approach(furnacePos)) return;
                    ItemStack moved = smeltable.split(Math.min(8, smeltable.getCount()));
                    furnace.setItem(0, moved);
                    furnace.setChanged();
                    supply.setChanged();
                    worker.setStatus("Loaded furnace");
                    worker.markWorkFound();
                    return;
                }
            }
            if (furnace.getItem(1).isEmpty()) {
                ItemStack fuel = findFuel(supply);
                if (!fuel.isEmpty()) {
                    if (!worker.approach(furnacePos)) return;
                    ItemStack moved = fuel.split(Math.min(8, fuel.getCount()));
                    furnace.setItem(1, moved);
                    furnace.setChanged();
                    supply.setChanged();
                    worker.setStatus("Fueled furnace");
                    worker.markWorkFound();
                    return;
                }
            }
        }
        if (worker.output != null && !Jobs.bagMostlyEmpty(worker)) {
            if (!Jobs.depositPublic(level, worker)) return;
            worker.markWorkFound();
            return;
        }
        worker.setStatus("Furnaces idle");
        worker.markIdle();
    }

    private static ItemStack findSmeltable(ServerLevel level, Container supply, BlockState furnaceState) {
        boolean blast = furnaceState.is(Blocks.BLAST_FURNACE);
        boolean smoker = furnaceState.is(Blocks.SMOKER);
        for (int i = 0; i < supply.getContainerSize(); i++) {
            ItemStack stack = supply.getItem(i);
            if (stack.isEmpty()) continue;
            var recipe = level.getRecipeManager().getRecipeFor(
                net.minecraft.world.item.crafting.RecipeType.SMELTING,
                new net.minecraft.world.item.crafting.SingleRecipeInput(stack), level);
            if (recipe.isEmpty()) continue;
            if (blast) {
                var r = level.getRecipeManager().getRecipeFor(
                    net.minecraft.world.item.crafting.RecipeType.BLASTING,
                    new net.minecraft.world.item.crafting.SingleRecipeInput(stack), level);
                if (r.isEmpty()) continue;
            } else if (smoker) {
                var r = level.getRecipeManager().getRecipeFor(
                    net.minecraft.world.item.crafting.RecipeType.SMOKING,
                    new net.minecraft.world.item.crafting.SingleRecipeInput(stack), level);
                if (r.isEmpty()) continue;
            }
            return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findFuel(Container supply) {
        for (int i = 0; i < supply.getContainerSize(); i++) {
            ItemStack stack = supply.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL) || stack.is(Items.COAL_BLOCK)
                || stack.is(Items.LAVA_BUCKET) || stack.is(Items.BLAZE_ROD)
                || stack.is(ItemTags.LOGS_THAT_BURN) || stack.is(ItemTags.PLANKS)
                || stack.is(Items.STICK) || stack.is(Items.DRIED_KELP_BLOCK)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    static void courier(ServerLevel level, Worker worker) {
        if (worker.pickups.isEmpty()) { worker.setStatus("Assign pickup chests"); worker.markIdle(); return; }
        if (worker.output == null) { worker.setStatus("Assign drop-off chest"); worker.markIdle(); return; }

        if (!Jobs.bagMostlyEmpty(worker)) {
            if (!Jobs.depositPublic(level, worker)) return;
            worker.markWorkFound();
            return;
        }

        for (BlockPos pickup : worker.pickups) {
            if (!level.isLoaded(pickup)) continue;
            if (!(level.getBlockEntity(pickup) instanceof Container chest)) continue;
            ItemStack take = firstNonEmpty(chest);
            if (take.isEmpty()) continue;
            if (!worker.approach(pickup)) return;
            ItemStack moved = take.split(Math.min(take.getCount(), take.getMaxStackSize()));
            chest.setChanged();
            worker.collect(moved);
            worker.setStatus("Picked up from " + pickup.toShortString());
            worker.markWorkFound();
            return;
        }
        worker.setStatus("Route clear — nothing to haul");
        worker.markIdle();
    }

    private static ItemStack firstNonEmpty(Container chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean takeOrSupply(ServerLevel level, Worker worker, ItemStack template) {
        if (Jobs.take(level, worker, template)) {
            if (template.getItem() instanceof ShearsItem) {
                for (int i = 0; i < worker.bag.getContainerSize(); i++) {
                    ItemStack s = worker.bag.getItem(i);
                    if (s.getItem() instanceof ShearsItem) {
                        worker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, s.split(1));
                        return true;
                    }
                }
            }
            return true;
        }
        return false;
    }

    private static boolean takeFromSupply(ServerLevel level, Worker worker, ItemStack want) {
        if (worker.supply == null || !(level.getBlockEntity(worker.supply) instanceof Container chest)) return false;
        int need = want.getCount();
        for (int i = 0; i < chest.getContainerSize() && need > 0; i++) {
            ItemStack stack = chest.getItem(i);
            if (!ItemStack.isSameItemSameComponents(stack, want)) continue;
            int take = Math.min(need, stack.getCount());
            ItemStack moved = stack.split(take);
            worker.collect(moved);
            need -= take;
            chest.setChanged();
        }
        return need <= 0;
    }

    private static int countItem(Worker worker, Item item) {
        int n = 0;
        if (worker.getMainHandItem().is(item)) n += worker.getMainHandItem().getCount();
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack s = worker.bag.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }
}
