package com.drmangotea.tfmg.content.electricity.base;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ElectricBlockValues {


    public long electricalNetworkId;

    public boolean destroyed = false;

    public boolean connectNextTick = false;

    public boolean checkForLoopsNextTick = false;

    public boolean updatePowerNextTick = false;

    public boolean updateNextTick = false;

    public boolean getsOutsidePower = false;

    public int networkResistance = 0;

    public int voltage = 0;

    public int voltageSupply = 0;

    public int networkPowerGeneration = 0;

    public float highestCurrent = 0;

    public boolean notEnoughPower = false;

    public boolean setVoltageNextTick = false;

    public int failTimer = 0;

    // Game time of the last flood fill that reached this block (not saved).
    // A block a flood already absorbed this tick skips its own flood.
    public long floodedTick = Long.MIN_VALUE;

    public ElectricBlockValues(long pos) {
        this.electricalNetworkId = pos;
    }

    public long getId() {
        return electricalNetworkId;
    }

    public boolean destroyed() {
        return destroyed;
    }

    public int getVoltage() {
        return voltage;
    }
}
