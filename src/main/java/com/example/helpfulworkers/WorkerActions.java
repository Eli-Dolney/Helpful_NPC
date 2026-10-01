package com.example.helpfulworkers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;

final class WorkerActions {
    private WorkerActions() {}

    static Worker findOwned(ServerPlayer player, int workerId) {
        Entity entity = player.serverLevel().getEntity(workerId);
        if (!(entity instanceof Worker worker)) return null;
        if (!worker.owns(player) || worker.level() != player.level()) return null;
        return worker;
    }

    static Worker nearestOwned(ServerPlayer player, double range) {
        Worker found = null;
        double best = Double.MAX_VALUE;
        for (Worker worker : player.serverLevel().getEntitiesOfClass(Worker.class, player.getBoundingBox().inflate(range))) {
            double distance = player.distanceToSqr(worker);
            if (worker.owns(player) && distance < best) {
                found = worker;
                best = distance;
            }
        }
        return found;
    }

    static ActionResult requireOwned(ServerPlayer player, Worker worker) {
        if (worker == null) return ActionResult.fail("Worker not found");
        if (!worker.owns(player)) return ActionResult.fail("This worker belongs to someone else.");
        if (worker.level() != player.level()) return ActionResult.fail("Worker is in another dimension");
        return null;
    }

    static ActionResult requireMenu(ServerPlayer player, Worker worker) {
        ActionResult owned = requireOwned(player, worker);
        if (owned != null) return owned;
        if (!WorkerSessions.hasDialogueOrInventory(player, worker)) {
            return ActionResult.fail("Open this worker's dialogue first");
        }
        return null;
    }

    static ActionResult start(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (worker.needsUnload && Jobs.bagFull(worker) && worker.output == null) {
            return ActionResult.fail(Jobs.OUT_OF_SPACE);
        }
        worker.working = true;
        worker.needsUnload = Jobs.bagFull(worker);
        worker.target = null;
        worker.status = "Working";
        sync(worker);
        return ActionResult.ok("Worker started");
    }

    static ActionResult pause(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.working = false;
        worker.target = null;
        worker.getNavigation().stop();
        worker.status = "Paused by owner";
        sync(worker);
        return ActionResult.ok("Worker paused — dig site kept. Clear or redraw it if you want a new job.");
    }

    static ActionResult toggleWork(ServerPlayer player, Worker worker) {
        return worker.working ? pause(player, worker) : start(player, worker);
    }

    static ActionResult home(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.working = false;
        worker.target = null;
        worker.getNavigation().stop();
        if (worker.bed != null) {
            worker.getNavigation().moveTo(worker.bed.getX() + 0.5, worker.bed.getY(), worker.bed.getZ() + 0.5, 1);
            worker.status = "Going home";
        } else {
            worker.status = "No bed assigned";
        }
        sync(worker);
        return ActionResult.ok("Worker sent home");
    }

