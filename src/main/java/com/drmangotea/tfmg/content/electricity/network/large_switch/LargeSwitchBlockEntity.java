package com.drmangotea.tfmg.content.electricity.network.large_switch;

import com.drmangotea.tfmg.base.lang.TFMGLang;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.content.electricity.base.KineticElectricBlockEntity;
import com.drmangotea.tfmg.content.electricity.base.UpdateInFrontPacket;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static com.drmangotea.tfmg.content.electricity.network.large_switch.LargeSwitchBlock.IS_MAIN_PART;
import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

public class LargeSwitchBlockEntity extends KineticElectricBlockEntity implements IInspectable {
    public boolean updateInFront = false;

    public boolean closed = false;

    public LerpedFloat visualAngle = LerpedFloat.angular();
    public float angle = 900;

    final boolean isMainPart;

    public LargeSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        isMainPart = state.getValue(IS_MAIN_PART);
        visualAngle.setValue(90);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putBoolean("Closed", closed);
        compound.putFloat("Angle", angle);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        closed = compound.getBoolean("Closed");
        if (compound.contains("Angle"))
            angle = compound.getFloat("Angle");
    }


    public int voltageGeneration() {

        if (isMainPart)
            return 0;

        int voltageGeneration = 0;


        if (getLevelAccessor().getBlockEntity(getBlockPos().relative(getBlockState().getValue(HORIZONTAL_FACING).getOpposite())) instanceof LargeSwitchBlockEntity be)
            if (be.getData().getId() != getData().getId())
                if (be.getData().getVoltage() != 0)
                    if (be.closed) {
                        voltageGeneration = Math.max(voltageGeneration, be.data.getVoltage());
                        getData().getsOutsidePower = true;
                    }

        if (voltageGeneration == 0)
            getData().getsOutsidePower = false;

        return voltageGeneration;
    }

    @Override
    public int getMaxCurrent() {
        return 1000;
    }

    @Override
    public int getMaxVoltage() {
        return 100000;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level.isClientSide || getBlockState().getValue(IS_MAIN_PART))
            return;
        if (level.getBlockEntity(getBlockPos().relative(getBlockState().getValue(HORIZONTAL_FACING).getOpposite())) instanceof LargeSwitchBlockEntity be) {
            if (be.getData().getId() == getData().getId())
                be.onPlaced();
            else if (data.notEnoughPower)
                be.updateNextTick();
        }
    }

    public IElectric getControlledBlock() {
        Direction facing = getBlockState().getValue(HORIZONTAL_FACING);
        if(level.getBlockEntity(getBlockPos().relative(facing))instanceof LargeSwitchBlockEntity be){
            return be;
        }
        return null;
    }
    @Override
    public float resistance() {
        if (!isMainPart)
            return 0;
        if(!closed)
            return 0;

        Direction facing = getBlockState().getValue(HORIZONTAL_FACING);
        if (level.getBlockEntity(getBlockPos().relative(facing)) instanceof IElectric be && be.getData().getId() != data.getId()) {
            int count = getBlocksConnectedToNetworkCount(getControlledBlock().getData().getId());
            if(count!=0)
                return Math.max(be.getNetworkResistance()*count, 0);
        }
        return 0;
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

    public int powerGeneration() {

        if (isMainPart)
            return 0;

        if(level.getBlockEntity(getBlockPos().relative(getBlockState().getValue(HORIZONTAL_FACING).getOpposite())) instanceof LargeSwitchBlockEntity be&&be.data.notEnoughPower)
            return 0;

        int powerGeneration = 0;


        if (getLevelAccessor().getBlockEntity(getBlockPos().relative(getBlockState().getValue(HORIZONTAL_FACING).getOpposite())) instanceof LargeSwitchBlockEntity be)
            if (be.getData().getId() != getData().getId())
                if (be.getData().getVoltage() != 0)
                    if (be.closed) {
                        int cachedGen = be.getData().networkPowerGeneration;
                        int available = cachedGen > 0 ? Math.min(10000, cachedGen) : 10000;
                        powerGeneration = Math.max(powerGeneration, available);
                        getData().getsOutsidePower = true;
                    }

        if (powerGeneration == 0)
            getData().getsOutsidePower = false;

        return powerGeneration;
    }

    public void updateInFront() {

        if (level instanceof ServerLevel serverLevel)
            CatnipServices.NETWORK.sendToClientsTrackingChunk(serverLevel, new ChunkPos(worldPosition), new UpdateInFrontPacket(BlockPos.of(getPos())));
        Direction facing = getBlockState().getValue(HORIZONTAL_FACING);
        if (level.getBlockEntity(getBlockPos().relative(facing)) instanceof IElectric be && be.getData().getId() != data.getId()) {
            be.updateNextTick();

        }
        sendStuff();
        setChanged();
    }


    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            visualAngle.chase(angle / 10d, 1d, LerpedFloat.Chaser.EXP);
            visualAngle.tickChaser();
        }
        if(updateInFront) {
            updateInFront();
            updateInFront = false;
        }
        if (!isMainPart)
            return;
        if (angle < 0)
            angle = 0;
        if (angle > 900)
            angle = 900;
        if (getSpeed() == 0)
            return;

        if (getSpeed() < 0 && angle != 900) {
            angle += getArmSpeed();
        }
        if (getSpeed() > 0 && angle != 0) {
            angle -= getArmSpeed();
        }

        boolean oldValue = closed;

        if (data.voltage < 1000) {
            closed = angle == 0;
        } else {
            closed = angle < data.voltage / 700f;
        }

        if (oldValue != closed)
            updateInFront();




    }

    @Override
    public boolean makeMultimeterTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.makeMultimeterTooltip(tooltip, isPlayerSneaking);



        return true;
    }

    @Override
    public void inspect(InspectionReport report) {
        Direction facing = getBlockState().getValue(HORIZONTAL_FACING);
        if (!isMainPart) {
            BlockPos mainPos = getBlockPos().relative(facing.getOpposite());
            if (level.isLoaded(mainPos) && level.getBlockEntity(mainPos) instanceof LargeSwitchBlockEntity main && main.isMainPart) {
                report.info("large_switch.output_part");
                main.inspect(report);
            } else {
                report.problem("large_switch.no_main");
            }
            return;
        }
        if (closed) {
            report.ok("large_switch.closed");
        } else {
            report.problem("large_switch.open");
            if (getSpeed() > 0)
                report.info("large_switch.closing");
            else if (getSpeed() < 0)
                report.fix("large_switch.open.fix_reverse");
            else
                report.fix("large_switch.open.fix");
        }
        report.info("large_switch.faces");
    }

    public float getArmSpeed() {
        return Math.abs(getSpeed()) * 0.3f;
    }


    @Override
    public boolean hasElectricitySlot(Direction direction) {

        return (direction == getBlockState().getValue(HORIZONTAL_FACING).getOpposite() && getBlockState().getValue(IS_MAIN_PART)) || (direction == getBlockState().getValue(HORIZONTAL_FACING) && !getBlockState().getValue(IS_MAIN_PART));

    }
}
