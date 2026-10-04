package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.content.electricity.base.ElectricalNetwork;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.content.electricity.connection.cables.CableConnectorBlockEntity;
import com.drmangotea.tfmg.content.electricity.connection.diagonal.DiagonalCableBlock;
import com.drmangotea.tfmg.content.electricity.generators.large_generator.RotorBlockEntity;
import com.drmangotea.tfmg.content.electricity.generators.large_generator.StatorBlock;
import com.drmangotea.tfmg.content.electricity.lights.LightBulbBlock;
import com.drmangotea.tfmg.content.electricity.lights.LightBulbBlockEntity;
import com.drmangotea.tfmg.content.electricity.measurement.VoltMeterBlockEntity;
import com.drmangotea.tfmg.content.electricity.network.large_switch.LargeSwitchBlock;
import com.drmangotea.tfmg.content.electricity.network.large_switch.LargeSwitchBlockEntity;
import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeCoilBlockEntity;
import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeTransformerBlockEntity;
import com.drmangotea.tfmg.content.electricity.network.transformer.small.TransformerBlockEntity;
import com.drmangotea.tfmg.content.electricity.storage.AccumulatorBlockEntity;
import com.drmangotea.tfmg.content.electricity.utilities.converter.ConverterBlock;
import com.drmangotea.tfmg.content.electricity.utilities.converter.ConverterBlockEntity;
import com.drmangotea.tfmg.content.electricity.utilities.electric_motor.ElectricMotorBlockEntity;
import com.drmangotea.tfmg.content.electricity.utilities.polarizer.PolarizerBlockEntity;
import com.drmangotea.tfmg.content.electricity.utilities.resistor.ResistorBlockEntity;
import com.drmangotea.tfmg.content.electricity.utilities.voltage_observer.VoltageObserverBlock;
import com.drmangotea.tfmg.content.electricity.utilities.voltage_observer.VoltageObserverBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.electrode_holder.ElectrodeHolderBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.freezer.FreezerBlockEntity;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGDisplaySources;
import com.drmangotea.tfmg.registry.TFMGElectrodes;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.blockEntity.ComparatorUtil;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.drmangotea.tfmg.gametest.TFMGPowerKit.*;

