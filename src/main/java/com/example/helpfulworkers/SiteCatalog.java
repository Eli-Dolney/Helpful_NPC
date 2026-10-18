package com.example.helpfulworkers;

import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

import java.util.*;

/** Original, deterministic structures; positions are local to the southwest footprint corner. */
final class SiteCatalog {
    static final String[] ORES = {
        "coal", "copper", "iron", "gold", "redstone", "lapis", "emerald", "diamond"
    };
    static final String[] TREES = {
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"
    };
    static final int[] SECONDS = {30, 30, 60, 120, 120, 120, 600, 600};
    static final int[][] PLOTS = {
        {6, 6}, {16, 6}, {26, 6}, {6, 16}, {26, 16}, {6, 26}, {16, 26}, {26, 26}
    };

    record Piece(int x, int y, int z, BlockState state) {}

    static final Map<String, List<Piece>> CACHE = new HashMap<>();

    static final String[] EXTRA = {"farm", "ranch", "fishing", "smelter", "builder", "guard"};
    static Set<String> roles(String t) {
        return switch(t) {
            case "warehouse" -> Set.of("courier"); case "logging" -> Set.of("forester");
            case "farm" -> Set.of("farmer"); case "ranch" -> Set.of("rancher");
            case "fishing" -> Set.of("fisher"); case "smelter" -> Set.of("smelter");
            case "builder" -> Set.of("builder"); case "guard" -> Set.of("knight","archer");
            default -> Set.of("miner");
        };
    }
    static boolean valid(String t) {
        return "logging".equals(t) || "warehouse".equals(t) || Arrays.asList(ORES).contains(t) || Arrays.asList(EXTRA).contains(t);
    }

    static int width(String t) {
        return "logging".equals(t) ? 33 : "warehouse".equals(t) ? 25 : Arrays.asList(EXTRA).contains(t) ? 15 : 7;
    }

    static int depth(String t) {
        return "logging".equals(t) ? 33 : "warehouse".equals(t) ? 21 : Arrays.asList(EXTRA).contains(t) ? 15 : 7;
    }

    static int height(String t) {
        return "logging".equals(t) ? 20 : "guard".equals(t) ? 12 : "warehouse".equals(t) || Arrays.asList(EXTRA).contains(t) ? 7 : 5;
    }

    static String label(String t) {
        if (Arrays.asList(EXTRA).contains(t)) return switch(t) {
            case "farm" -> "Crop Farm"; case "ranch" -> "Ranch"; case "fishing" -> "Fishing Pond";
            case "smelter" -> "Smelter Workshop"; case "builder" -> "Builder’s Yard"; default -> "Guard Tower";
        };
        return "logging".equals(t)
                ? "Logging Camp"
                : "warehouse".equals(t) ? "Warehouse" : pretty(t) + " Mining Pit";
    }

