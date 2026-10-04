package com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.controller;

import com.drmangotea.tfmg.base.ThrottledSync;
import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.content.decoration.tanks.steel.SteelTankBlock;
import com.drmangotea.tfmg.content.decoration.tanks.steel.SteelTankBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.output.DistillationOutputBlockEntity;
import com.drmangotea.tfmg.mixin.accessor.FluidTankBlockEntityAccessor;
import com.drmangotea.tfmg.recipes.DistillationRecipe;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGRecipeTypes;
import com.drmangotea.tfmg.registry.TFMGTags;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.recipe.RecipeConditions;
import com.simibubi.create.foundation.recipe.RecipeFinder;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;


import java.util.ArrayList;
import java.util.List;

import static com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.controller.DistillationControllerBlock.getFacing;

public class DistillationControllerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    // Per-tick fluid and progress changes sync at most every few ticks.
    private final ThrottledSync throttledSync = new ThrottledSync();

    private static final Object DistillationRecipesKey = new Object();

    public DistillationRecipe recipe;
    private ArrayList<DistillationOutputBlockEntity> cachedOutputs;
    private int outputsCacheCooldown;

    LerpedFloat angle = LerpedFloat.angular();

    protected IFluidHandler fluidCapability;

    public final FluidTank tank = new SmartFluidTank(8000, this::onFluidStackChanged);

    public DistillationControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        fluidCapability = tank;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                TFMGBlockEntities.DISTILLATION_CONTROLLER.get(),
                (be, context) -> be.fluidCapability
        );
    }

    @Override
    public void remove() {
        super.remove();
        SteelTankBlock.updateTowerState(level, getBlockPos().relative(getFacing(getBlockState()).getOpposite()),false,false);

    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void manageDialRendering(){
        if (level.isClientSide) {
            angle.chase(180 * ((float) tank.getFluidAmount() / tank.getCapacity()), 0.2f, LerpedFloat.Chaser.EXP);
            angle.tickChaser();
        }
    }

    public void findRecipe(ArrayList<DistillationOutputBlockEntity> outputs){
        if (recipe != null && recipe.matches(tank, outputs.size()))
            return;
        // The old code only overwrote this.recipe when getMatchingRecipes
        // returned a hit; on a miss the controller kept the previous recipe
        // and the rest of manageRecipe ran against stale getFluidResults().
        // Clear it so manageRecipe exits early instead of trying to fill
        // the wrong outputs with the wrong fluid.
        DistillationRecipe found = getMatchingRecipes();
        if (found == this.recipe)
            return;
        this.recipe = found;
        sendData();
    }

    public void manageRecipe(){
        if (level.isClientSide)
            return;

        ArrayList<DistillationOutputBlockEntity> outputs = getOutputsCached();
        BlockEntity beBehind = level.getBlockEntity(getBlockPos().relative(getFacing(getBlockState()).getOpposite()));
        if (!(beBehind instanceof SteelTankBlockEntity be))
            return;

        // If "be" is a slave whose controller chunk is not loaded, getControllerBE()
        // returns null and the old fallback used the slave itself — a slave has
        // width=1, height=1, activeHeat=0, so manageRecipe exited prematurely on
        // every tick where chunk loading was racy. This presented to the player
        // as "1 of 3 distillation towers works": the only tower whose tank
        // controller happened to be inside the same chunk progressed; the
        // others stalled. Skip the tick instead of falling back, and let the
        // next tick (after chunks settle) actually try.
        SteelTankBlockEntity controllerBE = be.getControllerBE();
        if (controllerBE == null && !be.isController())
            return;
        SteelTankBlockEntity heatSource = controllerBE != null ? controllerBE : be;

        int outputCount = outputs.size();
        if (outputCount == 0 || heatSource.activeHeat == 0)
            return;

        findRecipe(outputs);

        if (recipe == null)
            return;

        float speedModifier = (float) heatSource.activeHeat / 2;
        if (recipe.getInputFluid().amount() * speedModifier > tank.getFluidAmount())
            return;

        if (recipe.getFluidResults().size() != outputCount)
            return;
        SteelTankBlockEntity sizeRef = heatSource;
        int sizeRefWidth = ((FluidTankBlockEntityAccessor) sizeRef).tfmg$getWidth();
        if (sizeRef.getHeight() < outputCount * 2 || (sizeRefWidth < 2 && outputCount > 3))
            return;

        // Heavy oil distillation has heavy_oil as its first result (a partial
        // self-loop). If output 0 was set to KEEP_FLUID and got full, the
        // old code returned for the whole recipe, blocking outputs 1..N
        // even though they had room. The redundant pre-check has been
        // removed and the in-loop full-output handling now skips that
        // single output instead of breaking the chain — all the still-
        // empty outputs keep receiving their fractions and the tank
        // drains proportionally to what was actually filled.
        // Each output that actually gets filled drains its own share, so a full
        // set consumes exactly the recipe's input. The divisor was hardcoded to
        // six while the loop below runs once per output, so every recipe with
        // fewer than six fractions burned less oil than it declared: the
        // three-fraction light distillations ran on half the crude oil they
        // were supposed to consume, which reads as the tower yielding double.
        int consumption = recipe.getInputFluid().amount() / Math.max(1, recipe.getFluidResults().size());
        boolean anyFilled = false;
        for (int numero = 0; numero < outputs.size() && numero < recipe.getFluidResults().size(); numero++) {
            DistillationOutputBlockEntity output = outputs.get(numero);
            FluidStack fluidStack = recipe.getFluidResults().get(numero);
            if (fluidStack.isEmpty())
                continue;
            int fillAmount = (int) (fluidStack.getAmount() * speedModifier);
            if (fillAmount <= 0)
                continue;
            FluidStack toFill = new FluidStack(fluidStack.getFluidHolder(), fillAmount);
            int simulated = output.tank.fill(toFill, IFluidHandler.FluidAction.SIMULATE);
            if (simulated < fillAmount && output.mode.get() == DistillationOutputBlockEntity.DistillationOutputMode.KEEP_FLUID)
                continue;

            output.tank.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
            tank.drain((int) (consumption * speedModifier), IFluidHandler.FluidAction.EXECUTE);
            anyFilled = true;
        }
        if (!anyFilled)
            return;
    }
    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide)
            throttledSync.tick(this);

        manageDialRendering();
        if (!level.isClientSide)
            pullFromSteelTank();
        manageRecipe();

    }

    private void pullFromSteelTank() {
        int space = tank.getSpace();
        if (space <= 0)
            return;
        BlockEntity beBehind = level.getBlockEntity(getBlockPos().relative(getFacing(getBlockState()).getOpposite()));
        if (!(beBehind instanceof SteelTankBlockEntity be))
            return;
        SteelTankBlockEntity controllerBE = be.getControllerBE();
        // If we are touching a slave and the controller chunk is not loaded,
        // the slave's local tank is empty (storage lives on the controller).
        // Skip the tick rather than pulling 0 from a slave; this fixes the
        // cross-chunk distillation feed bug where the second tower never
        // received heavy oil because the SteelTank controller sat in a
        // neighbouring chunk.
        if (!be.isController() && controllerBE == null)
            return;
        FluidTank steelTank = (controllerBE != null ? controllerBE : be).getTankInventory();
        FluidStack stored = steelTank.getFluid();
        if (stored.isEmpty())
            return;
        if (!tank.getFluid().isEmpty() && !tank.getFluid().getFluid().isSame(stored.getFluid()))
            return;
        int pullAmount = Math.min(stored.getAmount(), space);
        if (pullAmount <= 0)
            return;
        FluidStack drained = steelTank.drain(pullAmount, IFluidHandler.FluidAction.EXECUTE);
        if (!drained.isEmpty())
            tank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
    }

    protected void onFluidStackChanged(FluidStack newFluidStack) {
        if (!hasLevel())
            return;

        if (!level.isClientSide) {
            setChanged();
            throttledSync.request(this);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

        BlockEntity beBehind = level.getBlockEntity(getBlockPos().relative(getFacing(getBlockState()).getOpposite()));
        if (beBehind instanceof SteelTankBlockEntity be) {
            SteelTankBlockEntity controllerBE = be.getControllerBE();
            TFMGTexts.header("distillation_tower").style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            TFMGTexts.Distillation.level(controllerBE != null ? controllerBE.activeHeat : be.activeHeat).forGoggles(tooltip, 1);
            TFMGTexts.Distillation.outputs(getOutputs().size()).forGoggles(tooltip, 1);
        } else
            TFMGTexts.Distillation.tankNotFound().forGoggles(tooltip, 1);

        TFMGUtils.createFluidTooltip(this,tooltip);

        return true;
    }

    protected DistillationRecipe getMatchingRecipes() {
        List<RecipeHolder<? extends Recipe<?>>> list = RecipeFinder.get(getRecipeCacheKey(), level, RecipeConditions.isOfType(TFMGRecipeTypes.DISTILLATION.getType()));
        int outputCount = getOutputs().size();
        FluidStack tankFluid = tank.getFluid();
        for (RecipeHolder<? extends Recipe<?>> holder : list) {
            DistillationRecipe recipe = (DistillationRecipe) holder.value();
            if (recipe.getFluidResults().size() != outputCount)
                continue;
            if (recipe.getFluidIngredients().isEmpty())
                continue;
            SizedFluidIngredient firstIngredient = recipe.getFluidIngredients().getFirst();
            if (tank.getFluidAmount() < firstIngredient.amount())
                continue;
            for (FluidStack ingredientFluid : firstIngredient.getFluids()) {
                if (tankFluid.getFluid().isSame(ingredientFluid.getFluid()))
                    return recipe;
            }
        }
        return null;
    }

    protected Object getRecipeCacheKey() {
        return DistillationRecipesKey;
    }

    //@Nonnull
    //@Override
    //public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction side) {
    //    if (cap == ForgeCapabilities.FLUID_HANDLER)
    //        return fluidCapability.cast();
    //    return super.getCapability(cap, side);
    //}

    public ArrayList<DistillationOutputBlockEntity> getOutputsCached() {
        if (cachedOutputs == null || outputsCacheCooldown <= 0 || !cachedOutputsStillValid()) {
            cachedOutputs = getOutputs();
            outputsCacheCooldown = 20;
        } else {
            outputsCacheCooldown--;
        }
        return cachedOutputs;
    }

    /**
     * The cache is rebuilt on a 20-tick timer, so for up to a second after a
     * player breaks a stage — or after the stages' chunk unloads while the
     * controller stays loaded, which a tower straddling a chunk border does —
     * the controller still counted the missing stage towards outputCount and
     * pushed its fraction into a block entity detached from the world, where
     * the fluid simply disappeared. Six references, checked once per tick.
     */
    private boolean cachedOutputsStillValid() {
        for (DistillationOutputBlockEntity output : cachedOutputs)
            if (output.isRemoved())
                return false;
        return true;
    }

    public void invalidateOutputsCache() {
        cachedOutputs = null;
        outputsCacheCooldown = 0;
    }

    public ArrayList<DistillationOutputBlockEntity> getOutputs() {
        ArrayList<DistillationOutputBlockEntity> outputs = new ArrayList<>();
        BlockPos checkedPos = this.getBlockPos().above();
        for (int i = 0; i < 11; i++) {
            if (i == 0 || i == 2 || i == 4 || i == 6 || i == 8 || i == 10) {
                if (level.getBlockEntity(checkedPos) instanceof DistillationOutputBlockEntity be) {
                    outputs.add(be);
                } else break;
            } else {
                if (!(level.getBlockState(checkedPos).is(TFMGTags.TFMGBlockTags.INDUSTRIAL_PIPE.tag)))
                    break;
            }
            checkedPos = checkedPos.above();
        }
        return outputs;
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);
        tank.readFromNBT(registries,compound.getCompound("TankContent"));
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.put("TankContent", tank.writeToNBT(registries,new CompoundTag()));
    }
}
