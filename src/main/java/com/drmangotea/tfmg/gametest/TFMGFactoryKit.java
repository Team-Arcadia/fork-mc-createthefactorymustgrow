package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.content.items.inspector.FactoryInspectorItem;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Building blocks for game tests that run real factories: Create motors,
 * pumps, creative and plain fluid tanks, chests and hoppers, plus a Factory
 * Inspector run that records which report lines a machine produced.
 *
 * Positions are relative to the test structure, like every GameTestHelper call.
 *
 * @author vyrriox
 */
public final class TFMGFactoryKit {

    private TFMGFactoryKit() {
    }

    // ------------------------------------------------------------ rotation

    /** A creative motor at {@code pos} whose shaft points to {@code facing}. */
    public static void motor(GameTestHelper helper, BlockPos pos, Direction facing, int rpm) {
        helper.setBlock(pos, AllBlocks.CREATIVE_MOTOR.get().defaultBlockState().setValue(BlockStateProperties.FACING, facing));
        helper.runAfterDelay(2, () -> setMotor(helper, pos, rpm));
    }

    public static void setMotor(GameTestHelper helper, BlockPos pos, int rpm) {
        if (helper.getBlockEntity(pos) instanceof CreativeMotorBlockEntity motor)
            motor.generatedSpeed.setValue(rpm);
    }

    /**
     * A mechanical pump moving fluid from the block behind it into the block
     * it faces. Pumps are small cogs: a cogwheel on {@code cogSide} meshes with
     * it and a creative motor on the cogwheel's back end drives both.
     */
    public static void pump(GameTestHelper helper, BlockPos pos, Direction facing, Direction cogSide, int rpm) {
        pump(helper, pos, facing, cogSide, false, rpm);
    }

    /** As above; {@code motorInFront} puts the motor on the cogwheel's front end instead of its back end. */
    public static void pump(GameTestHelper helper, BlockPos pos, Direction facing, Direction cogSide, boolean motorInFront, int rpm) {
        helper.setBlock(pos, AllBlocks.MECHANICAL_PUMP.get().defaultBlockState().setValue(BlockStateProperties.FACING, facing));
        BlockPos cog = pos.relative(cogSide);
        helper.setBlock(cog, AllBlocks.COGWHEEL.get().defaultBlockState().setValue(BlockStateProperties.AXIS, facing.getAxis()));
        if (motorInFront)
            motor(helper, cog.relative(facing), facing.getOpposite(), rpm);
        else
            motor(helper, cog.relative(facing.getOpposite()), facing, rpm);
    }

    /**
     * Create fluid pipes along {@code path}. A pipe set by code keeps its
     * placement state, so each one recomputes its connections from its
     * neighbours once everything around it exists.
     */
    public static void pipes(GameTestHelper helper, BlockPos... path) {
        for (BlockPos pos : path)
            helper.setBlock(pos, AllBlocks.FLUID_PIPE.get().defaultBlockState());
        helper.runAfterDelay(1, () -> {
            for (BlockPos pos : path) {
                BlockPos abs = helper.absolutePos(pos);
                net.minecraft.world.level.block.state.BlockState state = helper.getLevel().getBlockState(abs);
                helper.getLevel().setBlock(abs, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(state, helper.getLevel(), abs), 3);
            }
        });
    }

    /** Sets the output of a TFMG creative generator, in tens of volts (scroll value). */
    public static void generatorVoltage(GameTestHelper helper, BlockPos pos, int scrollValue) {
        if (helper.getBlockEntity(pos) instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity be) {
            com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour value =
                    com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(be,
                            com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE);
            if (value != null)
                value.setValue(scrollValue);
        }
    }

    /** Replaces the fluid a creative tank supplies. */
    public static void setCreativeFluid(GameTestHelper helper, BlockPos pos, Fluid fluid) {
        if (helper.getBlockEntity(pos) instanceof CreativeFluidTankBlockEntity tank
                && tank.getTankInventory() instanceof CreativeFluidTankBlockEntity.CreativeSmartFluidTank creative)
            creative.setContainedFluid(new FluidStack(fluid, 1000));
    }

