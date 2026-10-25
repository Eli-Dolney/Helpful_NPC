package com.example.helpfulworkers;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
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

/** Jobs for rancher (incl. beekeeping), fisher, smelter, courier, and the furnace feeding they share. */
final class ExtraJobs {
    private ExtraJobs() {}

    static void rancher(ServerLevel level, Worker worker) {
        Worker.AreaBounds box = worker.areaBounds();
        if (box == null) { worker.setStatusKey(Lang.STATUS_SET_PEN_AREA); worker.markIdle(); return; }
        AABB area = new AABB(box.minX(), box.minY() - 1, box.minZ(), box.maxX() + 1, box.maxY() + 3, box.maxZ() + 1);

        if (worker.culling) {
            if (tryCull(level, worker, area)) return;
            WorkerActions.returnBorrowedSword(worker);
            worker.culling = false;
            worker.setStatusKey(Lang.STATUS_HERD_THINNED);
            worker.markWorkFound();
            return;
        }

        if (worker.ranchEggs) {
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area)) {
                if (drop.getItem().is(Items.EGG) && worker.ranchAnimals.contains("chicken")) {
                    if (!worker.approach(drop.blockPosition())) return;
                    worker.collect(drop.getItem().copy());
                    drop.discard();
                    worker.setStatusKey(Lang.STATUS_COLLECTED_EGGS);
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
                worker.setStatusKey(Lang.STATUS_SHEARED_SHEEP);
                worker.markWorkFound();
                return;
            }
        }

        if (worker.ranchHoney && tryHarvestHoney(level, worker, box)) return;

        if (worker.ranchMilk && worker.ranchAnimals.contains("cow")) {
            List<Cow> cows = level.getEntitiesOfClass(Cow.class, area, c -> !c.isBaby());
            if (!cows.isEmpty() && countItem(worker, Items.BUCKET) > 0) {
                Cow cow = cows.get(0);
                if (!worker.approach(cow.blockPosition())) return;
                if (Jobs.take(level, worker, new ItemStack(Items.BUCKET))) {
                    worker.collect(new ItemStack(Items.MILK_BUCKET));
                    worker.setStatusKey(Lang.STATUS_MILKED_COW);
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

        worker.setStatusKey(Lang.STATUS_PEN_QUIET);
        worker.markIdle();
    }

    // ---------------------------------------------------------------- beekeeping

    /** Blocks above the pen that are still searched for hives (hives sit on fences and trees). */
    private static final int HIVE_HEADROOM = 3;

    /**
     * Bottle or shear a full hive in the pen, but only when a lit campfire smokes it from within
     * five blocks below (the vanilla rule that keeps the bees calm). Bottles come first, then
     * shears; either is fetched from the supply chest on foot when the bag has none. True while busy.
     */
    private static boolean tryHarvestHoney(ServerLevel level, Worker worker, Worker.AreaBounds box) {
        BlockPos hive = findFullHive(level, worker, box);
        if (hive == null) return false;
        boolean bottles = countItem(worker, Items.GLASS_BOTTLE) > 0;
        boolean shears = Jobs.toolAvailable(worker, ShearsItem.class);
        if (!bottles && !shears) {
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (Jobs.fetchFromSupply(level, worker, Jobs.itemKey(bottle), s -> s.is(Items.GLASS_BOTTLE),
                1, Jobs.FETCH_BATCH, "glass bottles", bottle)) return true;
            if (Jobs.tool(level, worker, ShearsItem.class, "shears")) shears = true;
            else if (worker.nav.hasErrand()) return true;
            else { worker.setStatusKey(Lang.STATUS_NEED_BOTTLES_OR_SHEARS); return false; }
        }
        if (!worker.approach(hive)) return true;
        BlockState state = level.getBlockState(hive);
        if (!isFullHive(level, hive, state)) return true;
        if (bottles) {
            if (!Jobs.take(level, worker, new ItemStack(Items.GLASS_BOTTLE))) return true;
            worker.collect(new ItemStack(Items.HONEY_BOTTLE));
            level.playSound(null, hive, net.minecraft.sounds.SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0f, 1.0f);
            worker.setStatusKey(Lang.STATUS_BOTTLED_HONEY, hive.toShortString());
        } else {
            if (!Jobs.tool(level, worker, ShearsItem.class, "shears")) return true;
            worker.collect(new ItemStack(Items.HONEYCOMB, 3));
            worker.getMainHandItem().hurtAndBreak(1, worker, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            level.playSound(null, hive, net.minecraft.sounds.SoundEvents.BEEHIVE_SHEAR, SoundSource.NEUTRAL, 1.0f, 1.0f);
            worker.setStatusKey(Lang.STATUS_COLLECTED_HONEYCOMB, hive.toShortString());
        }
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        level.setBlock(hive, state.setValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL, 0), 3);
        worker.markWorkFound();
        return true;
    }

    static boolean isFullHive(ServerLevel level, BlockPos pos, BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.BEEHIVES)
            && state.hasProperty(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL)
            && state.getValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL)
                >= net.minecraft.world.level.block.BeehiveBlock.MAX_HONEY_LEVELS
            && net.minecraft.world.level.block.CampfireBlock.isSmokeyPos(level, pos);
    }

