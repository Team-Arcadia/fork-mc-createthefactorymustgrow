package com.drmangotea.tfmg.recipes.jei;

import com.drmangotea.tfmg.recipes.VatMachineRecipe;
import com.drmangotea.tfmg.registry.TFMGGuiTextures;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.item.ItemHelper;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.ArrayList;
import java.util.List;

public class ChemicalVatCategory extends CreateRecipeCategory<VatMachineRecipe> {

    public ChemicalVatCategory(Info<VatMachineRecipe> info) {
        super(info);
    }

    public void setRecipe(IRecipeLayoutBuilder builder, VatMachineRecipe recipe, IFocusGroup focuses) {
        int fluidCount = recipe.getFluidIngredients().size();
        int pos = 55;
        int width = ((fluidCount) * 20) / 2;
        int movement = fluidCount != 4 ? 1 : 0;
        if (fluidCount == 1)
            movement = 2;
        for (int i = 0; i < fluidCount; i++) {

            addFluidSlot(builder, pos - width + movement, recipe.getIngredients().isEmpty() ? 72 : 85, recipe.getFluidIngredients().get(i));

            pos += 21;
        }
        List<Pair<Ingredient, MutableInt>> condensedIngredients = ItemHelper.condenseIngredients(recipe.getIngredients());

        int itemCount = condensedIngredients.size();
        int itemPos = 55;
        int itemWidth = ((itemCount) * 20) / 2;
        int itemMovement = itemCount != 4 ? 1 : 0;
        if (itemCount == 1)
            itemMovement = 2;
        for (Pair<Ingredient, MutableInt> pair : condensedIngredients) {
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemStack itemStack : pair.getFirst().getItems()) {
                ItemStack copy = itemStack.copy();
                copy.setCount(pair.getSecond().getValue());
                stacks.add(copy);
            }
            builder.addSlot(RecipeIngredientRole.INPUT, itemPos - itemWidth + itemMovement, recipe.getFluidIngredients().isEmpty() ? 72 : 64).setBackground(getRenderedSlot(), -1, -1).addItemStacks(stacks);

            itemPos += 21;
        }
        /////////////////////////////

        int fluidResultPos = 90;

        for (int i = 0; i < recipe.getFluidResults().size(); i++) {

            addFluidSlot(builder, 150, fluidResultPos, recipe.getFluidResults().get(i));

            fluidResultPos -= 21;
        }

        int itemResultPos = 90;

