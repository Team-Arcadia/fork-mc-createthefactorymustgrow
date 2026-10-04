package com.drmangotea.tfmg.content.electricity.storage;


import net.neoforged.neoforge.energy.EnergyStorage;

public abstract class TFMGForgeEnergyStorage extends EnergyStorage {

    public TFMGForgeEnergyStorage(int capacity, int maxTransfer) {
        super(capacity, maxTransfer);

    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int oldAmount = this.energy;

        int extractedEnergy = super.extractEnergy(maxExtract, simulate);
        // Simulated transfers change nothing. Notifying on them made every
        // neighbouring FE cable's probe recompute the electrical network and
        // send a sync packet, every tick.
        if(extractedEnergy != 0 && !simulate) {
            onEnergyChanged(-extractedEnergy,oldAmount);
        }
        return extractedEnergy;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int oldAmount = this.energy;
        int receiveEnergy = super.receiveEnergy(maxReceive, simulate);
        if(receiveEnergy != 0 && !simulate) {
            onEnergyChanged(receiveEnergy,oldAmount);
        }
        return receiveEnergy;
    }

    public int setEnergy(int energy) {
        int oldAmount = this.energy;
        // Clamp to [0, capacity]: NBT reads happen before the multiblock
        // rebuild resizes the storage, so an unclamped set could leave a
        // block holding more energy than it can store.
        this.energy = Math.max(0, Math.min(energy, getMaxEnergyStored()));

        if (this.energy != oldAmount)
            onEnergyChanged(this.energy, oldAmount);

        return 0;
    }

    public abstract void onEnergyChanged(int amount,int oldAmount);
}