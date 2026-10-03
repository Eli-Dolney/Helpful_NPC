package com.example.helpfulworkers;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

final class WorkerCommands {
    private WorkerCommands() {}

    static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("worker").requires(source -> source.getEntity() instanceof ServerPlayer);
        root.then(Commands.literal("site").executes(ctx -> withWorker(ctx.getSource().getPlayer(), w -> { SiteUi.openWorker(ctx.getSource().getPlayer(), w); return 1; })));
        root.then(Commands.literal("warehouse").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            var hit = p.pick(8, 0, false);
            if (hit instanceof BlockHitResult block) SiteUi.openCore(p, block.getBlockPos());
            return 1;
        }));
        root.then(Commands.literal("status").executes(ctx -> withWorker(ctx.getSource().getPlayer(),
            w -> msg(ctx.getSource().getPlayer(), WorkerActions.status(ctx.getSource().getPlayer(), w)))));
        root.then(Commands.literal("start").executes(ctx -> withWorker(ctx.getSource().getPlayer(),
            w -> msg(ctx.getSource().getPlayer(), WorkerActions.start(ctx.getSource().getPlayer(), w)))));
        root.then(Commands.literal("pause").executes(ctx -> withWorker(ctx.getSource().getPlayer(),
            w -> msg(ctx.getSource().getPlayer(), WorkerActions.pause(ctx.getSource().getPlayer(), w)))));
        root.then(Commands.literal("home").executes(ctx -> withWorker(ctx.getSource().getPlayer(),
            w -> msg(ctx.getSource().getPlayer(), WorkerActions.home(ctx.getSource().getPlayer(), w)))));
        root.then(Commands.literal("role").then(Commands.argument("role", StringArgumentType.word()).suggests((ctx,builder) -> {
            for (String role : new String[]{"farmer","forester","miner","builder","knight","archer","rancher","fisher","smelter","courier"}) builder.suggest(role);
            return builder.buildFuture();
        }).executes(ctx -> withWorker(ctx.getSource().getPlayer(), w ->
            msg(ctx.getSource().getPlayer(), WorkerActions.setRole(ctx.getSource().getPlayer(), w,
                StringArgumentType.getString(ctx, "role"), true))))));
        root.then(Commands.literal("mode").then(Commands.argument("mode", StringArgumentType.word()).suggests((ctx,builder) -> {
            builder.suggest("excavate").suggest("branches").suggest("terraform")
                .suggest("staircase").suggest("strip").suggest("colony"); return builder.buildFuture();
        }).executes(ctx -> withWorker(ctx.getSource().getPlayer(), w ->
            msg(ctx.getSource().getPlayer(), WorkerActions.setMode(ctx.getSource().getPlayer(), w,
                StringArgumentType.getString(ctx, "mode")))))));
        root.then(Commands.literal("depth").then(Commands.argument("depth",
            com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1, 256)).executes(ctx ->
            withWorker(ctx.getSource().getPlayer(), w -> msg(ctx.getSource().getPlayer(),
                WorkerActions.setDigDepth(ctx.getSource().getPlayer(), w,
                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "depth")))))));
        root.then(Commands.literal("area").then(Commands.argument("first", BlockPosArgument.blockPos())
            .then(Commands.argument("second", BlockPosArgument.blockPos()).executes(ctx -> withWorker(ctx.getSource().getPlayer(), w -> {
                try {
                    BlockPos first = BlockPosArgument.getLoadedBlockPos(ctx, "first");
                    BlockPos second = BlockPosArgument.getLoadedBlockPos(ctx, "second");
                    return msg(ctx.getSource().getPlayer(), WorkerActions.setArea(ctx.getSource().getPlayer(), w, first, second));
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
                    return msg(ctx.getSource().getPlayer(), ActionResult.fail("Area corners must be loaded"));
                }
            })))));
        root.then(Commands.literal("bind").then(Commands.argument("kind", StringArgumentType.word()).suggests((ctx,builder) -> {
            for (String kind : new String[]{"bed","supply","output","table"}) builder.suggest(kind);
            return builder.buildFuture();
        }).executes(ctx -> withWorker(ctx.getSource().getPlayer(), w -> {
            HitResult hit = ctx.getSource().getPlayer().pick(8, 0, false);
            if (!(hit instanceof BlockHitResult block)) {
                return msg(ctx.getSource().getPlayer(), ActionResult.fail("Look at a nearby block first"));
            }
            BlockPos pos = block.getBlockPos();
            return msg(ctx.getSource().getPlayer(), switch (StringArgumentType.getString(ctx, "kind")) {
                case "bed" -> WorkerActions.setBed(ctx.getSource().getPlayer(), w, pos);
                case "supply" -> WorkerActions.setSupply(ctx.getSource().getPlayer(), w, pos);
                case "output" -> WorkerActions.setOutput(ctx.getSource().getPlayer(), w, pos);
                case "table" -> WorkerActions.setTable(ctx.getSource().getPlayer(), w, pos);
                default -> ActionResult.fail("Unknown bind kind");
            });
        }))));
        root.then(Commands.literal("clear").then(Commands.argument("kind", StringArgumentType.word()).suggests((ctx,builder) -> {
            for (String kind : new String[]{"area","bed","supply","output","table","origin"}) builder.suggest(kind);
            return builder.buildFuture();
        }).executes(ctx -> withWorker(ctx.getSource().getPlayer(), w ->
            msg(ctx.getSource().getPlayer(), switch (StringArgumentType.getString(ctx, "kind")) {
                case "area" -> WorkerActions.clearArea(ctx.getSource().getPlayer(), w);
                case "bed" -> WorkerActions.clearBed(ctx.getSource().getPlayer(), w);
                case "supply" -> WorkerActions.clearSupply(ctx.getSource().getPlayer(), w);
                case "output" -> WorkerActions.clearOutput(ctx.getSource().getPlayer(), w);
                case "table" -> WorkerActions.clearTable(ctx.getSource().getPlayer(), w);
                case "origin" -> WorkerActions.clearOrigin(ctx.getSource().getPlayer(), w);
                default -> ActionResult.fail("Unknown clear kind");
            })))));
        root.then(Commands.literal("recipe")
            .then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.greedyString()).executes(ctx ->
                withWorker(ctx.getSource().getPlayer(), w -> msg(ctx.getSource().getPlayer(),
                    WorkerActions.removeRecipe(ctx.getSource().getPlayer(), w, StringArgumentType.getString(ctx, "id")))))))
            .then(Commands.argument("id", StringArgumentType.greedyString()).executes(ctx ->
                withWorker(ctx.getSource().getPlayer(), w -> msg(ctx.getSource().getPlayer(),
                    WorkerActions.approveRecipe(ctx.getSource().getPlayer(), w, StringArgumentType.getString(ctx, "id")))))));
        root.then(Commands.literal("blueprint")
            .then(Commands.literal("capture").then(Commands.argument("name", StringArgumentType.word()).executes(ctx ->
                withWorker(ctx.getSource().getPlayer(), w -> msg(ctx.getSource().getPlayer(),
                    WorkerActions.captureBlueprint(ctx.getSource().getPlayer(), w,
                        StringArgumentType.getString(ctx, "name"), true))))))
            .then(Commands.literal("materials").executes(ctx -> withWorker(ctx.getSource().getPlayer(), w ->
                msg(ctx.getSource().getPlayer(), WorkerActions.materials(ctx.getSource().getPlayer(), w)))))
            .then(Commands.literal("list").executes(ctx -> withWorker(ctx.getSource().getPlayer(), w -> {
                StringBuilder names = new StringBuilder();
                for (String name : WorkerActions.blueprintNames(w)) names.append(name).append(' ');
                return msg(ctx.getSource().getPlayer(), ActionResult.ok("Blueprints: " + names));
            })))
            .then(Commands.literal("load").then(Commands.argument("name", StringArgumentType.word()).executes(ctx ->
                withWorker(ctx.getSource().getPlayer(), w -> msg(ctx.getSource().getPlayer(),
                    WorkerActions.loadBlueprint(ctx.getSource().getPlayer(), w, StringArgumentType.getString(ctx, "name")))))))
            .then(Commands.literal("preview").executes(ctx -> withWorker(ctx.getSource().getPlayer(), w ->
                msg(ctx.getSource().getPlayer(), WorkerActions.preview(ctx.getSource().getPlayer(), w)))))
            .then(Commands.literal("place").then(Commands.argument("rotation",
                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3)).executes(ctx ->
                withWorker(ctx.getSource().getPlayer(), w -> {
                    HitResult hit = ctx.getSource().getPlayer().pick(8, 0, false);
                    if (!(hit instanceof BlockHitResult block)) {
                        return msg(ctx.getSource().getPlayer(), ActionResult.fail("Look at a placement block"));
                    }
                    ActionResult rot = WorkerActions.setRotation(ctx.getSource().getPlayer(), w,
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "rotation"));
                    if (!rot.success()) return msg(ctx.getSource().getPlayer(), rot);
                    return msg(ctx.getSource().getPlayer(), WorkerActions.setBuildOrigin(ctx.getSource().getPlayer(), w,
                        block.getBlockPos().relative(block.getDirection())));
                })))));
        event.getDispatcher().register(root);

        var hw = Commands.literal("helpfulworkers").requires(source -> source.hasPermission(2));
        hw.then(Commands.literal("perf").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal(WorkerPerf.report()), false);
            return 1;
        }));
        hw.then(Commands.literal("perf").then(Commands.literal("reset").executes(ctx -> {
            WorkerPerf.reset();
            ctx.getSource().sendSuccess(() -> Component.literal("Worker perf counters reset"), false);
            return 1;
        })));
        event.getDispatcher().register(hw);
    }

    private interface Action { int run(Worker worker); }

    private static int withWorker(ServerPlayer player, Action action) {
        Worker found = WorkerActions.nearestOwned(player, 12);
        if (found == null) return msg(player, ActionResult.fail("No owned worker within 12 blocks"));
        return action.run(found);
    }

    private static int msg(ServerPlayer player, ActionResult result) {
        player.sendSystemMessage(result.message());
        return result.success() ? 1 : 0;
    }
}
