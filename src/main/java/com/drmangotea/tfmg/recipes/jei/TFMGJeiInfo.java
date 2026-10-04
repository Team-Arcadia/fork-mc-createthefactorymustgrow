package com.drmangotea.tfmg.recipes.jei;

import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGItems;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI information pages for the machines whose rules were only discoverable
 * by reading the code: multiblock shapes, hidden requirements and which face
 * does what. Each page is a list of short lines under
 * {@code tfmg.jei.info.<page>.<n>}.
 *
 * @author vyrriox
 */
public final class TFMGJeiInfo {

    private TFMGJeiInfo() {
    }

    public static void register(IRecipeRegistration registration) {
        page(registration, "blast_furnace", 6, TFMGBlocks.BLAST_FURNACE_OUTPUT.get(), TFMGBlocks.BLAST_FURNACE_HATCH.get(),
                TFMGBlocks.FIREPROOF_BRICKS.get(), TFMGBlocks.FIREPROOF_BRICK_REINFORCEMENT.get(),
                TFMGBlocks.BLAST_FURNACE_REINFORCEMENT.get());
        page(registration, "blast_stove", 4, TFMGBlocks.BLAST_STOVE.get());
        page(registration, "air_intake", 3, TFMGBlocks.AIR_INTAKE.get());
        page(registration, "coke_oven", 4, TFMGBlocks.COKE_OVEN.get());
        page(registration, "casting_basin", 2, TFMGBlocks.CASTING_BASIN.get());
        page(registration, "arc_furnace", 4, TFMGBlocks.FIREPROOF_CHEMICAL_VAT.get(), TFMGItems.GRAPHITE_ELECTRODE.get());
        page(registration, "vat", 3, TFMGBlocks.STEEL_CHEMICAL_VAT.get(), TFMGBlocks.CAST_IRON_CHEMICAL_VAT.get());
        page(registration, "distillation", 4, TFMGBlocks.STEEL_DISTILLATION_CONTROLLER.get(),
                TFMGBlocks.STEEL_DISTILLATION_OUTPUT.get());
        page(registration, "firebox", 2, TFMGBlocks.FIREBOX.get());
        page(registration, "surface_scanner", 2, TFMGBlocks.SURFACE_SCANNER.get());
        page(registration, "large_generator", 2, TFMGBlocks.ROTOR.get(), TFMGBlocks.STATOR.get());
        page(registration, "coal_coke_dust", 2, TFMGItems.COAL_COKE_DUST.get());
    }

    private static void page(IRecipeRegistration registration, String key, int lines, ItemLike... items) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemLike item : items)
            stacks.add(new ItemStack(item));
        Component[] text = new Component[lines];
        for (int i = 0; i < lines; i++)
            text[i] = Component.translatable("tfmg.jei.info." + key + "." + (i + 1));
        registration.addItemStackInfo(stacks, text);
    }
}
