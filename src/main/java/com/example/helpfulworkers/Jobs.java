package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;

final class Jobs {
    private Jobs() {}

    private static final long UNLOAD_INTERVAL_TICKS = 5L * 60L * 20L; // 5 minutes
    private static final int SAPLING_RESERVE = 16;

    /** Farmer crop ids (the Resources page), the ones on by default, and the gatherer's plant ids. */
    static final List<String> FARMER_CROPS = List.of("wheat", "carrots", "potatoes", "beetroots", "melon", "pumpkin", "nether_wart");
    static final List<String> FARMER_DEFAULT_CROPS = List.of("wheat", "carrots", "potatoes", "beetroots");
    static final List<String> GATHER_RESOURCES = List.of("sugar_cane", "bamboo", "kelp", "sweet_berries", "cactus", "cocoa", "glow_berries");

    static void tick(ServerLevel level, Worker worker) {
        try {
            // A pending supply fetch or crafting trip comes before the job target.
            if (worker.nav.tickErrand()) return;
            if (SiteJobs.tick(level, worker)) { worker.markWorkFound(); return; }
            if ("builder".equals(worker.role)) { Blueprints.tick(level, worker); worker.markWorkFound(); return; }
            if ("smelter".equals(worker.role)) { ExtraJobs.smelter(level, worker); return; }
            if ("courier".equals(worker.role)) {
                if (!handleInventory(level, worker)) return;
                ExtraJobs.courier(level, worker);
                return;
            }
            if ("rancher".equals(worker.role)) {
                if (worker.first == null || worker.second == null) { worker.setStatusKey(Lang.STATUS_SET_PEN_AREA); return; }
                if (!handleInventory(level, worker)) return;
                ExtraJobs.rancher(level, worker);
                return;
            }
            if ("fisher".equals(worker.role)) {
                if (worker.first == null || worker.second == null) { worker.setStatusKey(Lang.STATUS_SET_FISHING_AREA); return; }
                if (!handleInventory(level, worker)) return;
                ExtraJobs.fisher(level, worker);
                return;
            }
            if (worker.first == null || worker.second == null) { worker.setStatusKey(Lang.STATUS_SET_WORK_CORNERS); return; }
            if (!handleInventory(level, worker)) return;

            if ("miner".equals(worker.role) && isPlannerMode(worker.mode)) {
                minePlanned(level, worker);
                worker.markWorkFound();
                return;
            }

            if ("forester".equals(worker.role)) {
                foresterTick(level, worker);
                return;
            }
            // Stalks cut higher up fall as items a tick later; the gatherer sweeps them up before cutting more.
            if ("gatherer".equals(worker.role) && !bagFull(worker) && sweepDrops(level, worker, Jobs::isGatherDrop, 4)) {
                worker.markWorkFound();
                return;
            }

            if (worker.target != null && (!inWorkScope(worker, worker.target) || !eligible(level, worker, worker.target))) {
                worker.target = null;
            }
            if (worker.target == null) worker.target = find(level, worker);
            if (worker.target == null) {
                worker.setStatusKey(Lang.STATUS_NO_REACHABLE_WORK);
                worker.markIdle();
                return;
            }
            if (!worker.approachStand(worker.target)) return;
            BlockPos pos = worker.target;
            boolean done = true;
            if ("farmer".equals(worker.role)) done = farm(level, worker, pos);
            else if ("miner".equals(worker.role) && "quarry".equals(worker.mode)) done = quarry(level, worker, pos);
            else if ("miner".equals(worker.role)) done = mine(level, worker, pos);
            else if ("gatherer".equals(worker.role)) done = gather(level, worker, pos);
            else worker.setStatusKey(Lang.STATUS_CHOOSE_ROLE);
            if (done) worker.target = null;
            worker.markWorkFound();
        } catch (Exception ex) {
            HelpfulWorkers.LOGGER.error("Worker {} ({}) job tick failed", worker.getId(), worker.role, ex);
            worker.setStatusKey(Lang.STATUS_PAUSED_ERROR, ex.getClass().getSimpleName());
            worker.working = false;
            worker.target = null;
            worker.getNavigation().stop();
        }
    }

    /** Forester area loop: fell → replant → sweep drops → go home & wait. */
    private static void foresterTick(ServerLevel level, Worker worker) {
        if (!worker.fellQueue.isEmpty()) {
            fellStep(level, worker);
            worker.markWorkFound();
            return;
        }
        refreshTreeIndex(level, worker);
        // Sweep loose forestry drops in the area first if bag not full
        if (!bagFull(worker) && sweepForestryDrops(level, worker)) {
            worker.markWorkFound();
            return;
        }
        // Bone-meal saplings occasionally
        if (worker.useBoneMeal && tryBoneMealSapling(level, worker)) {
            worker.markWorkFound();
            return;
        }
        BlockPos log = null;
        for (BlockPos trunk : worker.treeIndex) {
            if (!level.isLoaded(trunk)) continue;
            if (!isLog(level.getBlockState(trunk))) continue;
            if (worker.isUnreachable(trunk)) continue;
            log = trunk;
            break;
        }
        Worker.AreaBounds forest = worker.areaBounds();
        if (log == null && forest != null && forest.large() && worker.treeColumnsScanned < forest.dx() * forest.dz()) {
            // A big forest is searched a budget of columns per work tick; only rest once all of it was empty.
            worker.setStatusKey(Lang.STATUS_SEARCHING_FOREST,
                (int) (100L * worker.treeColumnsScanned / ((long) forest.dx() * forest.dz())));
            worker.markWorkFound();
            return;
        }
        if (log == null) {
            worker.treeColumnsScanned = 0;
            // Go home / deposit and wait
            if (worker.output != null && hasDepositableLoot(worker)) {
                if (!deposit(level, worker)) return;
            }
            BlockPos home = worker.bed != null ? worker.bed : worker.output;
            if (home != null && level.isLoaded(home) && worker.blockPosition().distSqr(home) > 9) {
                if (!worker.approachWithin(home, 2, 2)) return;
            }
            worker.idleUntil = level.getGameTime() + 20L * (120 + level.random.nextInt(60));
            worker.setStatusKey(Lang.STATUS_FOREST_QUIET);
            worker.markIdle();
            return;
        }
        worker.treeColumnsScanned = 0; // found one: the forest only counts as empty after a full pass without trees
        if (!worker.approachStand(log)) return;
        if (!fell(level, worker, log)) return;
        worker.treeIndex.remove(log);
        worker.markWorkFound();
        fellStep(level, worker);
    }

