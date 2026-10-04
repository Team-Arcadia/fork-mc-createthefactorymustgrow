package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.engines.CylinderItem;
import com.drmangotea.tfmg.content.engines.engine_controller.EngineControllerBlockEntity;
import com.drmangotea.tfmg.content.engines.fuels.BaseFuelTypes;
import com.drmangotea.tfmg.content.engines.fuels.EngineFuelTypeManager;
import com.drmangotea.tfmg.content.engines.fuels.FuelType;
import com.drmangotea.tfmg.content.engines.types.AbstractSmallEngineBlockEntity;
import com.drmangotea.tfmg.content.engines.types.large_engine.LargeEngineBlockEntity;
import com.drmangotea.tfmg.content.engines.types.regular_engine.RegularEngineBlockEntity;
import com.drmangotea.tfmg.content.engines.types.regular_engine.RegularEngineBlockEntity.EngineType;
import com.drmangotea.tfmg.content.decoration.concrete.ConcreteloggedBlock;
import com.drmangotea.tfmg.content.decoration.pipes.TFMGPipes;
import com.drmangotea.tfmg.content.machinery.misc.firebox.FireboxBlock;
import com.drmangotea.tfmg.content.machinery.misc.flarestack.FlarestackBlock;
import com.drmangotea.tfmg.content.machinery.misc.smokestack.SmokestackBlock;
import com.drmangotea.tfmg.content.machinery.oil_processing.surface_scanner.SurfaceScannerBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.output.DistillationOutputBlockEntity;
import com.drmangotea.tfmg.recipes.DistillationRecipe;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.drmangotea.tfmg.registry.TFMGTags;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.drmangotea.tfmg.gametest.TFMGOilEngineTestKit.*;

