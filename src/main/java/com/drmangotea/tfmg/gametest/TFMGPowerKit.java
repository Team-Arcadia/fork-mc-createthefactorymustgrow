package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.config.MachineConfig;
import com.drmangotea.tfmg.config.TFMGConfigs;
import com.drmangotea.tfmg.content.electricity.base.ElectricNetworkManager;
import com.drmangotea.tfmg.content.electricity.base.ElectricalNetwork;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.content.items.inspector.FactoryInspectorItem;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Building blocks for the power tests: sources, loads, wires and the readings
 * a player gets from a machine (block states, the Factory Inspector). Expected
 * values are computed from the same configs the mod reads, never hard-coded.
 *
 * @author vyrriox
 */
final class TFMGPowerKit {

    private static final String PROBLEM = "✖";
    private static final String OK = "✔";

    private TFMGPowerKit() {
    }

    static MachineConfig machines() {
        return TFMGConfigs.common().machines;
    }

    // ------------------------------------------------------------ placing

    /** The block's default state turned to face {@code facing}, whichever facing property it uses. */
    static BlockState facing(Block block, Direction facing) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.FACING))
            return state.setValue(BlockStateProperties.FACING, facing);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        return state;
    }

    static void place(GameTestHelper helper, BlockPos pos, Block block, Direction facing) {
        helper.setBlock(pos, facing(block, facing));
    }

    static void hub(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, TFMGBlocks.STEEL_CABLE_HUB.get().defaultBlockState());
    }

    static void hubs(GameTestHelper helper, BlockPos from, Direction direction, int count) {
        for (int i = 0; i < count; i++)
            hub(helper, from.relative(direction, i));
    }

    /** A creative generator set to {@code volts} (its dial moves in steps of 10 V). */
    static void creativeGenerator(GameTestHelper helper, BlockPos pos, int volts) {
        helper.setBlock(pos, TFMGBlocks.CREATIVE_GENERATOR.get().defaultBlockState());
        setCreativeVoltage(helper, pos, volts);
    }

    static void setCreativeVoltage(GameTestHelper helper, BlockPos pos, int volts) {
        scroll(helper, pos).setValue(volts / 10);
    }

    static ScrollValueBehaviour scroll(GameTestHelper helper, BlockPos pos) {
        BlockEntity be = helper.getBlockEntity(pos);
        if (!(be instanceof SmartBlockEntity smart) || smart.getBehaviour(ScrollValueBehaviour.TYPE) == null)
            throw new IllegalStateException("no value setting on the block at " + pos);
        return smart.getBehaviour(ScrollValueBehaviour.TYPE);
    }

    /**
     * A Create creative motor at {@code motor} turning a TFMG generator placed
     * next to it on {@code side}. Returns the generator's position.
     */
    static BlockPos motorDrivenGenerator(GameTestHelper helper, BlockPos motor, Direction side, int rpm) {
        BlockPos generator = motor.relative(side);
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, side));
        helper.setBlock(generator, facing(TFMGBlocks.GENERATOR.get(), side.getOpposite()));
        setMotorSpeed(helper, motor, rpm);
        return generator;
    }

    static void setMotorSpeed(GameTestHelper helper, BlockPos motor, int rpm) {
        CreativeMotorBlockEntity be = helper.getBlockEntity(motor);
        be.generatedSpeed.setValue(rpm);
    }

    // ----------------------------------------------------- expected values

    /** Voltage a generator makes at this speed, from the generator configs. */
    static int generatorVoltage(float rpm) {
        return Math.min(machines().generatorMaxVoltage.get(), generatorGeneration(rpm));
    }

    static int generatorPower(float rpm) {
        return generatorGeneration(rpm) * 40;
    }

    private static int generatorGeneration(float rpm) {
        return (int) Math.max(0, (Math.abs(rpm) - machines().generatorMinSpeed.getF()) * machines().generatorModifier.getF());
    }

    /** Speed an electric motor reaches on this voltage. */
    static float motorSpeed(int volts) {
        return Math.min(255, volts * .8f);
    }

    // ----------------------------------------------------------- readings

    static IElectric electric(GameTestHelper helper, BlockPos pos) {
        BlockEntity be = helper.getBlockEntity(pos);
        if (be instanceof IElectric electric)
            return electric;
        throw new IllegalStateException("no electric block at " + pos + " (found " + helper.getLevel().getBlockState(helper.absolutePos(pos)) + ")");
    }

    static int volts(GameTestHelper helper, BlockPos pos) {
        return electric(helper, pos).getData().getVoltage();
    }

    static ElectricalNetwork network(GameTestHelper helper, BlockPos pos) {
        return electric(helper, pos).getOrCreateElectricNetwork();
    }

    /** Distinct live network objects registered for this level. */
    static int liveNetworkCount(GameTestHelper helper, List<BlockPos> members) {
        Set<ElectricalNetwork> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (BlockPos pos : members)
            seen.add(network(helper, pos));
        return seen.size();
    }

    static Map<Long, ElectricalNetwork> networkMap(GameTestHelper helper) {
        return ElectricNetworkManager.networks.get(helper.getLevel());
    }

    // -------------------------------------------------------- the inspector

    /** What the Factory Inspector reports on a block, as translation keys without their prefix. */
    record Inspection(List<String> problems, List<String> oks, List<String> lines) {

        boolean has(String key) {
            for (String line : lines)
                if (line.equals(key))
                    return true;
            return false;
        }

        @Override
        public String toString() {
            return lines.toString();
        }
    }

    static Inspection inspect(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        List<String> oks = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (Component line : FactoryInspectorItem.inspect(level, helper.absolutePos(pos), new InspectionReport())) {
            String key = keyOf(line);
            lines.add(key);
            String text = line.getString();
            if (text.startsWith(PROBLEM))
                problems.add(key);
            else if (text.startsWith(OK))
                oks.add(key);
        }
        return new Inspection(problems, oks, lines);
    }

    private static String keyOf(Component line) {
        if (line.getContents() instanceof TranslatableContents own)
            return own.getKey().replace("tfmg.inspector.", "");
        for (Component sibling : line.getSiblings())
            if (sibling.getContents() instanceof TranslatableContents translatable)
                return translatable.getKey().replace("tfmg.inspector.", "");
        return line.getString();
    }

    /** Fails unless the inspector finds nothing wrong with any of these blocks. */
    static void assertHealthy(GameTestHelper helper, String when, BlockPos... positions) {
        for (BlockPos pos : positions) {
            Inspection inspection = inspect(helper, pos);
            helper.assertTrue(inspection.problems().isEmpty(), when + ": the inspector flags "
                    + blockName(helper, pos) + " at " + pos + ": " + inspection.problems() + " in " + inspection);
        }
    }

    /** Fails unless the inspector reports {@code key} as a problem on the block. */
    static void assertProblem(GameTestHelper helper, BlockPos pos, String key, String when) {
        Inspection inspection = inspect(helper, pos);
        helper.assertTrue(inspection.problems().contains(key), when + ": the inspector does not report '" + key
                + "' on " + blockName(helper, pos) + " at " + pos + ", it says " + inspection);
    }

    static String blockName(GameTestHelper helper, BlockPos pos) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(helper.getLevel().getBlockState(helper.absolutePos(pos)).getBlock()).getPath();
    }

    // -------------------------------------------------------------- wires

    /** A spool of the given item, full as a freshly crafted one. */
    static ItemStack spool(net.minecraft.world.item.Item spool) {
        ItemStack stack = new ItemStack(spool);
        stack.set(TFMGDataComponents.SPOOL_AMOUNT, 1000);
        return stack;
    }

    /**
     * Strings a wire between two cable connectors the way a player does: one
     * right click on each with the spool in hand.
     */
    static void stringWire(GameTestHelper helper, Player player, ItemStack spool, BlockPos a, BlockPos b) {
        player.setItemInHand(InteractionHand.MAIN_HAND, spool);
        InteractionResult first = click(helper, player, a);
        InteractionResult second = click(helper, player, b);
        helper.assertTrue(first.consumesAction() && second.consumesAction(),
                "the spool did not take both clicks (" + first + ", " + second + ")");
    }

    static InteractionResult click(GameTestHelper helper, Player player, BlockPos pos) {
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return player.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    static ItemStack coil(int turns) {
        ItemStack stack = new ItemStack(TFMGItems.ELECTROMAGNETIC_COIL.get());
        stack.set(TFMGDataComponents.COIL_TURNS, turns);
        return stack;
    }

    // -------------------------------------------------------- chunk reload

    /**
     * Does to these block entities what a chunk unload followed by a reload
     * does: each one is told its chunk unloaded and removed (no destroy, as
     * Create skips it on unload), then a fresh one is loaded from its save.
     */
    static void unloadAndReload(GameTestHelper helper, List<BlockPos> positions) {
        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();
        Map<BlockPos, CompoundTag> saves = new LinkedHashMap<>();
        for (BlockPos pos : new HashSet<>(positions)) {
            BlockPos abs = helper.absolutePos(pos);
            BlockEntity be = level.getBlockEntity(abs);
            if (be == null)
                continue;
            saves.put(abs, be.saveWithFullMetadata(registries));
            be.onChunkUnloaded();
            level.removeBlockEntity(abs);
        }
        for (Map.Entry<BlockPos, CompoundTag> entry : saves.entrySet()) {
            BlockEntity copy = BlockEntity.loadStatic(entry.getKey(), level.getBlockState(entry.getKey()), entry.getValue(), registries);
            if (copy == null)
                throw new IllegalStateException("could not reload the block entity at " + entry.getKey());
            level.setBlockEntity(copy);
        }
    }
}
