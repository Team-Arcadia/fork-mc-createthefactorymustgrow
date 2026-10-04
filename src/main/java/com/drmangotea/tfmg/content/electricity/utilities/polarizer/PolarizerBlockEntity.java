package com.drmangotea.tfmg.content.electricity.utilities.polarizer;

import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.config.TFMGConfigs;
import com.drmangotea.tfmg.content.electricity.base.ElectricBlockEntity;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.recipes.PolarizingRecipe;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGRecipeTypes;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.item.SmartInventory;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.List;
import java.util.Optional;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;


public class PolarizerBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation, IInspectable {

    public SmartInventory inventory = new SmartInventory(1, this, 1, false)
            .whenContentsChanged(this::onInventoryChanged);
    public IItemHandlerModifiable itemCapability;

    LerpedFloat angle = LerpedFloat.angular();

    public boolean chargeCapacitors = false;
    public int capacitorPercentage = 0;

    public PolarizerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        itemCapability = inventory;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                TFMGBlockEntities.POLARIZER.get(),
                (be, context) -> be.itemCapability
        );
    }

    @Override
    public boolean hasElectricitySlot(Direction direction) {
        return direction == getBlockState().getValue(FACING).getOpposite();
    }

    public void onInventoryChanged(int count) {
        sendData();
        setChanged();
        if (inventory.isEmpty()) {
            chargeCapacitors = false;
            updateNextTick();
            return;
        }
        ItemStack itemStack = inventory.getItem(0);

        if (getRecipe(itemStack).isPresent()) {



            chargeCapacitors = true;
            updateNextTick();
            if (capacitorPercentage >= 200) {
                performRecipe(getRecipe(itemStack).get().value());
            }
        } else {
            chargeCapacitors = false;
            updateNextTick();
        }

    }




    @Override
    public float resistance() {
        return chargeCapacitors ? 30 : 0;
    }

    //
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        TFMGTexts.Multimeter.charge(capacitorPercentage/2f).forGoggles(tooltip);
        if(getPowerUsage()<2000&&!inventory.isEmpty()){
            TFMGTexts.Multimeter.notEnoughPower(2000).forGoggles(tooltip, 1);
            return true;
        }
        TFMGTexts.header("polarizer").style(ChatFormatting.GRAY).forGoggles(tooltip, 1);



        return true;
    }


    @Override
    public void inspect(InspectionReport report) {
        report.info("polarizer.face", Component.translatable("tfmg.inspector.dir." + getBlockState().getValue(FACING).getOpposite().getName()));
        ItemStack stack = inventory.getStackInSlot(0);
        if (stack.isEmpty()) {
            report.info("polarizer.empty");
            report.fix("polarizer.empty.fix");
            return;
        }
        if (getRecipe(stack).isEmpty()) {
            report.problem("polarizer.no_recipe", stack.getHoverName());
            report.fix("polarizer.no_recipe.fix");
            return;
        }
        report.ok("polarizer.recipe", stack.getHoverName());
        report.check(getPowerUsage() >= 2000, "polarizer.power_ok", "polarizer.power_low", "polarizer.power_low.fix", getPowerUsage(), 2000);
        report.info("polarizer.charge", capacitorPercentage / 2);
    }

    @Override
    public void tick() {
        super.tick();


        if (level.isClientSide) {
            angle.chase(180 * (capacitorPercentage / 200f), 0.2f, LerpedFloat.Chaser.EXP);
            angle.tickChaser();
        }


            if (getPowerUsage() >= 2000) {
                if (chargeCapacitors) {
                    if (capacitorPercentage < 200) {
                        capacitorPercentage++;
                    } else onInventoryChanged(inventory.getStackInSlot(0).getCount());
                }
            }

    }

    @Override
    public void destroy() {
        super.destroy();
        // The one slot was never dropped, so breaking a polarizer deleted the
        // item sitting in it.
        ItemHelper.dropContents(level, worldPosition, inventory);
    }

    public void performRecipe(PolarizingRecipe recipe) {
        // A datapack recipe declaring no item result would throw out of the
        // machine tick rather than simply not matching.
        if (recipe.getRollableResults().isEmpty())
            return;
        // Both sides reach this - tick() charges the capacitors on the client
        // too, so the gauge keeps animating between syncs - but each side may
        // only do its own half. The client used to craft into its local copy of
        // the inventory, and with a rolled output it did not even pick the same
        // item the server did, so the result flickered until the next sync
        // corrected it. The casting basin, firebox and winding machine were
        // taken off the client for exactly this; the polarizer was missed.
        //
        // The particles stay unguarded on purpose: addParticle does nothing on
        // a server level, so moving them behind the guard would silence them.
        // Reset the charge BEFORE writing the result: the write re-enters
        // onInventoryChanged, which with the gauge still full ran the next
        // recipe of a chain at once, several steps in a single tick.
        capacitorPercentage = 0;
        if (level != null && !level.isClientSide) {
            ItemStack stack = recipe.getRollableResults().get(0).rollOutput(level.random);
            inventory.setStackInSlot(0, stack);
        }
        TFMGUtils.spawnElectricParticles(level, getBlockPos());
    }

    public Optional<RecipeHolder<PolarizingRecipe>> getRecipe(ItemStack item) {
        if (!hasLevel())
            return Optional.empty();
        Optional<RecipeHolder<PolarizingRecipe>> assemblyRecipe = SequencedAssemblyRecipe.getRecipe(this.level, item, TFMGRecipeTypes.POLARIZING.getType(), PolarizingRecipe.class);
        if (assemblyRecipe.isPresent()) {
            return assemblyRecipe;
        } else {
            //  inventory.setItem(0, item);
            return TFMGRecipeTypes.POLARIZING.find(new RecipeWrapper(inventory), this.level);
        }
    }

    public int getItemChargingRate() {
        return TFMGConfigs.common().machines.polarizerItemChargingRate.get();
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries , clientPacket);
        compound.put("Inventory", inventory.serializeNBT(registries));
        compound.putInt("CapacitorPercentage", capacitorPercentage);
        compound.putBoolean("ChargeCapacitors", chargeCapacitors);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);
        inventory.deserializeNBT(registries,compound.getCompound("Inventory"));
        capacitorPercentage = compound.getInt("CapacitorPercentage");
        chargeCapacitors = compound.getBoolean("ChargeCapacitors");
    }

}
