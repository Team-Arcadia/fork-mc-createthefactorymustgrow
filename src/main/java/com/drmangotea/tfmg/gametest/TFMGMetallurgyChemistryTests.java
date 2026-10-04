package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.electricity.utilities.polarizer.PolarizerBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.blast_furnace.BlastFurnaceOutputBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.casting_basin.CastingBasinBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.coke_oven.CokeOvenBlock;
import com.drmangotea.tfmg.content.machinery.metallurgy.coke_oven.CokeOvenBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.machine_input.MachineInputBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.winding_machine.WindingMachineBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.compressor.CompressorBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.electrode_holder.ElectrodeHolderBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.industrial_mixer.IndustrialMixerBlockEntity;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.google.gson.JsonObject;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.drmangotea.tfmg.gametest.TFMGFactoryKit.*;

/**
 * Metallurgy and chemistry, running for real: every machine is built in the
 * world, turned by Create motors or fed by TFMG generators, supplied through
 * hoppers, pumps and tanks, and its product is collected and counted where a
 * player's factory would collect it. While a machine works the Factory
 * Inspector must find nothing wrong with it; before it is supplied it must
 * name what is missing.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGMetallurgyChemistryTests {

    static final String BATCH = "tfmg_metallurgy";
    static final String SMALL = "gametest/platform";
    static final String LARGE = "gametest/platform_large";
    static final String HUGE = "gametest/platform_huge";
    static final int PUMP_RPM = 128;

    // ------------------------------------------------------------ fluids

    static Fluid steel() {
        return (Fluid) TFMGFluids.MOLTEN_STEEL.getSource();
    }

    static Fluid slag() {
        return (Fluid) TFMGFluids.MOLTEN_SLAG.getSource();
    }

    static Fluid creosote() {
        return (Fluid) TFMGFluids.CREOSOTE.getSource();
    }

    static Fluid co2() {
        return (Fluid) TFMGFluids.CARBON_DIOXIDE.getSource();
    }

    static Fluid hotAir() {
        return (Fluid) TFMGFluids.HOT_AIR.getSource();
    }

    static BlockState cokeOven(Direction facing) {
        return TFMGBlocks.COKE_OVEN.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    // ========================================================= coke oven

    /**
     * A single coke oven fed by a hopper turns two coal into two coal coke,
     * collected by a hopper into a chest, while a pump keeps its carbon
     * dioxide tank empty. Each coal leaves exactly 1200 mB of creosote.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 3200)
    public static void cokeOvenCokesCoalIntoChest(GameTestHelper helper) {
        BlockPos oven = new BlockPos(5, 1, 5);
        BlockPos feedChest = new BlockPos(5, 2, 4);
        BlockPos outChest = new BlockPos(6, 1, 6);
        helper.setBlock(oven, cokeOven(Direction.SOUTH));
        hopper(helper, new BlockPos(5, 1, 4), Direction.SOUTH);
        chest(helper, feedChest, new ItemStack(Items.COAL, 2));
        hopper(helper, new BlockPos(5, 1, 6), Direction.EAST);
        chest(helper, outChest);
        // Carbon dioxide leaves through the top into a creative tank that swallows it.
        pump(helper, new BlockPos(5, 2, 5), Direction.UP, Direction.EAST, PUMP_RPM);
        creativeTank(helper, new BlockPos(5, 3, 5), co2());

        Inspection idle = inspect(helper, oven);
        TFMGGameTestUtil.check(helper, idle.hasProblem("coke_oven.no_input"), "an empty oven does not report its missing coal: " + idle.text());

        RunningInspection running = new RunningInspection();
        int[] lastCreosote = {0};
        helper.onEachTick(() -> {
            CokeOvenBlockEntity be = helper.getBlockEntity(oven);
            int creosote = be.primaryTank.getFluidAmount();
            if (creosote > lastCreosote[0] && helper.getTick() % 20 == 0)
                running.sample(helper, oven);
            lastCreosote[0] = creosote;
        });
        helper.succeedWhen(() -> {
            CokeOvenBlockEntity be = helper.getBlockEntity(oven);
            helper.assertTrue(count(helper, outChest, TFMGItems.COAL_COKE.get()) == 2,
                    "coal coke in the chest: " + count(helper, outChest, TFMGItems.COAL_COKE.get()));
            helper.assertTrue(be.inventory.isEmpty(), "coal left in the oven");
            TFMGGameTestUtil.check(helper, count(helper, feedChest, Items.COAL) == 0, "coal left in the feed chest");
            TFMGGameTestUtil.check(helper, be.primaryTank.getFluidAmount() == 2400,
                    "two coal should leave 2400 mB of creosote, the oven holds " + be.primaryTank.getFluidAmount());
            TFMGGameTestUtil.check(helper, be.secondaryTank.getFluidAmount() < be.secondaryTank.getCapacity(),
                    "the pump did not keep the carbon dioxide tank from filling");
            TFMGGameTestUtil.check(helper, helper.getEntities(EntityType.ITEM).isEmpty(), "coke was left lying around");
            running.assertClean(helper, "coke oven");
        });
    }

    /**
     * A 3x3 oven wall forms one oven: coal pushed into its top back block
     * reaches the controller, cooks 1.5 times faster than in a single oven
     * and drops in front of the controller.
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 1800)
    public static void cokeOvenMultiblockCooksFaster(GameTestHelper helper) {
        BlockPos controller = new BlockPos(5, 1, 5);
        BlockPos member = new BlockPos(5, 3, 3);
        BlockPos outChest = new BlockPos(6, 1, 6);
        for (int y = 1; y <= 3; y++)
            for (int z = 3; z <= 5; z++)
                helper.setBlock(new BlockPos(5, y, z), cokeOven(Direction.SOUTH));
        hopper(helper, new BlockPos(5, 4, 3), Direction.DOWN);
        chest(helper, new BlockPos(5, 5, 3));
        hopper(helper, new BlockPos(5, 1, 6), Direction.EAST);
        chest(helper, outChest);
        pump(helper, new BlockPos(5, 4, 5), Direction.UP, Direction.EAST, PUMP_RPM);
        creativeTank(helper, new BlockPos(5, 5, 5), co2());

        long[] started = {-1};
        long[] finished = {-1};
        RunningInspection running = new RunningInspection();
        helper.runAfterDelay(20, () -> {
            CokeOvenBlockEntity be = helper.getBlockEntity(controller);
            TFMGGameTestUtil.check(helper, be.size == 3, "the 3x3 wall formed an oven of size " + be.size);
            TFMGGameTestUtil.check(helper, helper.getLevel().getBlockState(helper.absolutePos(controller))
                    .getValue(CokeOvenBlock.CONTROLLER_TYPE) == CokeOvenBlock.ControllerType.BOTTOM_ON, "the controller is not marked");
            Inspection formed = inspect(helper, member);
            TFMGGameTestUtil.check(helper, formed.has("coke_oven.member") && formed.has("coke_oven.formed"),
                    "a member block does not report the formed oven: " + formed.text());
            if (helper.getBlockEntity(new BlockPos(5, 5, 3)) instanceof Container chest)
                chest.setItem(0, new ItemStack(Items.COAL, 1));
        });
        helper.onEachTick(() -> {
            CokeOvenBlockEntity be = helper.getBlockEntity(controller);
            if (started[0] < 0 && be.primaryTank.getFluidAmount() > 0)
                started[0] = helper.getTick();
            if (started[0] >= 0 && finished[0] < 0 && helper.getTick() % 20 == 0)
                running.sample(helper, member, controller);
            if (finished[0] < 0 && count(helper, outChest, TFMGItems.COAL_COKE.get()) > 0)
                finished[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(finished[0] > 0, "no coke yet");
            long took = finished[0] - started[0];
            // 1200 ticks in a single oven, 800 in a size 3 oven, plus the hopper.
            TFMGGameTestUtil.check(helper, took >= 780 && took <= 900, "the size 3 oven took " + took + " ticks for one coal");
            running.assertClean(helper, "coke oven (3x3)");
        });
    }

    /**
     * Nobody draining the carbon dioxide: the oven pauses with the coal still
     * inside, the inspector names the full tank, and the oven finishes the
     * coal once a pump is added.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 2600)
    public static void cokeOvenPausesOnFullTankAndResumes(GameTestHelper helper) {
        BlockPos oven = new BlockPos(2, 1, 2);
        BlockPos outChest = new BlockPos(3, 1, 3);
        helper.setBlock(oven, cokeOven(Direction.SOUTH));
        hopper(helper, new BlockPos(2, 1, 1), Direction.SOUTH);
        chest(helper, new BlockPos(2, 2, 1), new ItemStack(Items.COAL, 1));
        hopper(helper, new BlockPos(2, 1, 3), Direction.EAST);
        chest(helper, outChest);

        boolean[] pumped = {false};
        int[] stalledFor = {0};
        int[] lastCreosote = {-1};
        helper.onEachTick(() -> {
            if (pumped[0])
                return;
            CokeOvenBlockEntity be = helper.getBlockEntity(oven);
            int creosote = be.primaryTank.getFluidAmount();
            stalledFor[0] = creosote > 0 && creosote == lastCreosote[0] ? stalledFor[0] + 1 : 0;
            lastCreosote[0] = creosote;
            if (stalledFor[0] < 40)
                return;
            TFMGGameTestUtil.check(helper, be.secondaryTank.getSpace() < 30, "the oven stalled with room left in its tanks");
            TFMGGameTestUtil.check(helper, !be.inventory.isEmpty(), "the stalled oven lost its coal");
            TFMGGameTestUtil.check(helper, count(helper, outChest, TFMGItems.COAL_COKE.get()) == 0, "coke came out of a stalled oven");
            Inspection full = inspect(helper, oven);
            TFMGGameTestUtil.check(helper, full.hasProblem("coke_oven.co2_full"), "the inspector does not name the full tank: " + full.text());
            pump(helper, new BlockPos(2, 2, 2), Direction.UP, Direction.EAST, PUMP_RPM);
            creativeTank(helper, new BlockPos(2, 3, 2), co2());
            pumped[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(pumped[0], "the oven never stalled");
            helper.assertTrue(count(helper, outChest, TFMGItems.COAL_COKE.get()) == 1, "no coke after the tank was drained");
            CokeOvenBlockEntity be = helper.getBlockEntity(oven);
            TFMGGameTestUtil.check(helper, be.primaryTank.getFluidAmount() == 1200,
                    "one coal should leave 1200 mB of creosote, found " + be.primaryTank.getFluidAmount());
        });
    }

    // ===================================================== blast furnace

    /** Builds a handbook blueprint at {@code origin}; returns its blocks by position relative to the test. */
    static Map<BlockPos, BlockState> buildBlueprint(GameTestHelper helper, String id, BlockPos origin) {
        JsonObject blueprint = TFMGStructureTests.readBlueprints().get(id);
        TFMGGameTestUtil.check(helper, blueprint != null, "blueprint " + id + " missing");
        Map<BlockPos, BlockState> blocks = new java.util.LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : TFMGStructureTests.blocksOf(blueprint).entrySet()) {
            helper.setBlock(origin.offset(e.getKey()), e.getValue());
            blocks.put(origin.offset(e.getKey()), e.getValue());
        }
        return blocks;
    }

    static void fill(GameTestHelper helper, BlockPos chestPos, ItemStack... stacks) {
        if (helper.getBlockEntity(chestPos) instanceof Container chest)
            for (int i = 0; i < stacks.length; i++)
                chest.setItem(i, stacks[i].copy());
    }

    /**
     * The handbook's 3-layer furnace run like a player's: ore, limesand and
     * coke dust go in through the gas hatch on top, a pump fills the tuyere
     * with hot air, another pumps the molten steel into a casting basin and a
     * hopper takes the ingots to a chest. The slag follows the steel down the
     * same pipe and is cast into slag blocks.
     */
    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 2400)
    public static void blastFurnaceCastsSteelIngots(GameTestHelper helper) {
        BlockPos output = new BlockPos(7, 2, 8);
        BlockPos tuyere = new BlockPos(6, 3, 7);
        BlockPos feedChest = new BlockPos(7, 7, 7);
        BlockPos hotAirTank = new BlockPos(4, 3, 7);
        BlockPos basin = new BlockPos(7, 2, 10);
        BlockPos ingots = new BlockPos(8, 1, 10);
        buildBlueprint(helper, "blast_furnace_1", new BlockPos(6, 2, 6));
        hopper(helper, new BlockPos(7, 6, 7), Direction.DOWN);
        chest(helper, feedChest);
        pump(helper, new BlockPos(5, 3, 7), Direction.EAST, Direction.NORTH, PUMP_RPM);
        creativeTank(helper, hotAirTank, null);
        pump(helper, new BlockPos(7, 2, 9), Direction.SOUTH, Direction.DOWN, PUMP_RPM);
        helper.setBlock(basin, TFMGBlocks.CASTING_BASIN.get().defaultBlockState());
        hopper(helper, new BlockPos(7, 1, 10), Direction.EAST);
        chest(helper, ingots);

        helper.runAfterDelay(25, () -> {
            Inspection idle = inspect(helper, output);
            for (String missing : List.of("blast_furnace.no_ore", "blast_furnace.no_fuel", "blast_furnace.no_hot_air"))
                TFMGGameTestUtil.check(helper, idle.hasProblem(missing), "an idle furnace does not report " + missing + ": " + idle.text());
            TFMGGameTestUtil.check(helper, idle.has("blast_furnace.not_reinforced"), "fireproof bricks reported as reinforced");
            fill(helper, feedChest, new ItemStack(AllItems.CRUSHED_IRON.get(), 1), new ItemStack(TFMGItems.LIMESAND.get(), 1),
                    new ItemStack(TFMGItems.COAL_COKE_DUST.get(), 2));
            setCreativeFluid(helper, hotAirTank, hotAir());
        });

        RunningInspection running = new RunningInspection();
        int[] maxTimer = {0};
        helper.onEachTick(() -> {
            BlastFurnaceOutputBlockEntity be = helper.getBlockEntity(output);
            maxTimer[0] = Math.max(maxTimer[0], be.timer);
            if (be.timer > 0 && be.fuel > 0 && !be.inputInventory.isEmpty() && helper.getTick() % 10 == 0)
                running.sample(helper, output, tuyere);
        });
        helper.succeedWhen(() -> {
            BlastFurnaceOutputBlockEntity be = helper.getBlockEntity(output);
            helper.assertTrue(count(helper, ingots, TFMGItems.STEEL_INGOT.get()) == 1
                            && count(helper, ingots, TFMGBlocks.SLAG_BLOCK.get().asItem()) == 7,
                    "steel ingots in the chest: " + count(helper, ingots, TFMGItems.STEEL_INGOT.get())
                            + ", slag blocks: " + count(helper, ingots, TFMGBlocks.SLAG_BLOCK.get().asItem())
                            + " [" + furnace(be) + ", basin " + describe(fluids(helper, basin, null)) + "]");
            // 144 mB of slag casts 7 slag blocks; the 4 mB left over stay in the basin.
            TFMGGameTestUtil.check(helper, be.primaryTank.isEmpty() && be.secondaryTank.isEmpty(), "metal left in the furnace: " + furnace(be));
            TFMGGameTestUtil.check(helper, amount(helper, basin, slag()) == 4, "basin: " + describe(fluids(helper, basin, null)));
            TFMGGameTestUtil.check(helper, be.getCachedSize() == 3, "furnace height " + be.getCachedSize());
            TFMGGameTestUtil.check(helper, maxTimer[0] > 0 && maxTimer[0] <= 340, "a 3 high furnace took " + maxTimer[0] + " ticks per ore");
            running.assertClean(helper, "blast furnace");
        });
    }

    /**
     * A 5 layer furnace of reinforced bricks, loaded through a hopper into its
     * output block, is recognised as reinforced and smelts each ore in half
     * the time of a regular furnace of the same height (150 ticks).
     */
    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 1600)
    public static void reinforcedTallBlastFurnaceIsFaster(GameTestHelper helper) {
        BlockPos output = new BlockPos(7, 2, 8);
        BlockPos tuyere = new BlockPos(6, 3, 7);
        for (int i = 0; i < 5; i++) {
            int y = 2 + i;
            for (int x : new int[]{6, 8})
                for (int z : new int[]{6, 8})
                    helper.setBlock(new BlockPos(x, y, z), TFMGBlocks.BLAST_FURNACE_REINFORCEMENT.get().defaultBlockState());
            for (BlockPos wall : new BlockPos[]{new BlockPos(7, y, 6), new BlockPos(6, y, 7), new BlockPos(8, y, 7), new BlockPos(7, y, 8)})
                helper.setBlock(wall, TFMGBlocks.REINFORCED_FIREPROOF_BRICKS.get().defaultBlockState());
        }
        helper.setBlock(new BlockPos(7, 2, 7), TFMGBlocks.REINFORCED_FIREPROOF_BRICKS.get().defaultBlockState());
        helper.setBlock(output, TFMGBlocks.BLAST_FURNACE_OUTPUT.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        helper.setBlock(tuyere, TFMGBlocks.BLAST_FURNACE_HATCH.get().defaultBlockState());
        helper.setBlock(new BlockPos(7, 7, 7), TFMGBlocks.BLAST_FURNACE_HATCH.get().defaultBlockState());
        hopper(helper, new BlockPos(7, 2, 9), Direction.NORTH);
        chest(helper, new BlockPos(7, 3, 9), new ItemStack(AllItems.CRUSHED_IRON.get(), 2), new ItemStack(TFMGItems.LIMESAND.get(), 2),
                new ItemStack(TFMGItems.COAL_COKE_DUST.get(), 2));
        pump(helper, new BlockPos(5, 3, 7), Direction.EAST, Direction.NORTH, PUMP_RPM);
        creativeTank(helper, new BlockPos(4, 3, 7), hotAir());

        RunningInspection running = new RunningInspection();
        int[] maxTimer = {0};
        helper.onEachTick(() -> {
            BlastFurnaceOutputBlockEntity be = helper.getBlockEntity(output);
            maxTimer[0] = Math.max(maxTimer[0], be.timer);
            if (be.timer > 0 && be.fuel > 0 && !be.inputInventory.isEmpty() && helper.getTick() % 10 == 0)
                running.sample(helper, output, tuyere);
        });
        helper.succeedWhen(() -> {
            BlastFurnaceOutputBlockEntity be = helper.getBlockEntity(output);
            helper.assertTrue(be.primaryTank.getFluidAmount() == 288, "molten steel: " + be.primaryTank.getFluidAmount() + " [" + furnace(be) + "]");
            TFMGGameTestUtil.check(helper, be.secondaryTank.getFluidAmount() == 288, "molten slag: " + be.secondaryTank.getFluidAmount());
            TFMGGameTestUtil.check(helper, be.getCachedSize() == 5, "furnace height " + be.getCachedSize());
            Inspection inspection = inspect(helper, output);
            TFMGGameTestUtil.check(helper, inspection.has("blast_furnace.reinforced"), "not seen as reinforced: " + inspection.text());
            TFMGGameTestUtil.check(helper, maxTimer[0] > 0 && maxTimer[0] <= 150, "a reinforced 5 high furnace took " + maxTimer[0] + " ticks per ore");
            TFMGGameTestUtil.check(helper, be.inputInventory.isEmpty() && be.fluxInventory.isEmpty(), "ore or flux left over");
            running.assertClean(helper, "reinforced blast furnace");
        });
    }

    // ===================================================== casting basin

    /**
     * Every casting recipe, each on its own line: a creative tank, a pump, a
     * casting basin and a hopper emptying it into a chest. Each line must
     * cast three items in a row.
     */
    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 1400)
    public static void castingBasinCastsEveryRecipe(GameTestHelper helper) {
        Fluid[] fluids = {steel(), slag(), (Fluid) TFMGFluids.LIQUID_SILICON.getSource(),
                (Fluid) TFMGFluids.MOLTEN_PLASTIC.getSource(), (Fluid) TFMGFluids.LIQUID_CONCRETE.getSource()};
        Item[] products = {TFMGItems.STEEL_INGOT.get(), TFMGBlocks.SLAG_BLOCK.get().asItem(), TFMGItems.SILICON_INGOT.get(),
                TFMGItems.PLASTIC_SHEET.get(), TFMGItems.CINDERBLOCK.get()};
        List<BlockPos> basins = new ArrayList<>();
        List<BlockPos> chests = new ArrayList<>();
        for (int i = 0; i < fluids.length; i++) {
            int x = 2 + 3 * i;
            BlockPos basin = new BlockPos(x, 2, 5);
            creativeTank(helper, new BlockPos(x, 2, 3), fluids[i]);
            pump(helper, new BlockPos(x, 2, 4), Direction.SOUTH, Direction.EAST, PUMP_RPM);
            helper.setBlock(basin, TFMGBlocks.CASTING_BASIN.get().defaultBlockState());
            hopper(helper, new BlockPos(x, 1, 5), Direction.EAST);
            chest(helper, new BlockPos(x + 1, 1, 5));
            basins.add(basin);
            chests.add(new BlockPos(x + 1, 1, 5));
            Inspection idle = inspect(helper, basin);
            TFMGGameTestUtil.check(helper, idle.hasProblem("casting_basin.empty"), "an empty basin does not say so: " + idle.text());
        }
        RunningInspection running = new RunningInspection();
        helper.onEachTick(() -> {
            if (helper.getTick() % 10 != 0)
                return;
            for (BlockPos basin : basins) {
                CastingBasinBlockEntity be = helper.getBlockEntity(basin);
                if (be.timer > 0 && be.inventory.isEmpty())
                    running.sample(helper, basin);
            }
        });
        helper.succeedWhen(() -> {
            for (int i = 0; i < fluids.length; i++) {
                int made = count(helper, chests.get(i), products[i]);
                helper.assertTrue(made >= 3, products[i] + ": " + made + " cast so far");
                Container chest = helper.getBlockEntity(chests.get(i));
                for (int slot = 0; slot < chest.getContainerSize(); slot++)
                    TFMGGameTestUtil.check(helper, chest.getItem(slot).isEmpty() || chest.getItem(slot).is(products[i]),
                            "line " + i + " cast " + chest.getItem(slot));
            }
            running.assertClean(helper, "casting basin");
        });
    }

    // ======================================================= chemical vat

    static Fluid f(com.tterrag.registrate.util.entry.FluidEntry<?> entry) {
        return (Fluid) entry.getSource();
    }

    /** One vat recipe set up the way a player would build it: vat, attachments, supplies and expected products. */
    static final class VatCase {
        final String name;
        final Supplier<Block> vat;
        int width = 2;
        final List<Fluid> fluids = new ArrayList<>();
        final List<ItemStack> items = new ArrayList<>();
        Item mixerTool;
        int electrodes;
        boolean graphite;
        boolean freezer;
        boolean compressor;
        boolean firebox;
        final Map<Fluid, Integer> expectFluids = new java.util.LinkedHashMap<>();
        final Map<Item, Integer> expectItems = new java.util.LinkedHashMap<>();

        VatCase(String name, Supplier<Block> vat) {
            this.name = name;
            this.vat = vat;
        }

        VatCase fluids(Fluid... in) {
            fluids.addAll(List.of(in));
            return this;
        }

        VatCase items(ItemStack... in) {
            items.addAll(List.of(in));
            return this;
        }

        VatCase mixer(Item tool) {
            mixerTool = tool;
            return this;
        }

        VatCase electrodes(int count, boolean graphite) {
            electrodes = count;
            this.graphite = graphite;
            return this;
        }

        VatCase freezer() {
            freezer = true;
            return this;
        }

        VatCase compressor() {
            compressor = true;
            return this;
        }

        VatCase heated() {
            firebox = true;
            return this;
        }

        VatCase width(int width) {
            this.width = width;
            return this;
        }

        VatCase makes(Fluid fluid, int amount) {
            expectFluids.put(fluid, amount);
            return this;
        }

        VatCase makes(Item item, int count) {
            expectItems.put(item, count);
            return this;
        }
    }

    static List<VatCase> vatCases() {
        Supplier<Block> castIron = TFMGBlocks.CAST_IRON_CHEMICAL_VAT::get;
        Supplier<Block> steelVat = TFMGBlocks.STEEL_CHEMICAL_VAT::get;
        Supplier<Block> fireproof = TFMGBlocks.FIREPROOF_CHEMICAL_VAT::get;
        Item blade = TFMGItems.MIXER_BLADE.get();
        Fluid water = net.minecraft.world.level.material.Fluids.WATER;
        List<VatCase> cases = new ArrayList<>();
        cases.add(new VatCase("aluminum", steelVat).items(new ItemStack(TFMGItems.BAUXITE_POWDER.get(), 8))
                .electrodes(2, false).heated().makes(TFMGItems.ALUMINUM_INGOT.get(), 2).makes(co2(), 1000));
        cases.add(new VatCase("arc_furnace_steel", fireproof).width(3)
                .items(new ItemStack(AllItems.CRUSHED_IRON.get(), 2), new ItemStack(TFMGItems.LIMESAND.get(), 2),
                        new ItemStack(TFMGItems.COAL_COKE_DUST.get(), 2))
                .electrodes(3, true).makes(steel(), 288).makes(slag(), 576));
        cases.add(new VatCase("compressed_lpg", steelVat).fluids(f(TFMGFluids.BUTANE), f(TFMGFluids.PROPANE)).compressor()
                .makes(f(TFMGFluids.LPG), 1000));
        cases.add(new VatCase("concrete", castIron).items(new ItemStack(Items.SAND), new ItemStack(Items.GRAVEL),
                new ItemStack(TFMGItems.LIMESAND.get())).fluids(water).mixer(blade).makes(f(TFMGFluids.LIQUID_CONCRETE), 32000));
        cases.add(new VatCase("cooling_fluid", castIron).fluids(water, f(TFMGFluids.LUBRICATION_OIL)).freezer()
                .makes(f(TFMGFluids.COOLING_FLUID), 500));
        cases.add(new VatCase("etched_circuit_board", steelVat).items(new ItemStack(TFMGItems.COATED_CIRCUIT_BOARD.get(), 2))
                .fluids(f(TFMGFluids.SULFURIC_ACID)).makes(TFMGItems.ETCHED_CIRCUIT_BOARD.get(), 2));
        cases.add(new VatCase("liquid_air", steelVat).fluids(f(TFMGFluids.AIR)).freezer().compressor()
                .makes(f(TFMGFluids.COOLING_FLUID), 250));
        cases.add(new VatCase("liquid_asphalt", castIron).items(new ItemStack(TFMGItems.ASPHALT_MIXTURE.get(), 2)).fluids(water)
                .mixer(blade).makes(f(TFMGFluids.LIQUID_ASPHALT), 2000));
        cases.add(new VatCase("naphtha_cracking", castIron).fluids(f(TFMGFluids.NAPHTHA)).mixer(blade).heated()
                .makes(f(TFMGFluids.ETHYLENE), 250).makes(f(TFMGFluids.PROPYLENE), 250));
        cases.add(new VatCase("neon", steelVat).fluids(f(TFMGFluids.AIR)).mixer(TFMGItems.CENTRIFUGE.get())
                .makes(f(TFMGFluids.NEON), 5));
        cases.add(new VatCase("plastic_from_ethylene", castIron).fluids(f(TFMGFluids.ETHYLENE)).mixer(blade).heated()
                .makes(f(TFMGFluids.MOLTEN_PLASTIC), 500));
        cases.add(new VatCase("plastic_from_propylene", castIron).fluids(f(TFMGFluids.PROPYLENE)).mixer(blade).heated()
                .makes(f(TFMGFluids.MOLTEN_PLASTIC), 500));
        cases.add(new VatCase("rubber", castIron).items(new ItemStack(TFMGItems.SULFUR_DUST.get(), 2)).fluids(f(TFMGFluids.HEAVY_OIL))
                .mixer(blade).heated().makes(TFMGItems.RUBBER_SHEET.get(), 2));
        cases.add(new VatCase("sulfuric_acid", castIron).items(new ItemStack(TFMGItems.SULFUR_DUST.get(), 6),
                new ItemStack(TFMGItems.NITRATE_DUST.get(), 2)).fluids(water).mixer(blade).makes(f(TFMGFluids.SULFURIC_ACID), 1000));
        cases.add(new VatCase("hydrogen", castIron).fluids(water).electrodes(2, false).makes(f(TFMGFluids.HYDROGEN), 1000));
        cases.add(new VatCase("lpg_separation", castIron).fluids(f(TFMGFluids.LPG)).freezer()
                .makes(f(TFMGFluids.BUTANE), 500).makes(f(TFMGFluids.PROPANE), 500));
        return cases;
    }

    /**
     * Every chemical vat recipe in a working vat: fluids come from creative
     * tanks through pumps, items from a chest through a hopper, the products
     * leave through a pump into a fluid tank and a hopper into a chest.
     * Mixers and compressors turn on Create motors, electrodes and freezers
     * run on TFMG generators, heat comes from a burning firebox.
     */
    @GameTestGenerator
    public static List<TestFunction> vatRecipes() {
        List<TestFunction> tests = new ArrayList<>();
        for (VatCase c : vatCases())
            tests.add(TFMGGameTestUtil.test(BATCH, "vat_recipe." + c.name, TFMGGameTestUtil.PLATFORM_LARGE, 1400, h -> runVat(h, c)));
        return tests;
    }

    static void runVat(GameTestHelper helper, VatCase c) {
        int w = c.width;
        BlockPos vat = new BlockPos(5, 3, 5);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < w; z++)
                helper.setBlock(vat.offset(x, 0, z), c.vat.get().defaultBlockState());
        List<BlockPos> machines = new ArrayList<>();

        // Fluid supply along the north face, both pumps on one cog.
        for (int i = 0; i < c.fluids.size(); i++) {
            creativeTank(helper, new BlockPos(5 + i, 3, 3), c.fluids.get(i));
            pumpBlock(helper, new BlockPos(5 + i, 3, 4), Direction.SOUTH);
        }
        if (!c.fluids.isEmpty()) {
            cog(helper, new BlockPos(4, 3, 4), Direction.Axis.Z);
            motor(helper, new BlockPos(4, 3, 3), Direction.SOUTH, PUMP_RPM);
        }
        // Fluid products out of the east face into a tank.
        BlockPos sink = new BlockPos(6 + w, 3, 5);
        pump(helper, new BlockPos(5 + w, 3, 5), Direction.EAST, Direction.SOUTH, true, PUMP_RPM);
        tank(helper, sink);
        // Items in from the west, out through the bottom.
        BlockPos feed = new BlockPos(4, 4, 6);
        BlockPos outChest = new BlockPos(6, 1, 5);
        hopper(helper, new BlockPos(4, 3, 6), Direction.EAST);
        chest(helper, feed, c.items.toArray(ItemStack[]::new));
        hopper(helper, new BlockPos(6, 2, 5), Direction.DOWN);
        chest(helper, outChest);

        if (c.firebox)
            helper.setBlock(new BlockPos(5, 2, 5), TFMGBlocks.FIREBOX.get().defaultBlockState());
        BlockPos compressor = new BlockPos(5, 2, 6);
        if (c.compressor) {
            helper.setBlock(compressor, TFMGBlocks.COMPRESSOR.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
            motor(helper, new BlockPos(4, 2, 6), Direction.EAST, PUMP_RPM);
            machines.add(compressor);
        }
        if (c.mixerTool != null) {
            BlockPos mixer = new BlockPos(5, 4, 5);
            helper.setBlock(mixer, TFMGBlocks.INDUSTRIAL_MIXER.get().defaultBlockState());
            motor(helper, new BlockPos(5, 5, 5), Direction.DOWN, 64);
            machines.add(mixer);
        }
        List<BlockPos> holders = w == 3
                ? List.of(new BlockPos(5, 4, 5), new BlockPos(7, 4, 5), new BlockPos(6, 4, 7))
                : List.of(new BlockPos(5, 4, 5), new BlockPos(6, 4, 6));
        for (int i = 0; i < c.electrodes; i++) {
            helper.setBlock(holders.get(i), TFMGBlocks.ELECTRODE_HOLDER.get().defaultBlockState());
            helper.setBlock(holders.get(i).above(), TFMGBlocks.CREATIVE_GENERATOR.get().defaultBlockState());
            machines.add(holders.get(i));
        }
        if (c.freezer) {
            BlockPos freezer = new BlockPos(5, 4, 6);
            helper.setBlock(freezer, TFMGBlocks.FREEZER.get().defaultBlockState());
            helper.setBlock(freezer.above(), TFMGBlocks.CREATIVE_GENERATOR.get().defaultBlockState());
            machines.add(freezer);
        }

        helper.runAfterDelay(3, () -> {
            if (c.firebox)
                fluids(helper, new BlockPos(5, 2, 5), null).fill(new FluidStack(f(TFMGFluids.KEROSENE), 1000), IFluidHandler.FluidAction.EXECUTE);
            if (c.mixerTool != null && helper.getBlockEntity(new BlockPos(5, 4, 5)) instanceof IndustrialMixerBlockEntity mixer)
                mixer.setMixerMode(new ItemStack(c.mixerTool), false);
            for (int i = 0; i < c.electrodes; i++) {
                if (helper.getBlockEntity(holders.get(i)) instanceof ElectrodeHolderBlockEntity holder)
                    holder.setElectrode(new ItemStack(c.graphite ? TFMGItems.GRAPHITE_ELECTRODE.get() : TFMGItems.COPPER_ELECTRODE.get()), false);
                // A graphite arc needs 1500 V to push 5 A through its 300 ohm.
                if (c.graphite)
                    generatorVoltage(helper, holders.get(i).above(), 250);
            }
        });
        if (c.compressor)
            helper.runAfterDelay(10, () -> {
                // Pressurising needs the compressor to turn forwards.
                if (helper.getBlockEntity(compressor) instanceof CompressorBlockEntity be && be.getSpeed() < 0)
                    setMotor(helper, new BlockPos(4, 2, 6), -PUMP_RPM);
            });

        RunningInspection running = new RunningInspection();
        helper.onEachTick(() -> {
            // Recipes without a duration finish the tick after they match, so
            // look whenever a recipe is lined up rather than on a fixed beat.
            if (!(helper.getBlockEntity(vat) instanceof VatBlockEntity be) || be.recipe == null)
                return;
            Inspection inspection = inspect(helper, vat);
            if (inspection.has("vat.progress")) {
                List<BlockPos> sampled = new ArrayList<>(machines);
                sampled.add(0, vat);
                running.sample(helper, sampled.toArray(BlockPos[]::new));
            }
        });
        helper.succeedWhen(() -> {
            VatBlockEntity be = helper.getBlockEntity(vat);
            StringBuilder state = new StringBuilder();
            boolean done = true;
            for (Map.Entry<Fluid, Integer> e : c.expectFluids.entrySet()) {
                int made = amount(be.outputTank.getCapability(), e.getKey()) + amount(helper, sink, e.getKey());
                done &= made >= e.getValue();
                state.append(BuiltInRegistries.FLUID.getKey(e.getKey()).getPath()).append(' ').append(made).append('/').append(e.getValue()).append("; ");
            }
            for (Map.Entry<Item, Integer> e : c.expectItems.entrySet()) {
                int made = count(be.outputInventory, e.getKey()) + count(helper, outChest, e.getKey());
                done &= made >= e.getValue();
                state.append(BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).append(' ').append(made).append('/').append(e.getValue()).append("; ");
            }
            helper.assertTrue(done, c.name + ": " + state + " recipe " + (be.recipe != null) + ", machines " + be.machineMap.values()
                    + ", inspector: " + inspect(helper, vat).text());
            if (c.graphite) {
                // The arc furnace returns its coke dust 90% of the time: never more than it was given.
                int dust = count(be.inputInventory, TFMGItems.COAL_COKE_DUST.get()) + count(be.outputInventory, TFMGItems.COAL_COKE_DUST.get())
                        + count(helper, outChest, TFMGItems.COAL_COKE_DUST.get()) + count(helper, feed, TFMGItems.COAL_COKE_DUST.get())
                        + count(helper, new BlockPos(4, 3, 6), TFMGItems.COAL_COKE_DUST.get());
                TFMGGameTestUtil.check(helper, dust <= 2, "the arc furnace holds " + dust + " coke dust out of 2");
            }
            running.assertClean(helper, "vat " + c.name);
        });
    }

    /**
     * Three vats, each missing one thing: heat, an attachment, power. The
     * inspector names exactly that, and each vat starts producing once the
     * missing piece is added.
     */
    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 800)
    public static void vatNamesWhatIsMissing(GameTestHelper helper) {
        BlockPos rubberVat = new BlockPos(3, 2, 3);
        BlockPos concreteVat = new BlockPos(8, 2, 3);
        // Electrode holders only take power from above, so two need a 2x2 vat.
        BlockPos hydrogenVat = new BlockPos(12, 2, 3);
        BlockPos holderA = new BlockPos(12, 3, 3);
        BlockPos holderB = new BlockPos(13, 3, 4);
        Fluid water = net.minecraft.world.level.material.Fluids.WATER;
        for (BlockPos pos : List.of(rubberVat, concreteVat, hydrogenVat, hydrogenVat.east(), hydrogenVat.south(), hydrogenVat.east().south()))
            helper.setBlock(pos, TFMGBlocks.CAST_IRON_CHEMICAL_VAT.get().defaultBlockState());
        helper.setBlock(rubberVat.above(), TFMGBlocks.INDUSTRIAL_MIXER.get().defaultBlockState());
        motor(helper, rubberVat.above(2), Direction.DOWN, 64);
        helper.setBlock(holderA, TFMGBlocks.ELECTRODE_HOLDER.get().defaultBlockState());
        helper.setBlock(holderB, TFMGBlocks.ELECTRODE_HOLDER.get().defaultBlockState());

        helper.runAfterDelay(3, () -> {
            ((IndustrialMixerBlockEntity) helper.getBlockEntity(rubberVat.above())).setMixerMode(new ItemStack(TFMGItems.MIXER_BLADE.get()), false);
            ((ElectrodeHolderBlockEntity) helper.getBlockEntity(holderA)).setElectrode(new ItemStack(TFMGItems.COPPER_ELECTRODE.get()), false);
            ((ElectrodeHolderBlockEntity) helper.getBlockEntity(holderB)).setElectrode(new ItemStack(TFMGItems.ZINC_ELECTRODE.get()), false);
            fillVat(helper, rubberVat, new FluidStack(f(TFMGFluids.HEAVY_OIL), 1000), new ItemStack(TFMGItems.SULFUR_DUST.get(), 1));
            fillVat(helper, concreteVat, new FluidStack(water, 1000), new ItemStack(Items.SAND),
                    new ItemStack(Items.GRAVEL), new ItemStack(TFMGItems.LIMESAND.get()));
            fillVat(helper, hydrogenVat, new FluidStack(water, 2000));
        });
        helper.runAfterDelay(40, () -> {
            Inspection rubber = inspect(helper, rubberVat);
            TFMGGameTestUtil.check(helper, rubber.hasProblem("vat.heat_low"), "an unheated rubber vat does not ask for heat: " + rubber.text());
            Inspection concrete = inspect(helper, concreteVat);
            TFMGGameTestUtil.check(helper, concrete.hasProblem("vat.missing_machine"), "a concrete vat without mixer does not ask for one: " + concrete.text());
            Inspection hydrogen = inspect(helper, hydrogenVat);
            TFMGGameTestUtil.check(helper, hydrogen.hasProblem("vat.machines_down"), "unpowered electrodes are not reported: " + hydrogen.text());
            Inspection holder = inspect(helper, holderA);
            TFMGGameTestUtil.check(helper, holder.hasProblem("electric.no_voltage"), "an unpowered electrode holder does not say so: " + holder.text());
            TFMGGameTestUtil.check(helper, amount(helper, rubberVat, f(TFMGFluids.HEAVY_OIL)) == 1000, "the unheated vat used its oil");

            helper.setBlock(rubberVat.below(), TFMGBlocks.FIREBOX.get().defaultBlockState());
            helper.setBlock(concreteVat.above(), TFMGBlocks.INDUSTRIAL_MIXER.get().defaultBlockState());
            motor(helper, concreteVat.above(2), Direction.DOWN, 64);
            helper.setBlock(holderA.above(), TFMGBlocks.CREATIVE_GENERATOR.get().defaultBlockState());
            helper.setBlock(holderB.above(), TFMGBlocks.CREATIVE_GENERATOR.get().defaultBlockState());
            helper.runAfterDelay(3, () -> {
                fluids(helper, rubberVat.below(), null).fill(new FluidStack(f(TFMGFluids.KEROSENE), 1000), IFluidHandler.FluidAction.EXECUTE);
                ((IndustrialMixerBlockEntity) helper.getBlockEntity(concreteVat.above())).setMixerMode(new ItemStack(TFMGItems.MIXER_BLADE.get()), false);
            });
        });
        helper.succeedWhen(() -> {
            VatBlockEntity rubber = helper.getBlockEntity(rubberVat);
            helper.assertTrue(count(rubber.outputInventory, TFMGItems.RUBBER_SHEET.get()) == 1, "no rubber once heated");
            helper.assertTrue(amount(helper, concreteVat, f(TFMGFluids.LIQUID_CONCRETE)) == 32000, "no concrete once mixed: " + inspect(helper, concreteVat).text()
                    + " / mixer: " + inspect(helper, concreteVat.above()).text());
            helper.assertTrue(amount(helper, hydrogenVat, f(TFMGFluids.HYDROGEN)) == 1000, "hydrogen once powered: "
                    + amount(helper, hydrogenVat, f(TFMGFluids.HYDROGEN)));
        });
    }

    static void fillVat(GameTestHelper helper, BlockPos vat, FluidStack fluid, ItemStack... stacks) {
        fluids(helper, vat, null).fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        net.neoforged.neoforge.items.IItemHandler handler = items(helper, vat, null);
        for (ItemStack stack : stacks) {
            ItemStack left = stack.copy();
            for (int slot = 0; slot < handler.getSlots() && !left.isEmpty(); slot++)
                left = handler.insertItem(slot, left, false);
        }
    }

    // ============================================================ chains

    /**
     * Hot air from scratch: a 2x2 air intake on a motor fills a 2x2x3 blast
     * stove through a pump, creosote is pumped into its top, its carbon dioxide
     * is pumped into an exhaust, and the hot air it makes is pumped through
     * pipes into the tuyere of a 3 high blast furnace, which casts its steel
     * into a basin emptied into a chest.
     */
    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 3600)
    public static void hotAirChainFeedsBlastFurnace(GameTestHelper helper) {
        BlockPos output = new BlockPos(7, 2, 8);
        BlockPos tuyere = new BlockPos(6, 3, 7);
        BlockPos stove = new BlockPos(1, 2, 7);
        BlockPos intake = new BlockPos(1, 2, 10);
        BlockPos ingots = new BlockPos(8, 1, 10);
        buildBlueprint(helper, "blast_furnace_1", new BlockPos(6, 2, 6));
        hopper(helper, new BlockPos(7, 6, 7), Direction.DOWN);
        chest(helper, new BlockPos(7, 7, 7), new ItemStack(AllItems.CRUSHED_IRON.get(), 1), new ItemStack(TFMGItems.LIMESAND.get(), 1),
                new ItemStack(TFMGItems.COAL_COKE_DUST.get(), 2));
        pump(helper, new BlockPos(7, 2, 9), Direction.SOUTH, Direction.DOWN, PUMP_RPM);
        helper.setBlock(new BlockPos(7, 2, 10), TFMGBlocks.CASTING_BASIN.get().defaultBlockState());
        hopper(helper, new BlockPos(7, 1, 10), Direction.EAST);
        chest(helper, ingots);

        // The stove, 2x2 and 3 high.
        for (int y = 2; y <= 4; y++)
            for (int x = 1; x <= 2; x++)
                for (int z = 7; z <= 8; z++)
                    helper.setBlock(new BlockPos(x, y, z), TFMGBlocks.BLAST_STOVE.get().defaultBlockState());
        // Air: a 2x2 intake facing down, pumped into the side of the bottom layer.
        for (BlockPos pos : List.of(intake, intake.east(), intake.south(), intake.east().south()))
            helper.setBlock(pos, TFMGBlocks.AIR_INTAKE.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        motor(helper, intake.above(), Direction.DOWN, 256);
        pump(helper, new BlockPos(1, 2, 9), Direction.NORTH, Direction.WEST, PUMP_RPM);
        // Carbon dioxide out of the other side into an exhaust.
        pump(helper, new BlockPos(3, 2, 7), Direction.EAST, Direction.NORTH, PUMP_RPM);
        helper.setBlock(new BlockPos(4, 2, 7), TFMGBlocks.EXHAUST.get().defaultBlockState());
        // Creosote into the top.
        creativeTank(helper, new BlockPos(1, 6, 8), creosote());
        pump(helper, new BlockPos(1, 5, 8), Direction.DOWN, Direction.WEST, PUMP_RPM);
        // Hot air out of the top, piped down to the tuyere.
        pump(helper, new BlockPos(2, 5, 7), Direction.UP, Direction.EAST, PUMP_RPM);
        pipes(helper, new BlockPos(2, 6, 7), new BlockPos(3, 6, 7), new BlockPos(4, 6, 7), new BlockPos(5, 6, 7),
                new BlockPos(5, 5, 7), new BlockPos(5, 4, 7), new BlockPos(5, 3, 7));

        Inspection idle = inspect(helper, stove);
        TFMGGameTestUtil.check(helper, idle.hasProblem("blast_stove.no_air") && idle.hasProblem("blast_stove.no_fuel"),
                "a stove with no supply yet does not say what it lacks: " + idle.text());
        Inspection still = inspect(helper, intake);
        TFMGGameTestUtil.check(helper, still.hasProblem("air_intake.too_slow"), "a still air intake is not reported: " + still.text());

        RunningInspection stoveRun = new RunningInspection();
        RunningInspection furnaceRun = new RunningInspection();
        int[] hotAirSeen = {0};
        helper.onEachTick(() -> {
            if (helper.getTick() % 20 != 0)
                return;
            int hotAir = amount(helper, stove, hotAir());
            hotAirSeen[0] = Math.max(hotAirSeen[0], hotAir);
            BlastFurnaceOutputBlockEntity be = helper.getBlockEntity(output);
            // Once the furnace is done the hot air backs up and the stove,
            // then the intake, rightly report that they stopped: only look
            // while each one still has somewhere to put its product.
            if (hotAir > 0 && hotAir < 7000)
                stoveRun.sample(helper, stove);
            int air = amount(helper, intake, (Fluid) TFMGFluids.AIR.getSource());
            if (air > 0 && air < 7000)
                stoveRun.sample(helper, intake);
            // A 12 block stove makes about half the hot air the furnace burns,
            // so the furnace keeps pausing on an empty hatch, and says so.
            // Judge it on the ticks it has the air to smelt.
            if (be.timer > 0 && be.fuel > 0 && !be.inputInventory.isEmpty() && amount(helper, tuyere, hotAir()) >= 20)
                furnaceRun.sample(helper, output, tuyere);
        });
        helper.succeedWhen(() -> {
            BlastFurnaceOutputBlockEntity be = helper.getBlockEntity(output);
            helper.assertTrue(count(helper, ingots, TFMGItems.STEEL_INGOT.get()) == 1, "no steel ingot yet [" + furnace(be)
                    + ", stove " + describe(fluids(helper, stove, Direction.UP)) + " / " + describe(fluids(helper, stove, Direction.NORTH)) + "]");
            stoveRun.assertClean(helper, "blast stove");
            furnaceRun.assertClean(helper, "blast furnace on stove air");
        });
    }

    /**
     * The whole steel line, automated end to end: coal goes from a chest into
     * a coke oven, the coke falls into crushing wheels, the coke dust falls
     * down the blast furnace shaft as fuel, ore and limesand go in through a
     * hopper, and the molten steel is pumped into a casting basin whose ingot
     * a hopper drops into a chest.
     */
    @GameTest(template = HUGE, batch = BATCH, timeoutTicks = 4000)
    public static void coalToSteelIngotLine(GameTestHelper helper) {
        BlockPos output = new BlockPos(7, 4, 8);
        BlockPos oven = new BlockPos(6, 7, 7);
        BlockPos basin = new BlockPos(7, 2, 8);
        BlockPos ingots = new BlockPos(8, 1, 8);
        BlockPos wheelA = new BlockPos(7, 7, 6);
        BlockPos wheelB = new BlockPos(7, 7, 8);
        BlockPos crusher = new BlockPos(7, 7, 7);
        // The furnace, without its gas hatch: the crusher sits on the shaft.
        for (Map.Entry<BlockPos, BlockState> e : buildBlueprint(helper, "blast_furnace_1", new BlockPos(6, 4, 6)).entrySet())
            if (e.getKey().equals(crusher))
                helper.setBlock(crusher, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        // Coke oven whose door opens into the crushing wheels.
        helper.setBlock(oven, cokeOven(Direction.EAST));
        hopper(helper, new BlockPos(5, 7, 7), Direction.EAST);
        chest(helper, new BlockPos(5, 8, 7), new ItemStack(Items.COAL, 2));
        pump(helper, new BlockPos(6, 8, 7), Direction.UP, Direction.SOUTH, PUMP_RPM);
        creativeTank(helper, new BlockPos(6, 9, 7), co2());
        for (BlockPos wheel : List.of(wheelA, wheelB))
            helper.setBlock(wheel, com.simibubi.create.AllBlocks.CRUSHING_WHEEL.get().defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        motor(helper, wheelA.east(), Direction.WEST, 64);
        motor(helper, wheelB.east(), Direction.WEST, -64);
        // Ore and flux through the output block.
        hopper(helper, new BlockPos(7, 4, 9), Direction.NORTH);
        chest(helper, new BlockPos(7, 5, 9), new ItemStack(AllItems.CRUSHED_IRON.get(), 1), new ItemStack(TFMGItems.LIMESAND.get(), 1));
        // Hot air into the tuyere.
        creativeTank(helper, new BlockPos(4, 5, 7), hotAir());
        pump(helper, new BlockPos(5, 5, 7), Direction.EAST, Direction.NORTH, PUMP_RPM);
        // Molten metal down into the basin, ingots into the chest.
        pump(helper, new BlockPos(7, 3, 8), Direction.DOWN, Direction.EAST, true, PUMP_RPM);
        helper.setBlock(basin, TFMGBlocks.CASTING_BASIN.get().defaultBlockState());
        hopper(helper, new BlockPos(7, 1, 8), Direction.EAST);
        chest(helper, ingots);

        helper.runAfterDelay(10, () -> {
            // The wheels must spin into each other so the controller throws downwards.
            BlockState controller = helper.getLevel().getBlockState(helper.absolutePos(crusher));
            if (controller.hasProperty(BlockStateProperties.FACING) && controller.getValue(BlockStateProperties.FACING) != Direction.DOWN) {
                setMotor(helper, wheelA.east(), -64);
                setMotor(helper, wheelB.east(), 64);
            }
        });

        RunningInspection running = new RunningInspection();
        helper.onEachTick(() -> {
            if (helper.getTick() % 20 != 0)
                return;
            CokeOvenBlockEntity coke = helper.getBlockEntity(oven);
            if (coke.primaryTank.getFluidAmount() > 0 && !coke.inventory.isEmpty())
                running.sample(helper, oven);
            BlastFurnaceOutputBlockEntity be = helper.getBlockEntity(output);
            if (be.timer > 0 && be.fuel > 0 && !be.inputInventory.isEmpty())
                running.sample(helper, output);
        });
        helper.succeedWhen(() -> {
            BlastFurnaceOutputBlockEntity be = helper.getBlockEntity(output);
            BlockState controller = helper.getLevel().getBlockState(helper.absolutePos(crusher));
            helper.assertTrue(count(helper, ingots, TFMGItems.STEEL_INGOT.get()) == 1, "no steel ingot yet [" + furnace(be)
                    + ", crusher " + controller + ", coke entities " + itemEntities(helper, TFMGItems.COAL_COKE.get())
                    + ", dust entities " + itemEntities(helper, TFMGItems.COAL_COKE_DUST.get()) + "]");
            TFMGGameTestUtil.check(helper, count(helper, ingots, TFMGBlocks.SLAG_BLOCK.get().asItem()) <= 7, "more slag than the ore gave");
            running.assertClean(helper, "coal to steel line");
        });
    }

    // ===================================================== winding machine

    /**
     * A winding machine fed and emptied by hoppers: the copper spool goes to
     * its spool slot, each unfinished coil is wound with 200 turns and the
     * finished coils, not the unfinished ones, end up in the chest below.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 1200)
    public static void windingMachineWindsCoilsFromHoppers(GameTestHelper helper) {
        BlockPos machine = new BlockPos(2, 2, 2);
        BlockPos outChest = new BlockPos(3, 1, 2);
        helper.setBlock(machine, TFMGBlocks.WINDING_MACHINE.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        motor(helper, new BlockPos(1, 2, 2), Direction.EAST, 64);
        ItemStack spool = TFMGItems.COPPER_SPOOL.asStack();
        spool.set(TFMGDataComponents.SPOOL_AMOUNT, 1000);
        hopper(helper, new BlockPos(2, 3, 2), Direction.DOWN);
        chest(helper, new BlockPos(2, 4, 2), spool, new ItemStack(TFMGItems.UNFINISHED_ELECTROMAGNETIC_COIL.get(), 2));
        hopper(helper, new BlockPos(2, 1, 2), Direction.EAST);
        chest(helper, outChest);

        Inspection idle = inspect(helper, machine);
        TFMGGameTestUtil.check(helper, idle.hasProblem("winding_machine.no_workpiece"), "an empty machine does not ask for work: " + idle.text());

        RunningInspection running = new RunningInspection();
        helper.onEachTick(() -> {
            WindingMachineBlockEntity be = helper.getBlockEntity(machine);
            TFMGGameTestUtil.check(helper, count(helper, outChest, TFMGItems.UNFINISHED_ELECTROMAGNETIC_COIL.get()) == 0,
                    "an unfinished coil was pulled out of the winding machine before it was wound");
            if (be.recipe != null && be.amountWinded > 0 && helper.getTick() % 10 == 0)
                running.sample(helper, machine);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, outChest, TFMGItems.ELECTROMAGNETIC_COIL.get()) == 2, "coils wound so far: "
                    + count(helper, outChest, TFMGItems.ELECTROMAGNETIC_COIL.get()));
            ItemStack coil = first(helper, outChest, TFMGItems.ELECTROMAGNETIC_COIL.get());
            TFMGGameTestUtil.check(helper, coil.getOrDefault(TFMGDataComponents.COIL_TURNS, 0) == 200,
                    "coil turns " + coil.getOrDefault(TFMGDataComponents.COIL_TURNS, 0));
            WindingMachineBlockEntity be = helper.getBlockEntity(machine);
            TFMGGameTestUtil.check(helper, be.spool.getOrDefault(TFMGDataComponents.SPOOL_AMOUNT, 0) == 600,
                    "spool turns left " + be.spool.getOrDefault(TFMGDataComponents.SPOOL_AMOUNT, 0) + ", expected 600");
            running.assertClean(helper, "winding machine");
        });
    }

    // ========================================================= polarizer

    /**
     * Magnetic alloy ingots fed by a hopper wait in the polarizer until it has
     * power, the inspector says so, and once a generator feeds its back face
     * each ingot leaves as a magnet through the hopper below.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 1000)
    public static void polarizerMagnetisesFromHoppers(GameTestHelper helper) {
        BlockPos polarizer = new BlockPos(2, 2, 2);
        BlockPos outChest = new BlockPos(3, 1, 2);
        helper.setBlock(polarizer, TFMGBlocks.POLARIZER.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        hopper(helper, new BlockPos(2, 3, 2), Direction.DOWN);
        chest(helper, new BlockPos(2, 4, 2), new ItemStack(TFMGItems.MAGNETIC_ALLOY_INGOT.get(), 2));
        hopper(helper, new BlockPos(2, 1, 2), Direction.EAST);
        chest(helper, outChest);

        helper.runAfterDelay(40, () -> {
            PolarizerBlockEntity be = helper.getBlockEntity(polarizer);
            TFMGGameTestUtil.check(helper, be.inventory.getStackInSlot(0).is(TFMGItems.MAGNETIC_ALLOY_INGOT.get()),
                    "the unpowered polarizer does not hold the ingot (out chest: "
                            + count(helper, outChest, TFMGItems.MAGNETIC_ALLOY_INGOT.get()) + " ingots)");
            Inspection unpowered = inspect(helper, polarizer);
            TFMGGameTestUtil.check(helper, unpowered.hasProblem("polarizer.power_low"), "no power is not reported: " + unpowered.text());
            helper.setBlock(new BlockPos(2, 2, 3), TFMGBlocks.CREATIVE_GENERATOR.get().defaultBlockState());
        });
        RunningInspection running = new RunningInspection();
        helper.onEachTick(() -> {
            TFMGGameTestUtil.check(helper, count(helper, outChest, TFMGItems.MAGNETIC_ALLOY_INGOT.get()) == 0,
                    "an alloy ingot was pulled out of the polarizer before it was polarised");
            PolarizerBlockEntity be = helper.getBlockEntity(polarizer);
            if (be.capacitorPercentage > 0 && helper.getTick() % 10 == 0)
                running.sample(helper, polarizer);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, outChest, TFMGItems.MAGNET.get()) == 2, "magnets: " + count(helper, outChest, TFMGItems.MAGNET.get()));
            running.assertClean(helper, "polarizer");
        });
    }

    // ===================================================== machine input

    /** The machine input reports a missing shaft, then carries a motor's rotation. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 200)
    public static void machineInputCarriesRotation(GameTestHelper helper) {
        BlockPos input = new BlockPos(2, 1, 2);
        helper.setBlock(input, TFMGBlocks.MACHINE_INPUT.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        Inspection idle = inspect(helper, input);
        TFMGGameTestUtil.check(helper, idle.hasProblem("kinetic.no_rotation"), "a still machine input is not reported: " + idle.text());
        motor(helper, new BlockPos(2, 1, 1), Direction.SOUTH, 64);
        helper.succeedWhen(() -> {
            MachineInputBlockEntity be = helper.getBlockEntity(input);
            helper.assertTrue(Math.abs(be.getSpeed()) == 64, "machine input speed " + be.getSpeed());
            Inspection running = inspect(helper, input);
            TFMGGameTestUtil.check(helper, running.problems().isEmpty() && running.has("kinetic.speed"), "turning input: " + running.text());
        });
    }

    static String furnace(BlastFurnaceOutputBlockEntity be) {
        return "size " + be.getCachedSize() + ", ore " + be.inputInventory.getStackInSlot(0) + ", flux " + be.fluxInventory.getStackInSlot(0)
                + ", fuel " + be.fuel + ", timer " + be.timer + ", steel " + be.primaryTank.getFluidAmount() + ", slag " + be.secondaryTank.getFluidAmount()
                + ", hatch " + be.tuyerePos + (be.tuyereBE == null ? "" : " " + describe(be.tuyereBE.tank));
    }

    static String describe(net.neoforged.neoforge.fluids.capability.IFluidHandler handler) {
        if (handler == null)
            return "no handler";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < handler.getTanks(); i++)
            if (!handler.getFluidInTank(i).isEmpty())
                out.append(handler.getFluidInTank(i).getAmount()).append(" ").append(handler.getFluidInTank(i).getHoverName().getString()).append("; ");
        return out.length() == 0 ? "empty" : out.toString();
    }

    static int itemEntities(GameTestHelper helper, Item item) {
        int n = 0;
        for (ItemEntity entity : helper.getEntities(EntityType.ITEM))
            if (entity.getItem().is(item))
                n += entity.getItem().getCount();
        return n;
    }
}
