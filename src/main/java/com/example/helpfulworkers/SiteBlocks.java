package com.example.helpfulworkers;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.registries.*;

import java.util.*;

final class SiteBlocks {
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HelpfulWorkers.ID);
    static final DeferredBlock<Block> CORE =
            BLOCKS.register("site_controller", () -> new CoreBlock());
    static final Map<String, DeferredBlock<Block>> MANAGED = new LinkedHashMap<>();
    static final Map<String, DeferredItem<Item>> CORES = new LinkedHashMap<>();

    static {
        for (String ore : SiteCatalog.ORES) registerManaged("ore_" + ore);
        for (String tree : SiteCatalog.TREES)
            for (String part : new String[] {"log_", "leaves_", "marker_"})
                registerManaged(part + tree);
        registerManaged("roots_mangrove");
        for (String key :
                new String[] {
                    "site_frame",
                    "mining_core",
                    "coal",
                    "copper",
                    "iron",
                    "gold",
                    "redstone",
                    "lapis",
                    "emerald",
                    "diamond",
                    "logging",
                    "warehouse", "farm", "ranch", "fishing", "smelter", "builder", "guard"
                }) {
            String name =
                    key.endsWith("core") || key.equals("site_frame") ? key : key + "_site_core";
            CORES.put(
                    key,
                    HelpfulWorkers.ITEMS.register(
                            name,
                            () ->
                                    new Item(new Item.Properties()) {
                                        @Override
                                        public void appendHoverText(
                                                ItemStack s,
                                                TooltipContext c,
                                                List<Component> lines,
                                                TooltipFlag f) {
                                            lines.add(
                                                    Component.literal(
                                                            SiteCatalog.valid(key)
                                                                    ? "Ask a builder to build "
                                                                            + SiteCatalog.label(key)
                                                                            + "."
                                                                    : "Crafting component for"
                                                                            + " worker sites."));
                                        }
                                    }));
        }
    }

    private static void registerManaged(String name) {
        MANAGED.put(name, BLOCKS.register("managed_" + name, () -> new ManagedBlock(name)));
    }

    static Block managed(String key) {
        return MANAGED.get(key).get();
    }

    static Item coreItem(String type) {
        return CORES.get(type).get();
    }

    static boolean protectedBlock(BlockState s) {
        return s.getBlock() instanceof ManagedBlock || s.is(CORE.get());
    }

    static class ManagedBlock extends Block {
        final String kind;

        private static BlockBehaviour.Properties properties(String kind) {
            var p =
                    BlockBehaviour.Properties.of()
                            .strength(-1, 3600000)
                            .noLootTable()
                            .pushReaction(PushReaction.BLOCK);
            return kind.startsWith("marker_") ? p.noCollission().noOcclusion() : p;
        }

        ManagedBlock(String kind) {
            super(properties(kind));
            this.kind = kind;
        }
    }

    static class CoreBlock extends Block {
        CoreBlock() {
            super(
                    BlockBehaviour.Properties.of()
                            .strength(-1, 3600000)
                            .noLootTable()
                            .pushReaction(PushReaction.BLOCK)
                            .lightLevel(s -> 7));
        }

        @Override
        protected InteractionResult useWithoutItem(
                BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (player instanceof ServerPlayer p) SiteUi.openCore(p, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }
}
