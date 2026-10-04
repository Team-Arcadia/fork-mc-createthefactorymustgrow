package com.drmangotea.tfmg.content.electricity.utilities.converter;

import com.drmangotea.tfmg.base.lang.TFMGLang;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.config.TFMGConfigs;
import com.drmangotea.tfmg.content.electricity.base.ElectricBlockEntity;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.content.electricity.base.IVoltageSource;
import com.drmangotea.tfmg.content.electricity.storage.AccumulatorBlockEntity;
import com.drmangotea.tfmg.content.electricity.storage.TFMGForgeEnergyStorage;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

import static com.drmangotea.tfmg.content.electricity.utilities.converter.ConverterBlock.INPUT;
import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;
import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class ConverterBlockEntity extends ElectricBlockEntity implements IVoltageSource {

    public final TFMGForgeEnergyStorage energy = createEnergyStorage();
    private IEnergyStorage energyCapability;


    public int timer = 0;

    protected ScrollValueBehaviour voltageGenerated;

    public ConverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        energyCapability = energy;
    }

    public TFMGForgeEnergyStorage createEnergyStorage() {
        return new TFMGForgeEnergyStorage(500000, 10000) {
            @Override
            public void onEnergyChanged(int amount, int oldAmount) {
                // Re-run the electrical network when the FE tank crosses one of
                // the thresholds the converter's output depends on (empty,
                // full) so FE->TFMG conversion publishes a voltage on the TFMG
                // side. Re-running it on every transfer recomputed the whole
                // grid each tick for as long as FE flowed.
                int max = getMaxEnergyStored();
                if ((oldAmount == 0) != (this.energy == 0) || (oldAmount >= max) != (this.energy >= max))
                    updateNextTick();
                sendStuff();
            }
        };

    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                TFMGBlockEntities.CONVERTER.get(),
                (be, context) -> be.energyCapability
        );
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        int max = 250;
        voltageGenerated = new ScrollValueBehaviour(TFMGLang.translateDirect("creative_generator.voltage_generation"),
                this, new ConverterValueBox());
        voltageGenerated.between(1, max);
        voltageGenerated.value = 20;
        voltageGenerated.withCallback(i -> this.updateNextTick());
        behaviours.add(voltageGenerated);

    }


    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries , clientPacket);
        compound.putInt("ForgeEnergy", energy.getEnergyStored());
        // Synced + persisted: the client mirror sim reads timer in
        // voltageGeneration(), and the recharge cooldown must survive
        // chunk reloads.
        compound.putInt("Timer", timer);
    }


    public boolean isInput() {
        return getBlockState().getValue(INPUT);
    }




    @Override
    public float resistance() {
        if (voltageGeneration() > 0)
            return 0;


        int power = 0;
        for (IElectric member : getOrCreateElectricNetwork().members)
            if (!(member instanceof ConverterBlockEntity) && !(member instanceof AccumulatorBlockEntity))
                power += member.getPowerUsage();
        if (energy.getEnergyStored() == getMaxCapacity() || getData().getVoltage() <= voltageGenerated.getValue() || canPower())
            return 0;
        if(Math.min(Math.max((data.networkPowerGeneration - power), 0), getMaxChargingRate())==0){
            return 0;
        }


        return (float) (data.voltage * data.voltage) /Math.min(Math.max((data.networkPowerGeneration - power), 0), getMaxChargingRate());
    }

    public boolean canPower() {
        if(timer!=0)
            return false;

        if (getBlockState().getValue(INPUT))
            return false;

        return getData().networkResistance > 0 && (getData().getVoltage() <= voltageGenerated.getValue()) && energy.getEnergyStored() > 0;
    }


    public int getChargingRate() {
        //
        // int chargingRate = Math.max((data.networkPowerGeneration - getNetworkPowerUsage()), 0);
        if (energy.getEnergyStored() == getMaxCapacity() || getData().getVoltage() < voltageGenerated.value || canPower()|| data.notEnoughPower)
            return 0;

        //return Math.min(chargingRate, getMaxChargingRate());
        return getMaxChargingRate();
    }

    @Override
    public int powerGeneration() {
        return voltageGeneration() > 0 ? 10000 : 0;
    }

    @Override
    public void tick() {
        super.tick();

        // FE transfer and the cooldown timer are server logic; the client
        // used to run them on its local copies, drifting the displayed FE
        // amount away from reality (energy itself syncs via write/read).
        if (level == null || level.isClientSide)
            return;

        if(timer>0){

            if(timer == 1)
                updateNextTick();

            timer--;
        }


        if (getBlockState().getValue(INPUT)) {
            if (getData().getVoltage() > TFMGConfigs.common().machines.accumulatorVoltage.get()) {
                energy.receiveEnergy((int) (getChargingRate() / TFMGConfigs.common().machines.FEtoWattTickConversionRate.get()), false);

            }
            // TFMG->FE: actively PUSH stored FE into adjacent energy consumers.
            // The converter only EXPOSES an IEnergyStorage capability, so a
            // passive energy cube (input mode) never received anything - only a
            // cable in PULL mode did. Push out of every face except the TFMG
            // (blue) slot so orange-side neighbours are filled without a puller.
            pushForgeEnergy();
        } else {
            // OUTPUT mode: expose the configured voltage on this BE so
            // neighbours that gate on getData().getVoltage() != 0 actually
            // see us as a power source.
            if (timer == 0 && energy.getEnergyStored() > 0) {
                int target = voltageGenerated.getValue();
                if (getData().voltage != target) {
                    getData().voltage = target;
                    sendStuff();
                }
            } else if (getData().voltage != 0) {
                getData().voltage = 0;
                sendStuff();
            }

            if (canPower()) {
                int energyToExtract = data.networkPowerGeneration == 0 ? getNetworkPowerUsage() : (int) Math.max(0, Math.max(((float) powerGeneration() / (float) data.networkPowerGeneration) * (float) getNetworkPowerUsage(), 0));
                energyToExtract /= TFMGConfigs.common().machines.FEtoWattTickConversionRate.get();
                energy.extractEnergy(Math.max(energyToExtract, 1), false);
                if (energy.getEnergyStored() == 0) {
                    timer = 100;
                    updateNextTick();
                }
            }
        }

    }

    @Override
    public boolean makeMultimeterTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.makeMultimeterTooltip(tooltip, isPlayerSneaking);

        // Surface the wrench-toggled mode — with no feedback, a converter
        // left in the wrong mode simply looked broken.
        //
        // Both lines used to name the blue side, which is only right in input
        // mode. hasElectricitySlot always returns FACING.getClockWise(), but the
        // blockstate swaps to block_rotated when input=true, so the colour
        // painted on that fixed physical side flips with the mode: blue for
        // TFMG → FE, orange for FE → TFMG. A tester wired FE to orange and a
        // heavy cable to blue in output mode, exactly as the tooltip said, and
        // watched 500k FE sit there converting nothing. Name both ports.
        TFMGLang.text(isInput() ? "Mode: TFMG → FE — TFMG cable on the BLUE side, FE machine on the ORANGE side"
                        : "Mode: FE → TFMG — FE source on the BLUE side, TFMG cable on the ORANGE side")
                .style(net.minecraft.ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        if (!isInput() && timer > 0)
            TFMGLang.text("Depleted — recharge cooldown: " + timer + "t")
                    .style(net.minecraft.ChatFormatting.RED)
                    .forGoggles(tooltip, 1);

        // Say WHY nothing is charging. In input mode getChargingRate also gates
        // on the scroll value, which is the output-voltage setting: a converter
        // left at a high setpoint in output mode and then wrenched to input
        // silently refuses to charge on a lower-voltage network, with no hint
        // that the dial is the reason.
        if (isInput() && getChargingRate() == 0) {
            String reason;
            if (energy.getEnergyStored() >= getMaxCapacity())
                reason = "Not charging: storage full";
            else if (getData().getVoltage() < voltageGenerated.getValue())
                reason = "Not charging: network at " + getData().getVoltage()
                        + " V, dial requires " + voltageGenerated.getValue() + " V";
            else if (data.notEnoughPower)
                reason = "Not charging: network has no spare power";
            else
                reason = "Not charging";
            TFMGLang.text(reason)
                    .style(net.minecraft.ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
        }

        TFMGTexts.electricalCapacity(energy.getEnergyStored()).forGoggles(tooltip, 1);
        TFMGTexts.chargingRate(getChargingRate()).forGoggles(tooltip, 1);
        TFMGTexts.electricalMaxCapacity(getMaxCapacity()).forGoggles(tooltip, 1);

        return true;
    }

    public int getMaxCapacity() {
        // The actual TFMGForgeEnergyStorage was created at 500_000 FE in
        // createEnergyStorage(), but this method used to return the
        // accumulatorStorage config (default 100_000). getChargingRate
        // gates on energy >= getMaxCapacity, so charging stopped at
        // 100 kFE even though the tank could physically hold 500 kFE.
        return energy.getMaxEnergyStored();
    }

    //in FE per tick
    public int getMaxChargingRate() {
        return TFMGConfigs.common().machines.accumulatorChargingRate.get()*10;
    }

    @Override
    public int voltageGeneration() {
        if (getBlockState().getValue(INPUT))
            return 0;
        if (timer != 0)
            return 0;
        if (energy.getEnergyStored() <= 0)
            return 0;
        return voltageGenerated.getValue();
    }

    @Override
    public int getOutputVoltage() {
        return voltageGeneration();
    }

    @Override
    public int getMaxPowerOutput() {
        return powerGeneration();
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);
        energy.setEnergy(compound.getInt("ForgeEnergy"));
        timer = compound.getInt("Timer");
    }

    private void pushForgeEnergy() {
        if (level == null || energy.getEnergyStored() <= 0)
            return;
        Direction tfmgSide = getBlockState().getValue(FACING).getClockWise();
        for (Direction d : Direction.values()) {
            if (d == tfmgSide)
                continue; // never push out of the TFMG (blue) electricity slot
            if (energy.getEnergyStored() <= 0)
                break;
            IEnergyStorage neighbour = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(d), d.getOpposite());
            if (neighbour == null || !neighbour.canReceive())
                continue;
            int simulated = neighbour.receiveEnergy(energy.getEnergyStored(), true);
            if (simulated <= 0)
                continue;
            int extracted = energy.extractEnergy(simulated, false);
            if (extracted > 0)
                neighbour.receiveEnergy(extracted, false);
        }
    }

    @Override
    public boolean hasElectricitySlot(Direction direction) {
        return direction == getBlockState().getValue(FACING).getClockWise();
    }
    public static class ConverterValueBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 3, 16.05);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction.getAxis() == state.getValue(HORIZONTAL_FACING).getAxis();
        }
    }
}
