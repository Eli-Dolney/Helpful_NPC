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
        worker.setStatusKey(Lang.STATUS_WORKING);
        worker.rebuildGoals();
        sync(worker);
        return ActionResult.ok("Worker started");
    }

    static ActionResult pause(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        WarehouseJobs.release(worker.getUUID());
        SiteJobs.suspend(worker);
        worker.working = false;
        worker.target = null;
        worker.getNavigation().stop();
        worker.setStatusKey(Lang.STATUS_PAUSED_BY_OWNER);
        worker.rebuildGoals();
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
            worker.setStatusKey(Lang.STATUS_GOING_HOME);
        } else if (worker.hall != null) {
            worker.getNavigation().moveTo(worker.hall.getX() + 0.5, worker.hall.getY() + 1, worker.hall.getZ() + 0.5, 1);
            worker.setStatusKey(Lang.STATUS_GOING_TO_HALL);
        } else {
            worker.setStatusKey(Lang.STATUS_NO_BED);
        }
        sync(worker);
        return ActionResult.ok("Worker sent home");
    }

    static ActionResult setRole(ServerPlayer player, Worker worker, String role, boolean requireKit) {
        if (worker.siteId != null || worker.constructionId != null) return ActionResult.fail("Release the site or finish construction before changing roles");
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
        worker.nav.reset();
        worker.scanCursor = 0;
        worker.minePlan = null;
        worker.minePlanIndex = 0;
        worker.furnaces.clear();
        worker.pickups.clear();
        worker.pickupFilters.clear();
        worker.pickupKeep.clear();
        worker.courierCursor = 0;
        worker.furnaceHaul.clear();
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
        // A role with a Resources page starts with everything on unless it was already configured.
        List<String> resources = resourcesFor(role);
        if (!resources.isEmpty() && java.util.Collections.disjoint(worker.enabledCrops, resources)) {
            worker.enabledCrops.addAll("farmer".equals(role) ? Jobs.FARMER_DEFAULT_CROPS : resources);
        }
        worker.setStatusKey(Lang.STATUS_ASSIGNED_AS, roleLabel(role));
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
        worker.setStatusKey(switch (mode) {
            case "follow" -> Lang.STATUS_FOLLOWING;
            case "guard" -> Lang.STATUS_GUARDING;
            default -> Lang.STATUS_HOLDING;
        });
        worker.rebuildGoals();
        sync(worker);
        return ActionResult.ok(worker.status);
    }

    static ActionResult setMode(ServerPlayer player, Worker worker, String mode) {
        if (worker.siteId != null || worker.constructionId != null) return ActionResult.fail("Release the site or finish construction first");
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if ("courier".equals(worker.role)) {
            if (!"zone".equals(mode) && !"porter".equals(mode)) return ActionResult.fail("Courier modes are zone or porter");
            worker.courierMode = mode;
            worker.porterPickups.clear();
            worker.lastPorterScan = 0;
            sync(worker);
            return ActionResult.ok("porter".equals(mode) ? "Collecting from every worker's output chest nearby" : "Hauling between the assigned chests");
        }
        if (!isMineMode(mode)) return ActionResult.fail("Unknown mining mode");
        if (!"miner".equals(worker.role)) return ActionResult.fail("Mining mode is only for miners");
        worker.mode = mode;
        worker.scanCursor = 0;
        worker.target = null;
        worker.minePlan = null;
        if (!usesDigDepth(mode) && !isPlannedMineMode(mode)) {
            worker.digDepth = 0;
        }
        sync(worker);
        return ActionResult.ok("Mining mode: " + mode);
    }

    /** Choose how a builder prepares the ground before placing blocks. */
    static ActionResult setSitePrep(ServerPlayer player, Worker worker, String mode) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!SitePrep.isMode(mode)) return ActionResult.fail("Ground prep must be none, clear or flatten");
        if (!"builder".equals(worker.role)) return ActionResult.fail("Ground prep is only for builders");
        if (!mode.equals(worker.sitePrep)) {
            worker.sitePrep = mode;
            // Only re-inspect the site when nothing has been built yet; a half-built house is left alone.
            if (worker.scanCursor == 0) SitePrep.reset(worker);
        }
        sync(worker);
        return ActionResult.ok("Ground prep: " + SitePrep.label(mode));
    }

    /** Flatten the marked plot (clear everything above ground, fill holes) without building a house. */
    static ActionResult flattenPlot(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!"builder".equals(worker.role)) return ActionResult.fail("Only builders flatten plots");
        if (worker.first == null || worker.second == null) {
            return ActionResult.fail("Mark a plot first (two corners on the ground)");
        }
        if (worker.needsUnload && Jobs.bagFull(worker) && worker.output == null) {
            return ActionResult.fail(Jobs.OUT_OF_SPACE);
        }
        worker.prepOnly = true;
        SitePrep.reset(worker);
        worker.working = true;
        worker.target = null;
        int plotW = Math.abs(worker.first.getX() - worker.second.getX()) + 1;
        int plotD = Math.abs(worker.first.getZ() - worker.second.getZ()) + 1;
        worker.setStatusKey(Lang.STATUS_FLATTENING_PLOT);
        sync(worker);
        WorkerNetwork.sendAreaOutline(player, worker, 200);
        return ActionResult.ok("Flattening the " + plotW + "\u00d7" + plotD + " plot to Y="
            + Math.min(worker.first.getY(), worker.second.getY()) + " (trees and plants cleared, holes filled)");
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
        List<String> resources = resourcesFor(worker.role);
        if (resources.isEmpty()) return ActionResult.fail("Only farmers and gatherers choose resources");
        if (!resources.contains(crop)) return ActionResult.fail("Unknown resource: " + crop);
        if (worker.enabledCrops.contains(crop)) worker.enabledCrops.remove(crop);
        else worker.enabledCrops.add(crop);
        sync(worker);
        return ActionResult.ok((worker.enabledCrops.contains(crop) ? "Enabled " : "Disabled ") + crop);
    }

    // ---------------------------------------------------------------- request board

    /** "What do you need?": ask for {@code count} of the held item; a second ask for the same item adds to it. */
    static ActionResult addRequest(ServerPlayer player, Worker worker, ItemStack held, int count) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (held == null || held.isEmpty()) return ActionResult.fail("Hold the item you need, then ask again");
        if (isCombatRole(worker.role)) return ActionResult.fail("Knights and archers don't collect things");
        if ("stonemason".equals(worker.role)) return addOrder(player, worker, held, count);
        count = Math.max(1, Math.min(2304, count));
        String id = ExtraJobs.itemId(held);
        String name = held.getHoverName().getString();
        for (int i = 0; i < worker.requests.size(); i++) {
            Worker.Request r = worker.requests.get(i);
            if (!r.item().equals(id)) continue;
            worker.requests.set(i, new Worker.Request(id, r.count() + count, r.done()));
            worker.refreshStatus();
            sync(worker);
            return ActionResult.ok("Now collecting " + (r.count() + count) + " " + name + " (" + r.done() + " so far)");
        }
        if (worker.requests.size() >= Worker.MAX_REQUESTS) {
            return ActionResult.fail("The list is full (" + Worker.MAX_REQUESTS + " items). Clear one first.");
        }
        worker.requests.add(new Worker.Request(id, count, 0));
        worker.refreshStatus();
        sync(worker);
        return ActionResult.ok("Asked for " + count + " " + name + (worker.requestsStop ? " — will pause when done" : ""));
    }

    static ActionResult removeRequest(ServerPlayer player, Worker worker, String indexArg) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        int index;
        try { index = Integer.parseInt(indexArg.trim()); } catch (NumberFormatException ex) { return ActionResult.fail("No such request"); }
        if (index < 0 || index >= worker.requests.size()) return ActionResult.fail("No such request");
        Worker.Request removed = worker.requests.remove(index);
        worker.refreshStatus();
        sync(worker);
        return ActionResult.ok("Dropped " + Worker.requestName(removed.item()) + " from the list");
    }

    static ActionResult clearRequests(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.requests.clear();
        worker.refreshStatus();
        sync(worker);
        return ActionResult.ok("Request list cleared");
    }

    /** Stonemason: cut this many of the held item (must be a stonecutter product). */
    static ActionResult addOrder(ServerPlayer player, Worker worker, ItemStack held, int count) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!"stonemason".equals(worker.role)) return ActionResult.fail("Only stonemasons take cutting orders");
        if (held == null || held.isEmpty()) return ActionResult.fail("Hold the block you want cut, then ask again");
        if (!(worker.level() instanceof ServerLevel level) || !StonemasonJobs.canCut(level, held)) {
            return ActionResult.fail(held.getHoverName().getString() + " can't be made on a stonecutter");
        }
        count = Math.max(1, Math.min(2304, count));
        String id = ExtraJobs.itemId(held);
        String name = held.getHoverName().getString();
        for (int i = 0; i < worker.orders.size(); i++) {
            Worker.Order o = worker.orders.get(i);
            if (!o.item().equals(id)) continue;
            worker.orders.set(i, new Worker.Order(id, o.count() + count, o.done(), false));
            sync(worker);
            return ActionResult.ok("Now cutting " + (o.count() + count) + " " + name + " (" + o.done() + " so far)");
        }
        if (worker.orders.size() >= StonemasonJobs.MAX_ORDERS) {
            return ActionResult.fail("The order list is full (" + StonemasonJobs.MAX_ORDERS + " items). Clear one first.");
        }
        worker.orders.add(new Worker.Order(id, count, 0, false));
        sync(worker);
        return ActionResult.ok("Order placed: " + count + " " + name);
    }

    static ActionResult removeOrder(ServerPlayer player, Worker worker, String indexArg) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        int index;
        try { index = Integer.parseInt(indexArg.trim()); } catch (NumberFormatException ex) { return ActionResult.fail("No such order"); }
        if (index < 0 || index >= worker.orders.size()) return ActionResult.fail("No such order");
        Worker.Order removed = worker.orders.remove(index);
        sync(worker);
        return ActionResult.ok("Dropped " + Worker.requestName(removed.item()) + " from the orders");
    }

    static ActionResult clearOrders(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.orders.clear();
        sync(worker);
        return ActionResult.ok("Orders cleared");
    }

    static ActionResult toggleSupplyBuilders(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (!"stonemason".equals(worker.role)) return ActionResult.fail("Only stonemasons supply builders");
        worker.supplyBuilders = !worker.supplyBuilders;
        worker.lastOrderScan = 0;
        if (!worker.supplyBuilders) worker.orders.removeIf(Worker.Order::auto);
        sync(worker);
        return ActionResult.ok(worker.supplyBuilders ? "Cutting whatever nearby builders' blueprints still need" : "Only cutting your own orders");
    }

    /** Cook options: feed hungry workmates / bake bread. */
    static ActionResult toggleCookFlag(ServerPlayer player, Worker worker, String flag) {
        ActionResult check = requireMenu(player, worker);
        if (check != null) return check;
        if (!"cook".equals(worker.role)) return ActionResult.fail("Only cooks have kitchen options");
        switch (flag) {
            case "feed" -> worker.feedWorkers = !worker.feedWorkers;
            case "meals" -> worker.craftMeals = !worker.craftMeals;
            default -> { return ActionResult.fail("Unknown kitchen option"); }
        }
        worker.lastFeedScan = 0;
        sync(worker);
        boolean on = "feed".equals(flag) ? worker.feedWorkers : worker.craftMeals;
        return ActionResult.ok(("feed".equals(flag) ? "Feeding workmates" : "Baking bread") + (on ? " on" : " off"));
    }

    static ActionResult toggleRequestStop(ServerPlayer player, Worker worker) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.requestsStop = !worker.requestsStop;
        sync(worker);
        return ActionResult.ok(worker.requestsStop ? "Will pause once the list is collected" : "Will keep collecting past the list");
    }

    /** "Keep N of the held item in your bag" (0 forgets it). Drop-offs leave that many behind. */
    static ActionResult setReserve(ServerPlayer player, Worker worker, ItemStack held, int count) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (held == null || held.isEmpty()) return ActionResult.fail("Hold the item the worker should keep, then ask again");
        String id = ExtraJobs.itemId(held);
        String name = held.getHoverName().getString();
        if (count <= 0) worker.reserves.remove(id);
        else worker.reserves.put(id, Math.min(count, 27 * 64));
        sync(worker);
        return ActionResult.ok(count <= 0 ? worker.getName().getString() + " will drop off all " + name
            : worker.getName().getString() + " keeps " + count + " " + name + " in the bag");
    }

    static ActionResult toggleBoneMeal(ServerPlayer player, Worker worker) {
        ActionResult check = requireMenu(player, worker);
        if (check != null) return check;
        worker.useBoneMeal = !worker.useBoneMeal;
        sync(worker);
        return ActionResult.ok(worker.useBoneMeal ? "Bone meal on" : "Bone meal off");
    }

    /** Null when a work area of this size is allowed (large areas are scanned in budgeted steps), else why not. */
    static String areaSizeError(int width, int height, int depth) {
        if (width > Worker.MAX_AREA_SIDE || depth > Worker.MAX_AREA_SIDE) {
            return "Area too wide (max " + Worker.MAX_AREA_SIDE + " x " + Worker.MAX_AREA_SIDE + " blocks). Shrink the footprint.";
        }
        long volume = (long) width * height * depth;
        if (volume > Worker.MAX_AREA_VOLUME) {
            return "Area too large (" + volume + " blocks). Max is " + Worker.MAX_AREA_VOLUME + ". Shrink the footprint or depth.";
        }
        return null;
    }

    static ActionResult setArea(ServerPlayer player, Worker worker, BlockPos first, BlockPos second) {
        return setArea(player, worker, first, second, worker.digDepth);
    }

    static ActionResult setArea(ServerPlayer player, Worker worker, BlockPos first, BlockPos second, int digDepth) {
        if (worker.siteId != null || worker.constructionId != null) return ActionResult.fail("Release the site or finish construction before changing zones");
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        if (first == null || second == null) return ActionResult.fail("Both area corners are required");
        // A big area's far corner may be out of view by the time the near one is marked; unloaded
        // parts are simply skipped by the scans until a player is near them again.
        if (!worker.level().isLoaded(first) && !worker.level().isLoaded(second)) {
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
        String sizeError = areaSizeError(maxX - minX + 1, top - bottom + 1, maxZ - minZ + 1);
        if (sizeError != null) return ActionResult.fail(sizeError);
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
        worker.scanPeak = Integer.MAX_VALUE;
        worker.scanPeakAcc = Integer.MIN_VALUE;
        worker.treeColumnsScanned = 0;
        worker.target = null;
        worker.minePlan = null;
        worker.minePlanIndex = 0;
        worker.unreachable.clear();
        worker.nav.reset();
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
        long volume = (long) (maxX - minX + 1) * (top - bottom + 1) * (maxZ - minZ + 1);
        return ActionResult.ok("Work area set (" + depthLabel + ", " + volume + " blocks)");
    }

    static boolean isPlannedMineMode(String mode) {
        return "staircase".equals(mode) || "strip".equals(mode) || "colony".equals(mode);
    }

    static final List<String> MINE_MODES = List.of("excavate", "branches", "terraform", "quarry", "staircase", "strip", "colony");

    static boolean isMineMode(String mode) {
        return MINE_MODES.contains(mode);
    }

    /** Modes whose area is a footprint plus a dig depth chosen afterwards (the depth picker). */
    static boolean usesDigDepth(String mode) {
        return "terraform".equals(mode) || "quarry".equals(mode);
    }

    /**
     * For builders: ensure a village house is loaded, fit it into the marked plot, and start.
     * Auto-picks a plains small house when nothing is loaded yet.
     */
    static ActionResult startVillageOnPlot(ServerPlayer player, Worker worker) {
        if (worker.siteId != null || worker.constructionId != null) return ActionResult.fail("Finish or dismantle the worker site first");
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
        worker.setStatusKey(Lang.STATUS_BUILDING, worker.blueprint);
        sync(worker);
        WorkerNetwork.sendAreaOutline(player, worker, 200);
        int[] size = Blueprints.footprint(worker);
        return ActionResult.ok("Building " + BuilderPresets.displayName(worker.blueprint)
            + " on your " + plotW + "×" + plotD + " plot (" + size[0] + "×" + size[2] + " house, free)");
    }

    static ActionResult prepareVillageHouse(ServerPlayer player, Worker worker, String entryId) {
        if (worker.siteId != null || worker.constructionId != null) return ActionResult.fail("Finish or dismantle the worker site first");
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
                worker.setStatusKey(Lang.STATUS_WORKING);
                WorkerNetwork.syncStatusToViewers(worker);
            }
            return result;
        }
        if (worker.first == null || worker.second == null) return ActionResult.fail("Set a work area first");
        return setArea(player, worker, worker.first, worker.second, digDepth);
    }

    static ActionResult clearArea(ServerPlayer player, Worker worker) {
        if (worker.siteId != null || worker.constructionId != null) return ActionResult.fail("Release the site or finish construction first");
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        worker.first = null;
        worker.second = null;
        worker.digDepth = 0;
        worker.scanCursor = 0;
        worker.target = null;
        worker.working = false;
        worker.getNavigation().stop();
        worker.setStatusKey(Lang.STATUS_DIG_SITE_CLEARED);
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
        worker.setStatusKey(Lang.STATUS_WAITING_DIG_SITE);
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
        worker.rebuildGoals();
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
        worker.supplyPeek.clear();
        worker.nav.cancelErrand();
        sync(worker);
        return ActionResult.ok("Assigned supply at " + pos.toShortString());
    }

    static ActionResult setOutput(ServerPlayer player, Worker worker, BlockPos pos) {
        if (worker.siteId != null || worker.constructionId != null) return ActionResult.fail("Release the site or finish construction first");
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
            worker.setStatusKey(Lang.STATUS_OUTPUT_READY);
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

    /** Craft-trade workstations: anvil / grindstone (toolsmith), stonecutter (stonemason). */
    static ActionResult setWorkstation(ServerPlayer player, Worker worker, String kind, BlockPos pos) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        ActionResult range = requireNear(player, pos);
        if (range != null) return range;
        var state = worker.level().getBlockState(pos);
        switch (kind) {
            case "anvil" -> {
                if (!(state.getBlock() instanceof net.minecraft.world.level.block.AnvilBlock)) return ActionResult.fail("Look at an anvil");
                if (!"toolsmith".equals(worker.role)) return ActionResult.fail("Only toolsmiths use an anvil");
                worker.anvil = pos.immutable();
            }
            case "grindstone" -> {
                if (!state.is(Blocks.GRINDSTONE)) return ActionResult.fail("Look at a grindstone");
                if (!"toolsmith".equals(worker.role)) return ActionResult.fail("Only toolsmiths use a grindstone");
                worker.grindstone = pos.immutable();
            }
            case "stonecutter" -> {
                if (!state.is(Blocks.STONECUTTER)) return ActionResult.fail("Look at a stonecutter");
                if (!"stonemason".equals(worker.role)) return ActionResult.fail("Only stonemasons use a stonecutter");
                worker.stonecutter = pos.immutable();
            }
            default -> { return ActionResult.fail("Unknown workstation"); }
        }
        sync(worker);
        return ActionResult.ok("Assigned " + kind + " at " + pos.toShortString());
    }

    static ActionResult clearWorkstation(ServerPlayer player, Worker worker, String kind) {
        ActionResult check = requireOwned(player, worker);
        if (check != null) return check;
        switch (kind) {
            case "anvil" -> worker.anvil = null;
            case "grindstone" -> worker.grindstone = null;
            case "stonecutter" -> worker.stonecutter = null;
            default -> { return ActionResult.fail("Unknown workstation"); }
        }
        sync(worker);
        return ActionResult.ok(Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + " assignment cleared");
    }

    /** Toolsmith: cycle how many spare tools of a kind to keep in stock (0/1/2/4). */
    static ActionResult cycleToolTarget(ServerPlayer player, Worker worker, String key) {
        ActionResult check = requireMenu(player, worker);
        if (check != null) return check;
        if (!"toolsmith".equals(worker.role)) return ActionResult.fail("Only toolsmiths keep tool stock");
        if (!ToolsmithJobs.TOOL_KEYS.contains(key)) return ActionResult.fail("Unknown tool");
        int current = ToolsmithJobs.target(worker, key);
        int next = ToolsmithJobs.TARGET_STEPS[0];
        for (int i = 0; i < ToolsmithJobs.TARGET_STEPS.length; i++) {
            if (ToolsmithJobs.TARGET_STEPS[i] == current) { next = ToolsmithJobs.TARGET_STEPS[(i + 1) % ToolsmithJobs.TARGET_STEPS.length]; break; }
        }
        worker.toolTargets.put(key, next);
        sync(worker);
        return ActionResult.ok(next == 0 ? "No spare " + ToolsmithJobs.label(key) + " kept" : "Keeping " + next + " spare " + ToolsmithJobs.label(key));
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
        worker.supplyPeek.clear();
        worker.nav.cancelErrand();
        sync(worker);
        return ActionResult.ok("Supply assignment cleared");
    }

    static ActionResult clearOutput(ServerPlayer player, Worker worker) {
        if (worker.siteId != null || worker.constructionId != null) return ActionResult.fail("Release the site or finish construction first");
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
        if (!"smelter".equals(worker.role) && !"courier".equals(worker.role) && !"cook".equals(worker.role)) {
            return ActionResult.fail("Only smelters, cooks and couriers use furnaces");
        }
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
        worker.pickupFilters.clear();
        worker.pickupKeep.clear();
        worker.courierCursor = 0;
        sync(worker);
        return ActionResult.ok("Pickup chests cleared");
    }

    private static final int[] KEEP_STEPS = {0, 8, 16, 32, 64};

    private static BlockPos pickupAt(Worker worker, String indexArg) {
        try {
            int index = Integer.parseInt(indexArg.trim());
            if (index < 0 || index >= worker.pickups.size()) return null;
            return worker.pickups.get(index);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Add the held item to a pickup chest's filter (or remove it when it is already there). Empty filter = take anything. */
    static ActionResult togglePickupFilter(ServerPlayer player, Worker worker, String indexArg, ItemStack held) {
        ActionResult check = requireMenu(player, worker);
        if (check != null) return check;
        if (!"courier".equals(worker.role)) return ActionResult.fail("Only couriers filter pickups");
        BlockPos pickup = pickupAt(worker, indexArg);
        if (pickup == null) return ActionResult.fail("No such pickup chest");
        if (held == null || held.isEmpty()) return ActionResult.fail("Hold the item the courier should take from this chest, then click again");
        String id = ExtraJobs.itemId(held);
        List<String> filter = worker.pickupFilters.computeIfAbsent(pickup.asLong(), k -> new ArrayList<>());
        boolean added;
        if (filter.remove(id)) added = false;
        else { filter.add(id); added = true; }
        if (filter.isEmpty()) worker.pickupFilters.remove(pickup.asLong());
        sync(worker);
        String name = held.getHoverName().getString();
        return ActionResult.ok(added ? "Chest " + (worker.pickups.indexOf(pickup) + 1) + " now gives " + name
            : "Chest " + (worker.pickups.indexOf(pickup) + 1) + " no longer filters " + name
                + (filter.isEmpty() ? " (takes anything)" : ""));
    }

    static ActionResult clearPickupFilter(ServerPlayer player, Worker worker, String indexArg) {
        ActionResult check = requireMenu(player, worker);
        if (check != null) return check;
        BlockPos pickup = pickupAt(worker, indexArg);
        if (pickup == null) return ActionResult.fail("No such pickup chest");
        worker.pickupFilters.remove(pickup.asLong());
        sync(worker);
        return ActionResult.ok("Chest " + (worker.pickups.indexOf(pickup) + 1) + " takes anything");
    }

    /** Cycle how many of each item the courier leaves in a pickup chest: 0 → 8 → 16 → 32 → 64 → 0. */
    static ActionResult cyclePickupKeep(ServerPlayer player, Worker worker, String indexArg) {
        ActionResult check = requireMenu(player, worker);
        if (check != null) return check;
        BlockPos pickup = pickupAt(worker, indexArg);
        if (pickup == null) return ActionResult.fail("No such pickup chest");
        int current = worker.pickupKeep.getOrDefault(pickup.asLong(), 0);
        int next = KEEP_STEPS[0];
        for (int i = 0; i < KEEP_STEPS.length; i++) {
            if (KEEP_STEPS[i] == current) { next = KEEP_STEPS[(i + 1) % KEEP_STEPS.length]; break; }
        }
        if (next == 0) worker.pickupKeep.remove(pickup.asLong());
        else worker.pickupKeep.put(pickup.asLong(), next);
        sync(worker);
        return ActionResult.ok(next == 0 ? "Chest " + (worker.pickups.indexOf(pickup) + 1) + ": take everything"
            : "Chest " + (worker.pickups.indexOf(pickup) + 1) + ": leave " + next + " of each item");
    }

    static ActionResult removePickup(ServerPlayer player, Worker worker, String indexArg) {
        ActionResult check = requireMenu(player, worker);
        if (check != null) return check;
        BlockPos pickup = pickupAt(worker, indexArg);
        if (pickup == null) return ActionResult.fail("No such pickup chest");
        worker.pickups.remove(pickup);
        worker.pickupFilters.remove(pickup.asLong());
        worker.pickupKeep.remove(pickup.asLong());
        worker.courierCursor = 0;
        sync(worker);
        return ActionResult.ok("Removed pickup chest at " + pickup.toShortString());
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
            case "honey" -> worker.ranchHoney = !worker.ranchHoney;
            default -> { return ActionResult.fail("Unknown ranch option"); }
        }
        sync(worker);
        boolean on = switch (flag) {
            case "breed" -> worker.ranchBreed;
            case "shear" -> worker.ranchShear;
            case "milk" -> worker.ranchMilk;
            case "eggs" -> worker.ranchEggs;
            case "honey" -> worker.ranchHoney;
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
        worker.setStatusKey(Lang.STATUS_THINNING_HERD);
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
        if (worker.siteId != null || worker.constructionId != null) return ActionResult.fail("Finish or dismantle the worker site first");
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

    static final List<String> ROLES = List.of("farmer", "forester", "miner", "builder", "knight", "archer", "toolsmith", "stonemason", "cook",
        "rancher", "fisher", "smelter", "courier", "gatherer");

    static boolean isRole(String role) {
        return role != null && ROLES.contains(role);
    }

    /** Ids the Resources page can toggle for a role (farmer crops, gatherer plants); empty for other roles. */
    static List<String> resourcesFor(String role) {
        return switch (role == null ? "" : role) {
            case "farmer" -> Jobs.FARMER_CROPS;
            case "gatherer" -> Jobs.GATHER_RESOURCES;
            default -> List.of();
        };
    }

    static boolean isCombatRole(String role) {
        return "knight".equals(role) || "archer".equals(role);
    }

    static boolean isGatherRole(String role) {
        return "forester".equals(role) || "farmer".equals(role) || "miner".equals(role)
            || "rancher".equals(role) || "fisher".equals(role) || "smelter".equals(role)
            || "courier".equals(role) || "gatherer".equals(role);
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
            case "gatherer" -> HelpfulWorkers.GATHERER_KIT.get();
            case "toolsmith" -> HelpfulWorkers.TOOLSMITH_KIT.get();
            case "stonemason" -> HelpfulWorkers.STONEMASON_KIT.get();
            case "cook" -> HelpfulWorkers.COOK_KIT.get();
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
        if (item == HelpfulWorkers.GATHERER_KIT.get()) return "gatherer";
        if (item == HelpfulWorkers.TOOLSMITH_KIT.get()) return "toolsmith";
        if (item == HelpfulWorkers.STONEMASON_KIT.get()) return "stonemason";
        if (item == HelpfulWorkers.COOK_KIT.get()) return "cook";
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
            case "gatherer" -> "Gatherer";
            case "toolsmith" -> "Toolsmith";
            case "stonemason" -> "Stonemason";
            case "cook" -> "Cook";
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
