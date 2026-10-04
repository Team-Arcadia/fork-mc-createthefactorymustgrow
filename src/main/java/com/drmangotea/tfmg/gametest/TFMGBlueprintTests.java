package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.items.blueprint.BlueprintLines;
import com.drmangotea.tfmg.registry.TFMGItems;
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

import java.util.HashSet;
import java.util.Set;

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

    @GameTest(template = "gametest/platform", batch = "tfmg_data")
    public static void blueprintsAreRareVillageLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<String> lines = new HashSet<>();
        int village = roll(level, "chests/village/village_toolsmith", lines);
        // 5% of 4000 is 200; the bounds leave room for chance on both sides.
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
