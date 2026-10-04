package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.electricity.generators.GeneratorBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.winding_machine.WindingMachineBlockEntity;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.google.gson.JsonObject;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Map;

/**
 * End-to-end production tests: each one builds a working machine, feeds it
 * the way a player's factory would, and waits for the product. These are the
 * steps the whole progression rests on.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGProductionTests {

    private static final BlockPos CENTER = new BlockPos(2, 1, 2);

    /** The handbook's 3-layer furnace, fed through its output, makes molten steel. */
    @GameTest(template = "gametest/platform_huge", batch = "tfmg_production", timeoutTicks = 1200)
    public static void blastFurnaceMakesSteel(GameTestHelper helper) {
        BlockPos origin = new BlockPos(5, 1, 5);
        JsonObject blueprint = TFMGStructureTests.readBlueprints().get("blast_furnace_1");
        TFMGGameTestUtil.check(helper, blueprint != null, "blueprint blast_furnace_1 missing");
        Map<BlockPos, BlockState> blocks = TFMGStructureTests.blocksOf(blueprint);
        BlockPos output = null;
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            helper.setBlock(origin.offset(e.getKey()), e.getValue());
            if (e.getValue().is(TFMGBlocks.BLAST_FURNACE_OUTPUT.get()))
                output = origin.offset(e.getKey());
        }
        BlockPos out = output;
        helper.runAfterDelay(30, () -> {
            IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(out), Direction.UP);
            TFMGGameTestUtil.check(helper, items != null, "the blast furnace output exposes no item handler");
            insert(items, new ItemStack(TFMGItems.COAL_COKE_DUST.get(), 8));
            insert(items, new ItemStack(TFMGItems.LIMESAND.get(), 4));
            insert(items, new ItemStack(AllItems.CRUSHED_IRON.get(), 2));
        });
        helper.succeedWhen(() -> {
            // Keep every hatch topped up with hot air, as a blast stove would.
            for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet())
                if (e.getValue().is(TFMGBlocks.BLAST_FURNACE_HATCH.get())) {
                    IFluidHandler hatch = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                            helper.absolutePos(origin.offset(e.getKey())), Direction.UP);
                    if (hatch != null)
                        hatch.fill(new FluidStack((Fluid) TFMGFluids.HOT_AIR.getSource(), 4000), IFluidHandler.FluidAction.EXECUTE);
                }
            IFluidHandler tanks = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(out), Direction.UP);
            int steel = 0;
            for (int i = 0; tanks != null && i < tanks.getTanks(); i++)
                if (tanks.getFluidInTank(i).getFluid().isSame(TFMGFluids.MOLTEN_STEEL.getSource()))
                    steel += tanks.getFluidInTank(i).getAmount();
            helper.assertTrue(steel >= 144, "no molten steel yet (" + steel + " mB)");
        });
    }

    /** A single coke oven turns coal into coal coke when its tanks are kept empty. */
    @GameTest(template = "gametest/platform", batch = "tfmg_production", timeoutTicks = 1600)
    public static void cokeOvenMakesCoke(GameTestHelper helper) {
        helper.setBlock(CENTER, TFMGBlocks.COKE_OVEN.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        helper.runAfterDelay(20, () -> {
            IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(CENTER), Direction.NORTH);
            TFMGGameTestUtil.check(helper, items != null, "the coke oven exposes no item handler");
            insert(items, new ItemStack(Items.COAL, 1));
        });
        helper.succeedWhen(() -> {
            // Pump the by-products away, as a working setup must.
            for (Direction side : new Direction[]{Direction.UP, Direction.NORTH}) {
                IFluidHandler tank = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(CENTER), side);
                if (tank != null)
                    tank.drain(8000, IFluidHandler.FluidAction.EXECUTE);
            }
            boolean coke = false;
            for (ItemEntity entity : helper.getEntities(EntityType.ITEM, CENTER, 3))
                coke |= entity.getItem().is(TFMGItems.COAL_COKE.get());
            helper.assertTrue(coke, "no coal coke dropped yet");
        });
    }

    /** A turning air intake fills its tank with air. */
    @GameTest(template = "gametest/platform", batch = "tfmg_production", timeoutTicks = 200)
    public static void airIntakeMakesAir(GameTestHelper helper) {
        helper.setBlock(CENTER, TFMGBlocks.AIR_INTAKE.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
        motor(helper, CENTER.south(), Direction.NORTH, 128);
        helper.succeedWhen(() -> {
            IFluidHandler tank = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(CENTER), Direction.UP);
            helper.assertTrue(tank != null && tank.getFluidInTank(0).getAmount() > 0, "no air produced");
        });
    }

    /** Copper wire in the winding machine winds onto an empty spool. */
    @GameTest(template = "gametest/platform", batch = "tfmg_production", timeoutTicks = 300)
    public static void windingMachineWindsWire(GameTestHelper helper) {
        helper.setBlock(CENTER, TFMGBlocks.WINDING_MACHINE.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        // Its shaft faces the counter-clockwise side of its facing: west.
        motor(helper, CENTER.west(), Direction.EAST, 64);
        helper.runAfterDelay(5, () -> {
            WindingMachineBlockEntity machine = helper.getBlockEntity(CENTER);
            machine.spool = TFMGItems.EMPTY_SPOOL.asStack();
            machine.inventory.setStackInSlot(0, new ItemStack(TFMGItems.COPPER_WIRE.get(), 1));
        });
        helper.succeedWhen(() -> {
            WindingMachineBlockEntity machine = helper.getBlockEntity(CENTER);
            helper.assertTrue(machine.spool.is(TFMGItems.COPPER_SPOOL.get())
                    && machine.spool.getOrDefault(TFMGDataComponents.SPOOL_AMOUNT, 0) > 10, "the spool is not winding");
        });
    }

    /** A generator turned above its minimum speed produces voltage. */
    @GameTest(template = "gametest/platform", batch = "tfmg_production", timeoutTicks = 200)
    public static void generatorMakesVoltage(GameTestHelper helper) {
        helper.setBlock(CENTER, TFMGBlocks.GENERATOR.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
        motor(helper, CENTER.north(), Direction.SOUTH, 128);
        helper.succeedWhen(() -> {
            GeneratorBlockEntity generator = helper.getBlockEntity(CENTER);
            helper.assertTrue(generator.voltageGeneration() > 0, "the generator produces no voltage at 128 RPM");
        });
    }

    private static void motor(GameTestHelper helper, BlockPos pos, Direction facing, int rpm) {
        helper.setBlock(pos, AllBlocks.CREATIVE_MOTOR.get().defaultBlockState().setValue(BlockStateProperties.FACING, facing));
        helper.runAfterDelay(2, () -> {
            CreativeMotorBlockEntity motor = helper.getBlockEntity(pos);
            motor.generatedSpeed.setValue(rpm);
        });
    }

    private static void insert(IItemHandler handler, ItemStack stack) {
        for (int slot = 0; slot < handler.getSlots() && !stack.isEmpty(); slot++)
            stack = handler.insertItem(slot, stack, false);
    }
}