    /** Budgeted scan of the pen (and a little above it) for a full, smoked hive; the cursor carries over between ticks. */
    private static BlockPos findFullHive(ServerLevel level, Worker worker, Worker.AreaBounds box) {
        int dx = box.dx(), dz = box.dz(), dy = box.dy() + HIVE_HEADROOM;
        long volume = (long) dx * dy * dz;
        if (volume <= 0) return null;
        int budget = WorkerConfig.scanBudget() * 4;
        for (int checked = 0; checked < Math.min(volume, budget); checked++) {
            int index = (int) Math.floorMod((long) worker.scanCursor++, volume);
            int yOff = index / (dx * dz);
            int zOff = (index / dx) % dz;
            int xOff = index % dx;
            BlockPos pos = new BlockPos(box.minX() + xOff, box.minY() + yOff, box.minZ() + zOff);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.is(net.minecraft.tags.BlockTags.BEEHIVES)) continue;
            if (isFullHive(level, pos, state) && !worker.isUnreachable(pos)) return pos;
        }
        return null;
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
                worker.setStatusKey(Lang.STATUS_CULLING, left - 1);
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
        // Food still in the supply chest starts a trip there; stay busy until it is in the bag.
        if (!consumeBreedFood(level, worker, foodA) || !consumeBreedFood(level, worker, foodB)) return worker.nav.hasErrand();
        worker.gainXp(2);
        a.setInLove(null);
        b.setInLove(null);
        worker.setStatusKey(Lang.STATUS_BREEDING, type.getSimpleName().toLowerCase());
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

    /** Bag first; otherwise {@link Jobs#take} walks to the supply chest instead of reaching into it from afar. */
    private static boolean consumeBreedFood(ServerLevel level, Worker worker, ItemStack want) {
        return Jobs.take(level, worker, want);
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
        if (box == null) { worker.setStatusKey(Lang.STATUS_SET_FISHING_AREA); worker.markIdle(); return; }
        BlockPos shore = findShore(level, worker, box);
        if (shore == null) { worker.setStatusKey(Lang.STATUS_NO_SHORE_WATER); worker.markIdle(); return; }
        if (!worker.approachWithin(shore, 2, 2)) return;
        if (!(worker.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem)
            && !Jobs.tool(level, worker, net.minecraft.world.item.FishingRodItem.class, "fishing rod")) {
            worker.setStatusKey(Lang.STATUS_MISSING_FISHING_ROD);
            return;
        }
        if (worker.idleUntil > level.getGameTime()) {
            worker.setStatusKey(Lang.STATUS_WAITING_RECAST);
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
        worker.setStatusKey(Lang.STATUS_CAUGHT_FISH, loot.size());
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

    // ---------------------------------------------------------------- smelter

    /**
     * Smelter loop: empty result slots, carry what was fetched to the furnaces, drop results off,
     * then go to the supply chest for the next smeltables or fuel. The supply is only peeked at
     * remotely; everything travels in the bag on foot.
     */
    static void smelter(ServerLevel level, Worker worker) {
        if (worker.furnaces.isEmpty()) { worker.setStatusKey(Lang.STATUS_ASSIGN_FURNACES); worker.markIdle(); return; }
        if (worker.supply == null) { worker.setStatusKey(Lang.STATUS_ASSIGN_SUPPLY); worker.markIdle(); return; }
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
                worker.setStatusKey(Lang.STATUS_COLLECTED, take.getCount(), StatusText.name(take));
                worker.markWorkFound();
                return;
            }
        }
        if (deliverFurnaceHaul(level, worker)) return;
        if (worker.output != null && Jobs.depositWorthwhile(worker)) {
            if (!Jobs.depositPublic(level, worker)) return;
            worker.markWorkFound();
            return;
        }
        if (fetchForFurnaces(level, worker)) return;
        worker.setStatusKey(Lang.STATUS_FURNACES_IDLE);
        worker.markIdle();
    }