/**
 * Oil, refining and engines, running for real: every machine is built the
 * way a player builds it, fed real fuels and recipes from the mod's data, and
 * checked on what it actually outputs (fluid in a tank, speed and stress on
 * a Create shaft). The Factory Inspector must read every running machine
 * cleanly and name the missing thing on every idle one.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGOilEngineTests {

    private static final String HUGE = TFMG.MOD_ID + ":gametest/platform_huge";
    private static final Direction FACING = Direction.NORTH;

    // ============================================================ engines

    /** Every built-in engine fuel drives a regular engine fitted with the cylinders crafted for it. */
    @GameTestGenerator
    public static List<TestFunction> engineFuels() {
        List<TestFunction> tests = new ArrayList<>();
        BaseFuelTypes.register();
        for (Map.Entry<ResourceLocation, FuelType> e : new TreeMap<>(EngineFuelTypeManager.BUILTIN_TYPE_MAP).entrySet()) {
            if (e.getValue() == BaseFuelTypes.FALLBACK)
                continue;
            FuelType fuel = e.getValue();
            tests.add(TFMGGameTestUtil.test("tfmg_engines", "engine.fuel." + e.getKey().getPath(), TFMGGameTestUtil.PLATFORM, 400,
                    helper -> runSmallEngine(helper, TFMGBlocks.REGULAR_ENGINE.get(), EngineType.I, 1, fuel, null)));
        }
        return tests;
    }

    /** Each regular engine layout (I, V, W, U, boxer), picked with an empty schematic, runs on gasoline. */
    @GameTestGenerator
    public static List<TestFunction> engineTypes() {
        List<TestFunction> tests = new ArrayList<>();
        for (EngineType type : new EngineType[]{EngineType.I, EngineType.V, EngineType.W, EngineType.U, EngineType.BOXER})
            tests.add(TFMGGameTestUtil.test("tfmg_engines", "engine.type." + type.name().toLowerCase(), TFMGGameTestUtil.PLATFORM, 400,
                    helper -> runSmallEngine(helper, TFMGBlocks.REGULAR_ENGINE.get(), type, 1, BaseFuelTypes.GASOLINE, null)));
        return tests;
    }

    /** The radial engine and its eight cylinders. */
    @GameTest(template = "gametest/platform", batch = "tfmg_engines", timeoutTicks = 400)
    public static void radialEngineRuns(GameTestHelper helper) {
        runSmallEngine(helper, TFMGBlocks.RADIAL_ENGINE.get(), EngineType.RADIAL, 1, BaseFuelTypes.GASOLINE, null);
    }

    /** The turbine engine on its turbine blades and kerosene. */
    @GameTest(template = "gametest/platform", batch = "tfmg_engines", timeoutTicks = 400)
    public static void turbineEngineRuns(GameTestHelper helper) {
        runSmallEngine(helper, TFMGBlocks.TURBINE_ENGINE.get(), EngineType.TURBINE, 1, BaseFuelTypes.KEROSENE, null);
    }

    /** Three regular engine blocks form one engine: each block pays its own build and adds its torque. */
    @GameTest(template = "gametest/platform", batch = "tfmg_engines", timeoutTicks = 400)
    public static void longEngineAddsTorque(GameTestHelper helper) {
        runSmallEngine(helper, TFMGBlocks.REGULAR_ENGINE.get(), EngineType.I, 3, BaseFuelTypes.GASOLINE, null);
    }

    /** A turbo on the engine makes it turn faster, as its upgrade figures say. */
    @GameTest(template = "gametest/platform", batch = "tfmg_engines", timeoutTicks = 400)
    public static void turboUpgradeSpeedsUp(GameTestHelper helper) {
        runSmallEngine(helper, TFMGBlocks.REGULAR_ENGINE.get(), EngineType.I, 1, BaseFuelTypes.GASOLINE, TFMGItems.TURBO.asStack());
    }

    @GameTest(template = "gametest/platform", batch = "tfmg_engines", timeoutTicks = 400)
    public static void goldenTurboUpgradeSpeedsUp(GameTestHelper helper) {
        runSmallEngine(helper, TFMGBlocks.REGULAR_ENGINE.get(), EngineType.I, 1, BaseFuelTypes.GASOLINE, TFMGItems.GOLDEN_TURBO.asStack());
    }

    /** A generator mounted on a running engine turns it into a voltage source. */
    @GameTest(template = "gametest/platform", batch = "tfmg_engines", timeoutTicks = 400)
    public static void generatorUpgradeMakesVoltage(GameTestHelper helper) {
        runSmallEngine(helper, TFMGBlocks.REGULAR_ENGINE.get(), EngineType.I, 1, BaseFuelTypes.GASOLINE, TFMGBlocks.GENERATOR.asStack(),
                engine -> helper.assertTrue(engine.voltageGeneration() > 0 && engine.powerGeneration() > 0,
                        "the generator upgrade produces " + engine.voltageGeneration() + " V / " + engine.powerGeneration() + " W"));
    }

    private static final BlockPos ENGINE_FRONT = new BlockPos(2, 1, 1);

    /**
     * Builds an engine with a mock player's clicks (type, components, output
     * shaft, cylinders, optional upgrade), fuels it, gives it a redstone
     * signal and checks the Create shaft in front turns at the speed the
     * engine type, fuel and upgrade say, with stress capacity, while fuel
     * burns and exhaust builds up. Before it is finished, the inspector
     * must name each missing part.
     */
    private static void runSmallEngine(GameTestHelper helper, Block block, EngineType type, int length, FuelType fuelType, ItemStack upgrade) {
        runSmallEngine(helper, block, type, length, fuelType, upgrade, engine -> {
        });
    }

    private static void runSmallEngine(GameTestHelper helper, Block block, EngineType type, int length, FuelType fuelType, ItemStack upgrade,
                                       java.util.function.Consumer<AbstractSmallEngineBlockEntity> extra) {
        BlockPos front = ENGINE_FRONT;
        BlockPos out = front.relative(FACING);
        for (int i = 0; i < length; i++)
            helper.setBlock(front.relative(FACING.getOpposite(), i), block.defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, FACING));
        Fluid fuel = sourceOf(fuelType.getFluid());
        int startFuel = 2000;
        float[] expected = new float[1];

        helper.runAfterDelay(5, () -> {
            Player player = player(helper);
            AbstractSmallEngineBlockEntity master = helper.getBlockEntity(front);
            // engineLength() counts the blocks behind the master; getAllEngines() is the whole chain.
            TFMGGameTestUtil.check(helper, master.isController() && master.getAllEngines().size() == length,
                    "engine did not form: " + master.getAllEngines().size() + " blocks instead of " + length);
            assertProblem(helper, front, "engine.missing_component", "bare engine");

            // Pick the layout first: switching it hands the cylinders back.
            if (master instanceof RegularEngineBlockEntity regular && regular.type != type) {
                for (int i = 0; i < EngineType.values().length && regular.type != type; i++)
                    use(helper, player, front, AllItems.EMPTY_SCHEMATIC.asStack());
                TFMGGameTestUtil.check(helper, regular.type == type, "the schematic did not switch the engine to " + type + " (is " + regular.type + ")");
            }

            for (int b = 0; b < length; b++)
                for (Ingredient component : master.componentsInventory.components) {
                    ItemStack left = use(helper, player, front, component.getItems()[0].copy());
                    TFMGGameTestUtil.check(helper, left.isEmpty(), "the engine refused component " + component.getItems()[0]);
                }
            if (!master.nextComponent().isEmpty())
                helper.fail("components still missing: " + master.nextComponent().getItems()[0]);
            assertProblem(helper, front, "engine.missing_cylinders", "engine without cylinders");

            if (block == TFMGBlocks.REGULAR_ENGINE.get()) {
                assertProblem(helper, front, "engine.no_shaft", "engine without output shaft");
                ItemStack left = use(helper, player, front, AllBlocks.SHAFT.asStack());
                TFMGGameTestUtil.check(helper, left.isEmpty(), "the engine refused its output shaft");
            }

            ItemStack cylinder = cylinderFor(helper, fuelType.getFluid(), block == TFMGBlocks.TURBINE_ENGINE.get());
            for (int b = 0; b < length; b++) {
                BlockPos pos = front.relative(FACING.getOpposite(), b);
                RegularEngineBlockEntity be = helper.getBlockEntity(pos);
                for (int s = 0; s < be.pistonInventory.getSlots(); s++) {
                    ItemStack left = use(helper, player, pos, cylinder.copyWithCount(1));
                    TFMGGameTestUtil.check(helper, left.isEmpty(), "block " + b + " refused cylinder " + (s + 1) + " " + cylinder);
                }
            }
            if (upgrade != null) {
                ItemStack left = use(helper, player, front, upgrade.copy());
                TFMGGameTestUtil.check(helper, left.isEmpty() && master.hasUpgrade(), "the engine refused the upgrade " + upgrade);
            }

            assertProblem(helper, front, "engine.no_fuel", "engine without fuel");
            int filled = fill(helper, front, fuel, startFuel);
            TFMGGameTestUtil.check(helper, filled == startFuel, "the engine took " + filled + " mB of " + BuiltInRegistries.FLUID.getKey(fuel));
            assertProblem(helper, front, "engine.no_signal", "engine without redstone");

            shaft(helper, out, FACING.getAxis());
            helper.setBlock(front.above(), Blocks.REDSTONE_BLOCK);

            float upgradeSpeed = upgrade == null ? 1 : master.getUpgradeSpeedModifier();
            expected[0] = Math.min((int) (4000 * type.speedModifier * fuelType.getSpeed() * upgradeSpeed / 40), 256);
        });

        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            AbstractSmallEngineBlockEntity master = helper.getBlockEntity(front);
            float speed = Math.abs(speed(helper, out));
            helper.assertTrue(Math.abs(speed - expected[0]) <= 1, "output shaft turns at " + speed + " RPM, expected " + expected[0]
                    + " -> " + report(helper, front));
            helper.assertTrue(networkCapacity(helper, out) > 0, "the engine adds no stress capacity");
            if (length > 1) {
                // Each block behind the master adds its share of torque.
                float perBlock = 15 * type.torqueModifier * fuelType.getStress();
                helper.assertTrue(Math.abs(master.torque - perBlock * (length - 1)) < 0.01f,
                        "torque " + master.torque + " for " + length + " blocks, expected " + perBlock * (length - 1));
            }
            int left = amount(helper, front, fuel);
            helper.assertTrue(left < startFuel, "no fuel burnt yet (" + left + " mB left)");
            helper.assertTrue(master.exhaustTank.getFluidAmount() > 0, "no exhaust produced");
            assertClean(helper, front, "running engine");
            extra.accept(master);
        }));
    }

    /** A source fluid of a tag, e.g. tfmg:diesel for c:diesel. */
    static Fluid sourceOf(TagKey<Fluid> tag) {
        for (Holder<Fluid> holder : BuiltInRegistries.FLUID.getTagOrEmpty(tag)) {
            Fluid fluid = holder.value();
            if (fluid.isSource(fluid.defaultFluidState()))
                return fluid;
        }
        throw new IllegalStateException("no source fluid in " + tag.location());
    }

    /** The cylinder (or turbine blade) a real recipe crafts for this fuel. */
    static ItemStack cylinderFor(GameTestHelper helper, TagKey<Fluid> fuel, boolean turbine) {
        String wanted = fuel.location().toString();
        for (RecipeHolder<?> holder : helper.getLevel().getRecipeManager().getRecipes()) {
            ItemStack result;
            try {
                result = holder.value().getResultItem(helper.getLevel().registryAccess());
            } catch (RuntimeException e) {
                continue;
            }
            if (!(result.getItem() instanceof CylinderItem) || result.is(TFMGItems.TURBINE_BLADE.get()) != turbine)
                continue;
            CompoundTag tags = result.get(TFMGDataComponents.FUEL_TAGS);
            if (tags == null)
                continue;
            for (String key : tags.getAllKeys())
                if (tags.getString(key).equals(wanted))
                    return result.copyWithCount(1);
        }
        throw new IllegalStateException("no recipe crafts a " + (turbine ? "turbine blade" : "cylinder") + " for " + wanted);
    }

    /** With too little fuel the engine runs, burns it, stops, and the inspector says why. */
    @GameTest(template = "gametest/platform", batch = "tfmg_engines", timeoutTicks = 600)
    public static void engineStopsWhenFuelRunsOut(GameTestHelper helper) {
        BlockPos front = ENGINE_FRONT;
        BlockPos out = front.relative(FACING);
        helper.setBlock(front, TFMGBlocks.REGULAR_ENGINE.get().defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, FACING));
        Fluid gasoline = sourceOf(TFMGTags.TFMGFluidTags.GASOLINE.tag);
        boolean[] ran = new boolean[1];
        helper.runAfterDelay(5, () -> {
            Player player = player(helper);
            AbstractSmallEngineBlockEntity master = helper.getBlockEntity(front);
            for (Ingredient component : master.componentsInventory.components)
                use(helper, player, front, component.getItems()[0].copy());
            use(helper, player, front, AllBlocks.SHAFT.asStack());
            ItemStack cylinder = cylinderFor(helper, TFMGTags.TFMGFluidTags.GASOLINE.tag, false);
            RegularEngineBlockEntity be = helper.getBlockEntity(front);
            for (int s = 0; s < be.pistonInventory.getSlots(); s++)
                use(helper, player, front, cylinder.copy());
            fill(helper, front, gasoline, 3);
            shaft(helper, out, FACING.getAxis());
            helper.setBlock(front.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            if (speed(helper, out) != 0)
                ran[0] = true;
            helper.assertTrue(ran[0], "the engine never turned on 3 mB of gasoline");
            helper.assertTrue(amount(helper, front, gasoline) == 0, "fuel left: " + amount(helper, front, gasoline));
            helper.assertTrue(speed(helper, out) == 0, "the engine still turns with an empty tank");
            assertProblem(helper, front, "engine.no_fuel", "engine out of fuel");
        }));
    }

    // ------------------------------------------------------ large engine

    /** The large engine burns each of the fuels it lists and drives the powered shaft above it. */
    @GameTestGenerator
    public static List<TestFunction> largeEngineFuels() {
        List<TestFunction> tests = new ArrayList<>();
        for (String fuel : new String[]{"diesel", "kerosene", "naphtha", "furnace_gas"}) {
            tests.add(TFMGGameTestUtil.test("tfmg_engines", "large_engine.fuel." + fuel, TFMGGameTestUtil.PLATFORM, 400,
                    helper -> runLargeEngine(helper, TFMGBlocks.LARGE_ENGINE.get(), fuel)));
            tests.add(TFMGGameTestUtil.test("tfmg_engines", "simple_large_engine.fuel." + fuel, TFMGGameTestUtil.PLATFORM, 400,
                    helper -> runLargeEngine(helper, TFMGBlocks.SIMPLE_LARGE_ENGINE.get(), fuel)));
        }
        return tests;
    }

    private static final BlockPos LARGE_ENGINE = new BlockPos(2, 1, 2);

    private static void runLargeEngine(GameTestHelper helper, Block block, String fuelName) {
        // The handbook layout: the shaft two blocks above, placed first so the
        // engine turns it into a powered shaft.
        BlockPos shaftPos = LARGE_ENGINE.above(2);
        shaft(helper, shaftPos, Direction.Axis.X);
        helper.setBlock(LARGE_ENGINE, block.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos east = shaftPos.east();
        shaft(helper, east, Direction.Axis.X);
        Fluid fuel = fluid("tfmg:" + fuelName);
        Fluid air = fluid("tfmg:air");
        helper.runAfterDelay(5, () -> {
            TFMGGameTestUtil.check(helper, helper.getLevel().getBlockState(helper.absolutePos(shaftPos)).is(AllBlocks.POWERED_SHAFT.get()),
                    "the shaft above the engine did not become a powered shaft");
            assertProblem(helper, LARGE_ENGINE, "large_engine.no_air", "large engine without air");
            assertProblem(helper, LARGE_ENGINE, "engine.no_fuel", "large engine without fuel");
            TFMGGameTestUtil.check(helper, fill(helper, LARGE_ENGINE, air, 1000) == 1000, "the large engine refused air");
            TFMGGameTestUtil.check(helper, fill(helper, LARGE_ENGINE, fuel, 2000) == 2000, "the large engine refused " + fuelName);
        });
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            LargeEngineBlockEntity engine = helper.getBlockEntity(LARGE_ENGINE);
            helper.assertTrue(Math.abs(speed(helper, east)) > 0, "the shaft beside the powered shaft does not turn (powered shaft "
                    + speed(helper, shaftPos) + " RPM, generating "
                    + ((com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity) be(helper, shaftPos)).getGeneratedSpeed()
                    + ") -> " + report(helper, LARGE_ENGINE));
            helper.assertTrue(networkCapacity(helper, east) > 0, "the large engine adds no stress capacity");
            helper.assertTrue(engine.fuelTank.getFluidAmount() < 2000, "no " + fuelName + " burnt yet");
            helper.assertTrue(engine.airTank.getFluidAmount() < 1000, "no air used yet");
            helper.assertTrue(engine.exhaustTank.getFluidAmount() > 0, "no exhaust produced");
            assertClean(helper, LARGE_ENGINE, "running large engine");
        }));
    }

    /** Out of air, the large engine lets go of its shaft and the inspector names the air. */
    @GameTest(template = "gametest/platform", batch = "tfmg_engines", timeoutTicks = 1200)
    public static void largeEngineStopsWithoutAir(GameTestHelper helper) {
        BlockPos shaftPos = LARGE_ENGINE.above(2);
        shaft(helper, shaftPos, Direction.Axis.X);
        helper.setBlock(LARGE_ENGINE, TFMGBlocks.LARGE_ENGINE.get().defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        boolean[] ran = new boolean[1];
        helper.runAfterDelay(5, () -> {
            fill(helper, LARGE_ENGINE, fluid("tfmg:air"), 150);
            fill(helper, LARGE_ENGINE, fluid("tfmg:diesel"), 2000);
        });
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            if (speed(helper, shaftPos) != 0)
                ran[0] = true;
            helper.assertTrue(ran[0], "the large engine never ran");
            helper.assertTrue(speed(helper, shaftPos) == 0, "the large engine still turns without air");
            assertProblem(helper, LARGE_ENGINE, "large_engine.no_air", "large engine out of air");
        }));
    }

    // -------------------------------------------- controller and gearbox

    /**
     * A transmission bound to an engine controller links the engine to it:
     * stopped, the inspector says so and nothing turns; started, the engine
     * idles; on the gas, it speeds up.
     */
    @GameTest(template = "gametest/platform", batch = "tfmg_engines", timeoutTicks = 600)
    public static void engineControllerDrivesEngine(GameTestHelper helper) {
        BlockPos front = ENGINE_FRONT;
        BlockPos out = front.relative(FACING);
        BlockPos controllerPos = new BlockPos(0, 1, 3);
        helper.setBlock(front, TFMGBlocks.REGULAR_ENGINE.get().defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, FACING));
        helper.setBlock(controllerPos, TFMGBlocks.ENGINE_CONTROLLER.get().defaultBlockState());
        float[] idle = new float[1];
        helper.runAfterDelay(5, () -> {
            Player player = player(helper);
            AbstractSmallEngineBlockEntity master = helper.getBlockEntity(front);
            for (Ingredient component : master.componentsInventory.components)
                use(helper, player, front, component.getItems()[0].copy());
            use(helper, player, front, AllBlocks.SHAFT.asStack());
            ItemStack cylinder = cylinderFor(helper, TFMGTags.TFMGFluidTags.GASOLINE.tag, false);
            RegularEngineBlockEntity be = helper.getBlockEntity(front);
            for (int s = 0; s < be.pistonInventory.getSlots(); s++)
                use(helper, player, front, cylinder.copy());
            fill(helper, front, sourceOf(TFMGTags.TFMGFluidTags.GASOLINE.tag), 2000);
            shaft(helper, out, FACING.getAxis());

            // Bind a transmission to the controller, then mount it on the engine.
            ItemStack transmission = use(helper, player, controllerPos, TFMGItems.TRANSMISSION.asStack());
            TFMGGameTestUtil.check(helper, transmission.get(TFMGDataComponents.POSITION) != null,
                    "clicking the controller did not bind the transmission");
            use(helper, player, front, transmission);
            EngineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
            TFMGGameTestUtil.check(helper, helper.absolutePos(front).equals(controller.enginePos), "the controller is not linked to the engine");
            TFMGGameTestUtil.check(helper, helper.absolutePos(controllerPos).equals(master.engineController), "the engine does not know its controller");
        });
        helper.runAfterDelay(30, () -> {
            assertProblem(helper, controllerPos, "engine.controller_stopped", "controller before start");
            TFMGGameTestUtil.check(helper, speed(helper, out) == 0, "the engine turns before the controller started it");
            EngineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
            controller.toggleEngine();
        });
        helper.runAfterDelay(36, () -> {
            // A one-block engine counts as two-shafted, so its gearbox sits in neutral until shifted.
            assertProblem(helper, controllerPos, "engine.neutral", "started engine in neutral");
            TFMGGameTestUtil.check(helper, speed(helper, out) == 0, "the engine drives its shaft in neutral");
            EngineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
            controller.shiftForward();
        });
        helper.runAfterDelay(40, () -> {
            idle[0] = Math.abs(speed(helper, out));
            TFMGGameTestUtil.check(helper, idle[0] > 0, "the started engine does not idle -> " + report(helper, controllerPos));
            assertClean(helper, controllerPos, "controller with a started engine");
            EngineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
            controller.gas = true;
        });
        helper.runAfterDelay(41, () -> helper.succeedWhen(() -> {
            float speed = Math.abs(speed(helper, out));
            helper.assertTrue(speed > idle[0], "the gas pedal does not speed the engine up (" + speed + " RPM, idle " + idle[0] + ")");
        }));
    }

    /** The engine gearbox takes the engine's shaft and turns it through a right angle. */
    @GameTest(template = "gametest/platform", batch = "tfmg_engines", timeoutTicks = 400)
    public static void engineGearboxTurnsTheCorner(GameTestHelper helper) {
        BlockPos front = new BlockPos(2, 1, 2);
        BlockPos gearbox = front.relative(FACING);
        BlockPos side = gearbox.east();
        helper.setBlock(front, TFMGBlocks.REGULAR_ENGINE.get().defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, FACING));
        // Its input face (FACING) looks back at the engine; the output axis runs across.
        helper.setBlock(gearbox, TFMGBlocks.ENGINE_GEARBOX.get().defaultBlockState()
                .setValue(HorizontalKineticBlock.HORIZONTAL_FACING, FACING.getOpposite()));
        shaft(helper, side, Direction.Axis.X);
        helper.runAfterDelay(5, () -> {
            Player player = player(helper);
            AbstractSmallEngineBlockEntity master = helper.getBlockEntity(front);
            for (Ingredient component : master.componentsInventory.components)
                use(helper, player, front, component.getItems()[0].copy());
            use(helper, player, front, AllBlocks.SHAFT.asStack());
            ItemStack cylinder = cylinderFor(helper, TFMGTags.TFMGFluidTags.DIESEL.tag, false);
            RegularEngineBlockEntity be = helper.getBlockEntity(front);
            for (int s = 0; s < be.pistonInventory.getSlots(); s++)
                use(helper, player, front, cylinder.copy());
            fill(helper, front, sourceOf(TFMGTags.TFMGFluidTags.DIESEL.tag), 2000);
            helper.setBlock(front.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            helper.assertTrue(speed(helper, gearbox) != 0, "the engine gearbox does not turn -> " + report(helper, front));
            helper.assertTrue(speed(helper, side) != 0, "the shaft on the gearbox side does not turn");
            helper.assertTrue(networkCapacity(helper, side) > 0, "no stress capacity behind the gearbox");
            assertClean(helper, gearbox, "engine gearbox");
        }));
    }

    /** The piping upgrade feeds the engine from the fuel tank next to it. */
    @GameTest(template = "gametest/platform", batch = "tfmg_engines", timeoutTicks = 400)
    public static void pipingUpgradeFeedsFromTank(GameTestHelper helper) {
        BlockPos front = ENGINE_FRONT;
        BlockPos out = front.relative(FACING);
        BlockPos tank = front.east();
        Fluid gasoline = sourceOf(TFMGTags.TFMGFluidTags.GASOLINE.tag);
        helper.setBlock(front, TFMGBlocks.REGULAR_ENGINE.get().defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, FACING));
        helper.setBlock(tank, TFMGBlocks.STEEL_FLUID_TANK.get().defaultBlockState());
        helper.runAfterDelay(5, () -> {
            Player player = player(helper);
            AbstractSmallEngineBlockEntity master = helper.getBlockEntity(front);
            for (Ingredient component : master.componentsInventory.components)
                use(helper, player, front, component.getItems()[0].copy());
            use(helper, player, front, AllBlocks.SHAFT.asStack());
            ItemStack cylinder = cylinderFor(helper, TFMGTags.TFMGFluidTags.GASOLINE.tag, false);
            RegularEngineBlockEntity be = helper.getBlockEntity(front);
            for (int s = 0; s < be.pistonInventory.getSlots(); s++)
                use(helper, player, front, cylinder.copy());
            TFMGGameTestUtil.check(helper, fill(helper, tank, gasoline, 4000) == 4000, "the steel tank refused gasoline");
            ItemStack left = use(helper, player, front, TFMGBlocks.INDUSTRIAL_PIPE.asStack());
            TFMGGameTestUtil.check(helper, left.isEmpty() && master.hasUpgrade(), "the engine refused the piping upgrade");
            shaft(helper, out, FACING.getAxis());
            helper.setBlock(front.above(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            int inEngine = amount(helper, front, gasoline);
            int inTank = amount(helper, tank, gasoline);
            helper.assertTrue(inEngine > 0, "the piping upgrade moved no fuel from the tank (tank " + inTank + " mB)");
            helper.assertTrue(inEngine + inTank <= 4000, "fuel was duplicated: " + inEngine + " + " + inTank + " mB from 4000");
            helper.assertTrue(speed(helper, out) != 0, "the engine fed through its piping upgrade does not run");
        }));
    }

    // ======================================================= finding oil

    /** A surface scanner turned at 64 RPM finds the oil deposit under its chunk; standing still it says it needs rotation. */
    @GameTest(template = "gametest/platform", batch = "tfmg_oil", timeoutTicks = 400)
    public static void surfaceScannerFindsDeposit(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = blueprint(helper, "finding_oil_2", new BlockPos(2, 1, 1));
        BlockPos scanner = find(blocks, TFMGBlocks.SURFACE_SCANNER.get());
        BlockPos shaftPos = find(blocks, AllBlocks.SHAFT.get());
        BlockPos deposit = new BlockPos(0, 0, 4);
        helper.setBlock(deposit, TFMGBlocks.OIL_DEPOSIT.get());
        helper.runAfterDelay(30, () -> {
            assertProblem(helper, scanner, "surface_scanner.slow", "scanner without rotation");
            // The handbook's input shaft, driven from a motor in its place.
            helper.setBlock(shaftPos, AllBlocks.CREATIVE_MOTOR.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        });
        helper.runAfterDelay(32, () -> setMotor(helper, shaftPos, 64));
        helper.runAfterDelay(33, () -> helper.succeedWhen(() -> {
            SurfaceScannerBlockEntity be = (SurfaceScannerBlockEntity) be(helper, scanner);
            BlockPos s = helper.absolutePos(scanner);
            BlockPos d = helper.absolutePos(deposit);
            int x = (d.getX() >> 4) - (s.getX() >> 4) + 2;
            int z = (d.getZ() >> 4) - (s.getZ() >> 4) + 2;
            Boolean cell = be.grid[x][z];
            helper.assertTrue(Boolean.TRUE.equals(cell), "the scanner does not show the deposit's chunk (cell " + x + "," + z + " = " + cell + ")");
            assertClean(helper, scanner, "turning surface scanner");
        }));
    }

    // ========================================================== pumpjack

    /** The handbook pumpjack, turned by a motor, pumps crude oil from the deposit into its base. */
    @GameTest(template = "gametest/platform_huge", batch = "tfmg_oil", timeoutTicks = 1200)
    public static void pumpjackPumpsCrudeOil(GameTestHelper helper) {
        runPumpjack(helper, false);
    }

    /** The same pumpjack with the large hammer head, beam and connector. */
    @GameTest(template = "gametest/platform_huge", batch = "tfmg_oil", timeoutTicks = 1200)
    public static void largePumpjackPumpsCrudeOil(GameTestHelper helper) {
        runPumpjack(helper, true);
    }

    private static void runPumpjack(GameTestHelper helper, boolean large) {
        Map<BlockPos, BlockState> blocks = blueprint(helper, "pumpjack_0", new BlockPos(2, 1, 2));
        if (large) {
            for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
                BlockState state = e.getValue();
                Block swap = state.is(TFMGBlocks.PUMPJACK_HAMMER_HEAD.get()) ? TFMGBlocks.LARGE_PUMPJACK_HAMMER_HEAD.get()
                        : state.is(TFMGBlocks.PUMPJACK_HAMMER_PART.get()) ? TFMGBlocks.LARGE_PUMPJACK_HAMMER_PART.get()
                        : state.is(TFMGBlocks.PUMPJACK_HAMMER_CONNECTOR.get()) ? TFMGBlocks.LARGE_PUMPJACK_HAMMER_CONNECTOR.get() : null;
                if (swap != null)
                    helper.setBlock(e.getKey(), swap.withPropertiesOf(state));
            }
        }
        BlockPos base = find(blocks, TFMGBlocks.PUMPJACK_BASE.get());
        BlockPos hammer = find(blocks, TFMGBlocks.PUMPJACK_HAMMER.get());
        BlockPos input = find(blocks, AllBlocks.SHAFT.get());
        Fluid crude = fluid("tfmg:crude_oil");
        helper.runAfterDelay(30, () -> {
            assertProblem(helper, hammer, "pumpjack.crank_input_still", "pumpjack without rotation");
            assertProblem(helper, hammer, "pumpjack.not_glued", "pumpjack with a loose beam");
            TFMGGameTestUtil.check(helper, amount(helper, base, crude) == 0, "oil before the pumpjack ever turned");
            glueBeam(helper, blocks);
            helper.setBlock(input, AllBlocks.CREATIVE_MOTOR.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        });
        helper.runAfterDelay(32, () -> setMotor(helper, input, 64));
        helper.runAfterDelay(33, () -> helper.succeedWhen(() -> {
            int oil = amount(helper, base, crude);
            helper.assertTrue(oil >= 1000, "the base holds " + oil + " mB of crude oil -> " + report(helper, hammer));
            assertClean(helper, hammer, "running pumpjack");
            assertClean(helper, base, "running pumpjack base");
        }));
    }

    /** Super Glue over the whole beam, head to connector, as the ponder scene tells players to. */
    static void glueBeam(GameTestHelper helper, Map<BlockPos, BlockState> blocks) {
        BlockPos head = find(blocks, TFMGBlocks.PUMPJACK_HAMMER_HEAD.get());
        BlockPos connector = find(blocks, TFMGBlocks.PUMPJACK_HAMMER_CONNECTOR.get());
        helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(),
                SuperGlueEntity.span(helper.absolutePos(head), helper.absolutePos(connector))));
    }

    // ======================================================= distillation

    /**
     * One test per distillation recipe in the mod's data: a 2x2 steel tower
     * with as many output stages as the recipe has fractions, heated by four
     * fireboxes burning LPG and filled with the recipe's input. Every stage
     * must fill with its own fraction, heaviest at the bottom.
     */
    @GameTestGenerator
    public static List<TestFunction> distillationRecipes() {
        List<TestFunction> tests = new ArrayList<>();
        for (String id : new String[]{"crude_oil", "crude_oil_no_naphtha", "crude_oil_light_distillation",
                "heavy_oil", "heavy_oil_no_naphtha", "heavy_oil_light_distillation"})
            tests.add(TFMGGameTestUtil.test("tfmg_oil", "distillation." + id, HUGE, 1200,
                    helper -> runDistillation(helper, "tfmg:distillation/" + id)));
        return tests;
    }

    private static final BlockPos TOWER_CONTROLLER = new BlockPos(4, 2, 4);

    /** Builds a 2x2 tower over four fireboxes with {@code stages} outputs; returns the outputs, bottom first. */
    static List<BlockPos> buildTower(GameTestHelper helper, BlockPos controller, int stages) {
        BlockPos tank = controller.south();
        int height = Math.max(2, stages * 2);
        for (int x = 0; x < 2; x++)
            for (int z = 0; z < 2; z++) {
                helper.setBlock(tank.offset(x, -1, z), TFMGBlocks.FIREBOX.get());
                for (int y = 0; y < height; y++)
                    helper.setBlock(tank.offset(x, y, z), TFMGBlocks.STEEL_FLUID_TANK.get());
            }
        helper.setBlock(controller, TFMGBlocks.STEEL_DISTILLATION_CONTROLLER.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        List<BlockPos> outputs = new ArrayList<>();
        for (int i = 0; i < stages; i++) {
            BlockPos out = controller.above(1 + 2 * i);
            helper.setBlock(out, TFMGBlocks.STEEL_DISTILLATION_OUTPUT.get());
            outputs.add(out);
            if (i < stages - 1)
                helper.setBlock(out.above(), TFMGBlocks.INDUSTRIAL_PIPE.get());
        }
        return outputs;
    }

    private static void runDistillation(GameTestHelper helper, String recipeId) {
        BlockPos controller = TOWER_CONTROLLER;
        BlockPos tank = controller.south();
        BlockPos firebox = tank.below();
        DistillationRecipe[] recipe = new DistillationRecipe[1];
        List<BlockPos> outputs = new ArrayList<>();
        helper.runAfterDelay(1, () -> {
            recipe[0] = recipe(helper, recipeId);
            outputs.addAll(buildTower(helper, controller, recipe[0].getFluidResults().size()));
        });
        helper.runAfterDelay(20, () -> {
            assertProblem(helper, controller, "distillation.no_input", "empty tower");
            Fluid in = recipe[0].getFluidIngredients().getFirst().getFluids()[0].getFluid();
            TFMGGameTestUtil.check(helper, fill(helper, tank, in, 20000) == 20000, "the tower tank refused its input");
        });
        helper.runAfterDelay(40, () -> {
            assertProblem(helper, controller, "distillation.no_heat", "cold tower");
            for (BlockPos out : outputs)
                TFMGGameTestUtil.check(helper, fluids(helper, out).getFluidInTank(0).isEmpty(), "a cold tower distilled something");
            TFMGGameTestUtil.check(helper, fill(helper, firebox, fluid("tfmg:lpg"), 8000) == 8000, "the firebox refused LPG");
        });
        helper.runAfterDelay(41, () -> helper.succeedWhen(() -> {
            List<FluidStack> results = recipe[0].getFluidResults();
            for (int i = 0; i < outputs.size(); i++) {
                FluidStack held = fluids(helper, outputs.get(i)).getFluidInTank(0);
                helper.assertTrue(!held.isEmpty(), "stage " + (i + 1) + " is still empty -> " + report(helper, controller));
                helper.assertTrue(held.getFluid().isSame(results.get(i).getFluid()), "stage " + (i + 1) + " holds "
                        + BuiltInRegistries.FLUID.getKey(held.getFluid()) + " instead of " + BuiltInRegistries.FLUID.getKey(results.get(i).getFluid()));
            }
            assertClean(helper, controller, "running tower");
        }));
    }

    /**
     * A tower whose fireboxes were placed lit (the handbook blueprint does
     * so) but hold no fuel must not distil: the fireboxes go out and the
     * inspector says the tower is cold.
     */
    @GameTest(template = "gametest/platform_huge", batch = "tfmg_oil", timeoutTicks = 600)
    public static void unfuelledFireboxesGoOut(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = blueprint(helper, "distillation_0", new BlockPos(4, 1, 4));
        BlockPos controller = find(blocks, TFMGBlocks.STEEL_DISTILLATION_CONTROLLER.get());
        BlockPos tank = controller.south();
        helper.runAfterDelay(10, () -> fill(helper, tank, fluid("tfmg:crude_oil"), 20000));
        helper.runAfterDelay(200, () -> {
            for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet())
                if (e.getValue().is(TFMGBlocks.FIREBOX.get()))
                    helper.assertBlockState(e.getKey(), s -> s.getValue(FireboxBlock.HEAT_LEVEL) == HeatLevel.NONE, () -> "an empty firebox still burns");
            for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet())
                if (e.getValue().is(TFMGBlocks.STEEL_DISTILLATION_OUTPUT.get()))
                    TFMGGameTestUtil.check(helper, fluids(helper, e.getKey()).getFluidInTank(0).isEmpty(), "the tower distilled without fuel");
            assertProblem(helper, controller, "distillation.no_heat", "tower over empty fireboxes");
            helper.succeed();
        });
    }

    // ==================================================== burning fluids

    /** Every firebox fuel lights a firebox, burns down and makes exhaust; gasoline is not a firebox fuel. */
    @GameTest(template = "gametest/platform_huge", batch = "tfmg_burners", timeoutTicks = 600)
    public static void fireboxBurnsEveryFuel(GameTestHelper helper) {
        List<Fluid> fuels = sources(TFMGTags.TFMGFluidTags.FIREBOX_FUEL.tag);
        List<BlockPos> boxes = new ArrayList<>();
        for (int i = 0; i < fuels.size(); i++) {
            BlockPos pos = new BlockPos(1 + (i % 8) * 2, 1, 1 + (i / 8) * 2);
            helper.setBlock(pos, TFMGBlocks.FIREBOX.get());
            boxes.add(pos);
        }
        BlockPos empty = new BlockPos(1, 1, 15);
        BlockPos wrong = new BlockPos(5, 1, 15);
        helper.setBlock(empty, TFMGBlocks.FIREBOX.get());
        helper.setBlock(wrong, TFMGBlocks.FIREBOX.get());
        int[] wrongFilled = new int[1];
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < fuels.size(); i++)
                TFMGGameTestUtil.check(helper, fill(helper, boxes.get(i), fuels.get(i), 1000) == 1000,
                        "the firebox refused " + BuiltInRegistries.FLUID.getKey(fuels.get(i)));
            wrongFilled[0] = fill(helper, wrong, fluid("tfmg:gasoline"), 1000);
        });
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            for (int i = 0; i < fuels.size(); i++) {
                BlockPos pos = boxes.get(i);
                String name = BuiltInRegistries.FLUID.getKey(fuels.get(i)).toString();
                helper.assertBlockState(pos, s -> s.getValue(FireboxBlock.HEAT_LEVEL) != HeatLevel.NONE, () -> "the firebox on " + name + " is not lit");
                helper.assertTrue(amount(helper, pos, fuels.get(i)) < 1000, "no " + name + " burnt");
                helper.assertTrue(amount(helper, pos, fluid("tfmg:carbon_dioxide")) > 0, "no exhaust from " + name);
                assertClean(helper, pos, "firebox on " + name);
            }
            assertProblem(helper, empty, "firebox.no_fuel", "firebox without fuel");
            if (wrongFilled[0] > 0)
                assertProblem(helper, wrong, "firebox.wrong_fuel", "firebox on gasoline");
            helper.assertBlockState(wrong, s -> s.getValue(FireboxBlock.HEAT_LEVEL) == HeatLevel.NONE, () -> "a firebox burns gasoline");
        }));
    }

    /** A flarestack lights on every flammable fluid and burns its tank empty; it refuses water. */
    @GameTest(template = "gametest/platform_huge", batch = "tfmg_burners", timeoutTicks = 600)
    public static void flarestackBurnsEveryFlammable(GameTestHelper helper) {
        List<Fluid> burnable = sources(TFMGTags.TFMGFluidTags.FLAMMABLE.tag);
        for (Fluid f : sources(TFMGTags.TFMGFluidTags.FUEL.tag))
            if (!burnable.contains(f))
                burnable.add(f);
        List<BlockPos> stacks = new ArrayList<>();
        for (int i = 0; i < burnable.size(); i++) {
            BlockPos pos = new BlockPos(1 + (i % 8) * 2, 1, 1 + (i / 8) * 2);
            helper.setBlock(pos, TFMGBlocks.FLARESTACK.get());
            stacks.add(pos);
        }
        BlockPos water = new BlockPos(1, 1, 15);
        helper.setBlock(water, TFMGBlocks.FLARESTACK.get());
        boolean[] seen = new boolean[burnable.size()];
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < burnable.size(); i++)
                TFMGGameTestUtil.check(helper, fill(helper, stacks.get(i), burnable.get(i), 2000) == 2000,
                        "the flarestack refused " + BuiltInRegistries.FLUID.getKey(burnable.get(i)));
            TFMGGameTestUtil.check(helper, fill(helper, water, Fluids.WATER, 1000) == 0, "the flarestack took water");
        });
        helper.runAfterDelay(7, () -> helper.succeedWhen(() -> {
            // Watch every stack first: they all burn at once, and a failed
            // check below would otherwise skip the ones after it this tick.
            for (int i = 0; i < burnable.size(); i++) {
                BlockPos pos = stacks.get(i);
                boolean lit = helper.getLevel().getBlockState(helper.absolutePos(pos)).getValue(FlarestackBlock.LIT);
                if (lit && amount(helper, pos, burnable.get(i)) > 0 && problems(helper, pos).isEmpty())
                    seen[i] = true;
            }
            for (int i = 0; i < burnable.size(); i++) {
                BlockPos pos = stacks.get(i);
                String name = BuiltInRegistries.FLUID.getKey(burnable.get(i)).toString();
                helper.assertTrue(seen[i], "the flarestack never burned " + name + " cleanly -> " + report(helper, pos));
                helper.assertTrue(amount(helper, pos, burnable.get(i)) == 0, "the flarestack still holds " + name);
            }
        }));
    }

    /** A gas lamp lights while it has gas; an empty one stays dark. */
    @GameTest(template = "gametest/platform", batch = "tfmg_burners", timeoutTicks = 200)
    public static void gasLampLightsOnGas(GameTestHelper helper) {
        BlockPos lamp = new BlockPos(1, 1, 2);
        BlockPos dark = new BlockPos(3, 1, 2);
        helper.setBlock(lamp, TFMGBlocks.GAS_LAMP.get());
        helper.setBlock(dark, TFMGBlocks.GAS_LAMP.get());
        helper.runAfterDelay(5, () -> TFMGGameTestUtil.check(helper, fill(helper, lamp, fluid("tfmg:lpg"), 1000) == 1000, "the gas lamp refused LPG"));
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            helper.assertBlockState(lamp, s -> s.getValue(BlockStateProperties.LIT), () -> "the fuelled gas lamp is dark");
            helper.assertBlockState(dark, s -> !s.getValue(BlockStateProperties.LIT), () -> "the empty gas lamp is lit");
            assertClean(helper, lamp, "gas lamp");
        }));
    }

    /** Exhaust and a three block smokestack take carbon dioxide and vent it; the smokestack passes it up to its top. */
    @GameTest(template = "gametest/platform", batch = "tfmg_burners", timeoutTicks = 1200)
    public static void exhaustAndSmokestackVent(GameTestHelper helper) {
        BlockPos exhaust = new BlockPos(1, 1, 2);
        BlockPos stack = new BlockPos(3, 1, 2);
        helper.setBlock(exhaust, TFMGBlocks.EXHAUST.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        for (int y = 0; y < 3; y++)
            helper.setBlock(stack.above(y), TFMGBlocks.METAL_SMOKESTACK.get());
        Fluid co2 = fluid("tfmg:carbon_dioxide");
        boolean[] rose = new boolean[1];
        helper.runAfterDelay(5, () -> {
            helper.assertBlockState(stack, s -> !s.getValue(SmokestackBlock.TOP), () -> "the bottom smokestack thinks it is the top");
            helper.assertBlockState(stack.above(2), s -> s.getValue(SmokestackBlock.TOP), () -> "the top smokestack is not marked as top");
            TFMGGameTestUtil.check(helper, fill(helper, exhaust, co2, 500) == 500, "the exhaust refused carbon dioxide");
            TFMGGameTestUtil.check(helper, fill(helper, stack, co2, 4000) == 4000, "the smokestack refused carbon dioxide");
            TFMGGameTestUtil.check(helper, fill(helper, exhaust, fluid("tfmg:lpg"), 100) == 0, "the exhaust took LPG");
            assertClean(helper, exhaust, "venting exhaust");
            assertClean(helper, stack, "venting smokestack");
        });
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            if (amount(helper, stack.above(2), co2) > 0)
                rose[0] = true;
            helper.assertTrue(rose[0], "no carbon dioxide reached the top of the smokestack");
            helper.assertTrue(amount(helper, exhaust, co2) == 0, "the exhaust still holds " + amount(helper, exhaust, co2) + " mB");
            int left = amount(helper, stack, co2) + amount(helper, stack.above(), co2) + amount(helper, stack.above(2), co2);
            helper.assertTrue(left == 0, "the smokestack still holds " + left + " mB");
        }));
    }

    /** A Create pump draws a burning firebox's exhaust into a smokestack, so the firebox never chokes. */
    @GameTest(template = "gametest/platform", batch = "tfmg_burners", timeoutTicks = 1200)
    public static void fireboxExhaustPumpedToSmokestack(GameTestHelper helper) {
        BlockPos firebox = new BlockPos(0, 1, 1);
        BlockPos pipe = new BlockPos(1, 1, 1);
        BlockPos pump = new BlockPos(2, 1, 1);
        BlockPos stack = new BlockPos(3, 1, 1);
        helper.setBlock(firebox, TFMGBlocks.FIREBOX.get());
        helper.setBlock(pipe, AllBlocks.FLUID_PIPE.get());
        helper.setBlock(pump, AllBlocks.MECHANICAL_PUMP.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(stack, TFMGBlocks.METAL_SMOKESTACK.get());
        helper.setBlock(stack.above(), TFMGBlocks.METAL_SMOKESTACK.get());
        cogDrive(helper, pump, Direction.Axis.X, 64);
        Fluid co2 = fluid("tfmg:carbon_dioxide");
        boolean[] vented = new boolean[1];
        helper.runAfterDelay(3, () -> connect(helper, List.of(pipe)));
        helper.runAfterDelay(5, () -> fill(helper, firebox, fluid("tfmg:lpg"), 8000));
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            if (amount(helper, stack, co2) + amount(helper, stack.above(), co2) > 0)
                vented[0] = true;
            helper.assertTrue(vented[0], "no exhaust reached the smokestack (firebox holds " + amount(helper, firebox, co2) + " mB)");
            helper.assertTrue(amount(helper, firebox, co2) == 0, "exhaust is left in the firebox");
            assertClean(helper, firebox, "firebox with exhaust piped away");
        }));
    }

    /** Drives a pump (a small cog) through a cogwheel beside it, as players do. */
    static void cogDrive(GameTestHelper helper, BlockPos pump, Direction.Axis axis, int rpm) {
        Direction side = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        BlockPos cog = pump.relative(side);
        helper.setBlock(cog, AllBlocks.COGWHEEL.get().defaultBlockState().setValue(BlockStateProperties.AXIS, axis));
        Direction along = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        motor(helper, cog.relative(along), along.getOpposite(), rpm);
    }

    static List<Fluid> sources(TagKey<Fluid> tag) {
        List<Fluid> out = new ArrayList<>();
        for (Holder<Fluid> holder : BuiltInRegistries.FLUID.getTagOrEmpty(tag))
            if (holder.value().isSource(holder.value().defaultFluidState()) && !out.contains(holder.value()))
                out.add(holder.value());
        return out;
    }

    // ============================================================ fluids

    /** Each TFMG pipe material carries crude oil from tank to tank through its own pump and glass pipe. */
    @GameTestGenerator
    public static List<TestFunction> pipeMaterials() {
        List<TestFunction> tests = new ArrayList<>();
        for (TFMGPipes.PipeMaterial material : TFMGPipes.PipeMaterial.values())
            tests.add(TFMGGameTestUtil.test("tfmg_fluids", "pipes." + material.name, TFMGGameTestUtil.PLATFORM_LARGE, 600,
                    helper -> runPipeLine(helper, material)));
        return tests;
    }

    private static void runPipeLine(GameTestHelper helper, TFMGPipes.PipeMaterial material) {
        var entry = TFMGPipes.PIPES.get(material);
        BlockPos source = new BlockPos(1, 1, 4);
        BlockPos target = new BlockPos(7, 1, 4);
        List<BlockPos> pipes = List.of(new BlockPos(2, 1, 4), new BlockPos(4, 1, 4), new BlockPos(6, 1, 4));
        BlockPos pump = new BlockPos(3, 1, 4);
        BlockPos glass = new BlockPos(5, 1, 4);
        helper.setBlock(source, TFMGBlocks.ALUMINUM_FLUID_TANK.get());
        helper.setBlock(target, TFMGBlocks.CAST_IRON_FLUID_TANK.get());
        for (BlockPos p : pipes)
            helper.setBlock(p, entry.getPipe().get());
        helper.setBlock(pump, entry.getPump().get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(glass, entry.getGlass().get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        cogDrive(helper, pump, Direction.Axis.X, 128);
        Fluid crude = fluid("tfmg:crude_oil");
        helper.runAfterDelay(3, () -> connect(helper, pipes));
        helper.runAfterDelay(5, () -> TFMGGameTestUtil.check(helper, fill(helper, source, crude, 4000) == 4000, "the aluminum tank refused crude oil"));
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            int moved = amount(helper, target, crude);
            int left = amount(helper, source, crude);
            helper.assertTrue(moved >= 1000, material.name + " line moved " + moved + " mB (source " + left + " mB)");
            helper.assertTrue(moved + left <= 4000, "crude oil was duplicated: " + moved + " + " + left);
        }));
    }

    /** Two stacked TFMG tanks of each metal merge into one tank holding both blocks' worth. */
    @GameTest(template = "gametest/platform", batch = "tfmg_fluids", timeoutTicks = 200)
    public static void tanksMergeAndHold(GameTestHelper helper) {
        Block[] tanks = {TFMGBlocks.STEEL_FLUID_TANK.get(), TFMGBlocks.ALUMINUM_FLUID_TANK.get(), TFMGBlocks.CAST_IRON_FLUID_TANK.get()};
        for (int i = 0; i < tanks.length; i++) {
            helper.setBlock(new BlockPos(i + 1, 1, 2), tanks[i]);
            helper.setBlock(new BlockPos(i + 1, 2, 2), tanks[i]);
        }
        helper.runAfterDelay(10, () -> {
            Fluid diesel = fluid("tfmg:diesel");
            for (int i = 0; i < tanks.length; i++) {
                BlockPos bottom = new BlockPos(i + 1, 1, 2);
                String name = BuiltInRegistries.BLOCK.getKey(tanks[i]).getPath();
                FluidTankBlockEntity tank = (FluidTankBlockEntity) be(helper, bottom);
                TFMGGameTestUtil.check(helper, tank.getControllerBE() != null && tank.getControllerBE().getHeight() == 2, name + " did not merge two blocks");
                int capacity = fluids(helper, bottom).getTankCapacity(0);
                int filled = fill(helper, bottom, diesel, 1_000_000);
                TFMGGameTestUtil.check(helper, filled == capacity && filled > 0, name + " took " + filled + " of " + capacity);
                TFMGGameTestUtil.check(helper, amount(helper, new BlockPos(i + 1, 2, 2), diesel) == filled,
                        name + ": the top block does not share the bottom block's fluid");
            }
            helper.succeed();
        });
    }

    /** The electric pump moves fluid only while a generator powers it. */
    @GameTest(template = "gametest/platform_large", batch = "tfmg_fluids", timeoutTicks = 600)
    public static void electricPumpMovesFluidWhenPowered(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 4);
        BlockPos target = new BlockPos(5, 1, 4);
        BlockPos pump = new BlockPos(3, 1, 4);
        List<BlockPos> pipes = List.of(new BlockPos(2, 1, 4), new BlockPos(4, 1, 4));
        helper.setBlock(source, TFMGBlocks.STEEL_FLUID_TANK.get());
        helper.setBlock(target, TFMGBlocks.STEEL_FLUID_TANK.get());
        for (BlockPos p : pipes)
            helper.setBlock(p, AllBlocks.FLUID_PIPE.get());
        helper.setBlock(pump, TFMGBlocks.ELECTRIC_PUMP.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.runAfterDelay(3, () -> connect(helper, pipes));
        helper.runAfterDelay(5, () -> fill(helper, source, Fluids.WATER, 8000));
        helper.runAfterDelay(60, () -> {
            assertProblem(helper, pump, "electric.no_voltage", "unpowered electric pump");
            TFMGGameTestUtil.check(helper, amount(helper, target, Fluids.WATER) == 0, "an unpowered electric pump moved water");
            helper.setBlock(pump.above(), TFMGBlocks.CREATIVE_GENERATOR.get());
        });
        helper.runAfterDelay(61, () -> helper.succeedWhen(() -> {
            int moved = amount(helper, target, Fluids.WATER);
            helper.assertTrue(moved >= 1000, "the powered electric pump moved " + moved + " mB -> " + report(helper, pump));
            assertClean(helper, pump, "powered electric pump");
        }));
    }

    /** The concrete hose lowers onto rebar and pours liquid concrete into it. */
    @GameTest(template = "gametest/platform", batch = "tfmg_fluids", timeoutTicks = 600)
    public static void concreteHoseFillsRebar(GameTestHelper helper) {
        BlockPos hose = new BlockPos(2, 4, 2);
        BlockPos rebar = new BlockPos(2, 1, 2);
        helper.setBlock(rebar, TFMGBlocks.REBAR_BLOCK.get());
        helper.setBlock(hose, TFMGBlocks.CONCRETE_HOSE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        // Its shaft is on the clockwise side of its facing: east.
        motor(helper, hose.east(), Direction.WEST, -32);
        helper.runAfterDelay(5, () -> {
            assertProblem(helper, hose, "concrete_hose.empty", "empty concrete hose");
            TFMGGameTestUtil.check(helper, fill(helper, hose, fluid("tfmg:liquid_concrete"), 1000) == 1000, "the hose refused liquid concrete");
        });
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> helper.assertBlockState(rebar,
                s -> s.getValue(ConcreteloggedBlock.CONCRETELOGGED), () -> "the rebar was not filled -> " + report(helper, hose))));
    }

    // ============================================================= chain

    /**
     * The whole oil industry, automated end to end: a pumpjack pumps crude
     * oil, a pump sends it into a firebox-heated distillation tower, a second
     * pump carries the tower's diesel to an engine, and the engine turns a
     * Create press that flattens an iron ingot into a sheet.
     */
    @GameTest(template = "gametest/platform_huge", batch = "tfmg_chain", timeoutTicks = 4000)
    public static void oilFieldToPressChain(GameTestHelper helper) {
        // Pumpjack, turned by a motor on its machine input.
        Map<BlockPos, BlockState> jack = blueprint(helper, "pumpjack_0", new BlockPos(0, 1, 0));
        BlockPos base = find(jack, TFMGBlocks.PUMPJACK_BASE.get());
        BlockPos hammer = find(jack, TFMGBlocks.PUMPJACK_HAMMER.get());
        BlockPos jackInput = find(jack, AllBlocks.SHAFT.get());
        motor(helper, jackInput, Direction.SOUTH, 64);
        helper.runAfterDelay(5, () -> glueBeam(helper, jack));

        // Crude oil line: base -> pipe -> pump -> pipes -> tower tank.
        List<BlockPos> crudeLine = new ArrayList<>();
        crudeLine.add(base.south());
        BlockPos crudePump = base.south(2);
        for (int z = crudePump.getZ() + 1; z <= 9; z++)
            crudeLine.add(new BlockPos(base.getX(), base.getY(), z));
        crudeLine.add(new BlockPos(base.getX() + 1, base.getY(), 9));
        for (BlockPos p : crudeLine)
            helper.setBlock(p, AllBlocks.FLUID_PIPE.get());
        helper.setBlock(crudePump, AllBlocks.MECHANICAL_PUMP.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        cogDrive(helper, crudePump, Direction.Axis.Z, 64);

        // Distillation tower: tank at x 2..3, z 9..10; controller in front.
        BlockPos controller = new BlockPos(2, 2, 8);
        List<BlockPos> stages = buildTower(helper, controller, 6);
        BlockPos dieselStage = stages.get(1);

        // The fireboxes' exhaust is pumped into a smokestack, or they choke.
        BlockPos exhaustPipe = controller.south().below().east(2);
        BlockPos exhaustPump = exhaustPipe.east();
        BlockPos chimney = exhaustPump.east();
        helper.setBlock(exhaustPipe, AllBlocks.FLUID_PIPE.get());
        helper.setBlock(exhaustPump, AllBlocks.MECHANICAL_PUMP.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(chimney, TFMGBlocks.METAL_SMOKESTACK.get());
        helper.setBlock(chimney.above(), TFMGBlocks.METAL_SMOKESTACK.get());
        cogDrive(helper, exhaustPump, Direction.Axis.X, 64);

        // Diesel line: stage -> pipe -> pump -> pipe -> engine.
        BlockPos dieselPipe = dieselStage.north();
        BlockPos dieselPump = dieselPipe.north();
        BlockPos enginePipe = dieselPump.north();
        BlockPos engine = enginePipe.north();
        helper.setBlock(dieselPipe, AllBlocks.FLUID_PIPE.get());
        helper.setBlock(enginePipe, AllBlocks.FLUID_PIPE.get());
        helper.setBlock(dieselPump, AllBlocks.MECHANICAL_PUMP.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        cogDrive(helper, dieselPump, Direction.Axis.Z, 64);

        // Engine facing east, its shaft into a press over a depot.
        helper.setBlock(engine, TFMGBlocks.REGULAR_ENGINE.get().defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
        BlockPos out = engine.east();
        BlockPos press = out.east();
        // The press head reaches one block down: the depot sits two below.
        BlockPos depot = press.below(2);
        helper.setBlock(press, AllBlocks.MECHANICAL_PRESS.get().defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
        helper.setBlock(depot, AllBlocks.DEPOT.get());

        helper.runAfterDelay(10, () -> {
            List<BlockPos> all = new ArrayList<>(crudeLine);
            all.add(dieselPipe);
            all.add(enginePipe);
            all.add(exhaustPipe);
            connect(helper, all);
            Player player = player(helper);
            AbstractSmallEngineBlockEntity master = helper.getBlockEntity(engine);
            for (Ingredient component : master.componentsInventory.components)
                use(helper, player, engine, component.getItems()[0].copy());
            use(helper, player, engine, AllBlocks.SHAFT.asStack());
            ItemStack cylinder = cylinderFor(helper, TFMGTags.TFMGFluidTags.DIESEL.tag, false);
            RegularEngineBlockEntity be = helper.getBlockEntity(engine);
            for (int s = 0; s < be.pistonInventory.getSlots(); s++)
                use(helper, player, engine, cylinder.copy());
            shaft(helper, out, Direction.Axis.X);
            helper.setBlock(engine.above(), Blocks.REDSTONE_BLOCK);
            // The tower's fireboxes get their first fuel by hand.
            fill(helper, controller.south().below(), fluid("tfmg:lpg"), 16000);
            DepotBlockEntity depotBE = (DepotBlockEntity) be(helper, depot);
            depotBE.setHeldItem(new ItemStack(Items.IRON_INGOT));
            // Only the diesel is piped away; the other fractions are voided
            // once their stage is full, as a player scrolls them to do.
            for (BlockPos stage : stages)
                ((DistillationOutputBlockEntity) be(helper, stage)).mode.setValue(DistillationOutputBlockEntity.DistillationOutputMode.VOID_WHEN_FULL.ordinal());
        });
        helper.runAfterDelay(11, () -> helper.succeedWhen(() -> {
            DepotBlockEntity depotBE = (DepotBlockEntity) be(helper, depot);
            helper.assertTrue(depotBE.getHeldItem().is(AllItems.IRON_SHEET.get()), "no iron sheet yet: base "
                    + amount(helper, base, fluid("tfmg:crude_oil")) + " mB crude, diesel stage " + contents(fluids(helper, dieselStage))
                    + ", engine " + contents(fluids(helper, engine)) + ", shaft " + speed(helper, out) + " RPM, press "
                    + speed(helper, press) + " RPM -> " + report(helper, controller) + " || " + report(helper, engine));
            // The tower runs in bursts as the pumpjack refills it, so it is
            // checked by the distillation tests rather than here.
            assertClean(helper, hammer, "chain pumpjack");
            assertClean(helper, engine, "chain engine");
        }));
    }
}
