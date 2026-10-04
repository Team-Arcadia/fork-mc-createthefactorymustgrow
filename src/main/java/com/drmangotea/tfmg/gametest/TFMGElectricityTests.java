package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.electricity.base.ElectricalNetwork;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.content.electricity.utilities.electric_motor.ElectricMotorBlockEntity;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Electrical network tests: a large grid must form one network and keep
 * working through the "every member reconnects" storm a chunk load causes,
 * and a cut line must stop the consumer and start it again once repaired.
 * The storm test logs how long the reconnect took, as a regression marker
 * for the network rebuild cost.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGElectricityTests {

    // A 10 x 5 x 10 block of hubs: 500 members in a single grid.
    private static final int SX = 10, SY = 5, SZ = 10;

    @GameTest(template = "gametest/platform_large", batch = "tfmg_electric_grid", timeoutTicks = 400)
    public static void largeGridSurvivesReconnectStorm(GameTestHelper helper) {
        List<BlockPos> hubs = new ArrayList<>();
        for (int x = 1; x <= SX; x++)
            for (int y = 1; y <= SY; y++)
                for (int z = 1; z <= SZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    hubs.add(pos);
                    helper.setBlock(pos, TFMGBlocks.STEEL_CABLE_HUB.get().defaultBlockState());
                }
        // Generator under one corner, motor on top of the opposite corner with
        // its back face (the power input) on the grid.
        BlockPos generator = new BlockPos(1, 1, 0);
        BlockPos motor = new BlockPos(SX, SY + 1, SZ);
        helper.setBlock(generator, TFMGBlocks.CREATIVE_GENERATOR.get().defaultBlockState());
        helper.setBlock(motor, TFMGBlocks.ELECTRIC_MOTOR.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));

        helper.runAfterDelay(60, () -> {
            assertOneNetwork(helper, hubs, generator, motor, "after placement");
            // What a chunk load does to every member: reconnect next tick.
            for (BlockPos pos : hubs)
                if (helper.getBlockEntity(pos) instanceof IElectric electric)
                    electric.getData().connectNextTick = true;
            long start = System.nanoTime();
            helper.runAfterDelay(1, () -> {
                long ms = (System.nanoTime() - start) / 1_000_000;
                TFMG.LOGGER.info("[gametest] reconnect storm over {} members took {} ms", hubs.size() + 2, ms);
            });
            helper.runAfterDelay(80, () -> {
                assertOneNetwork(helper, hubs, generator, motor, "after the reconnect storm");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "gametest/platform_large", batch = "tfmg_electric", timeoutTicks = 400)
    public static void cutLineStopsAndRepairRestarts(GameTestHelper helper) {
        // Generator, 9 hubs in a row, motor at the end facing away.
        BlockPos generator = new BlockPos(1, 1, 5);
        BlockPos motor = new BlockPos(11, 1, 5);
        helper.setBlock(generator, TFMGBlocks.CREATIVE_GENERATOR.get().defaultBlockState());
        for (int x = 2; x <= 10; x++)
            helper.setBlock(new BlockPos(x, 1, 5), TFMGBlocks.STEEL_CABLE_HUB.get().defaultBlockState());
        helper.setBlock(motor, TFMGBlocks.ELECTRIC_MOTOR.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        BlockPos cut = new BlockPos(6, 1, 5);

        helper.runAfterDelay(60, () -> {
            check(helper, speed(helper, motor) != 0, "the motor does not turn on the intact line");
            helper.destroyBlock(cut);
            helper.runAfterDelay(60, () -> {
                check(helper, speed(helper, motor) == 0, "the motor still turns with the line cut");
                helper.setBlock(cut, TFMGBlocks.STEEL_CABLE_HUB.get().defaultBlockState());
                helper.runAfterDelay(60, () -> {
                    check(helper, speed(helper, motor) != 0, "the motor did not restart once the line was repaired");
                    helper.succeed();
                });
            });
        });
    }

    private static float speed(GameTestHelper helper, BlockPos motor) {
        return helper.getBlockEntity(motor) instanceof ElectricMotorBlockEntity be ? be.getSpeed() : 0;
    }

    private static void assertOneNetwork(GameTestHelper helper, List<BlockPos> hubs, BlockPos generator, BlockPos motor, String when) {
        Set<Long> ids = new HashSet<>();
        List<BlockPos> all = new ArrayList<>(hubs);
        all.add(generator);
        all.add(motor);
        ElectricalNetwork network = null;
        for (BlockPos pos : all) {
            BlockEntity be = helper.getBlockEntity(pos);
            if (!(be instanceof IElectric electric)) {
                helper.fail("no electric block at " + pos + " " + when);
                return;
            }
            ids.add(electric.getData().getId());
            network = electric.getOrCreateElectricNetwork();
        }
        check(helper, ids.size() == 1, "the grid split into " + ids.size() + " network ids " + when);
        int members = network.getMembers().size();
        check(helper, members == all.size(), "the network lists " + members + " members, expected " + all.size() + " " + when);
        check(helper, speed(helper, motor) != 0, "the motor is not turning " + when);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        TFMGGameTestUtil.check(helper, condition, message);
    }
}
