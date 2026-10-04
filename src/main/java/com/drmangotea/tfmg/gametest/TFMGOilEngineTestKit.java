package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.content.items.inspector.FactoryInspectorItem;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.google.gson.JsonObject;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Helpers for the oil, refining and engine game tests: fluids, kinetics,
 * player clicks and Factory Inspector reports, all read the way a player's
 * machine would see them.
 *
 * @author vyrriox
 */
final class TFMGOilEngineTestKit {

    private TFMGOilEngineTestKit() {
    }

    // ------------------------------------------------------------ fluids

    static Fluid fluid(String id) {
        Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
        if (fluid == Fluids.EMPTY)
            throw new IllegalStateException("no fluid " + id);
        return fluid;
    }

    static IFluidHandler fluids(GameTestHelper helper, BlockPos rel) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(rel), null);
    }

    static int fill(GameTestHelper helper, BlockPos rel, Fluid fluid, int amount) {
        IFluidHandler handler = fluids(helper, rel);
        if (handler == null)
            throw new IllegalStateException("no fluid handler at " + rel);
        return handler.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    static int amount(GameTestHelper helper, BlockPos rel, Fluid fluid) {
        return amount(fluids(helper, rel), fluid);
    }

    static int amount(IFluidHandler handler, Fluid fluid) {
        int total = 0;
        for (int i = 0; handler != null && i < handler.getTanks(); i++)
            if (handler.getFluidInTank(i).getFluid().isSame(fluid))
                total += handler.getFluidInTank(i).getAmount();
        return total;
    }

    static String contents(IFluidHandler handler) {
        if (handler == null)
            return "no handler";
        List<String> out = new ArrayList<>();
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            out.add(stack.isEmpty() ? "empty" : stack.getAmount() + " " + BuiltInRegistries.FLUID.getKey(stack.getFluid()));
        }
        return out.toString();
    }

    // ---------------------------------------------------------- kinetics

    static void motor(GameTestHelper helper, BlockPos pos, Direction facing, int rpm) {
        helper.setBlock(pos, AllBlocks.CREATIVE_MOTOR.get().defaultBlockState().setValue(BlockStateProperties.FACING, facing));
        helper.runAfterDelay(2, () -> setMotor(helper, pos, rpm));
    }

    static void setMotor(GameTestHelper helper, BlockPos pos, int rpm) {
        CreativeMotorBlockEntity motor = helper.getBlockEntity(pos);
        motor.generatedSpeed.setValue(rpm);
    }

    static void shaft(GameTestHelper helper, BlockPos pos, Direction.Axis axis) {
        helper.setBlock(pos, AllBlocks.SHAFT.get().defaultBlockState().setValue(BlockStateProperties.AXIS, axis));
    }

    static float speed(GameTestHelper helper, BlockPos rel) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof KineticBlockEntity kinetic ? kinetic.getSpeed() : 0;
    }

    /** Stress capacity of the kinetic network the block at {@code rel} sits on. */
    static float networkCapacity(GameTestHelper helper, BlockPos rel) {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof KineticBlockEntity kinetic) || !kinetic.hasNetwork())
            return 0;
        return kinetic.getOrCreateNetwork().calculateCapacity();
    }

    // ------------------------------------------------------- the player

    static Player player(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.SURVIVAL);
    }

    /** Right-clicks the block with the stack, as a player holding it would, and returns what is left in the hand. */
    static ItemStack use(GameTestHelper helper, Player player, BlockPos rel, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.useBlock(rel, player);
        return player.getItemInHand(InteractionHand.MAIN_HAND);
    }

    // ----------------------------------------------------------- recipes

    @SuppressWarnings("unchecked")
    static <R extends Recipe<?>> R recipe(GameTestHelper helper, String id) {
        Optional<RecipeHolder<?>> holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id));
        if (holder.isEmpty())
            throw new IllegalStateException("recipe " + id + " is missing");
        return (R) holder.get().value();
    }

    static ItemStack result(GameTestHelper helper, String id) {
        return recipe(helper, id).getResultItem(helper.getLevel().registryAccess()).copy();
    }

    // ---------------------------------------------------------- inspector

    /** Runs the Factory Inspector and returns the translation key of every problem line. */
    static List<String> problems(GameTestHelper helper, BlockPos rel) {
        List<String> keys = new ArrayList<>();
        for (Component line : inspect(helper, rel)) {
            if (!line.getString().startsWith("✖"))
                continue;
            for (Component part : line.getSiblings())
                if (part.getContents() instanceof TranslatableContents translatable)
                    keys.add(translatable.getKey().replace("tfmg.inspector.", ""));
        }
        return keys;
    }

    static List<Component> inspect(GameTestHelper helper, BlockPos rel) {
        return FactoryInspectorItem.inspect(helper.getLevel(), helper.absolutePos(rel), new InspectionReport());
    }

    static String report(GameTestHelper helper, BlockPos rel) {
        List<String> lines = new ArrayList<>();
        for (Component line : inspect(helper, rel))
            lines.add(line.getString());
        return String.join(" | ", lines);
    }

    /** Fails unless the inspector shows no problem and no raw text for the block. */
    static void assertClean(GameTestHelper helper, BlockPos rel, String what) {
        List<String> problems = problems(helper, rel);
        helper.assertTrue(problems.isEmpty(), what + ": inspector reports problems " + problems + " -> " + report(helper, rel));
        List<String> raw = TFMGGameTestUtil.inspectionProblems(helper, helper.absolutePos(rel));
        helper.assertTrue(raw.isEmpty(), what + ": inspector shows raw text " + raw);
    }

    /** Fails unless the inspector names {@code key} as a problem for the block. */
    static void assertProblem(GameTestHelper helper, BlockPos rel, String key, String what) {
        List<String> problems = problems(helper, rel);
        helper.assertTrue(problems.contains(key), what + ": inspector should report " + key + " but reports " + problems
                + " -> " + report(helper, rel));
    }

    // -------------------------------------------------------- structures

    /** Places a handbook blueprint with its corner at {@code origin} and returns the placed blocks (relative to the test). */
    static Map<BlockPos, BlockState> blueprint(GameTestHelper helper, String id, BlockPos origin) {
        JsonObject json = TFMGStructureTests.readBlueprints().get(id);
        if (json == null)
            throw new IllegalStateException("blueprint " + id + " missing");
        Map<BlockPos, BlockState> placed = new java.util.LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : TFMGStructureTests.blocksOf(json).entrySet()) {
            BlockPos pos = origin.offset(e.getKey());
            helper.setBlock(pos, e.getValue());
            placed.put(pos, e.getValue());
        }
        return placed;
    }

    static BlockPos find(Map<BlockPos, BlockState> blocks, Block block) {
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet())
            if (e.getValue().is(block))
                return e.getKey();
        throw new IllegalStateException("no " + BuiltInRegistries.BLOCK.getKey(block) + " in the structure");
    }

    /** Lets every pipe in the list recompute its connections from its neighbours, as placing it by hand would. */
    static void connect(GameTestHelper helper, List<BlockPos> pipes) {
        for (int pass = 0; pass < 2; pass++)
            for (BlockPos rel : pipes) {
                BlockPos abs = helper.absolutePos(rel);
                BlockState state = helper.getLevel().getBlockState(abs);
                BlockState updated = Block.updateFromNeighbourShapes(state, helper.getLevel(), abs);
                if (updated != state)
                    helper.getLevel().setBlock(abs, updated, 3);
            }
    }

    static BlockEntity be(GameTestHelper helper, BlockPos rel) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
    }
}
