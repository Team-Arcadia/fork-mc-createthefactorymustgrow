package com.drmangotea.tfmg.content.machinery.misc.exhaust;



import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

@SuppressWarnings("removal")
public class ExhaustBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, IInspectable {

    protected IFluidHandler fluidCapability;
    public FluidTank tankInventory;

    public boolean spawnsSmoke=false;
    public int smokeTimer=0;

    /** Millibuckets the tank must move before another sync packet is worth it. */
    private static final int SYNC_STEP = 50;
    /** Amount at the last packet sent, -1 until the first one. Not persisted:
     *  a fresh block entity simply syncs on its first change. */
    private int lastSyncedAmount = -1;



    public ExhaustBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tankInventory = createInventory();
        fluidCapability = tankInventory;

    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                TFMGBlockEntities.EXHAUST.get(),
                (be, context) -> be.fluidCapability
        );
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

        return TFMGUtils.createFluidTooltip(this, tooltip);

    }


    @Override
    public void inspect(InspectionReport report) {
        int amount = tankInventory.getFluidAmount();
        int capacity = tankInventory.getCapacity();
        if (amount <= 0) {
            report.info("exhaust.idle");
            return;
        }
        // Same thresholds as tick(): fast once less than 300 mB of room is left.
        int rate = tankInventory.getSpace() < 300 ? 100 : 10;
        report.ok("exhaust.venting", rate, amount, capacity);
        if (tankInventory.getSpace() <= 0) {
            report.problem("exhaust.full");
            report.fix("exhaust.full.fix");
        }
    }

    protected SmartFluidTank createInventory() {
        return new SmartFluidTank(1000, this::onFluidStackChanged) {
            @Override
            public boolean isFluidValid(FluidStack stack) {
                return stack.getFluid().isSame(TFMGFluids.CARBON_DIOXIDE.getSource());
            }
        };
    }


    protected void onFluidStackChanged(FluidStack newFluidStack) {
        setChanged();

        // A venting exhaust drains every tick, so syncing on every change sent a
        // packet per tick to every client in range. The client needs exactly two
        // things from this tank: whether it holds anything, which decides if
        // smoke keeps spawning, and a readable amount for the goggles. Always
        // sync the empty/not-empty flip, otherwise only once the amount has
        // moved enough to be worth a packet.
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
        Direction direction = this.getBlockState().getValue(ExhaustBlock.FACING);

        if(smokeTimer!=0) {
            spawnsSmoke = true;
            smokeTimer--;
        }else spawnsSmoke = false;
        if (direction == Direction.UP)
            if(spawnsSmoke)
                makeParticles(level, this.getBlockPos(), 0);
        if (direction == Direction.DOWN)
            if(spawnsSmoke)
                makeParticles(level, this.getBlockPos(), 1);
        if (direction == Direction.NORTH)
            if(spawnsSmoke)
                makeParticles(level, this.getBlockPos(), 2);
        if (direction == Direction.SOUTH)
            if(spawnsSmoke)
                makeParticles(level, this.getBlockPos(), 3);
        if (direction == Direction.EAST)
            if(spawnsSmoke)
                makeParticles(level, this.getBlockPos(), 4);
        if (direction == Direction.WEST)
            if(spawnsSmoke)
                makeParticles(level, this.getBlockPos(), 5);
        // Arming the smoke timer stays on both sides: it is derived from the
        // tank, which is synced, and the client needs it to keep animating.
        if(tankInventory.getFluidAmount()>0) {

            smokeTimer = 100;
            spawnsSmoke = true;
        }

        // Venting the exhaust is server logic. Running it on the client too
        // emptied the client's own copy of the tank, so the amount shown by the
        // goggles drifted until the next sync packet corrected it.
        if (level == null || (level.isClientSide && !isVirtual()))
            return;

        // Vent FAST when the tank is backing up, slow when it is nearly empty.
        //
        // The test was the wrong way round: getSpace() is the room left, so
        // "space > 700" on a 1000 mB tank means it holds less than 300, and the
        // exhaust ran at 100 mB/t while empty and collapsed to 10 mB/t the
        // moment it had something to get rid of. An engine under load fills it
        // faster than that, and once the tank is full canWork() fails on
        // exhaustTank.getSpace() == 0 and the engine stalls with fuel still in
        // it. The smokestack next door has the same two rates with the
        // condition the right way round.
        if (tankInventory.getSpace() < 300) {
            tankInventory.drain(100, IFluidHandler.FluidAction.EXECUTE);
        } else tankInventory.drain(10, IFluidHandler.FluidAction.EXECUTE);






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

        // "Active" used to be written here and read nowhere, in every save and
        // every sync packet. The smoke timer is derived from the tank, which is
        // already synced, so the flag had no reader to gain.
    }

    public static void makeParticles(Level level, BlockPos pos, int particleRotation) {
        Random random = Create.RANDOM;
        int shouldSpawnSmoke = random.nextInt(7);
        if(shouldSpawnSmoke==0) {


            if(particleRotation==0)
                level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX()  +random.nextFloat(1), pos.getY() + 1, pos.getZ()  +random.nextFloat(1), 0.0D, 0.08D, 0.0D);
            if(particleRotation==1)
                level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX()  +random.nextFloat(1), pos.getY(), pos.getZ()  +random.nextFloat(1), 0.0D, 0.08D, 0.0D);

            if(particleRotation==2)
                level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX()  +random.nextFloat(1), pos.getY()  +random.nextFloat(1), pos.getZ(), 0.0D, 0.08D, 0.0D);
            if(particleRotation==3)
                level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX()  +random.nextFloat(1), pos.getY()  +random.nextFloat(1), pos.getZ() + 1, 0.0D, 0.08D, 0.0D);
            if(particleRotation==4)
                level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX() + 1, pos.getY()  +random.nextFloat(1), pos.getZ()  +random.nextFloat(1), 0.0D, 0.08D, 0.0D);
            if(particleRotation==5)
                level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX(), pos.getY()  +random.nextFloat(1), pos.getZ()  +random.nextFloat(1), 0.0D, 0.08D, 0.0D);



        }

    }

    //@Nonnull
    //@Override
    //public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
//
    //    if (cap == ForgeCapabilities.FLUID_HANDLER)
    //        return fluidCapability.cast();
    //    return super.getCapability(cap, side);
    //}



    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}



}

