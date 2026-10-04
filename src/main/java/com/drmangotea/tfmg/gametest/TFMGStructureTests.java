package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * One test per multiblock of the handbook (the generated blueprints): build
 * the whole structure, let it run, check that every block entity survives a
 * save and reload, then take it apart block by block and check that nothing
 * dropped more than was placed. Assembly, ticking and disassembly of every
 * structure players are told to build, on every run.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGStructureTests {

    private static final BlockPos ORIGIN = new BlockPos(5, 1, 5);
    private static final String TEMPLATE = TFMG.MOD_ID + ":gametest/platform_huge";

    @GameTestGenerator
    public static List<TestFunction> structures() {
        List<TestFunction> tests = new ArrayList<>();
        for (Map.Entry<String, JsonObject> blueprint : readBlueprints().entrySet()) {
            String id = blueprint.getKey();
            JsonObject json = blueprint.getValue();
            tests.add(TFMGGameTestUtil.test("tfmg_structures", "structure." + id, TEMPLATE, 600,
                    helper -> buildRunAndDismantle(helper, id, json)));
        }
        return tests;
    }

    /** Reads every generated blueprint straight from the mod file. */
    static Map<String, JsonObject> readBlueprints() {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        Path dir = ModList.get().getModFileById(TFMG.MOD_ID).getFile().findResource("assets", TFMG.MOD_ID, "blueprints");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted(Comparator.comparing(Path::toString)).toList()) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String name = file.getFileName().toString();
                    out.put(name.substring(0, name.length() - 5), JsonParser.parseReader(reader).getAsJsonObject());
                }
            }
        } catch (Exception e) {
            TFMG.LOGGER.error("[gametest] cannot read blueprints from {}", dir, e);
        }
        return out;
    }

    /** Blueprint layers, bottom first, as positions relative to the structure's corner. */
    static Map<BlockPos, BlockState> blocksOf(JsonObject json) {
        Map<Character, BlockState> key = new HashMap<>();
        JsonObject keyJson = json.getAsJsonObject("key");
        for (String symbol : keyJson.keySet()) {
            try {
                key.put(symbol.charAt(0), BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),
                        keyJson.get(symbol).getAsString(), false).blockState());
            } catch (Exception e) {
                throw new IllegalStateException("bad block state " + keyJson.get(symbol), e);
            }
        }
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        JsonArray layers = json.getAsJsonArray("layers");
        for (int y = 0; y < layers.size(); y++) {
            JsonArray rows = layers.get(y).getAsJsonArray();
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z).getAsString();
                for (int x = 0; x < row.length(); x++) {
                    BlockState state = key.get(row.charAt(x));
                    if (state != null && !state.isAir())
                        blocks.put(new BlockPos(x, y, z), state);
                }
            }
        }
        return blocks;
    }

    private static void buildRunAndDismantle(GameTestHelper helper, String id, JsonObject json) {
        Map<BlockPos, BlockState> blocks = blocksOf(json);
        Map<Item, Integer> placed = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            helper.setBlock(ORIGIN.offset(e.getKey()), e.getValue());
            Item item = e.getValue().getBlock().asItem();
            if (item != Items.AIR)
                placed.merge(item, 1, Integer::sum);
        }
        helper.runAfterDelay(80, () -> {
            ServerLevel level = helper.getLevel();
            HolderLookup.Provider registries = level.registryAccess();
            Set<String> problems = new TreeSet<>();
            for (BlockPos rel : blocks.keySet()) {
                BlockPos abs = helper.absolutePos(ORIGIN.offset(rel));
                BlockEntity be = level.getBlockEntity(abs);
                if (be == null)
                    continue;
                CompoundTag first = be.saveWithFullMetadata(registries);
                BlockEntity copy = BlockEntity.loadStatic(abs, be.getBlockState(), first, registries);
                if (copy == null) {
                    problems.add(rel + " failed to reload");
                    continue;
                }
                copy.setLevel(level);
                Set<String> diff = TFMGGameTestUtil.diffKeys(first, copy.saveWithFullMetadata(registries));
                if (!diff.isEmpty())
                    problems.add(rel + " " + BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath() + " " + diff);
            }
            TFMGGameTestUtil.check(helper, problems.isEmpty(), id + ": formed structure lost data on reload: " + problems);

            // What breaking each block should give back, as its own loot says
            // (reinforced bricks give fireproof bricks, wall plates give
            // reinforcements...). Anything above that is a duplication.
            Map<Item, Integer> expected = new HashMap<>();
            for (BlockPos rel : blocks.keySet()) {
                BlockPos abs = helper.absolutePos(ORIGIN.offset(rel));
                BlockState state = level.getBlockState(abs);
                for (net.minecraft.world.item.ItemStack drop : net.minecraft.world.level.block.Block.getDrops(state, level, abs, level.getBlockEntity(abs)))
                    expected.merge(drop.getItem(), drop.getCount(), Integer::sum);
            }

            // Take it apart from the top down, with drops.
            List<BlockPos> order = new ArrayList<>(blocks.keySet());
            order.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
            for (BlockPos rel : order)
                level.destroyBlock(helper.absolutePos(ORIGIN.offset(rel)), true);

            helper.runAfterDelay(10, () -> {
                Map<Item, Integer> dropped = new HashMap<>();
                for (ItemEntity entity : helper.getEntities(EntityType.ITEM))
                    dropped.merge(entity.getItem().getItem(), entity.getItem().getCount(), Integer::sum);
                List<String> dupes = new ArrayList<>();
                for (Map.Entry<Item, Integer> d : dropped.entrySet()) {
                    int count = Math.max(expected.getOrDefault(d.getKey(), 0), placed.getOrDefault(d.getKey(), 0));
                    if (d.getValue() > count)
                        dupes.add(BuiltInRegistries.ITEM.getKey(d.getKey()).getPath() + " " + d.getValue() + " dropped for " + count + " expected");
                }
                TFMGGameTestUtil.check(helper, dupes.isEmpty(), id + ": dismantling duplicated items: " + dupes);
                for (BlockPos rel : blocks.keySet())
                    TFMGGameTestUtil.check(helper, helper.getLevel().getBlockState(helper.absolutePos(ORIGIN.offset(rel))).isAir()
                            || !blocks.get(rel).is(helper.getLevel().getBlockState(helper.absolutePos(ORIGIN.offset(rel))).getBlock()),
                            id + ": a block survived dismantling at " + rel);
                helper.killAllEntitiesOfClass(ItemEntity.class);
                helper.succeed();
            });
        });
    }
}
