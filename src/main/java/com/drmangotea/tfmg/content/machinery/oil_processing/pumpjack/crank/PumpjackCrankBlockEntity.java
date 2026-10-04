package com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.crank;

import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.content.machinery.misc.machine_input.MachineInputBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.hammer.PumpjackBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class PumpjackCrankBlockEntity extends KineticBlockEntity implements IInspectable {

    // Driven by the Machine Input below, not by a shaft: skip the inspector's
    // generic "not turning" check, the crank's own report covers its drive.
    @Override
    public boolean wantsRotationCheck() {
        return false;
    }

    public float angle = 0;

    public Direction direction;
    public float heightModifier = 0;
    public float crankRadius = 0.7f;

    public PumpjackCrankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void tick() {
        super.tick();
        direction = this.getBlockState().getValue(FACING);
        setAngle();
        heightModifier = (float) (crankRadius * Math.sin(Math.toRadians(angle)));
    }

    public float getMachineInputSpeed() {

        if (level.getBlockEntity(getBlockPos().below()) instanceof MachineInputBlockEntity)
            return ((MachineInputBlockEntity) level.getBlockEntity(getBlockPos().below())).getSpeed();
        return 0;
    }

    /** The crank's own requirement: a turning machine input right below it. */
    public void inspectDrive(InspectionReport report) {
        BlockPos below = getBlockPos().below();
        if (!(level.getBlockEntity(below) instanceof MachineInputBlockEntity input)) {
            report.problem("pumpjack.crank_no_input", below.getX(), below.getY(), below.getZ());
            report.fix("pumpjack.crank_no_input.fix");
        } else if (input.getSpeed() == 0) {
            report.problem("pumpjack.crank_input_still");
            report.fix("pumpjack.crank_input_still.fix");
        } else {
            report.ok("pumpjack.crank_driven", (int) Math.abs(input.getSpeed()));
        }
    }

    @Override
    public void inspect(InspectionReport report) {
        if (level == null)
            return;
        // The hammer holds the crank reference; look for it along the crank's
        // axis, up to the 7 blocks the hammer scans down from its connector.
        Direction.Axis axis = getBlockState().getValue(FACING).getAxis();
        Direction along = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        for (int dy = 0; dy <= 8; dy++) {
            for (int d = -7; d <= 7; d++) {
                BlockPos pos = getBlockPos().above(dy).relative(along, d);
                if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PumpjackBlockEntity hammer && hammer.crank == this) {
                    report.info("pumpjack.part_of", pos.getX(), pos.getY(), pos.getZ());
                    hammer.inspect(report);
                    return;
                }
            }
        }
        inspectDrive(report);
        report.problem("pumpjack.crank_unlinked");
        report.fix("pumpjack.crank_unlinked.fix");
    }

    private void setAngle() {
        if (level.getBlockEntity(getBlockPos().below()) instanceof MachineInputBlockEntity) {
            // Cap the MAGNITUDE: Math.min alone only capped positive speeds,
            // so reversed rotation (negative speed) spun the crank unbounded
            // and the pump ran faster than intended.
            float rawSpeed = getMachineInputSpeed() / 6;
            float speed_amogus = Math.signum(rawSpeed) * Math.min(Math.abs(rawSpeed), (float) 10);
            if (level.isClientSide) {
                float time = AnimationTickHolder.getRenderTime(getLevel());
                if (speed_amogus != 0) {
                    angle = (time * speed_amogus * 3 / 10f) % 360;
                } else angle = 180;
                return;
            }
            if (speed_amogus != 0) {
                angle = (angle + speed_amogus * 3 / 10f) % 360;
                setChanged();
            } else angle = 180;
        }
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("CrankAngle", angle);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (!clientPacket && compound.contains("CrankAngle"))
            angle = compound.getFloat("CrankAngle");
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
}