    /** A bare mechanical pump, for pumps driven by a neighbouring pump's cog. */
    public static void pumpBlock(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, AllBlocks.MECHANICAL_PUMP.get().defaultBlockState().setValue(BlockStateProperties.FACING, facing));
    }

    public static void cog(GameTestHelper helper, BlockPos pos, Direction.Axis axis) {
        helper.setBlock(pos, AllBlocks.COGWHEEL.get().defaultBlockState().setValue(BlockStateProperties.AXIS, axis));
    }

    // --------------------------------------------------------------- fluids

    /** A creative fluid tank holding an endless supply of {@code fluid}; it also swallows anything pumped into it. */
    public static void creativeTank(GameTestHelper helper, BlockPos pos, Fluid fluid) {
        helper.setBlock(pos, AllBlocks.CREATIVE_FLUID_TANK.get().defaultBlockState());
        if (fluid != null)
            helper.runAfterDelay(1, () -> setCreativeFluid(helper, pos, fluid));
    }

    /** A plain Create fluid tank, used to collect a product. */
    public static void tank(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, AllBlocks.FLUID_TANK.get().defaultBlockState());
    }

    public static IFluidHandler fluids(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), side);
    }

    /** Total amount of {@code fluid} a handler shows across all its tanks. */
    public static int amount(IFluidHandler handler, Fluid fluid) {
        if (handler == null)
            return 0;
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++)
            if (handler.getFluidInTank(i).getFluid().isSame(fluid))
                total += handler.getFluidInTank(i).getAmount();
        return total;
    }

    public static int amount(GameTestHelper helper, BlockPos pos, Fluid fluid) {
        return amount(fluids(helper, pos, null), fluid);
    }

    // ---------------------------------------------------------------- items

    /** A chest holding {@code stacks}, one per slot. */
    public static void chest(GameTestHelper helper, BlockPos pos, ItemStack... stacks) {
        helper.setBlock(pos, Blocks.CHEST.defaultBlockState());
        if (helper.getBlockEntity(pos) instanceof Container chest)
            for (int i = 0; i < stacks.length; i++)
                chest.setItem(i, stacks[i].copy());
    }

    /** A hopper pushing into the block on {@code facing} (never UP). */
    public static void hopper(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing));
    }

    public static IItemHandler items(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), side);
    }

    /** How many of {@code item} a container (chest, hopper) holds. */
    public static int count(GameTestHelper helper, BlockPos pos, Item item) {
        BlockEntity be = helper.getBlockEntity(pos);
        int total = 0;
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++)
                if (container.getItem(i).is(item))
                    total += container.getItem(i).getCount();
        }
        return total;
    }

    /** How many of {@code item} an item handler holds. */
    public static int count(IItemHandler handler, Item item) {
        if (handler == null)
            return 0;
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++)
            if (handler.getStackInSlot(i).is(item))
                total += handler.getStackInSlot(i).getCount();
        return total;
    }

    /** First stack of {@code item} in a container, or empty. */
    public static ItemStack first(GameTestHelper helper, BlockPos pos, Item item) {
        if (helper.getBlockEntity(pos) instanceof Container container)
            for (int i = 0; i < container.getContainerSize(); i++)
                if (container.getItem(i).is(item))
                    return container.getItem(i);
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------ inspector

    /** What one Factory Inspector click on a block reported. */
    public record Inspection(List<String> problems, List<String> keys, List<String> rawText, String text) {

        public boolean has(String key) {
            return keys.contains(key);
        }

        public boolean hasProblem(String key) {
            return problems.contains(key);
        }
    }

    /** Records the key of every line, so a test can ask what the inspector said rather than parse text. */
    private static final class RecordingReport extends InspectionReport {
        final List<String> problems = new ArrayList<>();
        final List<String> keys = new ArrayList<>();

        @Override
        public InspectionReport add(Kind kind, String key, Object... args) {
            keys.add(key);
            if (kind == Kind.PROBLEM)
                problems.add(key);
            return super.add(kind, key, args);
        }
    }

    /** Clicks the Factory Inspector on a block, as a player would. */
    public static Inspection inspect(GameTestHelper helper, BlockPos pos) {
        RecordingReport report = new RecordingReport();
        List<Component> lines = FactoryInspectorItem.inspect(helper.getLevel(), helper.absolutePos(pos), report);
        StringBuilder text = new StringBuilder();
        for (Component line : lines)
            text.append(line.getString()).append(" | ");
        return new Inspection(report.problems, report.keys,
                TFMGGameTestUtil.inspectionProblems(helper, helper.absolutePos(pos)), text.toString());
    }

    /**
     * Collects inspector problems seen while a machine runs. Each sample adds
     * the problem keys (and any raw untranslated text) reported for each
     * block; a clean run ends with an empty set and at least one sample.
     */
    public static final class RunningInspection {
        private final Set<String> problems = new LinkedHashSet<>();
        private int samples;

        public void sample(GameTestHelper helper, BlockPos... positions) {
            samples++;
            for (BlockPos pos : positions) {
                Inspection inspection = inspect(helper, pos);
                for (String problem : inspection.problems())
                    problems.add(pos.toShortString() + " " + problem + " [" + inspection.text() + "]");
                for (String raw : inspection.rawText())
                    problems.add(pos.toShortString() + " raw text: " + raw);
            }
        }

        public int samples() {
            return samples;
        }

        public void assertClean(GameTestHelper helper, String machine) {
            TFMGGameTestUtil.check(helper, samples > 0, machine + ": the inspector was never run while it worked");
            TFMGGameTestUtil.check(helper, problems.isEmpty(), machine + ": the inspector reported problems while it worked: " + problems);
        }
    }
}
