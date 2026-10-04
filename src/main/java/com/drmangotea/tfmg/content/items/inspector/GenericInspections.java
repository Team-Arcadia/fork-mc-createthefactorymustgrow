package com.drmangotea.tfmg.content.items.inspector;

import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Checks every machine shares: rotation and stress for kinetic blocks, the
 * electrical readings for anything on a TFMG network, and the tanks. They run
 * after a machine's own {@link IInspectable#inspect}, so machine-specific
 * lines come first.
 *
 * @author vyrriox
 */
public final class GenericInspections {

    private static final int MAX_TANKS = 6;

    private GenericInspections() {
    }

    public static void kinetic(KineticBlockEntity be, InspectionReport report) {
        float speed = be.getSpeed();
        if (be instanceof IInspectable inspectable && !inspectable.wantsRotationCheck())
            return;
        // Sources (motors, engines, generators of rotation) explain their own
        // state in their report; "connect a shaft" would be wrong advice.
        if (be instanceof GeneratingKineticBlockEntity generator) {
            if (generator.getGeneratedSpeed() != 0)
                report.info("kinetic.generating", Math.abs((int) generator.getGeneratedSpeed()));
            else if (speed != 0)
                report.ok("kinetic.speed", Math.abs((int) speed));
            return;
        }
        if (speed == 0) {
            report.problem("kinetic.no_rotation");
            report.fix("kinetic.no_rotation.fix");
            return;
        }
        report.ok("kinetic.speed", Math.abs((int) speed));
        if (be.isOverStressed()) {
            report.problem("kinetic.overstressed");
            report.fix("kinetic.overstressed.fix");
        }
    }

    public static void electric(IElectric electric, InspectionReport report) {
        int voltage = electric.getData().getVoltage();
        int maxVoltage = electric.getMaxVoltage();
        float current = electric.getCurrent();
        int maxCurrent = electric.getMaxCurrent();
        int power = electric.powerGeneration(voltage);
        boolean generator = electric.voltageGeneration() > 0 || power > 0;
        if (generator)
            report.info("electric.generating", electric.voltageGeneration(), power);
        if (voltage <= 0 && !generator) {
            report.problem("electric.no_voltage");
            report.fix("electric.no_voltage.fix");
        } else {
            report.info("electric.reading", voltage, String.format("%.2f", current), electric.getPowerUsage());
        }
        if (electric.getData().notEnoughPower) {
            report.problem("electric.not_enough_power", electric.getNetworkPowerGeneration(), electric.getNetworkPowerUsage());
            report.fix("electric.not_enough_power.fix");
        }
        if (maxVoltage > 0 && voltage > maxVoltage) {
            report.problem("electric.overvoltage", voltage, maxVoltage);
            report.fix("electric.overvoltage.fix");
        } else if (maxVoltage > 0 && voltage > maxVoltage * 0.8f) {
            report.problem("electric.near_overvoltage", voltage, maxVoltage);
        }
        if (maxCurrent > 0 && maxCurrent < Integer.MAX_VALUE && current > maxCurrent * 0.8f) {
            report.problem("electric.near_overcurrent", String.format("%.1f", current), maxCurrent);
            report.fix("electric.overcurrent.fix");
        }
    }

    public static void tanks(BlockEntity be, InspectionReport report) {
        Level level = be.getLevel();
        if (level == null)
            return;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, (Direction) null);
        if (handler == null)
            return;
        for (int i = 0; i < handler.getTanks() && i < MAX_TANKS; i++) {
            FluidStack fluid = handler.getFluidInTank(i);
            if (fluid.isEmpty())
                continue;
            report.raw(InspectionReport.Kind.INFO, net.minecraft.network.chat.Component.translatable("tfmg.inspector.tank",
                    fluid.getHoverName(), fluid.getAmount(), handler.getTankCapacity(i)));
        }
    }
}
