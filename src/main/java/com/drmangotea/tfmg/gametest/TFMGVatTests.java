package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity;
import com.drmangotea.tfmg.recipes.VatMachineRecipe;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Chemical vat transaction tests. They drive {@link VatBlockEntity#handleRecipe()}
 * directly with a forced recipe, which isolates the consume / produce / recover
 * bookkeeping from machine discovery and multiblock formation.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGVatTests {

    private static final BlockPos VAT = new BlockPos(2, 1, 2);
    private static final int CYCLES = 60;

    /**
     * arc_furnace_steel consumes one coal coke dust per cycle and gives it
     * back with a 90% chance. Over many cycles: every cycle costs exactly 0 or
     * 1 dust, the recovered dust always lands back in the input (where the
     * next cycle can use it), and each cycle yields 144 mB of molten steel.
     */
    @GameTest(template = "gametest/platform", batch = "tfmg_vat", timeoutTicks = 200)
    public static void arcFurnaceRecoversCokeDust(GameTestHelper helper) {
        helper.setBlock(VAT, TFMGBlocks.FIREPROOF_CHEMICAL_VAT.get().defaultBlockState());
        helper.runAfterDelay(5, () -> {
            VatBlockEntity vat = helper.getBlockEntity(VAT);
            RecipeHolder<?> holder = helper.getLevel().getRecipeManager()
                    .byKey(TFMG.asResource("vat_machine_recipe/arc_furnace_steel")).orElse(null);
            TFMGGameTestUtil.check(helper, holder != null && holder.value() instanceof VatMachineRecipe,
                    "arc_furnace_steel recipe missing");
            VatMachineRecipe recipe = (VatMachineRecipe) holder.value();

            vat.inputInventory.setStackInSlot(0, new ItemStack(AllItems.CRUSHED_IRON.get(), 64));
            vat.inputInventory.setStackInSlot(1, new ItemStack(TFMGItems.LIMESAND.get(), 64));
            vat.inputInventory.setStackInSlot(2, new ItemStack(TFMGItems.COAL_COKE_DUST.get(), 64));

            int lost = 0;
            int steel = 0;
            for (int cycle = 0; cycle < CYCLES; cycle++) {
                int dustBefore = count(vat, TFMGItems.COAL_COKE_DUST.get().getDefaultInstance());
                int ironBefore = count(vat, AllItems.CRUSHED_IRON.asStack());
                // handleRecipe counts the timer up to the recipe duration, then
                // runs the transaction once.
                for (int t = 0; t <= recipe.getProcessingDuration() + 1 && count(vat, AllItems.CRUSHED_IRON.asStack()) == ironBefore; t++) {
                    vat.recipe = recipe;
                    vat.handleRecipe();
                }
                int dustAfter = count(vat, TFMGItems.COAL_COKE_DUST.get().getDefaultInstance());
                int delta = dustAfter - dustBefore;
                TFMGGameTestUtil.check(helper, count(vat, AllItems.CRUSHED_IRON.asStack()) == ironBefore - 1,
                        "cycle " + cycle + " did not consume exactly one crushed iron");
                TFMGGameTestUtil.check(helper, delta == 0 || delta == -1,
                        "cycle " + cycle + " changed the coke dust count by " + delta);
                if (delta == -1)
                    lost++;
                steel += drainSteel(vat);
            }

            TFMGGameTestUtil.check(helper, outputHasNo(vat, TFMGItems.COAL_COKE_DUST.get().getDefaultInstance()),
                    "recovered coke dust ended in the output, where no cycle can use it");
            TFMGGameTestUtil.check(helper, steel == CYCLES * 144,
                    "expected " + CYCLES * 144 + " mB of molten steel, got " + steel);
            // 60 draws at 10%: the chance of zero losses is 0.9^60 = 0.18%, of
            // more than 20 losses well under one in a million.
            TFMGGameTestUtil.check(helper, lost >= 1 && lost <= 20,
                    "coke dust lost on " + lost + " of " + CYCLES + " cycles, expected about 10%");
            helper.succeed();
        });
    }

    private static int count(VatBlockEntity vat, ItemStack like) {
        int n = 0;
        for (int i = 0; i < vat.inputInventory.getSlots(); i++) {
            ItemStack s = vat.inputInventory.getStackInSlot(i);
            if (ItemStack.isSameItem(s, like))
                n += s.getCount();
        }
        return n;
    }

    private static boolean outputHasNo(VatBlockEntity vat, ItemStack like) {
        for (int i = 0; i < vat.outputInventory.getSlots(); i++)
            if (ItemStack.isSameItem(vat.outputInventory.getStackInSlot(i), like))
                return false;
        return true;
    }

    private static int drainSteel(VatBlockEntity vat) {
        IFluidHandler out = vat.outputTank.getCapability();
        FluidStack drained = out.drain(new FluidStack((Fluid) TFMGFluids.MOLTEN_STEEL.getSource(), Integer.MAX_VALUE), IFluidHandler.FluidAction.EXECUTE);
        // Slag is a by-product; empty it so the tank never fills up.
        out.drain(new FluidStack((Fluid) TFMGFluids.MOLTEN_SLAG.getSource(), Integer.MAX_VALUE), IFluidHandler.FluidAction.EXECUTE);
        return drained.getAmount();
    }
}
