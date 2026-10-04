package com.drmangotea.tfmg.content.electricity.base;

import com.drmangotea.tfmg.base.blocks.TFMGHorizontalDirectionalBlock;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import static net.minecraft.world.level.block.DirectionalBlock.FACING;

public class VoltageAlteringBlockEntity extends ElectricBlockEntity implements IInspectable {

    public boolean updateInFront = false;

    public VoltageAlteringBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }


    public int getOutputVoltage() {
        return getData().getVoltage();
    }


    public int getOutputPower() {
        return getPowerUsage();
    }

    @Override
    public void tick() {
        super.tick();
        if (updateInFront) {
            updateInFront();
            updateInFront = false;
        }
    }

    public int getMaxPowerOutput(){
        return 10000;
    }

    @Override
    public int getMaxCurrent() {
        return 100;
    }

    /**
     * Re-entrancy guard: getPowerUsage probes the controlled network, whose
     * members' getPowerUsage can probe back through another bridge block
     * (two electric switches + a diode wired in a ring). getNetworkPowerUsage
     * only excludes the direct caller, so any cycle of 2+ bridges recursed
     * until StackOverflowError and took the server down on every world load.
     */
    protected boolean computingPowerUsage = false;

    @Override
    public int getPowerUsage() {
        if (computingPowerUsage)
            return 0; // feedback loop: contribute nothing instead of recursing
        Direction facing = getDirection();
        if (level.getBlockEntity(getBlockPos().relative(facing)) instanceof IElectric be && be.getData().getId() != data.getId()) {
            if (be.hasElectricitySlot(facing.getOpposite())) {
                computingPowerUsage = true;
                try {
                    return Math.max(be.getNetworkPowerUsage(this), 0);
                } finally {
                    computingPowerUsage = false;
                }
            }
        }

        return 0;

    }


    public IElectric getControlledBlock() {
        Direction facing = getBlockState().hasProperty(DirectionalBlock.FACING) ? getBlockState().getValue(DirectionalBlock.FACING) : getBlockState().getValue(HorizontalDirectionalBlock.FACING).getCounterClockWise();
        if (level.getBlockEntity(getBlockPos().relative(facing)) instanceof IElectric be && be.getData().getId() != data.getId()) {
            return be;
        }
        return null;
    }

    @Override
    public float resistance() {
        Direction facing = getDirection();
        if (level.getBlockEntity(getBlockPos().relative(facing)) instanceof IElectric be && be.getData().getId() != data.getId()) {
            if (be.hasElectricitySlot(facing.getOpposite())){
                int count = getBlocksConnectedToNetworkCount(getControlledBlock().getData().getId());
                if(count!=0)
                    return Math.max(be.getNetworkResistance()*count, 0);
            }
        }
        return 0;
    }

    public Direction getDirection(){
        if(!getBlockState().hasProperty(FACING)){
            return getBlockState().getValue(TFMGHorizontalDirectionalBlock.FACING).getCounterClockWise();
        }

        return getBlockState().getValue(FACING);
    }

    @Override
    public boolean hasElectricitySlot(Direction direction) {
        return getDirection().getOpposite() == direction;
    }

    @Override
    public void onNetworkChanged(int oldVoltage, int oldPower) {
        super.onNetworkChanged(oldVoltage, oldPower);

        if (oldVoltage != getData().getVoltage() || oldPower != getPowerUsage()) {
            updateInFront = true;
        }
        sendStuff();
        setChanged();
    }



    @Override
    public void remove() {
        super.remove();
    }

    @Override
    public void destroy() {
        super.destroy();
        updateInFront();
    }

    @Override
    public void onPlaced() {

        super.onPlaced();
        updateInFront = true;
    }

    /** Diode by default: one face in, the facing face out. Subclasses add their own checks. */
    @Override
    public void inspect(InspectionReport report) {
        inspectFaces(report);
    }

    protected void inspectFaces(InspectionReport report) {
        Direction output = getDirection();
        Direction input = null;
        for (Direction direction : Direction.values())
            if (hasElectricitySlot(direction))
                input = direction;
        if (input != null)
            report.info("bridge.faces", dirName(input), dirName(output));
        else
            report.info("bridge.output_face", dirName(output));
        BlockPos outPos = getBlockPos().relative(output);
        boolean outputConnected = level.isLoaded(outPos) && level.getBlockEntity(outPos) instanceof IElectric be
                && be.hasElectricitySlot(output.getOpposite());
        if (!outputConnected) {
            report.problem("bridge.no_output", dirName(output));
            report.fix("bridge.no_output.fix", dirName(output));
        } else {
            report.info("bridge.output_voltage", getOutputVoltage());
        }
    }

    protected static net.minecraft.network.chat.Component dirName(Direction direction) {
        return net.minecraft.network.chat.Component.translatable("tfmg.inspector.dir." + direction.getName());
    }

    public void updateInFrontNextTick(){
        updateInFront = true;
    }

    public void updateInFront() {

        if (level instanceof ServerLevel serverLevel)
            CatnipServices.NETWORK.sendToClientsTrackingChunk(serverLevel, new ChunkPos(worldPosition),new UpdateInFrontPacket(BlockPos.of(getPos())));
        Direction facing = getBlockState().hasProperty(FACING) ? getBlockState().getValue(FACING) : getBlockState().getValue(HorizontalDirectionalBlock.FACING).getCounterClockWise();
        if (level.getBlockEntity(getBlockPos().relative(facing)) instanceof IElectric be && be.getData().getId() != data.getId()) {
            if (be.hasElectricitySlot(facing.getOpposite())) {
                be.updateNextTick();

            }
        }
        sendStuff();
        setChanged();
    }
    public void updateBehind() {

        if (level instanceof ServerLevel serverLevel)
            CatnipServices.NETWORK.sendToClientsTrackingChunk(serverLevel, new ChunkPos(worldPosition),new UpdateInFrontPacket(BlockPos.of(getPos())));
        Direction facing = getBlockState().hasProperty(FACING) ? getBlockState().getValue(FACING) : getBlockState().getValue(HorizontalDirectionalBlock.FACING).getCounterClockWise();
        facing = facing.getOpposite();
        if (level.getBlockEntity(getBlockPos().relative(facing)) instanceof IElectric be && be.getData().getId() != data.getId()) {
            if (be.hasElectricitySlot(facing.getOpposite())) {
                be.updateNextTick();

            }
        }
        sendStuff();
        setChanged();
    }


}
