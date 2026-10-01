package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.List;
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

    static void tick(ServerLevel level, Worker worker) {
        try {
            if ("builder".equals(worker.role)) { Blueprints.tick(level, worker); worker.markWorkFound(); return; }
            if ("smelter".equals(worker.role)) { ExtraJobs.smelter(level, worker); return; }
            if ("courier".equals(worker.role)) {
                if (!handleInventory(level, worker)) return;
                ExtraJobs.courier(level, worker);
                return;
            }
            if ("rancher".equals(worker.role)) {
                if (worker.first == null || worker.second == null) { worker.setStatus("Set a pen area"); return; }
                if (!handleInventory(level, worker)) return;
                ExtraJobs.rancher(level, worker);
                return;
            }
            if ("fisher".equals(worker.role)) {
                if (worker.first == null || worker.second == null) { worker.setStatus("Set a fishing area"); return; }
                if (!handleInventory(level, worker)) return;
                ExtraJobs.fisher(level, worker);
                return;
            }
            if (worker.first == null || worker.second == null) { worker.setStatus("Set two work-area corners"); return; }
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

            if (worker.target != null && (!inWorkScope(worker, worker.target) || !eligible(level, worker, worker.target))) {
                worker.target = null;
            }
            if (worker.target == null) worker.target = find(level, worker);
            if (worker.target == null) {
                worker.setStatus("No reachable work found in assigned area");
                worker.markIdle();
                return;
            }
            if (!worker.approach(worker.target)) return;
            BlockPos pos = worker.target;
            if ("farmer".equals(worker.role)) farm(level, worker, pos);
            else if ("miner".equals(worker.role)) mine(level, worker, pos);
            else worker.setStatus("Choose a worker role");
            worker.target = null;
            worker.markWorkFound();
        } catch (Exception ex) {
            HelpfulWorkers.LOGGER.error("Worker {} ({}) job tick failed", worker.getId(), worker.role, ex);
            worker.setStatus("Paused: " + ex.getClass().getSimpleName());
            worker.working = false;
            worker.target = null;
            worker.getNavigation().stop();
        }
    }

    /** Forester area loop: fell → replant → sweep drops → go home & wait. */
    private static void foresterTick(ServerLevel level, Worker worker) {
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
        if (log == null) {
            // Go home / deposit and wait
            if (worker.output != null && hasDepositableLoot(worker)) {
                if (!deposit(level, worker)) return;
            }
            BlockPos home = worker.bed != null ? worker.bed : worker.output;
            if (home != null && level.isLoaded(home) && worker.blockPosition().distSqr(home) > 9) {
                if (!worker.approachWithin(home, 2, 2)) return;
            }
            worker.idleUntil = level.getGameTime() + 20L * (120 + level.random.nextInt(60));
            worker.setStatus("Forest quiet — resting, will recheck soon");
            worker.markIdle();
            return;
        }
        if (!worker.approach(log)) return;
        fell(level, worker, log);
        worker.treeIndex.remove(log);
        worker.markWorkFound();
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
        Worker.AreaBounds box = worker.areaBounds();
        if (box == null) return false;
        AABB area = new AABB(box.minX(), box.minY() - 1, box.minZ(), box.maxX() + 1, box.maxY() + 8, box.maxZ() + 1);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;
            if (!(stack.is(ItemTags.LOGS) || stack.is(ItemTags.SAPLINGS) || stack.is(Items.STICK) || stack.is(Items.APPLE))) continue;
            if (!worker.approach(entity.blockPosition())) return true;
            worker.collect(stack.copy());
            entity.discard();
            worker.setStatus("Gathered forest drops");
            return true;
        }
        return false;
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
                worker.setStatus("Bone-mealed sapling");
                return true;
            }
            return false;
        }
        return false;
    }

    /** Deposit when full if a chest is set; otherwise go to bed and wait for the owner. */
    private static boolean handleInventory(ServerLevel level, Worker worker) {
        if (!shouldUnload(level, worker)) return true;
        worker.needsUnload = true;
        if (worker.output != null) {
            if (!hasDepositableLoot(worker)) {
                worker.status = OUT_OF_SPACE;
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
        worker.status = OUT_OF_SPACE;
        worker.working = false;
        worker.target = null;
        worker.getNavigation().stop();
        if (worker.bed != null && level.isLoaded(worker.bed)) {
            if (worker.blockPosition().distSqr(worker.bed) > 4) {
                worker.getNavigation().moveTo(worker.bed.getX() + 0.5, worker.bed.getY(), worker.bed.getZ() + 0.5, 1);
                worker.status = OUT_OF_SPACE + " Returning to bed.";
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

    private static boolean hasDepositableLoot(Worker worker) {
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (stack.isEmpty() || isTool(stack)) continue;
            if (stack.is(ItemTags.SAPLINGS)) {
                if (countItem(worker, stack.getItem()) > SAPLING_RESERVE) return true;
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
        if (volume > 32768) { worker.setStatus("Area too large (max 32768 blocks)"); worker.working = false; return null; }
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
        for (int checked = 0; checked < Math.min(volume, budget * 4L); checked++) {
            int index = Math.floorMod(worker.scanCursor++, (int) volume);
            int yOff = dy - 1 - index / (dx * dz);
            int zOff = (index / dx) % dz;
            int xOff = index % dx;
            BlockPos pos = new BlockPos(box.minX() + xOff, box.minY() + yOff, box.minZ() + zOff);
            if (!level.isLoaded(pos)) continue;
            if ("miner".equals(worker.role) && !terraform && yOff == 0 && dy > 1) continue;
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
            case "miner" -> mineable(level, worker, pos, state);
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

    private static void farm(ServerLevel level, Worker worker, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (worker.useBoneMeal && state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock
            && !(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state))
            && !(state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE)) {
            if (take(level, worker, new ItemStack(Items.BONE_MEAL)) && BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos)) {
                worker.status = "Applied bone meal at " + pos.toShortString();
                return;
            }
        }

        if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
            breakBlock(level, worker, pos);
            worker.status = "Harvested fruit at " + pos.toShortString();
            return;
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
            worker.status = "Harvested nether wart at " + pos.toShortString();
            return;
        }

        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            String path = BuiltInRegistries.BLOCK.getKey(crop).getPath();
            ItemStack seed = seedForCrop(path);
            if (seed.isEmpty()) return;
            List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), worker, worker.getMainHandItem());
            boolean hasSeed = drops.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, seed) && s.getCount() > 0);
            if (!hasSeed && !take(level, worker, seed)) { worker.status = "Missing planting supplies"; return; }
            level.setBlock(pos, crop.getStateForAge(0), 3);
            if (hasSeed) for (ItemStack drop : drops) if (ItemStack.isSameItemSameComponents(drop, seed)) { drop.shrink(1); break; }
            drops.forEach(worker::collect);
            worker.status = "Harvested and replanted " + pos.toShortString();
            return;
        }

        if (canTill(level, worker, pos, state)) {
            if (!tool(level, worker, HoeItem.class, "hoe")) return;
            level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 3);
            worker.getMainHandItem().hurtAndBreak(1, worker, EquipmentSlot.MAINHAND);
            worker.status = "Tilled " + pos.toShortString();
            return;
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
                    worker.status = "Planted " + crop + " at " + pos.above().toShortString();
                    return;
                }
            }
        }

        if (state.is(Blocks.SOUL_SAND) && level.getBlockState(pos.above()).isAir() && cropEnabled(worker, "nether_wart")) {
            if (take(level, worker, new ItemStack(Items.NETHER_WART))) {
                level.setBlock(pos.above(), Blocks.NETHER_WART.defaultBlockState(), 3);
                worker.status = "Planted nether wart at " + pos.above().toShortString();
            }
        }
    }

    private static void fell(ServerLevel level, Worker worker, BlockPos pos) {
        if (!tool(level, worker, AxeItem.class, "axe")) return;
        BlockState original = level.getBlockState(pos);
        if (!isLog(original)) return;
        ItemStack sapling = saplingFor(original);
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
            tree.add(cur);
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
        tree.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        for (BlockPos log : tree) {
            if (!tool(level, worker, AxeItem.class, "axe")) {
                worker.status = "Axe broke while felling";
                return;
            }
            if (isLog(level.getBlockState(log))) breakBlock(level, worker, log);
        }

        // Break leaves by flood-fill from felled logs (max 6 steps), not full cubes.
        java.util.ArrayDeque<BlockPos> leafQ = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> leafSeen = new java.util.HashSet<>();
        for (BlockPos log : tree) {
            leafQ.add(log);
            leafSeen.add(log);
        }
        int leafBudget = 400;
        while (!leafQ.isEmpty() && leafBudget-- > 0) {
            BlockPos cur = leafQ.removeFirst();
            for (Direction dir : Direction.values()) {
                BlockPos n = cur.relative(dir);
                if (!leafSeen.add(n.immutable())) continue;
                if (!level.isLoaded(n)) continue;
                // limit manhattan distance from nearest trunk roughly via step depth: only expand from leaves
                BlockState st = level.getBlockState(n);
                if (st.is(BlockTags.LEAVES)) {
                    breakBlock(level, worker, n.immutable());
                    // only expand further if within 6 of any trunk
                    int minMan = Integer.MAX_VALUE;
                    for (BlockPos log : tree) minMan = Math.min(minMan, log.distManhattan(n));
                    if (minMan <= 6) leafQ.add(n.immutable());
                }
            }
        }

        pickupForestryDrops(level, worker, pos);

        if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(BlockTags.DIRT)) {
            replantSapling(level, worker, pos, sapling);
        }
        worker.status = "Felled tree (" + tree.size() + " logs)";
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

    private static void mine(ServerLevel level, Worker worker, BlockPos pos) {
        if (!tool(level, worker, PickaxeItem.class, "pickaxe")) return;
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.BEDROCK) || state.isAir()) return;
        if (state.requiresCorrectToolForDrops() && !worker.getMainHandItem().isCorrectToolForDrops(state)) {
            worker.status = "Pickaxe too weak for " + state.getBlock().getName().getString();
            return;
        }
        if ("terraform".equals(worker.mode)) {
            if (hasLavaNeighbor(level, pos)) {
                worker.status = "Skipping lava near " + pos.toShortString();
                return;
            }
        } else if (!safeMine(level, pos)) {
            worker.status = "Skipping hazard at " + pos.toShortString();
            return;
        }
        breakBlock(level, worker, pos);
        sealFluidNeighbors(level, worker, pos);
        worker.status = "Mining " + pos.toShortString();
    }

    private static void minePlanned(ServerLevel level, Worker worker) {
        if (worker.minePlan == null || worker.minePlan.isEmpty()) {
            worker.minePlan = MinePlanner.build(worker);
            worker.minePlanIndex = 0;
            if (worker.minePlan.isEmpty()) {
                worker.status = "No mine plan for mode " + worker.mode;
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
            worker.status = "Mine plan complete (reached Y=" + worker.targetY + ")";
            worker.working = false;
            return;
        }

        // Grab exposed ore the worker can already reach; never tunnel off-plan to buried ore.
        BlockPos ore = MinePlanner.findReachableOre(level, worker);
        if (ore != null) {
            BlockState oreState = level.getBlockState(ore);
            if (tool(level, worker, PickaxeItem.class, "pickaxe")
                && (!oreState.requiresCorrectToolForDrops() || worker.getMainHandItem().isCorrectToolForDrops(oreState))) {
                breakBlock(level, worker, ore);
                sealFluidNeighbors(level, worker, ore);
                worker.status = "Mined " + oreState.getBlock().getName().getString() + " at " + ore.toShortString();
                return;
            }
            worker.unreachable.put(ore.asLong(), level.getGameTime() + 12000);
        }

        MinePlanner.Step step = worker.minePlan.get(worker.minePlanIndex);
        if (!level.isLoaded(step.pos())) { worker.status = "Mine chunk unloaded"; return; }
        if (!worker.approach(step.pos())) return;

        switch (step.kind()) {
            case DIG -> {
                BlockState state = level.getBlockState(step.pos());
                if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                    if (!tool(level, worker, PickaxeItem.class, "pickaxe")) return;
                    if (state.requiresCorrectToolForDrops() && !worker.getMainHandItem().isCorrectToolForDrops(state)) {
                        worker.status = "Pickaxe too weak for " + state.getBlock().getName().getString();
                        return;
                    }
                    breakBlock(level, worker, step.pos());
                    sealFluidNeighbors(level, worker, step.pos());
                }
            }
            case PLACE_TORCH -> {
                if (!placeWallTorch(level, worker, step.pos())) worker.status = "No torches — lighting skipped";
            }
            case PLACE_LADDER -> {
                if (level.getBlockState(step.pos()).canBeReplaced() || level.getBlockState(step.pos()).isAir()) {
                    if (!take(level, worker, new ItemStack(Items.LADDER))) {
                        worker.status = "Missing ladder";
                        return;
                    }
                    level.setBlock(step.pos(), Blocks.LADDER.defaultBlockState(), 3);
                }
            }
            case PLACE_COBBLE -> {
                if (level.getBlockState(step.pos()).canBeReplaced() || level.getBlockState(step.pos()).isAir()) {
                    if (!take(level, worker, new ItemStack(Items.COBBLESTONE))) {
                        worker.status = "Missing cobblestone";
                        return;
                    }
                    level.setBlock(step.pos(), Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
            }
        }
        worker.minePlanIndex++;
        worker.status = "Digging down: Y=" + worker.blockPosition().getY() + " → " + worker.targetY
            + " (step " + worker.minePlanIndex + "/" + worker.minePlan.size() + ")";
    }

    private static boolean stepDone(ServerLevel level, MinePlanner.Step step) {
        if (!level.isLoaded(step.pos())) return false;
        BlockState state = level.getBlockState(step.pos());
        return switch (step.kind()) {
            case DIG -> state.isAir() || state.is(Blocks.BEDROCK);
            case PLACE_LADDER -> state.is(Blocks.LADDER);
            case PLACE_COBBLE -> !state.isAir() && !state.canBeReplaced();
            case PLACE_TORCH -> false;
        };
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

    private static void breakBlock(ServerLevel level, Worker worker, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), worker, worker.getMainHandItem());
        level.removeBlock(pos, false);
        drops.forEach(worker::collect);
        worker.getMainHandItem().hurtAndBreak(1, worker, EquipmentSlot.MAINHAND);
    }

    private static boolean toolAvailable(Worker worker, Class<?> type) {
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

    static boolean tool(ServerLevel level, Worker worker, Class<?> type, String name) {
        if (type.isInstance(worker.getMainHandItem().getItem())) return true;
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (type.isInstance(stack.getItem())) {
                worker.setItemSlot(EquipmentSlot.MAINHAND, stack.split(1));
                return true;
            }
        }
        if (worker.supply != null && level.getBlockEntity(worker.supply) instanceof Container chest) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                if (type.isInstance(stack.getItem())) {
                    worker.setItemSlot(EquipmentSlot.MAINHAND, chest.removeItem(i, 1));
                    chest.setChanged();
                    return true;
                }
            }
        }
        if (Crafting.tryCraft(level, worker, type)) return tool(level, worker, type, name);
        worker.status = "Missing " + name;
        return false;
    }

    static boolean take(ServerLevel level, Worker worker, ItemStack template) {
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, template)) { stack.shrink(1); return true; }
        }
        if (worker.supply != null && level.getBlockEntity(worker.supply) instanceof Container chest) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                if (ItemStack.isSameItemSameComponents(stack, template)) {
                    stack.shrink(1);
                    chest.setChanged();
                    return true;
                }
            }
        }
        return false;
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
        if (!level.isLoaded(worker.output)) { worker.status = "Output chunk unloaded"; return false; }
        if (!worker.approach(worker.output)) {
            worker.status = "Returning loot to chest";
            return false;
        }
        BlockEntity entity = level.getBlockEntity(worker.output);
        if (!(entity instanceof Container chest)) { worker.status = "Output chest missing"; return false; }
        for (int i = 0; i < worker.bag.getContainerSize(); i++) {
            ItemStack stack = worker.bag.getItem(i);
            if (stack.isEmpty() || isTool(stack)) continue;
            int depositLimit = stack.getCount();
            if (stack.is(ItemTags.SAPLINGS)) {
                int total = countItem(worker, stack.getItem());
                int excess = total - SAPLING_RESERVE;
                if (excess <= 0) continue;
                depositLimit = Math.min(stack.getCount(), excess);
            }
            ItemStack moving = stack.copyWithCount(depositLimit);
            for (int j = 0; j < chest.getContainerSize() && !moving.isEmpty(); j++) {
                ItemStack slot = chest.getItem(j);
                if (slot.isEmpty()) { chest.setItem(j, moving.copy()); moving.setCount(0); break; }
                if (ItemStack.isSameItemSameComponents(slot, moving) && slot.getCount() < slot.getMaxStackSize()) {
                    int moved = Math.min(moving.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(moved);
                    moving.shrink(moved);
                }
            }
            stack.shrink(depositLimit - moving.getCount());
            if (stack.isEmpty()) worker.bag.setItem(i, ItemStack.EMPTY);
        }
        chest.setChanged();
        if (bagFull(worker)) {
            worker.status = "Output storage full";
            return false;
        }
        worker.status = "Dropped off loot";
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
