package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.engines.types.large_engine.LargeEngineBlockEntity;
import com.drmangotea.tfmg.content.engines.types.regular_engine.RegularEngineBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.blast_stove.BlastStoveBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.casting_basin.CastingBasinBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.concrete_hose.ConcreteHoseBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.firebox.FireboxBlock;
import com.drmangotea.tfmg.content.machinery.misc.firebox.FireboxBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.controller.DistillationControllerBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity;
import com.drmangotea.tfmg.mixin.accessor.TankSegmentAccessor;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.drmangotea.tfmg.registry.TFMGTags;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Every machine input takes only the fluids meant for it. A tank that took
 * any fluid filled with whatever a neighbouring pipe carried the moment it
 * ran dry, no recipe matched again and, since pipes may not empty an input,
 * the machine was dead for good. Each test pushes the wrong fluid where a
 * badly placed pipe would, checks that it is refused, then checks that the
 * machine still runs on the right one. The load tests stand in for worlds
 * saved by an older version, where the wrong fluid is already in the tank:
 * it is voided on load and the machine recovers.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGFluidSlotTests {

    static final String BATCH = "tfmg_fluid_slots";
    static final String SMALL = "gametest/platform";
    static final String LARGE = "gametest/platform_large";
    static final String HUGE = "gametest/platform_huge";
    static final int PUMP_RPM = 128;

    static Fluid fluid(String id) {
        return TFMGOilEngineTestKit.fluid(id);
    }

    static IFluidHandler face(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), side);
    }

    static int push(IFluidHandler handler, Fluid fluid, int amount) {
        return handler == null ? -1 : handler.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    static String held(FluidTank tank) {
        return tank.isEmpty() ? "nothing" : tank.getFluidAmount() + " mB of " + tank.getFluid().getHoverName().getString();
    }

    // ========================================================= blast stove

    /**
     * The reported bug, built the way a player builds it: a 2x2x2 stove with
     * creosote pumped into the top, its hot air pumped out of the top into a
     * tank, an air line on one side and a creosote line that also touches a
     * side of the bottom layer. The stove burns its air until the air tank is
     * empty. The creosote pushing at the side must not take the air tank,
     * hot air flowing back must not take the fuel tank, and once air comes
     * back the stove makes hot air again.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 1600)
    public static void blastStoveAirTankRefusesCreosote(GameTestHelper helper) {
        BlockPos stove = new BlockPos(4, 2, 4);
        BlockPos hotAirTank = new BlockPos(5, 5, 5);
        BlockPos airMotor = new BlockPos(7, 2, 6);
        Fluid air = fluid("tfmg:air");
        Fluid hotAir = fluid("tfmg:hot_air");
        Fluid creosote = fluid("tfmg:creosote");
        for (int y = 2; y <= 3; y++)
            for (int x = 4; x <= 5; x++)
                for (int z = 4; z <= 5; z++)
                    helper.setBlock(new BlockPos(x, y, z), TFMGBlocks.BLAST_STOVE.get().defaultBlockState());
        // Creosote into the top.
        TFMGFactoryKit.creativeTank(helper, new BlockPos(4, 5, 4), creosote);
        TFMGFactoryKit.pump(helper, new BlockPos(4, 4, 4), Direction.DOWN, Direction.WEST, PUMP_RPM);
        // The stray creosote line, pushing into a side of the bottom layer.
        TFMGFactoryKit.creativeTank(helper, new BlockPos(2, 2, 4), creosote);
        TFMGFactoryKit.pump(helper, new BlockPos(3, 2, 4), Direction.EAST, Direction.NORTH, PUMP_RPM);
        // The air line, idle until the second half of the test.
        TFMGFactoryKit.creativeTank(helper, new BlockPos(7, 2, 5), air);
        helper.setBlock(new BlockPos(6, 2, 5), AllBlocks.MECHANICAL_PUMP.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST));
        TFMGFactoryKit.cog(helper, new BlockPos(6, 2, 6), Direction.Axis.X);
        TFMGFactoryKit.motor(helper, airMotor, Direction.WEST, 0);
        // Hot air out of the top into a plain tank, as a player drains it.
        TFMGFactoryKit.pump(helper, new BlockPos(5, 4, 5), Direction.UP, Direction.EAST, PUMP_RPM);
        TFMGFactoryKit.tank(helper, hotAirTank);

        int[] firstRun = {0};
        boolean[] airGone = {false};
        String[] hijacked = {null};
        helper.onEachTick(() -> {
            BlastStoveBlockEntity be = helper.getBlockEntity(stove);
            BlastStoveBlockEntity controller = be == null ? null : be.getControllerBE();
            if (controller == null || hijacked[0] != null)
                return;
            if (!controller.primaryInputInventory.isEmpty() && !controller.primaryInputInventory.getFluid().getFluid().isSame(air))
                hijacked[0] = "the air tank took " + held(controller.primaryInputInventory);
            if (!controller.secondaryInputInventory.isEmpty() && !controller.secondaryInputInventory.getFluid().getFluid().isSame(creosote))
                hijacked[0] = "the fuel tank took " + held(controller.secondaryInputInventory);
        });

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    BlastStoveBlockEntity controller = ((BlastStoveBlockEntity) helper.getBlockEntity(stove)).getControllerBE();
                    helper.assertTrue(controller != null && controller.getTotalTankSize() == 8, "the stove did not form a 2x2x2 multiblock");
                    // What an intake line delivers before it stops: 400 mB, two cycles of this stove.
                    int filled = push(face(helper, new BlockPos(5, 2, 5), Direction.EAST), air, 400);
                    helper.assertTrue(filled == 400, "the stove took " + filled + " mB of air instead of 400");
                })
                .thenWaitUntil(() -> {
                    BlastStoveBlockEntity controller = ((BlastStoveBlockEntity) helper.getBlockEntity(stove)).getControllerBE();
                    helper.assertTrue(hijacked[0] == null, hijacked[0]);
                    helper.assertTrue(controller.primaryInputInventory.isEmpty(), "the stove has not burnt its air yet: " + held(controller.primaryInputInventory));
                    helper.assertTrue(TFMGFactoryKit.amount(helper, hotAirTank, hotAir) + controller.primaryOutputInventory.getFluidAmount() == 400,
                            "400 mB of air did not become 400 mB of hot air");
                })
                .thenExecute(() -> airGone[0] = true)
                // The air tank stays empty while the creosote line pushes at it.
                .thenIdle(60)
                .thenExecute(() -> {
                    BlastStoveBlockEntity controller = ((BlastStoveBlockEntity) helper.getBlockEntity(stove)).getControllerBE();
                    helper.assertTrue(hijacked[0] == null, hijacked[0]);
                    helper.assertTrue(controller.primaryInputInventory.isEmpty(), "the empty air tank took " + held(controller.primaryInputInventory));
                    helper.assertTrue(controller.secondaryInputInventory.getFluid().getFluid().isSame(creosote), "the fuel tank lost its creosote");
                    // Neither tank takes the wrong fluid through any face.
                    helper.assertTrue(push(face(helper, stove, Direction.WEST), creosote, 100) == 0, "creosote pushed into a side went in");
                    helper.assertTrue(push(face(helper, stove, Direction.WEST), hotAir, 100) == 0, "hot air pushed into a side went in");
                    helper.assertTrue(push(face(helper, new BlockPos(4, 3, 4), Direction.UP), hotAir, 100) == 0, "hot air pushed back into the top went in");
                    helper.assertTrue(push(face(helper, new BlockPos(4, 3, 4), Direction.UP), air, 100) == 0, "air pushed into the top went in");
                    firstRun[0] = TFMGFactoryKit.amount(helper, hotAirTank, hotAir) + controller.primaryOutputInventory.getFluidAmount();
                    // The air comes back.
                    TFMGFactoryKit.setMotor(helper, airMotor, PUMP_RPM);
                })
                .thenWaitUntil(() -> {
                    BlastStoveBlockEntity controller = ((BlastStoveBlockEntity) helper.getBlockEntity(stove)).getControllerBE();
                    helper.assertTrue(hijacked[0] == null, hijacked[0]);
                    int made = TFMGFactoryKit.amount(helper, hotAirTank, hotAir) + controller.primaryOutputInventory.getFluidAmount();
                    helper.assertTrue(made >= firstRun[0] + 400, "no hot air since the air came back (" + made + " mB in all, "
                            + firstRun[0] + " before; air tank " + held(controller.primaryInputInventory) + ")");
                })
                .thenSucceed();
    }

    /**
     * A stove saved by an older version with creosote in its air tank and
     * hot air in its fuel tank: both are voided on load, and the stove then
     * makes hot air from the air and creosote piped in.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 800)
    public static void blastStoveClearsWrongFluidOnLoad(GameTestHelper helper) {
        BlockPos stove = new BlockPos(2, 2, 2);
        helper.setBlock(stove, TFMGBlocks.BLAST_STOVE.get().defaultBlockState());
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    BlastStoveBlockEntity be = helper.getBlockEntity(stove);
                    be.primaryInputInventory.setFluid(new FluidStack(fluid("tfmg:creosote"), 1000));
                    be.secondaryInputInventory.setFluid(new FluidStack(fluid("tfmg:hot_air"), 500));
                    helper.assertTrue(push(face(helper, stove, Direction.NORTH), fluid("tfmg:air"), 25) == 0,
                            "air went into a tank already holding creosote");
                    TFMGPowerKit.unloadAndReload(helper, List.of(stove));
                })
                .thenExecuteAfter(3, () -> {
                    BlastStoveBlockEntity be = helper.getBlockEntity(stove);
                    helper.assertTrue(be.primaryInputInventory.isEmpty(), "the air tank still holds " + held(be.primaryInputInventory));
                    helper.assertTrue(be.secondaryInputInventory.isEmpty(), "the fuel tank still holds " + held(be.secondaryInputInventory));
                    helper.assertTrue(push(face(helper, stove, Direction.NORTH), fluid("tfmg:air"), 25) == 25, "the cleared air tank refused air");
                    helper.assertTrue(push(face(helper, stove, Direction.UP), fluid("tfmg:creosote"), 5) == 5, "the cleared fuel tank refused creosote");
                })
                .thenWaitUntil(() -> {
                    BlastStoveBlockEntity be = helper.getBlockEntity(stove);
                    helper.assertTrue(be.primaryOutputInventory.getFluidAmount() == 25, "no hot air yet: " + held(be.primaryOutputInventory));
                })
                .thenSucceed();
    }

    // ============================================================= firebox

    /**
     * The firebox takes only firebox fuel: neither its own CO2 flowing back
     * nor water goes in, a firebox saved holding water clears it on load, and
     * it then lights on kerosene.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 400)
    public static void fireboxTakesOnlyFuel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, TFMGBlocks.FIREBOX.get().defaultBlockState());
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    IFluidHandler handler = face(helper, pos, Direction.UP);
                    helper.assertTrue(push(handler, fluid("tfmg:carbon_dioxide"), 500) == 0, "the firebox took its own CO2 as fuel");
                    helper.assertTrue(push(handler, Fluids.WATER, 500) == 0, "the firebox took water as fuel");
                    FireboxBlockEntity be = helper.getBlockEntity(pos);
                    ((FluidTank) be.getTankInventory()).setFluid(new FluidStack(Fluids.WATER, 1000));
                    TFMGPowerKit.unloadAndReload(helper, List.of(pos));
                })
                .thenExecuteAfter(3, () -> {
                    FireboxBlockEntity be = helper.getBlockEntity(pos);
                    helper.assertTrue(be.getTankInventory().getFluidAmount() == 0, "the water was not cleared on load");
                    helper.assertTrue(push(face(helper, pos, Direction.UP), fluid("tfmg:kerosene"), 1000) == 1000, "the cleared firebox refused kerosene");
                })
                .thenWaitUntil(() -> helper.assertBlockState(pos,
                        s -> s.getValue(FireboxBlock.HEAT_LEVEL) != BlazeBurnerBlock.HeatLevel.NONE, () -> "the firebox never lit"))
                .thenSucceed();
    }

    // ============================================================= engines

    private static final BlockPos ENGINE = new BlockPos(2, 1, 1);

    /**
     * A regular engine's fuel tank takes only engine fuel: its own exhaust
     * and water are refused, an engine saved holding water clears it on
     * load, and it then runs on gasoline.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 400)
    public static void engineFuelTankTakesOnlyFuel(GameTestHelper helper) {
        Direction facing = Direction.NORTH;
        BlockPos out = ENGINE.relative(facing);
        helper.setBlock(ENGINE, TFMGBlocks.REGULAR_ENGINE.get().defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, facing));
        Fluid gasoline = TFMGOilEngineTests.sourceOf(TFMGTags.TFMGFluidTags.GASOLINE.tag);
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    Player player = TFMGOilEngineTestKit.player(helper);
                    RegularEngineBlockEntity be = helper.getBlockEntity(ENGINE);
                    for (Ingredient component : be.componentsInventory.components)
                        TFMGOilEngineTestKit.use(helper, player, ENGINE, component.getItems()[0].copy());
                    TFMGOilEngineTestKit.use(helper, player, ENGINE, AllBlocks.SHAFT.asStack());
                    ItemStack cylinder = TFMGOilEngineTests.cylinderFor(helper, TFMGTags.TFMGFluidTags.GASOLINE.tag, false);
                    for (int s = 0; s < be.pistonInventory.getSlots(); s++)
                        TFMGOilEngineTestKit.use(helper, player, ENGINE, cylinder.copy());
                    helper.assertTrue(be.nextComponent().isEmpty(), "the engine is not complete");
                    helper.assertTrue(push(face(helper, ENGINE, null), fluid("tfmg:carbon_dioxide"), 500) == 0, "the engine took its exhaust as fuel");
                    helper.assertTrue(push(face(helper, ENGINE, null), Fluids.WATER, 500) == 0, "the engine took water as fuel");
                    be.fuelTank.setFluid(new FluidStack(Fluids.WATER, 1000));
                    TFMGPowerKit.unloadAndReload(helper, List.of(ENGINE));
                })
                .thenExecuteAfter(3, () -> {
                    RegularEngineBlockEntity be = helper.getBlockEntity(ENGINE);
                    helper.assertTrue(be.fuelTank.isEmpty(), "the water was not cleared on load: " + held(be.fuelTank));
                    helper.assertTrue(push(face(helper, ENGINE, null), gasoline, 2000) == 2000, "the cleared engine refused gasoline");
                    TFMGOilEngineTestKit.shaft(helper, out, facing.getAxis());
                    helper.setBlock(ENGINE.above(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(TFMGOilEngineTestKit.speed(helper, out) != 0,
                        "the engine does not run on gasoline -> " + TFMGOilEngineTestKit.report(helper, ENGINE)))
                .thenSucceed();
    }

    /**
     * A large engine takes only the fuels it burns: gasoline and water are
     * refused, one saved holding gasoline and water clears them on load, and
     * it then drives its shaft on diesel.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 400)
    public static void largeEngineTakesOnlyItsFuels(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockPos shaftPos = pos.above(2);
        BlockPos east = shaftPos.east();
        TFMGOilEngineTestKit.shaft(helper, shaftPos, Direction.Axis.X);
        helper.setBlock(pos, TFMGBlocks.LARGE_ENGINE.get().defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        TFMGOilEngineTestKit.shaft(helper, east, Direction.Axis.X);
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(TFMGOilEngineTestKit.fill(helper, pos, fluid("tfmg:gasoline"), 1000) == 0,
                            "the large engine took gasoline, which it cannot burn");
                    helper.assertTrue(TFMGOilEngineTestKit.fill(helper, pos, Fluids.WATER, 1000) == 0, "the large engine took water");
                    LargeEngineBlockEntity be = helper.getBlockEntity(pos);
                    be.fuelTank.setFluid(new FluidStack(fluid("tfmg:gasoline"), 1000));
                    be.airTank.setFluid(new FluidStack(Fluids.WATER, 500));
                    TFMGPowerKit.unloadAndReload(helper, List.of(pos));
                })
                .thenExecuteAfter(3, () -> {
                    LargeEngineBlockEntity be = helper.getBlockEntity(pos);
                    helper.assertTrue(be.fuelTank.isEmpty(), "the gasoline was not cleared on load: " + held(be.fuelTank));
                    helper.assertTrue(be.airTank.isEmpty(), "the water was not cleared on load: " + held(be.airTank));
                    helper.assertTrue(TFMGOilEngineTestKit.fill(helper, pos, fluid("tfmg:air"), 1000) == 1000, "the cleared engine refused air");
                    helper.assertTrue(TFMGOilEngineTestKit.fill(helper, pos, fluid("tfmg:diesel"), 2000) == 2000, "the cleared engine refused diesel");
                })
                .thenWaitUntil(() -> helper.assertTrue(Math.abs(TFMGOilEngineTestKit.speed(helper, east)) > 0,
                        "the large engine does not run on diesel -> " + TFMGOilEngineTestKit.report(helper, pos)))
                .thenSucceed();
    }

    // ================================================================= vat

    /**
     * Vat inputs take only fluids some vat recipe uses. A vat saved with all
     * four input tanks full of hot air, which left a second ingredient nowhere
     * to go, clears them on load and then takes water and sulfuric acid.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 200)
    public static void vatInputsTakeOnlyRecipeFluids(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, TFMGBlocks.STEEL_CHEMICAL_VAT.get().defaultBlockState());
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(push(face(helper, pos, Direction.UP), fluid("tfmg:hot_air"), 1000) == 0, "the vat took hot air");
                    helper.assertTrue(push(face(helper, pos, Direction.UP), fluid("tfmg:creosote"), 1000) == 0, "the vat took creosote");
                    VatBlockEntity vat = helper.getBlockEntity(pos);
                    for (SmartFluidTankBehaviour.TankSegment segment : vat.inputTank.getTanks())
                        ((TankSegmentAccessor) segment).tfmg$tank().setFluid(new FluidStack(fluid("tfmg:hot_air"), 1000));
                    helper.assertTrue(push(face(helper, pos, Direction.UP), Fluids.WATER, 1000) == 0, "water found room in a vat full of hot air");
                    TFMGPowerKit.unloadAndReload(helper, List.of(pos));
                })
                .thenExecuteAfter(3, () -> {
                    VatBlockEntity vat = helper.getBlockEntity(pos);
                    for (SmartFluidTankBehaviour.TankSegment segment : vat.inputTank.getTanks())
                        helper.assertTrue(((TankSegmentAccessor) segment).tfmg$tank().isEmpty(), "an input tank kept its hot air on load");
                    helper.assertTrue(push(face(helper, pos, Direction.UP), Fluids.WATER, 1000) == 1000, "the cleared vat refused water");
                    helper.assertTrue(push(face(helper, pos, Direction.UP), fluid("tfmg:sulfuric_acid"), 1000) == 1000,
                            "the cleared vat refused sulfuric acid");
                })
                .thenSucceed();
    }

    // ======================================================== distillation

    /**
     * The distillation controller takes only what a tower distills and its
     * stages only give fractions out. Water in the steel tank is left there
     * instead of being pulled in (or voided), and crude oil is pulled once the
     * tank holds it.
     */
    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 400)
    public static void distillationTakesOnlyOil(GameTestHelper helper) {
        BlockPos controller = new BlockPos(4, 2, 4);
        BlockPos tank = controller.south();
        List<BlockPos> stages = new java.util.ArrayList<>();
        Fluid crude = fluid("tfmg:crude_oil");
        helper.startSequence()
                .thenExecuteAfter(1, () -> stages.addAll(TFMGOilEngineTests.buildTower(helper, controller, 3)))
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(TFMGOilEngineTestKit.fill(helper, controller, Fluids.WATER, 1000) == 0, "the controller took water");
                    for (BlockPos stage : stages)
                        helper.assertTrue(TFMGOilEngineTestKit.fill(helper, stage, crude, 1000) == 0, "a stage at " + stage + " took crude oil from a pipe");
                    helper.assertTrue(TFMGOilEngineTestKit.fill(helper, tank, Fluids.WATER, 4000) == 4000, "the steel tank refused water");
                })
                .thenExecuteAfter(20, () -> {
                    DistillationControllerBlockEntity be = helper.getBlockEntity(controller);
                    helper.assertTrue(be.tank.isEmpty(), "the controller pulled " + held(be.tank));
                    helper.assertTrue(TFMGOilEngineTestKit.amount(helper, tank, Fluids.WATER) == 4000, "water vanished from the steel tank");
                    IFluidHandler steel = TFMGOilEngineTestKit.fluids(helper, tank);
                    steel.drain(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
                    helper.assertTrue(TFMGOilEngineTestKit.fill(helper, tank, crude, 4000) == 4000, "the steel tank refused crude oil");
                })
                .thenWaitUntil(() -> {
                    DistillationControllerBlockEntity be = helper.getBlockEntity(controller);
                    helper.assertTrue(be.tank.getFluid().getFluid().isSame(crude), "the controller does not pull crude oil: " + held(be.tank));
                })
                .thenSucceed();
    }

    // ===================================================== casting, hatch

    /** The casting basin takes only fluids a casting recipe uses, and still casts steel. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 400)
    public static void castingBasinTakesOnlyCastableFluids(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, TFMGBlocks.CASTING_BASIN.get().defaultBlockState());
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(push(face(helper, pos, Direction.UP), Fluids.WATER, 1000) == 0, "the basin took water");
            helper.assertTrue(push(face(helper, pos, Direction.UP), fluid("tfmg:creosote"), 1000) == 0, "the basin took creosote");
            helper.assertTrue(push(face(helper, pos, Direction.UP), fluid("tfmg:molten_steel"), 144) == 144, "the basin refused molten steel");
        });
        helper.runAfterDelay(3, () -> helper.succeedWhen(() -> {
            CastingBasinBlockEntity basin = helper.getBlockEntity(pos);
            helper.assertTrue(basin.inventory.getStackInSlot(0).is(TFMGItems.STEEL_INGOT.get()), "no steel ingot yet");
        }));
    }

    /** A blast furnace hatch takes hot air and furnace gas, nothing that would keep the hot air out. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 100)
    public static void blastFurnaceHatchTakesOnlyItsGases(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, TFMGBlocks.BLAST_FURNACE_HATCH.get().defaultBlockState());
        helper.runAfterDelay(2, () -> {
            IFluidHandler hatch = face(helper, pos, Direction.UP);
            helper.assertTrue(push(hatch, fluid("tfmg:creosote"), 1000) == 0, "the hatch took creosote");
            helper.assertTrue(push(hatch, Fluids.WATER, 1000) == 0, "the hatch took water");
            helper.assertTrue(push(hatch, fluid("tfmg:hot_air"), 1000) == 1000, "the hatch refused hot air");
            hatch.drain(new FluidStack(fluid("tfmg:hot_air"), 1000), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(push(hatch, fluid("tfmg:furnace_gas"), 1000) == 1000, "the hatch refused furnace gas");
            helper.succeed();
        });
    }

    // ======================================================= concrete hose

    /** A concrete hose saved holding water clears it on load and takes liquid concrete again. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 100)
    public static void concreteHoseClearsWrongFluidOnLoad(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 4, 2);
        helper.setBlock(pos, TFMGBlocks.CONCRETE_HOSE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    ConcreteHoseBlockEntity be = helper.getBlockEntity(pos);
                    be.internalTank.setFluid(new FluidStack(Fluids.WATER, 500));
                    helper.assertTrue(TFMGOilEngineTestKit.fill(helper, pos, fluid("tfmg:liquid_concrete"), 500) == 0,
                            "concrete went into a hose holding water");
                    TFMGPowerKit.unloadAndReload(helper, List.of(pos));
                })
                .thenExecuteAfter(3, () -> {
                    ConcreteHoseBlockEntity be = helper.getBlockEntity(pos);
                    helper.assertTrue(be.internalTank.isEmpty(), "the water was not cleared on load: " + held(be.internalTank));
                    helper.assertTrue(TFMGOilEngineTestKit.fill(helper, pos, fluid("tfmg:liquid_concrete"), 500) == 500,
                            "the cleared hose refused liquid concrete");
                })
                .thenSucceed();
    }
}