    private static void refreshTreeIndex(ServerLevel level, Worker worker) {
        Worker.AreaBounds box = worker.areaBounds();
        if (box == null) return;
        long now = level.getGameTime();
        boolean stale = worker.treeIndex.isEmpty() || now - worker.treeIndexRefresh > 20L * 120L;
        if (!stale) return;
        worker.treeIndex.clear();
        worker.treeIndexRefresh = now;
        int budget = WorkerConfig.scanBudget();
        int columns = box.dx() * box.dz();
        for (int checked = 0; checked < Math.min(columns, budget * 4); checked++) {
            worker.treeColumnsScanned++;
            int index = Math.floorMod(worker.scanCursor++, columns);
            int x = box.minX() + index % box.dx();
            int z = box.minZ() + index / box.dx();
            int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos found = null;
            for (int y = Math.min(surface, box.maxY() + 24); y >= Math.max(box.minY() - 1, level.getMinBuildHeight()); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isLoaded(pos)) continue;
                if (isLog(level.getBlockState(pos))) found = pos; // keep scanning down for lowest log
                else if (found != null) break;
            }
            if (found != null) worker.treeIndex.add(found.immutable());
        }
    }

    private static boolean sweepForestryDrops(ServerLevel level, Worker worker) {
        return sweepDrops(level, worker, Jobs::isForestryDrop, 8);
    }

    private static boolean isForestryDrop(ItemStack stack) {
        return stack.is(ItemTags.LOGS) || stack.is(ItemTags.SAPLINGS) || stack.is(Items.STICK) || stack.is(Items.APPLE);
    }

    static boolean isGatherDrop(ItemStack stack) {
        return stack.is(Items.SUGAR_CANE) || stack.is(Items.BAMBOO) || stack.is(Items.KELP) || stack.is(Items.SWEET_BERRIES)
            || stack.is(Items.CACTUS) || stack.is(Items.COCOA_BEANS) || stack.is(Items.GLOW_BERRIES);
    }

    /** Walk to and pocket the first matching item lying in the area (plus {@code above} blocks of headroom). True while busy with one. */
    private static boolean sweepDrops(ServerLevel level, Worker worker, Predicate<ItemStack> match, int above) {
        Worker.AreaBounds box = worker.areaBounds();
        if (box == null) return false;
        AABB area = new AABB(box.minX(), box.minY() - 1, box.minZ(), box.maxX() + 1, box.maxY() + above, box.maxZ() + 1);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || !match.test(stack)) continue;
            if (!worker.approach(entity.blockPosition())) return true;
            worker.collect(stack.copy());
            entity.discard();
            worker.setStatusKey(Lang.STATUS_GATHERED, StatusText.name(stack));
            return true;
        }
        return false;
    }

    /** Pocket matching items within reach of pos without walking (used right after cutting a stalk). */
    private static void pocketDropsNear(ServerLevel level, Worker worker, BlockPos pos, Predicate<ItemStack> match) {
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3))) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || !match.test(stack)) continue;
            worker.collect(stack.copy());
            entity.discard();
        }
    }

    private static boolean tryBoneMealSapling(ServerLevel level, Worker worker) {
        if (countItem(worker, Items.BONE_MEAL) <= 0 && worker.supply == null) return false;
        Worker.AreaBounds box = worker.areaBounds();
        if (box == null) return false;
        int budget = Math.min(32, WorkerConfig.scanBudget());
        for (int i = 0; i < budget; i++) {
            int index = Math.floorMod(worker.scanCursor++, box.dx() * box.dz());
            int x = box.minX() + index % box.dx();
            int z = box.minZ() + index / box.dx();
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock)) continue;
            if (!worker.approach(pos)) return true;
            if (take(level, worker, new ItemStack(Items.BONE_MEAL))
                && BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos)) {
                worker.setStatusKey(Lang.STATUS_BONE_MEALED_SAPLING);
                return true;
            }
            return false;
        }
        return false;
    }

    /** Deposit when full if a chest is set; otherwise go to bed and wait for the owner. */
    static boolean handleInventory(ServerLevel level, Worker worker) {
        if (!shouldUnload(level, worker)) return true;
        worker.needsUnload = true;
        if (worker.output != null) {
            if (!hasDepositableLoot(worker)) {
                worker.setStatusKey(Lang.STATUS_FULL);
                worker.working = false;
                worker.target = null;
                worker.getNavigation().stop();
                return false;
            }
            if (!deposit(level, worker)) return false;
            worker.needsUnload = false;
            worker.lastUnloadGameTime = level.getGameTime();
            return true;
        }
        worker.setStatusKey(Lang.STATUS_FULL);
        worker.working = false;
        worker.target = null;
        worker.getNavigation().stop();
        if (worker.bed != null && level.isLoaded(worker.bed)) {
            if (worker.blockPosition().distSqr(worker.bed) > 4) {
                worker.getNavigation().moveTo(worker.bed.getX() + 0.5, worker.bed.getY(), worker.bed.getZ() + 0.5, 1);
                worker.setStatusKey(Lang.STATUS_FULL_RETURNING);
            }
        }
        return false;
    }

    static final String OUT_OF_SPACE = "Out of space — assign an output chest or clear my inventory.";

    static boolean bagFull(Worker worker) {
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            if (worker.bag.getItem(i).isEmpty()) return false;
        }
        return true;
    }

    private static boolean bagMostlyFull(Worker worker) {
        int used = 0;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            if (!worker.bag.getItem(i).isEmpty()) used++;
        }
        return used * 4 >= worker.bag.getContainerSize() * 3;
    }

    private static boolean shouldUnload(ServerLevel level, Worker worker) {
        if (worker.needsUnload || bagFull(worker)) return true;
        if (!isGatherRole(worker.role)) return false;
        if (bagMostlyFull(worker)) return true;
        if (worker.lastUnloadGameTime == 0) worker.lastUnloadGameTime = level.getGameTime();
        return level.getGameTime() - worker.lastUnloadGameTime >= UNLOAD_INTERVAL_TICKS
            && hasDepositableLoot(worker);
    }

    private static boolean isGatherRole(String role) {
        return WorkerActions.isGatherRole(role);
    }

    private static boolean isPlannerMode(String mode) {
        return "staircase".equals(mode) || "strip".equals(mode) || "colony".equals(mode);
    }

    /** Items fetched for the furnaces stay in the bag until a furnace takes them. */
    static boolean isFurnaceHaul(Worker worker, ItemStack stack) {
        return !worker.furnaceHaul.isEmpty() && worker.furnaceHaul.contains(ExtraJobs.itemId(stack));
    }

    /** Enough loot to make the walk worthwhile: three depositable slots, or a bag that is nearly full. */
    static boolean depositWorthwhile(Worker worker) {
        int slots = 0;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (stack.isEmpty() || isTool(stack) || isFurnaceHaul(worker, stack)) continue;
            slots++;
        }
        return slots >= 3 || (slots > 0 && bagMostlyFull(worker));
    }

    static boolean hasDepositableLoot(Worker worker) {
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (stack.isEmpty() || isTool(stack) || isFurnaceHaul(worker, stack)) continue;
            int reserve = worker.reserveFor(stack);
            if (reserve > 0) {
                if (countItem(worker, stack.getItem()) > reserve) return true;
                continue;
            }
            return true;
        }
        return false;
    }

    private static BlockPos find(ServerLevel level, Worker worker) {
        Worker.AreaBounds box = worker.areaBounds();
        if (box == null) return null;
        long volume = box.volume();
        if (volume > Worker.MAX_AREA_VOLUME) { worker.setStatusKey(Lang.STATUS_AREA_TOO_LARGE); worker.working = false; return null; }
        int dx = box.dx(), dy = box.dy(), dz = box.dz();
        int budget = WorkerConfig.scanBudget();

        if ("forester".equals(worker.role)) {
            // Prefer tree index; fall back to heightmap column scan.
            for (BlockPos trunk : worker.treeIndex) {
                if (level.isLoaded(trunk) && isLog(level.getBlockState(trunk)) && !worker.isUnreachable(trunk)) return trunk;
            }
            int columns = dx * dz;
            for (int checked = 0; checked < Math.min(columns, budget); checked++) {
                int index = Math.floorMod(worker.scanCursor++, columns);
                int zOff = index / dx;
                int xOff = index % dx;
                int x = box.minX() + xOff;
                int z = box.minZ() + zOff;
                int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos log = findLogInColumn(level, worker, x, z, box.minY(), Math.max(box.maxY(), surface));
                if (log != null && !worker.isUnreachable(log)) return log;
            }
            return null;
        }

        boolean terraform = "miner".equals(worker.role) && "terraform".equals(worker.mode);
        boolean quarry = "miner".equals(worker.role) && "quarry".equals(worker.mode);
        if (box.large()) return findLarge(level, worker, box, terraform, quarry, budget);
        for (int checked = 0; checked < Math.min(volume, budget * 4L); checked++) {
            int index = Math.floorMod(worker.scanCursor++, (int) volume);
            int yOff = dy - 1 - index / (dx * dz);
            int zOff = (index / dx) % dz;
            int xOff = index % dx;
            BlockPos pos = new BlockPos(box.minX() + xOff, box.minY() + yOff, box.minZ() + zOff);
            if (!level.isLoaded(pos)) continue;
            // Excavate/branches keep the bottom layer as a floor; a quarry lays its own cobble floor.
            if ("miner".equals(worker.role) && !terraform && !quarry && yOff == 0 && dy > 1) continue;
            if ("miner".equals(worker.role) && "branches".equals(worker.mode)
                && ((yOff != 1 && yOff != 2) || (zOff != dz / 2 && xOff % 4 != 0))) continue;
            if ("farmer".equals(worker.role) && needsWaterCheck(level, worker, pos)
                && !nearWaterCached(level, worker, pos)) continue;
            if (eligible(level, worker, pos) && !worker.isUnreachable(pos)) return pos;
        }
        return null;
    }

    /**
     * Budgeted top-down scan for areas above 32768 blocks. Positions above the terrain surface are
     * skipped from the heightmap (cheap, up to 256x the block budget per call); after the top layer of
     * a pass the highest surface is known, so empty layers above it are jumped over.
     */
    static BlockPos findLarge(ServerLevel level, Worker worker, Worker.AreaBounds box, boolean terraform, boolean quarry, int budget) {
        int dx = box.dx(), dy = box.dy(), dz = box.dz();
        int layer = dx * dz;
        int volume = (int) box.volume();
        int real = 0, cheap = 0, realMax = budget * 4, cheapMax = budget * 256;
        while (real < realMax && cheap < cheapMax) {
            int index = Math.floorMod(worker.scanCursor, volume);
            int layerIndex = index % layer;
            if (index == 0) { worker.scanPeak = Integer.MAX_VALUE; worker.scanPeakAcc = Integer.MIN_VALUE; }
            else if (layerIndex == 0) {
                if (index == layer) worker.scanPeak = worker.scanPeakAcc;
                int y = box.minY() + dy - 1 - index / layer;
                if (worker.scanPeak != Integer.MAX_VALUE && y > worker.scanPeak) {
                    // Nothing but air down to the highest terrain: go straight to that layer (or end the pass).
                    int peakOff = worker.scanPeak - box.minY();
                    worker.scanCursor = peakOff < 0 ? 0 : (dy - 1 - peakOff) * layer;
                    if (peakOff < 0) return null;
                    cheap++;
                    continue;
                }
            }
            worker.scanCursor++;
            int yOff = dy - 1 - index / layer;
            int zOff = layerIndex / dx;
            int xOff = layerIndex % dx;
            int x = box.minX() + xOff, z = box.minZ() + zOff, y = box.minY() + yOff;
            if (!level.hasChunk(x >> 4, z >> 4)) { cheap++; continue; }
            int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (yOff == dy - 1) worker.scanPeakAcc = Math.max(worker.scanPeakAcc, surface);
            if (y > surface) { cheap++; continue; }
            real++;
            BlockPos pos = new BlockPos(x, y, z);
            if ("miner".equals(worker.role) && !terraform && !quarry && yOff == 0 && dy > 1) continue;
            if ("miner".equals(worker.role) && "branches".equals(worker.mode)
                && ((yOff != 1 && yOff != 2) || (zOff != dz / 2 && xOff % 4 != 0))) continue;
            if ("farmer".equals(worker.role) && needsWaterCheck(level, worker, pos)
                && !nearWaterCached(level, worker, pos)) continue;
            if (eligible(level, worker, pos) && !worker.isUnreachable(pos)) return pos;
        }
        return null;
    }

    private static boolean needsWaterCheck(ServerLevel level, Worker worker, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH);
    }

    private static boolean nearWaterCached(ServerLevel level, Worker worker, BlockPos pos) {
        long key = (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
        Boolean cached = worker.waterCache.get(key);
        if (cached != null) return cached;
        boolean ok = hasWaterWithin(level, pos, 4);
        if (worker.waterCache.size() > 2048) worker.waterCache.clear();
        worker.waterCache.put(key, ok);
        return ok;
    }

    /** Footprint columns, including trunk height above a short ground mark. */
    private static boolean inWorkScope(Worker worker, BlockPos pos) {
        if ("forester".equals(worker.role) && worker.first != null && worker.second != null) {
            int minX = Math.min(worker.first.getX(), worker.second.getX());
            int maxX = Math.max(worker.first.getX(), worker.second.getX());
            int minZ = Math.min(worker.first.getZ(), worker.second.getZ());
            int maxZ = Math.max(worker.first.getZ(), worker.second.getZ());
            int minY = Math.min(worker.first.getY(), worker.second.getY());
            if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) return false;
            return pos.getY() >= minY && pos.getY() <= minY + 48;
        }
        return worker.contains(pos);
    }

    /** Prefer the lowest log in the column so the worker can reach the trunk from the ground. */
    private static BlockPos findLogInColumn(ServerLevel level, Worker worker, int x, int z, int minY, int maxY) {
        int top = Math.min(Math.max(maxY + 24, minY + 32), level.getMaxBuildHeight() - 1);
        for (int y = minY; y <= top; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isLoaded(pos)) continue;
            if (!inWorkScope(worker, pos)) continue;
            if (isLog(level.getBlockState(pos))) return pos;
        }
        return null;
    }

    private static boolean eligible(ServerLevel level, Worker worker, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return switch (worker.role) {
            case "farmer" -> farmerEligible(level, worker, pos, state);
            case "forester" -> isLog(state);
            case "miner" -> "quarry".equals(worker.mode)
                ? quarryEligible(level, worker, pos, state) : mineable(level, worker, pos, state);
            case "gatherer" -> gathererEligible(level, worker, pos, state);
            default -> false;
        };
    }

    private static boolean farmerEligible(ServerLevel level, Worker worker, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && cropEnabled(worker, crop)) return true;
        if (state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE
            && cropEnabled(worker, "nether_wart")) return true;
        if ((state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN))
            && (cropEnabled(worker, "melon") || cropEnabled(worker, "pumpkin"))) return true;
        if (canTill(level, worker, pos, state)) return true;
        if (state.is(Blocks.FARMLAND) && level.getBlockState(pos.above()).isAir() && hasPlantableCrop(worker)) return true;
        if (state.is(Blocks.SOUL_SAND) && level.getBlockState(pos.above()).isAir() && cropEnabled(worker, "nether_wart")
            && countItem(worker, Items.NETHER_WART) > 0) return true;
        if (worker.useBoneMeal && state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock
            && countItem(worker, Items.BONE_MEAL) > 0) return true;
        return false;
    }

    // ---------------------------------------------------------------- gatherer

    /**
     * What a gatherer harvests: stalks (sugar cane, bamboo, kelp) above their bottom segment so
     * they grow back, the top block of a cactus, ripe berry bushes and cocoa, and glowing cave vines.
     */
    private static boolean gathererEligible(ServerLevel level, Worker worker, BlockPos pos, BlockState state) {
        if (state.is(Blocks.SUGAR_CANE)) return cropEnabled(worker, "sugar_cane") && isSecondSegment(level, pos, Blocks.SUGAR_CANE);
        if (state.is(Blocks.BAMBOO)) return cropEnabled(worker, "bamboo") && isSecondSegment(level, pos, Blocks.BAMBOO);
        if (state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)) {
            return cropEnabled(worker, "kelp") && level.getBlockState(pos.below()).is(Blocks.KELP_PLANT);
        }
        if (state.is(Blocks.CACTUS)) {
            return cropEnabled(worker, "cactus") && level.getBlockState(pos.below()).is(Blocks.CACTUS)
                && !level.getBlockState(pos.above()).is(Blocks.CACTUS);
        }
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            return cropEnabled(worker, "sweet_berries")
                && state.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) >= 2;
        }
        if (state.is(Blocks.COCOA)) {
            return cropEnabled(worker, "cocoa")
                && state.getValue(net.minecraft.world.level.block.CocoaBlock.AGE) >= net.minecraft.world.level.block.CocoaBlock.MAX_AGE;
        }
        if (state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)) {
            return cropEnabled(worker, "glow_berries") && state.getValue(net.minecraft.world.level.block.CaveVines.BERRIES);
        }
        return false;
    }

    /** The segment right above the bottom one: cutting it drops everything higher and leaves the root to regrow. */
    private static boolean isSecondSegment(ServerLevel level, BlockPos pos, Block stalk) {
        return level.getBlockState(pos.below()).is(stalk) && !level.getBlockState(pos.below(2)).is(stalk);
    }

    private static boolean gather(ServerLevel level, Worker worker, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            int age = state.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE);
            if (age < 2) return true;
            int count = 1 + level.random.nextInt(2) + (age == 3 ? 1 : 0);
            level.setBlock(pos, state.setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 1), 2);
            worker.collect(new ItemStack(Items.SWEET_BERRIES, count));
            pickSound(level, worker, pos, net.minecraft.sounds.SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES);
            worker.setStatusKey(Lang.STATUS_PICKED_SWEET_BERRIES, pos.toShortString());
            return true;
        }
        if (state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)) {
            if (!state.getValue(net.minecraft.world.level.block.CaveVines.BERRIES)) return true;
            level.setBlock(pos, state.setValue(net.minecraft.world.level.block.CaveVines.BERRIES, false), 2);
            worker.collect(new ItemStack(Items.GLOW_BERRIES));
            pickSound(level, worker, pos, net.minecraft.sounds.SoundEvents.CAVE_VINES_PICK_BERRIES);
            worker.setStatusKey(Lang.STATUS_PICKED_GLOW_BERRIES, pos.toShortString());
            return true;
        }
        if (state.is(Blocks.COCOA)) {
            if (state.getValue(net.minecraft.world.level.block.CocoaBlock.AGE) < net.minecraft.world.level.block.CocoaBlock.MAX_AGE) return true;
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, worker, worker.getMainHandItem());
            level.setBlock(pos, state.setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 0), 3);
            boolean replanted = false;
            for (ItemStack drop : drops) {
                if (!replanted && drop.is(Items.COCOA_BEANS) && drop.getCount() > 0) { drop.shrink(1); replanted = true; }
                if (!drop.isEmpty()) worker.collect(drop);
            }
            pickSound(level, worker, pos, net.minecraft.sounds.SoundEvents.CROP_BREAK);
            worker.setStatusKey(Lang.STATUS_HARVESTED_COCOA, pos.toShortString());
            return true;
        }
        // Stalks: sugar cane, bamboo, kelp and the cactus top are cut like a player would.
        if (!breakBlock(level, worker, pos)) return false;
        pocketDropsNear(level, worker, pos, Jobs::isGatherDrop);
        worker.setStatusKey(Lang.STATUS_CUT, StatusText.name(state.getBlock()), pos.toShortString());
        return true;
    }

    private static void pickSound(ServerLevel level, Worker worker, BlockPos pos, net.minecraft.sounds.SoundEvent sound) {
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        level.playSound(null, pos, sound, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.8f + level.random.nextFloat() * 0.4f);
    }

    private static boolean cropEnabled(Worker worker, CropBlock crop) {
        return cropEnabled(worker, BuiltInRegistries.BLOCK.getKey(crop).getPath());
    }

    private static boolean cropEnabled(Worker worker, String id) {
        return worker.enabledCrops.contains(id);
    }

    private static boolean hasPlantableCrop(Worker worker) {
        for (String crop : worker.enabledCrops) {
            ItemStack seed = seedForCrop(crop);
            if (!seed.isEmpty() && countItem(worker, seed.getItem()) > 0) return true;
        }
        return false;
    }

    private static ItemStack seedForCrop(String crop) {
        return switch (crop) {
            case "wheat" -> new ItemStack(Items.WHEAT_SEEDS);
            case "carrots" -> new ItemStack(Items.CARROT);
            case "potatoes" -> new ItemStack(Items.POTATO);
            case "beetroots" -> new ItemStack(Items.BEETROOT_SEEDS);
            case "melon" -> new ItemStack(Items.MELON_SEEDS);
            case "pumpkin" -> new ItemStack(Items.PUMPKIN_SEEDS);
            case "nether_wart" -> new ItemStack(Items.NETHER_WART);
            default -> ItemStack.EMPTY;
        };
    }

    private static BlockState plantStateFor(String crop) {
        return switch (crop) {
            case "wheat" -> Blocks.WHEAT.defaultBlockState();
            case "carrots" -> Blocks.CARROTS.defaultBlockState();
            case "potatoes" -> Blocks.POTATOES.defaultBlockState();
            case "beetroots" -> Blocks.BEETROOTS.defaultBlockState();
            case "melon" -> Blocks.MELON_STEM.defaultBlockState();
            case "pumpkin" -> Blocks.PUMPKIN_STEM.defaultBlockState();
            case "nether_wart" -> Blocks.NETHER_WART.defaultBlockState();
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    private static boolean canTill(ServerLevel level, Worker worker, BlockPos pos, BlockState state) {
        if (!(state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH))) return false;
        if (!level.getBlockState(pos.above()).isAir()) return false;
        if (!hasWaterWithin(level, pos, 4)) return false;
        return hasPlantableCrop(worker) || toolAvailable(worker, HoeItem.class);
    }

    private static boolean hasWaterWithin(ServerLevel level, BlockPos pos, int range) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-range, -1, -range), pos.offset(range, 1, range))) {
            if (!level.isLoaded(p)) continue;
            if (level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) return true;
        }
        return false;
    }

    private static boolean mineable(ServerLevel level, Worker worker, BlockPos pos, BlockState state) {
        if (state.isAir() || state.is(Blocks.BEDROCK) || state.hasBlockEntity()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if ("terraform".equals(worker.mode)) return !hasLavaNeighbor(level, pos);
        return safeMine(level, pos);
    }

    // ---------------------------------------------------------------- quarry mode

    /** Sand, gravel, clay and dirt: the loose material a quarry is for, and what it digs out of its own floor layer. */
    static boolean isSoftQuarryBlock(BlockState state) {
        return state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(BlockTags.DIRT);
    }

    /** Everything a quarry digs: the soft materials plus plain overworld stone. Never its own cobble floor. */
    static boolean isQuarryBlock(BlockState state) {
        return isSoftQuarryBlock(state) || state.is(BlockTags.BASE_STONE_OVERWORLD);
    }

    /**
     * Quarry targets: loose material and stone anywhere in the area; on the bottom layer (the
     * area's min Y, never below) only soft blocks are dug and every dug or open spot gets a
     * cobblestone floor so the pit stays walkable.
     */
    private static boolean quarryEligible(ServerLevel level, Worker worker, BlockPos pos, BlockState state) {
        Worker.AreaBounds box = worker.areaBounds();
        if (box == null || pos.getY() < box.minY()) return false;
        if (!state.getFluidState().isEmpty() || state.hasBlockEntity() || state.is(Blocks.BEDROCK)) return false;
        boolean floor = pos.getY() == box.minY();
        if (state.isAir() || state.canBeReplaced()) {
            // An open spot on the floor layer still needs its cobble; only worth walking to with cobble in reach.
            return floor && canProvide(level, worker, new ItemStack(Items.COBBLESTONE));
        }
        if (state.getDestroySpeed(level, pos) < 0) return false;
        if (floor ? !isSoftQuarryBlock(state) : !isQuarryBlock(state)) return false;
        return safeMine(level, pos);
    }

    /** True when at least one of the item is in the bag, or the (remotely peeked) supply chest holds some. */
    static boolean canProvide(ServerLevel level, Worker worker, ItemStack template) {
        if (countInBag(worker, template) > 0) return true;
        return worker.supply != null && supplyCount(level, worker, itemKey(template),
            s -> ItemStack.isSameItemSameComponents(s, template)) > 0;
    }

    /** One quarry step at pos: dig the block (shovel for soft material when there is one), floor the bottom layer. */
    private static boolean quarry(ServerLevel level, Worker worker, BlockPos pos) {
        Worker.AreaBounds box = worker.areaBounds();
        if (box == null) return true;
        boolean floor = pos.getY() == box.minY();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty())) {
            if (!floor) return true;
            return layQuarryFloor(level, worker, pos);
        }
        if (isSoftQuarryBlock(state) && toolAvailable(worker, net.minecraft.world.item.ShovelItem.class)) {
            if (!tool(level, worker, net.minecraft.world.item.ShovelItem.class, "shovel")) return true;
        } else if (!tool(level, worker, PickaxeItem.class, "pickaxe")) {
            return true;
        }
        if (state.requiresCorrectToolForDrops() && !worker.getMainHandItem().isCorrectToolForDrops(state)) {
            worker.setStatusKey(Lang.STATUS_PICKAXE_TOO_WEAK, StatusText.name(state.getBlock()));
            return true;
        }
        if (!safeMine(level, pos)) {
            worker.setStatusKey(Lang.STATUS_SKIPPING_HAZARD, pos.toShortString());
            return true;
        }
        if (!breakBlock(level, worker, pos)) return false;
        sealFluidNeighbors(level, worker, pos);
        worker.setStatusKey(Lang.STATUS_QUARRIED, StatusText.name(state.getBlock()), pos.toShortString());
        if (floor) return layQuarryFloor(level, worker, pos);
        return true;
    }

    /** Cobble under the pit. False (target kept) while the cobble is still on its way from the supply chest. */
    private static boolean layQuarryFloor(ServerLevel level, Worker worker, BlockPos pos) {
        if (!take(level, worker, new ItemStack(Items.COBBLESTONE))) {
            missing(worker, "cobblestone for the quarry floor");
            return !worker.nav.hasErrand();
        }
        level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 3);
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        var sound = Blocks.COBBLESTONE.defaultBlockState().getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), net.minecraft.sounds.SoundSource.NEUTRAL,
            (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
        worker.setStatusKey(Lang.STATUS_LAID_QUARRY_FLOOR, pos.toShortString());
        return true;
    }

    private static boolean hasLavaNeighbor(ServerLevel level, BlockPos pos) {
        for (BlockPos neighbor : new BlockPos[]{pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (!level.isLoaded(neighbor)) continue;
            if (level.getFluidState(neighbor).is(net.minecraft.tags.FluidTags.LAVA)) return true;
        }
        return false;
    }

    private static boolean safeMine(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos.below()).isAir() || !level.getFluidState(pos.below()).isEmpty()) return false;
        for (BlockPos neighbor : new BlockPos[]{pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (!level.isLoaded(neighbor) || !level.getFluidState(neighbor).isEmpty()) return false;
        }
        return true;
    }

    private static boolean farm(ServerLevel level, Worker worker, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (worker.useBoneMeal && state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock
            && !(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state))
            && !(state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE)) {
            if (take(level, worker, new ItemStack(Items.BONE_MEAL)) && BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos)) {
                worker.setStatusKey(Lang.STATUS_APPLIED_BONE_MEAL, pos.toShortString());
                return true;
            }
        }

        if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
            if (!breakBlock(level, worker, pos)) return false;
            worker.setStatusKey(Lang.STATUS_HARVESTED_FRUIT, pos.toShortString());
            return true;
        }

        if (state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE) {
            List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), worker, worker.getMainHandItem());
            level.setBlock(pos, Blocks.NETHER_WART.defaultBlockState(), 3);
            boolean reserved = false;
            for (ItemStack drop : drops) {
                if (!reserved && drop.is(Items.NETHER_WART) && drop.getCount() > 0) {
                    drop.shrink(1);
                    reserved = true;
                }
                if (!drop.isEmpty()) worker.collect(drop);
            }
            worker.setStatusKey(Lang.STATUS_HARVESTED_NETHER_WART, pos.toShortString());
            return true;
        }

        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            String path = BuiltInRegistries.BLOCK.getKey(crop).getPath();
            ItemStack seed = seedForCrop(path);
            if (seed.isEmpty()) return true;
            List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), worker, worker.getMainHandItem());
            boolean hasSeed = drops.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, seed) && s.getCount() > 0);
            if (!hasSeed && !take(level, worker, seed)) { missing(worker, "planting supplies"); return true; }
            level.setBlock(pos, crop.getStateForAge(0), 3);
            if (hasSeed) for (ItemStack drop : drops) if (ItemStack.isSameItemSameComponents(drop, seed)) { drop.shrink(1); break; }
            drops.forEach(worker::collect);
            worker.setStatusKey(Lang.STATUS_HARVESTED_REPLANTED, pos.toShortString());
            return true;
        }

        if (canTill(level, worker, pos, state)) {
            if (!tool(level, worker, HoeItem.class, "hoe")) return true;
            level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 3);
            worker.getMainHandItem().hurtAndBreak(1, worker, EquipmentSlot.MAINHAND);
            worker.setStatusKey(Lang.STATUS_TILLED, pos.toShortString());
            return true;
        }

        if (state.is(Blocks.FARMLAND) && level.getBlockState(pos.above()).isAir()) {
            for (String crop : worker.enabledCrops) {
                if ("nether_wart".equals(crop)) continue;
                ItemStack seed = seedForCrop(crop);
                BlockState plant = plantStateFor(crop);
                if (seed.isEmpty() || plant.isAir()) continue;
                if (plant.getBlock() instanceof StemBlock || plant.getBlock() instanceof CropBlock) {
                    if (!take(level, worker, seed)) continue;
                    level.setBlock(pos.above(), plant, 3);
                    worker.setStatusKey(Lang.STATUS_PLANTED, crop, pos.above().toShortString());
                    return true;
                }
            }
        }

        if (state.is(Blocks.SOUL_SAND) && level.getBlockState(pos.above()).isAir() && cropEnabled(worker, "nether_wart")) {
            if (take(level, worker, new ItemStack(Items.NETHER_WART))) {
                level.setBlock(pos.above(), Blocks.NETHER_WART.defaultBlockState(), 3);
                worker.setStatusKey(Lang.STATUS_PLANTED_NETHER_WART, pos.above().toShortString());
            }
        }
        return true;
    }

    /** Fell the tree at pos. False when the worker first has to get an axe (or pos is no longer a log). */
    /**
     * Plan a tree: flood-fill its logs from the trunk block and queue them bottom first. The logs
     * are then chopped one per work tick by {@link #fellStep}; leaves decay on their own once the
     * wood is gone, like a player-cut tree. Returns false while the axe is still being fetched.
     */
    private static boolean fell(ServerLevel level, Worker worker, BlockPos pos) {
        if (!tool(level, worker, AxeItem.class, "axe")) return false;
        BlockState original = level.getBlockState(pos);
        if (!isLog(original)) return true;
        int minX = Math.min(worker.first.getX(), worker.second.getX()) - 2;
        int maxX = Math.max(worker.first.getX(), worker.second.getX()) + 2;
        int minZ = Math.min(worker.first.getZ(), worker.second.getZ()) - 2;
        int maxZ = Math.max(worker.first.getZ(), worker.second.getZ()) + 2;
        int maxY = pos.getY() + 32;
        ArrayList<BlockPos> tree = new ArrayList<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        queue.add(pos);
        seen.add(pos);
        while (!queue.isEmpty() && tree.size() < 200) {
            BlockPos cur = queue.removeFirst();
            if (!level.isLoaded(cur) || !isLog(level.getBlockState(cur))) continue;
            if (cur.getX() < minX || cur.getX() > maxX || cur.getZ() < minZ || cur.getZ() > maxZ) continue;
            if (cur.getY() < pos.getY() - 1 || cur.getY() > maxY) continue;
            tree.add(cur.immutable());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos next = cur.offset(dx, dy, dz);
                        if (seen.add(next)) queue.add(next);
                    }
                }
            }
        }
        // Bottom first: the worker stands at the base and works upward like a player would.
        tree.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        worker.fellQueue.clear();
        worker.fellQueue.addAll(tree);
        worker.fellBase = pos.immutable();
        ItemStack sapling = saplingFor(original);
        worker.fellSapling = sapling.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(sapling.getItem()).toString();
        worker.setStatusKey(Lang.STATUS_FELLING_TREE, tree.size());
        return true;
    }

    /** Chop the next queued log; when the queue empties, sweep the drops and replant. */
    private static void fellStep(ServerLevel level, Worker worker) {
        // Drop logs that are already gone (another worker, a player, a reload mid-tree).
        while (!worker.fellQueue.isEmpty()) {
            BlockPos log = worker.fellQueue.get(0);
            if (!level.isLoaded(log)) { worker.setStatusKey(Lang.STATUS_TREE_CHUNK_UNLOADED); return; }
            if (isLog(level.getBlockState(log))) break;
            worker.fellQueue.remove(0);
        }
        if (worker.fellQueue.isEmpty()) {
            finishTree(level, worker);
            return;
        }
        if (!tool(level, worker, AxeItem.class, "axe")) {
            if (!worker.nav.hasErrand()) worker.setStatusKey(Lang.STATUS_NEED_AXE);
            return;
        }
        BlockPos base = worker.fellBase != null ? worker.fellBase : worker.fellQueue.get(0);
        if (!worker.approachStand(base)) return;
        BlockPos log = worker.fellQueue.get(0);
        if (!breakBlock(level, worker, log)) {
            worker.setStatusKey(Lang.STATUS_CHOPPING, worker.fellQueue.size());
            return;
        }
        worker.fellQueue.remove(0);
        if (worker.fellQueue.isEmpty()) finishTree(level, worker);
        else worker.setStatusKey(Lang.STATUS_CHOPPING, worker.fellQueue.size());
    }

    private static void finishTree(ServerLevel level, Worker worker) {
        BlockPos base = worker.fellBase;
        if (base != null && level.isLoaded(base)) {
            pickupForestryDrops(level, worker, base);
            ItemStack sapling = ItemStack.EMPTY;
            if (worker.fellSapling != null && !worker.fellSapling.isEmpty()) {
                var id = net.minecraft.resources.ResourceLocation.tryParse(worker.fellSapling);
                if (id != null) sapling = new ItemStack(BuiltInRegistries.ITEM.get(id));
            }
            if (level.getBlockState(base).isAir() && level.getBlockState(base.below()).is(BlockTags.DIRT) && !sapling.isEmpty()) {
                replantSapling(level, worker, base, sapling);
            }
        }
        worker.fellQueue.clear();
        worker.fellBase = null;
        worker.fellSapling = "";
        worker.setStatusKey(Lang.STATUS_FELLED_TREE);
    }

    private static void pickupForestryDrops(ServerLevel level, Worker worker, BlockPos around) {
        AABB box = new AABB(around).inflate(6);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;
            if (!(stack.is(ItemTags.LOGS) || stack.is(ItemTags.SAPLINGS) || stack.is(Items.STICK) || stack.is(Items.APPLE))) continue;
            worker.collect(stack.copy());
            entity.discard();
        }
    }

    private static void replantSapling(ServerLevel level, Worker worker, BlockPos pos, ItemStack sapling) {
        if (sapling.isEmpty()) return;
        boolean needs2x2 = sapling.is(Items.DARK_OAK_SAPLING) || sapling.is(Items.SPRUCE_SAPLING);
        if (needs2x2 && countItem(worker, sapling.getItem()) >= 4) {
            BlockPos origin = find2x2Spot(level, pos);
            if (origin != null) {
                for (BlockPos p : new BlockPos[]{origin, origin.east(), origin.south(), origin.east().south()}) {
                    if (!take(level, worker, sapling.copyWithCount(1))) return;
                    level.setBlock(p, Block.byItem(sapling.getItem()).defaultBlockState(), 3);
                }
                return;
            }
        }
        if (take(level, worker, sapling)) {
            level.setBlock(pos, Block.byItem(sapling.getItem()).defaultBlockState(), 3);
        }
    }

    private static BlockPos find2x2Spot(ServerLevel level, BlockPos near) {
        for (BlockPos base : new BlockPos[]{near, near.north(), near.west(), near.north().west()}) {
            boolean ok = true;
            for (BlockPos p : new BlockPos[]{base, base.east(), base.south(), base.east().south()}) {
                if (!level.isLoaded(p) || !level.getBlockState(p).isAir()) { ok = false; break; }
                if (!level.getBlockState(p.below()).is(BlockTags.DIRT)) { ok = false; break; }
            }
            if (ok) return base;
        }
        return null;
    }

    private static boolean isLog(BlockState state) {
        return (state.is(BlockTags.LOGS) || state.is(BlockTags.OVERWORLD_NATURAL_LOGS)) && !state.hasBlockEntity();
    }

    private static ItemStack saplingFor(BlockState log) {
        String id = BuiltInRegistries.BLOCK.getKey(log.getBlock()).getPath();
        if (id.contains("spruce")) return new ItemStack(Items.SPRUCE_SAPLING);
        if (id.contains("birch")) return new ItemStack(Items.BIRCH_SAPLING);
        if (id.contains("jungle")) return new ItemStack(Items.JUNGLE_SAPLING);
        if (id.contains("acacia")) return new ItemStack(Items.ACACIA_SAPLING);
        if (id.contains("dark_oak")) return new ItemStack(Items.DARK_OAK_SAPLING);
        if (id.contains("oak")) return new ItemStack(Items.OAK_SAPLING);
        if (id.contains("cherry")) return new ItemStack(Items.CHERRY_SAPLING);
        return ItemStack.EMPTY;
    }

    private static boolean mine(ServerLevel level, Worker worker, BlockPos pos) {
        if (!tool(level, worker, PickaxeItem.class, "pickaxe")) return true;
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.BEDROCK) || state.isAir()) return true;
        if (state.requiresCorrectToolForDrops() && !worker.getMainHandItem().isCorrectToolForDrops(state)) {
            worker.setStatusKey(Lang.STATUS_PICKAXE_TOO_WEAK, StatusText.name(state.getBlock()));
            return true;
        }
        if ("terraform".equals(worker.mode)) {
            if (hasLavaNeighbor(level, pos)) {
                worker.setStatusKey(Lang.STATUS_SKIPPING_LAVA, pos.toShortString());
                return true;
            }
        } else if (!safeMine(level, pos)) {
            worker.setStatusKey(Lang.STATUS_SKIPPING_HAZARD, pos.toShortString());
            return true;
        }
        if (!breakBlock(level, worker, pos)) return false;
        sealFluidNeighbors(level, worker, pos);
        worker.setStatusKey(Lang.STATUS_MINED, pos.toShortString());
        return true;
    }

    private static void minePlanned(ServerLevel level, Worker worker) {
        if (worker.minePlan == null || worker.minePlan.isEmpty()) {
            worker.minePlan = MinePlanner.build(worker);
            worker.minePlanIndex = 0;
            if (worker.minePlan.isEmpty()) {
                worker.setStatusKey(Lang.STATUS_NO_MINE_PLAN, worker.mode);
                return;
            }
        }
        // Fast-forward past steps that are already done (e.g. after a reload) without walking back up.
        int skipped = 0;
        while (worker.minePlanIndex < worker.minePlan.size() && skipped < 256
            && stepDone(level, worker.minePlan.get(worker.minePlanIndex))) {
            worker.minePlanIndex++;
            skipped++;
        }
        if (worker.minePlanIndex >= worker.minePlan.size()) {
            worker.setStatusKey(Lang.STATUS_MINE_PLAN_COMPLETE, worker.targetY);
            worker.working = false;
            return;
        }

        // Grab exposed ore the worker can already reach; never tunnel off-plan to buried ore.
        BlockPos ore = MinePlanner.findReachableOre(level, worker);
        if (ore != null) {
            BlockState oreState = level.getBlockState(ore);
            if (tool(level, worker, PickaxeItem.class, "pickaxe")
                && (!oreState.requiresCorrectToolForDrops() || worker.getMainHandItem().isCorrectToolForDrops(oreState))) {
                if (!breakBlock(level, worker, ore)) return;
                sealFluidNeighbors(level, worker, ore);
                worker.setStatusKey(Lang.STATUS_MINED_ORE, StatusText.name(oreState.getBlock()), ore.toShortString());
                return;
            }
            worker.unreachable.put(ore.asLong(), level.getGameTime() + 12000);
        }

        MinePlanner.Step step = worker.minePlan.get(worker.minePlanIndex);
        if (!level.isLoaded(step.pos())) { worker.setStatusKey(Lang.STATUS_MINE_CHUNK_UNLOADED); return; }
        if (!worker.approachStand(step.pos())) return;

        switch (step.kind()) {
            case DIG -> {
                BlockState state = level.getBlockState(step.pos());
                if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                    if (!tool(level, worker, PickaxeItem.class, "pickaxe")) return;
                    if (state.requiresCorrectToolForDrops() && !worker.getMainHandItem().isCorrectToolForDrops(state)) {
                        worker.setStatusKey(Lang.STATUS_PICKAXE_TOO_WEAK, StatusText.name(state.getBlock()));
                        return;
                    }
                    if (!breakBlock(level, worker, step.pos())) return;
                    sealFluidNeighbors(level, worker, step.pos());
                }
            }
            case PLACE_TORCH -> {
                if (!placeWallTorch(level, worker, step.pos())) {
                    if (worker.nav.hasErrand()) return; // torches are on their way from the supply chest
                    worker.setStatusKey(Lang.STATUS_NO_TORCHES);
                }
            }
            case PLACE_LADDER -> {
                if (level.getBlockState(step.pos()).canBeReplaced() || level.getBlockState(step.pos()).isAir()) {
                    if (!take(level, worker, new ItemStack(Items.LADDER))) {
                        missing(worker, "ladder");
                        return;
                    }
                    level.setBlock(step.pos(), Blocks.LADDER.defaultBlockState(), 3);
                }
            }
            case PLACE_COBBLE -> {
                if (level.getBlockState(step.pos()).canBeReplaced() || level.getBlockState(step.pos()).isAir()) {
                    if (!take(level, worker, new ItemStack(Items.COBBLESTONE))) {
                        missing(worker, "cobblestone");
                        return;
                    }
                    level.setBlock(step.pos(), Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
            }
        }
        worker.minePlanIndex++;
        worker.setStatusKey(Lang.STATUS_DIGGING_DOWN, worker.blockPosition().getY(), worker.targetY,
            worker.minePlanIndex, worker.minePlan.size());
    }

    /** Whether a mine-plan step already shows in the world, so plans fast-forward after a reload. */
    static boolean stepDone(ServerLevel level, MinePlanner.Step step) {
        if (!level.isLoaded(step.pos())) return false;
        BlockState state = level.getBlockState(step.pos());
        return switch (step.kind()) {
            case DIG -> state.isAir() || state.is(Blocks.BEDROCK);
            case PLACE_LADDER -> state.is(Blocks.LADDER);
            case PLACE_COBBLE -> !state.isAir() && !state.canBeReplaced();
            case PLACE_TORCH -> hasTorch(level, step.pos());
        };
    }

    /** A torch standing on the step, or a wall torch hung on it from any open side. */
    static boolean hasTorch(ServerLevel level, BlockPos wall) {
        BlockState state = level.getBlockState(wall);
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) return true;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos spot = wall.relative(dir);
            if (!level.isLoaded(spot)) continue;
            BlockState torch = level.getBlockState(spot);
            if (torch.is(Blocks.WALL_TORCH)
                && torch.getValue(net.minecraft.world.level.block.WallTorchBlock.FACING) == dir) return true;
        }
        return false;
    }

    /** Torch step positions sit in the tunnel wall; hang a wall torch on it from the open side. */
    private static boolean placeWallTorch(ServerLevel level, Worker worker, BlockPos wall) {
        BlockState wallState = level.getBlockState(wall);
        if (wallState.isAir()) {
            if (!level.getBlockState(wall.below()).isFaceSturdy(level, wall.below(), Direction.UP)) return true;
            if (!take(level, worker, new ItemStack(Items.TORCH))) return false;
            level.setBlock(wall, Blocks.TORCH.defaultBlockState(), 3);
            return true;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos spot = wall.relative(dir);
            if (!level.getBlockState(spot).isAir()) continue;
            BlockState torch = Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, dir);
            if (!torch.canSurvive(level, spot)) continue;
            if (!take(level, worker, new ItemStack(Items.TORCH))) return false;
            level.setBlock(spot, torch, 3);
            return true;
        }
        return true;
    }

    private static void sealFluidNeighbors(ServerLevel level, Worker worker, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (!level.isLoaded(neighbor)) continue;
            if (level.getFluidState(neighbor).isEmpty()) continue;
            if (!take(level, worker, new ItemStack(Items.COBBLESTONE))) return;
            level.setBlock(neighbor, Blocks.COBBLESTONE.defaultBlockState(), 3);
        }
    }

    /**
     * Break pos the way a player would: the first calls only swing at it (see {@link WorkAnimator});
     * once the block has been worked long enough it breaks with particles and sound and the drops
     * go into the bag. Returns false while still chipping, so callers keep their target.
     */
    private static boolean breakBlock(ServerLevel level, Worker worker, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) { worker.animator.clear(level); return true; }
        if (!worker.animator.ready(level, pos)) {
            worker.setStatusKey(Lang.STATUS_WORKING_ON, StatusText.name(state.getBlock()));
            return false;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), worker, worker.getMainHandItem());
        worker.animator.clear(level);
        level.destroyBlock(pos, false, worker);
        drops.forEach(worker::collect);
        worker.getMainHandItem().hurtAndBreak(1, worker, EquipmentSlot.MAINHAND);
        return true;
    }

    static boolean toolAvailable(Worker worker, Class<?> type) {
        if (type.isInstance(worker.getMainHandItem().getItem())) return true;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            if (type.isInstance(worker.bag.getItem(i).getItem())) return true;
        }
        return false;
    }

    static boolean bagMostlyEmpty(Worker worker) {
        int used = 0;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            if (!worker.bag.getItem(i).isEmpty()) used++;
        }
        return used <= 2;
    }

    static boolean depositPublic(ServerLevel level, Worker worker) {
        return deposit(level, worker);
    }

    /**
     * Put a tool of the given type in hand. Bag first; otherwise walk to the supply chest for one
     * (false while on the way), else craft one. "Missing" only shows when nothing is pending.
     */
    static boolean tool(ServerLevel level, Worker worker, Class<?> type, String name) {
        if (type.isInstance(worker.getMainHandItem().getItem())) return true;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (type.isInstance(stack.getItem())) {
                ItemStack held = worker.getMainHandItem();
                ItemStack taking = stack.split(1);
                // Swap, never drop: the tool that was in hand goes back into the bag (or stays if the bag is full).
                if (!held.isEmpty()) {
                    ItemStack rest = worker.bag.addItem(held.copy());
                    if (!rest.isEmpty()) { stack.grow(1); worker.bag.setChanged(); return false; }
                }
                worker.setItemSlot(EquipmentSlot.MAINHAND, taking);
                worker.bag.setChanged();
                return true;
            }
        }
        if (fetchFromSupply(level, worker, "tool:" + type.getName(), s -> type.isInstance(s.getItem()), 1, 1, name, null)) {
            return false;
        }
        if (worker.nav.hasErrand()) return false;
        if (Crafting.tryCraft(level, worker, type)) return tool(level, worker, type, name);
        missing(worker, name);
        return false;
    }

    /**
     * Remove {@code template.getCount()} (at least 1) matching items from the bag. When the bag is
     * short, the supply chest is only peeked at remotely; if it holds enough the worker starts a
     * trip there and this returns false until the items are in the bag. Callers retry next tick.
     */
    static boolean take(ServerLevel level, Worker worker, ItemStack template) {
        int need = Math.max(1, template.getCount());
        if (countInBag(worker, template) >= need) {
            removeFromBag(worker, template, need);
            return true;
        }
        int grab = Math.max(need, Math.min(FETCH_BATCH, template.getMaxStackSize()));
        Predicate<ItemStack> match = s -> ItemStack.isSameItemSameComponents(s, template);
        String label = template.getHoverName().getString();
        if (fetchFromSupply(level, worker, itemKey(template), match, need, grab, label, template)) return false;
        // Building blocks the supply lacks (sealing, quarry floors, steps) may come from the worker's own output pile.
        if (isFillerBlock(template) && worker.output != null && !worker.output.equals(worker.supply)) {
            fetchFrom(level, worker, worker.output, "out:" + itemKey(template), match, need, grab, label, template, false);
        }
        return false;
    }

    static boolean isFillerBlock(ItemStack stack) {
        return stack.is(Items.COBBLESTONE) || stack.is(Items.DIRT) || stack.is(Items.COBBLED_DEEPSLATE);
    }

    /** Status for a failed take/tool, suppressed while a supply trip is already under way. */
    static void missing(Worker worker, Object name) {
        if (!worker.nav.hasErrand()) worker.setStatusKey(Lang.STATUS_MISSING, name);
    }

    /** "An armful": how many of a stackable item one supply trip brings back. */
    static final int FETCH_BATCH = 16;
    /** How long a remote "supply has X" answer stays valid. */
    static final long PEEK_TICKS = 200;

    /** Cached remote look into the supply chest: how many matching items it held, and until when that counts. */
    record Peek(int count, long until) {}

    static String itemKey(ItemStack stack) {
        String key = "item:" + BuiltInRegistries.ITEM.getKey(stack.getItem());
        return stack.getComponentsPatch().isEmpty() ? key : key + "#" + stack.getComponentsPatch().hashCode();
    }

    static Container supplyContainer(ServerLevel level, Worker worker) {
        if (worker.supply == null || !level.isLoaded(worker.supply)) return null;
        return level.getBlockEntity(worker.supply) instanceof Container chest ? chest : null;
    }

    /** Remote peek, cached for {@link #PEEK_TICKS}: how many items in the supply chest match. */
    static int supplyCount(ServerLevel level, Worker worker, String key, Predicate<ItemStack> match) {
        return chestCount(level, worker, worker.supply, key, match);
    }

    /** Remote peek at any chest, cached per key; what other workers already claimed there is not counted. */
    static int chestCount(ServerLevel level, Worker worker, BlockPos chestPos, String key, Predicate<ItemStack> match) {
        return Math.max(0, rawChestCount(level, worker, chestPos, key, match)
            - (chestPos == null ? 0 : WorkerStorage.claimedByOthers(level, chestPos, worker, key)));
    }

    private static int rawChestCount(ServerLevel level, Worker worker, BlockPos chestPos, String key, Predicate<ItemStack> match) {
        long now = level.getGameTime();
        Peek cached = worker.supplyPeek.get(key);
        if (cached != null && now < cached.until()) return cached.count();
        int count = 0;
        Container chest = chestPos == null || !level.isLoaded(chestPos) ? null
            : level.getBlockEntity(chestPos) instanceof Container c ? c : null;
        if (chest != null) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                if (!stack.isEmpty() && match.test(stack)) count += stack.getCount();
            }
        }
        if (worker.supplyPeek.size() >= 64) worker.supplyPeek.clear();
        worker.supplyPeek.put(key, new Peek(count, now + PEEK_TICKS));
        return count;
    }

    /**
     * Start a trip to the supply chest for {@code grab} matching items if it holds at least
     * {@code need} (remote peek, cached). True when a trip is pending (started now or earlier), so
     * the caller should wait; false when the supply cannot provide the item.
     */
    static boolean fetchFromSupply(ServerLevel level, Worker worker, String key, Predicate<ItemStack> match,
                                   int need, int grab, String label, ItemStack template) {
        return fetchFrom(level, worker, worker.supply, key, match, need, grab, label, template, true);
    }

    /**
     * Walk to chestPos for up to {@code grab} matching items if it holds {@code need} that no other
     * worker has claimed. The trip is announced as a claim so workmates sharing the chest don't walk
     * for the same items. Items fetched with a template are remembered as a bag reserve (up to an
     * armful) so the next drop-off does not hand them straight back.
     */
    static boolean fetchFrom(ServerLevel level, Worker worker, BlockPos chestPos, String key, Predicate<ItemStack> match,
                             int need, int grab, String label, ItemStack template, boolean supply) {
        if (chestPos == null) return false;
        if (worker.nav.hasErrand()) return true;
        if (!level.isLoaded(chestPos)) return false;
        if (worker.isUnreachable(chestPos)) {
            if (supply) worker.setStatusKey(Lang.STATUS_CANT_REACH_SUPPLY);
            return false;
        }
        if (chestCount(level, worker, chestPos, key, match) < need) return false;
        if (!hasRoom(worker.bag, template)) { worker.setStatusKey(Lang.STATUS_BAG_FULL_CARRY, label); return false; }
        WorkerStorage.claim(level, chestPos, worker, key, grab);
        String errandLabel = supply ? "Fetching " + label + " from supply" : "Fetching " + label + " from the output chest";
        worker.nav.requestErrand(chestPos, errandLabel, (lvl, w) -> {
            WorkerStorage.release(lvl, chestPos, w, key);
            w.supplyPeek.clear();
            if (!(lvl.getBlockEntity(chestPos) instanceof Container chest)) { w.setStatusKey(Lang.STATUS_SUPPLY_CHEST_MISSING); return; }
            int moved = WorkerNav.pull(w, chest, match, grab);
            if (moved == 0) {
                w.supplyPeek.put(key, new Peek(0, lvl.getGameTime() + PEEK_TICKS));
                w.setStatusKey(Lang.STATUS_SUPPLY_HAS_NO, label);
            } else {
                if (template != null && template.isStackable()) {
                    w.reserves.putIfAbsent(ExtraJobs.itemId(template), Math.min(grab, FETCH_BATCH));
                }
                w.setStatusKey(Lang.STATUS_PICKED_UP, moved, label);
            }
        });
        return true;
    }

    /** True when the bag can take at least one of template (or has a free slot when template is null). */
    static boolean hasRoom(Container bag, ItemStack template) {
        for (int i = 0; i < bag.getContainerSize(); i++) {
            ItemStack slot = bag.getItem(i);
            if (slot.isEmpty()) return true;
            if (template != null && ItemStack.isSameItemSameComponents(slot, template)
                && slot.getCount() < slot.getMaxStackSize()) return true;
        }
        return false;
    }

    private static int countInBag(Worker worker, ItemStack template) {
        int count = 0;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, template)) count += stack.getCount();
        }
        return count;
    }

    private static void removeFromBag(Worker worker, ItemStack template, int count) {
        for (int i = 0; i < worker.bag.getContainerSize() && count > 0; i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (!ItemStack.isSameItemSameComponents(stack, template)) continue;
            int n = Math.min(count, stack.getCount());
            stack.shrink(n);
            count -= n;
        }
        worker.bag.setChanged();
    }

    private static int countItem(Worker worker, net.minecraft.world.item.Item item) {
        int count = 0;
        if (worker.getMainHandItem().is(item)) count += worker.getMainHandItem().getCount();
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static boolean deposit(ServerLevel level, Worker worker) {
        if (worker.output == null) return true;
        if (!level.isLoaded(worker.output)) { worker.setStatusKey(Lang.STATUS_OUTPUT_CHUNK_UNLOADED); return false; }
        if (!worker.approach(worker.output)) {
            if (!worker.nav.hasErrand()) worker.setStatusKey(Lang.STATUS_RETURNING_LOOT);
            return false;
        }
        Container chest = StorageOps.at(level, worker.output);
        if (chest == null) { worker.setStatusKey(Lang.STATUS_OUTPUT_CHEST_MISSING); return false; }
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (stack.isEmpty() || isFurnaceHaul(worker, stack)) continue;
            // Shared export boundary: tools, role supplies and owner reserves stay in the bag.
            int limit = StorageOps.exportable(worker, i);
            if (limit > 0) StorageOps.move(worker.bag, i, chest, limit);
        }
        chest.setChanged();
        if (bagFull(worker)) {
            worker.setStatusKey(Lang.STATUS_OUTPUT_STORAGE_FULL);
            return false;
        }
        worker.lastDepositTime = level.getGameTime();
        worker.setStatusKey(Lang.STATUS_DROPPED_OFF_LOOT);
        return true;
    }

    private static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof PickaxeItem
            || stack.getItem() instanceof AxeItem
            || stack.getItem() instanceof HoeItem
            || stack.getItem() instanceof net.minecraft.world.item.ShovelItem
            || stack.getItem() instanceof net.minecraft.world.item.ShearsItem;
    }
}
