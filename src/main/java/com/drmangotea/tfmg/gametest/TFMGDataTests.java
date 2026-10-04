package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Whole-registry data checks: loot tables, recipe ingredients and
 * translations. Each test lists every offender at once so one run shows the
 * full damage instead of the first entry.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGDataTests {

    private static final int MAX_LISTED = 40;

    @GameTest(template = "gametest/platform", batch = "tfmg_data")
    public static void everyBlockHasLoot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<String> missing = new ArrayList<>();
        for (Block block : TFMGGameTestUtil.tfmgBlocks()) {
            if (block instanceof LiquidBlock || block instanceof BaseFireBlock)
                continue; // fluids and fires never drop anything
            ResourceKey<LootTable> key = block.getLootTable();
            if (key == BuiltInLootTables.EMPTY)
                continue; // explicitly declared as dropping nothing
            if (server.reloadableRegistries().getLootTable(key) == LootTable.EMPTY)
                missing.add(BuiltInRegistries.BLOCK.getKey(block).getPath());
        }
        report(helper, missing, "blocks without a loot table");
    }

    @GameTest(template = "gametest/platform", batch = "tfmg_data")
    public static void everyRecipeIngredientResolves(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<String> broken = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            ResourceLocation id = holder.id();
            if (!id.getNamespace().equals(TFMG.MOD_ID))
                continue;
            for (Ingredient ingredient : holder.value().getIngredients()) {
                if (ingredient.isEmpty())
                    continue;
                if (!resolves(ingredient))
                    broken.add(id + " item ingredient " + describe(ingredient));
            }
            if (holder.value() instanceof ProcessingRecipe<?, ?> processing) {
                for (SizedFluidIngredient fluid : processing.getFluidIngredients())
                    if (fluid.getFluids().length == 0)
                        broken.add(id + " fluid ingredient matches no fluid");
            }
        }
        report(helper, broken, "recipe ingredients that match nothing");
    }

    @GameTest(template = "gametest/platform", batch = "tfmg_data")
    public static void everyItemIsTranslated(GameTestHelper helper) {
        Language language = Language.getInstance();
        List<String> missing = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(TFMG.MOD_ID))
                continue;
            String key = item.getDescriptionId();
            if (!language.has(key))
                missing.add(key);
        }
        report(helper, missing, "items without an en_us name");
    }

    /** An empty tag yields a single barrier stack named after the tag. */
    private static boolean resolves(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0)
            return false;
        for (ItemStack stack : items)
            if (!stack.is(Items.BARRIER))
                return true;
        return false;
    }

    private static String describe(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        return items.length == 0 ? "[]" : items[0].getHoverName().getString();
    }

    private static void report(GameTestHelper helper, List<String> offenders, String what) {
        if (offenders.isEmpty()) {
            helper.succeed();
            return;
        }
        TFMG.LOGGER.error("[gametest] {} {}: {}", offenders.size(), what, offenders);
        List<String> shown = offenders.subList(0, Math.min(MAX_LISTED, offenders.size()));
        helper.fail(offenders.size() + " " + what + ": " + shown);
    }
}
