package com.drmangotea.tfmg.content.machinery.misc.smokestack;

import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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

import static com.drmangotea.tfmg.content.machinery.misc.smokestack.SmokestackBlock.TOP;


public class SmokestackBlockEntity extends SmartBlockEntity implements IInspectable {


    int smokeTimer = 0;

    /** Millibuckets the tank must move before another sync packet is worth it. */
    private static final int SYNC_STEP = 100;
    /** Amount at the last packet sent, -1 until the first one. Not persisted:
     *  a fresh block entity simply syncs on its first change. */
    private int lastSyncedAmount = -1;


    public FluidTank tankInventory;

    protected IFluidHandler fluidCapability;

    public SmokestackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        tankInventory = new SmartFluidTank(8000, this::onFluidStackChanged) {
            @Override
            public boolean isFluidValid(FluidStack stack) {
                return stack.getFluid().isSame(TFMGFluids.CARBON_DIOXIDE.getSource());
            }
        };

        fluidCapability = tankInventory;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                TFMGBlockEntities.SMOKESTACK.get(),
                (be, context) -> be.fluidCapability
        );
    }

    //@Nonnull
    //@Override
    //@SuppressWarnings("removal")
    //public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction side) {
//
    //    if (cap == ForgeCapabilities.FLUID_HANDLER)
    //        return fluidCapability.cast();
    //    return super.getCapability(cap, side);
    //}

    @Override
    public void invalidate() {
        super.invalidate();

       invalidateCapabilities();
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);

        tankInventory.readFromNBT(registries,compound.getCompound("TankContent"));


    }

    protected void onFluidStackChanged(FluidStack newFluidStack) {
        if (!hasLevel())
            return;

        setChanged();

        // A running stack drains every tick, so syncing on every change sent a
        // packet per tick to every client in range. The client needs exactly two
        // things from this tank: whether it is empty, which decides if smoke
        // keeps spawning, and a readable amount for the goggles. Always sync the
        // empty/not-empty flip, otherwise only once the amount has moved enough
        // to be worth a packet.
        int amount = newFluidStack.getAmount();
        boolean emptinessFlipped = (amount == 0) != (lastSyncedAmount == 0);
        if (!emptinessFlipped && lastSyncedAmount >= 0 && Math.abs(amount - lastSyncedAmount) < SYNC_STEP)
            return;

        lastSyncedAmount = amount;
        sendData();
    }

    public static void makeParticles(Level level, BlockPos pos) {
        Random random = Create.RANDOM;
        int shouldSpawnSmoke = random.nextInt(7);
        if (shouldSpawnSmoke == 0) {

            level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX() + random.nextFloat(1), pos.getY() + 1, pos.getZ() + random.nextFloat(1), 0.0D, 0.08D, 0.0D);

        }

    }

    @Override
    public void tick() {
        super.tick();

        if (smokeTimer > 0) {

            makeParticles(level, getBlockPos());

            smokeTimer--;
        }


        if (tankInventory.isEmpty())
            return;

        // Arming the smoke timer stays on both sides: it is derived from the
        // tank, which is synced, and the client needs it to keep animating.
        if (getBlockState().getValue(TOP))
            smokeTimer = 40;

        // Draining the stack and pushing CO2 upward is server logic. Running it
        // on the client as well emptied the client's own copy of the tank, so
        // the level shown drifted away from reality until the next sync packet
        // happened to correct it.
        if (level == null || (level.isClientSide && !isVirtual()))
            return;

        if (getBlockState().getValue(TOP))
            tankInventory.drain(tankInventory.getSpace() < 1000 ? 50 : 10, IFluidHandler.FluidAction.EXECUTE);

        if (level.getBlockEntity(getBlockPos().above()) instanceof SmokestackBlockEntity be) {

            int transferAmount = Math.min(tankInventory.getFluidAmount(), be.tankInventory.getCapacity() - be.tankInventory.getFluidAmount());

            // Fill first and drain only what the block above took. Draining
            // first lost the gas whenever the fill was refused.
            if (transferAmount > 0) {
                int accepted = be.tankInventory.fill(tankInventory.getFluid().copyWithAmount(transferAmount), IFluidHandler.FluidAction.EXECUTE);
                if (accepted > 0)
                    tankInventory.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
            }

        }
    }

    @Override
    public void inspect(InspectionReport report) {
        if (level == null)
            return;
        // Gas climbs the column block by block and only the TOP block vents it.
        BlockPos top = getBlockPos();
        int height = 1;
        while (height < 64 && level.isLoaded(top.above()) && level.getBlockEntity(top.above()) instanceof SmokestackBlockEntity) {
            top = top.above();
            height++;
        }
        if (!(level.getBlockEntity(top) instanceof SmokestackBlockEntity topStack))
            return;
        if (!topStack.getBlockState().getValue(TOP)) {
            report.problem("smokestack.no_top", top.getX(), top.getY(), top.getZ());
            report.fix("smokestack.no_top.fix");
        } else {
            int amount = topStack.tankInventory.getFluidAmount();
            int rate = topStack.tankInventory.getSpace() < 1000 ? 50 : 10;
            if (amount <= 0)
                report.info("smokestack.idle");
            else
                report.ok("smokestack.venting", rate, top.getX(), top.getY(), top.getZ());
        }
        if (tankInventory.getSpace() <= 0) {
            report.problem("smokestack.full", tankInventory.getFluidAmount(), tankInventory.getCapacity());
            report.fix("smokestack.full.fix");
        }
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries , clientPacket);


        compound.put("TankContent", tankInventory.writeToNBT(registries,new CompoundTag()));

        // "Active" used to be written here and read nowhere, in every save and
        // every sync packet. The smoke timer is derived from the tank, which is
        // already synced, so the flag had no reader to gain.

    }


    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }
}