        for (int i = 0; i < recipe.getRollableResults().size(); i++) {
            ProcessingOutput output = recipe.getRollableResults().get(i);
            var slot = builder
                    .addSlot(RecipeIngredientRole.OUTPUT, 128, itemResultPos)
                    .setBackground(getRenderedSlot(output), -1, -1)
                    .addItemStack(output.getStack())
                    .addRichTooltipCallback(addStochasticTooltip(output));
            // A result that is also an ingredient (the coal coke dust of the
            // arc furnace) never reaches the output: the vat puts it straight
            // back into its input. Shown as a plain chance output, players
            // waited for a product that never appeared.
            if (isRecovered(recipe, output.getStack())) {
                int lossPercent = Math.round((1 - output.getChance()) * 100);
                slot.addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("tfmg.jei.vat.recovered").withStyle(ChatFormatting.GOLD));
                    tooltip.add(Component.translatable("tfmg.jei.vat.recovered.loss", lossPercent).withStyle(ChatFormatting.GRAY));
                });
            }

            itemResultPos -= 21;
        }
    }

    private static boolean isRecovered(VatMachineRecipe recipe, ItemStack result) {
        for (Ingredient ingredient : recipe.getIngredients())
            if (ingredient.test(result))
                return true;
        return false;
    }

    public void draw(VatMachineRecipe recipe, IRecipeSlotsView iRecipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {

        List<String> machines = recipe.machines;
        List<String> allowedVatTypes = recipe.allowedVatTypes;


        TFMGGuiTextures.VAT.render(graphics, 0, 24);

        drawVatTypes(allowedVatTypes, graphics);

        drawSprites(machines, graphics);

        // Text hint for required machines that have no sprite above (freezer,
        // compressor). Without it the LPG / cooling-fluid recipes showed no
        // machine at all, so players assumed "just add the fluids".
        drawMachineHints(recipe, graphics);


        if(recipe.heatLevel!=0) {
            TFMGGuiTextures.VAT_HEATER.render(graphics, 55 - 10, 109);
            graphics.drawString(Minecraft.getInstance().font, String.valueOf((recipe.heatLevel + 10f) / 10f), 76.0F, 113.0F, 0xFF501C, false);
        }

        int pos = 55;
        int width = ((recipe.getFluidIngredients().size()) * 21) / 2;
        for (int i = 0; i < recipe.getFluidIngredients().size(); i++) {

            TFMGGuiTextures.SLOT.render(graphics, pos - width, recipe.getIngredients().isEmpty() ? 70 : 83);

            pos += 21;
        }
        int posItem = 55;
        List<Pair<Ingredient, MutableInt>> condensedIngredients = ItemHelper.condenseIngredients(recipe.getIngredients());
        int widthItem = ((condensedIngredients.size()) * 21) / 2;
        for (int i = 0; i < condensedIngredients.size(); i++) {

            TFMGGuiTextures.SLOT.render(graphics, posItem - widthItem, recipe.getFluidIngredients().isEmpty() ? 70 : 62);

            posItem += 21;
        }


        //AllGuiTextures.JEI_ARROW.render(graphics, 85, 32);
        //AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 43, 4);


    }

    private void renderHeated(HeatCondition heatCondition, GuiGraphics graphics) {
        if (heatCondition == HeatCondition.HEATED)
            TFMGGuiTextures.VAT_HEATER.render(graphics, 55 - 10, 109);
        if (heatCondition == HeatCondition.SUPERHEATED)
            TFMGGuiTextures.VAT_SUPERHEATER.render(graphics, 55 - 10, 109);
    }

    private void drawVatTypes(List<String> allowedVatTypes, GuiGraphics graphics) {
        if (allowedVatTypes.contains("tfmg:firebrick_lined_vat") && allowedVatTypes.size() == 1) {
            TFMGGuiTextures.FIREPROOF_BRICK_OVERLAY.render(graphics, 55 - 48, 32);
        }
        if (allowedVatTypes.contains("tfmg:cast_iron_vat") && allowedVatTypes.size() == 1) {
            TFMGGuiTextures.CAST_IRON_VAT_OVERLAY.render(graphics, 0, 24);
        }
    }

    private void drawSprites(List<String> machines, GuiGraphics graphics) {
        if (machines.contains("tfmg:mixing")) {
            TFMGGuiTextures.VAT_MACHINE.render(graphics, 55 - 12, 0);
            TFMGGuiTextures.MIXER.render(graphics, 55 - 19, 32);
        }
        if (machines.contains("tfmg:centrifuge")) {
            TFMGGuiTextures.VAT_MACHINE.render(graphics, 55 - 12, 0);
            TFMGGuiTextures.CENTRIFUGE.render(graphics, 55 - 12, 32);
        }
        if (machines.contains("tfmg:electrode")) {
            TFMGGuiTextures.VAT_MACHINE.render(graphics, 55 - 12 - 32, 0);
            TFMGGuiTextures.VAT_MACHINE.render(graphics, 55 - 12 + 32, 0);
            TFMGGuiTextures.ELECTRODE.render(graphics, 55 - 3 - 32, 32);
            TFMGGuiTextures.ELECTRODE.render(graphics, 55 - 3 + 32, 32);
        }
        if (machines.contains("tfmg:graphite_electrode")) {
            TFMGGuiTextures.VAT_MACHINE.render(graphics, 55 - 12 - 32, 0);
            TFMGGuiTextures.VAT_MACHINE.render(graphics, 55 - 12 + 32, 0);
            TFMGGuiTextures.VAT_MACHINE.render(graphics, 55 - 12, 0);
            TFMGGuiTextures.GRAPHITE_ELECTRODE.render(graphics, 55 - 4 - 32, 32);
            TFMGGuiTextures.GRAPHITE_ELECTRODE.render(graphics, 55 - 4 + 32, 32);
            TFMGGuiTextures.GRAPHITE_ELECTRODE.render(graphics, 55 - 4, 32);
        }
    }

    // Machines that drawSprites() already renders as a picture — skip them in
    // the text hint so we don't duplicate the info.
    private static final List<String> SPRITED_MACHINES = List.of(
            "tfmg:mixing", "tfmg:centrifuge", "tfmg:electrode", "tfmg:graphite_electrode");

    /**
     * Draws a small text list of the machines a recipe needs that have no
     * sprite (the freezer and the compressor), plus the pressure requirement.
     * This is the fix for recipes like compressed_lpg / cooling_fluid that
     * otherwise showed no machine and read as "just add the fluids".
     */
    private void drawMachineHints(VatMachineRecipe recipe, GuiGraphics graphics) {
        int y = 2;
        java.util.Set<String> shown = new java.util.HashSet<>();
        for (String machine : recipe.machines) {
            if (SPRITED_MACHINES.contains(machine))
                continue;
            if (!shown.add(machine))
                continue;
            String name = Component.translatable("tfmg.goggles.vat." + machine.replace(":", ".")).getString().trim();
            graphics.drawString(Minecraft.getInstance().font, name, 2, y, 0xFF404040, false);
            y += 10;
        }
        if (recipe.pressure != 0) {
            graphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("tfmg.jei.vat.pressure", recipe.pressure).getString(),
                    2, y, 0xFF1C3C64, false);
            y += 10;
        }
        // The minimum size and a vat-type restriction used to be invisible:
        // a 2x2 firebrick vat never ran the arc furnace and nothing said why.
        if (recipe.minSize > 1) {
            int side = (int) Math.ceil(Math.sqrt(recipe.minSize));
            graphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("tfmg.jei.vat.min_size", recipe.minSize, side, side).getString(),
                    2, y, 0xFF404040, false);
            y += 10;
        }
        if (recipe.allowedVatTypes.size() > 1 && recipe.allowedVatTypes.size() < ALL_VAT_TYPES) {
            StringBuilder names = new StringBuilder();
            for (String type : recipe.allowedVatTypes) {
                if (!names.isEmpty())
                    names.append(", ");
                names.append(Component.translatable("tfmg.jei.vat.type." + type.replace(":", ".")).getString());
            }
            graphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("tfmg.jei.vat.types", names.toString()).getString(),
                    2, y, 0xFF404040, false);
        }
    }

    private static final int ALL_VAT_TYPES = 3;

}