package com.drmangotea.tfmg.base.capability;

import com.drmangotea.tfmg.TFMG;
import com.simibubi.create.foundation.recipe.RecipeConditions;
import com.simibubi.create.foundation.recipe.RecipeFinder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Keeps each fluid input of a machine for the fluids meant for it.
 *
 * A machine input tank that takes any fluid is a trap: the moment it runs
 * dry, whatever a neighbouring pipe carries (the fuel line, the exhaust, the
 * product pumped out of the same face) flows in, the recipe can never match
 * again and the tank, which pipes may not empty, keeps the wrong fluid for
 * good. Validators built here decide what a slot takes from the recipes that
 * read it, so a datapack recipe extends them on its own.
 *
 * @author vyrriox
 */
public final class FluidSlots {

    private FluidSlots() {
    }

    /**
     * Whether some recipe of {@code type} takes {@code stack} in the slot that
     * {@code slot} picks out of it. Amounts are ignored: a trickle of the right
     * fluid belongs in the slot as much as a full tank.
     *
     * Without a level, or when no recipe of the type exists at all (a pack that
     * removed them), every fluid is accepted, which is how these tanks behaved
     * before; refusing everything would only make the machine unusable.
     */
    public static boolean acceptedByRecipes(@Nullable Level level, Object cacheKey, RecipeType<?> type,
                                            Function<Recipe<?>, List<SizedFluidIngredient>> slot, FluidStack stack) {
        if (stack.isEmpty())
            return false;
        if (level == null)
            return true;
        List<RecipeHolder<? extends Recipe<?>>> recipes = RecipeFinder.get(cacheKey, level, RecipeConditions.isOfType(type));
        if (recipes.isEmpty())
            return true;
        for (RecipeHolder<? extends Recipe<?>> holder : recipes)
            for (SizedFluidIngredient ingredient : slot.apply(holder.value()))
                if (ingredient != null && ingredient.ingredient().test(stack))
                    return true;
        return false;
    }

    /**
     * Empties a machine input that holds a fluid it does not take, and logs it.
     * Only worlds saved before the inputs had validators can hold one: the
     * fluid was stuck there for good, since pipes may not drain an input, and
     * kept the machine from ever running again. Voiding it on load lets the
     * machine resume with nothing for the player to do.
     *
     * @return whether anything was voided
     */
    public static boolean voidForeignFluid(FluidTank tank, Predicate<FluidStack> accepts, BlockEntity owner, String slot) {
        FluidStack held = tank.getFluid();
        if (held.isEmpty() || accepts.test(held))
            return false;
        TFMG.LOGGER.warn("Voided {} mB of {} from the {} of the {} at {}: that tank does not take this fluid",
                held.getAmount(), BuiltInRegistries.FLUID.getKey(held.getFluid()), slot,
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType()), owner.getBlockPos().toShortString());
        tank.setFluid(FluidStack.EMPTY);
        return true;
    }

    /** As above, judged by the tank's own validator. */
    public static boolean voidForeignFluid(FluidTank tank, BlockEntity owner, String slot) {
        return voidForeignFluid(tank, tank::isFluidValid, owner, slot);
    }
}