    static String pretty(String t) {
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).replace('_', ' ');
    }

    static BlockPos at(SiteData.Site s, int x, int y, int z) {
        int w = s.width(), d = s.depth();
        return s.origin.offset(
                switch (s.rotation) {
                    case 1 -> d - 1 - z;
                    case 2 -> w - 1 - x;
                    case 3 -> z;
                    default -> x;
                },
                y,
                switch (s.rotation) {
                    case 1 -> x;
                    case 2 -> d - 1 - z;
                    case 3 -> w - 1 - x;
                    default -> z;
                });
    }

    static Rotation rotation(int r) {
        return Rotation.values()[Math.floorMod(r, 4)];
    }

    static Item output(String type) {
        return switch (type) {
            case "coal" -> Items.COAL;
            case "copper" -> Items.RAW_COPPER;
            case "iron" -> Items.RAW_IRON;
            case "gold" -> Items.RAW_GOLD;
            case "redstone" -> Items.REDSTONE;
            case "lapis" -> Items.LAPIS_LAZULI;
            case "emerald" -> Items.EMERALD;
            default -> Items.DIAMOND;
        };
    }

    static int yield(String t) {
        return SiteSettings.yield(t);
    }

    static int oreIndex(String t) {
        return Math.max(0, Arrays.asList(ORES).indexOf(t));
    }

    static List<Piece> pieces(String type) {
        return CACHE.computeIfAbsent(type, SiteCatalog::make);
    }

    private static void put(Map<BlockPos, BlockState> m, int x, int y, int z, Block b) {
        m.put(new BlockPos(x, y, z), b.defaultBlockState());
    }

    private static List<Piece> make(String t) {
        Map<BlockPos, BlockState> m = new LinkedHashMap<>();
        int w = width(t), d = depth(t);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++)
                put(m, x, 0, z, ("logging".equals(t) || "ranch".equals(t) || "farm".equals(t)) ? Blocks.GRASS_BLOCK : Blocks.STONE_BRICKS);
        // Boundary posts with an entrance centered on the south edge.
        for (int x = 0; x < w; x++)
            for (int z : new int[] {0, d - 1})
                if (Math.abs(x - w / 2) > 1 || z == 0) put(m, x, 1, z, Blocks.OAK_FENCE);
        for (int z = 1; z < d - 1; z++)
            for (int x : new int[] {0, w - 1}) put(m, x, 1, z, Blocks.OAK_FENCE);
        if ("warehouse".equals(t)) {
            for (int y = 1; y <= 4; y++)
                for (int x = 0; x < w; x++)
                    for (int z = 0; z < d; z++)
                        if (x == 0 || x == w - 1 || z == 0 || z == d - 1) {
                            if (z == d - 1 && Math.abs(x - w / 2) <= 1 && y <= 3) continue;
                            put(m, x, y, z, y == 3 ? Blocks.GLASS : Blocks.OAK_PLANKS);
                        }
            for (int x = 0; x < w; x++)
                for (int z = 0; z < d; z++) put(m, x, 5, z, Blocks.OAK_PLANKS);
            for (int z : new int[] {3, 7, 11, 15})
                for (int x = 1; x <= 22; x += 3) {
                    m.put(
                            new BlockPos(x, 1, z),
                            Blocks.CHEST
                                    .defaultBlockState()
                                    .setValue(ChestBlock.FACING, Direction.SOUTH)
                                    .setValue(ChestBlock.TYPE, ChestType.RIGHT));
                    m.put(
                            new BlockPos(x + 1, 1, z),
                            Blocks.CHEST
                                    .defaultBlockState()
                                    .setValue(ChestBlock.FACING, Direction.SOUTH)
                                    .setValue(ChestBlock.TYPE, ChestType.LEFT));
                }
            put(m, w / 2 - 1, 1, d / 2, Blocks.CHEST); // incoming
        } else if ("logging".equals(t)) {
            for (int x = 1; x < w - 1; x++)
                for (int z = 1; z < d - 1; z++)
                    if (Math.abs(x - 16) <= 1 || Math.abs(z - 16) <= 1)
                        put(m, x, 0, z, Blocks.DIRT_PATH);
            for (int x = 13; x <= 19; x++)
                for (int z = 13; z <= 19; z++) put(m, x, 0, z, Blocks.OAK_PLANKS);
            for (int x : new int[] {13, 19})
                for (int z : new int[] {13, 19})
                    for (int y = 1; y <= 4; y++) put(m, x, y, z, Blocks.OAK_LOG);
            for (int x = 13; x <= 19; x++)
                for (int z = 13; z <= 19; z++) put(m, x, 5, z, Blocks.OAK_PLANKS);
            for (int i = 0; i < 8; i++) {
                int[] p = PLOTS[i];
                put(m, p[0], 0, p[1], Blocks.DIRT);
                m.put(
                        new BlockPos(p[0], 1, p[1]),
                        SiteBlocks.managed("marker_" + TREES[i]).defaultBlockState());
            }
        } else if (Arrays.asList(EXTRA).contains(t)) {
            if (t.equals("farm")) {
                for(int x=2;x<=12;x++) for(int z=2;z<=5;z++) {
                    m.put(new BlockPos(x,0,z), x==7 ? Blocks.WATER.defaultBlockState() : Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
                    put(m,x,-1,z,Blocks.DIRT);
                }
            } else if (t.equals("fishing")) {
                for(int x=2;x<=12;x++) for(int z=2;z<=5;z++) {
                    put(m,x,-1,z,Blocks.STONE_BRICKS); put(m,x,0,z,Blocks.WATER);
                }
            } else if(t.equals("smelter") || t.equals("builder")) {
                put(m,2,1,2,Blocks.CRAFTING_TABLE);
                if(t.equals("smelter")) for(int x=4;x<=10;x+=2) put(m,x,1,2,Blocks.FURNACE);
                for(int x=1;x<=13;x++) for(int z=1;z<=5;z++) put(m,x,4,z,Blocks.OAK_PLANKS);
                for(int x:new int[]{1,13}) for(int z:new int[]{1,5}) for(int y=1;y<4;y++) put(m,x,y,z,Blocks.OAK_LOG);
            } else if(t.equals("guard")) {
                for(int x=2;x<=6;x++) for(int z=2;z<=6;z++) put(m,x,5,z,Blocks.OAK_PLANKS);
                for(int y=1;y<=4;y++) for(int x:new int[]{2,6}) for(int z:new int[]{2,6}) put(m,x,y,z,Blocks.OAK_LOG);
                for(int n=0;n<5;n++) {
                    for(int y=0;y<=n;y++) put(m,8,y,9-n,Blocks.STONE_BRICKS);
                    m.put(new BlockPos(8,n+1,9-n),Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH));
                }
                for(int x=2;x<=8;x++) put(m,x,5,3,Blocks.OAK_PLANKS);
            }
        } else {
            for (int x = 2; x <= 4; x++) put(m, x, 0, 1, Blocks.POLISHED_ANDESITE);
            for (int x : new int[] {0, 6}) {
                for (int y = 2; y <= 3; y++) put(m, x, y, 0, Blocks.OAK_LOG);
                put(m, x, 4, 0, Blocks.TORCH);
            }
        }
        put(m, w / 2 - 1, 1, d / 2, Blocks.CHEST);
        put(m, w / 2, 1, d / 2, SiteBlocks.CORE.get());
        put(m, w / 2 + 1, 1, d / 2, Blocks.CHEST);
        // Light walkways without occupying chest access.
        for (int x : new int[] {1, w - 2})
            for (int z : new int[] {1, d - 2}) put(m, x, 1, z, Blocks.TORCH);
        List<Piece> out = new ArrayList<>();
        m.forEach((p, b) -> out.add(new Piece(p.getX(), p.getY(), p.getZ(), b)));
        out.sort(
                Comparator.comparingInt(Piece::y)
                        .thenComparingInt(Piece::z)
                        .thenComparingInt(Piece::x));
        return List.copyOf(out);
    }

    static Map<BlockPos, BlockState> tree(SiteData.Site s, int i) {
        Map<BlockPos, BlockState> m = new LinkedHashMap<>();
        int x = PLOTS[i][0], z = PLOTS[i][1];
        String type = TREES[i];
        boolean broad = i == 5;
        int h = i == 1 ? 7 : i == 3 ? 8 : i == 4 ? 4 : 5;
        for (int y = 1; y <= h; y++)
            for (int dx = 0; dx < (broad ? 2 : 1); dx++)
                for (int dz = 0; dz < (broad ? 2 : 1); dz++)
                    m.put(
                            s.at(x + dx, y, z + dz),
                            SiteBlocks.managed("log_" + type).defaultBlockState());
        if (i == 4)
            for (int dx = 1; dx <= 2; dx++)
                m.put(s.at(x + dx, h, z), SiteBlocks.managed("log_" + type).defaultBlockState());
        for (int y = h - 1; y <= h + 1; y++) {
            int radius = i == 1 ? (h + 2 - y) : i == 7 ? 3 : 2;
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++)
                    if (Math.abs(dx) + Math.abs(dz) <= radius * 2 - 1)
                        m.putIfAbsent(
                                s.at(x + dx, y, z + dz),
                                SiteBlocks.managed("leaves_" + type).defaultBlockState());
        }
        if (i == 6)
            for (int[] off : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                m.put(
                        s.at(x + off[0], 1, z + off[1]),
                        SiteBlocks.managed("roots_mangrove").defaultBlockState());
        return m;
    }
}
