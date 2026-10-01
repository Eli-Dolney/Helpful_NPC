package com.example.helpfulworkers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Curated vanilla village structure templates loaded at runtime for free builder presets. */
final class VillageCatalog {
    private VillageCatalog() {}

    record Entry(String id, String biome, String label, String templatePath) {}

    private static final List<String> BIOMES = List.of("plains", "desert", "savanna", "taiga", "snowy");
    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();
    private static final Map<String, List<Entry>> BY_BIOME = new LinkedHashMap<>();

    static {
        for (String biome : BIOMES) BY_BIOME.put(biome, new ArrayList<>());
        // plains
        add("plains", "small_house", "Plains small house", "village/plains/houses/plains_small_house_1");
        add("plains", "medium_house", "Plains medium house", "village/plains/houses/plains_medium_house_1");
        add("plains", "weaponsmith", "Plains weaponsmith", "village/plains/houses/plains_weaponsmith_1");
        add("plains", "armorer", "Plains armorer", "village/plains/houses/plains_armorer_house_1");
        add("plains", "toolsmith", "Plains toolsmith", "village/plains/houses/plains_tool_smith_1");
        add("plains", "library", "Plains library", "village/plains/houses/plains_library_1");
        add("plains", "farm", "Plains farm", "village/plains/houses/plains_small_farm_1");
        add("plains", "animal_pen", "Plains animal pen", "village/plains/houses/plains_animal_pen_1");
        add("plains", "meeting_point", "Plains meeting point", "village/plains/town_centers/plains_meeting_point_1");
        add("plains", "well", "Plains fountain well", "village/plains/town_centers/plains_fountain_01");
        // desert
        add("desert", "small_house", "Desert small house", "village/desert/houses/desert_small_house_1");
        add("desert", "medium_house", "Desert medium house", "village/desert/houses/desert_medium_house_1");
        add("desert", "weaponsmith", "Desert weaponsmith", "village/desert/houses/desert_weaponsmith_1");
        add("desert", "armorer", "Desert armorer", "village/desert/houses/desert_armorer_1");
        add("desert", "toolsmith", "Desert toolsmith", "village/desert/houses/desert_tool_smith_1");
        add("desert", "library", "Desert library", "village/desert/houses/desert_library_1");
        add("desert", "farm", "Desert farm", "village/desert/houses/desert_farm_1");
        add("desert", "animal_pen", "Desert animal pen", "village/desert/houses/desert_animal_pen_1");
        add("desert", "meeting_point", "Desert meeting point", "village/desert/town_centers/desert_meeting_point_1");
        add("desert", "well", "Desert well bottom", "village/common/well_bottom");
        // savanna
        add("savanna", "small_house", "Savanna small house", "village/savanna/houses/savanna_small_house_1");
        add("savanna", "medium_house", "Savanna medium house", "village/savanna/houses/savanna_medium_house_1");
        add("savanna", "weaponsmith", "Savanna weaponsmith", "village/savanna/houses/savanna_weaponsmith_1");
        add("savanna", "armorer", "Savanna armorer", "village/savanna/houses/savanna_armorer_1");
        add("savanna", "toolsmith", "Savanna toolsmith", "village/savanna/houses/savanna_tool_smith_1");
        add("savanna", "library", "Savanna library", "village/savanna/houses/savanna_library_1");
        add("savanna", "farm", "Savanna farm", "village/savanna/houses/savanna_small_farm");
        add("savanna", "animal_pen", "Savanna animal pen", "village/savanna/houses/savanna_animal_pen_1");
        add("savanna", "meeting_point", "Savanna meeting point", "village/savanna/town_centers/savanna_meeting_point_1");
        add("savanna", "well", "Savanna well bottom", "village/common/well_bottom");
        // taiga
        add("taiga", "small_house", "Taiga small house", "village/taiga/houses/taiga_small_house_1");
        add("taiga", "medium_house", "Taiga medium house", "village/taiga/houses/taiga_medium_house_1");
        add("taiga", "weaponsmith", "Taiga weaponsmith", "village/taiga/houses/taiga_weaponsmith_1");
        add("taiga", "armorer", "Taiga armorer", "village/taiga/houses/taiga_armorer_house_1");
        add("taiga", "toolsmith", "Taiga toolsmith", "village/taiga/houses/taiga_tool_smith_1");
        add("taiga", "library", "Taiga library", "village/taiga/houses/taiga_library_1");
        add("taiga", "farm", "Taiga farm", "village/taiga/houses/taiga_small_farm_1");
        add("taiga", "animal_pen", "Taiga animal pen", "village/taiga/houses/taiga_animal_pen_1");
        add("taiga", "meeting_point", "Taiga meeting point", "village/taiga/town_centers/taiga_meeting_point_1");
        add("taiga", "well", "Taiga well bottom", "village/common/well_bottom");
        // snowy
        add("snowy", "small_house", "Snowy small house", "village/snowy/houses/snowy_small_house_1");
        add("snowy", "medium_house", "Snowy medium house", "village/snowy/houses/snowy_medium_house_1");
        add("snowy", "weaponsmith", "Snowy weaponsmith", "village/snowy/houses/snowy_weapon_smith_1");
        add("snowy", "armorer", "Snowy armorer", "village/snowy/houses/snowy_armorer_house_1");
        add("snowy", "toolsmith", "Snowy toolsmith", "village/snowy/houses/snowy_tool_smith_1");
        add("snowy", "library", "Snowy library", "village/snowy/houses/snowy_library_1");
        add("snowy", "farm", "Snowy farm", "village/snowy/houses/snowy_farm_1");
        add("snowy", "animal_pen", "Snowy animal pen", "village/snowy/houses/snowy_animal_pen_1");
        add("snowy", "meeting_point", "Snowy meeting point", "village/snowy/town_centers/snowy_meeting_point_1");
        add("snowy", "well", "Snowy well bottom", "village/common/well_bottom");
    }

