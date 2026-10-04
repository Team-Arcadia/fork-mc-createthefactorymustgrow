package com.drmangotea.tfmg.content.machinery.misc.flarestack;


import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGTags;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;


import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;
import java.util.Random;

public class FlarestackBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, IInspectable {



    protected IFluidHandler fluidCapability;
    public FluidTank tankInventory;
    public boolean spawnsSmoke=false;
    public int smokeTimer=0;

    /** Millibuckets the tank must move before another sync packet is worth it. */
    private static final int SYNC_STEP = 250;
    /** Amount at the last packet sent, -1 until the first one. Not persisted:
     *  a fresh block entity simply syncs on its first change. */
    private int lastSyncedAmount = -1;


    public FlarestackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tankInventory = createInventory();
        fluidCapability = tankInventory;


    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                TFMGBlockEntities.FLARESTACK.get(),
                (be, context) -> be.fluidCapability
        );
    }
    @Override
    @SuppressWarnings("removal")
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

        return TFMGUtils.createFluidTooltip(this, tooltip);


    }

    @Override
    public void inspect(InspectionReport report) {
        int amount = tankInventory.getFluidAmount();
        if (amount <= 0) {
            report.info("flarestack.idle");
            return;
        }
        // Same thresholds as tick(): 100 mB a tick above 1000 mB, 30 below.
        int rate = amount > 1000 ? 100 : 30;
        report.ok("flarestack.burning", tankInventory.getFluid().getHoverName(), rate);
        report.info("flarestack.waste");
        if (tankInventory.getSpace() <= 0) {
            report.problem("flarestack.full", amount, tankInventory.getCapacity());
            report.fix("flarestack.full.fix");
        }
    }

    protected SmartFluidTank createInventory() {
        return new SmartFluidTank(2500, this::onFluidStackChanged) {
            @Override
            public boolean isFluidValid(FluidStack stack) {
                return stack.getFluid().is(TFMGTags.TFMGFluidTags.FLAMMABLE.tag)||
                       stack.getFluid().is(TFMGTags.TFMGFluidTags.FUEL.tag);
            }
        };
    }

    protected void onFluidStackChanged(FluidStack newFluidStack) {
        setChanged();

        // A burning flarestack drains every tick, so syncing on every change
        // sent a full block entity packet per tick to every client in range.
        // The client needs two things from this tank: whether it holds anything,
        // which decides if the flame keeps burning, and a readable amount for
        // the goggles. Always sync the empty/not-empty flip, otherwise only once
        // the amount has moved enough to be worth a packet.
        int amount = newFluidStack.getAmount();
        boolean emptinessFlipped = (amount == 0) != (lastSyncedAmount == 0);
        if (!emptinessFlipped && lastSyncedAmount >= 0 && Math.abs(amount - lastSyncedAmount) < SYNC_STEP)
            return;

        lastSyncedAmount = amount;
        sendData();
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null)
            return;

        if (smokeTimer != 0) {
            spawnsSmoke = true;
            smokeTimer--;
        } else {
            spawnsSmoke = false;
        }

        // The tank is synced, so both sides agree on whether there is anything
        // to burn and the flame keeps its timer topped up. Only the server may
        // burn it: the client used to drain its own copy at up to 100 mB a tick
        // and ran a 2500 mB tank dry in twenty-five ticks, so the goggle readout
        // collapsed to zero and jumped back on every sync.
        if (tankInventory.getFluidAmount() > 0) {
            smokeTimer = 100;
            spawnsSmoke = true;
        }

        if (level.isClientSide && !isVirtual()) {
            if (spawnsSmoke)
                makeParticles(level, this.getBlockPos());
            return;
        }

        setLit(spawnsSmoke);

        if (tankInventory.getFluidAmount() > 0) {
            if (tankInventory.getFluidAmount() > 1000) {
                tankInventory.drain(100, IFluidHandler.FluidAction.EXECUTE);
            } else tankInventory.drain(30, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    // Both branches called setBlock on every tick, whatever the state already
    // was: a block update packet to every player in range and a light engine
    // recalculation twenty times a second, for a value that changes twice a
    // burn. Only write it when it actually differs.
    private void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (!state.hasProperty(FlarestackBlock.LIT) || state.getValue(FlarestackBlock.LIT) == lit)
            return;
        level.setBlock(getBlockPos(), state.setValue(FlarestackBlock.LIT, lit), 2);
    }

    public static void makeParticles(Level level, BlockPos pos) {
        Random random = Create.RANDOM;
        int shouldSpawnSmoke = random.nextInt(7);
        if(shouldSpawnSmoke==0) {


            level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX()  +random.nextFloat(1), pos.getY() + 1, pos.getZ()  +random.nextFloat(1), 0.0D, 0.08D, 0.0D);
            level.addParticle(ParticleTypes.FLAME, pos.getX()  +random.nextFloat(1), pos.getY() + 1, pos.getZ()  +random.nextFloat(1), Create.RANDOM.nextDouble(0.28)-0.14D, 0.14D, Create.RANDOM.nextDouble(0.28)-0.14D);
            level.addParticle(ParticleTypes.FLAME, pos.getX()  +random.nextFloat(1), pos.getY() + 1, pos.getZ()  +random.nextFloat(1), Create.RANDOM.nextDouble(0.28)-0.14D, 0.14D, Create.RANDOM.nextDouble(0.28)-0.14D);
            level.addParticle(ParticleTypes.FLAME, pos.getX()  +random.nextFloat(1), pos.getY() + 1, pos.getZ()  +random.nextFloat(1), Create.RANDOM.nextDouble(0.28)-0.14D, 0.14D, Create.RANDOM.nextDouble(0.28)-0.14D);

        }

    }



    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);

        tankInventory.readFromNBT(registries,compound.getCompound("TankContent"));
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries , clientPacket);
        compound.put("TankContent", tankInventory.writeToNBT(registries,new CompoundTag()));



    }




    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

}

