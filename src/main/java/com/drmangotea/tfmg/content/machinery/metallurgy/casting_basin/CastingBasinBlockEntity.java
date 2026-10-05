package com.drmangotea.tfmg.content.machinery.metallurgy.casting_basin;

import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.base.capability.FluidSlots;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.recipes.CastingRecipe;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGRecipeTypes;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.item.SmartInventory;
import com.simibubi.create.foundation.recipe.RecipeConditions;
import com.simibubi.create.foundation.recipe.RecipeFinder;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.List;

public class CastingBasinBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, IInspectable {

    int flowTimer = 0;
    // Output only: an item pushed in by a hopper or chute blocked casting.
    public SmartInventory inventory = new SmartInventory(1, this, 1, false).forbidInsertion();

    // Only fluids a casting recipe takes go in; anything else matched no
    // recipe and kept the metal out until it was pumped away.
    public FluidTank tank = TFMGUtils.createTank(1000, true, true, this::onFluidChanged, this::acceptsInput);
    public IFluidHandler fluidCapability;
    public IItemHandlerModifiable itemCapability;
    public CastingRecipe recipe = null;
    public int timer = 0;
    private static final Object castingRecipeKey = new Object();

    LerpedFloat fluidLevel = LerpedFloat.linear();

    public CastingBasinBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        fluidCapability = tank;
        itemCapability = inventory;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                TFMGBlockEntities.CASTING_BASIN.get(),
                (be, context) -> be.fluidCapability
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                TFMGBlockEntities.CASTING_BASIN.get(),
                (be, context) -> be.itemCapability
        );
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null)
            return;

        // Fill animation, and it has to run BEFORE the server guard below.
        //
        // It used to sit at the end of this method, unreachable on the client
        // ever since the guard was added: nothing drove the chaser, so the
        // renderer read a fluid level frozen at zero and the basin looked empty
        // however much metal was in it.
        if (level.isClientSide) {
            if (flowTimer > 0)
                flowTimer--;
            fluidLevel.chase(tank.getFluidAmount(), 0.3f, LerpedFloat.Chaser.EXP);
            fluidLevel.tickChaser();
        }

        // Casting is server logic; the client used to run the whole recipe
        // on its local copy (drain + setStackInSlot), flickering results.
        if (level.isClientSide && !isVirtual())
            return;
        // Old code gated the recipe on tank.getSpace() == 0 (tank full at
        // capacity 144 mB), which meant the only way to start a 144 mB
        // recipe was to fill the basin to its hard cap exactly. Pipes
        // pushing in multiple ticks would still get there eventually, but
        // single-tick over-pushes (a bucket = 1000 mB) overflowed and
        // dropped fluid. The Arcadia V2 KubeJS workaround capped every
        // recipe to 140 mB so the basin could always accept a small
        // surplus before it tried to start. Lift the cap to 1000 mB and
        // gate the recipe on having ENOUGH fluid for the recipe instead
        // of the tank being literally full — recipes consume their
        // declared amount and any excess stays in the tank for the next
        // craft. Original 144 mB recipes work without modification.
        if (recipe == null || !recipe.getIngrenient().test(tank.getFluid()))
            findRecipe();
        // A datapack recipe declaring no item result would throw below rather
        // than simply not matching, and it would take the basin's tick with it.
        if (recipe != null && recipe.getRollableResults().isEmpty())
            recipe = null;
        if (recipe != null) {
            int needed = recipe.getIngrenient().amount();
            if (tank.getFluidAmount() >= needed && inventory.isEmpty()) {
                if (timer >= recipe.getProcessingDuration()) {
                    tank.drain(needed, IFluidHandler.FluidAction.EXECUTE);
                    inventory.setStackInSlot(0, recipe.getRollableResults().get(0).rollOutput(level.random));
                    recipe = null;
                    timer = 0;
                } else timer++;
            } else timer = 0;
        } else timer = 0;
    }

    public void findRecipe() {
        recipe = null;
        List<RecipeHolder<? extends Recipe<?>>> list = RecipeFinder.get(getRecipeCacheKey(), level, RecipeConditions.isOfType(TFMGRecipeTypes.CASTING.getType()));
        for (RecipeHolder<? extends Recipe<?>> recipe1 : list) {
            CastingRecipe testedRecipe = (CastingRecipe) recipe1.value();
            if (testedRecipe.getIngrenient().test(tank.getFluid()) && inventory.isEmpty()) {
                recipe = testedRecipe;
                return;
            }
        }
    }

    protected Object getRecipeCacheKey() {
        return castingRecipeKey;
    }

    /** Whether some casting recipe takes this fluid. */
    public boolean acceptsInput(FluidStack stack) {
        return FluidSlots.acceptedByRecipes(level, getRecipeCacheKey(), TFMGRecipeTypes.CASTING.getType(),
                recipe -> List.of(((CastingRecipe) recipe).getIngrenient()), stack);
    }

    @Override
    public void inspect(InspectionReport report) {
        if (level == null)
            return;
        FluidStack fluid = tank.getFluid();
        if (!inventory.isEmpty()) {
            report.problem("casting_basin.output_waiting", inventory.getStackInSlot(0).getHoverName());
            report.fix("casting_basin.output_waiting.fix");
        }
        if (fluid.isEmpty()) {
            report.problem("casting_basin.empty");
            report.fix("casting_basin.empty.fix");
            return;
        }

        // Same lookup as findRecipe, read only: the first casting recipe whose
        // fluid ingredient accepts what the basin holds.
        CastingRecipe match = null;
        for (RecipeHolder<? extends Recipe<?>> holder : RecipeFinder.get(getRecipeCacheKey(), level, RecipeConditions.isOfType(TFMGRecipeTypes.CASTING.getType()))) {
            CastingRecipe tested = (CastingRecipe) holder.value();
            if (tested.getIngrenient().test(fluid) && !tested.getRollableResults().isEmpty()) {
                match = tested;
                break;
            }
        }
        if (match == null) {
            report.problem("casting_basin.no_recipe", fluid.getHoverName());
            report.fix("casting_basin.no_recipe.fix", fluid.getHoverName());
            return;
        }
        Component result = match.getRollableResults().get(0).getStack().getHoverName();
        int needed = match.getIngrenient().amount();
        if (fluid.getAmount() < needed) {
            report.problem("casting_basin.not_enough", fluid.getAmount(), needed, fluid.getHoverName(), result);
            report.fix("casting_basin.not_enough.fix", needed - fluid.getAmount(), fluid.getHoverName());
        } else {
            report.ok("casting_basin.recipe", fluid.getHoverName(), result, needed);
        }
        if (timer > 0 && recipe != null)
            report.info("casting_basin.progress", timer, match.getProcessingDuration());
        else
            report.info("casting_basin.duration", String.format("%.1f", match.getProcessingDuration() / 20f));
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    private void onFluidChanged(FluidStack stack) {
        flowTimer = 10;
        sendData();
        setChanged();
    }

    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, inventory);
    }



    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        TFMGUtils.createFluidTooltip(this, tooltip);
        TFMGUtils.createItemTooltip(this, tooltip);
        return true;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries , clientPacket);
        compound.put("Inventory", inventory.serializeNBT(registries));
        compound.put("Tank", tank.writeToNBT(registries,new CompoundTag()));
        compound.putInt("Timer",timer);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);
        inventory.deserializeNBT(registries,compound.getCompound("Inventory"));
        tank.readFromNBT(registries,compound.getCompound("Tank"));
        timer = compound.getInt("Timer");
    }
}