    // ---------------------------------------------------------------- furnace feeding (smelter and courier)

    /** Anything with a burn time is fuel: data-driven, so modded fuels and datapack changes count. */
    static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    /** Recipe type the furnace block cooks with: blast furnaces take ores, smokers food, furnaces everything. */
    static RecipeType<? extends AbstractCookingRecipe> cookingType(BlockState furnaceState) {
        if (furnaceState.is(Blocks.BLAST_FURNACE)) return RecipeType.BLASTING;
        if (furnaceState.is(Blocks.SMOKER)) return RecipeType.SMOKING;
        return RecipeType.SMELTING;
    }

    static boolean isSmeltableIn(ServerLevel level, ItemStack stack, BlockState furnaceState) {
        if (stack.isEmpty()) return false;
        return level.getRecipeManager().getRecipeFor(cookingType(furnaceState), new SingleRecipeInput(stack), level).isPresent();
    }

    /** Furnace slot that would take the stack right now (0 input, 1 fuel), or -1. */
    private static int acceptingSlot(ServerLevel level, AbstractFurnaceBlockEntity furnace, BlockState furnaceState, ItemStack stack) {
        if (isSmeltableIn(level, stack, furnaceState) && hasRoom(furnace.getItem(0), stack)) return 0;
        if (isFuel(stack) && hasRoom(furnace.getItem(1), stack)) return 1;
        return -1;
    }

    private static boolean hasRoom(ItemStack slot, ItemStack stack) {
        return slot.isEmpty() || (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize());
    }

    /**
     * Carry fetched smeltables and fuel from the bag into the furnaces, a stack at a time. True while
     * walking or after loading something. Haul items a furnace cannot take right now stay in the bag
     * (deposit leaves them alone) until a slot frees up.
     */
    static boolean deliverFurnaceHaul(ServerLevel level, Worker worker) {
        if (worker.furnaceHaul.isEmpty()) return false;
        if (!worker.nav.hasErrand()) worker.furnaceHaul.removeIf(id -> countItemId(worker, id) == 0);
        if (worker.furnaceHaul.isEmpty()) return false;
        for (BlockPos furnacePos : worker.furnaces) {
            if (!level.isLoaded(furnacePos) || worker.isUnreachable(furnacePos)) continue;
            if (!(level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace)) continue;
            BlockState furnaceState = level.getBlockState(furnacePos);
            for (int i = 0; i < worker.bag.getContainerSize(); i++) {
                ItemStack stack = worker.bag.getItem(i);
                if (stack.isEmpty() || !worker.furnaceHaul.contains(itemId(stack))) continue;
                int slot = acceptingSlot(level, furnace, furnaceState, stack);
                if (slot < 0) continue;
                if (!worker.approach(furnacePos)) return true;
                ItemStack current = furnace.getItem(slot);
                int room = current.isEmpty() ? stack.getMaxStackSize() : current.getMaxStackSize() - current.getCount();
                int n = Math.min(room, stack.getCount());
                if (current.isEmpty()) furnace.setItem(slot, stack.split(n));
                else { current.grow(n); stack.shrink(n); }
                furnace.setChanged();
                worker.bag.setChanged();
                worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                worker.setStatusKey(slot == 0 ? Lang.STATUS_LOADED_FURNACE : Lang.STATUS_FUELLED_FURNACE,
                    StatusText.name(furnaceState.getBlock()), n, StatusText.name(stack));
                worker.markWorkFound();
                return true;
            }
        }
        return false;
    }

