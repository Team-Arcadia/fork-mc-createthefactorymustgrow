package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.electricity.utilities.electric_motor.ElectricMotorBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.blast_furnace.BlastFurnaceHatchBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.casting_basin.CastingBasinBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.firebox.FireboxBlock;
import com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Behaviour tests for individual machines: they build the machine, feed it
 * the way a player or a pipe would, and check what comes out. Each one guards
 * a bug fixed in 1.3.0 or a core production step.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGMachineTests {

    private static final BlockPos CENTER = new BlockPos(2, 1, 2);

    // ---------------------------------------------------------------- vat

    /** Pipes may feed the vat's input and take its output, never the reverse. */
    @GameTest(template = "gametest/platform", batch = "tfmg_machines")
    public static void vatItemFaceIsOneWay(GameTestHelper helper) {
        helper.setBlock(CENTER, TFMGBlocks.STEEL_CHEMICAL_VAT.get().defaultBlockState());
        helper.runAfterDelay(5, () -> {
            IItemHandler items = itemHandler(helper, CENTER);
            TFMGGameTestUtil.check(helper, items != null, "vat exposes no item handler");
            ItemStack left = insertAnywhere(items, new ItemStack(Items.SAND, 16));
            TFMGGameTestUtil.check(helper, left.isEmpty(), "vat refused sand into its input");
            VatBlockEntity vat = helper.getBlockEntity(CENTER);
            TFMGGameTestUtil.check(helper, vat.inputInventory.getStackInSlot(0).getCount() == 16,
                    "sand did not land in the input");
            for (int slot = 0; slot < items.getSlots(); slot++)
                TFMGGameTestUtil.check(helper, items.extractItem(slot, 64, true).isEmpty(),
                        "an ingredient could be pulled out of slot " + slot);
            vat.outputInventory.setStackInSlot(0, new ItemStack(Items.IRON_NUGGET, 3));
            boolean extracted = false;
            for (int slot = 0; slot < items.getSlots(); slot++)
                extracted |= items.extractItem(slot, 64, false).is(Items.IRON_NUGGET);
            TFMGGameTestUtil.check(helper, extracted, "a product could not be pulled out of the output");
            helper.succeed();
        });
    }

    /** Breaking a vat used to delete every item it held. */
    @GameTest(template = "gametest/platform", batch = "tfmg_machines")
    public static void vatDropsItsItemsWhenBroken(GameTestHelper helper) {
        helper.setBlock(CENTER, TFMGBlocks.STEEL_CHEMICAL_VAT.get().defaultBlockState());
        helper.runAfterDelay(5, () -> {
            VatBlockEntity vat = helper.getBlockEntity(CENTER);
            vat.inputInventory.setStackInSlot(0, new ItemStack(Items.SAND, 20));
            vat.outputInventory.setStackInSlot(0, new ItemStack(Items.IRON_NUGGET, 7));
            helper.destroyBlock(CENTER);
            helper.runAfterDelay(2, () -> {
                TFMGGameTestUtil.check(helper, countItemEntities(helper, Items.SAND) == 20, "the 20 sand were not dropped");
                TFMGGameTestUtil.check(helper, countItemEntities(helper, Items.IRON_NUGGET) == 7, "the 7 nuggets were not dropped");
                helper.succeed();
            });
        });
    }

    /**
     * A vat placed under a filled vat becomes the controller; the old one's
     * items and fluid must follow, not stay stranded in a member block.
     */
    @GameTest(template = "gametest/platform", batch = "tfmg_machines")
    public static void vatGrowingKeepsItsContents(GameTestHelper helper) {
        BlockPos top = CENTER.above();
        helper.setBlock(top, TFMGBlocks.STEEL_CHEMICAL_VAT.get().defaultBlockState());
        helper.runAfterDelay(5, () -> {
            VatBlockEntity old = helper.getBlockEntity(top);
            old.inputInventory.setStackInSlot(0, new ItemStack(Items.SAND, 12));
            old.inputTank.getCapability().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
            helper.setBlock(CENTER, TFMGBlocks.STEEL_CHEMICAL_VAT.get().defaultBlockState());
            helper.runAfterDelay(10, () -> {
                VatBlockEntity lower = helper.getBlockEntity(CENTER);
                VatBlockEntity controller = lower.isController() ? lower : lower.getControllerBE();
                TFMGGameTestUtil.check(helper, controller != null, "the two vats did not form one vat");
                int sand = 0;
                for (int i = 0; i < controller.inputInventory.getSlots(); i++)
                    if (controller.inputInventory.getStackInSlot(i).is(Items.SAND))
                        sand += controller.inputInventory.getStackInSlot(i).getCount();
                TFMGGameTestUtil.check(helper, sand == 12, "controller holds " + sand + " sand instead of 12");
                int water = 0;
                IFluidHandler tanks = controller.inputTank.getCapability();
                for (int i = 0; i < tanks.getTanks(); i++)
                    if (tanks.getFluidInTank(i).getFluid().isSame(net.minecraft.world.level.material.Fluids.WATER))
                        water += tanks.getFluidInTank(i).getAmount();
                TFMGGameTestUtil.check(helper, water == 2000, "controller holds " + water + " mB of water instead of 2000");
                helper.succeed();
            });
        });
    }

    // ------------------------------------------------------ casting basin

    /** 144 mB of molten steel casts one steel ingot; hoppers cannot block the slot. */
    @GameTest(template = "gametest/platform", batch = "tfmg_machines", timeoutTicks = 400)
    public static void castingBasinCastsSteel(GameTestHelper helper) {
        helper.setBlock(CENTER, TFMGBlocks.CASTING_BASIN.get().defaultBlockState());
        helper.runAfterDelay(2, () -> {
            IItemHandler items = itemHandler(helper, CENTER);
            TFMGGameTestUtil.check(helper, items != null && !items.insertItem(0, new ItemStack(Items.DIRT), true).isEmpty(),
                    "the basin's output slot accepted an inserted item");
            IFluidHandler fluids = fluidHandler(helper, CENTER);
            int filled = fluids.fill(new FluidStack((Fluid) TFMGFluids.MOLTEN_STEEL.getSource(), 144), IFluidHandler.FluidAction.EXECUTE);
            TFMGGameTestUtil.check(helper, filled == 144, "basin took " + filled + " mB of molten steel");
        });
        helper.succeedWhen(() -> {
            CastingBasinBlockEntity basin = helper.getBlockEntity(CENTER);
            helper.assertTrue(basin.inventory.getStackInSlot(0).is(TFMGItems.STEEL_INGOT.get()), "no steel ingot yet");
            helper.assertTrue(basin.tank.getFluidAmount() == 0, "molten steel left in the basin");
        });
    }

    // ------------------------------------------------- blast furnace hatch

    /** Items held by the hatch used to vanish on every chunk reload. */
    @GameTest(template = "gametest/platform", batch = "tfmg_machines")
    public static void blastFurnaceHatchKeepsItsItems(GameTestHelper helper) {
        helper.setBlock(CENTER, TFMGBlocks.BLAST_FURNACE_HATCH.get().defaultBlockState());
        helper.runAfterDelay(2, () -> {
            BlastFurnaceHatchBlockEntity hatch = helper.getBlockEntity(CENTER);
            hatch.inventory.setStackInSlot(0, new ItemStack(AllItems.CRUSHED_IRON.get(), 9));
            BlockEntity copy = reload(helper, hatch);
            TFMGGameTestUtil.check(helper, copy instanceof BlastFurnaceHatchBlockEntity h
                    && h.inventory.getStackInSlot(0).getCount() == 9, "the hatch lost its items on reload");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------ firebox

    /** A firebox holding fuel lights up and heats. */
    @GameTest(template = "gametest/platform", batch = "tfmg_machines", timeoutTicks = 200)
    public static void fireboxBurnsFuel(GameTestHelper helper) {
        helper.setBlock(CENTER, TFMGBlocks.FIREBOX.get().defaultBlockState());
        helper.runAfterDelay(2, () -> {
            IFluidHandler fluids = fluidHandler(helper, CENTER);
            int filled = fluids.fill(new FluidStack((Fluid) TFMGFluids.KEROSENE.getSource(), 1000), IFluidHandler.FluidAction.EXECUTE);
            TFMGGameTestUtil.check(helper, filled > 0, "the firebox refused kerosene");
        });
        helper.succeedWhen(() -> helper.assertBlockState(CENTER,
                s -> s.getValue(FireboxBlock.HEAT_LEVEL) != BlazeBurnerBlock.HeatLevel.NONE,
                () -> "the firebox never lit"));
    }

    // -------------------------------------------------------- electricity

    /** A creative generator behind an electric motor makes the motor turn. */
    @GameTest(template = "gametest/platform", batch = "tfmg_machines", timeoutTicks = 200)
    public static void electricMotorRunsOnGenerator(GameTestHelper helper) {
        helper.setBlock(CENTER, TFMGBlocks.ELECTRIC_MOTOR.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
        helper.setBlock(CENTER.south(), TFMGBlocks.CREATIVE_GENERATOR.get().defaultBlockState());
        helper.succeedWhen(() -> {
            ElectricMotorBlockEntity motor = helper.getBlockEntity(CENTER);
            helper.assertTrue(motor.getSpeed() != 0, "the motor is not turning (voltage " + motor.getData().getVoltage() + ")");
        });
    }

    // ------------------------------------------------------------ helpers

    private static IItemHandler itemHandler(GameTestHelper helper, BlockPos rel) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(rel), Direction.UP);
    }

    private static IFluidHandler fluidHandler(GameTestHelper helper, BlockPos rel) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(rel), Direction.UP);
    }

    private static ItemStack insertAnywhere(IItemHandler handler, ItemStack stack) {
        for (int slot = 0; slot < handler.getSlots() && !stack.isEmpty(); slot++)
            stack = handler.insertItem(slot, stack, false);
        return stack;
    }

    private static int countItemEntities(GameTestHelper helper, net.minecraft.world.item.Item item) {
        int n = 0;
        List<ItemEntity> entities = helper.getEntities(EntityType.ITEM, CENTER, 3);
        for (ItemEntity entity : entities)
            if (entity.getItem().is(item))
                n += entity.getItem().getCount();
        return n;
    }

    /** Saves a block entity and loads a fresh one from the save, as a chunk reload would. */
    static BlockEntity reload(GameTestHelper helper, BlockEntity be) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag tag = be.saveWithFullMetadata(registries);
        BlockEntity copy = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), tag, registries);
        if (copy != null)
            copy.setLevel(helper.getLevel());
        return copy;
    }
}