    static ActionResult setRole(ServerPlayer player, Worker worker, String role, boolean requireKit) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!isRole(role)) return ActionResult.fail("Unknown role: " + role);
        if (requireKit && !player.getAbilities().instabuild) {
            Item kit = kitForRole(role);
            if (kit == null || !hasItem(player, kit)) {
                return ActionResult.fail("You need a " + roleLabel(role) + "'s Kit to assign that role");
            }
        }
        worker.role = role;
        worker.working = false;
        worker.getNavigation().stop();
        worker.target = null;
        worker.navigationTarget = null;
        worker.scanCursor = 0;
        worker.stuckTicks = 0;
        worker.lastDistance = Double.MAX_VALUE;
        worker.minePlan = null;
        worker.minePlanIndex = 0;
        worker.furnaces.clear();
        worker.pickups.clear();
        worker.treeIndex.clear();
        worker.waterCache.clear();
        if (worker.culling) {
            WorkerActions.returnBorrowedSword(worker);
            worker.culling = false;
        }
        worker.equipDirty = true;
        if (isCombatRole(role) && (worker.companionMode == null || worker.companionMode.isBlank())) {
            worker.companionMode = "follow";
        }
        worker.status = "Assigned as " + roleLabel(role);
        if (worker.baseName == null || worker.baseName.isBlank()) {
            worker.baseName = Worker.stripRoleSuffix(worker.getName().getString());
        }
        worker.refreshDisplayName();
        worker.rebuildGoals();
        sync(worker);
        return ActionResult.ok(worker.status);
    }

    static ActionResult setCompanionMode(ServerPlayer player, Worker worker, String mode) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!isCombatRole(worker.role)) return ActionResult.fail("Only knights and archers use protection modes");
        if (!mode.equals("follow") && !mode.equals("guard") && !mode.equals("stay")) {
            return ActionResult.fail("Unknown protection mode");
        }
        worker.companionMode = mode;
        worker.working = true;
        worker.status = switch (mode) {
            case "follow" -> "Following you";
            case "guard" -> "Guarding the village";
            default -> "Holding position";
        };
        worker.rebuildGoals();
        sync(worker);
        return ActionResult.ok(worker.status);
    }

    static ActionResult setMode(ServerPlayer player, Worker worker, String mode) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!mode.equals("excavate") && !mode.equals("branches") && !mode.equals("terraform")
            && !mode.equals("staircase") && !mode.equals("strip") && !mode.equals("colony")) {
            return ActionResult.fail("Unknown mining mode");
        }
        if (!"miner".equals(worker.role)) return ActionResult.fail("Mining mode is only for miners");
        worker.mode = mode;
        worker.scanCursor = 0;
        worker.target = null;
        worker.minePlan = null;
        if (!"terraform".equals(mode) && !"staircase".equals(mode) && !"strip".equals(mode) && !"colony".equals(mode)) {
            worker.digDepth = 0;
        }
        sync(worker);
        return ActionResult.ok("Mining mode: " + mode);
    }

    static ActionResult setTargetY(ServerPlayer player, Worker worker, int y) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!"miner".equals(worker.role)) return ActionResult.fail("Target Y is only for miners");
        worker.targetY = y;
        worker.scanCursor = 0;
        worker.minePlan = null;
        sync(worker);
        return ActionResult.ok("Target depth set to Y=" + y);
    }

    static ActionResult toggleCrop(ServerPlayer player, Worker worker, String crop) {
        ActionResult check = requireMenu(player, worker);
        if (check != null) return check;
        if (!"farmer".equals(worker.role)) return ActionResult.fail("Only farmers manage crops");
        if (worker.enabledCrops.contains(crop)) worker.enabledCrops.remove(crop);
        else worker.enabledCrops.add(crop);
        sync(worker);
        return ActionResult.ok((worker.enabledCrops.contains(crop) ? "Enabled " : "Disabled ") + crop);
    }

    static ActionResult toggleBoneMeal(ServerPlayer player, Worker worker) {
        ActionResult check = requireMenu(player, worker);
        if (check != null) return check;
        worker.useBoneMeal = !worker.useBoneMeal;
        sync(worker);
        return ActionResult.ok(worker.useBoneMeal ? "Bone meal on" : "Bone meal off");
    }

    static ActionResult setArea(ServerPlayer player, Worker worker, BlockPos first, BlockPos second) {
        return setArea(player, worker, first, second, worker.digDepth);
    }

    static ActionResult setArea(ServerPlayer player, Worker worker, BlockPos first, BlockPos second, int digDepth) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (first == null || second == null) return ActionResult.fail("Both area corners are required");
        if (!worker.level().isLoaded(first) || !worker.level().isLoaded(second)) {
            return ActionResult.fail("Area corners must be loaded");
        }
        boolean plannedMine = "miner".equals(worker.role) && isPlannedMineMode(worker.mode);
        if (plannedMine) digDepth = 0;
        int top = Math.max(first.getY(), second.getY());
        int minX = Math.min(first.getX(), second.getX());
        int maxX = Math.max(first.getX(), second.getX());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxZ = Math.max(first.getZ(), second.getZ());
        int bottom = Math.min(first.getY(), second.getY());
        if (digDepth > 0) bottom = top - digDepth + 1;
        else if (digDepth < 0) bottom = worker.level().getMinBuildHeight();
        if (bottom < worker.level().getMinBuildHeight()) bottom = worker.level().getMinBuildHeight();
        long volume = (long)(maxX - minX + 1) * (top - bottom + 1) * (maxZ - minZ + 1);
        if (volume > 32768) {
            return ActionResult.fail("Area too large (" + volume + " blocks). Max is 32768. Shrink the footprint or depth.");
        }
        if (plannedMine) {
            // Keep click order: the stairway starts at the first corner and heads toward the second.
            worker.first = first.immutable();
            worker.second = second.immutable();
        } else {
            worker.first = new BlockPos(minX, top, minZ);
            worker.second = new BlockPos(maxX, digDepth == 0 ? bottom : top, maxZ);
        }
        worker.digDepth = digDepth;
        worker.scanCursor = 0;
        worker.target = null;
        worker.minePlan = null;
        worker.minePlanIndex = 0;
        worker.unreachable.clear();
        worker.failedRoutes = 0;
        worker.waterCache.clear();
        worker.treeIndex.clear();
        worker.treeIndexRefresh = 0;
        sync(worker);
        WorkerNetwork.sendAreaOutline(player, worker, 200);
        if (plannedMine) {
            return ActionResult.ok("Mine entrance set at " + first.toShortString() + ", digging toward "
                + second.toShortString() + " down to Y=" + worker.targetY);
        }
        String depthLabel = digDepth < 0 ? "to bedrock" : digDepth > 0 ? digDepth + " deep" : "clicked height";
        return ActionResult.ok("Work area set (" + depthLabel + ", " + volume + " blocks)");
    }

    static boolean isPlannedMineMode(String mode) {
        return "staircase".equals(mode) || "strip".equals(mode) || "colony".equals(mode);
    }

    /**
     * For builders: ensure a village house is loaded, fit it into the marked plot, and start.
     * Auto-picks a plains small house when nothing is loaded yet.
     */
    static ActionResult startVillageOnPlot(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!"builder".equals(worker.role)) return ActionResult.fail("Only builders can build village houses");
        if (worker.first == null || worker.second == null) {
            return ActionResult.fail("Mark a plot first (two corners). Aim for at least 10×10.");
        }
        int plotW = Math.abs(worker.first.getX() - worker.second.getX()) + 1;
        int plotD = Math.abs(worker.first.getZ() - worker.second.getZ()) + 1;
        if (plotW < 10 || plotD < 10) {
            return ActionResult.fail("Area too small — mark at least a 10×10 plot (yours is "
                + plotW + "×" + plotD + "). Retry with a larger area.");
        }
        if (worker.blueprintBlocks.isEmpty() || !Blueprints.isFreePreset(worker.blueprint)) {
            String loaded = VillageCatalog.loadIntoWorker((ServerLevel) worker.level(), worker, "plains_small_house");
            if (!loaded.startsWith("Loaded")) {
                // fallback: first plains entry
                var list = VillageCatalog.forBiome("plains");
                if (list.isEmpty()) return ActionResult.fail("No village houses available");
                loaded = VillageCatalog.loadIntoWorker((ServerLevel) worker.level(), worker, list.get(0).id());
                if (!loaded.startsWith("Loaded")) return ActionResult.fail(loaded);
            }
        }
        String fit = Blueprints.fitToPlot(worker);
        if (fit != null) {
            VillageCatalog.Entry chosen = VillageCatalog.byId(worker.blueprint);
            String biome = chosen == null ? "plains" : chosen.biome();
            String fallback = null;
            int bestArea = -1;
            for (VillageCatalog.Entry entry : VillageCatalog.forBiome(biome)) {
                if (!VillageCatalog.loadIntoWorker((ServerLevel) worker.level(), worker, entry.id()).startsWith("Loaded")) continue;
                int[] size = Blueprints.footprint(worker);
                boolean fits = Math.max(size[0], size[2]) <= Math.max(plotW, plotD)
                    && Math.min(size[0], size[2]) <= Math.min(plotW, plotD);
                if (fits && size[0] * size[2] > bestArea) { bestArea = size[0] * size[2]; fallback = entry.id(); }
            }
            if (fallback == null) {
                if (chosen != null) VillageCatalog.loadIntoWorker((ServerLevel) worker.level(), worker, chosen.id());
                return ActionResult.fail(fit);
            }
            VillageCatalog.loadIntoWorker((ServerLevel) worker.level(), worker, fallback);
            fit = Blueprints.fitToPlot(worker);
            if (fit != null) return ActionResult.fail(fit);
        }
        worker.working = true;
        worker.status = "Building " + worker.blueprint;
        sync(worker);
        WorkerNetwork.sendAreaOutline(player, worker, 200);
        int[] size = Blueprints.footprint(worker);
        return ActionResult.ok("Building " + BuilderPresets.displayName(worker.blueprint)
            + " on your " + plotW + "×" + plotD + " plot (" + size[0] + "×" + size[2] + " house, free)");
    }

    static ActionResult prepareVillageHouse(ServerPlayer player, Worker worker, String entryId) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!"builder".equals(worker.role)) return ActionResult.fail("Only builders can load village houses");
        String loaded = VillageCatalog.loadIntoWorker((ServerLevel) worker.level(), worker, entryId);
        if (!loaded.startsWith("Loaded")) return ActionResult.fail(loaded);
        int[] size = Blueprints.footprint(worker);
        sync(worker);
        return ActionResult.ok(loaded + ". Mark a plot at least " + size[0] + "×" + size[2]
            + ", then choose Build on marked plot.");
    }

    static ActionResult setDigDepth(ServerPlayer player, Worker worker, int digDepth) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        WorkerSessions.ClipboardSession session = WorkerSessions.getClipboard(player);
        if (session != null && session.awaitingDepth && session.pendingFirst != null && session.pendingConfirm != null) {
            ActionResult result = setArea(player, worker, session.pendingFirst, session.pendingConfirm, digDepth);
            WorkerSessions.clearClipboard(player);
            if (result.success()) {
                worker.working = true;
                worker.status = "Working";
                WorkerNetwork.syncStatusToViewers(worker);
            }
            return result;
        }
        if (worker.first == null || worker.second == null) return ActionResult.fail("Set a work area first");
        return setArea(player, worker, worker.first, worker.second, digDepth);
    }

    static ActionResult clearArea(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.first = null;
        worker.second = null;
        worker.digDepth = 0;
        worker.scanCursor = 0;
        worker.target = null;
        worker.working = false;
        worker.getNavigation().stop();
        worker.status = "Dig site cleared — mark a new work area when ready";
        sync(worker);
        return ActionResult.ok("Work area cleared");
    }

    /** Pause, wipe the dig site, and hand the player the clipboard to redraw. */
    static ActionResult redrawArea(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.first = null;
        worker.second = null;
        worker.digDepth = 0;
        worker.scanCursor = 0;
        worker.target = null;
        worker.working = false;
        worker.getNavigation().stop();
        worker.status = "Waiting for a new dig site";
        sync(worker);
        return ActionResult.ok("Old dig site cancelled");
    }

    static ActionResult setBed(ServerPlayer player, Worker worker, BlockPos pos) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        ActionResult range = requireNear(player, pos);
        if (range != null) return range;
        if (!(worker.level().getBlockState(pos).getBlock() instanceof BedBlock)) {
            return ActionResult.fail("Look at a bed");
        }
        worker.bed = pos.immutable();
        sync(worker);
        return ActionResult.ok("Assigned bed at " + pos.toShortString());
    }

    static ActionResult setSupply(ServerPlayer player, Worker worker, BlockPos pos) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        ActionResult range = requireNear(player, pos);
        if (range != null) return range;
        if (!(worker.level().getBlockEntity(pos) instanceof Container)) {
            return ActionResult.fail("Look at a chest or other inventory");
        }
        worker.supply = pos.immutable();
        sync(worker);
        return ActionResult.ok("Assigned supply at " + pos.toShortString());
    }

    static ActionResult setOutput(ServerPlayer player, Worker worker, BlockPos pos) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        ActionResult range = requireNear(player, pos);
        if (range != null) return range;
        if (!(worker.level().getBlockEntity(pos) instanceof Container)) {
            return ActionResult.fail("Look at a chest or other inventory");
        }
        worker.output = pos.immutable();
        if (worker.needsUnload) {
            worker.needsUnload = false;
            worker.status = "Output chest ready — start work to drop off loot";
        }
        sync(worker);
        return ActionResult.ok("Assigned output at " + pos.toShortString());
    }

    static ActionResult setTable(ServerPlayer player, Worker worker, BlockPos pos) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        ActionResult range = requireNear(player, pos);
        if (range != null) return range;
        if (!worker.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
            return ActionResult.fail("Look at a crafting table");
        }
        worker.craftingTable = pos.immutable();
        sync(worker);
        return ActionResult.ok("Assigned crafting table at " + pos.toShortString());
    }

    static ActionResult setBuildOrigin(ServerPlayer player, Worker worker, BlockPos pos) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        ActionResult range = requireNear(player, pos);
        if (range != null) return range;
        if (worker.blueprintBlocks.isEmpty()) return ActionResult.fail("Capture or load a blueprint first");
        return ActionResult.ok(Blueprints.place(worker, pos.immutable(), worker.buildRotation));
    }

    static ActionResult clearBed(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.bed = null;
        sync(worker);
        return ActionResult.ok("Bed assignment cleared");
    }

    static ActionResult clearSupply(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.supply = null;
        sync(worker);
        return ActionResult.ok("Supply assignment cleared");
    }

    static ActionResult clearOutput(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.output = null;
        sync(worker);
        return ActionResult.ok("Output assignment cleared");
    }

    static ActionResult clearTable(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.craftingTable = null;
        sync(worker);
        return ActionResult.ok("Crafting table assignment cleared");
    }

    static ActionResult clearOrigin(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.buildOrigin = null;
        worker.scanCursor = 0;
        sync(worker);
        return ActionResult.ok("Blueprint origin cleared");
    }

    static ActionResult addFurnace(ServerPlayer player, Worker worker, BlockPos pos) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!"smelter".equals(worker.role)) return ActionResult.fail("Only smelters use furnaces");
        ActionResult range = requireNear(player, pos);
        if (range != null) return range;
        if (!(worker.level().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock)) {
            return ActionResult.fail("Look at a furnace, smoker, or blast furnace");
        }
        if (worker.furnaces.size() >= 4) return ActionResult.fail("Max 4 furnaces");
        BlockPos p = pos.immutable();
        if (!worker.furnaces.contains(p)) worker.furnaces.add(p);
        sync(worker);
        return ActionResult.ok("Furnace assigned (" + worker.furnaces.size() + "/4)");
    }

    static ActionResult addPickup(ServerPlayer player, Worker worker, BlockPos pos) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!"courier".equals(worker.role)) return ActionResult.fail("Only couriers use pickup chests");
        ActionResult range = requireNear(player, pos);
        if (range != null) return range;
        if (!(worker.level().getBlockEntity(pos) instanceof Container)) {
            return ActionResult.fail("Look at a chest");
        }
        if (worker.pickups.size() >= 6) return ActionResult.fail("Max 6 pickup chests");
        BlockPos p = pos.immutable();
        if (!worker.pickups.contains(p)) worker.pickups.add(p);
        sync(worker);
        return ActionResult.ok("Pickup chest assigned (" + worker.pickups.size() + "/6)");
    }

    static ActionResult clearFurnaces(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.furnaces.clear();
        sync(worker);
        return ActionResult.ok("Furnaces cleared");
    }

    static ActionResult clearPickups(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.pickups.clear();
        sync(worker);
        return ActionResult.ok("Pickup chests cleared");
    }

    static ActionResult toggleRanchAnimal(ServerPlayer player, Worker worker, String animal) {
        ActionResult check = requireMenu(player, worker);
        if (check != null) return check;
        if (!"rancher".equals(worker.role)) return ActionResult.fail("Only ranchers manage animals");
        if (!List.of("sheep", "cow", "pig", "chicken").contains(animal)) {
            return ActionResult.fail("Unknown animal");
        }
        if (worker.ranchAnimals.contains(animal)) worker.ranchAnimals.remove(animal);
        else worker.ranchAnimals.add(animal);
        sync(worker);
        return ActionResult.ok((worker.ranchAnimals.contains(animal) ? "Handling " : "Ignoring ") + animal);
    }

    static ActionResult toggleRanchFlag(ServerPlayer player, Worker worker, String flag) {
        ActionResult check = requireMenu(player, worker);
        if (check != null) return check;
        if (!"rancher".equals(worker.role)) return ActionResult.fail("Only ranchers manage the pen");
        switch (flag) {
            case "breed" -> worker.ranchBreed = !worker.ranchBreed;
            case "shear" -> worker.ranchShear = !worker.ranchShear;
            case "milk" -> worker.ranchMilk = !worker.ranchMilk;
            case "eggs" -> worker.ranchEggs = !worker.ranchEggs;
            default -> { return ActionResult.fail("Unknown ranch option"); }
        }
        sync(worker);
        boolean on = switch (flag) {
            case "breed" -> worker.ranchBreed;
            case "shear" -> worker.ranchShear;
            case "milk" -> worker.ranchMilk;
            case "eggs" -> worker.ranchEggs;
            default -> false;
        };
        return ActionResult.ok(capitalize(flag) + (on ? " on" : " off"));
    }

    /** Start a one-shot cull-to-2. borrowSword=true takes a sword (prefer Looting) from the player. */
    static ActionResult startCull(ServerPlayer player, Worker worker, boolean borrowSword) {
        ActionResult check = requireMenu(player, worker);
        if (check != null) return check;
        if (!"rancher".equals(worker.role)) return ActionResult.fail("Only ranchers cull animals");
        if (worker.culling) return ActionResult.fail("Already thinning the herd");
        if (borrowSword) {
            ItemStack sword = takeLootingSword(player);
            if (sword.isEmpty()) return ActionResult.fail("No sword to borrow — hold one or keep one in your inventory");
            worker.stashedMainHand = worker.getMainHandItem().copy();
            worker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, sword);
            worker.borrowedSword = sword.copy();
            worker.swordLender = player.getUUID();
        }
        worker.culling = true;
        worker.working = true;
        worker.setStatus("Thinning herd to 2 each");
        sync(worker);
        return ActionResult.ok(borrowSword
            ? "Borrowing your sword — thinning to 2 of each kind"
            : "Thinning herd to 2 of each kind (no Looting)");
    }

    private static ItemStack takeLootingSword(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(net.minecraft.tags.ItemTags.SWORDS)) {
            ItemStack taken = main.copyWithCount(1);
            main.shrink(1);
            return taken;
        }
        var looting = player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .get(net.minecraft.world.item.enchantment.Enchantments.LOOTING);
        ItemStack best = ItemStack.EMPTY;
        int bestSlot = -1;
        int bestLooting = -1;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(net.minecraft.tags.ItemTags.SWORDS)) continue;
            int level = looting.isEmpty() ? 0
                : net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(looting.get(), stack);
            if (best.isEmpty() || level > bestLooting) {
                best = stack;
                bestSlot = i;
                bestLooting = level;
            }
        }
        if (bestSlot < 0) return ItemStack.EMPTY;
        ItemStack taken = best.copyWithCount(1);
        best.shrink(1);
        return taken;
    }

    static void returnBorrowedSword(Worker worker) {
        if (worker.borrowedSword.isEmpty() && worker.swordLender == null) {
            if (!worker.stashedMainHand.isEmpty()) {
                worker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, worker.stashedMainHand);
                worker.stashedMainHand = ItemStack.EMPTY;
            }
            return;
        }
        ItemStack sword = worker.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS)
            ? worker.getMainHandItem().copy() : worker.borrowedSword.copy();
        worker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
            worker.stashedMainHand.isEmpty() ? ItemStack.EMPTY : worker.stashedMainHand);
        worker.stashedMainHand = ItemStack.EMPTY;
        worker.borrowedSword = ItemStack.EMPTY;
        UUID lender = worker.swordLender;
        worker.swordLender = null;
        if (sword.isEmpty() || lender == null) return;
        if (worker.level() instanceof net.minecraft.server.level.ServerLevel level) {
            net.minecraft.server.level.ServerPlayer owner =
                level.getServer().getPlayerList().getPlayer(lender);
            if (owner != null) {
                if (!owner.getInventory().add(sword)) {
                    owner.drop(sword, false);
                }
                return;
            }
            if (worker.supply != null && level.getBlockEntity(worker.supply) instanceof net.minecraft.world.Container chest) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    if (chest.getItem(i).isEmpty()) {
                        chest.setItem(i, sword);
                        chest.setChanged();
                        return;
                    }
                }
            }
            worker.collect(sword);
        }
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static ActionResult approveRecipe(ServerPlayer player, Worker worker, String id) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        ActionResult support = Crafting.validateRecipe(worker.level() instanceof ServerLevel level ? level : null, id);
        if (support != null && !support.success()) return support;
        if (!worker.recipes.contains(id)) worker.recipes.add(id);
        sync(worker);
        return ActionResult.ok("Approved recipe " + id);
    }

    static ActionResult removeRecipe(ServerPlayer player, Worker worker, String id) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.recipes.remove(id);
        sync(worker);
        return ActionResult.ok("Removed recipe " + id);
    }

    static ActionResult captureBlueprint(ServerPlayer player, Worker worker, String name, boolean allowOverwrite) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!"builder".equals(worker.role)) return ActionResult.fail("Only builders can capture blueprints");
        if (!allowOverwrite && Blueprints.hasName(worker, name)) {
            return ActionResult.fail("Blueprint \"" + name + "\" already exists. Confirm overwrite.");
        }
        String result = Blueprints.capture((ServerLevel) worker.level(), worker, name);
        sync(worker);
        boolean ok = result.startsWith("Saved");
        return ok ? ActionResult.ok(result) : ActionResult.fail(result);
    }

    static ActionResult loadBlueprint(ServerPlayer player, Worker worker, String name) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        String result;
        if (VillageCatalog.byId(name) != null) {
            result = VillageCatalog.loadIntoWorker((ServerLevel) worker.level(), worker, name);
        } else {
            result = Blueprints.load(worker, name);
        }
        sync(worker);
        return result.startsWith("Loaded") ? ActionResult.ok(result) : ActionResult.fail(result);
    }

    static ActionResult setRotation(ServerPlayer player, Worker worker, int rotation) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (rotation < 0 || rotation > 3) return ActionResult.fail("Rotation must be 0-3");
        worker.buildRotation = rotation;
        worker.scanCursor = 0;
        sync(worker);
        return ActionResult.ok("Rotation set to " + (rotation * 90) + " degrees");
    }

    static ActionResult preview(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        return ActionResult.ok(Blueprints.preview((ServerLevel) worker.level(), worker));
    }

    static ActionResult materials(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        return ActionResult.ok(Blueprints.materialsReadable((ServerLevel) worker.level(), worker));
    }

    static ActionResult status(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        return ActionResult.ok(worker.role + ": " + worker.status);
    }

    static List<String> blueprintNames(Worker worker) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < worker.blueprintLibrary.size(); i++) {
            names.add(worker.blueprintLibrary.getCompound(i).getString("Name"));
        }
        return names;
    }

    static boolean isRole(String role) {
        return role.equals("farmer") || role.equals("forester") || role.equals("miner")
            || role.equals("builder") || role.equals("knight") || role.equals("archer")
            || role.equals("rancher") || role.equals("fisher") || role.equals("smelter")
            || role.equals("courier");
    }

    static boolean isCombatRole(String role) {
        return "knight".equals(role) || "archer".equals(role);
    }

    static boolean isGatherRole(String role) {
        return "forester".equals(role) || "farmer".equals(role) || "miner".equals(role)
            || "rancher".equals(role) || "fisher".equals(role) || "smelter".equals(role)
            || "courier".equals(role);
    }

    static Item kitForRole(String role) {
        return switch (role) {
            case "farmer" -> HelpfulWorkers.FARMER_KIT.get();
            case "forester" -> HelpfulWorkers.FORESTER_KIT.get();
            case "miner" -> HelpfulWorkers.MINER_KIT.get();
            case "builder" -> HelpfulWorkers.BUILDER_KIT.get();
            case "knight" -> HelpfulWorkers.KNIGHT_KIT.get();
            case "archer" -> HelpfulWorkers.ARCHER_KIT.get();
            case "rancher" -> HelpfulWorkers.RANCHER_KIT.get();
            case "fisher" -> HelpfulWorkers.FISHER_KIT.get();
            case "smelter" -> HelpfulWorkers.SMELTER_KIT.get();
            case "courier" -> HelpfulWorkers.COURIER_KIT.get();
            default -> null;
        };
    }

    static String roleForKit(Item item) {
        if (item == HelpfulWorkers.FARMER_KIT.get()) return "farmer";
        if (item == HelpfulWorkers.FORESTER_KIT.get()) return "forester";
        if (item == HelpfulWorkers.MINER_KIT.get()) return "miner";
        if (item == HelpfulWorkers.BUILDER_KIT.get()) return "builder";
        if (item == HelpfulWorkers.KNIGHT_KIT.get()) return "knight";
        if (item == HelpfulWorkers.ARCHER_KIT.get()) return "archer";
        if (item == HelpfulWorkers.RANCHER_KIT.get()) return "rancher";
        if (item == HelpfulWorkers.FISHER_KIT.get()) return "fisher";
        if (item == HelpfulWorkers.SMELTER_KIT.get()) return "smelter";
        if (item == HelpfulWorkers.COURIER_KIT.get()) return "courier";
        return null;
    }

    static String roleLabel(String role) {
        return switch (role) {
            case "farmer" -> "Farmer";
            case "forester" -> "Forester";
            case "miner" -> "Miner";
            case "builder" -> "Builder";
            case "knight" -> "Knight";
            case "archer" -> "Archer";
            case "rancher" -> "Rancher";
            case "fisher" -> "Fisherman";
            case "smelter" -> "Smelter";
            case "courier" -> "Courier";
            default -> role;
        };
    }

    static boolean hasItem(ServerPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) return true;
        }
        return false;
    }

    static ActionResult requireNear(ServerPlayer player, BlockPos pos) {
        if (pos == null) return ActionResult.fail("No block selected");
        if (player.blockPosition().distSqr(pos) > 64) return ActionResult.fail("Target must be within 8 blocks");
        if (!player.level().isLoaded(pos)) return ActionResult.fail("Target chunk is unloaded");
        return null;
    }

    static void sync(Worker worker) {
        WorkerNetwork.syncStatusToViewers(worker);
    }
}