    /**
     * Find a furnace with an empty input or fuel slot and start a supply trip for a stack of what
     * it needs. The supply chest is only looked into remotely; the items arrive in the bag and
     * {@link #deliverFurnaceHaul} carries them over. True when a trip was started or is pending.
     */
    static boolean fetchForFurnaces(ServerLevel level, Worker worker) {
        if (worker.supply == null) return false;
        if (worker.nav.hasErrand()) return true;
        Container supply = Jobs.supplyContainer(level, worker);
        if (supply == null) { worker.setStatusKey(Lang.STATUS_SUPPLY_CHEST_MISSING); return false; }
        for (BlockPos furnacePos : worker.furnaces) {
            if (!level.isLoaded(furnacePos) || worker.isUnreachable(furnacePos)) continue;
            if (!(level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace)) continue;
            BlockState furnaceState = level.getBlockState(furnacePos);
            if (furnace.getItem(0).isEmpty() && !carrying(level, worker, s -> wantsToCook(level, worker, s, furnaceState))) {
                ItemStack want = firstMatching(supply, s -> wantsToCook(level, worker, s, furnaceState));
                if (!want.isEmpty() && startHaul(level, worker, want)) return true;
            }
            if (furnace.getItem(1).isEmpty() && !carrying(level, worker, ExtraJobs::isFuel)) {
                ItemStack want = firstMatching(supply, ExtraJobs::isFuel);
                if (!want.isEmpty() && startHaul(level, worker, want)) return true;
            }
        }
        return false;
    }

    /** Smeltable here, and for a cook only if it comes out as food. */
    static boolean wantsToCook(ServerLevel level, Worker worker, ItemStack stack, BlockState furnaceState) {
        if (!isSmeltableIn(level, stack, furnaceState)) return false;
        return !"cook".equals(worker.role) || CookJobs.cooksIntoFood(level, stack, furnaceState);
    }

    private static boolean startHaul(ServerLevel level, Worker worker, ItemStack want) {
        String id = itemId(want);
        boolean pending = Jobs.fetchFromSupply(level, worker, Jobs.itemKey(want),
            s -> ItemStack.isSameItemSameComponents(s, want), 1, want.getMaxStackSize(),
            want.getHoverName().getString() + " for the furnaces", want);
        if (pending && !worker.furnaceHaul.contains(id)) worker.furnaceHaul.add(id);
        if (pending) worker.markWorkFound();
        return pending;
    }

