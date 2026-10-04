package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.items.blueprint.BlueprintLines;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Factory Blueprints must stay rare and only come from villages: about 5% of
 * the village workshop chests, never other chests, and one master
 * cartographer trade.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGBlueprintTests {

    private static final int ROLLS = 4000;
    private static final int MIN_STRUCTURES_PER_LINE = 3;

    @GameTest(template = "gametest/platform", batch = "tfmg_data")
    public static void blueprintsAreRareVillageLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<String> lines = new HashSet<>();
        int village = roll(level, "chests/village/village_toolsmith", lines);
        // 9 in 180 (5%) of 4000 is 200; the bounds leave room for chance on
        // both sides. More lines must never mean more blueprints.
        TFMGGameTestUtil.check(helper, village >= 120 && village <= 300,
                "village toolsmith chests gave " + village + " blueprints in " + ROLLS + " rolls, expected about 200");
        TFMGGameTestUtil.check(helper, lines.containsAll(BlueprintLines.LINES), "not every line dropped: " + lines);
        int dungeon = roll(level, "chests/simple_dungeon", new HashSet<>());
        TFMGGameTestUtil.check(helper, dungeon == 0, "a dungeon chest gave " + dungeon + " blueprints");
        helper.succeed();
    }

    @GameTest(template = "gametest/platform", batch = "tfmg_data")
    public static void masterCartographerSellsBlueprints(GameTestHelper helper) {
        var listings = VillagerTrades.TRADES.get(VillagerProfession.CARTOGRAPHER).get(5);
        boolean found = false;
        RandomSource random = RandomSource.create(1);
        for (var listing : listings) {
            MerchantOffer offer;
            try {
                // Vanilla map trades need a real villager; ours does not.
                offer = listing.getOffer(null, random);
            } catch (RuntimeException e) {
                continue;
            }
            if (offer != null && offer.getResult().is(TFMGItems.FACTORY_BLUEPRINT.get())) {
                found = true;
                TFMGGameTestUtil.check(helper, BlueprintLines.lineOf(offer.getResult()) != null, "the traded blueprint is blank");
                TFMGGameTestUtil.check(helper, offer.getMaxUses() == 1, "the blueprint trade is not single-use");
            }
        }
        TFMGGameTestUtil.check(helper, found, "master cartographers do not sell blueprints");
        helper.succeed();
    }

    /**
     * Every line has enough structures to be worth collecting, and every
     * generated blueprint is well formed: a known line, names in both
     * languages, symbols that all have a valid block state.
     */
    @GameTest(template = "gametest/platform", batch = "tfmg_data")
    public static void everyLineHasValidStructures(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Map<String, Integer> perLine = new TreeMap<>();
        Map<String, JsonObject> blueprints = TFMGStructureTests.readBlueprints();
        if (blueprints.isEmpty())
            problems.add("no blueprint found in the mod file");
        for (Map.Entry<String, JsonObject> e : blueprints.entrySet()) {
            String id = e.getKey();
            JsonObject json = e.getValue();
            String line = json.has("line") ? json.get("line").getAsString() : null;
            if (line == null || !BlueprintLines.LINES.contains(line))
                problems.add(id + ": unknown line " + line);
            else
                perLine.merge(line, 1, Integer::sum);
            JsonObject name = json.getAsJsonObject("name");
            for (String lang : new String[]{"en_us", "fr_fr"})
                if (name == null || !name.has(lang) || name.get(lang).getAsString().isBlank())
                    problems.add(id + ": no " + lang + " name");
            JsonObject key = json.getAsJsonObject("key");
            for (String symbol : key.keySet()) {
                try {
                    BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), key.get(symbol).getAsString(), false);
                } catch (Exception ex) {
                    problems.add(id + ": symbol " + symbol + " has an invalid block state " + key.get(symbol).getAsString());
                }
            }
            // An all-air layer is fine (the large engine needs room for its
            // piston): the projector skips it and keeps the heights.
            JsonArray layers = json.getAsJsonArray("layers");
            int blocks = 0;
            for (int y = 0; y < layers.size(); y++)
                for (JsonElement row : layers.get(y).getAsJsonArray())
                    for (char c : row.getAsString().toCharArray()) {
                        if (c == ' ')
                            continue;
                        blocks++;
                        if (!key.has(String.valueOf(c)))
                            problems.add(id + ": layer " + y + " uses symbol '" + c + "' missing from the key");
                    }
            if (blocks == 0)
                problems.add(id + ": no block at all");
        }
        for (String line : BlueprintLines.LINES)
            if (perLine.getOrDefault(line, 0) < MIN_STRUCTURES_PER_LINE)
                problems.add("line " + line + " has only " + perLine.getOrDefault(line, 0) + " structures");
        TFMGGameTestUtil.check(helper, problems.isEmpty(), "blueprint problems: " + problems);
        helper.succeed();
    }

    private static int roll(ServerLevel level, String table, Set<String> lines) {
        LootTable loot = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace(table)));
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        int count = 0;
        for (int i = 0; i < ROLLS; i++)
            for (ItemStack stack : loot.getRandomItems(params))
                if (stack.is(TFMGItems.FACTORY_BLUEPRINT.get())) {
                    count++;
                    String line = BlueprintLines.lineOf(stack);
                    if (line != null)
                        lines.add(line);
                }
        return count;
    }
}
