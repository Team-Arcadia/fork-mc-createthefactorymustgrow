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
}