    private static boolean carrying(ServerLevel level, Worker worker, java.util.function.Predicate<ItemStack> match) {
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (!stack.isEmpty() && worker.furnaceHaul.contains(itemId(stack)) && match.test(stack)) return true;
        }
        return false;
    }

    private static ItemStack firstMatching(Container chest, java.util.function.Predicate<ItemStack> match) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (!stack.isEmpty() && match.test(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    static String itemId(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static int countItemId(Worker worker, String id) {
        int n = 0;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack s = worker.bag.getItem(i);
            if (!s.isEmpty() && id.equals(itemId(s))) n += s.getCount();
        }
        return n;
    }

    // ---------------------------------------------------------------- courier

    /** How many of this item one visit may take from a pickup chest, honouring its filter and "keep N". */
    record Pick(int slot, int count) {}

    /**
     * Courier loop: furnace goods in the bag go to the furnaces, loot goes to the drop-off chest,
     * then the pickups are visited round-robin (each visit takes one stack that passes the chest's
     * filter and leaves its "keep" count behind), and finally the supply chest is raided for the
     * furnaces when there are any.
     */
    static void courier(ServerLevel level, Worker worker) {
        boolean porter = "porter".equals(worker.courierMode);
        if (porter) refreshPorterPickups(level, worker);
        List<BlockPos> pickups = porter ? worker.porterPickups : worker.pickups;
        if (porter && worker.output == null) { worker.setStatusKey(Lang.STATUS_ASSIGN_DROPOFF); worker.markIdle(); return; }
        if (porter && pickups.isEmpty()) { worker.setStatusKey(Lang.STATUS_PORTER_NO_WORKERS); worker.markIdle(); return; }
        boolean route = !pickups.isEmpty() && worker.output != null;
        boolean furnaceRun = !worker.furnaces.isEmpty() && worker.supply != null;
        if (!route && !furnaceRun) {
            if (worker.pickups.isEmpty() && worker.furnaces.isEmpty()) worker.setStatusKey(Lang.STATUS_ASSIGN_PICKUPS);
            else if (!worker.pickups.isEmpty() && worker.output == null) worker.setStatusKey(Lang.STATUS_ASSIGN_DROPOFF);
            else worker.setStatusKey(Lang.STATUS_ASSIGN_SUPPLY_FURNACES);
            worker.markIdle();
            return;
        }
        if (deliverFurnaceHaul(level, worker)) return;
        if (worker.output != null && Jobs.hasDepositableLoot(worker)) {
            if (!Jobs.depositPublic(level, worker)) return;
            worker.markWorkFound();
            return;
        }
        if (route && pickupRound(level, worker, pickups)) return;
        if (furnaceRun && fetchForFurnaces(level, worker)) return;
        worker.setStatusKey(Lang.STATUS_ROUTE_CLEAR);
        worker.markIdle();
    }

    /** Visit the next pickup chest (from the saved cursor) that has something takeable. True while busy. */
    /** Porter mode: every owned worker's output chest within range is a pickup (refreshed once a minute). */
    static final int PORTER_RADIUS = 96;
    static final long PORTER_SCAN_INTERVAL = 1200;

    static void refreshPorterPickups(ServerLevel level, Worker worker) {
        long now = level.getGameTime();
        if (worker.lastPorterScan != 0 && now - worker.lastPorterScan < PORTER_SCAN_INTERVAL && !worker.porterPickups.isEmpty()) return;
        worker.lastPorterScan = now;
        worker.porterPickups.clear();
        if (worker.owner == null) return;
        for (Worker mate : level.getEntitiesOfClass(Worker.class, worker.getBoundingBox().inflate(PORTER_RADIUS),
                m -> m != worker && worker.owner.equals(m.owner) && !"courier".equals(m.role) && m.output != null)) {
            BlockPos out = mate.output;
            if (out.equals(worker.output) || worker.porterPickups.contains(out)) continue;
            worker.porterPickups.add(out);
        }
    }

    private static boolean pickupRound(ServerLevel level, Worker worker, List<BlockPos> pickups) {
        int n = pickups.size();
        for (int i = 0; i < n; i++) {
            int index = Math.floorMod(worker.courierCursor + i, n);
            BlockPos pickup = pickups.get(index);
            if (!level.isLoaded(pickup) || worker.isUnreachable(pickup)) continue;
            if (!(level.getBlockEntity(pickup) instanceof Container chest)) continue;
            Pick pick = pickFrom(worker, pickup, chest);
            if (pick == null) continue;
            if (!worker.approach(pickup)) return true;
            pick = pickFrom(worker, pickup, chest); // the chest may have changed on the way
            if (pick == null) continue;
            ItemStack moved = chest.getItem(pick.slot()).split(pick.count());
            chest.setChanged();
            worker.collect(moved);
            worker.courierCursor = index + 1;
            worker.setStatusKey(Lang.STATUS_PICKED_UP_FROM_CHEST, moved.getCount(), StatusText.name(moved), index + 1);
            worker.markWorkFound();
            return true;
        }
        return false;
    }

    static Pick pickFrom(Worker worker, BlockPos pickup, Container chest) {
        List<String> filter = worker.pickupFilters.getOrDefault(pickup.asLong(), List.of());
        int keep = worker.pickupKeep.getOrDefault(pickup.asLong(), 0);
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.isEmpty()) continue;
            if (!filter.isEmpty() && !filter.contains(itemId(stack))) continue;
            int allowed = stack.getCount();
            if (keep > 0) {
                int total = 0;
                for (int j = 0; j < chest.getContainerSize(); j++) {
                    ItemStack other = chest.getItem(j);
                    if (ItemStack.isSameItemSameComponents(other, stack)) total += other.getCount();
                }
                allowed = Math.min(allowed, total - keep);
            }
            if (allowed <= 0) continue;
            return new Pick(i, Math.min(allowed, stack.getMaxStackSize()));
        }
        return null;
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
