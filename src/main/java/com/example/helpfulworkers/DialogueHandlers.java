package com.example.helpfulworkers;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.PacketDistributor;

final class DialogueHandlers {
    private DialogueHandlers() {}

    static void handle(ServerPlayer player, WorkerNetwork.DialogueActionPayload payload) {
        Worker worker = WorkerActions.findOwned(player, payload.workerId());
        if (worker == null) {
            WorkerNetwork.toast(player, "Worker not found");
            return;
        }
        String action = payload.action();
        String arg = payload.arg() == null ? "" : payload.arg();
        ActionResult result = switch (action) {
            case "sites" -> {
                ActionResult check = WorkerActions.requireMenu(player, worker);
                if (check != null) yield check;
                SiteUi.openWorker(player, worker);
                yield ActionResult.ok("Worker sites opened");
            }
            case "close" -> {
                WorkerSessions.closeViewer(player);
                yield ActionResult.ok("Goodbye");
            }
            case "start" -> WorkerActions.start(player, worker);
            case "pause" -> WorkerActions.pause(player, worker);
            case "redraw_area" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                WorkerActions.redrawArea(player, worker);
                yield startAssign(player, worker, "area");
            }
            case "home" -> WorkerActions.home(player, worker);
            case "set_role" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                yield WorkerActions.setRole(player, worker, arg, true);
            }
            case "confirm_role" -> WorkerActions.setRole(player, worker, arg, true);
            case "set_mode" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                yield WorkerActions.setMode(player, worker, arg);
            }
            case "set_depth" -> {
                int depth;
                try { depth = Integer.parseInt(arg); }
                catch (NumberFormatException ex) { yield ActionResult.fail("Invalid depth"); }
                yield WorkerActions.setDigDepth(player, worker, depth);
            }
            case "set_target_y" -> {
                int y;
                try { y = Integer.parseInt(arg); }
                catch (NumberFormatException ex) { yield ActionResult.fail("Invalid Y"); }
                yield WorkerActions.setTargetY(player, worker, y);
            }
            case "set_companion" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                yield WorkerActions.setCompanionMode(player, worker, arg);
            }
            case "toggle_crop" -> WorkerActions.toggleCrop(player, worker, arg);
            case "toggle_bonemeal" -> WorkerActions.toggleBoneMeal(player, worker);
            case "toggle_ranch_animal" -> WorkerActions.toggleRanchAnimal(player, worker, arg);
            case "toggle_ranch_flag" -> WorkerActions.toggleRanchFlag(player, worker, arg);
            case "start_cull" -> WorkerActions.startCull(player, worker, false);
            case "start_cull_sword" -> WorkerActions.startCull(player, worker, true);
            case "load_preset" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                if (!"builder".equals(worker.role)) yield ActionResult.fail("Only builders can load presets");
                yield WorkerActions.prepareVillageHouse(player, worker, arg);
            }
            case "build_on_plot" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                if (!"builder".equals(worker.role)) yield ActionResult.fail("Only builders build village houses");
                yield WorkerActions.startVillageOnPlot(player, worker);
            }
            case "show_outline" -> {
                WorkerNetwork.sendAreaOutline(player, worker, 200);
                yield ActionResult.ok(worker.first == null ? "No work area to show" : "Showing work area");
            }
            case "open_inventory" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                PacketDistributor.sendToPlayer(player, new WorkerNetwork.CloseDialoguePayload());
                WorkerSessions.closeViewer(player);
                player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new WorkerInventoryMenu(id, inv, worker),
                    Component.literal(worker.getName().getString() + " Equipment")),
                    buf -> buf.writeVarInt(worker.getId()));
                WorkerSessions.openViewer(player, worker, WorkerSessions.ViewerKind.INVENTORY);
                yield ActionResult.ok("Opened equipment");
            }
            case "approve_recipe" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                ActionResult r = WorkerActions.approveRecipe(player, worker, arg);
                WorkerNetwork.sendRecipeList(player, worker);
                yield r;
            }
            case "remove_recipe" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                ActionResult r = WorkerActions.removeRecipe(player, worker, arg);
                WorkerNetwork.sendRecipeList(player, worker);
                yield r;
            }
            case "request_recipes" -> {
                WorkerNetwork.sendRecipeList(player, worker);
                yield ActionResult.ok("Recipes updated");
            }
            case "capture_blueprint" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                if (!"builder".equals(worker.role)) yield ActionResult.fail("Only builders capture blueprints");
                ActionResult r = WorkerActions.captureBlueprint(player, worker, arg, false);
                WorkerNetwork.sendBlueprintList(player, worker);
                yield r;
            }
            case "overwrite_blueprint" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                if (!"builder".equals(worker.role)) yield ActionResult.fail("Only builders capture blueprints");
                ActionResult r = WorkerActions.captureBlueprint(player, worker, arg, true);
                WorkerNetwork.sendBlueprintList(player, worker);
                yield r;
            }
            case "load_blueprint" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                ActionResult r = WorkerActions.loadBlueprint(player, worker, arg);
                WorkerNetwork.sendBlueprintList(player, worker);
                yield r;
            }
            case "set_rotation" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                try {
                    yield WorkerActions.setRotation(player, worker, Integer.parseInt(arg));
                } catch (NumberFormatException ex) {
                    yield ActionResult.fail("Invalid rotation");
                }
            }
            case "preview" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                yield WorkerActions.preview(player, worker);
            }
            case "materials" -> {
                ActionResult menu = WorkerActions.requireMenu(player, worker);
                if (menu != null) yield menu;
                yield WorkerActions.materials(player, worker);
            }
            case "start_assign" -> startAssign(player, worker, arg);
            case "start_village_plot" -> startAssign(player, worker, "village_plot");
            case "clear_assign" -> clearAssign(player, worker, arg);
            default -> ActionResult.fail("Unknown action");
        };
        if (!"close".equals(action)) {
            WorkerNetwork.toast(player, result.text());
            PacketDistributor.sendToPlayer(player, WorkerNetwork.WorkerStatusPayload.from(worker));
        }
    }

    private static ActionResult startAssign(ServerPlayer player, Worker worker, String kind) {
        ActionResult menu = WorkerActions.requireMenu(player, worker);
        if (menu != null) return menu;
        if (!WorkerActions.hasItem(player, HelpfulWorkers.CLIPBOARD.get()) && !player.getAbilities().instabuild) {
            return ActionResult.fail("Craft an Assignment Clipboard: paper + stick");
        }
        WorkerSessions.ClipboardKind clipboardKind = switch (kind) {
            case "area", "village_plot", "copy_area" -> WorkerSessions.ClipboardKind.AREA;
            case "bed" -> WorkerSessions.ClipboardKind.BED;
            case "supply" -> WorkerSessions.ClipboardKind.SUPPLY;
            case "output" -> WorkerSessions.ClipboardKind.OUTPUT;
            case "table" -> WorkerSessions.ClipboardKind.TABLE;
            case "origin" -> WorkerSessions.ClipboardKind.BUILD_ORIGIN;
            case "furnace" -> WorkerSessions.ClipboardKind.FURNACE;
            case "pickup" -> WorkerSessions.ClipboardKind.PICKUP;
            default -> null;
        };
        if (clipboardKind == null) return ActionResult.fail("Unknown assignment");
        if (clipboardKind == WorkerSessions.ClipboardKind.AREA) {
            WorkerActions.redrawArea(player, worker);
        }
        if (clipboardKind == WorkerSessions.ClipboardKind.BUILD_ORIGIN && worker.blueprintBlocks.isEmpty()) {
            return ActionResult.fail("Load or capture a blueprint first");
        }
        WorkerSessions.ClipboardSession session = WorkerSessions.startClipboard(player, worker, clipboardKind);
        session.villageBuild = "village_plot".equals(kind) || ("area".equals(kind) && "builder".equals(worker.role));
        player.closeContainer();
        WorkerSessions.closeViewer(player);
        PacketDistributor.sendToPlayer(player, new WorkerNetwork.CloseDialoguePayload());
        boolean plannedMine = "miner".equals(worker.role) && WorkerActions.isPlannedMineMode(worker.mode);
        player.displayClientMessage(Component.literal(session.villageBuild
            ? "Mark a house plot for " + worker.getName().getString()
                + ": click two opposite corners on the ground, at least 10×10 apart. Height doesn't matter."
                + " The house starts as soon as the plot fits. Sneak-right-click him to cancel."
            : switch (kind) {
            case "copy_area" -> "Mark the structure to copy for " + worker.getName().getString()
                + ": click the bottom corner, then the opposite top corner. Sneak-right-click him to cancel.";
            case "area" -> plannedMine
                ? "Mark the mine for " + worker.getName().getString()
                    + ": click the ground where the stairs start, then a block in the direction to dig. Sneak-right-click him to cancel."
                : "Mark an area for " + worker.getName().getString()
                    + ". Left-click or right-click one corner, then the opposite corner. Sneak-right-click him to cancel.";
            default -> "Assigning " + kind + " for " + worker.getName().getString()
                + ". Right-click the block. Sneak-right-click him to cancel.";
        }), false);
        return ActionResult.ok("Use the Assignment Clipboard to choose a target");
    }

    private static ActionResult clearAssign(ServerPlayer player, Worker worker, String kind) {
        ActionResult menu = WorkerActions.requireMenu(player, worker);
        if (menu != null) return menu;
        return switch (kind) {
            case "area" -> WorkerActions.clearArea(player, worker);
            case "bed" -> WorkerActions.clearBed(player, worker);
            case "supply" -> WorkerActions.clearSupply(player, worker);
            case "output" -> WorkerActions.clearOutput(player, worker);
            case "table" -> WorkerActions.clearTable(player, worker);
            case "origin" -> WorkerActions.clearOrigin(player, worker);
            case "furnace" -> WorkerActions.clearFurnaces(player, worker);
            case "pickup" -> WorkerActions.clearPickups(player, worker);
            default -> ActionResult.fail("Unknown clear target");
        };
    }
}
