package com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.hammer;


import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.base.PumpjackBaseBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.crank.PumpjackCrankBlockEntity;
import com.drmangotea.tfmg.registry.TFMGTags;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.IBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.List;

import static com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.hammer.PumpjackBlock.WIDE;
import static net.minecraft.world.level.block.DirectionalBlock.FACING;

public class PumpjackBlockEntity extends GeneratingKineticBlockEntity
        implements IBearingBlockEntity, IDisplayAssemblyExceptions, IInspectable {
    protected ControlledContraptionEntity movedContraption;
    protected float angle;
    protected boolean running;
    protected boolean assembleNextTick;
    protected float clientAngleDiff;
    protected AssemblyException lastException;
    protected double sequencedAngleLimit;
    private float prevAngle;
    public BlockPos headPosition = null;
    public BlockPos connectorPosition = null;
    public PumpjackCrankBlockEntity crank = null;
    public PumpjackBaseBlockEntity base = null;
    public int connectorDistance = 0;
    public int headDistance = 0;
    public boolean connectorAtFront = false;
    public boolean headAtFront = false;
    public int crankConnectorDistance = 0;
    public int headBaseDistance = 0;
    private int findScanCooldown = 0;
    private int refScanCooldown = 0;

    public PumpjackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(3);
        sequencedAngleLimit = -1;
    }

    @Override
    public boolean isWoodenTop() {
        return false;
    }

    @Override
    protected boolean syncSequenceContext() {
        return true;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().inflate(7);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        registerAwardables(behaviours, AllAdvancements.CONTRAPTION_ACTORS);
    }

    @Override
    public void remove() {
        if (!level.isClientSide)
            disassemble();
        super.remove();
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (connectorPosition != null) {
            compound.putInt("connectorX", connectorPosition.getX());
            compound.putInt("connectorY", connectorPosition.getY());
            compound.putInt("connectorZ", connectorPosition.getZ());
        }
        if (headPosition != null) {
            compound.putInt("headX", headPosition.getX());
            compound.putInt("headY", headPosition.getY());
            compound.putInt("headZ", headPosition.getZ());
        }
        compound.putBoolean("connectorAtFront", connectorAtFront);
        compound.putBoolean("headAtFront", headAtFront);
        compound.putBoolean("Running", running);
        compound.putFloat("Angle", angle);
        if (sequencedAngleLimit >= 0)
            compound.putDouble("SequencedAngleLimit", sequencedAngleLimit);
        AssemblyException.write(compound,registries, lastException);
        super.write(compound,registries , clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (wasMoved) {
            super.read(compound,registries , clientPacket);
            return;
        }
        if (compound.contains("connectorX")) {
            connectorPosition = new BlockPos(
                    compound.getInt("connectorX"),
                    compound.getInt("connectorY"),
                    compound.getInt("connectorZ")
            );
        } else
            connectorPosition = null;
        if (compound.contains("headX")) {
            headPosition = new BlockPos(
                    compound.getInt("headX"),
                    compound.getInt("headY"),
                    compound.getInt("headZ")
            );
        } else
            headPosition = null;
        connectorAtFront = compound.getBoolean("connectorAtFront");
        headAtFront = compound.getBoolean("headAtFront");
        float angleBefore = angle;
        running = compound.getBoolean("Running");
        angle = compound.getFloat("Angle");
        sequencedAngleLimit = compound.contains("SequencedAngleLimit") ? compound.getDouble("SequencedAngleLimit") : -1;
        lastException = AssemblyException.read(compound,registries);
        super.read(compound,registries , clientPacket);
        if (!clientPacket)
            return;
        if (running) {
            if (movedContraption == null || !movedContraption.isStalled()) {
                clientAngleDiff = AngleHelper.getShortestAngleDiff(angleBefore, angle);
                angle = angleBefore;
            }
        } else
            movedContraption = null;
    }

    @Override
    public float getInterpolatedAngle(float partialTicks) {
        if (isVirtual())
            return Mth.lerp(partialTicks + .5f, prevAngle, angle);
        if (movedContraption == null || movedContraption.isStalled() || !running)
            partialTicks = 0;
        float angularSpeed = getAngularSpeed();
        if (sequencedAngleLimit >= 0)
            angularSpeed = (float) Mth.clamp(angularSpeed, -sequencedAngleLimit, sequencedAngleLimit);
        return Mth.lerp(partialTicks, angle, angle + angularSpeed);
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);
        assembleNextTick = true;
        sequencedAngleLimit = -1;
        if (movedContraption != null && Math.signum(prevSpeed) != Math.signum(getSpeed()) && prevSpeed != 0) {
            if (!movedContraption.isStalled()) {
                angle = Math.round(angle);
                applyRotation();
            }
            movedContraption.getContraption()
                    .stop(level);
        }
        if (sequenceContext != null
                && sequenceContext.instruction() == SequencerInstructions.TURN_ANGLE)
            sequencedAngleLimit = sequenceContext.getEffectiveValue(getTheoreticalSpeed());
    }

    public float getAngularSpeed() {
        float speed = convertToAngular(getSpeed());
        if (getSpeed() == 0)
            speed = 0;
        if (level.isClientSide) {
            speed *= ServerSpeedProvider.get();
            speed += clientAngleDiff / 3f;
        }
        return speed;
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }

    @Override
    public BlockPos getBlockPosition() {
        return worldPosition;
    }

    public void assemble() {
        if (!(level.getBlockState(worldPosition)
                .getBlock() instanceof BearingBlock))
            return;
        Direction direction = getBlockState().getValue(BearingBlock.FACING);
        PumpjackContraption contraption = new PumpjackContraption(direction);
        try {
            if (!contraption.assemble(level, worldPosition))
                return;
            if (connectorPosition == null || headPosition == null)
                return;
            lastException = null;
        } catch (AssemblyException e) {
            lastException = e;
            sendData();
            return;
        }
        int q = 1;
        if (direction.getAxis() == Direction.Axis.X)
            q = -1;
        boolean canAssemble = true;
        boolean foundHead = false;
        boolean foundConnector = false;
        BlockPos headLocalPos = headPosition.subtract(getBlockPos().above());
        for (StructureTemplate.StructureBlockInfo block : contraption.getBlocks().values()) {
            if (block.state().is(TFMGTags.TFMGBlockTags.PUMPJACK_HEAD.tag)) {
                foundHead = true;
                if (block.pos().getX() != headLocalPos.getX() ||
                        block.pos().getY() != q * headLocalPos.getY() ||
                        block.pos().getZ() != q * headLocalPos.getZ())
                    canAssemble = false;

            }
        }
        BlockPos connectorLocalPos = connectorPosition.subtract(getBlockPos().above());
        for (StructureTemplate.StructureBlockInfo block : contraption.getBlocks().values()) {
            if (block.state().is(TFMGTags.TFMGBlockTags.PUMPJACK_CONNECTOR.tag)) {
                foundConnector = true;
                if (block.pos().getX() != connectorLocalPos.getX() ||
                        block.pos().getY() != q * connectorLocalPos.getY() ||
                        block.pos().getZ() != q * connectorLocalPos.getZ())
                    canAssemble = false;
            }
        }
        if (!canAssemble || !foundHead || !foundConnector)
            return;
        if (base.controllerHammer != this && base.controllerHammer != null)
            return;
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        movedContraption = ControlledContraptionEntity.create(level, this, contraption);
        BlockPos anchor = worldPosition.above();
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        movedContraption.setRotationAxis(direction.getClockWise().getAxis());
        level.addFreshEntity(movedContraption);
        AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playOnServer(level, worldPosition);
        if (contraption.containsBlockBreakers())
            award(AllAdvancements.CONTRAPTION_ACTORS);
        running = true;
        angle = 0;
        sendData();
        updateGeneratedRotation();
    }

    public boolean isHead(BlockPos pos) {
        return level.getBlockState(pos).is(TFMGTags.TFMGBlockTags.PUMPJACK_HEAD.tag);
    }

    public boolean isPart(BlockPos pos) {
        return level.getBlockState(pos).is(TFMGTags.TFMGBlockTags.PUMPJACK_PART.tag);
    }

    public boolean isConnector(BlockPos pos) {
        return level.getBlockState(pos).is(TFMGTags.TFMGBlockTags.PUMPJACK_CONNECTOR.tag);
    }

    private boolean findHeadAndConnector() {
        Direction direction = getBlockState().getValue(FACING);
        BlockPos checkedPos = this.getBlockPos().above();
        if (!areChunksLoadedForScan(direction))
            return connectorPosition != null && headPosition != null;
        BlockPos prevConnector = connectorPosition;
        BlockPos prevHead = headPosition;
        boolean prevConnectorAtFront = connectorAtFront;
        boolean prevHeadAtFront = headAtFront;
        connectorPosition = null;
        headPosition = null;
        boolean foundBoth = scanDirection(checkedPos, direction, true);
        if (!foundBoth) {
            checkedPos = this.getBlockPos().above();
            foundBoth = scanDirection(checkedPos, direction.getOpposite(), false);
        }
        boolean changed = !java.util.Objects.equals(prevConnector, connectorPosition)
                || !java.util.Objects.equals(prevHead, headPosition)
                || prevConnectorAtFront != connectorAtFront
                || prevHeadAtFront != headAtFront;
        if (changed)
            sendData();
        return foundBoth;
    }

    private boolean scanDirection(BlockPos checkedPos, Direction direction, boolean front) {
        for (int i = 0; i < 7; i++) {
            if (connectorPosition != null && headPosition != null)
                return true;
            if (i != 0 && isHead(checkedPos)) {
                headPosition = checkedPos;
                headAtFront = front;
                checkedPos = checkedPos.relative(direction);
                continue;
            }
            if (i != 0 && isConnector(checkedPos)) {
                if (level.getBlockState(checkedPos).getValue(HorizontalDirectionalBlock.FACING).getAxis() == this.getBlockState().getValue(FACING).getAxis()) {
                    connectorPosition = checkedPos;
                    connectorAtFront = front;
                    checkedPos = checkedPos.relative(direction);
                    continue;
                }
            }
            if (!isPart(checkedPos)) {
                return connectorPosition != null && headPosition != null;
            }
            if (level.getBlockState(checkedPos).getValue(HorizontalDirectionalBlock.FACING).getAxis() != this.getBlockState().getValue(FACING).getAxis()) {
                return connectorPosition != null && headPosition != null;
            }
            checkedPos = checkedPos.relative(direction);
        }
        return connectorPosition != null && headPosition != null;
    }

    public void disassemble() {
        if (!running && movedContraption == null)
            return;
        connectorDistance = 0;
        headDistance = 0;
        angle = 0;
        sequencedAngleLimit = -1;
        if (movedContraption != null) {
            movedContraption.disassemble();
            AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playOnServer(level, worldPosition);
        }
        movedContraption = null;
        running = false;
        updateGeneratedRotation();
        assembleNextTick = false;
        sendData();
    }

    @Override
    public void tick() {
        super.tick();

        // Resolve the crank and base FIRST. Neither reference is persisted, so
        // on the first tick after a chunk reload both are null even though the
        // structure is perfectly intact — while "running" IS persisted. The
        // completeness check further down then saw an incomplete machine and
        // called disassemble(), whose own guard (!running) let it straight
        // through, tearing down a working pumpjack: connectorDistance and angle
        // reset to 0 and running set to false. The hammer hung in the air
        // exactly as it does before the crank is installed, the crank kept
        // spinning on its own kinetics, and the base kept pumping because it
        // reads the crank's speed rather than the hammer's state. Stopping and
        // restarting rotation could not recover it either, since nothing
        // re-triggers assembly once running is false.
        //
        // Compare identity, not just type. After a chunk reload getBlockEntity
        // hands back a NEW instance at the same position, so an instanceof test
        // still saw "a crank is there" and kept the dead reference this field
        // held: the hammer then wrote crankRadius and read heightModifier on a
        // removed block entity, which goes nowhere. PumpjackBaseBlockEntity
        // already validates its own cached hammer this way.
        if (crank != null && (crank.isRemoved()
                || (level.isLoaded(crank.getBlockPos()) && level.getBlockEntity(crank.getBlockPos()) != crank)))
            crank = null;
        if (base != null && (base.isRemoved()
                || (level.isLoaded(base.getBlockPos()) && level.getBlockEntity(base.getBlockPos()) != base)))
            base = null;
        boolean rescan = refScanCooldown <= 0;
        if (rescan)
            refScanCooldown = 10;
        else
            refScanCooldown--;
        if (connectorPosition != null && (crank == null || rescan))
            crank = findCrank();
        if (headPosition != null && (base == null || rescan))
            base = findBase();

        if (!isRunning()) {
            if (findScanCooldown <= 0) {
                findHeadAndConnector();
                findScanCooldown = 10;
            } else {
                findScanCooldown--;
            }
        } else {
            findScanCooldown = 0;
        }
        if (!isRunning() && isComplete()
                && !level.isClientSide
        ) {
            assemble();
        }
        if (base != null)
            if (base.controllerHammer == null) {
                if (isRunning())
                    base.setControllerHammer(this);
            }
        if (!isComplete())
            if (!level.isClientSide)
                disassemble();

        setHolderSize();
        Direction direction = getBlockState().getValue(BearingBlock.FACING);
        if (connectorPosition != null) {
            if (direction.getAxis() == Direction.Axis.Z)
                connectorDistance = Math.abs(getBlockPos().getZ() - connectorPosition.getZ());

            if (direction.getAxis() == Direction.Axis.X)
                connectorDistance = Math.abs(getBlockPos().getX() - connectorPosition.getX());

            if (crank != null) {
                crankConnectorDistance = Math.abs(crank.getBlockPos().getY() - connectorPosition.getY());
                crank.crankRadius = (float) connectorDistance / 5;
            }
        }
        if (headPosition != null) {
            if (direction.getAxis() == Direction.Axis.Z)
                headDistance = Math.abs(getBlockPos().getZ() - headPosition.getZ());
            if (direction.getAxis() == Direction.Axis.X)
                headDistance = Math.abs(getBlockPos().getX() - headPosition.getX());
            if (base != null) {
                headBaseDistance = Math.abs(base.getBlockPos().getY() - headPosition.getY());
            }
        }
        prevAngle = angle;
        if (level.isClientSide)
            clientAngleDiff /= 2;
        if (
                !level.isClientSide &&
                        assembleNextTick) {
            assembleNextTick = false;
            if (running) {
            } else {
                assemble();
            }
        }
        if (!running)
            return;
        if (!(movedContraption != null && movedContraption.isStalled())) {
            if (crank != null && connectorDistance > 0) {
                int x = 1;
                if (connectorAtFront)
                    x = -1;
                if (direction == Direction.SOUTH || direction == Direction.WEST) {
                    angle = (float) Math.toDegrees(Math.atan(crank.heightModifier * x / connectorDistance));

                } else angle = (float) Math.toDegrees(Math.atan(-crank.heightModifier * x / connectorDistance));
            }
        }
        applyRotation();
    }

    public void setHolderSize() {
        if (isRunning())
            return;
        if (level.isClientSide)
            return;


        if (!level.getBlockState(getBlockPos().above()).is(TFMGTags.TFMGBlockTags.PUMPJACK_SMALL_PART.tag) && !getBlockState().getValue(WIDE))
            level.setBlock(getBlockPos(), getBlockState().setValue(WIDE, true), 2);

        if (level.getBlockState(getBlockPos().above()).is(TFMGTags.TFMGBlockTags.PUMPJACK_SMALL_PART.tag) && getBlockState().getValue(WIDE))
            level.setBlock(getBlockPos(), getBlockState().setValue(WIDE, false), 2);
    }

    public boolean isComplete() {
        return base != null && crank != null;
    }

    private PumpjackCrankBlockEntity findCrank() {
        BlockPos checkedPos = connectorPosition.below();
        for (int i = 0; i < 7; i++) {

            if (!level.isLoaded(checkedPos))
                return crank;
            if (level.getBlockEntity(checkedPos) instanceof PumpjackCrankBlockEntity)
                if (level.getBlockState(checkedPos).getValue(HorizontalDirectionalBlock.FACING).getAxis() == this.getBlockState().getValue(FACING).getAxis())
                    return (PumpjackCrankBlockEntity) level.getBlockEntity(checkedPos);
            checkedPos = checkedPos.below();
        }
        return null;
    }

    private PumpjackBaseBlockEntity findBase() {
        BlockPos checkedPos = headPosition.below();
        for (int i = 0; i < 8; i++) {

            if (!level.isLoaded(checkedPos))
                return base;
            if (level.getBlockEntity(checkedPos) instanceof PumpjackBaseBlockEntity)
                return (PumpjackBaseBlockEntity) level.getBlockEntity(checkedPos);

            checkedPos = checkedPos.below();
        }
        return null;
    }

    private boolean areChunksLoadedForScan(Direction direction) {
        BlockPos origin = getBlockPos().above();
        for (int i = 0; i < 7; i++) {
            if (!level.isLoaded(origin.relative(direction, i)))
                return false;
            if (!level.isLoaded(origin.relative(direction.getOpposite(), i)))
                return false;
        }
        return true;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (movedContraption != null && !level.isClientSide)
            sendData();
    }

    protected void applyRotation() {
        if (movedContraption == null)
            return;
        movedContraption.setAngle(angle);
        BlockState blockState = getBlockState();
        if (blockState.hasProperty(BlockStateProperties.FACING))
            movedContraption.setRotationAxis(blockState.getValue(BlockStateProperties.FACING).getClockWise()
                    .getAxis());
    }

    @Override
    public void attach(ControlledContraptionEntity contraption) {
        BlockState blockState = getBlockState();
        if (!(contraption.getContraption() instanceof PumpjackContraption))
            return;
        if (!blockState.hasProperty(BearingBlock.FACING))
            return;
        this.movedContraption = contraption;
        setChanged();
        BlockPos anchor = worldPosition.above();
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        if (!level.isClientSide) {
            this.running = true;
            sendData();
        }
    }

    @Override
    public void onStall() {
        if (!level.isClientSide)
            sendData();
    }

    @Override
    public boolean isValid() {
        return !isRemoved();
    }

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity contraption) {
        return movedContraption == contraption;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * The whole pumpjack, read from the hammer that ties it together: the
     * beam (head and connector), the crank under the connector, the base
     * under the head, then the well below the base.
     */
    @Override
    public void inspect(InspectionReport report) {
        if (level == null)
            return;
        if (headPosition == null || connectorPosition == null) {
            report.problem("pumpjack.no_beam");
            report.fix("pumpjack.no_beam.fix");
        } else {
            report.ok("pumpjack.beam", headDistance, connectorDistance);
        }

        PumpjackCrankBlockEntity liveCrank = crank != null && !crank.isRemoved() ? crank : null;
        PumpjackBaseBlockEntity liveBase = base != null && !base.isRemoved() ? base : null;
        if (connectorPosition != null) {
            if (liveCrank == null) {
                report.problem("pumpjack.no_crank", connectorPosition.getX(), connectorPosition.getY(), connectorPosition.getZ());
                report.fix("pumpjack.no_crank.fix");
            } else {
                liveCrank.inspectDrive(report);
            }
        }
        if (headPosition != null && liveBase == null) {
            report.problem("pumpjack.no_base", headPosition.getX(), headPosition.getY(), headPosition.getZ());
            report.fix("pumpjack.no_base.fix");
        }

        if (running) {
            report.ok("pumpjack.assembled");
        } else {
            if (lastException != null)
                report.problem("pumpjack.assembly_error", lastException.component);
            report.problem("pumpjack.not_assembled");
            // Everything found and still idle: the hammer only lifts what
            // Super Glue holds to the block above the holder, and the advice
            // below ("assembles once all three are found") did not say so.
            BlockPos loose = headPosition != null && connectorPosition != null && liveCrank != null && liveBase != null
                    ? firstUngluedBeamBlock() : null;
            if (loose != null) {
                report.problem("pumpjack.not_glued", loose.getX(), loose.getY(), loose.getZ());
                report.fix("pumpjack.not_glued.fix");
            } else {
                report.fix("pumpjack.not_assembled.fix");
            }
        }

        if (liveCrank != null && connectorDistance > 0) {
            int peak = (int) Math.abs(liveCrank.getMachineInputSpeed() * ((float) connectorDistance / 5));
            report.info("pumpjack.peak_rate", peak);
        }
        if (liveBase != null)
            liveBase.inspectWell(report);
    }

    /** The first beam block not glued to its neighbour on the way from the holder to the head or the connector. */
    private BlockPos firstUngluedBeamBlock() {
        java.util.Set<com.simibubi.create.content.contraptions.glue.SuperGlueEntity> cache = new java.util.HashSet<>();
        BlockPos anchor = getBlockPos().above();
        for (BlockPos end : new BlockPos[]{headPosition, connectorPosition}) {
            if (end.equals(anchor))
                continue;
            Direction step = Direction.getNearest(end.getX() - anchor.getX(), 0, end.getZ() - anchor.getZ());
            for (BlockPos pos = anchor; !pos.equals(end); pos = pos.relative(step)) {
                if (!level.isLoaded(pos.relative(step)))
                    return null;
                if (!com.simibubi.create.content.contraptions.glue.SuperGlueEntity.isGlued(level, pos, step, cache))
                    return pos.relative(step);
            }
        }
        return null;
    }

    public void setAngle(float forcedAngle) {
        angle = forcedAngle;
    }
}
