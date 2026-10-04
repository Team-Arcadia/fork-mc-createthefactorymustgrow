package com.drmangotea.tfmg.recipes.jei;

import com.drmangotea.tfmg.content.machinery.misc.winding_machine.SpoolItem;
import com.drmangotea.tfmg.recipes.WindingRecipe;
import com.drmangotea.tfmg.recipes.jei.machines.WindingMachine;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedDeployer;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public class WindingCategory extends CreateRecipeCategory<WindingRecipe> {
    private final WindingMachine windingMachine = new WindingMachine();

    public WindingCategory(Info<WindingRecipe> info) {
        super(info);
    }

    public void setRecipe(IRecipeLayoutBuilder builder, WindingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 15, 9).setBackground(getRenderedSlot(), -1, -1).addIngredients(recipe.getIngredients().get(0));

        // Copy: Ingredient.getItems() exposes the ingredient's cached stacks;
        // mutating them in place leaked the display component into the live
        // recipe object shared with matching logic.
        ItemStack[] spoolStacks = recipe.getSpool().getItems();
        ItemStack coil = spoolStacks.length == 0 ? ItemStack.EMPTY : spoolStacks[0].copy();
        if (!coil.isEmpty())
            coil.set(TFMGDataComponents.SPOOL_AMOUNT,recipe.getProcessingDuration());

        builder.addSlot(RecipeIngredientRole.INPUT, 15, 30).setBackground(getRenderedSlot(), -1, -1).addItemStack(coil);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 140, 28).setBackground(getRenderedSlot(), -1, -1).addItemStack(recipe.getResultItem(Minecraft.getInstance().level.registryAccess()));
    }

    public void draw(WindingRecipe recipe, IRecipeSlotsView iRecipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {

        AllGuiTextures.JEI_ARROW.render(graphics, 85, 32);
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 43, 4);

        int coilColor = 0;

        ItemStack[] spoolStacks = recipe.getSpool().getItems();
        if (spoolStacks.length > 0 && spoolStacks[0].getItem() instanceof SpoolItem) {
            coilColor = spoolStacks[0].getBarColor();
        }
        this.windingMachine.draw(graphics, 48, 27,coilColor,true);
        graphics.drawString(Minecraft.getInstance().font, recipe.getProcessingDuration() + " Turns", 86.0F, 9.0F, 4210752, false);
    }

    public static class AssemblyWinding extends SequencedAssemblySubCategory {
        private final WindingMachine windingMachine = new WindingMachine();
        private final AnimatedDeployer 	deployer = new AnimatedDeployer();
        public AssemblyWinding() {
            super(25);
        }

        public void setRecipe(IRecipeLayoutBuilder builder, SequencedRecipe<?> recipe, IFocusGroup focuses, int x) {
            // Copy for the same reason as WindingCategory.setRecipe above.
            ItemStack[] spoolStacks = recipe.getRecipe().getIngredients().size() < 2
                    ? new ItemStack[0]
                    : recipe.getRecipe().getIngredients().get(1).getItems();
            ItemStack coil = spoolStacks.length == 0 ? ItemStack.EMPTY : spoolStacks[0].copy();
            if (!coil.isEmpty())
                coil.set(TFMGDataComponents.SPOOL_AMOUNT,recipe.getRecipe().getProcessingDuration());
            builder.addSlot(RecipeIngredientRole.INPUT, x + 4, 15).setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1).addItemStack(coil);
        }

        public void draw(SequencedRecipe<?> recipe, GuiGraphics graphics, double mouseX, double mouseY, int index) {

            PoseStack ms = graphics.pose();

            int coilColor = 0;

            ItemStack[] spoolStacks = recipe.getRecipe().getIngredients().size() < 2
                    ? new ItemStack[0]
                    : recipe.getRecipe().getIngredients().get(1).getItems();
            if (spoolStacks.length > 0 && spoolStacks[0].getItem() instanceof SpoolItem) {
                coilColor = spoolStacks[0].getBarColor();
            }
            windingMachine.offset = index;
            ms.pushPose();
            ms.translate(0.0, 67, 0.0);
            ms.scale(0.7F, 0.7F, 0.7F);
            this.windingMachine.draw(graphics, this.getWidth() / 2, 0,coilColor,false);
            ms.popPose();

        }
    }
}