package com.drmangotea.tfmg.content.machinery.misc.concrete_hose;

import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;
import net.createmod.catnip.animation.LerpedFloat;
import com.drmangotea.tfmg.content.decoration.concrete.ConcreteloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

public class ConcreteHoseBlockEntity extends KineticBlockEntity implements IInspectable {

    LerpedFloat offset;
    boolean isMoving;

    public SmartFluidTank internalTank;
    public IFluidHandler capability;
    public ConcreteFillingBehavior filler;
    public ConcreteHoseFluidHandler handler;
    public boolean infinite;

    public ConcreteHoseBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        offset = LerpedFloat.linear()
                .startWithValue(0);
        isMoving = true;
        internalTank = new SmartFluidTank(1500, this::onTankContentsChanged);
        handler = new ConcreteHoseFluidHandler(internalTank, filler,
                () -> worldPosition.below((int) Math.ceil(offset.getValue())), () -> !this.isMoving);
        capability = handler;
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                TFMGBlockEntities.CONCRETE_HOSE.get(),
                (be, context) -> {
                    if (context == null || HosePulleyBlock.hasPipeTowards(be.level, be.worldPosition, be.getBlockState(), context))
                        return be.handler;
                    return null;
                }
        );
    }
    @Override
    public void sendData() {
        infinite = filler.isInfinite();
        super.sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean addToGoggleTooltip = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (infinite)
            TooltipHelper.addHint(tooltip, "hint.hose_pulley");
        return addToGoggleTooltip;
    }
    @Override
    public void inspect(InspectionReport report) {
        if (level == null)
            return;
        FluidStack held = internalTank.getFluid();
        if (held.isEmpty()) {
            report.problem("concrete_hose.empty");
            report.fix("concrete_hose.empty.fix");
        } else if (!held.getFluid().isSame(TFMGFluids.LIQUID_CONCRETE.getSource())) {
            report.problem("concrete_hose.wrong_fluid", held.getHoverName());
        } else if (held.getAmount() < 1000) {
            report.problem("concrete_hose.low", held.getAmount());
            report.fix("concrete_hose.empty.fix");
        } else {
            report.ok("concrete_hose.concrete", held.getAmount());
        }

        if (!com.simibubi.create.infrastructure.config.AllConfigs.server().fluids.fluidFillPlaceFluidSourceBlocks.get()) {
            report.problem("concrete_hose.config");
            report.fix("concrete_hose.config.fix");
        }

        int length = (int) Math.ceil(offset.getValue());
        if (isMoving) {
            report.info("concrete_hose.moving", length);
            return;
        }
        report.ok("concrete_hose.extended", length);

        // The pour starts at the block the hose end hangs in and spreads only
        // through rebar (concretelogged blocks), never upwards.
        BlockPos root = worldPosition.below(length);
        int empty = 0;
        int filled = 0;
        for (Direction side : Direction.values()) {
            if (side == Direction.UP)
                continue;
            BlockPos pos = root.relative(side);
            if (!level.isLoaded(pos))
                continue;
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(ConcreteloggedBlock.CONCRETELOGGED))
                continue;
            if (state.getValue(ConcreteloggedBlock.CONCRETELOGGED))
                filled++;
            else
                empty++;
        }
        if (empty > 0)
            report.ok("concrete_hose.rebar", empty, root.getX(), root.getY(), root.getZ());
        else if (filled > 0)
            report.info("concrete_hose.rebar_filled");
        else {
            report.problem("concrete_hose.no_rebar", root.getX(), root.getY(), root.getZ());
            report.fix("concrete_hose.no_rebar.fix");
        }
    }

    public float getInterpolatedOffset(float pt) {
        return offset.getValue(pt);
    }
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        filler = new ConcreteFillingBehavior(this);
        behaviours.add(filler);
        super.addBehaviours(behaviours);
    }

    protected void onTankContentsChanged(FluidStack contents) {
        // A pour that only touched a neighbouring chunk left this one clean,
        // and the drained concrete came back on reload.
        setChanged();
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        isMoving = true;
        if (getSpeed() == 0) {
            offset.forceNextSync();
            offset.setValue(Math.round(offset.getValue()));
            isMoving = false;
        }

        if (isMoving) {
            float newOffset = offset.getValue() + getMovementSpeed();
            if (newOffset < 0)
                isMoving = false;
            if (!level.getBlockState(worldPosition.below((int) Math.ceil(newOffset)))
                    .canBeReplaced()) {
                isMoving = false;
            }
            if (isMoving) {
                filler.reset();
            }
        }

        super.onSpeedChanged(previousSpeed);
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().expandTowards(0, -offset.getValue(), 0);
    }

    @Override
    public void tick() {
        super.tick();
        float newOffset = offset.getValue() + getMovementSpeed();
        if (newOffset < 0) {
            newOffset = 0;
            isMoving = false;
        }
        if (!level.getBlockState(worldPosition.below((int) Math.ceil(newOffset)))
                .canBeReplaced()) {
            newOffset = (int) newOffset;
            isMoving = false;
        }
        if (getSpeed() == 0)
            isMoving = false;

        offset.setValue(newOffset);
        invalidateRenderBoundingBox();

        // Pour from our own tick rather than only as a side effect of an
        // incoming fill. A pipe stops pushing once the internal tank is full,
        // and the deposit used to run only from that push, so a hose that had
        // been lowered, filled and stopped just sat there holding its concrete.
        if (!level.isClientSide && !isMoving && internalTank.getFluidAmount() >= 1000) {
            FluidStack held = internalTank.getFluid();
            BlockPos root = worldPosition.below((int) Math.ceil(offset.getValue()));
            if (filler.tryDeposit(held.getFluid(), root, false)) {
                internalTank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
                sendData();
            }
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level.isClientSide)
            return;
        if (isMoving)
            return;

        int ceil = (int) Math.ceil(offset.getValue() + getMovementSpeed());
        if (getMovementSpeed() > 0 && level.getBlockState(worldPosition.below(ceil))
                .canBeReplaced()) {
            isMoving = true;
            filler.reset();
            return;
        }

        sendData();
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (clientPacket)
            offset.forceNextSync();
        compound.put("Offset", offset.writeNBT());
        compound.put("Tank", internalTank.writeToNBT(registries,new CompoundTag()));
        super.write(compound,registries , clientPacket);
        if (clientPacket)
            compound.putBoolean("Infinite", infinite);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        offset.readNBT(compound.getCompound("Offset"), clientPacket);
        internalTank.readFromNBT(registries,compound.getCompound("Tank"));
        super.read(compound,registries , clientPacket);
        if (clientPacket)
            infinite = compound.getBoolean("Infinite");
    }

    @Override
    public void invalidate() {
        super.invalidate();
        invalidateCapabilities();
    }

    public float getMovementSpeed() {
        float movementSpeed = convertToLinear(getSpeed());
        if (level.isClientSide)
            movementSpeed *= ServerSpeedProvider.get();
        return movementSpeed;
    }
   //@Override
   //public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
   //    if (isFluidHandlerCap(cap)
   //            && (side == null || HosePulleyBlock.hasPipeTowards(level, worldPosition, getBlockState(), side)))
   //        return this.capability.cast();
   //    return super.getCapability(cap, side);
   //}
}