    private static void add(String biome, String building, String label, String templatePath) {
        String id = biome + "_" + building;
        Entry entry = new Entry(id, biome, label, templatePath);
        BY_ID.put(id, entry);
        BY_BIOME.get(biome).add(entry);
    }

    static List<String> biomes() {
        return BIOMES;
    }

    static List<Entry> forBiome(String biome) {
        List<Entry> list = BY_BIOME.get(biome == null ? "" : biome.toLowerCase(Locale.ROOT));
        return list == null ? List.of() : List.copyOf(list);
    }

    static Entry byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    static List<Entry> all() {
        return List.copyOf(BY_ID.values());
    }

    static String loadIntoWorker(ServerLevel level, Worker worker, String entryId) {
        Entry entry = byId(entryId);
        if (entry == null) return "Unknown village preset " + entryId;
        Optional<StructureTemplate> optional = level.getStructureManager()
            .get(ResourceLocation.withDefaultNamespace(entry.templatePath()));
        if (optional.isEmpty()) return "Missing structure template " + entry.templatePath();
        StructureTemplate template = optional.get();
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE);
        List<StructureTemplate.StructureBlockInfo> infos = extractBlocks(template, settings);
        if (infos.isEmpty()) return "Template empty: " + entry.templatePath();

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        List<StructureTemplate.StructureBlockInfo> kept = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : infos) {
            BlockState state = info.state();
            CompoundTag nbt = info.nbt();
            if (state.is(Blocks.STRUCTURE_VOID)) continue;
            if (state.is(Blocks.JIGSAW)) {
                BlockState resolved = resolveJigsaw(nbt);
                if (resolved == null || resolved.isAir()) continue;
                state = resolved;
                nbt = null;
            }
            BlockPos pos = info.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            kept.add(new StructureTemplate.StructureBlockInfo(pos, state, nbt));
        }
        final int floorY = minY;
        // Air on the foundation layer would dig holes in the yard; above it, air clears grass and trees from the rooms.
        kept.removeIf(info -> info.state().isAir() && info.pos().getY() <= floorY);
        if (kept.stream().noneMatch(info -> !info.state().isAir())) return "No placeable blocks in " + entry.templatePath();
        kept.sort(java.util.Comparator
            .comparing((StructureTemplate.StructureBlockInfo info) -> !info.state().isAir())
            .thenComparingInt(info -> info.pos().getY()));

        ListTag list = new ListTag();
        for (StructureTemplate.StructureBlockInfo info : kept) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("X", info.pos().getX() - minX);
            tag.putInt("Y", info.pos().getY() - minY);
            tag.putInt("Z", info.pos().getZ() - minZ);
            tag.put("State", NbtUtils.writeBlockState(info.state()));
            if (info.nbt() != null) tag.put("BlockEntity", info.nbt().copy());
            list.add(tag);
        }

        worker.blueprint = entry.id();
        worker.blueprintBlocks = list;
        worker.buildOrigin = null;
        worker.scanCursor = 0;
        return "Loaded " + entry.label() + " (" + list.size() + " blocks)";
    }

    private static BlockState resolveJigsaw(CompoundTag nbt) {
        if (nbt == null || !nbt.contains("final_state")) return null;
        String finalState = nbt.getString("final_state");
        if (finalState == null || finalState.isBlank() || "minecraft:air".equals(finalState)) return null;
        try {
            ResourceLocation id = ResourceLocation.parse(finalState.contains("[")
                ? finalState.substring(0, finalState.indexOf('['))
                : finalState);
            return BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.AIR).defaultBlockState();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Palette iteration via StructurePlaceSettings (field access; palettes is private in 1.21.1). */
    @SuppressWarnings("unchecked")
    private static List<StructureTemplate.StructureBlockInfo> extractBlocks(
        StructureTemplate template, StructurePlaceSettings settings
    ) {
        try {
            Field field = StructureTemplate.class.getDeclaredField("palettes");
            field.setAccessible(true);
            List<StructureTemplate.Palette> palettes = (List<StructureTemplate.Palette>) field.get(template);
            if (palettes == null || palettes.isEmpty()) return List.of();
            return List.copyOf(settings.getRandomPalette(palettes, BlockPos.ZERO).blocks());
        } catch (ReflectiveOperationException ex) {
            // Fallback: filterBlocks only returns one block type; gather via save/load NBT.
            return extractViaSave(template);
        }
    }

    private static List<StructureTemplate.StructureBlockInfo> extractViaSave(StructureTemplate template) {
        CompoundTag saved = template.save(new CompoundTag());
        ListTag paletteTag = saved.contains("palettes", 9)
            ? saved.getList("palettes", 9).getList(0)
            : saved.getList("palette", 10);
        ListTag blocksTag = saved.getList("blocks", 10);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i)));
        }
        List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>(blocksTag.size());
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag entry = blocksTag.getCompound(i);
            ListTag posTag = entry.getList("pos", 3);
            BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            int stateId = entry.getInt("state");
            if (stateId < 0 || stateId >= palette.size()) continue;
            CompoundTag nbt = entry.contains("nbt") ? entry.getCompound("nbt") : null;
            out.add(new StructureTemplate.StructureBlockInfo(pos, palette.get(stateId), nbt));
        }
        return out;
    }
}
