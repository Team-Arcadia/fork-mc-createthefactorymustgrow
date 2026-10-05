package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.TFMGRegistries;
import com.drmangotea.tfmg.content.decoration.concrete.ConcreteloggedBlock;
import com.drmangotea.tfmg.content.decoration.pipes.TFMGPipeBlockEntity;
import com.drmangotea.tfmg.content.decoration.pipes.TFMGPipeEntry;
import com.drmangotea.tfmg.content.decoration.pipes.TFMGPipes;
import com.drmangotea.tfmg.content.electricity.lights.LightBulbBlockEntity;
import com.drmangotea.tfmg.content.electricity.measurement.VoltMeterBlockEntity;
import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeCoilBlockEntity;
import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeTransformerBlock;
import com.drmangotea.tfmg.content.engines.fuels.BaseFuelTypes;
import com.drmangotea.tfmg.content.engines.fuels.EngineFuelTypeManager;
import com.drmangotea.tfmg.content.engines.fuels.FuelType;
import com.drmangotea.tfmg.content.items.inspector.FactoryInspectorItem;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.content.items.weapons.flamethrover.FlamethrowerFuelType;
import com.drmangotea.tfmg.content.machinery.misc.winding_machine.WindingMachineBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.output.DistillationOutputBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.industrial_mixer.IndustrialMixerBlockEntity;
import com.drmangotea.tfmg.recipes.DistillationRecipe;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGEncasedBlocks;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.drmangotea.tfmg.registry.TFMGTags;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.VirtualFluid;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.drmangotea.tfmg.gametest.TFMGFactoryKit.*;

