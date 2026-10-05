package com.drmangotea.tfmg.content.machinery.metallurgy.blast_furnace;

import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.config.TFMGConfigs;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.recipes.IndustrialBlastingRecipe;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.drmangotea.tfmg.registry.TFMGRecipeTypes;
import com.simibubi.create.foundation.recipe.RecipeConditions;
import com.simibubi.create.foundation.recipe.RecipeFinder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.item.SmartInventory;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.List;


public class BlastFurnaceHatchBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, IInspectable {


    public FluidTank tank;

    public SmartInventory inventory;

    public IFluidHandler fluidCapability;

    public IItemHandlerModifiable itemCapability;


    public BlastFurnaceHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
        // A hatch is either the furnace's hot air tuyere or its gas outlet, so
        // it takes hot air and the gases blasting recipes give off, nothing
        // else: any other fluid kept the hot air out of the tuyere.
        tank = TFMGUtils.createTank(4000, true, true, this::onFluidChanged, this::acceptsFluid);
        inventory = new SmartInventory(1, this).withMaxStackSize(64);
        fluidCapability = tank;
        itemCapability = inventory;
    }
    /** Hot air, or a gas some industrial blasting recipe gives off. */
    public boolean acceptsFluid(FluidStack stack) {
        if (stack.getFluid().isSame(TFMGFluids.HOT_AIR.getSource()))
            return true;
        if (level == null)
            return true;
        for (RecipeHolder<? extends Recipe<?>> holder : RecipeFinder.get(BLASTING_GAS_KEY, level,
                RecipeConditions.isOfType(TFMGRecipeTypes.INDUSTRIAL_BLASTING.getType()))) {
            FluidStack gas = ((IndustrialBlastingRecipe) holder.value()).getGasByproduct();
            if (!gas.isEmpty() && gas.getFluid().isSame(stack.getFluid()))
                return true;
        }
        return false;
    }

    private static final Object BLASTING_GAS_KEY = new Object();

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                TFMGBlockEntities.BLAST_FURNACE_HATCH.get(),
                (be, context) -> be.fluidCapability
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                TFMGBlockEntities.BLAST_FURNACE_HATCH.get(),
                (be, context) -> be.inventory
        );
    }
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

        return TFMGUtils.createFluidTooltip(this,tooltip);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        dropItems();
    }

    /**
     * The furnace this hatch serves, found by walking down to its output
     * block: either as the hot air tuyere in its walls or as the gas hatch
     * sitting on top of its shaft. Read only, loaded chunks only.
     */
    private BlastFurnaceOutputBlockEntity findFurnace(boolean[] gasHatch) {
        int maxHeight = TFMGConfigs.common().machines.blastFurnaceMaxHeight.get();
        for (int k = 0; k <= maxHeight; k++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                // Gas hatch: straight above the shaft, the output sits next to the shaft's bottom.
                BlockPos shaftBottom = worldPosition.below(k);
                if (k > 0 && furnaceAt(shaftBottom.relative(side), shaftBottom) instanceof BlastFurnaceOutputBlockEntity out
                        && out.getCachedSize() == k) {
                    gasHatch[0] = true;
                    return out;
                }
                // Tuyere: in a wall next to the shaft.
                BlockPos middle = worldPosition.relative(side).below(k);
                for (Direction outSide : Direction.Plane.HORIZONTAL) {
                    if (furnaceAt(middle.relative(outSide), middle) instanceof BlastFurnaceOutputBlockEntity out
                            && worldPosition.equals(out.tuyerePos))
                        return out;
                }
            }
        }
        return null;
    }

    private BlastFurnaceOutputBlockEntity furnaceAt(BlockPos pos, BlockPos expectedMiddle) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof BlastFurnaceOutputBlockEntity out))
            return null;
        BlockPos middle = pos.relative(out.getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).getOpposite());
        return middle.equals(expectedMiddle) ? out : null;
    }

    @Override
    public void inspect(InspectionReport report) {
        if (level == null)
            return;
        boolean[] gasHatch = {false};
        BlastFurnaceOutputBlockEntity furnace = findFurnace(gasHatch);
        if (furnace == null) {
            report.problem("blast_furnace_hatch.unused");
            report.fix("blast_furnace_hatch.unused.fix");
            return;
        }
        BlockPos out = furnace.getBlockPos();
        if (gasHatch[0]) {
            report.ok("blast_furnace_hatch.role_gas", out.getX(), out.getY(), out.getZ());
            if (tank.getSpace() <= 0) {
                report.problem("blast_furnace_hatch.gas_full");
                report.fix("blast_furnace_hatch.gas_full.fix");
            }
        } else {
            report.ok("blast_furnace_hatch.role_tuyere", out.getX(), out.getY(), out.getZ());
        }
        if (!inventory.isEmpty())
            report.info("blast_furnace_hatch.item", inventory.getStackInSlot(0).getCount(), inventory.getStackInSlot(0).getHoverName());
        report.info("blast_furnace_hatch.furnace_report");
        furnace.inspect(report);
    }

    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, inventory);
    }

    public void dropItems(){

        // Entity spawning must stay server-side; lazyTick runs on both sides.
        if (level.isClientSide)
            return;
        if(level.getBlockState(getBlockPos().below()).isAir()){
            if (inventory.getItem(0).getItem() == Items.AIR){
                return;
            } else {
                Vec3 dropVec = VecHelper.getCenterOf(worldPosition)
                        .add(0, -12 / 16f, 0);
                ItemEntity dropped = new ItemEntity(level, dropVec.x, dropVec.y, dropVec.z, inventory.getItem(0).copy());
                dropped.setDefaultPickUpDelay();
                dropped.setDeltaMovement(0, -.25f, 0);
                level.addFreshEntity(dropped);
                inventory.setStackInSlot(0, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);

        tank.readFromNBT(registries,compound.getCompound("TankContent"));
        // The slot holds items a funnel pushed in while the block below was
        // not air; without this they vanished on the next chunk reload.
        if (compound.contains("Inventory"))
            inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries , clientPacket);
        compound.put("TankContent", tank.writeToNBT(registries,new CompoundTag()));
        compound.put("Inventory", inventory.serializeNBT(registries));


    }

    private void onFluidChanged(FluidStack stack) {
        if (!hasLevel())
            return;

        if (!level.isClientSide) {
            setChanged();
            sendData();
        }
    }





}