/**
 * Functional tests of everything electrical. Every machine is switched on in a
 * real circuit, its readings are compared with what the configs say it should
 * do, the Factory Inspector is run on it while it works (no problem allowed)
 * and again once its power is gone (it must say so).
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGPowerTests {

    private static final String SMALL = "gametest/platform";
    private static final String LARGE = "gametest/platform_large";
    private static final String HUGE = "gametest/platform_huge";
    private static final String BATCH = "tfmg_power";

    // ========================================================= generators

    /**
     * A generator turned by a creative motor makes the voltage and power its
     * configs give for the speed, nothing below the minimum speed, and drops
     * to zero once the shaft stops.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void generatorOutputFollowsShaftSpeed(GameTestHelper helper) {
        int[] speeds = {32, 64, 128, 256};
        BlockPos[] motors = new BlockPos[speeds.length];
        BlockPos[] generators = new BlockPos[speeds.length];
        BlockPos[] loads = new BlockPos[speeds.length];
        for (int i = 0; i < speeds.length; i++) {
            motors[i] = new BlockPos(1, 1, 1 + i * 3);
            generators[i] = motorDrivenGenerator(helper, motors[i], Direction.EAST, speeds[i]);
            hub(helper, generators[i].above());
            loads[i] = generators[i].above(2);
            place(helper, loads[i], TFMGBlocks.RESISTOR.get(), Direction.UP);
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (int i = 0; i < speeds.length; i++) {
                        int expected = generatorVoltage(speeds[i]);
                        IElectric generator = electric(helper, generators[i]);
                        helper.assertTrue(generator.voltageGeneration() == expected, speeds[i] + " RPM: generator makes "
                                + generator.voltageGeneration() + " V, configs say " + expected);
                        helper.assertTrue(generator.powerGeneration() == generatorPower(speeds[i]), speeds[i] + " RPM: generator makes "
                                + generator.powerGeneration() + " W, configs say " + generatorPower(speeds[i]));
                        IElectric load = electric(helper, loads[i]);
                        helper.assertTrue(load.getData().getVoltage() == expected, speeds[i] + " RPM: the load sees "
                                + load.getData().getVoltage() + " V instead of " + expected);
                        int watts = (int) (expected * (expected / 500f));
                        helper.assertTrue(load.getPowerUsage() == watts, speeds[i] + " RPM: a 500 ohm load draws "
                                + load.getPowerUsage() + " W instead of " + watts);
                    }
                })
                .thenExecute(() -> {
                    helper.assertTrue(generatorVoltage(speeds[0]) == 0, "the configs make power below the minimum speed");
                    for (int i = 1; i < speeds.length; i++)
                        helper.assertTrue(generatorVoltage(speeds[i]) > generatorVoltage(speeds[i - 1]), "voltage does not rise with speed");
                    assertProblem(helper, generators[0], "generator.too_slow", "below the minimum speed");
                    assertProblem(helper, loads[0], "electric.no_voltage", "below the minimum speed");
                    for (int i = 1; i < speeds.length; i++)
                        assertHealthy(helper, speeds[i] + " RPM", generators[i], generators[i].above(), loads[i]);
                    setMotorSpeed(helper, motors[3], 0);
                })
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, loads[3]) == 0, "the load keeps " + volts(helper, loads[3]) + " V after the shaft stopped"))
                .thenExecute(() -> assertProblem(helper, loads[3], "electric.no_voltage", "shaft stopped"))
                .thenSucceed();
    }

    /**
     * The large generator works with its full ring of eight stators, at the
     * voltage and power its configs give, and stops when one is missing.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 500)
    public static void largeGeneratorNeedsItsFullStatorRing(GameTestHelper helper) {
        BlockPos rotor = new BlockPos(5, 3, 5);
        int rpm = 100;
        helper.setBlock(rotor, TFMGBlocks.ROTOR.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        List<BlockPos> stators = new ArrayList<>();
        for (Direction a : new Direction[]{Direction.UP, Direction.DOWN})
            for (Direction b : new Direction[]{Direction.NORTH, Direction.SOUTH})
                stators.add(rotor.relative(a).relative(b));
        for (Direction d : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH})
            stators.add(rotor.relative(d));
        for (BlockPos pos : stators)
            helper.setBlock(pos, TFMGBlocks.STATOR.getDefaultState());
        BlockPos motor = rotor.west();
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        setMotorSpeed(helper, motor, rpm);
        BlockPos busHub = rotor.above(2);
        BlockPos load = rotor.above(3);
        hub(helper, busHub);
        place(helper, load, TFMGBlocks.RESISTOR.get(), Direction.UP);

        var m = machines();
        int generation = (int) Math.max(0, (rpm - m.largeGeneratorMinSpeed.getF()) * m.largeGeneratorModifier.getF());
        int volts = (int) Math.min(m.largeGeneratorMaxVoltage.get(), generation * m.largeGeneratorVoltageMultiplier.getF());
        int watts = (int) (generation * 40 * m.largeGeneratorPowerMultiplier.getF());
        BlockPos corner = rotor.above().north();

        helper.startSequence()
                .thenWaitUntil(() -> {
                    RotorBlockEntity be = helper.getBlockEntity(rotor);
                    helper.assertTrue(be.voltageGeneration() == volts, "the rotor makes " + be.voltageGeneration() + " V, configs say " + volts);
                    helper.assertTrue(be.powerGeneration() == watts, "the rotor makes " + be.powerGeneration() + " W, configs say " + watts);
                    helper.assertTrue(volts(helper, load) == volts, "the load sees " + volts(helper, load) + " V instead of " + volts);
                })
                .thenExecute(() -> {
                    BlockState top = helper.getLevel().getBlockState(helper.absolutePos(rotor.above()));
                    helper.assertTrue(top.getValue(StatorBlock.STATOR_STATE) == StatorBlock.StatorState.SIDE
                            && top.getValue(BlockStateProperties.FACING) == Direction.DOWN, "the top stator was not shaped into the ring: " + top);
                    assertHealthy(helper, "running", rotor, rotor.above(), busHub, load);
                    helper.destroyBlock(corner);
                })
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, load) == 0, "the generator still powers the load with a stator missing"))
                .thenExecute(() -> {
                    assertProblem(helper, rotor, "rotor.stators_missing", "one stator removed");
                    helper.setBlock(corner, TFMGBlocks.STATOR.getDefaultState());
                })
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, load) == volts, "the generator did not restart once the ring was complete again ("
                        + volts(helper, load) + " V)"))
                .thenSucceed();
    }

    /** The creative generator's output follows its dial, ten volts a step. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 300)
    public static void creativeGeneratorFollowsItsDial(GameTestHelper helper) {
        BlockPos generator = new BlockPos(1, 1, 2);
        BlockPos load = new BlockPos(3, 1, 2);
        creativeGenerator(helper, generator, 100);
        hub(helper, new BlockPos(2, 1, 2));
        place(helper, load, TFMGBlocks.RESISTOR.get(), Direction.EAST);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, load) == 100, "load at " + volts(helper, load) + " V, dial set to 100"))
                .thenExecute(() -> setCreativeVoltage(helper, generator, 250))
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, load) == 250, "load at " + volts(helper, load) + " V, dial set to 250"))
                .thenExecute(() -> {
                    IElectric resistor = electric(helper, load);
                    helper.assertTrue(Math.abs(resistor.getCurrent() - 0.5f) < 1e-4, "250 V on 500 ohm gives " + resistor.getCurrent() + " A");
                    helper.assertTrue(resistor.getPowerUsage() == 125, "250 V on 500 ohm draws " + resistor.getPowerUsage() + " W");
                    assertHealthy(helper, "dial at 250 V", generator, load);
                    setCreativeVoltage(helper, generator, 0);
                })
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, load) == 0, "load keeps " + volts(helper, load) + " V with the dial at 0"))
                .thenExecute(() -> assertProblem(helper, load, "electric.no_voltage", "dial at 0"))
                .thenSucceed();
    }

    /**
     * Several generators on one bus add their power and the bus runs at the
     * highest voltage; stopping one takes exactly its share away.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void parallelGeneratorsAddTheirPower(GameTestHelper helper) {
        BlockPos[] motors = {new BlockPos(1, 1, 2), new BlockPos(1, 1, 5), new BlockPos(1, 1, 8)};
        BlockPos[] generators = new BlockPos[3];
        for (int i = 0; i < 3; i++)
            generators[i] = motorDrivenGenerator(helper, motors[i], Direction.EAST, 128);
        hubs(helper, new BlockPos(2, 2, 2), Direction.SOUTH, 7);
        BlockPos load = new BlockPos(3, 2, 5);
        place(helper, load, TFMGBlocks.RESISTOR.get(), Direction.EAST);
        helper.startSequence()
                .thenWaitUntil(() -> expectBus(helper, load, generatorVoltage(128), 3 * generatorPower(128)))
                .thenExecute(() -> {
                    helper.assertTrue(network(helper, load).getMembers().size() == 3 + 7 + 1,
                            "the bus lists " + network(helper, load).getMembers().size() + " members instead of 11");
                    assertHealthy(helper, "three generators", generators[0], generators[1], generators[2], load);
                    setMotorSpeed(helper, motors[2], 256);
                })
                .thenWaitUntil(() -> expectBus(helper, load, generatorVoltage(256), 2 * generatorPower(128) + generatorPower(256)))
                .thenExecute(() -> setMotorSpeed(helper, motors[0], 0))
                .thenWaitUntil(() -> expectBus(helper, load, generatorVoltage(256), generatorPower(128) + generatorPower(256)))
                .thenSucceed();
    }

    private static void expectBus(GameTestHelper helper, BlockPos load, int volts, int watts) {
        IElectric electric = electric(helper, load);
        helper.assertTrue(electric.getData().getVoltage() == volts, "bus at " + electric.getData().getVoltage() + " V instead of " + volts);
        helper.assertTrue(electric.getData().networkPowerGeneration == watts, "bus reports " + electric.getData().networkPowerGeneration
                + " W generated instead of " + watts);
        helper.assertTrue(electric.getNetworkPowerGeneration() == watts, "bus sums " + electric.getNetworkPowerGeneration()
                + " W generated instead of " + watts);
    }

    // ======================================================= transmission

    /**
     * A wire of every type, strung with its spool the way a player does,
     * carries power between two cable connectors. Breaking a connector cuts
     * the line and pays the wire back; a new wire restores it.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 500)
    public static void everyWireTypeCarriesPower(GameTestHelper helper) {
        List<Item> spools = List.of(TFMGItems.COPPER_SPOOL.get(), TFMGItems.ALUMINUM_SPOOL.get(), TFMGItems.CONSTANTAN_SPOOL.get());
        List<Item> wires = List.of(TFMGItems.COPPER_WIRE.get(), TFMGItems.ALUMINUM_WIRE.get(), TFMGItems.CONSTANTAN_WIRE.get());
        List<String> types = List.of("copper", "aluminum", "constantan");
        int rows = spools.size();
        BlockPos[] near = new BlockPos[rows];
        BlockPos[] far = new BlockPos[rows];
        BlockPos[] loads = new BlockPos[rows];
        ItemStack[] stacks = new ItemStack[rows];
        for (int i = 0; i < rows; i++) {
            int z = 2 + i * 4;
            creativeGenerator(helper, new BlockPos(1, 1, z), 100);
            near[i] = new BlockPos(1, 2, z);
            place(helper, near[i], TFMGBlocks.CABLE_CONNECTOR.get(), Direction.UP);
            hub(helper, new BlockPos(10, 1, z));
            far[i] = new BlockPos(10, 2, z);
            place(helper, far[i], TFMGBlocks.CABLE_CONNECTOR.get(), Direction.UP);
            loads[i] = new BlockPos(10, 1, z + 1);
            place(helper, loads[i], TFMGBlocks.RESISTOR.get(), Direction.SOUTH);
            stacks[i] = spool(spools.get(i));
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AtomicInteger dropped = new AtomicInteger();
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    for (int i = 0; i < rows; i++)
                        helper.assertTrue(volts(helper, loads[i]) == 0, types.get(i) + ": the load has power before any wire");
                    for (int i = 0; i < rows; i++)
                        stringWire(helper, player, stacks[i], near[i], far[i]);
                })
                .thenWaitUntil(() -> {
                    for (int i = 0; i < rows; i++)
                        helper.assertTrue(volts(helper, loads[i]) == 100, types.get(i) + " wire: the far load sees " + volts(helper, loads[i]) + " V");
                })
                .thenExecute(() -> {
                    for (int i = 0; i < rows; i++) {
                        CableConnectorBlockEntity a = helper.getBlockEntity(near[i]);
                        CableConnectorBlockEntity b = helper.getBlockEntity(far[i]);
                        helper.assertTrue(a.connections.size() == 1 && b.connections.size() == 1, types.get(i) + ": "
                                + a.connections.size() + " and " + b.connections.size() + " wire ends instead of one each");
                        helper.assertTrue(a.connections.get(0).type.getKey().getPath().equals(types.get(i)), "the wire is "
                                + a.connections.get(0).type.getKey() + " instead of " + types.get(i));
                        int cost = (int) (1000 - a.connections.get(0).getLength() / 8 * 125);
                        int left = stacks[i].getOrDefault(TFMGDataComponents.SPOOL_AMOUNT, -1);
                        helper.assertTrue(left == cost, types.get(i) + " spool holds " + left + " after the wire, expected " + cost);
                        helper.assertTrue(electric(helper, near[i]).getData().getId() == electric(helper, loads[i]).getData().getId(),
                                types.get(i) + ": both ends of the wire are not on one network");
                        assertHealthy(helper, types.get(i) + " wire", near[i], far[i], loads[i]);
                    }
                    // Break the far connector of the copper line, with drops.
                    dropped.set((int) (((CableConnectorBlockEntity) helper.getBlockEntity(near[0])).connections.get(0).getLength() / 8));
                    helper.getLevel().destroyBlock(helper.absolutePos(far[0]), true);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(volts(helper, loads[0]) == 0, "the load keeps " + volts(helper, loads[0]) + " V after its connector broke");
                    CableConnectorBlockEntity a = helper.getBlockEntity(near[0]);
                    helper.assertTrue(a.connections.isEmpty(), "the near connector keeps " + a.connections.size() + " wire ends to a broken connector");
                })
                .thenExecute(() -> {
                    int wiresOnGround = 0;
                    for (ItemEntity entity : helper.getEntities(EntityType.ITEM, far[0], 3))
                        if (entity.getItem().is(wires.get(0)))
                            wiresOnGround += entity.getItem().getCount();
                    helper.assertTrue(wiresOnGround == dropped.get(), "breaking the connector dropped " + wiresOnGround
                            + " wire instead of " + dropped.get());
                    assertProblem(helper, loads[0], "electric.no_voltage", "line cut");
                    helper.assertTrue(volts(helper, loads[1]) == 100 && volts(helper, loads[2]) == 100, "cutting one line touched the others");
                    place(helper, far[0], TFMGBlocks.CABLE_CONNECTOR.get(), Direction.UP);
                })
                .thenIdle(2)
                .thenExecute(() -> stringWire(helper, player, stacks[0], near[0], far[0]))
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, loads[0]) == 100, "the line did not come back after rewiring ("
                        + volts(helper, loads[0]) + " V)"))
                .thenSucceed();
    }

    /**
     * Copycat cable, the cable tubes and posts, every cable hub and the
     * diagonal cable all pass power along.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 300)
    public static void everyConductorPassesPower(GameTestHelper helper) {
        List<BlockPos> line = new ArrayList<>();
        creativeGenerator(helper, new BlockPos(0, 1, 5), 100);
        helper.setBlock(new BlockPos(1, 1, 5), TFMGBlocks.COPYCAT_CABLE_BLOCK.getDefaultState());
        line.add(new BlockPos(1, 1, 5));
        List<BlockEntry<? extends Block>> tubes = List.of(TFMGBlocks.CABLE_TUBE, TFMGBlocks.ELECTRIC_POST, TFMGBlocks.CONCRETE_ENCASED_CABLE_TUBE,
                TFMGBlocks.CONCRETE_ENCASED_ELECTRIC_POST);
        int x = 2;
        for (BlockEntry<? extends Block> tube : tubes) {
            helper.setBlock(new BlockPos(x, 1, 5), tube.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
            line.add(new BlockPos(x++, 1, 5));
        }
        List<BlockEntry<? extends Block>> hubs = List.of(TFMGBlocks.BRASS_CABLE_HUB, TFMGBlocks.COPPER_CABLE_HUB, TFMGBlocks.STEEL_CABLE_HUB,
                TFMGBlocks.ALUMINUM_CABLE_HUB, TFMGBlocks.STEEL_CASING_CABLE_HUB, TFMGBlocks.HEAVY_CABLE_HUB);
        for (BlockEntry<? extends Block> hub : hubs) {
            helper.setBlock(new BlockPos(x, 1, 5), hub.getDefaultState());
            line.add(new BlockPos(x++, 1, 5));
        }
        BlockPos end = new BlockPos(x - 1, 2, 5);
        place(helper, end, TFMGBlocks.RESISTOR.get(), Direction.UP);

        creativeGenerator(helper, new BlockPos(1, 1, 9), 100);
        BlockPos diagonal = new BlockPos(2, 1, 9);
        helper.setBlock(diagonal, TFMGBlocks.DIAGONAL_CABLE_BLOCK.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.WEST).setValue(DiagonalCableBlock.FACING_UP, true));
        hub(helper, diagonal.above());
        BlockPos diagonalLoad = diagonal.above(2);
        place(helper, diagonalLoad, TFMGBlocks.RESISTOR.get(), Direction.UP);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(volts(helper, end) == 100, "power did not cross the conductor line (" + volts(helper, end) + " V at the end)");
                    helper.assertTrue(volts(helper, diagonalLoad) == 100, "power did not cross the diagonal cable (" + volts(helper, diagonalLoad) + " V)");
                })
                .thenExecute(() -> {
                    long id = electric(helper, end).getData().getId();
                    for (BlockPos pos : line) {
                        helper.assertTrue(electric(helper, pos).getData().getId() == id, blockName(helper, pos) + " is not on the line's network");
                        helper.assertTrue(volts(helper, pos) == 100, blockName(helper, pos) + " reads " + volts(helper, pos) + " V");
                    }
                    assertHealthy(helper, "conductor line", line.toArray(new BlockPos[0]));
                    assertHealthy(helper, "diagonal cable", diagonal, diagonalLoad, end);
                })
                .thenSucceed();
    }

    /**
     * Resistors draw what Ohm's law says, in parallel they lower the network
     * resistance, and a potentiometer passes the share of the voltage its
     * dial asks for.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 300)
    public static void resistorsAndPotentiometerFollowOhmsLaw(GameTestHelper helper) {
        creativeGenerator(helper, new BlockPos(1, 1, 2), 200);
        hub(helper, new BlockPos(2, 1, 2));
        BlockPos a = new BlockPos(2, 2, 2);
        BlockPos b = new BlockPos(2, 1, 3);
        place(helper, a, TFMGBlocks.RESISTOR.get(), Direction.UP);
        place(helper, b, TFMGBlocks.RESISTOR.get(), Direction.SOUTH);
        ItemStack resistorItem = new ItemStack(TFMGBlocks.RESISTOR.get());
        resistorItem.set(TFMGDataComponents.RESISTANCE, 250);
        ResistorBlockEntity rb = helper.getBlockEntity(b);
        rb.setResistance(resistorItem);
        rb.updateNextTick();

        creativeGenerator(helper, new BlockPos(1, 1, 6), 200);
        hub(helper, new BlockPos(2, 1, 6));
        BlockPos pot = new BlockPos(3, 1, 6);
        place(helper, pot, TFMGBlocks.POTENTIOMETER.get(), Direction.EAST);
        hub(helper, new BlockPos(4, 1, 6));
        BlockPos potLoad = new BlockPos(4, 2, 6);
        place(helper, potLoad, TFMGBlocks.RESISTOR.get(), Direction.UP);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    IElectric ra = electric(helper, a);
                    IElectric rbe = electric(helper, b);
                    helper.assertTrue(ra.getData().getVoltage() == 200 && rbe.getData().getVoltage() == 200, "the resistors are not at 200 V");
                    helper.assertTrue(Math.abs(ra.getCurrent() - 0.4f) < 1e-4 && ra.getPowerUsage() == 80, "500 ohm at 200 V: "
                            + ra.getCurrent() + " A, " + ra.getPowerUsage() + " W");
                    helper.assertTrue(Math.abs(rbe.getCurrent() - 0.8f) < 1e-4 && rbe.getPowerUsage() == 160, "250 ohm at 200 V: "
                            + rbe.getCurrent() + " A, " + rbe.getPowerUsage() + " W");
                    helper.assertTrue(ra.getNetworkResistance() == 166, "500 and 250 ohm in parallel read " + ra.getNetworkResistance() + " ohm");
                    helper.assertTrue(ra.getNetworkPowerUsage() == 240, "the network draws " + ra.getNetworkPowerUsage() + " W instead of 240");
                    helper.assertTrue(volts(helper, potLoad) == 200, "a potentiometer at 100% passes " + volts(helper, potLoad) + " V");
                })
                .thenExecute(() -> {
                    assertHealthy(helper, "resistors", a, b, pot, potLoad);
                    scroll(helper, pot).setValue(50);
                })
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, potLoad) == 100, "a potentiometer at 50% passes " + volts(helper, potLoad) + " V"))
                .thenExecute(() -> {
                    int load = electric(helper, potLoad).getPowerUsage();
                    helper.assertTrue(electric(helper, pot).getPowerUsage() == load, "the potentiometer reports " + electric(helper, pot).getPowerUsage()
                            + " W drawn, its load takes " + load);
                    scroll(helper, pot).setValue(25);
                })
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, potLoad) == 50, "a potentiometer at 25% passes " + volts(helper, potLoad) + " V"))
                .thenSucceed();
    }

    /**
     * The encased diode and potentiometer bridge like their plain versions,
     * a glass insulator carries a wire like the plain one, and the segmented
     * display draws power and runs on it.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 300)
    public static void encasedBridgesGlassInsulatorAndDisplayWork(GameTestHelper helper) {
        BlockPos[] bridges = {new BlockPos(3, 1, 2), new BlockPos(3, 1, 5)};
        BlockPos[] loads = new BlockPos[2];
        List<BlockEntry<? extends Block>> blocks = List.of(TFMGBlocks.ENCASED_DIODE, TFMGBlocks.ENCASED_POTENTIOMETER);
        for (int i = 0; i < 2; i++) {
            BlockPos bridge = bridges[i];
            creativeGenerator(helper, bridge.west(2), 200);
            hub(helper, bridge.west());
            helper.setBlock(bridge, facing(blocks.get(i).get(), Direction.EAST));
            hub(helper, bridge.east());
            loads[i] = bridge.east().above();
            place(helper, loads[i], TFMGBlocks.RESISTOR.get(), Direction.UP);
        }
        creativeGenerator(helper, new BlockPos(1, 1, 9), 100);
        BlockPos near = new BlockPos(1, 2, 9);
        place(helper, near, TFMGBlocks.GLASS_CABLE_CONNECTOR.get(), Direction.UP);
        hub(helper, new BlockPos(8, 1, 9));
        BlockPos far = new BlockPos(8, 2, 9);
        place(helper, far, TFMGBlocks.GLASS_CABLE_CONNECTOR.get(), Direction.UP);
        BlockPos display = new BlockPos(9, 1, 9);
        place(helper, display, TFMGBlocks.SEGMENTED_DISPLAY.get(), Direction.EAST);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> stringWire(helper, player, spool(TFMGItems.ALUMINUM_SPOOL.get()), near, far))
                .thenWaitUntil(() -> {
                    for (int i = 0; i < 2; i++)
                        helper.assertTrue(volts(helper, loads[i]) == 200, blockName(helper, bridges[i]) + " passes " + volts(helper, loads[i]) + " V of 200");
                    helper.assertTrue(volts(helper, display) == 100, "the segmented display behind a glass insulator wire is at " + volts(helper, display) + " V");
                })
                .thenExecute(() -> {
                    IElectric screen = electric(helper, display);
                    int watts = (int) (100 * (100 / screen.resistance()));
                    helper.assertTrue(screen.canWork() && screen.getPowerUsage() == watts, "the segmented display draws " + screen.getPowerUsage()
                            + " W instead of " + watts);
                    assertHealthy(helper, "bridges and display", bridges[0], bridges[1], loads[0], loads[1], near, far, display);
                    scroll(helper, bridges[1]).setValue(50);
                })
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, loads[1]) == 100, "an encased potentiometer at 50% passes " + volts(helper, loads[1]) + " V"))
                .thenSucceed();
    }

    /** An electrical switch passes power only while it gets a redstone signal. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 300)
    public static void electricalSwitchFollowsRedstone(GameTestHelper helper) {
        creativeGenerator(helper, new BlockPos(0, 1, 2), 200);
        hub(helper, new BlockPos(1, 1, 2));
        BlockPos sw = new BlockPos(2, 1, 2);
        place(helper, sw, TFMGBlocks.ELECTRICAL_SWITCH.get(), Direction.EAST);
        hub(helper, new BlockPos(3, 1, 2));
        BlockPos load = new BlockPos(3, 2, 2);
        place(helper, load, TFMGBlocks.RESISTOR.get(), Direction.UP);
        BlockPos torch = sw.north();
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(volts(helper, load) == 0, "an open switch passes " + volts(helper, load) + " V");
                    assertProblem(helper, sw, "switch.open", "no signal");
                    helper.setBlock(torch, Blocks.REDSTONE_BLOCK.defaultBlockState());
                })
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, load) == 200, "a switch with a full signal passes " + volts(helper, load) + " V"))
                .thenExecute(() -> {
                    assertHealthy(helper, "switch closed", sw, load);
                    helper.setBlock(torch, Blocks.AIR.defaultBlockState());
                })
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, load) == 0, "the switch still passes " + volts(helper, load) + " V once the signal is gone"))
                .thenSucceed();
    }

    /**
     * Transformers multiply the voltage by their coil ratio, up and down and
     * whichever way they face; a coil with too few turns, or a missing one,
     * stops them and the inspector says which.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 300)
    public static void transformersFollowTheirCoilRatio(GameTestHelper helper) {
        int[][] coils = {{100, 200}, {200, 100}, {100, 30}, {100, 0}, {100, 300}};
        BlockPos[] transformers = new BlockPos[coils.length];
        BlockPos[] loads = new BlockPos[coils.length];
        for (int i = 0; i < coils.length; i++) {
            int z = 1 + i * 2;
            boolean reversed = i == coils.length - 1;
            int dir = reversed ? -1 : 1;
            int start = reversed ? 5 : 1;
            creativeGenerator(helper, new BlockPos(start, 1, z), 100);
            hub(helper, new BlockPos(start + dir, 1, z));
            transformers[i] = new BlockPos(start + 2 * dir, 1, z);
            place(helper, transformers[i], TFMGBlocks.TRANSFORMER.get(), reversed ? Direction.NORTH : Direction.SOUTH);
            hub(helper, new BlockPos(start + 3 * dir, 1, z));
            loads[i] = new BlockPos(start + 3 * dir, 2, z);
            place(helper, loads[i], TFMGBlocks.RESISTOR.get(), Direction.UP);
            TransformerBlockEntity be = helper.getBlockEntity(transformers[i]);
            be.primaryCoil = coil(coils[i][0]);
            be.secondaryCoil = coils[i][1] == 0 ? ItemStack.EMPTY : coil(coils[i][1]);
            be.updateCoils();
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (int i = 0; i < coils.length; i++) {
                        boolean works = coils[i][0] >= 50 && coils[i][1] >= 50;
                        int expected = works ? (int) (100 * ((float) coils[i][1] / coils[i][0])) : 0;
                        helper.assertTrue(volts(helper, loads[i]) == expected, "coils " + coils[i][0] + "/" + coils[i][1] + ": the load sees "
                                + volts(helper, loads[i]) + " V instead of " + expected);
                    }
                })
                .thenExecute(() -> {
                    for (int i : new int[]{0, 1, 4}) {
                        assertHealthy(helper, "coils " + coils[i][0] + "/" + coils[i][1], transformers[i], loads[i]);
                        int drawn = electric(helper, loads[i]).getPowerUsage();
                        helper.assertTrue(electric(helper, transformers[i]).getPowerUsage() == drawn, "the transformer passes "
                                + electric(helper, transformers[i]).getPowerUsage() + " W to the source, its load draws " + drawn);
                    }
                    assertProblem(helper, transformers[2], "transformer.coil_few_turns", "a 30 turn coil");
                    assertProblem(helper, transformers[3], "transformer.coil_missing", "one coil");
                })
                .thenSucceed();
    }

    /**
     * Two large coils assembled into a large transformer multiply the voltage
     * by their turn ratio, and the inspector of either part reads that ratio.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 300)
    public static void largeTransformerFollowsItsTurnRatio(GameTestHelper helper) {
        BlockPos main = new BlockPos(4, 1, 5);
        BlockPos output = new BlockPos(5, 1, 5);
        helper.setBlock(main, TFMGBlocks.LARGE_COIL.getDefaultState());
        helper.setBlock(output, TFMGBlocks.LARGE_COIL.getDefaultState());
        ItemStack primary = new ItemStack(TFMGBlocks.LARGE_COIL.get());
        primary.set(TFMGDataComponents.COIL_TURNS, 100);
        ItemStack secondary = new ItemStack(TFMGBlocks.LARGE_COIL.get());
        secondary.set(TFMGDataComponents.COIL_TURNS, 300);
        ((LargeCoilBlockEntity) helper.getBlockEntity(main)).setCapacity(primary);
        ((LargeCoilBlockEntity) helper.getBlockEntity(output)).setCapacity(secondary);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ((LargeCoilBlockEntity) helper.getBlockEntity(main)).createTransformer(player, Direction.EAST);
        creativeGenerator(helper, new BlockPos(3, 2, 5), 100);
        hub(helper, main.above());
        BlockPos load = output.above();
        place(helper, load, TFMGBlocks.RESISTOR.get(), Direction.UP);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getBlockEntity(main) instanceof LargeTransformerBlockEntity
                            && helper.getBlockEntity(output) instanceof LargeTransformerBlockEntity, "the coils did not become a large transformer");
                    helper.assertTrue(volts(helper, load) == 300, "a 1:3 large transformer gives " + volts(helper, load) + " V from 100");
                })
                .thenExecute(() -> {
                    assertHealthy(helper, "large transformer", main, output, load);
                    for (BlockPos part : new BlockPos[]{main, output}) {
                        Object[] args = argsOf(helper, part, "large_transformer.ratio");
                        helper.assertTrue(args != null && String.format("%.2f", 3f).equals(String.valueOf(args[0])), "the inspector on the "
                                + (part == main ? "input" : "output") + " part reads a ratio of " + (args == null ? "nothing" : args[0]) + " instead of 3.00");
                    }
                    helper.assertTrue(electric(helper, output).powerGeneration() == 30000, "an air cooled large transformer passes "
                            + electric(helper, output).powerGeneration() + " W instead of 30000");
                })
                .thenSucceed();
    }

    /**
     * The large switch closes when its shaft turns one way and opens when it
     * turns the other, and passes power only while closed.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void largeSwitchFollowsItsShaft(GameTestHelper helper) {
        BlockPos main = new BlockPos(4, 1, 5);
        BlockState state = TFMGBlocks.LARGE_SWITCH.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST).setValue(LargeSwitchBlock.IS_MAIN_PART, true);
        helper.setBlock(main, state);
        state.getBlock().setPlacedBy(helper.getLevel(), helper.absolutePos(main), state, null, ItemStack.EMPTY);
        creativeGenerator(helper, new BlockPos(3, 1, 5), 100);
        BlockPos load = new BlockPos(6, 1, 5);
        place(helper, load, TFMGBlocks.RESISTOR.get(), Direction.EAST);
        BlockPos motor = main.north();
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        setMotorSpeed(helper, motor, 64);
        AtomicInteger closingSign = new AtomicInteger();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getBlockEntity(main.east()) instanceof LargeSwitchBlockEntity, "the large switch did not place its second half");
                    helper.assertTrue(((LargeSwitchBlockEntity) helper.getBlockEntity(main)).getSpeed() != 0, "the large switch does not turn");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    LargeSwitchBlockEntity be = helper.getBlockEntity(main);
                    // Positive speed closes it, negative opens it.
                    closingSign.set(be.getSpeed() > 0 ? 1 : -1);
                    if (closingSign.get() < 0)
                        setMotorSpeed(helper, motor, -64);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(((LargeSwitchBlockEntity) helper.getBlockEntity(main)).closed, "the switch did not close");
                    helper.assertTrue(volts(helper, load) == 100, "a closed large switch passes " + volts(helper, load) + " V");
                })
                .thenExecute(() -> {
                    assertHealthy(helper, "closed", main, load);
                    setMotorSpeed(helper, motor, -64 * closingSign.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, load) == 0, "an opening large switch still passes " + volts(helper, load) + " V"))
                .thenExecute(() -> assertProblem(helper, main, "large_switch.open", "opened"))
                .thenSucceed();
    }

    // ===================================================== storage and FE

    /**
     * Three accumulators make one bank: it charges at its configured rate from
     * a generator, carries the load at its own voltage once the generator is
     * gone, and everything goes dark when it runs empty.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 600)
    public static void accumulatorBankChargesThenCarriesTheLoad(GameTestHelper helper) {
        BlockPos generator = new BlockPos(1, 1, 5);
        creativeGenerator(helper, generator, 100);
        hub(helper, new BlockPos(2, 1, 5));
        BlockPos controller = new BlockPos(3, 1, 5);
        for (int x = 3; x <= 5; x++)
            place(helper, new BlockPos(x, 1, 5), TFMGBlocks.ACCUMULATOR.get(), Direction.EAST);
        hub(helper, new BlockPos(6, 1, 5));
        BlockPos bulb = new BlockPos(6, 2, 5);
        place(helper, bulb, TFMGBlocks.LIGHT_BULB.get(), Direction.UP);
        BlockPos resistor = new BlockPos(6, 1, 6);
        place(helper, resistor, TFMGBlocks.RESISTOR.get(), Direction.SOUTH);
        var m = machines();
        int bankVolts = m.accumulatorVoltage.get() * 3;
        int rate = (int) (m.accumulatorChargingRate.get() / m.FEtoWattTickConversionRate.get());
        AtomicInteger mark = new AtomicInteger();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    AccumulatorBlockEntity be = helper.getBlockEntity(controller);
                    helper.assertTrue(be.isController() && be.length == 3, "the bank formed with length " + be.length);
                    helper.assertTrue(be.getMaxCapacity() == m.accumulatorStorage.get() * 3, "the bank holds " + be.getMaxCapacity());
                    helper.assertTrue(be.getOutputVoltage() == bankVolts, "the bank is rated " + be.getOutputVoltage() + " V");
                    helper.assertTrue(volts(helper, bulb) == 100, "the bulb sees " + volts(helper, bulb) + " V from the generator");
                    helper.assertTrue(be.energy.getEnergyStored() > 0, "the bank does not charge");
                })
                .thenExecute(() -> mark.set(energy(helper, controller)))
                .thenExecuteAfter(40, () -> {
                    int gained = energy(helper, controller) - mark.get();
                    helper.assertTrue(Math.abs(gained - 40 * rate) <= rate, "the bank gained " + gained + " FE in 40 ticks, its rate is " + rate + " per tick");
                    Inspection inspection = inspect(helper, controller);
                    helper.assertTrue(inspection.oks().contains("accumulator.charging"), "the inspector does not see the bank charging: " + inspection);
                    assertHealthy(helper, "charging", controller, bulb, resistor);
                    helper.destroyBlock(generator);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(volts(helper, bulb) == bankVolts, "on the bank alone the bulb sees " + volts(helper, bulb) + " V, the bank is rated " + bankVolts);
                    helper.assertTrue(lightOf(helper, bulb) == Math.min(bankVolts / 10, 15), "on the bank alone the bulb shines " + lightOf(helper, bulb));
                })
                .thenExecute(() -> mark.set(energy(helper, controller)))
                .thenExecuteAfter(20, () -> {
                    int used = mark.get() - energy(helper, controller);
                    int load = electric(helper, bulb).getNetworkPowerUsage();
                    helper.assertTrue(used > 0, "the bank does not discharge under load");
                    helper.assertTrue(Math.abs(used - 20 * Math.max(load, 1)) <= 2 * Math.max(load, 1), "the bank gave " + used
                            + " FE in 20 ticks to a " + load + " W load");
                    Inspection inspection = inspect(helper, controller);
                    helper.assertTrue(inspection.oks().contains("accumulator.discharging"), "the inspector does not see the bank discharging: " + inspection);
                    assertHealthy(helper, "on the bank", controller, bulb, resistor);
                    ((AccumulatorBlockEntity) helper.getBlockEntity(controller)).energy.setEnergy(40);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(energy(helper, controller) == 0, "the bank never ran empty");
                    helper.assertTrue(volts(helper, bulb) == 0, "the bulb keeps " + volts(helper, bulb) + " V from an empty bank");
                    helper.assertTrue(lightOf(helper, bulb) == 0, "the bulb keeps shining on an empty bank");
                })
                .thenExecute(() -> {
                    assertProblem(helper, controller, "accumulator.empty", "bank empty");
                    assertProblem(helper, bulb, "electric.no_voltage", "bank empty");
                })
                .thenSucceed();
    }

    /**
     * A bank that is not needed must not count as a power source while it
     * charges: a generator too weak for its load stays too weak with a bank
     * on the line, and no energy appears from nowhere.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void chargingAccumulatorAddsNoPower(GameTestHelper helper) {
        int rpm = 64;
        int volts = generatorVoltage(rpm);
        // Two loads that together draw a little more than the generator makes.
        int r1 = 1;
        int r2 = 4;
        int draw = (int) (volts * ((float) volts / r1)) + (int) (volts * ((float) volts / r2));
        helper.assertTrue(draw > generatorPower(rpm) && draw < generatorPower(rpm) + machines().accumulatorVoltage.get() * machines().accumulatorMaxAmpOutput.get(),
                "the test loads (" + draw + " W) do not sit between the generator alone and the generator plus a bank");

        BlockPos[] loads = new BlockPos[2];
        // Row 0: generator and loads only. Row 1: the same with a charged bank in between.
        for (int row = 0; row < 2; row++) {
            int z = 2 + row * 5;
            BlockPos generator = motorDrivenGenerator(helper, new BlockPos(1, 1, z), Direction.EAST, rpm);
            hub(helper, generator.east());
            BlockPos bus = generator.east();
            if (row == 1) {
                place(helper, bus.east(), TFMGBlocks.ACCUMULATOR.get(), Direction.EAST);
                bus = bus.east(2);
                hub(helper, bus);
            }
            loads[row] = bus.above();
            place(helper, loads[row], TFMGBlocks.RESISTOR.get(), Direction.UP);
            place(helper, bus.south(), TFMGBlocks.RESISTOR.get(), Direction.SOUTH);
            setResistance(helper, loads[row], r1);
            setResistance(helper, bus.south(), r2);
        }
        BlockPos bank = new BlockPos(4, 1, 7);
        ((AccumulatorBlockEntity) helper.getBlockEntity(bank)).energy.setEnergy(50000);
        AtomicInteger mark = new AtomicInteger();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(volts(helper, loads[0]) == volts && volts(helper, loads[1]) == volts, "the loads are not at " + volts + " V");
                    helper.assertTrue(electric(helper, loads[0]).getData().notEnoughPower, "the generator alone runs " + draw + " W on "
                            + generatorPower(rpm) + " W");
                })
                .thenIdle(20)
                .thenExecute(() -> mark.set(energy(helper, bank)))
                .thenExecuteAfter(40, () -> {
                    boolean powered = !electric(helper, loads[1]).getData().notEnoughPower;
                    int change = energy(helper, bank) - mark.get();
                    helper.assertTrue(!(powered && change >= 0), "the bank charged by " + change + " FE while the network ran a " + draw
                            + " W load on a " + generatorPower(rpm) + " W generator: the bank was counted as a source without paying for it");
                })
                .thenSucceed();
    }

    /**
     * Breaking a block of a charged bank never creates or destroys energy:
     * what the surviving banks hold plus what the dropped item carries equals
     * what the bank held, whichever block breaks and however it breaks.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 300)
    public static void breakingAnAccumulatorConservesItsCharge(GameTestHelper helper) {
        // Column 0: the middle of five breaks. Column 1: the controller of two
        // breaks with drops, as a drill or an explosion would.
        int[] lengths = {5, 2};
        BlockPos[] bases = {new BlockPos(2, 1, 2), new BlockPos(6, 1, 2)};
        int[] broken = {2, 0};
        int[] charge = {5 * machines().accumulatorStorage.get(), 150000};
        for (int c = 0; c < 2; c++)
            for (int y = 0; y < lengths[c]; y++)
                place(helper, bases[c].above(y), TFMGBlocks.ACCUMULATOR.get(), Direction.UP);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (int c = 0; c < 2; c++) {
                        AccumulatorBlockEntity be = helper.getBlockEntity(bases[c]);
                        helper.assertTrue(be.isController() && be.length == lengths[c], "column " + c + " formed with length " + be.length);
                    }
                })
                .thenExecute(() -> {
                    for (int c = 0; c < 2; c++)
                        ((AccumulatorBlockEntity) helper.getBlockEntity(bases[c])).energy.setEnergy(charge[c]);
                    for (int c = 0; c < 2; c++)
                        helper.getLevel().destroyBlock(helper.absolutePos(bases[c].above(broken[c])), true);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    for (int c = 0; c < 2; c++) {
                        int kept = 0;
                        StringBuilder detail = new StringBuilder();
                        for (int y = 0; y < lengths[c]; y++)
                            if (helper.getLevel().getBlockEntity(helper.absolutePos(bases[c].above(y))) instanceof AccumulatorBlockEntity be) {
                                kept += be.energy.getEnergyStored();
                                detail.append(" [y").append(y).append(": ").append(be.energy.getEnergyStored()).append("/")
                                        .append(be.energy.getMaxEnergyStored()).append(" len ").append(be.length).append("]");
                            }
                        int carried = 0;
                        for (ItemEntity entity : helper.getEntities(EntityType.ITEM, bases[c].above(broken[c]), 2))
                            carried += entity.getItem().getOrDefault(TFMGDataComponents.ACCUMULATOR_STORAGE, 0);
                        helper.assertTrue(kept + carried == charge[c], "column " + c + ": " + charge[c] + " FE became " + kept
                                + " in the banks and " + carried + " in the dropped item:" + detail);
                    }
                })
                .thenSucceed();
    }

    /**
     * The converter turns Forge Energy into TFMG power at its dial voltage
     * and TFMG power into Forge Energy, through the NeoForge energy
     * capability both ways, and pushes it into a neighbouring FE block.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void converterWorksBothWays(GameTestHelper helper) {
        // FE -> TFMG: output mode, TFMG side east.
        BlockPos out = new BlockPos(3, 1, 2);
        helper.setBlock(out, facing(TFMGBlocks.CONVERTER.get(), Direction.NORTH).setValue(ConverterBlock.INPUT, false));
        hub(helper, out.east());
        BlockPos bulb = out.east().above();
        place(helper, bulb, TFMGBlocks.LIGHT_BULB.get(), Direction.UP);
        // TFMG -> FE: input mode fed by a generator, an accumulator on its FE side.
        BlockPos in = new BlockPos(3, 1, 7);
        helper.setBlock(in, facing(TFMGBlocks.CONVERTER.get(), Direction.NORTH).setValue(ConverterBlock.INPUT, true));
        hub(helper, in.east());
        creativeGenerator(helper, in.east(2), 100);
        BlockPos sink = in.west();
        place(helper, sink, TFMGBlocks.ACCUMULATOR.get(), Direction.UP);
        int rate = (int) (machines().accumulatorChargingRate.get() * 10 / machines().FEtoWattTickConversionRate.get());
        AtomicInteger mark = new AtomicInteger();
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(volts(helper, bulb) == 0, "a converter without FE powers the bulb");
                    assertProblem(helper, out, "converter.no_fe", "no FE");
                    IEnergyStorage cap = energyCap(helper, out, Direction.WEST);
                    helper.assertTrue(cap != null && cap.receiveEnergy(10000, false) == 10000, "the converter does not take FE through its capability");
                })
                .thenWaitUntil(() -> {
                    int dial = scroll(helper, out).getValue();
                    helper.assertTrue(volts(helper, bulb) == dial, "the bulb sees " + volts(helper, bulb) + " V, the converter dial is " + dial);
                    helper.assertTrue(lightOf(helper, bulb) == Math.min(dial / 10, 15), "the bulb shines " + lightOf(helper, bulb));
                })
                .thenExecute(() -> {
                    Inspection inspection = inspect(helper, out);
                    helper.assertTrue(inspection.oks().contains("converter.supplying"), "the inspector does not see the converter supplying: " + inspection);
                    assertHealthy(helper, "FE to TFMG", out, bulb);
                    helper.assertTrue(energyCap(helper, out, Direction.WEST).extractEnergy(100, true) == 0,
                            "an FE machine can pull back the FE a converter is turning into TFMG power");
                    mark.set(((ConverterBlockEntity) helper.getBlockEntity(out)).energy.getEnergyStored());
                })
                .thenExecuteAfter(20, () -> {
                    int now = ((ConverterBlockEntity) helper.getBlockEntity(out)).energy.getEnergyStored();
                    helper.assertTrue(now < mark.get(), "the converter powers a bulb without spending FE");
                    ((ConverterBlockEntity) helper.getBlockEntity(out)).energy.setEnergy(0);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(volts(helper, bulb) == 0, "the bulb keeps " + volts(helper, bulb) + " V from an empty converter");
                    helper.assertTrue(lightOf(helper, bulb) == 0, "the bulb keeps shining on an empty converter");
                })
                .thenExecute(() -> {
                    assertProblem(helper, bulb, "electric.no_voltage", "converter empty");
                    mark.set(stored(helper, in) + stored(helper, sink));
                })
                .thenExecuteAfter(20, () -> {
                    int gained = stored(helper, in) + stored(helper, sink) - mark.get();
                    helper.assertTrue(Math.abs(gained - 20 * rate) <= rate, "the converter turned " + gained + " FE out of 20 ticks of power, its rate is "
                            + rate + " per tick");
                    helper.assertTrue(stored(helper, sink) > 0, "the converter did not push FE into its neighbour");
                    Inspection inspection = inspect(helper, in);
                    helper.assertTrue(inspection.oks().contains("converter.charging"), "the inspector does not see the converter charging: " + inspection);
                    assertHealthy(helper, "TFMG to FE", in);
                    IEnergyStorage cap = energyCap(helper, in, Direction.SOUTH);
                    helper.assertTrue(cap.receiveEnergy(100, true) == 0, "FE can be pushed back into a converter that turns TFMG power into FE");
                    int before = cap.getEnergyStored();
                    int taken = cap.extractEnergy(500, false);
                    helper.assertTrue(taken == Math.min(500, before), "an FE machine could only pull " + taken + " FE out of the converter");
                })
                .thenSucceed();
    }

    // ========================================================== consumers

    /**
     * An electric motor turns at the speed its voltage gives, provides stress
     * capacity in proportion, and says when it has no power.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 300)
    public static void electricMotorFollowsVoltage(GameTestHelper helper) {
        int[] volts = {100, 200, 400};
        BlockPos[] generators = new BlockPos[volts.length];
        BlockPos[] motors = new BlockPos[volts.length];
        for (int i = 0; i < volts.length; i++) {
            generators[i] = new BlockPos(2 + 3 * i, 1, 2);
            creativeGenerator(helper, generators[i], volts[i]);
            motors[i] = generators[i].above();
            place(helper, motors[i], TFMGBlocks.ELECTRIC_MOTOR.get(), Direction.UP);
        }
        hub(helper, new BlockPos(2, 1, 6));
        BlockPos idle = new BlockPos(2, 2, 6);
        place(helper, idle, TFMGBlocks.ELECTRIC_MOTOR.get(), Direction.UP);
        double capacity = BlockStressValues.getCapacity(TFMGBlocks.ELECTRIC_MOTOR.get());
        float resistance = machines().electricMotorInternalResistance.getF();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (int i = 0; i < volts.length; i++)
                        expectMotor(helper, motors[i], volts[i], capacity, resistance);
                })
                .thenExecute(() -> {
                    assertHealthy(helper, "motors running", motors);
                    assertProblem(helper, idle, "electric.no_voltage", "motor without a generator");
                    setCreativeVoltage(helper, generators[0], 300);
                })
                .thenWaitUntil(() -> expectMotor(helper, motors[0], 300, capacity, resistance))
                .thenSucceed();
    }

    private static void expectMotor(GameTestHelper helper, BlockPos pos, int volts, double capacity, float resistance) {
        ElectricMotorBlockEntity motor = helper.getBlockEntity(pos);
        float speed = motorSpeed(volts);
        helper.assertTrue(motor.getData().getVoltage() == volts, "motor at " + motor.getData().getVoltage() + " V instead of " + volts);
        helper.assertTrue(Math.abs(Math.abs(motor.getSpeed()) - speed) < 0.01f, volts + " V: motor turns at " + motor.getSpeed() + " RPM instead of " + speed);
        float stress = motor.calculateAddedStressCapacity();
        helper.assertTrue(Math.abs(stress - capacity * speed / 256) < 0.01, volts + " V: motor offers " + stress + " SU/RPM instead of "
                + capacity * speed / 256);
        helper.assertTrue(Math.abs(motor.getCurrent() - volts / resistance) < 1e-3, volts + " V: motor draws " + motor.getCurrent() + " A");
    }

    /**
     * An electric pump moves fluid from one tank to the other once it has
     * power, pump against the tanks or with pipes in between, even when the
     * power arrives after it was placed.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 500)
    public static void electricPumpMovesFluidOncePowered(GameTestHelper helper) {
        BlockPos[] sources = {new BlockPos(1, 1, 2), new BlockPos(1, 1, 6)};
        BlockPos[] pumps = {new BlockPos(2, 1, 2), new BlockPos(3, 1, 6)};
        BlockPos[] targets = {new BlockPos(3, 1, 2), new BlockPos(5, 1, 6)};
        List<BlockPos> pipes = List.of(new BlockPos(2, 1, 6), new BlockPos(4, 1, 6));
        for (int i = 0; i < 2; i++) {
            helper.setBlock(sources[i], AllBlocks.FLUID_TANK.getDefaultState());
            helper.setBlock(targets[i], AllBlocks.FLUID_TANK.getDefaultState());
            place(helper, pumps[i], TFMGBlocks.ELECTRIC_PUMP.get(), Direction.EAST);
            hub(helper, pumps[i].above());
        }
        for (BlockPos pipe : pipes)
            helper.setBlock(pipe, AllBlocks.FLUID_PIPE.getDefaultState());
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    FluidPipeBlock pipeBlock = (FluidPipeBlock) AllBlocks.FLUID_PIPE.get();
                    for (BlockPos pipe : pipes) {
                        BlockPos abs = helper.absolutePos(pipe);
                        BlockState state = pipeBlock.updateBlockState(pipeBlock.defaultBlockState(), Direction.EAST, null, helper.getLevel(), abs);
                        helper.getLevel().setBlock(abs, state, 3);
                    }
                    for (BlockPos source : sources) {
                        IFluidHandler tank = fluidCap(helper, source);
                        helper.assertTrue(tank.fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE) == 4000, "the source tank refused water");
                    }
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    for (int i = 0; i < 2; i++) {
                        helper.assertTrue(fluidIn(helper, targets[i]) == 0, "an unpowered pump moved fluid");
                        assertProblem(helper, pumps[i], "electric.no_voltage", "pump unpowered");
                        creativeGenerator(helper, pumps[i].above(2), 200);
                    }
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(fluidIn(helper, targets[0]) > 0, "a powered pump between two tanks moves nothing");
                    helper.assertTrue(fluidIn(helper, targets[1]) > 0, "a powered pump between two pipes moves nothing");
                })
                .thenExecute(() -> assertHealthy(helper, "pumping", pumps))
                .thenSucceed();
    }

    /**
     * The polarizer turns magnetic alloy into a magnet when it draws enough
     * power, and stays idle and says why when it does not.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 500)
    public static void polarizerMagnetisesOnEnoughPower(GameTestHelper helper) {
        int[] volts = {250, 200, 0};
        BlockPos[] polarizers = new BlockPos[volts.length];
        for (int i = 0; i < volts.length; i++) {
            int z = 2 + 3 * i;
            if (volts[i] > 0)
                creativeGenerator(helper, new BlockPos(1, 1, z), volts[i]);
            polarizers[i] = new BlockPos(2, 1, z);
            place(helper, polarizers[i], TFMGBlocks.POLARIZER.get(), Direction.EAST);
        }
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    for (BlockPos pos : polarizers) {
                        IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), Direction.UP);
                        helper.assertTrue(items != null && items.insertItem(0, new ItemStack(TFMGItems.MAGNETIC_ALLOY_INGOT.get()), false).isEmpty(),
                                "the polarizer refused the ingot");
                    }
                })
                .thenWaitUntil(() -> {
                    PolarizerBlockEntity be = helper.getBlockEntity(polarizers[0]);
                    helper.assertTrue(be.capacitorPercentage > 20, "the polarizer at 250 V does not charge");
                })
                .thenExecute(() -> {
                    Inspection inspection = inspect(helper, polarizers[0]);
                    helper.assertTrue(inspection.oks().contains("polarizer.power_ok"), "the inspector does not see enough power: " + inspection);
                    assertHealthy(helper, "polarizing", polarizers[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(((PolarizerBlockEntity) helper.getBlockEntity(polarizers[0])).inventory.getStackInSlot(0)
                        .is(TFMGItems.MAGNET.get()), "no magnet came out of the polarizer"))
                .thenExecute(() -> {
                    for (int i = 1; i < volts.length; i++) {
                        PolarizerBlockEntity be = helper.getBlockEntity(polarizers[i]);
                        helper.assertTrue(be.capacitorPercentage == 0 && be.inventory.getStackInSlot(0).is(TFMGItems.MAGNETIC_ALLOY_INGOT.get()),
                                volts[i] + " V: the polarizer worked without enough power");
                        assertProblem(helper, polarizers[i], "polarizer.power_low", volts[i] + " V");
                    }
                    assertProblem(helper, polarizers[2], "electric.no_voltage", "no generator");
                })
                .thenSucceed();
    }

    /**
     * Every light shines in proportion to its voltage, goes dark without
     * power, and the bulbs switch off on a redstone signal.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void lightsShineOnlyWhenPowered(GameTestHelper helper) {
        List<BlockEntry<? extends Block>> lights = List.of(TFMGBlocks.LIGHT_BULB, TFMGBlocks.CIRCULAR_LIGHT, TFMGBlocks.MODERN_LIGHT,
                TFMGBlocks.ALUMINUM_LAMP, TFMGBlocks.NEON_TUBE);
        BlockPos[] generators = new BlockPos[lights.size()];
        BlockPos[] lamps = new BlockPos[lights.size()];
        for (int i = 0; i < lights.size(); i++) {
            generators[i] = new BlockPos(1 + 2 * i, 1, 2);
            creativeGenerator(helper, generators[i], 100);
            hub(helper, generators[i].above());
            lamps[i] = generators[i].above(2);
            helper.setBlock(lamps[i], facing(lights.get(i).get(), Direction.UP));
        }
        BlockPos dimmed = new BlockPos(1, 3, 7);
        creativeGenerator(helper, new BlockPos(1, 1, 7), 50);
        hub(helper, new BlockPos(1, 2, 7));
        place(helper, dimmed, TFMGBlocks.LIGHT_BULB.get(), Direction.UP);
        BlockPos redstone = dimmed.east();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (BlockPos lamp : lamps) {
                        helper.assertTrue(lightOf(helper, lamp) == 10, blockName(helper, lamp) + " at 100 V shines " + lightOf(helper, lamp) + " instead of 10");
                        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(lamp));
                        helper.assertTrue(state.getLightEmission(helper.getLevel(), helper.absolutePos(lamp)) == 10, blockName(helper, lamp) + " emits "
                                + state.getLightEmission(helper.getLevel(), helper.absolutePos(lamp)));
                    }
                    helper.assertTrue(lightOf(helper, dimmed) == 5, "a bulb at 50 V shines " + lightOf(helper, dimmed) + " instead of 5");
                })
                .thenExecute(() -> {
                    assertHealthy(helper, "lights on", lamps);
                    for (BlockPos generator : generators)
                        setCreativeVoltage(helper, generator, 0);
                    helper.setBlock(redstone, Blocks.REDSTONE_BLOCK.defaultBlockState());
                })
                .thenWaitUntil(() -> {
                    for (BlockPos lamp : lamps)
                        helper.assertTrue(lightOf(helper, lamp) == 0, blockName(helper, lamp) + " keeps shining without power");
                    helper.assertTrue(lightOf(helper, dimmed) == 0, "a bulb with a redstone signal keeps shining");
                })
                .thenExecute(() -> {
                    for (BlockPos lamp : lamps)
                        assertProblem(helper, lamp, "electric.no_voltage", "lights off");
                    assertProblem(helper, dimmed, "light.redstone_off", "redstone signal");
                    helper.setBlock(redstone, Blocks.AIR.defaultBlockState());
                })
                .thenWaitUntil(() -> helper.assertTrue(lightOf(helper, dimmed) == 5, "the bulb did not come back once the signal was gone"))
                .thenSucceed();
    }

    /**
     * A light on a network that cannot feed all its loads stays dark: an
     * undersupplied network runs nothing.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 300)
    public static void lightsStayDarkOnAnOverloadedNetwork(GameTestHelper helper) {
        int rpm = 80;
        int volts = generatorVoltage(rpm);
        BlockPos generator = motorDrivenGenerator(helper, new BlockPos(1, 1, 5), Direction.EAST, rpm);
        hub(helper, generator.east());
        BlockPos hog = generator.east().south();
        place(helper, hog, TFMGBlocks.RESISTOR.get(), Direction.SOUTH);
        setResistance(helper, hog, 1);
        BlockPos bulb = generator.east().above();
        BlockPos neon = generator.east().north();
        place(helper, bulb, TFMGBlocks.LIGHT_BULB.get(), Direction.UP);
        helper.setBlock(neon, TFMGBlocks.NEON_TUBE.getDefaultState()
                .setValue(BlockStateProperties.SOUTH, true).setValue(BlockStateProperties.UP, false).setValue(BlockStateProperties.DOWN, false));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(volts(helper, bulb) == volts, "the bulb is at " + volts(helper, bulb) + " V");
                    helper.assertTrue(volts(helper, neon) == volts, "the neon tube is at " + volts(helper, neon) + " V");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    // A 1 ohm load on this generator draws more than it makes.
                    helper.assertTrue(volts * volts > generatorPower(rpm), "the test load does not overload the generator");
                    helper.assertTrue(electric(helper, bulb).getData().notEnoughPower, "the overloaded network is not flagged");
                    helper.assertTrue(lightOf(helper, bulb) == 0, "a bulb shines on an overloaded network");
                    helper.assertTrue(lightOf(helper, neon) == 0, "a neon tube shines on an overloaded network");
                    assertProblem(helper, neon, "electric.not_enough_power", "overloaded");
                    setResistance(helper, hog, 5000);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(lightOf(helper, bulb) == Math.min(volts / 10, 15), "the bulb did not light once the load was light");
                    helper.assertTrue(lightOf(helper, neon) == Math.min(volts / 10, 15), "the neon did not light once the load was light");
                })
                .thenSucceed();
    }

    /** The traffic light cycles its three colours while powered and draws nothing without power. */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void trafficLightCyclesWhenPowered(GameTestHelper helper) {
        creativeGenerator(helper, new BlockPos(1, 1, 2), 100);
        hub(helper, new BlockPos(2, 1, 2));
        BlockPos light = new BlockPos(2, 2, 2);
        place(helper, light, TFMGBlocks.TRAFFIC_LIGHT.get(), Direction.NORTH);
        hub(helper, new BlockPos(6, 1, 2));
        BlockPos dark = new BlockPos(6, 2, 2);
        place(helper, dark, TFMGBlocks.TRAFFIC_LIGHT.get(), Direction.NORTH);
        Set<Integer> colours = new HashSet<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(volts(helper, light) == 100 && electric(helper, light).getPowerUsage() > 0,
                        "the traffic light is not powered (" + volts(helper, light) + " V)"))
                .thenExecuteFor(200, () -> colours.add(helper.getBlockEntity(light).saveWithoutMetadata(helper.getLevel().registryAccess()).getInt("Light")))
                .thenExecute(() -> {
                    helper.assertTrue(colours.size() == 3, "the traffic light showed " + colours + " in one cycle");
                    assertHealthy(helper, "traffic light", light);
                    helper.assertTrue(volts(helper, dark) == 0 && electric(helper, dark).getPowerUsage() == 0, "an unpowered traffic light draws power");
                    assertProblem(helper, dark, "electric.no_voltage", "no generator");
                })
                .thenSucceed();
    }

    /**
     * The voltmeter reads the block behind it in every mode, the voltage
     * observer drives redstone and a comparator, and the display link sources
     * report the network.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 300)
    public static void metersObserversAndDisplaysReadTheNetwork(GameTestHelper helper) {
        BlockPos generator = new BlockPos(1, 1, 2);
        creativeGenerator(helper, generator, 150);
        BlockPos hub = new BlockPos(2, 1, 2);
        hub(helper, hub);
        BlockPos resistor = new BlockPos(2, 2, 2);
        place(helper, resistor, TFMGBlocks.RESISTOR.get(), Direction.UP);
        BlockPos hubMeter = new BlockPos(3, 1, 2);
        place(helper, hubMeter, TFMGBlocks.VOLTMETER.get(), Direction.EAST);
        BlockPos resistorMeter = new BlockPos(3, 2, 2);
        place(helper, resistorMeter, TFMGBlocks.VOLTMETER.get(), Direction.EAST);
        BlockPos observer = new BlockPos(2, 1, 1);
        place(helper, observer, TFMGBlocks.VOLTAGE_OBSERVER.get(), Direction.SOUTH);
        BlockPos lamp = new BlockPos(1, 1, 1);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        BlockPos link = observer.above();
        helper.setBlock(link, AllBlocks.DISPLAY_LINK.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(volts(helper, resistor) == 150, "the resistor is at " + volts(helper, resistor) + " V");
                    VoltMeterBlockEntity meter = helper.getBlockEntity(hubMeter);
                    helper.assertTrue(meter.value == 150, "the voltmeter reads " + meter.value + " V on a 150 V hub");
                    helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(observer)).getValue(VoltageObserverBlock.POWERED),
                            "the voltage observer is not powered");
                    helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(lamp)).getValue(RedstoneLampBlock.LIT), "the observer does not light a lamp");
                })
                .thenExecute(() -> {
                    VoltMeterBlockEntity meter = helper.getBlockEntity(resistorMeter);
                    IElectric load = electric(helper, resistor);
                    expectReading(helper, meter, VoltMeterBlockEntity.MeasureMode.VOLTAGE, 150);
                    expectReading(helper, meter, VoltMeterBlockEntity.MeasureMode.CURRENT, load.getCurrent());
                    expectReading(helper, meter, VoltMeterBlockEntity.MeasureMode.RESISTANCE, 500);
                    expectReading(helper, meter, VoltMeterBlockEntity.MeasureMode.POWER, load.getPowerUsage());
                    expectReading(helper, meter, VoltMeterBlockEntity.MeasureMode.NETWORK_POWER_USAGE, load.getNetworkPowerUsage());

                    BlockState observerState = helper.getLevel().getBlockState(helper.absolutePos(observer));
                    int comparator = observerState.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(observer));
                    helper.assertTrue(comparator == ComparatorUtil.fractionToRedstoneLevel(150 / 250d), "the observer gives the comparator "
                            + comparator + " for 150 V");

                    DisplayLinkBlockEntity be = helper.getBlockEntity(link);
                    DisplayLinkContext context = new DisplayLinkContext(helper.getLevel(), be);
                    DisplayTargetStats stats = new DisplayTargetStats(1, 32, null);
                    ElectricalNetwork network = network(helper, hub);
                    String voltage = text(TFMGDisplaySources.VOLTAGE.get(), context, stats);
                    helper.assertTrue(voltage.equals(TFMGUtils.formatUnits(150, "V")), "the voltage display source shows '" + voltage + "'");
                    String id = text(TFMGDisplaySources.NETWORK_ID.get(), context, stats);
                    helper.assertTrue(id.equals(com.simibubi.create.foundation.utility.CreateLang.number((double) network.getId()).component().getString()),
                            "the network id display shows '" + id + "' for network " + network.getId());
                    for (DisplaySource source : List.of(TFMGDisplaySources.CURRENT.get(), TFMGDisplaySources.POWER_USAGE.get(), TFMGDisplaySources.RESISTANCE.get(),
                            TFMGDisplaySources.POWER_GENERATION.get(), TFMGDisplaySources.VOLTAGE_GENERATION.get(), TFMGDisplaySources.NETWORK_CONSUMPTION.get(),
                            TFMGDisplaySources.NETWORK_GENERATION.get(), TFMGDisplaySources.NETWORK_RESISTANCE.get()))
                        helper.assertTrue(!text(source, context, stats).isEmpty(), source.getClass().getSimpleName() + " shows nothing on a live network");
                    assertHealthy(helper, "metering", hub, resistor, observer);
                    helper.destroyBlock(generator);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(((VoltMeterBlockEntity) helper.getBlockEntity(hubMeter)).value == 0, "the voltmeter keeps a reading without power");
                    helper.assertTrue(!helper.getLevel().getBlockState(helper.absolutePos(observer)).getValue(VoltageObserverBlock.POWERED),
                            "the observer stays powered without voltage");
                    helper.assertTrue(!helper.getLevel().getBlockState(helper.absolutePos(lamp)).getValue(RedstoneLampBlock.LIT), "the lamp stays lit");
                })
                .thenExecute(() -> {
                    DisplayLinkContext context = new DisplayLinkContext(helper.getLevel(), helper.getBlockEntity(link));
                    String voltage = text(TFMGDisplaySources.VOLTAGE.get(), context, new DisplayTargetStats(1, 32, null));
                    helper.assertTrue(voltage.equals(TFMGUtils.formatUnits(0, "V")), "the voltage display shows '" + voltage + "' without power");
                    assertProblem(helper, resistor, "electric.no_voltage", "generator removed");
                })
                .thenSucceed();
    }

    private static void expectReading(GameTestHelper helper, VoltMeterBlockEntity meter, VoltMeterBlockEntity.MeasureMode mode, float expected) {
        meter.mode = mode;
        meter.lazyTick();
        float want = Math.min(expected, mode.defaultRange);
        helper.assertTrue(Math.abs(meter.value - want) < 1e-3, "the voltmeter in " + mode + " mode reads " + meter.value + " instead of " + want);
    }

    private static String text(DisplaySource source, DisplayLinkContext context, DisplayTargetStats stats) {
        List<MutableComponent> lines = source.provideText(context, stats);
        return lines.isEmpty() ? "" : lines.get(0).getString();
    }

    /** The freezer and the electrode holder run only on enough current, and say so. */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 300)
    public static void vatMachinesNeedEnoughCurrent(GameTestHelper helper) {
        BlockPos freezerVat = new BlockPos(2, 1, 2);
        helper.setBlock(freezerVat, TFMGBlocks.STEEL_CHEMICAL_VAT.getDefaultState());
        BlockPos freezer = freezerVat.above();
        helper.setBlock(freezer, TFMGBlocks.FREEZER.getDefaultState());
        BlockPos freezerGen = freezer.east();
        creativeGenerator(helper, freezerGen, 300);

        BlockPos electrodeVat = new BlockPos(7, 1, 2);
        helper.setBlock(electrodeVat, TFMGBlocks.STEEL_CHEMICAL_VAT.getDefaultState());
        BlockPos holder = electrodeVat.above();
        helper.setBlock(holder, TFMGBlocks.ELECTRODE_HOLDER.getDefaultState());
        hub(helper, holder.above());
        BlockPos holderGen = holder.above().east();
        creativeGenerator(helper, holderGen, 100);
        ((ElectrodeHolderBlockEntity) helper.getBlockEntity(holder)).setElectrode(TFMGElectrodes.copper.get(), false);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(((FreezerBlockEntity) helper.getBlockEntity(freezer)).isOperational(), "the freezer at 300 V does not run ("
                            + electric(helper, freezer).getCurrent() + " A)");
                    VatBlockEntity vat = helper.getBlockEntity(electrodeVat);
                    helper.assertTrue(((ElectrodeHolderBlockEntity) helper.getBlockEntity(holder)).canOperate(vat), "the electrode at 100 V does not run ("
                            + electric(helper, holder).getCurrent() + " A)");
                })
                .thenExecute(() -> {
                    expectInspector(helper, freezer, "freezer.current_ok", "freezer.low_current");
                    expectInspector(helper, holder, "electrode.current_ok", "electrode.low_current");
                    setCreativeVoltage(helper, freezerGen, 100);
                    setCreativeVoltage(helper, holderGen, 40);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(!((FreezerBlockEntity) helper.getBlockEntity(freezer)).isOperational(), "the freezer runs at 100 V");
                    VatBlockEntity vat = helper.getBlockEntity(electrodeVat);
                    helper.assertTrue(!((ElectrodeHolderBlockEntity) helper.getBlockEntity(holder)).canOperate(vat), "the electrode runs at 40 V");
                })
                .thenExecute(() -> {
                    assertProblem(helper, freezer, "freezer.low_current", "100 V");
                    assertProblem(helper, holder, "electrode.low_current", "40 V");
                })
                .thenSucceed();
    }

    private static void expectInspector(GameTestHelper helper, BlockPos pos, String okKey, String problemKey) {
        Inspection inspection = inspect(helper, pos);
        helper.assertTrue(inspection.oks().contains(okKey), blockName(helper, pos) + ": the inspector does not report " + okKey + ": " + inspection);
        helper.assertTrue(!inspection.problems().contains(problemKey), blockName(helper, pos) + ": the inspector reports " + problemKey);
        for (String problem : inspection.problems())
            helper.assertTrue(!problem.startsWith("electric."), blockName(helper, pos) + ": the inspector reports " + problem + " on a running machine");
    }

    // ======================================================== edge cases

    /**
     * Too much voltage burns a motor, too much current burns a bulb and a thin
     * hub, while a hub rated for the current carries it.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void overloadsBurnWhatTheyShould(GameTestHelper helper) {
        BlockPos motor = new BlockPos(1, 2, 2);
        creativeGenerator(helper, motor.below(), 2500);
        place(helper, motor, TFMGBlocks.ELECTRIC_MOTOR.get(), Direction.UP);

        BlockPos bulb = new BlockPos(4, 3, 2);
        creativeGenerator(helper, new BlockPos(4, 1, 2), 500);
        hub(helper, new BlockPos(4, 2, 2));
        place(helper, bulb, TFMGBlocks.LIGHT_BULB.get(), Direction.UP);

        BlockPos thin = new BlockPos(8, 2, 2);
        BlockPos thick = new BlockPos(8, 2, 7);
        for (BlockPos hub : new BlockPos[]{thin, thick}) {
            creativeGenerator(helper, hub.below(), 500);
            helper.setBlock(hub, (hub == thin ? TFMGBlocks.COPPER_CABLE_HUB : TFMGBlocks.STEEL_CABLE_HUB).getDefaultState());
            place(helper, hub.west(), TFMGBlocks.ELECTRIC_MOTOR.get(), Direction.WEST);
            place(helper, hub.east(), TFMGBlocks.ELECTRIC_MOTOR.get(), Direction.EAST);
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(motor) instanceof IElectric e && e.getData().getVoltage() == 2500,
                        "the motor never saw the 2500 V"))
                .thenExecute(() -> assertProblem(helper, motor, "electric.overvoltage", "2500 V on a motor"))
                .thenWaitUntil(() -> {
                    helper.assertTrue(isAir(helper, motor), "a motor on 2500 V survived");
                    helper.assertTrue(isAir(helper, bulb), "a bulb on 500 V (1.25 A) survived");
                    helper.assertTrue(isAir(helper, thin), "a copper hub carrying twice its current survived");
                })
                .thenExecute(() -> {
                    helper.assertTrue(!isAir(helper, thick), "a steel hub burned under a load it is rated for");
                    for (BlockPos pos : new BlockPos[]{thick.west(), thick.east()})
                        helper.assertTrue(((ElectricMotorBlockEntity) helper.getBlockEntity(pos)).getSpeed() != 0, "a motor behind the steel hub stopped");
                    assertHealthy(helper, "rated hub", thick.west(), thick.east());
                })
                .thenSucceed();
    }

    /**
     * Two networks joined by a cable become one that adds both generators,
     * split again when the joint breaks, and repeated joins never count a
     * generator twice or leave a stale network behind.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 600)
    public static void networksSplitAndMergeWithoutDoubleCounting(GameTestHelper helper) {
        BlockPos left = motorDrivenGenerator(helper, new BlockPos(1, 1, 2), Direction.EAST, 128);
        BlockPos right = motorDrivenGenerator(helper, new BlockPos(11, 1, 2), Direction.WEST, 128);
        hubs(helper, new BlockPos(3, 1, 2), Direction.EAST, 3);
        hubs(helper, new BlockPos(7, 1, 2), Direction.EAST, 3);
        BlockPos leftLoad = new BlockPos(5, 2, 2);
        BlockPos rightLoad = new BlockPos(7, 2, 2);
        place(helper, leftLoad, TFMGBlocks.RESISTOR.get(), Direction.UP);
        place(helper, rightLoad, TFMGBlocks.RESISTOR.get(), Direction.UP);
        BlockPos joint = new BlockPos(6, 1, 2);
        List<BlockPos> all = new ArrayList<>(List.of(left, right, leftLoad, rightLoad));
        for (int x = 3; x <= 9; x++)
            if (x != 6)
                all.add(new BlockPos(x, 1, 2));
        int one = generatorPower(128);
        var sequence = helper.startSequence()
                .thenWaitUntil(() -> expectSplit(helper, leftLoad, rightLoad, all, one));
        for (int round = 0; round < 3; round++) {
            sequence = sequence
                    .thenExecute(() -> hub(helper, joint))
                    .thenWaitUntil(() -> expectJoined(helper, leftLoad, rightLoad, all, joint, one))
                    .thenExecute(() -> helper.destroyBlock(joint))
                    .thenWaitUntil(() -> expectSplit(helper, leftLoad, rightLoad, all, one));
        }
        sequence.thenExecute(() -> hub(helper, joint))
                .thenWaitUntil(() -> expectJoined(helper, leftLoad, rightLoad, all, joint, one))
                .thenExecute(() -> assertHealthy(helper, "joined", left, right, joint, leftLoad, rightLoad))
                .thenSucceed();
    }

    private static void expectSplit(GameTestHelper helper, BlockPos leftLoad, BlockPos rightLoad, List<BlockPos> all, int one) {
        for (BlockPos load : new BlockPos[]{leftLoad, rightLoad}) {
            IElectric electric = electric(helper, load);
            helper.assertTrue(electric.getData().networkPowerGeneration == one && electric.getNetworkPowerGeneration() == one, "a split side reports "
                    + electric.getData().networkPowerGeneration + " W generated instead of " + one);
            helper.assertTrue(network(helper, load).getMembers().size() == 5, "a split side lists " + network(helper, load).getMembers().size()
                    + " members instead of 5");
        }
        helper.assertTrue(liveNetworkCount(helper, all) == 2, "the two sides use " + liveNetworkCount(helper, all) + " networks");
    }

    private static void expectJoined(GameTestHelper helper, BlockPos leftLoad, BlockPos rightLoad, List<BlockPos> all, BlockPos joint, int one) {
        List<BlockPos> members = new ArrayList<>(all);
        members.add(joint);
        helper.assertTrue(liveNetworkCount(helper, members) == 1, "the joined line uses " + liveNetworkCount(helper, members) + " networks");
        IElectric electric = electric(helper, leftLoad);
        helper.assertTrue(electric.getData().networkPowerGeneration == 2 * one && electric.getNetworkPowerGeneration() == 2 * one,
                "the joined line reports " + electric.getData().networkPowerGeneration + " W generated instead of " + 2 * one);
        helper.assertTrue(network(helper, leftLoad).getMembers().size() == 11, "the joined line lists " + network(helper, leftLoad).getMembers().size()
                + " members instead of 11");
        int keyed = 0;
        for (ElectricalNetwork network : networkMap(helper).values())
            for (BlockPos pos : members)
                if (network.getMembers().at(helper.absolutePos(pos).asLong()) != null) {
                    keyed++;
                    break;
                }
        helper.assertTrue(keyed == 1, keyed + " registered networks hold blocks of the joined line");
    }

    /**
     * A working network unloaded and loaded again, as a chunk reload does,
     * comes back with the same voltages, the same members, the same power and
     * every stored value intact.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 500)
    public static void networkSurvivesAReload(GameTestHelper helper) {
        List<BlockPos> all = new ArrayList<>();
        BlockPos generator = new BlockPos(1, 1, 2);
        creativeGenerator(helper, generator, 100);
        hub(helper, new BlockPos(2, 1, 2));
        BlockPos transformer = new BlockPos(3, 1, 2);
        place(helper, transformer, TFMGBlocks.TRANSFORMER.get(), Direction.SOUTH);
        TransformerBlockEntity tbe = helper.getBlockEntity(transformer);
        tbe.primaryCoil = coil(100);
        tbe.secondaryCoil = coil(200);
        tbe.updateCoils();
        hub(helper, new BlockPos(4, 1, 2));
        BlockPos near = new BlockPos(4, 2, 2);
        place(helper, near, TFMGBlocks.CABLE_CONNECTOR.get(), Direction.UP);
        hub(helper, new BlockPos(9, 1, 2));
        BlockPos far = new BlockPos(9, 2, 2);
        place(helper, far, TFMGBlocks.CABLE_CONNECTOR.get(), Direction.UP);
        BlockPos bank = new BlockPos(9, 1, 3);
        place(helper, bank, TFMGBlocks.ACCUMULATOR.get(), Direction.SOUTH);
        hub(helper, new BlockPos(9, 1, 4));
        BlockPos bulb = new BlockPos(9, 2, 4);
        place(helper, bulb, TFMGBlocks.LIGHT_BULB.get(), Direction.UP);
        BlockPos motor = new BlockPos(10, 1, 4);
        place(helper, motor, TFMGBlocks.ELECTRIC_MOTOR.get(), Direction.EAST);
        all.addAll(List.of(generator, new BlockPos(2, 1, 2), transformer, new BlockPos(4, 1, 2), near, new BlockPos(9, 1, 2), far, bank,
                new BlockPos(9, 1, 4), bulb, motor));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int[] before = new int[4];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> stringWire(helper, player, spool(TFMGItems.COPPER_SPOOL.get()), near, far))
                .thenWaitUntil(() -> {
                    helper.assertTrue(volts(helper, bulb) == 200, "the bulb is at " + volts(helper, bulb) + " V");
                    helper.assertTrue(energy(helper, bank) > 0, "the bank does not charge");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    before[0] = network(helper, bulb).getMembers().size();
                    before[1] = electric(helper, bulb).getData().networkPowerGeneration;
                    before[2] = network(helper, generator).getMembers().size();
                    before[3] = energy(helper, bank);
                    assertHealthy(helper, "before the reload", all.toArray(new BlockPos[0]));
                    unloadAndReload(helper, all);
                })
                .thenIdle(2)
                .thenWaitUntil(() -> {
                    helper.assertTrue(volts(helper, bulb) == 200, "after the reload the bulb is at " + volts(helper, bulb) + " V");
                    ElectricMotorBlockEntity m = helper.getBlockEntity(motor);
                    helper.assertTrue(Math.abs(Math.abs(m.getSpeed()) - motorSpeed(200)) < 0.01f, "after the reload the motor turns at " + m.getSpeed());
                    helper.assertTrue(network(helper, bulb).getMembers().size() == before[0], "after the reload the load side lists "
                            + network(helper, bulb).getMembers().size() + " members instead of " + before[0]);
                    helper.assertTrue(network(helper, generator).getMembers().size() == before[2], "after the reload the source side lists "
                            + network(helper, generator).getMembers().size() + " members instead of " + before[2]);
                    helper.assertTrue(electric(helper, bulb).getData().networkPowerGeneration == before[1], "after the reload the load side reports "
                            + electric(helper, bulb).getData().networkPowerGeneration + " W generated instead of " + before[1]);
                })
                .thenExecute(() -> {
                    helper.assertTrue(energy(helper, bank) >= before[3], "the bank lost charge in the reload (" + before[3] + " -> " + energy(helper, bank) + ")");
                    TransformerBlockEntity t = helper.getBlockEntity(transformer);
                    helper.assertTrue(t.coilRatio == 2f, "the transformer came back with a ratio of " + t.coilRatio);
                    helper.assertTrue(((CableConnectorBlockEntity) helper.getBlockEntity(near)).connections.size() == 1
                            && ((CableConnectorBlockEntity) helper.getBlockEntity(far)).connections.size() == 1, "the wire did not survive the reload");
                    assertHealthy(helper, "after the reload", all.toArray(new BlockPos[0]));
                })
                .thenSucceed();
    }

    // ===================================================== the whole chain

    /**
     * The full chain a player builds: a creative motor turns a generator, a
     * transformer steps the voltage up onto a long line of hubs and a wire, a
     * second one steps it back down to a bank of accumulators and a bus with
     * an electric motor grinding wheat in a millstone, lights, a voltmeter and
     * a voltage observer. Then the generator stops: the bank takes over. Then
     * the bank runs dry: everything stops.
     */
    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 1500)
    public static void fullPowerChainFromShaftToMillstone(GameTestHelper helper) {
        int z = 8;
        BlockPos creativeMotor = new BlockPos(0, 1, z);
        BlockPos generator = motorDrivenGenerator(helper, creativeMotor, Direction.EAST, 256);
        hub(helper, new BlockPos(2, 1, z));
        BlockPos stepUp = new BlockPos(3, 1, z);
        place(helper, stepUp, TFMGBlocks.TRANSFORMER.get(), Direction.SOUTH);
        hubs(helper, new BlockPos(4, 1, z), Direction.EAST, 3);
        BlockPos near = new BlockPos(6, 2, z);
        place(helper, near, TFMGBlocks.CABLE_CONNECTOR.get(), Direction.UP);
        BlockPos far = new BlockPos(14, 2, z);
        place(helper, far, TFMGBlocks.CABLE_CONNECTOR.get(), Direction.UP);
        hub(helper, new BlockPos(14, 1, z));
        BlockPos stepDown = new BlockPos(15, 1, z);
        place(helper, stepDown, TFMGBlocks.TRANSFORMER.get(), Direction.SOUTH);
        for (Object[] t : new Object[][]{{stepUp, 100, 200}, {stepDown, 200, 100}}) {
            TransformerBlockEntity be = helper.getBlockEntity((BlockPos) t[0]);
            be.primaryCoil = coil((int) t[1]);
            be.secondaryCoil = coil((int) t[2]);
            be.updateCoils();
        }
        hub(helper, new BlockPos(16, 1, z));
        BlockPos bank = new BlockPos(16, 1, z + 1);
        for (int i = 1; i <= 3; i++)
            place(helper, new BlockPos(16, 1, z + i), TFMGBlocks.ACCUMULATOR.get(), Direction.SOUTH);
        BlockPos bus = new BlockPos(16, 1, z + 4);
        hub(helper, bus);
        hub(helper, bus.south());
        BlockPos motor = bus.above();
        place(helper, motor, TFMGBlocks.ELECTRIC_MOTOR.get(), Direction.UP);
        BlockPos millstone = motor.above();
        helper.setBlock(millstone, AllBlocks.MILLSTONE.getDefaultState());
        BlockPos bulb = bus.east();
        place(helper, bulb, TFMGBlocks.LIGHT_BULB.get(), Direction.EAST);
        BlockPos bulb2 = bus.south().above();
        place(helper, bulb2, TFMGBlocks.LIGHT_BULB.get(), Direction.UP);
        BlockPos meter = bus.west();
        place(helper, meter, TFMGBlocks.VOLTMETER.get(), Direction.WEST);
        BlockPos observer = bus.south(2);
        place(helper, observer, TFMGBlocks.VOLTAGE_OBSERVER.get(), Direction.NORTH);
        BlockPos lamp = observer.east();
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());

        int genVolts = generatorVoltage(256);
        int lineVolts = genVolts * 2;
        int busVolts = (int) (lineVolts * 0.5f);
        int bankVolts = machines().accumulatorVoltage.get() * 3;
        BlockPos lineHub = new BlockPos(5, 1, z);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AtomicInteger mark = new AtomicInteger();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> stringWire(helper, player, spool(TFMGItems.COPPER_SPOOL.get()), near, far))
                .thenWaitUntil(() -> {
                    helper.assertTrue(volts(helper, generator) == genVolts, "the generator side is at " + volts(helper, generator) + " V, expected " + genVolts);
                    helper.assertTrue(volts(helper, lineHub) == lineVolts, "the line is at " + volts(helper, lineHub) + " V, expected " + lineVolts);
                    helper.assertTrue(volts(helper, far) == lineVolts, "the far end of the wire is at " + volts(helper, far) + " V");
                    helper.assertTrue(volts(helper, bus) == busVolts, "the bus is at " + volts(helper, bus) + " V, expected " + busVolts);
                    expectBusRunning(helper, motor, bulb, bulb2, meter, observer, lamp, busVolts);
                    helper.assertTrue(energy(helper, bank) > 0, "the bank does not charge from the line");
                })
                .thenExecute(() -> {
                    IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(millstone), Direction.UP);
                    helper.assertTrue(items != null && items.insertItem(0, new ItemStack(Items.WHEAT, 4), false).isEmpty(), "the millstone refused wheat");
                    assertHealthy(helper, "chain running", generator, new BlockPos(2, 1, z), stepUp, lineHub, near, far, stepDown, new BlockPos(16, 1, z),
                            bank, bus, motor, millstone, bulb, bulb2, observer);
                })
                .thenWaitUntil(() -> {
                    MillstoneBlockEntity be = helper.getBlockEntity(millstone);
                    boolean flour = false;
                    for (int i = 0; i < be.outputInv.getSlots(); i++)
                        flour |= be.outputInv.getStackInSlot(i).is(AllItems.WHEAT_FLOUR.get());
                    helper.assertTrue(flour, "the millstone turned by the electric motor made no flour");
                })
                .thenExecute(() -> {
                    helper.getBlockEntity(millstone).getLevel().removeBlock(helper.absolutePos(millstone), false);
                    setMotorSpeed(helper, creativeMotor, 0);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(volts(helper, lineHub) == 0, "the line keeps " + volts(helper, lineHub) + " V with the generator stopped");
                    helper.assertTrue(volts(helper, bus) == bankVolts, "on the bank the bus is at " + volts(helper, bus) + " V, the bank is rated " + bankVolts);
                    expectBusRunning(helper, motor, bulb, bulb2, meter, observer, lamp, bankVolts);
                })
                .thenExecute(() -> {
                    Inspection inspection = inspect(helper, bank);
                    helper.assertTrue(inspection.oks().contains("accumulator.discharging"), "the inspector does not see the bank discharging: " + inspection);
                    assertHealthy(helper, "on the bank", bank, bus, motor, bulb, bulb2, observer);
                    mark.set(energy(helper, bank));
                })
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(energy(helper, bank) < mark.get(), "the bank does not discharge");
                    ((AccumulatorBlockEntity) helper.getBlockEntity(bank)).energy.setEnergy(200);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(energy(helper, bank) == 0, "the bank never ran dry");
                    helper.assertTrue(volts(helper, bus) == 0, "the bus keeps " + volts(helper, bus) + " V on an empty bank");
                    helper.assertTrue(((ElectricMotorBlockEntity) helper.getBlockEntity(motor)).getSpeed() == 0, "the motor turns on an empty bank");
                    helper.assertTrue(lightOf(helper, bulb) == 0 && lightOf(helper, bulb2) == 0, "a bulb shines on an empty bank");
                    helper.assertTrue(((VoltMeterBlockEntity) helper.getBlockEntity(meter)).value == 0, "the voltmeter keeps a reading");
                    helper.assertTrue(!helper.getLevel().getBlockState(helper.absolutePos(observer)).getValue(VoltageObserverBlock.POWERED), "the observer stays on");
                    helper.assertTrue(!helper.getLevel().getBlockState(helper.absolutePos(lamp)).getValue(RedstoneLampBlock.LIT), "the lamp stays lit");
                })
                .thenExecute(() -> {
                    assertProblem(helper, bank, "accumulator.empty", "bank dry");
                    assertProblem(helper, bulb, "electric.no_voltage", "bank dry");
                    assertProblem(helper, motor, "electric.no_voltage", "bank dry");
                })
                .thenSucceed();
    }

    private static void expectBusRunning(GameTestHelper helper, BlockPos motor, BlockPos bulb, BlockPos bulb2, BlockPos meter, BlockPos observer,
                                         BlockPos lamp, int volts) {
        ElectricMotorBlockEntity m = helper.getBlockEntity(motor);
        helper.assertTrue(Math.abs(Math.abs(m.getSpeed()) - motorSpeed(volts)) < 0.01f, "the motor turns at " + m.getSpeed() + " RPM on " + volts + " V");
        int light = Math.min(volts / 10, 15);
        helper.assertTrue(lightOf(helper, bulb) == light && lightOf(helper, bulb2) == light, "the bulbs shine " + lightOf(helper, bulb) + " and "
                + lightOf(helper, bulb2) + " instead of " + light);
        helper.assertTrue(((VoltMeterBlockEntity) helper.getBlockEntity(meter)).value == volts, "the voltmeter reads "
                + ((VoltMeterBlockEntity) helper.getBlockEntity(meter)).value + " instead of " + volts);
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(observer)).getValue(VoltageObserverBlock.POWERED), "the observer is off");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(lamp)).getValue(RedstoneLampBlock.LIT), "the observer's lamp is off");
    }

    // ============================================================ helpers

    private static int energy(GameTestHelper helper, BlockPos accumulator) {
        AccumulatorBlockEntity be = helper.getBlockEntity(accumulator);
        if (!be.isController() && helper.getLevel().getBlockEntity(be.controller) instanceof AccumulatorBlockEntity controller)
            return controller.energy.getEnergyStored();
        return be.energy.getEnergyStored();
    }

    private static int stored(GameTestHelper helper, BlockPos pos) {
        IEnergyStorage cap = energyCap(helper, pos, null);
        return cap == null ? 0 : cap.getEnergyStored();
    }

    private static IEnergyStorage energyCap(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(pos), side);
    }

    private static IFluidHandler fluidCap(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), Direction.UP);
    }

    private static int fluidIn(GameTestHelper helper, BlockPos tank) {
        IFluidHandler handler = fluidCap(helper, tank);
        int amount = 0;
        for (int i = 0; i < handler.getTanks(); i++)
            amount += handler.getFluidInTank(i).getAmount();
        return amount;
    }

    private static int lightOf(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(pos));
        return state.hasProperty(LightBulbBlock.LIGHT) ? state.getValue(LightBulbBlock.LIGHT) : -1;
    }

    private static boolean isAir(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockState(helper.absolutePos(pos)).isAir();
    }

    private static void setResistance(GameTestHelper helper, BlockPos pos, int ohms) {
        ItemStack item = new ItemStack(TFMGBlocks.RESISTOR.get());
        item.set(TFMGDataComponents.RESISTANCE, ohms);
        ResistorBlockEntity be = helper.getBlockEntity(pos);
        be.setResistance(item);
        be.updateNextTick();
    }

    /** The arguments of the first inspector line with this key, or null. */
    private static Object[] argsOf(GameTestHelper helper, BlockPos pos, String key) {
        ServerLevel level = helper.getLevel();
        for (net.minecraft.network.chat.Component line : com.drmangotea.tfmg.content.items.inspector.FactoryInspectorItem
                .inspect(level, helper.absolutePos(pos), new com.drmangotea.tfmg.content.items.inspector.InspectionReport()))
            for (net.minecraft.network.chat.Component sibling : line.getSiblings())
                if (sibling.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                        && t.getKey().equals("tfmg.inspector." + key))
                    return t.getArgs();
        return null;
    }
}