/**
 * Variants and settings, running for real: every pipe material's valve,
 * smart pipe, encased and glass pipe moving or holding back fluid, the large
 * transformer's cooling upgrades, blaze burners heating a chemical vat, every
 * winding recipe and spool, every TFMG fluid in the world and in its bucket
 * or gas tank, every value a player can dial on a block, the decoration
 * blocks that do something, every fluid tank size, and the gears, gearboxes,
 * flywheels and casings no other test turns.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGVariantTests {

    static final String BATCH = "tfmg_variants";
    static final String SMALL = "gametest/platform";
    static final String LARGE = "gametest/platform_large";
    static final String HUGE = "gametest/platform_huge";

    // ========================================================== the inspector

    /** Every line one Factory Inspector click wrote, by key, with its arguments. */
    static final class Report extends InspectionReport {
        final Map<String, Object[]> args = new LinkedHashMap<>();
        final List<String> problems = new ArrayList<>();
        String text = "";

        @Override
        public InspectionReport add(Kind kind, String key, Object... a) {
            args.put(key, a);
            if (kind == Kind.PROBLEM)
                problems.add(key);
            return super.add(kind, key, a);
        }

        boolean has(String key) {
            return args.containsKey(key);
        }

        /** The argument at {@code index} of the line {@code key}, as a string, or null. */
        String arg(String key, int index) {
            Object[] a = args.get(key);
            return a == null || a.length <= index ? null : String.valueOf(a[index]);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    static Report report(GameTestHelper helper, BlockPos pos) {
        Report report = new Report();
        StringBuilder text = new StringBuilder();
        for (Component line : FactoryInspectorItem.inspect(helper.getLevel(), helper.absolutePos(pos), report))
            text.append(line.getString()).append(" | ");
        report.text = text.toString();
        return report;
    }

    // ================================================================ pipes

    /**
     * Every pipe material, three lines from a water tank: through its valve,
     * which holds the water back until its shaft turns forwards and closes
     * again when the shaft turns back; through its smart pipe filtering
     * water, which lets it through; and through its smart pipe filtering
     * lava, which does not. A copper casing encases the pipe behind the
     * valve without stopping the flow, the wrench takes the casing off again
     * and turns a straight pipe into a glass pipe, the screwdriver locks a
     * pipe against a new neighbour, and every variant drops its own item.
     */
    @GameTestGenerator
    public static List<TestFunction> pipeVariants() {
        List<TestFunction> tests = new ArrayList<>();
        for (TFMGPipes.PipeMaterial material : TFMGPipes.PipeMaterial.values())
            tests.add(TFMGGameTestUtil.test(BATCH, "pipe_variants." + material.name, TFMGGameTestUtil.PLATFORM_LARGE, 1400,
                    helper -> runPipeVariants(helper, material)));
        return tests;
    }

    /** Water tank, pipe, the material's own pump, pipe, {@code middle}, pipe, empty tank, along X at row {@code z}. */
    private static List<BlockPos> pipeLine(GameTestHelper helper, TFMGPipeEntry entry, int z, BlockState middle) {
        creativeTank(helper, new BlockPos(1, 1, z), Fluids.WATER);
        tank(helper, new BlockPos(7, 1, z));
        List<BlockPos> pipes = List.of(new BlockPos(2, 1, z), new BlockPos(4, 1, z), new BlockPos(6, 1, z));
        for (BlockPos p : pipes)
            helper.setBlock(p, entry.getPipe().get());
        BlockPos pump = new BlockPos(3, 1, z);
        helper.setBlock(pump, entry.getPump().get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        TFMGOilEngineTests.cogDrive(helper, pump, Direction.Axis.X, 128);
        helper.setBlock(new BlockPos(5, 1, z), middle);
        return pipes;
    }

    private static void runPipeVariants(GameTestHelper helper, TFMGPipes.PipeMaterial material) {
        TFMGPipeEntry entry = TFMGPipes.PIPES.get(material);
        String name = material.name;
        Fluid water = Fluids.WATER;
        BlockPos valve = new BlockPos(5, 1, 2);
        BlockPos valveTarget = new BlockPos(7, 1, 2);
        BlockPos valveMotor = valve.north();
        BlockPos cased = new BlockPos(6, 1, 2);
        BlockPos straight = new BlockPos(4, 1, 2);
        BlockPos smartPass = new BlockPos(5, 1, 6);
        BlockPos passTarget = new BlockPos(7, 1, 6);
        BlockPos smartBlock = new BlockPos(5, 1, 9);
        BlockPos blockTarget = new BlockPos(7, 1, 9);
        BlockPos locked = new BlockPos(6, 1, 9);
        BlockPos newcomer = new BlockPos(6, 1, 8);

        List<BlockPos> pipes = new ArrayList<>(pipeLine(helper, entry, 2, entry.getValve().get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false)));
        BlockState smart = entry.getSmart().get().defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        pipes.addAll(pipeLine(helper, entry, 6, smart));
        pipes.addAll(pipeLine(helper, entry, 9, smart));
        Player player = TFMGOilEngineTestKit.player(helper);
        int[] openRpm = {32};
        int[] heldBack = new int[1];

        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    TFMGOilEngineTestKit.connect(helper, pipes);
                    smartFilter(helper, smartPass, new ItemStack(Items.WATER_BUCKET));
                    smartFilter(helper, smartBlock, new ItemStack(Items.LAVA_BUCKET));
                    // The player encases the pipe behind the valve.
                    TFMGOilEngineTestKit.use(helper, player, cased, AllBlocks.COPPER_CASING.asStack());
                })
                .thenExecuteAfter(1, () -> helper.assertBlockPresent(entry.getEncased().get(), cased))
                .thenWaitUntil(() -> helper.assertTrue(amount(helper, passTarget, water) >= 1000,
                        "the " + name + " smart pipe filtering water passed " + amount(helper, passTarget, water) + " mB"))
                .thenExecute(() -> {
                    helper.assertTrue(amount(helper, valveTarget, water) == 0, "a still " + name + " valve let "
                            + amount(helper, valveTarget, water) + " mB through");
                    helper.assertBlockState(valve, s -> !s.getValue(FluidValveBlock.ENABLED), () -> "the " + name + " valve is open without a shaft");
                    helper.assertTrue(amount(helper, blockTarget, water) == 0, "the " + name + " smart pipe filtering lava passed "
                            + amount(helper, blockTarget, water) + " mB of water");
                    motor(helper, valveMotor, Direction.SOUTH, openRpm[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(TFMGOilEngineTestKit.speed(helper, valve) != 0, "the " + name + " valve does not turn"))
                .thenExecute(() -> {
                    // The valve opens when its shaft turns forwards.
                    if (TFMGOilEngineTestKit.speed(helper, valve) < 0) {
                        openRpm[0] = -openRpm[0];
                        setMotor(helper, valveMotor, openRpm[0]);
                    }
                })
                .thenWaitUntil(() -> {
                    helper.assertBlockState(valve, s -> s.getValue(FluidValveBlock.ENABLED), () -> "the " + name + " valve did not open");
                    helper.assertTrue(amount(helper, valveTarget, water) >= 1000, "the open " + name + " valve passed "
                            + amount(helper, valveTarget, water) + " mB");
                })
                .thenExecute(() -> setMotor(helper, valveMotor, -openRpm[0]))
                .thenWaitUntil(() -> helper.assertBlockState(valve, s -> !s.getValue(FluidValveBlock.ENABLED),
                        () -> "the " + name + " valve did not close when its shaft turned back"))
                .thenIdle(20)
                .thenExecute(() -> heldBack[0] = amount(helper, valveTarget, water))
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(amount(helper, valveTarget, water) == heldBack[0], "the closed " + name + " valve still passed "
                            + (amount(helper, valveTarget, water) - heldBack[0]) + " mB");
                    // The wrench takes the casing back off, and turns a straight pipe into a glass one.
                    player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
                    TFMGPowerKit.click(helper, player, cased);
                    TFMGPowerKit.click(helper, player, straight);
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertBlockPresent(entry.getPipe().get(), cased);
                    helper.assertBlockPresent(entry.getGlass().get(), straight);
                    // The screwdriver locks a pipe: a pipe laid next to it is not joined.
                    player.setItemInHand(InteractionHand.MAIN_HAND, TFMGItems.SCREWDRIVER.asStack());
                    TFMGPowerKit.click(helper, player, locked);
                    helper.assertTrue(TFMGOilEngineTestKit.be(helper, locked) instanceof TFMGPipeBlockEntity pipe && pipe.locked,
                            "the screwdriver did not lock the " + name + " pipe");
                    helper.setBlock(newcomer, entry.getPipe().get());
                    TFMGOilEngineTestKit.connect(helper, List.of(newcomer, locked));
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertBlockState(locked, s -> !s.getValue(BlockStateProperties.NORTH), () -> "the locked " + name + " pipe joined its new neighbour");
                    TFMGPowerKit.unloadAndReload(helper, List.of(locked));
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertTrue(TFMGOilEngineTestKit.be(helper, locked) instanceof TFMGPipeBlockEntity pipe && pipe.locked,
                            "the " + name + " pipe lost its lock on reload");
                    TFMGPowerKit.click(helper, player, locked);
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertBlockState(locked, s -> s.getValue(BlockStateProperties.NORTH), () -> "the unlocked " + name + " pipe did not join its neighbour");
                    // Each variant drops the item it is built from.
                    breakWithDrops(helper, straight);
                    breakWithDrops(helper, valve);
                    breakWithDrops(helper, smartPass);
                    TFMGOilEngineTestKit.use(helper, player, new BlockPos(6, 1, 6), AllBlocks.COPPER_CASING.asStack());
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertBlockPresent(entry.getEncased().get(), new BlockPos(6, 1, 6));
                    breakWithDrops(helper, new BlockPos(6, 1, 6));
                    breakWithDrops(helper, new BlockPos(3, 1, 9));
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertItemEntityCountIs(entry.getPipe().get().asItem(), straight, 1.0, 1);
                    helper.assertItemEntityCountIs(entry.getValve().get().asItem(), valve, 1.0, 1);
                    helper.assertItemEntityCountIs(entry.getSmart().get().asItem(), smartPass, 1.0, 1);
                    helper.assertItemEntityCountIs(entry.getPipe().get().asItem(), new BlockPos(6, 1, 6), 1.0, 1);
                    helper.assertItemEntityCountIs(entry.getPump().get().asItem(), new BlockPos(3, 1, 9), 1.0, 1);
                })
                .thenSucceed();
    }

    /** Breaks a block the way mining it would, dropping its loot (GameTestHelper.destroyBlock drops nothing). */
    static void breakWithDrops(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
    }

    static void smartFilter(GameTestHelper helper, BlockPos pos, ItemStack filter) {
        FilteringBehaviour behaviour = BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(pos), FilteringBehaviour.TYPE);
        helper.assertTrue(behaviour != null && behaviour.setFilter(filter), "the smart pipe at " + pos + " took no filter");
    }

    // ==================================================== large transformer

    static ItemStack largeCoil(int turns) {
        ItemStack stack = new ItemStack(TFMGBlocks.LARGE_COIL.get());
        stack.set(TFMGDataComponents.COIL_TURNS, turns);
        return stack;
    }

    /**
     * An air cooled large transformer passes 30 kW. A block of steel clicked
     * on it by a player makes it metal cooled (50 kW), a bucket of
     * lubrication oil clicked on its output part makes it oil cooled
     * (100 kW) and hands back the bucket. The inspector names the cooling,
     * its cap and the next upgrade on both parts, and the upgrade survives a
     * chunk reload.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void largeTransformerCoolingRaisesItsCap(GameTestHelper helper) {
        BlockPos main = new BlockPos(4, 1, 5);
        BlockPos output = new BlockPos(5, 1, 5);
        helper.setBlock(main, TFMGBlocks.LARGE_COIL.getDefaultState());
        helper.setBlock(output, TFMGBlocks.LARGE_COIL.getDefaultState());
        ((LargeCoilBlockEntity) helper.getBlockEntity(main)).setCapacity(largeCoil(100));
        ((LargeCoilBlockEntity) helper.getBlockEntity(output)).setCapacity(largeCoil(300));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ((LargeCoilBlockEntity) helper.getBlockEntity(main)).createTransformer(player, Direction.EAST);
        TFMGPowerKit.creativeGenerator(helper, new BlockPos(3, 2, 5), 100);
        TFMGPowerKit.hub(helper, main.above());
        BlockPos load = output.above();
        TFMGPowerKit.place(helper, load, TFMGBlocks.RESISTOR.get(), Direction.UP);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(TFMGPowerKit.volts(helper, load) == 300,
                        "the 1:3 large transformer gives " + TFMGPowerKit.volts(helper, load) + " V"))
                .thenExecute(() -> expectCooling(helper, main, output, 30000, "large_transformer.cooling", "large_transformer.needs_steel.fix"))
                .thenExecute(() -> {
                    ItemStack left = TFMGOilEngineTestKit.use(helper, player, main, new ItemStack(TFMGBlocks.STEEL_BLOCK.get(), 2));
                    helper.assertTrue(left.getCount() == 1, "the steel upgrade took " + (2 - left.getCount()) + " blocks of steel");
                    for (BlockPos part : new BlockPos[]{main, output})
                        helper.assertBlockState(part, s -> !s.getValue(LargeTransformerBlock.UNFINISHED_MODEL),
                                () -> "the steel cased transformer still shows its unfinished model");
                })
                .thenExecute(() -> expectCooling(helper, main, output, 50000, "large_transformer.cooling", "large_transformer.needs_oil.fix"))
                .thenExecute(() -> {
                    ItemStack left = TFMGOilEngineTestKit.use(helper, player, output, new ItemStack(TFMGFluids.LUBRICATION_OIL.getBucket().get()));
                    helper.assertTrue(left.is(Items.BUCKET), "pouring the oil left " + left + " in the hand instead of a bucket");
                })
                .thenExecute(() -> expectCooling(helper, main, output, 100000, "large_transformer.finished", null))
                .thenExecute(() -> TFMGPowerKit.unloadAndReload(helper, List.of(main, output)))
                .thenWaitUntil(() -> {
                    expectCooling(helper, main, output, 100000, "large_transformer.finished", null);
                    helper.assertTrue(TFMGPowerKit.volts(helper, load) == 300, "after a reload the load sees " + TFMGPowerKit.volts(helper, load) + " V");
                })
                .thenSucceed();
    }

    private static void expectCooling(GameTestHelper helper, BlockPos main, BlockPos output, int cap, String key, String fix) {
        int passed = TFMGPowerKit.electric(helper, output).powerGeneration();
        helper.assertTrue(passed == cap, "the large transformer passes " + passed + " W instead of " + cap);
        for (BlockPos part : new BlockPos[]{main, output}) {
            Report report = report(helper, part);
            helper.assertTrue(String.valueOf(cap).equals(report.arg(key, 0)), "the inspector on " + part + " does not read "
                    + key + " " + cap + ": " + report);
            helper.assertTrue(fix == null || report.has(fix), "the inspector on " + part + " does not suggest " + fix + ": " + report);
            helper.assertTrue(report.problems.isEmpty(), "the inspector flags " + report.problems + ": " + report);
        }
    }

    // ============================================== blaze burner under a vat

    static Fluid naphtha() {
        return (Fluid) TFMGFluids.NAPHTHA.getSource();
    }

    static Fluid ethylene() {
        return (Fluid) TFMGFluids.ETHYLENE.getSource();
    }

    /**
     * Blaze burners heat a chemical vat with Create's boiler heat: a kindled
     * burner counts 1, a seething one 2, and the burners under a wide vat add
     * up. Naphtha cracking needs heat 2: one kindled burner under a single
     * vat is refused and the inspector says how much heat is missing, two
     * kindled burners under a 2x2 vat crack it, and a blaze cake that makes
     * the single burner seethe gets that vat going too.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 1600)
    public static void blazeBurnersHeatAChemicalVat(GameTestHelper helper) {
        BlockPos single = new BlockPos(3, 2, 3);
        BlockPos wide = new BlockPos(7, 2, 3);
        List<BlockPos> burners = List.of(single.below(), wide.below(), wide.offset(1, -1, 1));
        helper.setBlock(single, TFMGBlocks.CAST_IRON_CHEMICAL_VAT.get().defaultBlockState());
        for (int x = 0; x < 2; x++)
            for (int z = 0; z < 2; z++)
                helper.setBlock(wide.offset(x, 0, z), TFMGBlocks.CAST_IRON_CHEMICAL_VAT.get().defaultBlockState());
        for (BlockPos burner : burners)
            helper.setBlock(burner, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.SMOULDERING));
        for (BlockPos vat : List.of(single, wide)) {
            helper.setBlock(vat.above(), TFMGBlocks.INDUSTRIAL_MIXER.get().defaultBlockState());
            motor(helper, vat.above(2), Direction.DOWN, 64);
        }
        Player player = TFMGOilEngineTestKit.player(helper);
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    for (BlockPos vat : List.of(single, wide)) {
                        ((IndustrialMixerBlockEntity) helper.getBlockEntity(vat.above())).setMixerMode(new ItemStack(TFMGItems.MIXER_BLADE.get()), false);
                        TFMGMetallurgyChemistryTests.fillVat(helper, vat, new FluidStack(naphtha(), 1000));
                    }
                    for (BlockPos burner : burners)
                        TFMGOilEngineTestKit.use(helper, player, burner, new ItemStack(Items.COAL_BLOCK));
                })
                .thenExecuteAfter(1, () -> {
                    for (BlockPos burner : burners)
                        helper.assertBlockState(burner, s -> s.getValue(BlazeBurnerBlock.HEAT_LEVEL) == HeatLevel.KINDLED,
                                () -> "a blaze burner fed a block of coal is not kindled");
                })
                .thenWaitUntil(() -> helper.assertTrue(amount(((VatBlockEntity) helper.getBlockEntity(wide)).outputTank.getCapability(), ethylene()) >= 250,
                        "two kindled burners under a 2x2 vat did not crack naphtha: " + report(helper, wide)))
                .thenExecute(() -> {
                    Report wideReport = report(helper, wide);
                    helper.assertTrue("2".equals(wideReport.arg("vat.heat", 0)), "two kindled burners give the 2x2 vat heat "
                            + wideReport.arg("vat.heat", 0) + ": " + wideReport);
                    // The single vat, one kindled burner: heat 1 of the 2 cracking needs.
                    Report singleReport = report(helper, single);
                    helper.assertTrue(singleReport.problems.contains("vat.heat_low") && "1".equals(singleReport.arg("vat.heat_low", 0))
                            && "2".equals(singleReport.arg("vat.heat_low", 1)), "a single kindled burner is not reported as heat 1 of 2: " + singleReport);
                    helper.assertTrue(amount(helper, single, naphtha()) == 1000, "the under-heated vat used its naphtha");
                    TFMGOilEngineTestKit.use(helper, player, single.below(), AllItems.BLAZE_CAKE.asStack());
                })
                .thenExecuteAfter(1, () -> helper.assertBlockState(single.below(), s -> s.getValue(BlazeBurnerBlock.HEAT_LEVEL) == HeatLevel.SEETHING,
                        () -> "a blaze cake did not make the burner seethe"))
                .thenWaitUntil(() -> helper.assertTrue(amount(((VatBlockEntity) helper.getBlockEntity(single)).outputTank.getCapability(), ethylene()) >= 250,
                        "a seething burner did not get the vat cracking: " + report(helper, single)))
                .thenExecute(() -> {
                    Report singleReport = report(helper, single);
                    helper.assertTrue("2".equals(singleReport.arg("vat.heat", 0)), "a seething burner gives heat "
                            + singleReport.arg("vat.heat", 0) + ": " + singleReport);
                })
                .thenSucceed();
    }

    // ============================================================= winding

    static ItemStack fullSpool(Item spool) {
        ItemStack stack = new ItemStack(spool);
        stack.set(TFMGDataComponents.SPOOL_AMOUNT, 1000);
        return stack;
    }

    static void windingMachine(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, TFMGBlocks.WINDING_MACHINE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        // Its shaft faces the counter-clockwise side of its facing: west.
        motor(helper, pos.west(), Direction.EAST, 64);
    }

    static int spoolTurns(GameTestHelper helper, BlockPos machine) {
        return ((WindingMachineBlockEntity) helper.getBlockEntity(machine)).spool.getOrDefault(TFMGDataComponents.SPOOL_AMOUNT, 0);
    }

    static ItemStack workpiece(GameTestHelper helper, BlockPos machine) {
        return ((WindingMachineBlockEntity) helper.getBlockEntity(machine)).inventory.getStackInSlot(0);
    }

    /**
     * A constantan spool winds an unfinished resistor into a resistor of the
     * resistance the dial asks for, one ohm per turn; turning the dial up
     * winds the same resistor further, and the dial survives a chunk
     * reload. A copper spool cannot wind a resistor: the machine waits and
     * the inspector names the spool it needs.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 600)
    public static void windingMachineWindsResistorsToItsDial(GameTestHelper helper) {
        BlockPos machine = new BlockPos(2, 1, 1);
        BlockPos wrong = new BlockPos(2, 1, 3);
        windingMachine(helper, machine);
        windingMachine(helper, wrong);
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    TFMGPowerKit.scroll(helper, machine).setValue(5);
                    IItemHandler h = items(helper, machine, null);
                    h.insertItem(1, fullSpool(TFMGItems.CONSTANTAN_SPOOL.get()), false);
                    h.insertItem(0, new ItemStack(TFMGItems.UNFINISHED_RESISTOR.get()), false);
                    IItemHandler w = items(helper, wrong, null);
                    w.insertItem(1, fullSpool(TFMGItems.COPPER_SPOOL.get()), false);
                    w.insertItem(0, new ItemStack(TFMGItems.UNFINISHED_RESISTOR.get()), false);
                })
                .thenWaitUntil(() -> {
                    ItemStack work = workpiece(helper, machine);
                    helper.assertTrue(work.is(TFMGBlocks.RESISTOR.asItem()) && work.getOrDefault(TFMGDataComponents.RESISTANCE, 0) == 50,
                            "no 50 ohm resistor yet: " + work + " " + work.get(TFMGDataComponents.RESISTANCE));
                })
                .thenExecute(() -> {
                    helper.assertTrue(spoolTurns(helper, machine) == 950, "a 50 ohm resistor took " + (1000 - spoolTurns(helper, machine)) + " turns");
                    Report done = report(helper, machine);
                    helper.assertTrue(done.has("winding_machine.target_reached") && done.problems.isEmpty(), "finished resistor: " + done);
                    helper.assertTrue(workpiece(helper, wrong).is(TFMGItems.UNFINISHED_RESISTOR.get()) && spoolTurns(helper, wrong) == 1000,
                            "a copper spool wound a resistor: " + workpiece(helper, wrong) + ", " + spoolTurns(helper, wrong) + " turns left");
                    Report stuck = report(helper, wrong);
                    helper.assertTrue(stuck.problems.contains("winding_machine.wrong_spool"), "a copper spool on a resistor is not reported: " + stuck);
                    TFMGPowerKit.scroll(helper, machine).setValue(8);
                })
                .thenWaitUntil(() -> helper.assertTrue(workpiece(helper, machine).getOrDefault(TFMGDataComponents.RESISTANCE, 0) == 80,
                        "the resistor did not wind on to 80 ohm: " + workpiece(helper, machine).get(TFMGDataComponents.RESISTANCE)))
                .thenExecute(() -> {
                    helper.assertTrue(spoolTurns(helper, machine) == 920, "80 ohm took " + (1000 - spoolTurns(helper, machine)) + " turns");
                    TFMGPowerKit.unloadAndReload(helper, List.of(machine));
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(TFMGPowerKit.scroll(helper, machine).getValue() == 8, "the dial reads "
                            + TFMGPowerKit.scroll(helper, machine).getValue() + " after a reload");
                    helper.assertTrue(spoolTurns(helper, machine) == 920, "the spool holds " + spoolTurns(helper, machine) + " turns after a reload");
                    ItemStack out = items(helper, machine, null).extractItem(0, 1, false);
                    helper.assertTrue(out.is(TFMGBlocks.RESISTOR.asItem()) && out.getOrDefault(TFMGDataComponents.RESISTANCE, 0) == 80,
                            "automation took " + out + " out of the machine");
                })
                .thenSucceed();
    }

    /**
     * A block of laminated magnetic alloy and a copper spool make a large
     * coil with as many turns as the dial asks for (200 by default).
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 600)
    public static void windingMachineWindsALargeCoil(GameTestHelper helper) {
        BlockPos machine = new BlockPos(2, 1, 2);
        windingMachine(helper, machine);
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    IItemHandler h = items(helper, machine, null);
                    h.insertItem(1, fullSpool(TFMGItems.COPPER_SPOOL.get()), false);
                    h.insertItem(0, new ItemStack(TFMGBlocks.LAMINATED_MAGNETIC_ALLOY_BLOCK.get()), false);
                })
                .thenWaitUntil(() -> helper.assertTrue(workpiece(helper, machine).is(TFMGBlocks.LARGE_COIL.asItem()),
                        "no large coil yet: " + report(helper, machine)))
                .thenExecute(() -> {
                    ItemStack coil = workpiece(helper, machine);
                    helper.assertTrue(coil.getOrDefault(TFMGDataComponents.COIL_TURNS, 0) == 200, "the large coil has "
                            + coil.get(TFMGDataComponents.COIL_TURNS) + " turns");
                    helper.assertTrue(spoolTurns(helper, machine) == 800, "200 turns took " + (1000 - spoolTurns(helper, machine)) + " from the spool");
                })
                .thenSucceed();
    }

    /** Each wire winds onto an empty spool, which becomes that metal's spool: 125 turns per wire. */
    @GameTestGenerator
    public static List<TestFunction> wireOntoSpools() {
        List<TestFunction> tests = new ArrayList<>();
        Map<String, Supplier<Item[]>> cases = new TreeMap<>();
        cases.put("copper", () -> new Item[]{TFMGItems.COPPER_WIRE.get(), TFMGItems.COPPER_SPOOL.get()});
        cases.put("aluminum", () -> new Item[]{TFMGItems.ALUMINUM_WIRE.get(), TFMGItems.ALUMINUM_SPOOL.get()});
        cases.put("constantan", () -> new Item[]{TFMGItems.CONSTANTAN_WIRE.get(), TFMGItems.CONSTANTAN_SPOOL.get()});
        for (Map.Entry<String, Supplier<Item[]>> e : cases.entrySet())
            tests.add(TFMGGameTestUtil.test(BATCH, "winding.wire." + e.getKey(), TFMGGameTestUtil.PLATFORM, 400,
                    helper -> runWireOntoSpool(helper, e.getValue().get()[0], e.getValue().get()[1])));
        return tests;
    }

    private static void runWireOntoSpool(GameTestHelper helper, Item wire, Item spool) {
        BlockPos machine = new BlockPos(2, 1, 2);
        windingMachine(helper, machine);
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    IItemHandler h = items(helper, machine, null);
                    h.insertItem(1, TFMGItems.EMPTY_SPOOL.asStack(), false);
                    h.insertItem(0, new ItemStack(wire), false);
                })
                .thenWaitUntil(() -> {
                    WindingMachineBlockEntity be = helper.getBlockEntity(machine);
                    helper.assertTrue(be.spool.is(spool) && spoolTurns(helper, machine) == 125 && be.wireTurnsPending == 0,
                            "one wire wound " + be.spool + " with " + spoolTurns(helper, machine) + " turns, " + be.wireTurnsPending + " pending");
                })
                .thenExecute(() -> helper.assertTrue(workpiece(helper, machine).isEmpty(), "the wire was not used up"))
                .thenSucceed();
    }

    /**
     * The winding steps of the sequenced recipes: a shaft becomes an
     * unfinished electric motor on a copper spool (75 turns), a heavy
     * machinery casing an unfinished potentiometer on a constantan spool
     * (100 turns), and an unfinished generator at its winding step moves on.
     */
    @GameTestGenerator
    public static List<TestFunction> sequencedWinding() {
        List<TestFunction> tests = new ArrayList<>();
        tests.add(TFMGGameTestUtil.test(BATCH, "winding.sequenced.motor", TFMGGameTestUtil.PLATFORM, 400,
                helper -> runSequencedWinding(helper, AllBlocks.SHAFT.asStack(), TFMGItems.COPPER_SPOOL.get(), "tfmg:sequenced_assembly/motor", 0, 75)));
        tests.add(TFMGGameTestUtil.test(BATCH, "winding.sequenced.potentiometer", TFMGGameTestUtil.PLATFORM, 400,
                helper -> runSequencedWinding(helper, TFMGBlocks.HEAVY_MACHINERY_CASING.asStack(), TFMGItems.CONSTANTAN_SPOOL.get(),
                        "tfmg:sequenced_assembly/potentiometer", 0, 100)));
        tests.add(TFMGGameTestUtil.test(BATCH, "winding.sequenced.generator", TFMGGameTestUtil.PLATFORM, 400,
                helper -> {
                    ItemStack midway = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("tfmg:unfinished_generator")));
                    midway.set(AllDataComponents.SEQUENCED_ASSEMBLY, new SequencedAssemblyRecipe.SequencedAssembly(
                            ResourceLocation.parse("tfmg:sequenced_assembly/generator"), 2, 2f / 18f));
                    runSequencedWinding(helper, midway, TFMGItems.COPPER_SPOOL.get(), "tfmg:sequenced_assembly/generator", 2, 75);
                }));
        return tests;
    }

    private static void runSequencedWinding(GameTestHelper helper, ItemStack start, Item spool, String recipe, int step, int turns) {
        BlockPos machine = new BlockPos(2, 1, 2);
        windingMachine(helper, machine);
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    IItemHandler h = items(helper, machine, null);
                    h.insertItem(1, fullSpool(spool), false);
                    helper.assertTrue(h.insertItem(0, start.copy(), false).isEmpty(), "the machine refused " + start);
                })
                .thenWaitUntil(() -> {
                    SequencedAssemblyRecipe.SequencedAssembly progress = workpiece(helper, machine).get(AllDataComponents.SEQUENCED_ASSEMBLY);
                    helper.assertTrue(progress != null && progress.step() == step + 1, "the winding step is not done: "
                            + workpiece(helper, machine) + " " + progress + " -> " + report(helper, machine));
                })
                .thenExecute(() -> {
                    SequencedAssemblyRecipe.SequencedAssembly progress = workpiece(helper, machine).get(AllDataComponents.SEQUENCED_ASSEMBLY);
                    helper.assertTrue(progress.id().toString().equals(recipe), "the item moved on in " + progress.id());
                    helper.assertTrue(spoolTurns(helper, machine) == 1000 - turns, "the step took " + (1000 - spoolTurns(helper, machine))
                            + " turns instead of " + turns);
                })
                .thenSucceed();
    }

    // ============================================================== fluids

    /** Every TFMG fluid that has a world block, by its source. */
    static List<Fluid> placeableFluids() {
        List<Fluid> out = new ArrayList<>();
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (!id.getNamespace().equals(TFMG.MOD_ID) || !fluid.isSource(fluid.defaultFluidState()))
                continue;
            if (fluid.defaultFluidState().createLegacyBlock().getBlock() instanceof LiquidBlock)
                out.add(fluid);
        }
        return out;
    }

    /** Every TFMG gas, which has no world block and comes in a tank. */
    static List<Fluid> gases() {
        List<Fluid> out = new ArrayList<>();
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id.getNamespace().equals(TFMG.MOD_ID) && fluid instanceof VirtualFluid && fluid.isSource(fluid.defaultFluidState()))
                out.add(fluid);
        }
        return out;
    }

    static String path(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).getPath();
    }

    static boolean sets(Fluid fluid) {
        return fluid.isSame(TFMGFluids.LIQUID_CONCRETE.getSource()) || fluid.isSame(TFMGFluids.LIQUID_ASPHALT.getSource());
    }

    /**
     * Every placeable TFMG fluid: its bucket, used by a player looking at the
     * floor, pours a source block and comes back empty; the fluid flows out
     * (concrete and asphalt are meant to stay where they are poured); and an
     * empty bucket picks the source up again into a full bucket of it.
     */
    @GameTestGenerator
    public static List<TestFunction> fluidBuckets() {
        List<TestFunction> tests = new ArrayList<>();
        for (Fluid fluid : placeableFluids())
            tests.add(TFMGGameTestUtil.test(BATCH, "fluid.bucket." + path(fluid), TFMGGameTestUtil.PLATFORM, 200,
                    helper -> runBucket(helper, fluid)));
        return tests;
    }

    /**
     * Walls in the platform's 3x3 middle so poured fluid stays on it. The
     * template's floor is the layer at y=1, so the pool stands at y=2.
     */
    static void pool(GameTestHelper helper) {
        for (int x = 0; x < 5; x++)
            for (int z = 0; z < 5; z++)
                if (x == 0 || z == 0 || x == 4 || z == 4)
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
    }

    /**
     * A bare server player, for items whose use casts the player to a
     * ServerPlayer (a bucket picking up a fluid does). It is never logged in:
     * see ERROR_LOG, a logged-in mock player crashes the test server.
     */
    static Player serverPlayer(GameTestHelper helper) {
        return new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "tfmg-test"),
                net.minecraft.server.level.ClientInformation.createDefault());
    }

    /** Stands the player over {@code rel}, looking straight down. */
    static void aimDown(GameTestHelper helper, Player player, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        player.moveTo(abs.getX() + 0.5, abs.getY() + 0.1, abs.getZ() + 0.5, 0, 90);
    }

    static ItemStack useItem(GameTestHelper helper, Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResultHolder<ItemStack> result = stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, result.getObject());
        return result.getObject();
    }

    private static void runBucket(GameTestHelper helper, Fluid fluid) {
        String name = path(fluid);
        pool(helper);
        BlockPos at = new BlockPos(2, 2, 2);
        BlockPos beside = new BlockPos(3, 2, 2);
        Player player = serverPlayer(helper);
        Item bucket = fluid.getBucket();
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(bucket instanceof BucketItem, name + " has no bucket");
                    aimDown(helper, player, at);
                    ItemStack left = useItem(helper, player, new ItemStack(bucket));
                    helper.assertTrue(left.is(Items.BUCKET), "pouring " + name + " left " + left + " in the hand");
                    FluidState placed = helper.getLevel().getFluidState(helper.absolutePos(at));
                    helper.assertTrue(placed.isSource() && placed.getType().isSame(fluid), "the " + name + " bucket poured " + placed);
                })
                .thenIdle(sets(fluid) ? 5 : 0)
                .thenWaitUntil(() -> {
                    FluidState next = helper.getLevel().getFluidState(helper.absolutePos(beside));
                    if (sets(fluid))
                        helper.assertTrue(next.isEmpty(), name + " ran out of where it was poured");
                    else
                        helper.assertTrue(next.getType().isSame(fluid) && !next.isSource(), name + " does not flow: " + next);
                })
                .thenExecute(() -> {
                    aimDown(helper, player, at);
                    ItemStack full = useItem(helper, player, new ItemStack(Items.BUCKET));
                    helper.assertTrue(full.is(bucket), "an empty bucket picked up " + full + " from " + name);
                    FluidState left = helper.getLevel().getFluidState(helper.absolutePos(at));
                    helper.assertTrue(!(left.isSource() && left.getType().isSame(fluid)), "the " + name + " source is still there");
                })
                .thenSucceed();
    }

    /**
     * Molten metals, slag, plastic and silicon glow at full light and set a
     * pig standing in them on fire; sulfuric acid hurts it.
     */
    @GameTestGenerator
    public static List<TestFunction> fluidEffects() {
        List<TestFunction> tests = new ArrayList<>();
        for (Fluid fluid : placeableFluids()) {
            boolean hot = fluid.getFluidType().getTemperature() > 1000;
            boolean acid = fluid.isSame(TFMGFluids.SULFURIC_ACID.getSource());
            if (hot || acid)
                tests.add(TFMGGameTestUtil.test(BATCH, "fluid.effect." + path(fluid), TFMGGameTestUtil.PLATFORM, 200,
                        helper -> runFluidEffect(helper, fluid, hot)));
        }
        return tests;
    }

    private static void runFluidEffect(GameTestHelper helper, Fluid fluid, boolean hot) {
        String name = path(fluid);
        pool(helper);
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, fluid.defaultFluidState().createLegacyBlock());
        Pig pig = helper.spawn(EntityType.PIG, at);
        if (hot) {
            int light = helper.getLevel().getBlockState(helper.absolutePos(at)).getLightEmission(helper.getLevel(), helper.absolutePos(at));
            helper.assertTrue(light == 15, name + " gives light " + light + " instead of 15");
        }
        helper.succeedWhen(() -> {
            if (hot)
                helper.assertTrue(pig.getRemainingFireTicks() > 0 || !pig.isAlive(), "a pig in " + name + " is not on fire");
            else
                helper.assertTrue(!pig.isAlive() || pig.getHealth() < pig.getMaxHealth(), "a pig in " + name + " is not hurt");
        });
    }

    /** Liquid concrete sets into concrete and liquid asphalt into asphalt, wherever they lie. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 100)
    public static void concreteAndAsphaltSet(GameTestHelper helper) {
        Map<Fluid, Block> cases = new LinkedHashMap<>();
        cases.put(TFMGFluids.LIQUID_CONCRETE.getSource(), TFMGBlocks.CONCRETE.block.get());
        cases.put(TFMGFluids.LIQUID_ASPHALT.getSource(), TFMGBlocks.ASPHALT.get());
        RandomSource random = RandomSource.create(42);
        int x = 1;
        for (Map.Entry<Fluid, Block> e : cases.entrySet()) {
            BlockPos rel = new BlockPos(x, 1, 2);
            x += 2;
            BlockPos abs = helper.absolutePos(rel);
            ServerLevel level = helper.getLevel();
            level.setBlock(abs, e.getKey().defaultFluidState().createLegacyBlock(), 3);
            FluidState state = level.getFluidState(abs);
            helper.assertTrue(state.isRandomlyTicking(), path(e.getKey()) + " never ticks, so it would never set");
            for (int i = 0; i < 200 && !level.getBlockState(abs).is(e.getValue()); i++)
                level.getFluidState(abs).randomTick(level, abs, random);
            helper.assertBlockPresent(e.getValue(), rel);
        }
        helper.succeed();
    }

    /**
     * Every gas comes in a tank: a spout fills an empty bucket with 1000 mB
     * of it into that gas's tank, the tank empties back into a fluid tank,
     * and using a full tank on the floor places nothing and keeps the gas.
     */
    @GameTestGenerator
    public static List<TestFunction> gasTanks() {
        List<TestFunction> tests = new ArrayList<>();
        for (Fluid gas : gases())
            tests.add(TFMGGameTestUtil.test(BATCH, "fluid.gas_tank." + path(gas), TFMGGameTestUtil.PLATFORM, 100,
                    helper -> runGasTank(helper, gas)));
        return tests;
    }

    private static void runGasTank(GameTestHelper helper, Fluid gas) {
        String name = path(gas);
        Item tankItem = BuiltInRegistries.ITEM.get(TFMG.asResource(name + "_bucket"));
        helper.assertTrue(tankItem instanceof BucketItem, name + " has no tank item");
        ServerLevel level = helper.getLevel();
        FluidStack supply = new FluidStack(gas, 1000);
        ItemStack empty = new ItemStack(Items.BUCKET);
        int needed = FillingBySpout.getRequiredAmountForItem(level, empty, supply);
        helper.assertTrue(needed == 1000, "a spout cannot fill a bucket with " + name + " (needs " + needed + " mB)");
        ItemStack filled = FillingBySpout.fillItem(level, needed, empty.copy(), supply.copy());
        helper.assertTrue(filled.is(tankItem), "a spout filled a bucket with " + name + " into " + filled);

        BlockPos target = new BlockPos(1, 1, 2);
        tank(helper, target);
        IFluidHandler handler = fluids(helper, target, null);
        var result = FluidUtil.tryEmptyContainer(filled, handler, 1000, null, true);
        helper.assertTrue(result.isSuccess() && result.getResult().is(Items.BUCKET), "the " + name + " tank did not empty into a fluid tank");
        helper.assertTrue(amount(handler, gas) == 1000, "the fluid tank holds " + amount(handler, gas) + " mB of " + name);

        BlockPos floor = new BlockPos(3, 2, 2);
        Player player = serverPlayer(helper);
        aimDown(helper, player, floor);
        ItemStack kept = useItem(helper, player, new ItemStack(tankItem));
        helper.assertTrue(kept.is(tankItem), "using a " + name + " tank on the floor left " + kept);
        helper.assertBlockPresent(Blocks.AIR, floor);
        helper.succeed();
    }

    // ------------------------------------------------- handbook and data

    static Path handbookEntry(String entry) {
        var file = ModList.get().getModFileById(TFMG.MOD_ID).getFile();
        return file.findResource("assets", TFMG.MOD_ID, "patchouli_books", "handbook", "en_us", "entries", entry + ".json");
    }

    static String handbookText(String entry) throws Exception {
        try (Reader reader = Files.newBufferedReader(handbookEntry(entry), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            StringBuilder text = new StringBuilder();
            for (JsonElement page : json.getAsJsonArray("pages")) {
                JsonObject p = page.getAsJsonObject();
                if (p.has("text"))
                    text.append(p.get("text").getAsString()).append('\n');
            }
            return text.toString();
        }
    }

    static List<String> sourceNames(net.minecraft.tags.TagKey<Fluid> tag) {
        List<String> out = new ArrayList<>();
        for (Holder<Fluid> holder : BuiltInRegistries.FLUID.getTagOrEmpty(tag))
            if (holder.value().isSource(holder.value().defaultFluidState()))
                out.add(holder.value().getFluidType().getDescription().getString().toLowerCase(Locale.ROOT));
        return out;
    }

    /**
     * The handbook says what the code does: the firebox page lists exactly
     * the fluids of the firebox fuel tag, the engine fuel table gives every
     * built-in fuel with its own speed, efficiency and torque, and the
     * flamethrower page gives every flamethrower fuel with its own spread,
     * speed and sparks.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 100)
    public static void handbookFuelListsMatchTheCode(GameTestHelper helper) throws Exception {
        List<String> problems = new ArrayList<>();

        // Firebox fuels.
        String firebox = handbookText("fireboxes_and_gases/00_the_firebox");
        Matcher list = Pattern.compile("Only fluids in the firebox fuel list burn: \\$\\(item\\)([^$]+)\\$\\(\\)").matcher(firebox);
        if (!list.find()) {
            problems.add("the firebox page no longer lists its fuels");
        } else {
            TreeSet<String> written = new TreeSet<>();
            for (String part : list.group(1).split(",| and "))
                if (!part.isBlank())
                    written.add(part.trim().toLowerCase(Locale.ROOT));
            TreeSet<String> tagged = new TreeSet<>(sourceNames(TFMGTags.TFMGFluidTags.FIREBOX_FUEL.tag));
            if (!written.equals(tagged))
                problems.add("firebox page lists " + written + ", the firebox fuel tag holds " + tagged);
        }

        // Engine fuels.
        BaseFuelTypes.register();
        Map<String, float[]> table = new TreeMap<>();
        Matcher row = Pattern.compile("\\$\\(item\\)([^$]+)\\$\\(\\): ([0-9.]+) / ([0-9.]+) / ([0-9.]+)").matcher(handbookText("engines/08_fuel_table"));
        while (row.find())
            table.put(row.group(1).toLowerCase(Locale.ROOT).replace(' ', '_'),
                    new float[]{Float.parseFloat(row.group(2)), Float.parseFloat(row.group(3)), Float.parseFloat(row.group(4))});
        for (Map.Entry<ResourceLocation, FuelType> e : EngineFuelTypeManager.BUILTIN_TYPE_MAP.entrySet()) {
            if (e.getValue() == BaseFuelTypes.FALLBACK)
                continue;
            String fuel = e.getKey().getPath();
            float[] written = table.remove(fuel);
            FuelType type = e.getValue();
            if (written == null)
                problems.add("the fuel table does not list " + fuel);
            else if (Math.abs(written[0] - type.getSpeed()) > 1e-3 || Math.abs(written[1] - type.getEfficiency()) > 1e-3
                    || Math.abs(written[2] - type.getStress()) > 1e-3)
                problems.add("the fuel table gives " + fuel + " " + written[0] + " / " + written[1] + " / " + written[2] + ", the code "
                        + type.getSpeed() + " / " + type.getEfficiency() + " / " + type.getStress());
        }
        if (!table.isEmpty())
            problems.add("the fuel table lists fuels the code does not have: " + table.keySet());

        // Flamethrower fuels.
        Map<String, float[]> flames = new TreeMap<>();
        Matcher flame = Pattern.compile("\\$\\(item\\)([^$]+)\\$\\(\\): ([0-9.]+) / ([0-9.]+) / ([0-9.]+)")
                .matcher(handbookText("weapons_and_tools/01_flamethrower_fuels"));
        while (flame.find())
            flames.put(flame.group(1).toLowerCase(Locale.ROOT).replace(' ', '_'),
                    new float[]{Float.parseFloat(flame.group(2)), Float.parseFloat(flame.group(3)), Float.parseFloat(flame.group(4))});
        HolderLookup.RegistryLookup<FlamethrowerFuelType> fuels = helper.getLevel().registryAccess().lookupOrThrow(TFMGRegistries.FLAMETHROWER_FUEL_TYPE);
        for (Holder.Reference<FlamethrowerFuelType> ref : fuels.listElements().toList()) {
            String fuel = ref.key().location().getPath();
            if (fuel.equals("fallback"))
                continue;
            FlamethrowerFuelType type = ref.value();
            float[] written = flames.remove(fuel);
            if (written == null)
                problems.add("the flamethrower page does not list " + fuel);
            else if (written[0] != type.spread() || Math.abs(written[1] - type.speed()) > 1e-3 || written[2] != type.amount())
                problems.add("the flamethrower page gives " + fuel + " " + written[0] + " / " + written[1] + " / " + written[2]
                        + ", the data " + type.spread() + " / " + type.speed() + " / " + type.amount());
            if (type.fluids().size() == 0)
                problems.add("the flamethrower fuel " + fuel + " burns no fluid");
        }
        if (!flames.isEmpty())
            problems.add("the flamethrower page lists fuels the data does not have: " + flames.keySet());

        helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
        helper.succeed();
    }

    // ======================================================= dials and values

    /**
     * Every value a player dials on a TFMG block, found by looking at every
     * block entity: set to something other than its default, it comes back
     * the same from a save and reload.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 100)
    public static void everyDialSurvivesASaveAndReload(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();
        List<String> checked = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Block block : TFMGGameTestUtil.tfmgBlocks()) {
            if (!(block instanceof EntityBlock))
                continue;
            String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
            helper.setBlock(pos, block.defaultBlockState());
            BlockPos abs = helper.absolutePos(pos);
            if (level.getBlockEntity(abs) instanceof SmartBlockEntity smart && smart.getBehaviour(ScrollValueBehaviour.TYPE) != null) {
                ScrollValueBehaviour scroll = smart.getBehaviour(ScrollValueBehaviour.TYPE);
                int before = scroll.getValue();
                scroll.setValue(before + 1);
                if (scroll.getValue() == before)
                    scroll.setValue(before - 1);
                int set = scroll.getValue();
                if (set == before) {
                    problems.add(name + ": the dial does not move from " + before);
                } else {
                    CompoundTag saved = smart.saveWithFullMetadata(registries);
                    BlockEntity copy = BlockEntity.loadStatic(abs, level.getBlockState(abs), saved, registries);
                    int read = copy instanceof SmartBlockEntity smartCopy && smartCopy.getBehaviour(ScrollValueBehaviour.TYPE) != null
                            ? smartCopy.getBehaviour(ScrollValueBehaviour.TYPE).getValue() : Integer.MIN_VALUE;
                    if (read != set)
                        problems.add(name + ": dialled " + set + ", reloaded " + read);
                    checked.add(name);
                }
            }
            helper.setBlock(pos, Blocks.AIR);
        }
        for (String expected : List.of("creative_generator", "converter", "potentiometer", "encased_potentiometer", "winding_machine",
                "electric_motor", "steel_distillation_output", "traffic_light"))
            if (!checked.contains(expected))
                problems.add(expected + " has no dial any more");
        helper.assertTrue(problems.isEmpty(), String.join("; ", problems) + " (checked " + checked + ")");
        helper.succeed();
    }

    /** The electric motor's dial turns it the other way, and keeps doing so after a reload. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 300)
    public static void electricMotorDialReversesIt(GameTestHelper helper) {
        BlockPos generator = new BlockPos(2, 1, 2);
        BlockPos motor = generator.above();
        TFMGPowerKit.creativeGenerator(helper, generator, 100);
        TFMGPowerKit.place(helper, motor, TFMGBlocks.ELECTRIC_MOTOR.get(), Direction.UP);
        float[] first = new float[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(Math.abs(TFMGOilEngineTestKit.speed(helper, motor)) == 80,
                        "the motor turns at " + TFMGOilEngineTestKit.speed(helper, motor) + " RPM on 100 V"))
                .thenExecute(() -> {
                    first[0] = TFMGOilEngineTestKit.speed(helper, motor);
                    ScrollValueBehaviour dial = TFMGPowerKit.scroll(helper, motor);
                    dial.setValue(1 - dial.getValue());
                })
                .thenWaitUntil(() -> helper.assertTrue(TFMGOilEngineTestKit.speed(helper, motor) == -first[0],
                        "the dial did not reverse the motor: " + TFMGOilEngineTestKit.speed(helper, motor) + " RPM"))
                .thenExecute(() -> TFMGPowerKit.unloadAndReload(helper, List.of(motor)))
                .thenIdle(5)
                .thenWaitUntil(() -> helper.assertTrue(TFMGOilEngineTestKit.speed(helper, motor) == -first[0],
                        "after a reload the motor turns at " + TFMGOilEngineTestKit.speed(helper, motor) + " RPM"))
                .thenSucceed();
    }

    /** The traffic light's dial sets the length of its whole red, orange and green cycle, and survives a reload. */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 1400)
    public static void trafficLightDialSetsItsCycle(GameTestHelper helper) {
        BlockPos generator = new BlockPos(2, 1, 2);
        BlockPos light = generator.above();
        TFMGPowerKit.creativeGenerator(helper, generator, 100);
        TFMGPowerKit.place(helper, light, TFMGBlocks.TRAFFIC_LIGHT.get(), Direction.NORTH);
        int length = 400;
        List<Long> reds = new ArrayList<>();
        int[] last = {-1};
        helper.startSequence()
                .thenExecuteAfter(2, () -> TFMGPowerKit.scroll(helper, light).setValue(length))
                .thenExecuteFor(length * 2 + 60, () -> {
                    int now = helper.getBlockEntity(light).saveWithoutMetadata(helper.getLevel().registryAccess()).getInt("Light");
                    if (now == 2 && last[0] == 0)
                        reds.add(helper.getTick());
                    last[0] = now;
                })
                .thenExecute(() -> {
                    helper.assertTrue(reds.size() >= 2, "the light turned red " + reds.size() + " times in two cycles");
                    long period = reds.get(1) - reds.get(0);
                    helper.assertTrue(Math.abs(period - length) <= 1, "a " + length + " tick dial gives a " + period + " tick cycle");
                    TFMGPowerKit.unloadAndReload(helper, List.of(light));
                })
                .thenExecuteAfter(2, () -> helper.assertTrue(TFMGPowerKit.scroll(helper, light).getValue() == length,
                        "the dial reads " + TFMGPowerKit.scroll(helper, light).getValue() + " after a reload"))
                .thenSucceed();
    }

    /**
     * A distillation stage set to keep its fluid stays full and the tower
     * keeps feeding the other stages, burning only their share of the oil;
     * set to void it, the tower burns that stage's share too and runs
     * through its oil half as fast again. The inspector reads the setting,
     * and the setting survives a reload.
     */
    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 1400)
    public static void distillationStageDialKeepsOrVoids(GameTestHelper helper) {
        BlockPos controller = new BlockPos(4, 2, 4);
        BlockPos tank = controller.south();
        List<BlockPos> stages = new ArrayList<>();
        DistillationRecipe[] recipe = new DistillationRecipe[1];
        Fluid crude = TFMGOilEngineTestKit.fluid("tfmg:crude_oil");
        int[] mark = new int[2];
        int[] drained = new int[2];
        // The controller pulls from the tank into its own 8000 mB buffer: count both.
        Supplier<Integer> oil = () -> TFMGOilEngineTestKit.amount(helper, tank, crude) + TFMGOilEngineTestKit.amount(helper, controller, crude);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    recipe[0] = TFMGOilEngineTestKit.recipe(helper, "tfmg:distillation/crude_oil_light_distillation");
                    stages.addAll(TFMGOilEngineTests.buildTower(helper, controller, recipe[0].getFluidResults().size()));
                })
                .thenExecuteAfter(20, () -> {
                    TFMGOilEngineTestKit.fill(helper, tank, crude, 150000);
                    // The other stages void what they cannot hold, so they always take their share.
                    for (BlockPos stage : stages.subList(1, stages.size()))
                        ((DistillationOutputBlockEntity) TFMGOilEngineTestKit.be(helper, stage)).mode
                                .setValue(DistillationOutputBlockEntity.DistillationOutputMode.VOID_WHEN_FULL.ordinal());
                    // The bottom stage is already full of its own fraction. Pipes
                    // can only drain a stage, so fill its tank directly.
                    Fluid bottom = recipe[0].getFluidResults().get(0).getFluid();
                    ((DistillationOutputBlockEntity) TFMGOilEngineTestKit.be(helper, stages.get(0))).tank
                            .fill(new FluidStack(bottom, 8000), IFluidHandler.FluidAction.EXECUTE);
                    Report keep = report(helper, stages.get(0));
                    helper.assertTrue(keep.has("distillation_output.keep"), "a fresh stage does not keep its fluid: " + keep);
                    TFMGOilEngineTestKit.fill(helper, tank.below(), TFMGOilEngineTestKit.fluid("tfmg:lpg"), 8000);
                })
                .thenWaitUntil(() -> helper.assertTrue(TFMGOilEngineTestKit.amount(helper, stages.get(1), recipe[0].getFluidResults().get(1).getFluid()) > 0,
                        "the tower does not feed the other stages while the first is full: " + TFMGOilEngineTestKit.report(helper, controller)))
                .thenExecute(() -> mark[0] = oil.get())
                .thenIdle(40)
                .thenExecute(() -> {
                    drained[0] = mark[0] - oil.get();
                    helper.assertTrue(TFMGOilEngineTestKit.amount(helper, stages.get(0), recipe[0].getFluidResults().get(0).getFluid()) == 8000,
                            "the kept stage changed while full");
                    ((DistillationOutputBlockEntity) TFMGOilEngineTestKit.be(helper, stages.get(0))).mode
                            .setValue(DistillationOutputBlockEntity.DistillationOutputMode.VOID_WHEN_FULL.ordinal());
                    TFMGPowerKit.unloadAndReload(helper, List.of(stages.get(0)));
                })
                .thenExecuteAfter(2, () -> {
                    Report voiding = report(helper, stages.get(0));
                    helper.assertTrue(voiding.has("distillation_output.void"), "the stage forgot its void setting on reload: " + voiding);
                    mark[1] = oil.get();
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    drained[1] = mark[1] - oil.get();
                    helper.assertTrue(TFMGOilEngineTestKit.amount(helper, tank, crude) > 0, "the tower ran out of oil during the test");
                    // Two of three shares while keeping, three of three while voiding.
                    helper.assertTrue(drained[0] > 0 && Math.abs(drained[1] * 2 - drained[0] * 3) <= drained[0] / 10,
                            "keeping burned " + drained[0] + " mB, voiding " + drained[1] + " mB in 40 ticks, not two shares against three");
                })
                .thenSucceed();
    }

    /**
     * Settings set by a click are saved: the voltmeter's mode cycled with a
     * wrench, and a light bulb's colour set with a dye, both come back after
     * a save and reload. A client that saw another mode follows the meter
     * back to plain voltage.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 100)
    public static void clickedSettingsAreSaved(GameTestHelper helper) {
        BlockPos meter = new BlockPos(1, 1, 2);
        BlockPos bulb = new BlockPos(3, 1, 2);
        helper.setBlock(meter, TFMGBlocks.VOLTMETER.get().defaultBlockState());
        helper.setBlock(bulb, TFMGBlocks.LIGHT_BULB.get().defaultBlockState());
        Player player = TFMGOilEngineTestKit.player(helper);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    ServerLevel level = helper.getLevel();
                    level.getChunkAt(helper.absolutePos(meter)).setUnsaved(false);
                    player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
                    TFMGPowerKit.click(helper, player, meter);
                    VoltMeterBlockEntity be = helper.getBlockEntity(meter);
                    helper.assertTrue(be.mode == VoltMeterBlockEntity.MeasureMode.HIGH_VOLTAGE, "the wrench set the voltmeter to " + be.mode);
                    helper.assertTrue(level.getChunkAt(helper.absolutePos(meter)).isUnsaved(), "the voltmeter mode change was not marked for saving");
                    TFMGOilEngineTestKit.use(helper, player, bulb, new ItemStack(Items.RED_DYE));
                    helper.assertTrue(((LightBulbBlockEntity) helper.getBlockEntity(bulb)).color == DyeColor.RED, "the dye did not colour the bulb");
                    TFMGPowerKit.unloadAndReload(helper, List.of(meter, bulb));
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertTrue(((VoltMeterBlockEntity) helper.getBlockEntity(meter)).mode == VoltMeterBlockEntity.MeasureMode.HIGH_VOLTAGE,
                            "the voltmeter mode was lost on reload");
                    helper.assertTrue(((LightBulbBlockEntity) helper.getBlockEntity(bulb)).color == DyeColor.RED, "the bulb colour was lost on reload");
                    // A second copy plays the client: it last saw high voltage, then the meter goes back to voltage.
                    HolderLookup.Provider registries = helper.getLevel().registryAccess();
                    VoltMeterBlockEntity server = helper.getBlockEntity(meter);
                    BlockEntity client = BlockEntity.loadStatic(server.getBlockPos(), server.getBlockState(), server.saveWithFullMetadata(registries), registries);
                    server.mode = VoltMeterBlockEntity.MeasureMode.VOLTAGE;
                    client.handleUpdateTag(server.getUpdateTag(registries), registries);
                    helper.assertTrue(((VoltMeterBlockEntity) client).mode == VoltMeterBlockEntity.MeasureMode.VOLTAGE,
                            "a client kept " + ((VoltMeterBlockEntity) client).mode + " after the meter went back to voltage");
                })
                .thenSucceed();
    }

    // ========================================================== decoration

    /** Every sliding door opens and closes by hand and by redstone, and drops one door when broken. */
    @GameTestGenerator
    public static List<TestFunction> slidingDoors() {
        List<TestFunction> tests = new ArrayList<>();
        for (BlockEntry<?> door : List.of(TFMGBlocks.HEAVY_CASING_DOOR, TFMGBlocks.STEEL_CASING_DOOR, TFMGBlocks.ALUMINUM_DOOR, TFMGBlocks.HEAVY_PLATED_DOOR))
            tests.add(TFMGGameTestUtil.test(BATCH, "door." + door.getId().getPath(), TFMGGameTestUtil.PLATFORM, 200,
                    helper -> runDoor(helper, door.get())));
        return tests;
    }

    private static void runDoor(GameTestHelper helper, Block door) {
        BlockPos lower = new BlockPos(2, 1, 2);
        BlockPos upper = lower.above();
        BlockState state = door.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        helper.setBlock(lower, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(upper, state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        Player player = TFMGOilEngineTestKit.player(helper);
        String name = BuiltInRegistries.BLOCK.getKey(door).getPath();
        helper.startSequence()
                .thenExecuteAfter(2, () -> TFMGOilEngineTestKit.use(helper, player, lower, ItemStack.EMPTY))
                .thenExecuteAfter(2, () -> {
                    helper.assertBlockState(lower, s -> s.getValue(DoorBlock.OPEN), () -> name + " did not open by hand");
                    helper.assertBlockState(upper, s -> s.getValue(DoorBlock.OPEN), () -> name + " opened only its lower half");
                    TFMGOilEngineTestKit.use(helper, player, upper, ItemStack.EMPTY);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertBlockState(lower, s -> !s.getValue(DoorBlock.OPEN), () -> name + " did not close by hand");
                    helper.setBlock(lower.east(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertBlockState(lower, s -> s.getValue(DoorBlock.OPEN), () -> name + " did not open on redstone");
                    helper.setBlock(lower.east(), Blocks.AIR);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertBlockState(lower, s -> !s.getValue(DoorBlock.OPEN), () -> name + " stayed open without redstone");
                    breakWithDrops(helper, lower);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertBlockPresent(Blocks.AIR, upper);
                    helper.assertItemEntityCountIs(door.asItem(), lower, 2.0, 1);
                })
                .thenSucceed();
    }

    /** The steel trapdoor opens by hand and by redstone. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 100)
    public static void steelTrapdoorOpens(GameTestHelper helper) {
        BlockPos hatch = new BlockPos(2, 1, 2);
        helper.setBlock(hatch, TFMGBlocks.STEEL_TRAPDOOR.get().defaultBlockState());
        Player player = TFMGOilEngineTestKit.player(helper);
        helper.startSequence()
                .thenExecuteAfter(1, () -> TFMGOilEngineTestKit.use(helper, player, hatch, ItemStack.EMPTY))
                .thenExecuteAfter(1, () -> {
                    helper.assertBlockState(hatch, s -> s.getValue(TrapDoorBlock.OPEN), () -> "the steel trapdoor did not open by hand");
                    TFMGOilEngineTestKit.use(helper, player, hatch, ItemStack.EMPTY);
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertBlockState(hatch, s -> !s.getValue(TrapDoorBlock.OPEN), () -> "the steel trapdoor did not close by hand");
                    helper.setBlock(hatch.east(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(2, () -> helper.assertBlockState(hatch, s -> s.getValue(TrapDoorBlock.OPEN), () -> "the steel trapdoor ignores redstone"))
                .thenSucceed();
    }

    /** A lithium torch on a wall gives light 14, and drops when its wall is taken away. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 100)
    public static void lithiumTorchHangsOnItsWall(GameTestHelper helper) {
        BlockPos wall = new BlockPos(1, 1, 2);
        BlockPos torch = new BlockPos(2, 1, 2);
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(torch, TFMGBlocks.LITHIUM_TORCH.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    int light = helper.getLevel().getBlockState(helper.absolutePos(torch)).getLightEmission(helper.getLevel(), helper.absolutePos(torch));
                    helper.assertTrue(light == 14, "the lithium torch gives light " + light);
                    helper.setBlock(wall, Blocks.AIR);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertBlockPresent(Blocks.AIR, torch);
                    helper.assertItemEntityCountIs(TFMGBlocks.LITHIUM_TORCH.asItem(), torch, 1.0, 1);
                })
                .thenSucceed();
    }

    /**
     * Every rebar shape takes liquid concrete from a bucket, gives it back to
     * an empty bucket, and once filled sets into the concrete version of the
     * same shape, keeping its facing.
     */
    @GameTestGenerator
    public static List<TestFunction> rebarSetsIntoConcrete() {
        List<TestFunction> tests = new ArrayList<>();
        tests.add(rebarCase("rebar_block", () -> TFMGBlocks.REBAR_BLOCK.getDefaultState(), () -> TFMGBlocks.REBAR_CONCRETE.block.get()));
        tests.add(rebarCase("rebar_floor", () -> TFMGBlocks.REBAR_FLOOR.getDefaultState(), () -> TFMGBlocks.REBAR_CONCRETE_FLOOR.get()));
        tests.add(rebarCase("rebar_wall", () -> TFMGBlocks.REBAR_WALL.getDefaultState(), () -> TFMGBlocks.REBAR_CONCRETE.wall.get()));
        tests.add(rebarCase("rebar_stairs", () -> TFMGBlocks.REBAR_STAIRS.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST),
                () -> TFMGBlocks.REBAR_CONCRETE.stairs.get()));
        tests.add(rebarCase("rebar_pillar", () -> TFMGBlocks.REBAR_PILLAR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST),
                () -> TFMGBlocks.REBAR_CONCRETE_PILLAR.get()));
        return tests;
    }

    private static TestFunction rebarCase(String name, Supplier<BlockState> rebar, Supplier<Block> dried) {
        return TFMGGameTestUtil.test(BATCH, "rebar." + name, TFMGGameTestUtil.PLATFORM, 100, helper -> runRebar(helper, name, rebar.get(), dried.get()));
    }

    private static void runRebar(GameTestHelper helper, String name, BlockState rebar, Block dried) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, rebar);
        Player player = TFMGOilEngineTestKit.player(helper);
        Item concreteBucket = TFMGFluids.LIQUID_CONCRETE.getBucket().get();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        ItemStack left = TFMGOilEngineTestKit.use(helper, player, pos, new ItemStack(concreteBucket));
        helper.assertTrue(left.is(Items.BUCKET), name + ": pouring concrete left " + left);
        helper.assertTrue(level.getBlockState(abs).getValue(ConcreteloggedBlock.CONCRETELOGGED), name + " did not take the concrete");
        helper.assertTrue(level.getFluidState(abs).getType().isSame(TFMGFluids.LIQUID_CONCRETE.getSource()), name + " does not hold liquid concrete");
        ItemStack back = TFMGOilEngineTestKit.use(helper, player, pos, new ItemStack(Items.BUCKET));
        helper.assertTrue(back.is(concreteBucket) && !level.getBlockState(abs).getValue(ConcreteloggedBlock.CONCRETELOGGED),
                name + ": an empty bucket got " + back + " back");
        TFMGOilEngineTestKit.use(helper, player, pos, new ItemStack(concreteBucket));
        BlockState filled = level.getBlockState(abs);
        helper.assertTrue(filled.isRandomlyTicking(), name + " never ticks, so it never sets");
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < 200 && level.getBlockState(abs).getBlock() != dried; i++)
            level.getBlockState(abs).randomTick(level, abs, random);
        BlockState set = level.getBlockState(abs);
        helper.assertTrue(set.is(dried), name + " set into " + set + " instead of " + BuiltInRegistries.BLOCK.getKey(dried));
        for (var property : List.of(BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING))
            if (filled.hasProperty(property) && set.hasProperty(property))
                helper.assertTrue(filled.getValue(property) == set.getValue(property), name + " turned from " + filled.getValue(property)
                        + " to " + set.getValue(property) + " as it set");
        helper.succeed();
    }

    /**
     * No TFMG block starts out holding a fluid. Commands, structures and
     * everything else that places a block without a player use its default
     * state; a default that is waterlogged or full of liquid concrete leaves
     * that fluid behind when the block breaks, or refuses the bucket a
     * player brings to fill it.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 20)
    public static void noBlockStartsFullOfFluid(GameTestHelper helper) {
        List<String> wet = new ArrayList<>();
        for (Block block : TFMGGameTestUtil.tfmgBlocks()) {
            if (block instanceof LiquidBlock)
                continue;
            BlockState state = block.defaultBlockState();
            String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
            if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED))
                wet.add(name + " is waterlogged");
            else if (state.hasProperty(ConcreteloggedBlock.CONCRETELOGGED) && state.getValue(ConcreteloggedBlock.CONCRETELOGGED))
                wet.add(name + " is full of concrete");
            else if (!state.getFluidState().isEmpty())
                wet.add(name + " holds " + state.getFluidState());
        }
        helper.assertTrue(wet.isEmpty(), "default states holding a fluid: " + wet);
        helper.succeed();
    }

    /** Every metal ladder and scaffolding can be climbed. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 100)
    public static void laddersAndScaffoldingClimb(GameTestHelper helper) {
        BlockPos wall = new BlockPos(2, 1, 3);
        BlockPos spot = new BlockPos(2, 1, 2);
        helper.setBlock(wall, Blocks.STONE);
        Player player = TFMGOilEngineTestKit.player(helper);
        List<String> problems = new ArrayList<>();
        List<Block> climbables = new ArrayList<>();
        for (Block block : TFMGGameTestUtil.tfmgBlocks())
            if (block instanceof LadderBlock || block instanceof net.minecraft.world.level.block.ScaffoldingBlock)
                climbables.add(block);
        helper.assertTrue(climbables.size() >= 8, "only " + climbables.size() + " ladders and scaffoldings found");
        for (Block block : climbables) {
            BlockState state = block.defaultBlockState();
            if (state.hasProperty(LadderBlock.FACING))
                state = state.setValue(LadderBlock.FACING, Direction.NORTH);
            helper.setBlock(spot, state);
            BlockPos abs = helper.absolutePos(spot);
            player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
            if (!helper.getLevel().getBlockState(abs).is(block))
                problems.add(BuiltInRegistries.BLOCK.getKey(block).getPath() + " did not stay in place");
            else if (!player.onClimbable() || !helper.getLevel().getBlockState(abs).is(BlockTags.CLIMBABLE))
                problems.add(BuiltInRegistries.BLOCK.getKey(block).getPath() + " cannot be climbed");
        }
        helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
        helper.succeed();
    }

    /**
     * Trusses, frames, rebar and lithium blocks: a water bucket waterlogs the
     * waterloggable ones, and a sneaking player's wrench picks each one up
     * into the inventory.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 100)
    public static void wrenchPicksUpDecoration(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        Player player = TFMGOilEngineTestKit.player(helper);
        player.setShiftKeyDown(true);
        List<Block> blocks = new ArrayList<>();
        TFMGBlocks.TRUSSES.forEach(e -> blocks.add(e.get()));
        TFMGBlocks.FRAMES.forEach(e -> blocks.add(e.get()));
        blocks.add(TFMGBlocks.LITHIUM_BLOCK.get());
        blocks.add(TFMGBlocks.REBAR_BLOCK.get());
        List<String> problems = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        for (Block block : blocks) {
            String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
            helper.setBlock(pos, block.defaultBlockState());
            if (block.defaultBlockState().hasProperty(BlockStateProperties.WATERLOGGED)) {
                ((BucketItem) Items.WATER_BUCKET).emptyContents(player, level, abs, null, new ItemStack(Items.WATER_BUCKET));
                if (!level.getBlockState(abs).getValue(BlockStateProperties.WATERLOGGED))
                    problems.add(name + " did not take water");
            }
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
            TFMGPowerKit.click(helper, player, pos);
            if (level.getBlockState(abs).is(block))
                problems.add(name + " was not picked up by the wrench");
            else if (player.getInventory().countItem(block.asItem()) != 1)
                problems.add(name + ": the wrench gave " + player.getInventory().countItem(block.asItem()) + " back");
            helper.setBlock(pos, Blocks.AIR);
        }
        helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
        helper.succeed();
    }

    // =========================================================== fluid tanks

    /**
     * Steel, aluminium and cast iron tanks of every width (1 to 3) and two
     * blocks high merge into one tank holding every block's capacity, every
     * block reads the same fluid, and breaking a corner keeps every millibucket
     * that still fits in the blocks left.
     */
    @GameTestGenerator
    public static List<TestFunction> tankSizes() {
        List<TestFunction> tests = new ArrayList<>();
        for (BlockEntry<?> tank : List.of(TFMGBlocks.STEEL_FLUID_TANK, TFMGBlocks.ALUMINUM_FLUID_TANK, TFMGBlocks.CAST_IRON_FLUID_TANK))
            for (int width = 1; width <= 3; width++) {
                int w = width;
                tests.add(TFMGGameTestUtil.test(BATCH, "tank_size." + tank.getId().getPath() + "." + w, TFMGGameTestUtil.PLATFORM_LARGE, 300,
                        helper -> runTankSize(helper, tank.get(), w)));
            }
        return tests;
    }

    private static void runTankSize(GameTestHelper helper, Block block, int width) {
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath() + " " + width + "x" + width;
        BlockPos origin = new BlockPos(3, 1, 3);
        int height = 2;
        List<BlockPos> parts = new ArrayList<>();
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                for (int z = 0; z < width; z++) {
                    BlockPos p = origin.offset(x, y, z);
                    helper.setBlock(p, block);
                    parts.add(p);
                }
        int perBlock = AllConfigs.server().fluids.fluidTankCapacity.get() * 1000;
        int blocks = parts.size();
        Fluid diesel = TFMGOilEngineTestKit.fluid("tfmg:diesel");
        BlockPos corner = origin.offset(width - 1, height - 1, width - 1);
        int keep = (blocks - 1) * perBlock - 500;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    FluidTankBlockEntity tank = (FluidTankBlockEntity) TFMGOilEngineTestKit.be(helper, origin);
                    FluidTankBlockEntity controller = tank.getControllerBE();
                    helper.assertTrue(controller != null && controller.getWidth() == width && controller.getHeight() == height,
                            name + " did not merge: " + (controller == null ? "no controller" : controller.getWidth() + "x" + controller.getHeight()));
                })
                .thenExecute(() -> {
                    IFluidHandler handler = TFMGOilEngineTestKit.fluids(helper, origin);
                    helper.assertTrue(handler.getTankCapacity(0) == blocks * perBlock, name + " holds " + handler.getTankCapacity(0)
                            + " mB instead of " + blocks * perBlock);
                    int filled = TFMGOilEngineTestKit.fill(helper, origin, diesel, blocks * perBlock * 2);
                    helper.assertTrue(filled == blocks * perBlock, name + " took " + filled + " mB");
                    for (BlockPos p : parts)
                        helper.assertTrue(TFMGOilEngineTestKit.amount(helper, p, diesel) == filled, name + ": the block at " + p + " reads "
                                + TFMGOilEngineTestKit.amount(helper, p, diesel) + " mB");
                    handler.drain(filled - keep, IFluidHandler.FluidAction.EXECUTE);
                    breakWithDrops(helper, corner);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    List<FluidTankBlockEntity> controllers = new ArrayList<>();
                    for (BlockPos p : parts) {
                        if (p.equals(corner))
                            continue;
                        if (TFMGOilEngineTestKit.be(helper, p) instanceof FluidTankBlockEntity tank && tank.getControllerBE() != null
                                && !controllers.contains(tank.getControllerBE()))
                            controllers.add(tank.getControllerBE());
                    }
                    int kept = 0;
                    for (FluidTankBlockEntity controller : controllers)
                        kept += controller.getTankInventory().getFluidAmount();
                    helper.assertTrue(kept == keep, name + ": breaking a corner left " + kept + " mB of " + keep + " in "
                            + controllers.size() + " tanks");
                })
                .thenSucceed();
    }

    // ============================================================= kinetics

    /** A creative motor turning {@code first} along X from the west. */
    static void driveFromWest(GameTestHelper helper, BlockPos first, int rpm) {
        motor(helper, first.west(), Direction.EAST, rpm);
    }

    /**
     * Every TFMG gear, encased gear, encased shaft, gearbox and flywheel
     * carries a creative motor's rotation on to a Create shaft: plain
     * cogwheels mesh with a cogwheel beside them, large cogwheels double the
     * speed of a small cogwheel on their rim, and everything else passes the
     * speed straight through.
     */
    @GameTestGenerator
    public static List<TestFunction> kineticRelays() {
        List<TestFunction> tests = new ArrayList<>();
        for (BlockEntry<?> cog : List.of(TFMGBlocks.STEEL_COGWHEEL, TFMGBlocks.ALUMINUM_COGWHEEL))
            tests.add(TFMGGameTestUtil.test(BATCH, "kinetics." + cog.getId().getPath(), TFMGGameTestUtil.PLATFORM, 200, helper -> {
                BlockPos a = new BlockPos(2, 1, 2);
                BlockPos b = new BlockPos(2, 1, 3);
                BlockPos out = new BlockPos(3, 1, 3);
                helper.setBlock(a, cog.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                helper.setBlock(b, cog.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                TFMGOilEngineTestKit.shaft(helper, out, Direction.Axis.X);
                driveFromWest(helper, a, 32);
                helper.succeedWhen(() -> {
                    helper.assertTrue(Math.abs(TFMGOilEngineTestKit.speed(helper, a)) == 32, "the driven cogwheel turns at " + TFMGOilEngineTestKit.speed(helper, a));
                    helper.assertTrue(TFMGOilEngineTestKit.speed(helper, b) == -TFMGOilEngineTestKit.speed(helper, a), "the meshing cogwheel turns at "
                            + TFMGOilEngineTestKit.speed(helper, b));
                    helper.assertTrue(TFMGOilEngineTestKit.speed(helper, out) == TFMGOilEngineTestKit.speed(helper, b), "its shaft turns at "
                            + TFMGOilEngineTestKit.speed(helper, out));
                });
            }));
        for (BlockEntry<?> large : List.of(TFMGBlocks.LARGE_STEEL_COGWHEEL, TFMGBlocks.LARGE_ALUMINUM_COGWHEEL))
            tests.add(TFMGGameTestUtil.test(BATCH, "kinetics." + large.getId().getPath(), TFMGGameTestUtil.PLATFORM, 200, helper -> {
                BlockPos big = new BlockPos(2, 1, 2);
                BlockPos small = new BlockPos(2, 2, 3);
                helper.setBlock(big, large.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                helper.setBlock(small, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                driveFromWest(helper, big, 32);
                helper.succeedWhen(() -> {
                    helper.assertTrue(Math.abs(TFMGOilEngineTestKit.speed(helper, big)) == 32, "the large cogwheel turns at "
                            + TFMGOilEngineTestKit.speed(helper, big));
                    helper.assertTrue(TFMGOilEngineTestKit.speed(helper, small) == -2 * TFMGOilEngineTestKit.speed(helper, big),
                            "the small cogwheel on its rim turns at " + TFMGOilEngineTestKit.speed(helper, small));
                });
            }));
        List<BlockEntry<?>> straight = new ArrayList<>(List.of(
                TFMGEncasedBlocks.STEEL_ENCASED_SHAFT, TFMGEncasedBlocks.HEAVY_CASING_ENCASED_SHAFT,
                TFMGEncasedBlocks.STEEL_ENCASED_STEEL_COGWHEEL, TFMGEncasedBlocks.HEAVY_CASING_ENCASED_STEEL_COGWHEEL,
                TFMGEncasedBlocks.STEEL_ENCASED_LARGE_STEEL_COGWHEEL, TFMGEncasedBlocks.HEAVY_CASING_ENCASED_LARGE_STEEL_COGWHEEL,
                TFMGEncasedBlocks.STEEL_ENCASED_ALUMINUM_COGWHEEL, TFMGEncasedBlocks.HEAVY_CASING_ENCASED_ALUMINUM_COGWHEEL,
                TFMGEncasedBlocks.STEEL_ENCASED_LARGE_ALUMINUM_COGWHEEL, TFMGEncasedBlocks.HEAVY_CASING_ENCASED_LARGE_ALUMINUM_COGWHEEL,
                TFMGBlocks.STEEL_FLYWHEEL, TFMGBlocks.LEAD_FLYWHEEL, TFMGBlocks.CAST_IRON_FLYWHEEL, TFMGBlocks.ALUMINUM_FLYWHEEL,
                TFMGBlocks.NICKEL_FLYWHEEL));
        for (BlockEntry<?> relay : straight)
            tests.add(TFMGGameTestUtil.test(BATCH, "kinetics." + relay.getId().getPath(), TFMGGameTestUtil.PLATFORM, 200, helper -> {
                BlockPos mid = new BlockPos(2, 1, 2);
                BlockPos out = mid.east();
                BlockState state = relay.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
                if (state.hasProperty(EncasedCogwheelBlock.TOP_SHAFT))
                    state = state.setValue(EncasedCogwheelBlock.TOP_SHAFT, true).setValue(EncasedCogwheelBlock.BOTTOM_SHAFT, true);
                helper.setBlock(mid, state);
                TFMGOilEngineTestKit.shaft(helper, out, Direction.Axis.X);
                driveFromWest(helper, mid, 32);
                helper.succeedWhen(() -> {
                    helper.assertTrue(Math.abs(TFMGOilEngineTestKit.speed(helper, mid)) == 32, relay.getId().getPath() + " turns at "
                            + TFMGOilEngineTestKit.speed(helper, mid));
                    helper.assertTrue(TFMGOilEngineTestKit.speed(helper, out) == TFMGOilEngineTestKit.speed(helper, mid), "the shaft after "
                            + relay.getId().getPath() + " turns at " + TFMGOilEngineTestKit.speed(helper, out));
                });
            }));
        tests.add(TFMGGameTestUtil.test(BATCH, "kinetics.steel_gearbox", TFMGGameTestUtil.PLATFORM, 200, helper -> {
            BlockPos box = new BlockPos(2, 1, 2);
            helper.setBlock(box, TFMGBlocks.STEEL_GEARBOX.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
            TFMGOilEngineTestKit.shaft(helper, box.east(), Direction.Axis.X);
            TFMGOilEngineTestKit.shaft(helper, box.north(), Direction.Axis.Z);
            TFMGOilEngineTestKit.shaft(helper, box.south(), Direction.Axis.Z);
            driveFromWest(helper, box, 32);
            helper.succeedWhen(() -> {
                for (BlockPos out : List.of(box.east(), box.north(), box.south()))
                    helper.assertTrue(Math.abs(TFMGOilEngineTestKit.speed(helper, out)) == 32, "the steel gearbox turns its "
                            + out + " shaft at " + TFMGOilEngineTestKit.speed(helper, out));
            });
        }));
        return tests;
    }

    /**
     * A player encases a turning steel cogwheel with steel casing, an
     * aluminium cogwheel with heavy machinery casing and a shaft with steel
     * casing: each keeps turning what it turned.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 200)
    public static void casingsEncaseTurningParts(GameTestHelper helper) {
        BlockPos[] parts = {new BlockPos(2, 1, 0), new BlockPos(2, 1, 2), new BlockPos(2, 1, 4)};
        Block[] plain = {TFMGBlocks.STEEL_COGWHEEL.get(), TFMGBlocks.ALUMINUM_COGWHEEL.get(), AllBlocks.SHAFT.get()};
        Block[] casing = {TFMGBlocks.STEEL_CASING.get(), TFMGBlocks.HEAVY_MACHINERY_CASING.get(), TFMGBlocks.STEEL_CASING.get()};
        Block[] encased = {TFMGEncasedBlocks.STEEL_ENCASED_STEEL_COGWHEEL.get(), TFMGEncasedBlocks.HEAVY_CASING_ENCASED_ALUMINUM_COGWHEEL.get(),
                TFMGEncasedBlocks.STEEL_ENCASED_SHAFT.get()};
        for (int i = 0; i < parts.length; i++) {
            helper.setBlock(parts[i], plain[i].defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
            TFMGOilEngineTestKit.shaft(helper, parts[i].east(), Direction.Axis.X);
            driveFromWest(helper, parts[i], 32);
        }
        Player player = TFMGOilEngineTestKit.player(helper);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (BlockPos part : parts)
                        helper.assertTrue(Math.abs(TFMGOilEngineTestKit.speed(helper, part.east())) == 32, "the shaft after " + part + " is still");
                })
                .thenExecute(() -> {
                    for (int i = 0; i < parts.length; i++)
                        TFMGOilEngineTestKit.use(helper, player, parts[i], new ItemStack(casing[i]));
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    for (int i = 0; i < parts.length; i++) {
                        helper.assertBlockPresent(encased[i], parts[i]);
                        helper.assertTrue(Math.abs(TFMGOilEngineTestKit.speed(helper, parts[i].east())) == 32,
                                "the shaft after the encased " + BuiltInRegistries.BLOCK.getKey(encased[i]).getPath() + " stopped");
                    }
                })
                .thenSucceed();
    }

    /**
     * Brick, metal and concrete smokestacks all take carbon dioxide and vent
     * it out of their top.
     */
    @GameTestGenerator
    public static List<TestFunction> smokestacks() {
        List<TestFunction> tests = new ArrayList<>();
        for (BlockEntry<?> stack : List.of(TFMGBlocks.BRICK_SMOKESTACK, TFMGBlocks.METAL_SMOKESTACK, TFMGBlocks.CONCRETE_SMOKESTACK))
            tests.add(TFMGGameTestUtil.test(BATCH, "smokestack." + stack.getId().getPath(), TFMGGameTestUtil.PLATFORM, 600, helper -> {
                BlockPos bottom = new BlockPos(2, 1, 2);
                for (int y = 0; y < 2; y++)
                    helper.setBlock(bottom.above(y), stack.get());
                Fluid co2 = TFMGOilEngineTestKit.fluid("tfmg:carbon_dioxide");
                helper.startSequence()
                        .thenExecuteAfter(5, () -> helper.assertTrue(TFMGOilEngineTestKit.fill(helper, bottom, co2, 2000) == 2000,
                                stack.getId().getPath() + " refused carbon dioxide"))
                        .thenWaitUntil(() -> {
                            int left = TFMGOilEngineTestKit.amount(helper, bottom, co2) + TFMGOilEngineTestKit.amount(helper, bottom.above(), co2);
                            helper.assertTrue(left == 0, stack.getId().getPath() + " still holds " + left + " mB");
                        })
                        .thenSucceed();
            }));
        return tests;
    }
}
