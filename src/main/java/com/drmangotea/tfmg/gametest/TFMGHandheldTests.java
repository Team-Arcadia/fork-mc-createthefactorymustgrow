package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.TFMGRegistries;
import com.drmangotea.tfmg.base.TFMGTiers;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.base.spark.DryIceFlake;
import com.drmangotea.tfmg.base.spark.LithiumSpark;
import com.drmangotea.tfmg.base.spark.Spark;
import com.drmangotea.tfmg.content.decoration.LithiumTorchBlock;
import com.drmangotea.tfmg.content.decoration.pipes.TFMGPipeBlockEntity;
import com.drmangotea.tfmg.content.decoration.pipes.TFMGPipes;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.content.electricity.configuration_wrench.ElectriciansWrenchPacket;
import com.drmangotea.tfmg.content.engines.types.AbstractSmallEngineBlockEntity;
import com.drmangotea.tfmg.content.items.blueprint.BlueprintLines;
import com.drmangotea.tfmg.content.items.guide.GuideEvents;
import com.drmangotea.tfmg.content.items.weapons.advanced_potato_cannon.projectile.NapalmPotato;
import com.drmangotea.tfmg.content.items.weapons.explosives.napalm.NapalmBombEntity;
import com.drmangotea.tfmg.content.items.weapons.explosives.pipe_bomb.PipeBomb;
import com.drmangotea.tfmg.content.items.weapons.explosives.thermite_grenades.ThermiteGrenade;
import com.drmangotea.tfmg.content.items.weapons.explosives.thermite_grenades.ThermiteGrenadeItem;
import com.drmangotea.tfmg.content.items.weapons.explosives.thermite_grenades.fire.TFMGColoredFires;
import com.drmangotea.tfmg.content.items.weapons.fire_extinguisher.FireExtinguisherItem;
import com.drmangotea.tfmg.content.items.weapons.flamethrover.FlamethrowerFuel;
import com.drmangotea.tfmg.content.items.weapons.flamethrover.FlamethrowerFuelType;
import com.drmangotea.tfmg.content.items.weapons.flamethrover.FlamethrowerItem;
import com.drmangotea.tfmg.content.items.weapons.lithium_blade.LithiumBladeItem;
import com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.base.FluidReservoir;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGEntityTypes;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.drmangotea.tfmg.registry.TFMGMobEffects;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllEnchantments;
import com.simibubi.create.content.equipment.potatoCannon.PotatoProjectileEntity;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.fluid.FluidHelper;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.drmangotea.tfmg.gametest.TFMGHandheldKit.*;

/**
 * Every hand-held item of the mod used by a player on a server, the way a
 * connected player's clicks reach it, with its real effect checked in the
 * world: flamethrower fuels, the fire extinguisher, the lithium blade and
 * torch, both potato cannons, grenades and bombs, tools of every tier, and
 * the right-click tools (screwdriver, oil hammer, configuration wrench,
 * multimeter, oil can, cooling fluid bottle, transmission, Factory
 * Inspector, Handbook and Blueprint).
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGHandheldTests {

    private static final String BATCH = "tfmg_handheld";
    private static final String SMALL = "gametest/platform";
    private static final String LARGE = "gametest/platform_large";

    // ======================================================== flamethrower

    /** Every flamethrower fuel the mod ships, as registered in TFMGFlamethrowerFuelTypes. */
    private static final List<String> FUELS = List.of("gasoline", "diesel", "kerosene", "naphtha", "lpg", "napalm", "molten_slag");

    /** Barrel offset of the flamethrower, as FlamethrowerItem#onUseTick computes it. */
    private static final Vec3 FLAMETHROWER_BARREL = new Vec3(.75f, -0.65f, 1.5f);

    private static ResourceKey<FlamethrowerFuelType> fuelKey(String name) {
        return ResourceKey.create(TFMGRegistries.FLAMETHROWER_FUEL_TYPE, TFMG.asResource(name));
    }

    private static FlamethrowerFuel fuelOf(ItemStack flamethrower) {
        return flamethrower.getOrDefault(TFMGDataComponents.FLAMETHROWER, FlamethrowerFuel.EMPTY);
    }

    private static Fluid still(net.minecraft.world.level.material.Fluid fluid) {
        return FluidHelper.convertToStill(fluid);
    }

    private static void tankWith(GameTestHelper helper, BlockPos pos, Fluid fluid, int amount) {
        helper.setBlock(pos, AllBlocks.FLUID_TANK.get().defaultBlockState());
        if (fluid != null && amount > 0) {
            int filled = TFMGFactoryKit.fluids(helper, pos, null).fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
            if (filled != amount)
                throw new IllegalStateException("the test tank took " + filled + " of " + amount + " mB");
        }
    }

    /** Each fuel fills the flamethrower from a tank, then burns: fuel goes down and a pig in front catches it. */
    @GameTestGenerator
    public static List<TestFunction> flamethrowerFuels() {
        List<TestFunction> tests = new ArrayList<>();
        for (String fuel : FUELS)
            tests.add(TFMGGameTestUtil.test(BATCH, "flamethrower.fuel." + fuel, TFMGGameTestUtil.PLATFORM_LARGE, 300,
                    helper -> flamethrowerBurns(helper, fuel)));
        return tests;
    }

    private static void flamethrowerBurns(GameTestHelper helper, String fuelName) {
        FlamethrowerFuelType type = helper.getLevel().registryAccess().registryOrThrow(TFMGRegistries.FLAMETHROWER_FUEL_TYPE).get(fuelKey(fuelName));
        helper.assertTrue(type != null, "no flamethrower fuel type " + fuelName);
        Fluid fluid = type.fluids().get(0).value();
        BlockPos tank = new BlockPos(2, 2, 2);
        tankWith(helper, tank, fluid, 2000);

        Tester player = player(helper, new Vec3(6.5, 2, 1.5), 0, 0);
        InteractionResult refuel = click(helper, player, tank, Direction.UP, TFMGItems.FLAMETHROWER.asStack());
        FlamethrowerFuel fuel = fuelOf(held(player));
        helper.assertTrue(refuel.consumesAction(), "refuelling from the tank answered " + refuel);
        helper.assertTrue(fuelKey(fuelName).equals(fuel.fuelType()) && fuel.amount() == 2000,
                "a flamethrower clicked on 2000 mB of " + BuiltInRegistries.FLUID.getKey(fluid) + " holds " + fuel + " (click " + refuel
                        + ", tank " + TFMGFactoryKit.fluids(helper, tank, null).getFluidInTank(0).getAmount() + " "
                        + BuiltInRegistries.FLUID.getKey(TFMGFactoryKit.fluids(helper, tank, null).getFluidInTank(0).getFluid()) + ")");
        helper.assertTrue(TFMGFactoryKit.amount(helper, tank, fluid) == 0, "the tank still holds "
                + TFMGFactoryKit.amount(helper, tank, fluid) + " mB after refuelling");
        helper.assertTrue(player.getCooldowns().isOnCooldown(TFMGItems.FLAMETHROWER.get()), "refuelling sets no cooldown");

        Mob pig = target(helper, new Vec3(6.5, 2, 6.0));
        // Slow sparks fall on their way: aim above the target by the drop over the flight.
        Vec3 aim = centre(pig);
        double flight = barrel(player, FLAMETHROWER_BARREL).distanceTo(aim) / Math.max(0.1f, type.speed());
        aimBarrel(player, aim.add(0, 0.01 * flight * flight, 0), FLAMETHROWER_BARREL);
        useInAir(helper, player, held(player));
        helper.assertTrue(player.isUsingItem(), "a fuelled flamethrower does not start firing");
        holdUse(helper, player, 60, () -> {
            int left = fuelOf(held(player)).amount();
            helper.assertTrue(left < 2000, fuelName + ": 60 ticks of fire burnt no fuel");
            helper.assertTrue(hurt(pig) || pig.isOnFire(), fuelName + ": the pig in front was neither burnt nor hurt");
            helper.succeed();
        });
    }

    /** The fuel list above matches the registry, so a new fuel cannot go untested. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void flamethrowerFuelListIsComplete(GameTestHelper helper) {
        Set<String> registered = new TreeSet<>();
        for (var key : helper.getLevel().registryAccess().registryOrThrow(TFMGRegistries.FLAMETHROWER_FUEL_TYPE).keySet())
            if (!key.getPath().equals("fallback"))
                registered.add(key.getPath());
        helper.assertTrue(registered.equals(new TreeSet<>(FUELS)), "registered flamethrower fuels " + registered + " differ from the tested " + FUELS);
        helper.succeed();
    }

    /**
     * The flamethrower takes no water, keeps to the fuel it holds, tops up to
     * its 4000 mB and takes nothing once full.
     */
    @GameTest(template = SMALL, batch = BATCH)
    public static void flamethrowerTakesOnlyItsFuel(GameTestHelper helper) {
        Fluid gasoline = still(TFMGFluids.GASOLINE.get());
        Fluid diesel = still(TFMGFluids.DIESEL.get());
        BlockPos water = new BlockPos(0, 2, 0);
        BlockPos dieselTank = new BlockPos(2, 2, 0);
        BlockPos gasolineTank = new BlockPos(4, 2, 0);
        tankWith(helper, water, Fluids.WATER, 1000);
        tankWith(helper, dieselTank, diesel, 1000);
        tankWith(helper, gasolineTank, gasoline, 3500);
        Tester player = player(helper, new Vec3(2.5, 2, 3.5), 180, 0);

        InteractionResult onWater = click(helper, player, water, Direction.UP, TFMGItems.FLAMETHROWER.asStack());
        helper.assertTrue(!onWater.consumesAction() && fuelOf(held(player)).isEmpty(), "water filled the flamethrower: " + fuelOf(held(player)));
        helper.assertTrue(TFMGFactoryKit.amount(helper, water, Fluids.WATER) == 1000, "the flamethrower drained water");

        ItemStack holding = TFMGItems.FLAMETHROWER.asStack();
        holding.set(TFMGDataComponents.FLAMETHROWER, FlamethrowerFuel.createForType(helper.getLevel().registryAccess(), gasoline, 1000));
        click(helper, player, dieselTank, Direction.UP, holding);
        helper.assertTrue(fuelOf(held(player)).amount() == 1000 && fuelKey("gasoline").equals(fuelOf(held(player)).fuelType()),
                "diesel mixed into gasoline: " + fuelOf(held(player)));
        helper.assertTrue(TFMGFactoryKit.amount(helper, dieselTank, diesel) == 1000, "diesel left the tank for a gasoline flamethrower");

        player.getCooldowns().removeCooldown(TFMGItems.FLAMETHROWER.get());
        click(helper, player, gasolineTank, Direction.UP);
        helper.assertTrue(fuelOf(held(player)).amount() == FlamethrowerItem.FUEL_CAPACITY, "topping up 1000 mB from 3500 gave " + fuelOf(held(player)));
        helper.assertTrue(TFMGFactoryKit.amount(helper, gasolineTank, gasoline) == 500, "the tank kept "
                + TFMGFactoryKit.amount(helper, gasolineTank, gasoline) + " mB, 500 expected");

        player.getCooldowns().removeCooldown(TFMGItems.FLAMETHROWER.get());
        click(helper, player, gasolineTank, Direction.UP);
        helper.assertTrue(TFMGFactoryKit.amount(helper, gasolineTank, gasoline) == 500, "a full flamethrower still drained the tank");
        helper.succeed();
    }

    /** An engine's fuel cannot be taken: the flamethrower used to refill for free from any engine. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void flamethrowerGetsNothingFromEngines(GameTestHelper helper) {
        BlockPos engine = new BlockPos(2, 2, 2);
        helper.setBlock(engine, TFMGBlocks.REGULAR_ENGINE.get().defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.NORTH));
        Fluid gasoline = still(TFMGFluids.GASOLINE.get());
        helper.runAfterDelay(5, () -> {
            int filled = TFMGFactoryKit.fluids(helper, engine, null).fill(new FluidStack(gasoline, 2000), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(filled == 2000, "the engine took " + filled + " mB of gasoline");
            Tester player = player(helper, new Vec3(2.5, 2, 4.5), 180, 0);

            click(helper, player, engine, Direction.UP, TFMGItems.FLAMETHROWER.asStack());
            helper.assertTrue(fuelOf(held(player)).isEmpty(), "an empty flamethrower took " + fuelOf(held(player)) + " from an engine");

            ItemStack holding = TFMGItems.FLAMETHROWER.asStack();
            holding.set(TFMGDataComponents.FLAMETHROWER, FlamethrowerFuel.createForType(helper.getLevel().registryAccess(), gasoline, 100));
            click(helper, player, engine, Direction.UP, holding);
            helper.assertTrue(fuelOf(held(player)).amount() == 100, "a gasoline flamethrower grew to " + fuelOf(held(player)) + " on an engine");
            helper.assertTrue(TFMGFactoryKit.amount(helper, engine, gasoline) == 2000, "the engine lost fuel to the flamethrower");
            helper.succeed();
        });
    }

    /** A nearly dry flamethrower fires its last fuel, then lets go of the trigger and will not fire again. */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void flamethrowerStopsWhenEmpty(GameTestHelper helper) {
        BlockPos tank = new BlockPos(2, 2, 2);
        Fluid gasoline = still(TFMGFluids.GASOLINE.get());
        tankWith(helper, tank, gasoline, 30);
        Tester player = player(helper, new Vec3(6.5, 2, 1.5), 0, 30);
        click(helper, player, tank, Direction.UP, TFMGItems.FLAMETHROWER.asStack());
        helper.assertTrue(fuelOf(held(player)).amount() == 30, "the flamethrower took " + fuelOf(held(player)) + " from a 30 mB tank, which kept "
                + TFMGFactoryKit.amount(helper, tank, gasoline) + " mB");
        player.getCooldowns().removeCooldown(TFMGItems.FLAMETHROWER.get());
        player.setXRot(30);
        useInAir(helper, player, held(player));
        holdUse(helper, player, 60, () -> {
            helper.assertTrue(!entities(helper, Spark.class).isEmpty() || fuelOf(held(player)).isEmpty(), "no spark was fired");
            helper.assertTrue(fuelOf(held(player)).isEmpty(), "30 mB lasted more than 60 ticks: " + fuelOf(held(player)));
            helper.assertTrue(!player.isUsingItem(), "the empty flamethrower keeps the trigger held");
            useInAir(helper, player, held(player));
            helper.assertTrue(!player.isUsingItem(), "an empty flamethrower starts firing");
            helper.succeed();
        });
    }

    // =================================================== fire extinguisher

    /** Spraying at a patch of fire puts every flame out, one charge per tick held. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 200)
    public static void extinguisherPutsOutFire(GameTestHelper helper) {
        List<BlockPos> fires = new ArrayList<>();
        for (int x = 0; x < 5; x++)
            for (int z = 1; z < 5; z++)
                helper.setBlock(new BlockPos(x, 1, z), Blocks.NETHERRACK);
        for (int x = 1; x <= 3; x++)
            for (int z = 2; z <= 3; z++) {
                BlockPos fire = new BlockPos(x, 2, z);
                helper.setBlock(fire, Blocks.FIRE);
                fires.add(fire);
            }
        Tester player = player(helper, new Vec3(2.5, 2, 0.3), 0, 0);
        // Flakes leave 1.2 blocks above the feet (FireExtinguisherItem#onUseTick).
        lookFrom(player, player.position().add(0, 1.2, 0), helper.absoluteVec(new Vec3(2.5, 2.0, 3.0)));
        ItemStack extinguisher = TFMGItems.FIRE_EXTINGUISHER.asStack();
        helper.assertTrue(extinguisher.getOrDefault(TFMGDataComponents.AMOUNT, 0) == FireExtinguisherItem.DRY_ICE_CAPACITY,
                "a new extinguisher is not full: " + extinguisher.get(TFMGDataComponents.AMOUNT));
        useInAir(helper, player, extinguisher);
        helper.assertTrue(player.isUsingItem(), "the extinguisher does not spray");
        StringBuilder seen = flakeTrace(helper);
        holdUse(helper, player, 60, () -> {
            List<BlockPos> burning = new ArrayList<>();
            for (BlockPos fire : fires)
                if (helper.getBlockState(fire).is(Blocks.FIRE))
                    burning.add(fire);
            helper.assertTrue(burning.isEmpty(), "60 ticks of spraying left fire at " + burning + " flakes " + seen);
            int left = held(player).getOrDefault(TFMGDataComponents.AMOUNT, 0);
            helper.assertTrue(left == FireExtinguisherItem.DRY_ICE_CAPACITY - 60, "60 ticks used " + (FireExtinguisherItem.DRY_ICE_CAPACITY - left) + " charges");
            helper.succeed();
        });
    }

    private static StringBuilder flakeTrace(GameTestHelper helper) {
        StringBuilder seen = new StringBuilder();
        int[] tick = new int[1];
        helper.onEachTick(() -> {
            if (tick[0]++ % 10 == 0 && tick[0] < 40)
                for (DryIceFlake flake : entities(helper, DryIceFlake.class))
                    seen.append(String.format("[%.1f %.1f %.1f v%.2f %.2f %.2f]", helper.relativeVec(flake.position()).x,
                            helper.relativeVec(flake.position()).y, helper.relativeVec(flake.position()).z,
                            flake.getDeltaMovement().x, flake.getDeltaMovement().y, flake.getDeltaMovement().z));
        });
        return seen;
    }

    /** Flakes put out a burning mob and chill it. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 200)
    public static void extinguisherPutsOutBurningMob(GameTestHelper helper) {
        Mob pig = target(helper, new Vec3(2.5, 2, 3.2));
        pig.igniteForSeconds(30);
        Tester player = player(helper, new Vec3(2.5, 2, 0.3), 0, 0);
        lookFrom(player, player.position().add(0, 1.2, 0), centre(pig).add(0, 0.2, 0));
        useInAir(helper, player, TFMGItems.FIRE_EXTINGUISHER.asStack());
        StringBuilder seen = flakeTrace(helper);
        holdUse(helper, player, 40, () -> {
            helper.assertTrue(!pig.isOnFire(), "the pig at " + helper.relativeVec(pig.position()) + " still burns after 40 ticks of spraying, flakes " + seen);
            helper.assertTrue(pig.getTicksFrozen() > 0 || pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the flakes neither chilled nor slowed the pig");
            helper.succeed();
        });
    }

    /** An empty extinguisher sprays nothing. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void emptyExtinguisherSpraysNothing(GameTestHelper helper) {
        ItemStack empty = TFMGItems.FIRE_EXTINGUISHER.asStack();
        empty.set(TFMGDataComponents.AMOUNT, 0);
        Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 20);
        useInAir(helper, player, empty);
        holdUse(helper, player, 10, () -> {
            helper.assertTrue(entities(helper, DryIceFlake.class).isEmpty(), "an empty extinguisher sprayed flakes");
            helper.assertTrue(held(player).getOrDefault(TFMGDataComponents.AMOUNT, 0) == 0, "the empty extinguisher's charge changed");
            helper.succeed();
        });
    }

    /** A spout refills a used extinguisher with carbon dioxide, and only with carbon dioxide. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void spoutRefillsExtinguisher(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack used = TFMGItems.FIRE_EXTINGUISHER.asStack();
        used.set(TFMGDataComponents.AMOUNT, 120);
        FluidStack co2 = new FluidStack(still(TFMGFluids.CARBON_DIOXIDE.get()), 1000);
        helper.assertTrue(FillingBySpout.canItemBeFilled(level, used), "a spout cannot fill a used extinguisher");
        int needed = FillingBySpout.getRequiredAmountForItem(level, used, co2);
        helper.assertTrue(needed > 0 && needed <= 1000, "the spout asks " + needed + " mB of carbon dioxide");
        ItemStack refilled = FillingBySpout.fillItem(level, needed, used, co2);
        helper.assertTrue(refilled.is(TFMGItems.FIRE_EXTINGUISHER.get())
                        && refilled.getOrDefault(TFMGDataComponents.AMOUNT, 0) == FireExtinguisherItem.DRY_ICE_CAPACITY,
                "the spout gave " + refilled + " with " + refilled.get(TFMGDataComponents.AMOUNT) + " charges");
        ItemStack other = TFMGItems.FIRE_EXTINGUISHER.asStack();
        other.set(TFMGDataComponents.AMOUNT, 120);
        helper.assertTrue(FillingBySpout.getRequiredAmountForItem(level, other, new FluidStack(Fluids.WATER, 1000)) <= 0,
                "a spout would fill an extinguisher with water");
        helper.succeed();
    }

    // ======================================================== lithium blade

    /** A lithium charge lights the blade, which keeps its enchantments and wear; without a charge nothing happens. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void lithiumBladeLightsWithACharge(GameTestHelper helper) {
        Holder<Enchantment> sharpness = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
        Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
        ItemStack blade = TFMGItems.LITHIUM_BLADE.asStack();
        blade.enchant(sharpness, 2);
        blade.setDamageValue(10);
        useInAir(helper, player, blade);
        helper.assertTrue(held(player).is(TFMGItems.LITHIUM_BLADE.get()), "the blade lit without a lithium charge");

        player.getInventory().setItem(5, new ItemStack(TFMGItems.LITHIUM_CHARGE.get(), 2));
        useInAir(helper, player, held(player));
        ItemStack lit = held(player);
        helper.assertTrue(lit.is(TFMGItems.LIT_LITHIUM_BLADE.get()), "a charge did not light the blade: " + lit);
        helper.assertTrue(Integer.valueOf(LithiumBladeItem.MAX_TIME).equals(lit.get(TFMGDataComponents.LITHIUM_BLADE_TIMER)),
                "the lit blade burns for " + lit.get(TFMGDataComponents.LITHIUM_BLADE_TIMER));
        helper.assertTrue(lit.getEnchantmentLevel(sharpness) == 2 && lit.getDamageValue() == 10,
                "lighting lost the enchantment or the wear: " + lit.getEnchantments() + ", damage " + lit.getDamageValue());
        helper.assertTrue(player.getInventory().getItem(5).getCount() == 1, "lighting used " + (2 - player.getInventory().getItem(5).getCount()) + " charges");
        helper.succeed();
    }

    /** The lit blade throws ten hellfire sparks for 100 ticks of burn time, then waits; with too little time left it throws none. */
    @GameTest(template = LARGE, batch = BATCH)
    public static void litBladeThrowsSparks(GameTestHelper helper) {
        Tester player = player(helper, new Vec3(6.5, 2, 1.5), 0, 20);
        ItemStack lit = TFMGItems.LIT_LITHIUM_BLADE.asStack();
        lit.set(TFMGDataComponents.LITHIUM_BLADE_TIMER, 2000);
        useInAir(helper, player, lit);
        helper.assertTrue(entities(helper, LithiumSpark.class).size() == 10, entities(helper, LithiumSpark.class).size() + " sparks thrown, 10 expected");
        helper.assertTrue(Integer.valueOf(1900).equals(held(player).get(TFMGDataComponents.LITHIUM_BLADE_TIMER)),
                "a burst left " + held(player).get(TFMGDataComponents.LITHIUM_BLADE_TIMER) + " ticks of burn time");
        helper.assertTrue(player.getCooldowns().isOnCooldown(TFMGItems.LIT_LITHIUM_BLADE.get()), "no cooldown after a burst");

        Tester other = player(helper, new Vec3(3.5, 2, 1.5), 0, 20);
        ItemStack low = TFMGItems.LIT_LITHIUM_BLADE.asStack();
        low.set(TFMGDataComponents.LITHIUM_BLADE_TIMER, 100);
        useInAir(helper, other, low);
        helper.assertTrue(entities(helper, LithiumSpark.class).size() == 10, "a blade with 100 ticks left still threw sparks");
        helper.assertTrue(Integer.valueOf(100).equals(held(other).get(TFMGDataComponents.LITHIUM_BLADE_TIMER)), "a refused burst spent burn time");
        helper.succeed();
    }

    /** Burnt out in the off hand, the blade turns back into the unlit blade in that hand and touches nothing else. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 200)
    public static void litBladeBurnsOutInPlace(GameTestHelper helper) {
        Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
        ItemStack lit = TFMGItems.LIT_LITHIUM_BLADE.asStack();
        lit.set(TFMGDataComponents.LITHIUM_BLADE_TIMER, 40);
        player.setItemInHand(InteractionHand.OFF_HAND, lit);
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 5));
        helper.onEachTick(() -> player.getInventory().tick());
        helper.succeedWhen(() -> {
            helper.assertTrue(player.getOffhandItem().is(TFMGItems.LITHIUM_BLADE.get()), "the off hand holds " + player.getOffhandItem());
            helper.assertTrue(player.getInventory().getItem(0).is(Items.DIRT) && player.getInventory().getItem(0).getCount() == 5,
                    "the first hotbar slot changed to " + player.getInventory().getItem(0));
            int blades = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(TFMGItems.LITHIUM_BLADE.get()) || stack.is(TFMGItems.LIT_LITHIUM_BLADE.get()))
                    blades += stack.getCount();
            }
            helper.assertTrue(blades == 1, blades + " blades in the inventory");
        });
    }

    /** A lit blade hit sets seven seconds of hellfire, a second hit stacks it, and each hit wears the blade by 2. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void litBladeHitSetsHellfire(GameTestHelper helper) {
        Mob pig = target(helper, new Vec3(2.5, 2, 2.5));
        Tester player = player(helper, new Vec3(2.5, 2, 0.8), 0, 0);
        ItemStack lit = TFMGItems.LIT_LITHIUM_BLADE.asStack();
        lit.set(TFMGDataComponents.LITHIUM_BLADE_TIMER, 2000);
        player.setItemInHand(InteractionHand.MAIN_HAND, lit);
        player.attack(pig);
        MobEffectInstance hellfire = pig.getEffect(TFMGMobEffects.HELLFIRE);
        helper.assertTrue(hellfire != null && hellfire.getDuration() >= 140, "one hit gave hellfire " + hellfire);
        helper.assertTrue(held(player).getDamageValue() == 2, "one hit wore the lit blade by " + held(player).getDamageValue() + ", 2 expected");
        pig.invulnerableTime = 0;
        player.attack(pig);
        hellfire = pig.getEffect(TFMGMobEffects.HELLFIRE);
        helper.assertTrue(hellfire != null && hellfire.getDuration() > 200, "a second hit did not stack hellfire: " + hellfire);
        helper.assertTrue(held(player).getDamageValue() == 4, "two hits wore the lit blade by " + held(player).getDamageValue() + ", 4 expected");
        helper.succeed();
    }

    // ======================================================= tools by tier

    private static final Map<String, String> TOOL_TIER = Map.of("steel", "diamond", "aluminum", "iron", "lead", "stone");

    @GameTestGenerator
    public static List<TestFunction> toolTiers() {
        List<TestFunction> tests = new ArrayList<>();
        for (String material : List.of("steel", "aluminum", "lead"))
            tests.add(TFMGGameTestUtil.test(BATCH, "tools." + material, TFMGGameTestUtil.PLATFORM, 100, helper -> toolset(helper, material)));
        return tests;
    }

    private static Item tool(String name) {
        Item item = BuiltInRegistries.ITEM.get(TFMG.asResource(name));
        if (item == Items.AIR)
            throw new IllegalStateException("no item tfmg:" + name);
        return item;
    }

    private static double attribute(ItemStack stack, Holder<Attribute> attribute) {
        double total = 0;
        for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY).modifiers())
            if (entry.attribute().equals(attribute))
                total += entry.modifier().amount();
        return total;
    }

    /**
     * One material's five tools: durability and mining speed from the tier,
     * the harvest level the tier promises, a real block broken by a player,
     * the right-click actions of axe, shovel and hoe, attack speeds like
     * vanilla tools, and the wear and effect of a hit.
     */
    private static void toolset(GameTestHelper helper, String material) {
        TFMGTiers tier = TFMGTiers.valueOf(material.toUpperCase());
        Item sword = tool(material + "_sword"), pickaxe = tool(material + "_pickaxe"), axe = tool(material + "_axe"),
                shovel = tool(material + "_shovel"), hoe = tool(material + "_hoe");
        List<String> problems = new ArrayList<>();

        for (Item item : List.of(sword, pickaxe, axe, shovel, hoe))
            if (new ItemStack(item).getMaxDamage() != tier.getUses())
                problems.add(item + " lasts " + new ItemStack(item).getMaxDamage() + " uses, the tier says " + tier.getUses());

        Map<Item, BlockState> mines = Map.of(pickaxe, Blocks.STONE.defaultBlockState(), axe, Blocks.OAK_LOG.defaultBlockState(),
                shovel, Blocks.DIRT.defaultBlockState(), hoe, Blocks.HAY_BLOCK.defaultBlockState());
        mines.forEach((item, state) -> {
            float speed = new ItemStack(item).getDestroySpeed(state);
            if (speed != tier.getSpeed())
                problems.add(item + " mines " + state.getBlock() + " at " + speed + ", the tier says " + tier.getSpeed());
        });

        // Harvest level: steel like diamond, aluminum like iron, lead like stone.
        String level = TOOL_TIER.get(material);
        ItemStack pick = new ItemStack(pickaxe);
        boolean iron = pick.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState());
        boolean diamond = pick.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState());
        boolean obsidian = pick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState());
        boolean expectedDiamond = !level.equals("stone");
        boolean expectedObsidian = level.equals("diamond");
        if (!iron || diamond != expectedDiamond || obsidian != expectedObsidian)
            problems.add(pickaxe + " harvests iron ore " + iron + ", diamond ore " + diamond + ", obsidian " + obsidian
                    + " where a " + level + " pickaxe would " + true + "/" + expectedDiamond + "/" + expectedObsidian);

        // Attack speed modifiers as on TFMG's 1.20.1 tools (and every vanilla tool): all slower than a bare hand.
        Map<Item, Double> speeds = Map.of(sword, -2.4, pickaxe, -2.8, axe, -3.2, shovel, -3.0, hoe, -3.0);
        speeds.forEach((item, expected) -> {
            double speed = attribute(new ItemStack(item), Attributes.ATTACK_SPEED);
            if (Math.abs(speed - expected) > 1e-4)
                problems.add(item + " has an attack speed modifier of " + speed + ", " + expected + " expected");
        });

        // A player breaks a block of lithium (needs an iron pickaxe) with the pickaxe.
        BlockPos lithium = new BlockPos(1, 2, 1);
        helper.setBlock(lithium, TFMGBlocks.LITHIUM_BLOCK.get());
        Tester player = player(helper, new Vec3(2.5, 2, 3.5), 180, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(pickaxe));
        boolean broken = player.gameMode.destroyBlock(helper.absolutePos(lithium));
        if (!broken || !helper.getBlockState(lithium).isAir())
            problems.add("the player could not break a lithium block with " + pickaxe);
        if (held(player).getDamageValue() != 1)
            problems.add("breaking a block wore " + pickaxe + " by " + held(player).getDamageValue());
        boolean drops = !level.equals("stone");

        // Right-click actions: strip a log, flatten a path, till the soil.
        BlockPos log = new BlockPos(0, 2, 4), grass = new BlockPos(2, 1, 4), dirt = new BlockPos(4, 1, 4);
        helper.setBlock(log, Blocks.OAK_LOG);
        helper.setBlock(grass, Blocks.GRASS_BLOCK);
        helper.setBlock(dirt, Blocks.DIRT);
        click(helper, player, log, Direction.UP, new ItemStack(axe));
        if (!helper.getBlockState(log).is(Blocks.STRIPPED_OAK_LOG) || held(player).getDamageValue() != 1)
            problems.add(axe + " left " + helper.getBlockState(log).getBlock() + ", worn by " + held(player).getDamageValue());
        InteractionResult flatten = click(helper, player, grass, Direction.UP, new ItemStack(shovel));
        if (!helper.getBlockState(grass).is(Blocks.DIRT_PATH) || held(player).getDamageValue() != 1)
            problems.add(shovel + " left " + helper.getBlockState(grass).getBlock() + ", worn by " + held(player).getDamageValue()
                    + " (" + flatten + ", above " + helper.getBlockState(grass.above()) + ")");
        InteractionResult till = click(helper, player, dirt, Direction.UP, new ItemStack(hoe));
        if (!helper.getBlockState(dirt).is(Blocks.FARMLAND) || held(player).getDamageValue() != 1)
            problems.add(hoe + " left " + helper.getBlockState(dirt).getBlock() + ", worn by " + held(player).getDamageValue()
                    + " (" + till + ", above " + helper.getBlockState(dirt.above()) + ")");

        // Hits: swords wear 1 (lead 2, and poisons), axes wear 2 (lead axes poison longer).
        Mob pig = target(helper, new Vec3(2.5, 2, 2.0));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(sword));
        player.attack(pig);
        int swordWear = material.equals("lead") ? 2 : 1;
        if (held(player).getDamageValue() != swordWear)
            problems.add("a hit wore " + sword + " by " + held(player).getDamageValue() + ", " + swordWear + " expected");
        checkPoison(pig, material.equals("lead") ? 100 : 0, sword, problems);
        pig.removeAllEffects();
        pig.invulnerableTime = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(axe));
        player.attack(pig);
        if (held(player).getDamageValue() != 2)
            problems.add("a hit wore " + axe + " by " + held(player).getDamageValue() + ", 2 expected");
        checkPoison(pig, material.equals("lead") ? 160 : 0, axe, problems);

        helper.runAfterDelay(2, () -> {
            int dropped = dropped(helper, TFMGBlocks.LITHIUM_BLOCK.asItem());
            if (drops != (dropped == 1))
                problems.add("a " + level + " pickaxe dropped " + dropped + " lithium blocks, " + (drops ? 1 : 0) + " expected");
            helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
            helper.succeed();
        });
    }

    private static void checkPoison(Mob target, int duration, Item weapon, List<String> problems) {
        MobEffectInstance poison = target.getEffect(MobEffects.POISON);
        int actual = poison == null ? 0 : poison.getDuration();
        if (duration == 0 ? actual != 0 : actual < duration)
            problems.add(weapon + " poisoned for " + actual + " ticks, " + duration + " expected");
    }

    /** The lithium blades carry the steel tier. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void lithiumBladesUseTheSteelTier(GameTestHelper helper) {
        for (Item blade : List.of(TFMGItems.LITHIUM_BLADE.get(), TFMGItems.LIT_LITHIUM_BLADE.get()))
            helper.assertTrue(new ItemStack(blade).getMaxDamage() == TFMGTiers.STEEL.getUses(), blade + " lasts " + new ItemStack(blade).getMaxDamage());
        helper.succeed();
    }

    // ===================================================== lithium torch

    /** A lithium torch stands on floors, walls and ceilings, glows at 14, takes water in, and drops when its support goes. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 100)
    public static void lithiumTorchPlacement(GameTestHelper helper) {
        Block torch = TFMGBlocks.LITHIUM_TORCH.get();
        BlockPos wall = new BlockPos(0, 2, 3), ceiling = new BlockPos(4, 4, 0), wet = new BlockPos(3, 2, 3);
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(ceiling, Blocks.STONE);
        // A water source in a stone basin, so it cannot run off the platform.
        for (Direction side : Direction.Plane.HORIZONTAL)
            helper.setBlock(wet.relative(side), Blocks.STONE);
        helper.setBlock(wet, Blocks.WATER);
        Tester player = player(helper, new Vec3(1.5, 2, 0.5), 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(torch, 8));

        List<InteractionResult> results = List.of(click(helper, player, new BlockPos(1, 1, 1), Direction.UP),
                click(helper, player, wall, Direction.EAST),
                click(helper, player, ceiling, Direction.DOWN),
                click(helper, player, wet.below(), Direction.UP));
        helper.assertTrue(results.stream().allMatch(InteractionResult::consumesAction), "the placements answered " + results);

        BlockPos floor = new BlockPos(1, 2, 1), onWall = new BlockPos(1, 2, 3), hanging = new BlockPos(4, 3, 0);
        helper.assertBlockProperty(floor, LithiumTorchBlock.FACING, Direction.UP);
        helper.assertBlockProperty(onWall, LithiumTorchBlock.FACING, Direction.EAST);
        helper.assertBlockProperty(hanging, LithiumTorchBlock.FACING, Direction.DOWN);
        helper.assertBlockProperty(floor, LithiumTorchBlock.WATERLOGGED, false);
        helper.assertBlockProperty(wet, LithiumTorchBlock.WATERLOGGED, true);
        helper.assertTrue(held(player).getCount() == 4, "placing four torches left " + held(player).getCount() + " of 8");

        helper.setBlock(wall, Blocks.AIR);
        helper.assertTrue(!helper.getBlockState(onWall).is(torch), "the wall torch stayed without its wall");
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(floor)) == 14,
                    "the torch lights its block at " + helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(floor)));
            helper.assertTrue(dropped(helper, torch.asItem()) == 1, "the torch that lost its wall dropped " + dropped(helper, torch.asItem()));
        });
    }

    // ======================================================= potato cannons

    /** Barrel offset of both cannons (ShootableGadgetItemMethods#getGunBarrelVec). */
    private static final Vec3 CANNON_BARREL = new Vec3(.75f, -0.15f, 1.5f);

    /** The advanced cannon fires one napalm potato from the inventory; it bursts into fire where it lands. */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void advancedCannonFiresNapalm(GameTestHelper helper) {
        Mob pig = target(helper, new Vec3(6.5, 2, 7.5));
        Tester player = player(helper, new Vec3(6.5, 2, 1.5), 0, 0);
        player.getInventory().setItem(7, new ItemStack(TFMGItems.NAPALM_POTATO.get(), 3));
        aimBarrel(player, centre(pig), CANNON_BARREL);
        InteractionResult shot = useInAir(helper, player, TFMGItems.ADVANCED_POTATO_CANNON.asStack());
        helper.assertTrue(shot.consumesAction(), "the cannon answered " + shot);
        helper.assertTrue(entities(helper, NapalmPotato.class).size() == 1, entities(helper, NapalmPotato.class).size() + " napalm potatoes in the air");
        helper.assertTrue(player.getInventory().getItem(7).getCount() == 2, "a shot used " + (3 - player.getInventory().getItem(7).getCount()) + " potatoes");
        helper.assertTrue(held(player).getDamageValue() == 1, "a shot wore the cannon by " + held(player).getDamageValue());
        helper.assertTrue(player.getCooldowns().isOnCooldown(TFMGItems.ADVANCED_POTATO_CANNON.get()), "the cannon has no reload time");

        Tester dry = player(helper, new Vec3(3.5, 2, 1.5), 0, 0);
        InteractionResult noAmmo = useInAir(helper, dry, TFMGItems.ADVANCED_POTATO_CANNON.asStack());
        helper.assertTrue(!noAmmo.consumesAction() && held(dry).getDamageValue() == 0, "a cannon without ammo answered " + noAmmo);
        helper.assertTrue(entities(helper, NapalmPotato.class).size() == 1, "a cannon without ammo fired");

        helper.succeedWhen(() -> {
            helper.assertTrue(entities(helper, NapalmPotato.class).isEmpty(), "the napalm potato is still flying");
            helper.assertTrue(hurt(pig), "the napalm potato did not hurt the pig it was aimed at");
        });
    }

    /** The quad cannon fires four projectiles for one piece of ammo. */
    @GameTest(template = LARGE, batch = BATCH)
    public static void quadCannonFiresFour(GameTestHelper helper) {
        Tester player = player(helper, new Vec3(6.5, 2, 1.5), 0, 50);
        player.getInventory().setItem(7, new ItemStack(Items.POTATO, 5));
        InteractionResult shot = useInAir(helper, player, TFMGItems.QUAD_POTATO_CANNON.asStack());
        helper.assertTrue(shot.consumesAction(), "the cannon answered " + shot);
        int projectiles = entities(helper, PotatoProjectileEntity.class).size();
        helper.assertTrue(projectiles == 4, projectiles + " potatoes fired, 4 expected");
        helper.assertTrue(player.getInventory().getItem(7).getCount() == 4, "a shot used " + (5 - player.getInventory().getItem(7).getCount()) + " potatoes");
        helper.assertTrue(held(player).getDamageValue() == 1, "a shot wore the cannon by " + held(player).getDamageValue());
        helper.succeed();
    }

    /**
     * With Potato Recovery, only one of the four projectiles may give the
     * ammo back, as Create's own cannon does for split shots: each of them
     * could, which turned one potato into up to four.
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void quadCannonRecoversOneAmmoAtMost(GameTestHelper helper) {
        Holder<Enchantment> recovery = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(AllEnchantments.POTATO_RECOVERY);
        ItemStack cannon = TFMGItems.QUAD_POTATO_CANNON.asStack();
        cannon.enchant(recovery, 3);
        Tester player = player(helper, new Vec3(6.5, 2, 1.5), 0, 50);
        player.getInventory().setItem(7, new ItemStack(Items.POTATO, 5));
        useInAir(helper, player, cannon);
        List<PotatoProjectileEntity> fired = entities(helper, PotatoProjectileEntity.class);
        int recovering = 0;
        for (PotatoProjectileEntity potato : fired)
            if (potato.saveWithoutId(new CompoundTag()).getFloat("Recovery") > 0)
                recovering++;
        helper.assertTrue(fired.size() == 4, fired.size() + " potatoes fired");
        helper.assertTrue(recovering == 1, recovering + " of the 4 potatoes can give the ammo back, 1 expected");
        helper.succeed();
    }

    // ======================================================= throwables

    /** A pipe bomb is thrown, used up, and blows up near a mob. */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void pipeBombThrownAndExplodes(GameTestHelper helper) {
        Mob pig = target(helper, new Vec3(6.5, 2, 6.5));
        Tester player = player(helper, new Vec3(6.5, 2, 1.5), 0, 20);
        useInAir(helper, player, new ItemStack(TFMGItems.PIPE_BOMB.get(), 3));
        helper.assertTrue(held(player).getCount() == 2, "throwing left " + held(player).getCount() + " of 3 pipe bombs");
        helper.assertTrue(entities(helper, PipeBomb.class).size() == 1, entities(helper, PipeBomb.class).size() + " pipe bombs in the air");
        helper.assertTrue(player.getCooldowns().isOnCooldown(TFMGItems.PIPE_BOMB.get()), "no cooldown after a throw");
        helper.succeedWhen(() -> {
            helper.assertTrue(entities(helper, PipeBomb.class).isEmpty(), "the pipe bomb has not gone off");
            helper.assertTrue(hurt(pig), "the explosion did not reach the pig");
        });
    }

    @GameTestGenerator
    public static List<TestFunction> grenades() {
        List<TestFunction> tests = new ArrayList<>();
        for (String name : List.of("thermite_grenade", "zinc_grenade", "copper_grenade"))
            tests.add(TFMGGameTestUtil.test(BATCH, "grenade." + name, TFMGGameTestUtil.PLATFORM_LARGE, 200, helper -> grenade(helper, name)));
        return tests;
    }

    private static EntityType<? extends Spark> sparkOf(ThermiteGrenade.ChemicalColor color) {
        return switch (color) {
            case GREEN -> TFMGEntityTypes.GREEN_SPARK.get();
            case BLUE -> TFMGEntityTypes.BLUE_SPARK.get();
            default -> TFMGEntityTypes.SPARK.get();
        };
    }

    /**
     * A grenade is thrown, used up, and bursts near a mob into twenty sparks
     * of its own colour with an explosion that hurts the mob. Where those
     * sparks set fire is checked by {@link #sparksSetTheirOwnFire}: the
     * grenade's blast flings them several blocks high and away, into the
     * barriers that enclose a game test.
     */
    private static void grenade(GameTestHelper helper, String name) {
        ThermiteGrenadeItem item = (ThermiteGrenadeItem) tool(name);
        Mob pig = target(helper, new Vec3(6.5, 2, 5.5));
        Tester player = player(helper, new Vec3(6.5, 2, 1.5), 0, 20);
        useInAir(helper, player, new ItemStack(item, 2));
        helper.assertTrue(held(player).getCount() == 1, "throwing left " + held(player).getCount() + " of 2");
        helper.assertTrue(player.getCooldowns().isOnCooldown(item), "no cooldown after a throw");
        List<ThermiteGrenade> thrown = entities(helper, ThermiteGrenade.class);
        helper.assertTrue(thrown.size() == 1 && thrown.get(0).flameColor == item.flameColor,
                "thrown: " + thrown + (thrown.isEmpty() ? "" : " with colour " + thrown.get(0).flameColor));
        EntityType<?> sparkType = sparkOf(item.flameColor);
        Set<java.util.UUID> burst = new java.util.HashSet<>();
        helper.onEachTick(() -> {
            for (Spark spark : entities(helper, Spark.class))
                if (spark.tickCount < 3 && spark.getType() == sparkType)
                    burst.add(spark.getUUID());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(entities(helper, ThermiteGrenade.class).isEmpty(), "the grenade has not gone off");
            helper.assertTrue(hurt(pig), "the explosion did not reach the pig");
            helper.assertTrue(burst.size() == 20, "the grenade burst into " + burst.size() + " " + BuiltInRegistries.ENTITY_TYPE.getKey(sparkType) + ", 20 expected");
        });
    }

    /** Each kind of spark landing on the floor sets its own kind of fire; lithium sparks and dry ice set none. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 100)
    public static void sparksSetTheirOwnFire(GameTestHelper helper) {
        Map<EntityType<? extends Spark>, Block> fires = new java.util.LinkedHashMap<>();
        fires.put(TFMGEntityTypes.SPARK.get(), Blocks.FIRE);
        fires.put(TFMGEntityTypes.GREEN_SPARK.get(), TFMGColoredFires.GREEN_FIRE.get());
        fires.put(TFMGEntityTypes.BLUE_SPARK.get(), TFMGColoredFires.BLUE_FIRE.get());
        fires.put(TFMGEntityTypes.LITHIUM_SPARK.get(), Blocks.AIR);
        fires.put(TFMGEntityTypes.DRY_ICE_FLAKE.get(), Blocks.AIR);
        Map<BlockPos, Block> expected = new java.util.LinkedHashMap<>();
        int x = 0;
        for (Map.Entry<EntityType<? extends Spark>, Block> e : fires.entrySet()) {
            Spark spark = e.getKey().create(helper.getLevel());
            Vec3 start = helper.absoluteVec(new Vec3(x + 0.5, 3.5, 2.5));
            spark.setPos(start.x, start.y, start.z);
            spark.setDeltaMovement(0, -0.5, 0);
            helper.getLevel().addFreshEntity(spark);
            expected.put(new BlockPos(x, 2, 2), e.getValue());
            x++;
        }
        boolean[] seen = new boolean[expected.size()];
        helper.onEachTick(() -> {
            int i = 0;
            for (Map.Entry<BlockPos, Block> e : expected.entrySet()) {
                if (e.getValue() != Blocks.AIR && helper.getBlockState(e.getKey()).is(e.getValue()))
                    seen[i] = true;
                i++;
            }
        });
        helper.runAfterDelay(20, () -> {
            List<String> problems = new ArrayList<>();
            int i = 0;
            for (Map.Entry<BlockPos, Block> e : expected.entrySet()) {
                BlockState there = helper.getBlockState(e.getKey());
                if (e.getValue() == Blocks.AIR ? !there.isAir() : !seen[i])
                    problems.add(e.getKey() + " holds " + there.getBlock() + ", " + e.getValue() + " expected");
                i++;
            }
            helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
            helper.succeed();
        });
    }

    /** A grenade in flight keeps its colour through a save, as a chunk unload saves it. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void grenadeColourSurvivesASave(GameTestHelper helper) {
        Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
        List<String> problems = new ArrayList<>();
        Map<ThermiteGrenade.ChemicalColor, EntityType<ThermiteGrenade>> types = Map.of(
                ThermiteGrenade.ChemicalColor.BASE, TFMGEntityTypes.THERMITE_GRENADE.get(),
                ThermiteGrenade.ChemicalColor.GREEN, TFMGEntityTypes.ZINC_GRENADE.get(),
                ThermiteGrenade.ChemicalColor.BLUE, TFMGEntityTypes.COPPER_GRENADE.get());
        types.forEach((color, type) -> {
            ThermiteGrenade grenade = new ThermiteGrenade(helper.getLevel(), player, color, type);
            CompoundTag saved = new CompoundTag();
            grenade.save(saved);
            Entity loaded = EntityType.create(saved, helper.getLevel()).orElse(null);
            if (!(loaded instanceof ThermiteGrenade reloaded) || reloaded.flameColor != color)
                problems.add(BuiltInRegistries.ENTITY_TYPE.getKey(type) + " came back as "
                        + (loaded instanceof ThermiteGrenade g ? g.flameColor : loaded));
        });
        helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
        helper.succeed();
    }

    /** Flint and steel on a napalm bomb primes it; it goes off and sets fire around. */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 250)
    public static void napalmBombPrimesAndBurns(GameTestHelper helper) {
        BlockPos bomb = new BlockPos(6, 2, 6);
        helper.setBlock(bomb, TFMGBlocks.NAPALM_BOMB.get());
        Mob pig = target(helper, new Vec3(8.5, 2, 6.5));
        Tester player = player(helper, new Vec3(6.5, 2, 3.5), 0, 30);
        InteractionResult lit = click(helper, player, bomb, Direction.NORTH, new ItemStack(Items.FLINT_AND_STEEL));
        helper.assertTrue(lit.consumesAction(), "flint and steel on the bomb answered " + lit);
        helper.assertTrue(helper.getBlockState(bomb).isAir(), "the primed bomb is still a block");
        helper.assertTrue(entities(helper, NapalmBombEntity.class).size() == 1, "no primed napalm bomb");
        helper.assertTrue(held(player).getDamageValue() == 1, "the flint and steel was not used");
        helper.succeedWhen(() -> {
            helper.assertTrue(entities(helper, NapalmBombEntity.class).isEmpty(), "the napalm bomb has not gone off");
            helper.assertTrue(hurt(pig), "the napalm bomb did not reach the pig");
        });
    }

    // ===================================================== right-click tools

    private static BlockPos steelPipe(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, TFMGPipes.PIPES.get(TFMGPipes.PipeMaterial.STEEL).getPipe().get().defaultBlockState());
        return pos;
    }

    /**
     * The screwdriver locks a pipe so a new neighbour does not join it, the
     * lock survives a reload, and a second turn unlocks and reconnects it.
     * Each turn wears the screwdriver by one.
     */
    @GameTest(template = SMALL, batch = BATCH)
    public static void screwdriverLocksPipes(GameTestHelper helper) {
        // A straight north-south run, so the middle pipe has no east side.
        BlockPos pipe = steelPipe(helper, new BlockPos(2, 2, 2));
        steelPipe(helper, pipe.north());
        steelPipe(helper, pipe.south());
        TFMGOilEngineTestKit.connect(helper, List.of(pipe.north(), pipe, pipe.south()));
        helper.assertTrue(!helper.getBlockState(pipe).getValue(BlockStateProperties.EAST)
                && helper.getBlockState(pipe).getValue(BlockStateProperties.NORTH), "the run did not form: " + helper.getBlockState(pipe));
        Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
        InteractionResult turn = click(helper, player, pipe, Direction.UP, TFMGItems.SCREWDRIVER.asStack());
        helper.assertTrue(turn.consumesAction(), "the screwdriver answered " + turn);
        TFMGPipeBlockEntity be = helper.getBlockEntity(pipe);
        helper.assertTrue(be.locked, "the pipe did not lock");
        helper.assertTrue(held(player).getDamageValue() == 1, "a turn wore the screwdriver by " + held(player).getDamageValue());

        TFMGPowerKit.unloadAndReload(helper, List.of(pipe));
        TFMGPipeBlockEntity reloaded = helper.getBlockEntity(pipe);
        helper.assertTrue(reloaded.locked, "the lock was lost on reload");

        steelPipe(helper, pipe.east());
        TFMGOilEngineTestKit.connect(helper, List.of(pipe.east(), pipe));
        helper.assertTrue(!helper.getBlockState(pipe).getValue(BlockStateProperties.EAST), "a locked pipe joined its new neighbour");
        click(helper, player, pipe, Direction.UP);
        TFMGPipeBlockEntity unlocked = helper.getBlockEntity(pipe);
        helper.assertTrue(!unlocked.locked, "a second turn did not unlock the pipe");
        helper.assertTrue(helper.getBlockState(pipe).getValue(BlockStateProperties.EAST), "the unlocked pipe did not join its neighbour");
        helper.assertTrue(held(player).getDamageValue() == 2, "two turns wore the screwdriver by " + held(player).getDamageValue());
        helper.succeed();
    }

    /** The oil hammer tells the player the reserves of the deposit under the clicked block. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void oilHammerReadsReserves(GameTestHelper helper) {
        BlockPos deposit = new BlockPos(2, 1, 2);
        helper.setBlock(deposit, TFMGBlocks.OIL_DEPOSIT.get());
        long key = helper.absolutePos(deposit).asLong();
        TFMG.DEPOSITS.addDeposit(helper.getLevel(), key);
        FluidReservoir reservoir = TFMG.DEPOSITS.getReservoirFor(key);
        helper.assertTrue(reservoir != null, "the deposit has no reservoir");
        helper.setBlock(deposit.above(), Blocks.STONE);
        Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
        InteractionResult knock = click(helper, player, deposit.above(), Direction.UP, TFMGItems.OIL_HAMMER.asStack());
        helper.assertTrue(knock.consumesAction(), "the hammer answered " + knock);
        String expected = Component.translatable("tfmg.oil_hammer.reserves", reservoir.oilReserves).getString();
        helper.assertTrue(player.said().contains(String.valueOf(reservoir.oilReserves)),
                "the player was told [" + player.said() + "], expected the reserves " + expected);
        TFMG.DEPOSITS.removeDeposit(key);
        helper.succeed();
    }

    /** The configuration wrench stores the group picked in its screen, keeps it through a save and a sync, and leaves blocks alone. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void configurationWrenchStoresItsGroup(GameTestHelper helper) {
        Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, TFMGItems.CONFIGURATION_WRENCH.asStack());
        new ElectriciansWrenchPacket(7, InteractionHand.MAIN_HAND).handle(player);
        ItemStack wrench = held(player);
        helper.assertTrue(Integer.valueOf(7).equals(wrench.get(TFMGDataComponents.CONFIGURATION_WRENCH_NUMBER)), "the wrench holds group " + wrench.get(TFMGDataComponents.CONFIGURATION_WRENCH_NUMBER));

        var registries = helper.getLevel().registryAccess();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        Tag saved = ItemStack.CODEC.encodeStart(ops, wrench).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        ItemStack.STREAM_CODEC.encode(buf, wrench);
        ItemStack synced = ItemStack.STREAM_CODEC.decode(buf);
        buf.release();
        helper.assertTrue(Integer.valueOf(7).equals(loaded.get(TFMGDataComponents.CONFIGURATION_WRENCH_NUMBER))
                && Integer.valueOf(7).equals(synced.get(TFMGDataComponents.CONFIGURATION_WRENCH_NUMBER)), "the group was lost through a save or a sync");

        // Another item in hand: the packet must not touch it.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        new ElectriciansWrenchPacket(3, InteractionHand.MAIN_HAND).handle(player);
        helper.assertTrue(!held(player).has(TFMGDataComponents.CONFIGURATION_WRENCH_NUMBER), "the packet wrote a group on a stick");

        BlockPos generator = new BlockPos(2, 2, 2);
        TFMGPowerKit.creativeGenerator(helper, generator, 100);
        BlockState before = helper.getBlockState(generator);
        CompoundTag data = helper.getBlockEntity(generator).saveWithoutMetadata(registries);
        player.setShiftKeyDown(true);
        useInAir(helper, player, wrench);
        player.setShiftKeyDown(false);
        click(helper, player, generator, Direction.UP, wrench);
        helper.assertTrue(helper.getBlockState(generator) == before, "the wrench changed the block");
        helper.assertTrue(TFMGGameTestUtil.diffKeys(data, helper.getBlockEntity(generator).saveWithoutMetadata(registries)).isEmpty(),
                "the wrench changed the block's data");
        helper.succeed();
    }

    /**
     * The readings a multimeter overlay shows on a powered load (the overlay
     * itself is drawn on the client only): voltage, resistance, current and
     * power, consistent with Ohm's law, and the multimeter is recognised in
     * either hand.
     */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 200)
    public static void multimeterReadsTheNetwork(GameTestHelper helper) {
        BlockPos generator = new BlockPos(1, 2, 2), load = new BlockPos(3, 2, 2);
        TFMGPowerKit.creativeGenerator(helper, generator, 100);
        TFMGPowerKit.hub(helper, new BlockPos(2, 2, 2));
        TFMGPowerKit.place(helper, load, TFMGBlocks.RESISTOR.get(), Direction.EAST);
        Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
        player.setItemInHand(InteractionHand.OFF_HAND, TFMGItems.MULTIMETER.asStack());
        helper.assertTrue(com.drmangotea.tfmg.content.electricity.measurement.MultimeterItem.isHeldByPlayer(player), "a multimeter in the off hand is not seen");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        for (var coloured : TFMGItems.MULTIMETERS.values()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, coloured.asStack());
            helper.assertTrue(com.drmangotea.tfmg.content.electricity.measurement.MultimeterItem.isHeldByPlayer(player), coloured.getId() + " is not seen as a multimeter");
        }
        helper.succeedWhen(() -> {
            IElectric resistor = TFMGPowerKit.electric(helper, load);
            int volts = resistor.getData().getVoltage();
            float ohms = resistor.resistance();
            helper.assertTrue(volts == 100, "the load is at " + volts + " V");
            helper.assertTrue(ohms > 0, "the load reads " + ohms + " ohm");
            helper.assertTrue(Math.abs(resistor.getCurrent() - volts / ohms) < 1e-3, "it reads " + resistor.getCurrent() + " A for " + volts + " V on " + ohms + " ohm");
            helper.assertTrue(Math.abs(resistor.getPowerUsage() - volts * volts / ohms) < 0.5, "it reads " + resistor.getPowerUsage() + " W");
            helper.assertTrue(TFMGTexts.voltage(volts).contains("100"), "the voltage is written " + TFMGTexts.voltage(volts));
        });
    }

    // ============================================= oil can, cooling bottle

    private static BlockPos engine(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, TFMGBlocks.REGULAR_ENGINE.get().defaultBlockState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.NORTH));
        return pos;
    }

    private static int amountOf(ItemStack stack) {
        return stack.getOrDefault(TFMGDataComponents.AMOUNT, 0);
    }

    /** The oil can fills from a tank of lubrication oil, then pours into an engine up to its 2000 mB. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void oilCanFillsAnEngine(GameTestHelper helper) {
        canFillsAnEngine(helper, TFMGItems.OIL_CAN.asStack(), still(TFMGFluids.LUBRICATION_OIL.get()), true);
    }

    /** The cooling fluid bottle does the same with cooling fluid. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void coolingBottleFillsAnEngine(GameTestHelper helper) {
        canFillsAnEngine(helper, TFMGItems.COOLING_FLUID_BOTTLE.asStack(), still(TFMGFluids.COOLING_FLUID.get()), false);
    }

    private static void canFillsAnEngine(GameTestHelper helper, ItemStack can, Fluid fluid, boolean oil) {
        BlockPos tank = new BlockPos(0, 2, 0), engine = engine(helper, new BlockPos(3, 2, 3));
        tankWith(helper, tank, fluid, 3000);
        helper.setBlock(new BlockPos(0, 2, 2), AllBlocks.FLUID_TANK.get());
        TFMGFactoryKit.fluids(helper, new BlockPos(0, 2, 2), null).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.runAfterDelay(5, () -> {
            Tester player = player(helper, new Vec3(2.5, 2, 1.5), 0, 0);
            click(helper, player, new BlockPos(0, 2, 2), Direction.UP, can);
            helper.assertTrue(amountOf(held(player)) == 0, "the " + can.getItem() + " took water: " + amountOf(held(player)));

            click(helper, player, tank, Direction.UP);
            helper.assertTrue(amountOf(held(player)) == 3000, "the can took " + amountOf(held(player)) + " mB from a 3000 mB tank");
            helper.assertTrue(TFMGFactoryKit.amount(helper, tank, fluid) == 0, "the tank kept " + TFMGFactoryKit.amount(helper, tank, fluid) + " mB");

            InteractionResult poured = click(helper, player, engine, Direction.UP);
            AbstractSmallEngineBlockEntity be = helper.getBlockEntity(engine);
            int inEngine = oil ? be.oil : be.coolingFluid;
            helper.assertTrue(poured.consumesAction(), "the engine answered " + poured);
            helper.assertTrue(inEngine == 2000 && amountOf(held(player)) == 1000,
                    "the engine got " + inEngine + " mB and the can kept " + amountOf(held(player)));
            helper.assertTrue(be.fuelTank.getFluidAmount() == 0, "the can poured into the fuel tank: " + be.fuelTank.getFluid().getAmount());
            helper.succeed();
        });
    }

    /**
     * Sneak-clicking an engine with a full can empties the can as its
     * tooltip says; it must not pour lubrication oil or cooling fluid into
     * the engine's fuel tank, which nothing can drain again.
     */
    @GameTest(template = SMALL, batch = BATCH)
    public static void sneakingCanNeverFuelsAnEngine(GameTestHelper helper) {
        BlockPos engine = engine(helper, new BlockPos(1, 2, 2)), other = engine(helper, new BlockPos(3, 2, 2));
        helper.runAfterDelay(5, () -> {
            List<String> problems = new ArrayList<>();
            int i = 0;
            for (ItemStack can : List.of(TFMGItems.OIL_CAN.asStack(), TFMGItems.COOLING_FLUID_BOTTLE.asStack())) {
                BlockPos pos = i++ == 0 ? engine : other;
                can.set(TFMGDataComponents.AMOUNT, 4000);
                Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
                player.setShiftKeyDown(true);
                click(helper, player, pos, Direction.UP, can);
                AbstractSmallEngineBlockEntity be = helper.getBlockEntity(pos);
                if (be.fuelTank.getFluidAmount() > 0)
                    problems.add(can.getItem() + " put " + be.fuelTank.getFluidAmount() + " mB of "
                            + BuiltInRegistries.FLUID.getKey(be.fuelTank.getFluid().getFluid()) + " in the fuel tank");
                if (amountOf(held(player)) != 0)
                    problems.add(can.getItem() + " kept " + amountOf(held(player)) + " mB, the tooltip says sneaking empties it");
            }
            helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
            helper.succeed();
        });
    }

    /** Sneaking with a full can over a tank pours into the tank. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void sneakingCanPoursIntoATank(GameTestHelper helper) {
        Fluid oil = still(TFMGFluids.LUBRICATION_OIL.get());
        BlockPos tank = new BlockPos(2, 2, 2);
        tankWith(helper, tank, null, 0);
        helper.runAfterDelay(2, () -> {
            ItemStack can = TFMGItems.OIL_CAN.asStack();
            can.set(TFMGDataComponents.AMOUNT, 2500);
            Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
            player.setShiftKeyDown(true);
            click(helper, player, tank, Direction.UP, can);
            helper.assertTrue(TFMGFactoryKit.amount(helper, tank, oil) == 2500 && amountOf(held(player)) == 0,
                    "the tank got " + TFMGFactoryKit.amount(helper, tank, oil) + " mB and the can kept " + amountOf(held(player)));
            helper.succeed();
        });
    }

    /** A spout fills an oil can with lubrication oil and nothing else. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void spoutFillsAnOilCan(GameTestHelper helper) {
        var level = helper.getLevel();
        FluidStack oil = new FluidStack(still(TFMGFluids.LUBRICATION_OIL.get()), 1000);
        ItemStack can = TFMGItems.OIL_CAN.asStack();
        int needed = FillingBySpout.getRequiredAmountForItem(level, can, oil);
        helper.assertTrue(needed > 0, "a spout cannot fill an oil can (" + needed + ")");
        ItemStack filled = FillingBySpout.fillItem(level, needed, can, oil);
        helper.assertTrue(filled.is(TFMGItems.OIL_CAN.get()) && amountOf(filled) == needed, "the spout made " + filled + " holding " + amountOf(filled));
        helper.assertTrue(FillingBySpout.getRequiredAmountForItem(level, TFMGItems.OIL_CAN.asStack(), new FluidStack(still(TFMGFluids.DIESEL.get()), 1000)) <= 0,
                "a spout would fill an oil can with diesel");
        helper.succeed();
    }

    /** Sneaking with a bound transmission in the air forgets the controller it was bound to. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void transmissionForgetsItsControllerWhenSneaking(GameTestHelper helper) {
        BlockPos controller = new BlockPos(2, 2, 2);
        helper.setBlock(controller, TFMGBlocks.ENGINE_CONTROLLER.get());
        Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
        click(helper, player, controller, Direction.UP, TFMGItems.TRANSMISSION.asStack());
        helper.assertTrue(Long.valueOf(helper.absolutePos(controller).asLong()).equals(held(player).get(TFMGDataComponents.POSITION)),
                "clicking the controller bound the transmission to " + held(player).get(TFMGDataComponents.POSITION));
        useInAir(helper, player, held(player));
        helper.assertTrue(held(player).has(TFMGDataComponents.POSITION), "a plain right click forgot the controller");
        player.setShiftKeyDown(true);
        player.setPose(Pose.CROUCHING);
        useInAir(helper, player, held(player));
        helper.assertTrue(!held(player).has(TFMGDataComponents.POSITION), "sneaking did not forget the controller");
        helper.succeed();
    }

    // ===================================================== 1.3.0 helpers

    /** The Factory Inspector on a machine sends the player a report with a header and readings, then cools down. */
    @GameTest(template = SMALL, batch = BATCH, timeoutTicks = 200)
    public static void inspectorReportsOnAMachine(GameTestHelper helper) {
        BlockPos generator = new BlockPos(1, 2, 2), load = new BlockPos(3, 2, 2);
        TFMGPowerKit.creativeGenerator(helper, generator, 100);
        TFMGPowerKit.hub(helper, new BlockPos(2, 2, 2));
        TFMGPowerKit.place(helper, load, TFMGBlocks.RESISTOR.get(), Direction.EAST);
        helper.runAfterDelay(20, () -> {
            Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
            InteractionResult result = click(helper, player, load, Direction.UP, TFMGItems.FACTORY_INSPECTOR.asStack());
            helper.assertTrue(result.consumesAction(), "the inspector answered " + result);
            helper.assertTrue(player.messages.size() >= 2, "the inspector sent " + player.messages.size() + " lines: " + player.said());
            String header = Component.translatable("tfmg.inspector.header", TFMGBlocks.RESISTOR.get().getName()).getString();
            helper.assertTrue(player.messages.get(0).getString().equals(header), "the report starts with " + player.messages.get(0).getString());
            helper.assertTrue(!player.said().contains("tfmg.inspector.") && !player.said().contains("%s"), "raw text in the report: " + player.said());
            helper.assertTrue(player.getCooldowns().isOnCooldown(TFMGItems.FACTORY_INSPECTOR.get()), "the inspector has no cooldown");
            helper.assertTrue(helper.getBlockState(load).is(TFMGBlocks.RESISTOR.get()), "the inspector changed the block");
            helper.succeed();
        });
    }

    /** In the air the inspector sends the progression checklist; a client-side player gets nothing and nothing breaks. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void inspectorInTheAirShowsProgress(GameTestHelper helper) {
        Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
        InteractionResult result = useInAir(helper, player, TFMGItems.FACTORY_INSPECTOR.asStack());
        helper.assertTrue(result.consumesAction(), "the inspector answered " + result);
        helper.assertTrue(player.messages.size() >= 20, "the checklist has " + player.messages.size() + " lines");
        helper.assertTrue(!player.said().contains("tfmg.inspector.") && !player.said().contains("%s"), "raw text in the checklist");

        Player mock = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        mock.setItemInHand(InteractionHand.MAIN_HAND, TFMGItems.FACTORY_INSPECTOR.asStack());
        held(mock).use(helper.getLevel(), mock, InteractionHand.MAIN_HAND);
        helper.succeed();
    }

    /** Using the Handbook on the server opens it without error; a first login hands out one, and only one. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void handbookOpensAndIsGivenOnce(GameTestHelper helper) {
        Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
        InteractionResult result = useInAir(helper, player, TFMGItems.FACTORY_GUIDE.asStack());
        helper.assertTrue(result.consumesAction(), "the handbook answered " + result);
        helper.assertTrue(held(player).is(TFMGItems.FACTORY_GUIDE.get()) && held(player).getCount() == 1, "opening the handbook used it up");

        if (!com.drmangotea.tfmg.config.TFMGConfigs.common().giveHandbookOnFirstJoin.get()) {
            helper.succeed();
            return;
        }
        Tester newcomer = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
        GuideEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(newcomer));
        GuideEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(newcomer));
        int books = 0;
        for (int i = 0; i < newcomer.getInventory().getContainerSize(); i++)
            if (newcomer.getInventory().getItem(i).is(TFMGItems.FACTORY_GUIDE.get()))
                books += newcomer.getInventory().getItem(i).getCount();
        helper.assertTrue(books == 1, "two logins gave " + books + " handbooks");
        helper.succeed();
    }

    /** The Factory Blueprint, blank or carrying a line, used in the air or on a block on the server: nothing breaks, nothing is placed. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void blueprintIsHarmlessOnTheServer(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 1, 2);
        List<String> problems = new ArrayList<>();
        Map<BlockPos, BlockState> before = new java.util.HashMap<>();
        BlockPos.betweenClosedStream(helper.getBounds()).forEach(pos -> before.put(pos.immutable(), helper.getLevel().getBlockState(pos)));
        for (ItemStack blueprint : List.of(TFMGItems.FACTORY_BLUEPRINT.asStack(), BlueprintLines.stack("steel"), BlueprintLines.stack("oil"))) {
            Tester player = player(helper, new Vec3(2.5, 2, 0.5), 0, 0);
            boolean blank = BlueprintLines.lineOf(blueprint) == null;
            InteractionResult air = useInAir(helper, player, blueprint.copy());
            InteractionResult block = click(helper, player, floor, Direction.UP, blueprint.copy());
            player.setShiftKeyDown(true);
            click(helper, player, floor, Direction.UP, blueprint.copy());
            if (blank == air.consumesAction() || blank == block.consumesAction())
                problems.add((blank ? "a blank" : "a " + BlueprintLines.lineOf(blueprint)) + " blueprint answered " + air + " in the air and " + block + " on a block");
            if (!held(player).is(TFMGItems.FACTORY_BLUEPRINT.get()))
                problems.add("the blueprint was used up");
        }
        int changed = 0;
        for (Map.Entry<BlockPos, BlockState> e : before.entrySet())
            if (helper.getLevel().getBlockState(e.getKey()) != e.getValue())
                changed++;
        if (changed > 0)
            problems.add(changed + " blocks changed");
        helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
        helper.succeed();
    }

    // ============================================================== fuels

    /** Coal coke and its block burn in a furnace for their stated times; a block of laminated magnetic alloy is no fuel. */
    @GameTest(template = SMALL, batch = BATCH)
    public static void fuelItemsBurn(GameTestHelper helper) {
        Map<Item, Integer> times = Map.of(TFMGItems.COAL_COKE.get(), 3200, TFMGItems.COAL_COKE_DUST.get(), 3200,
                TFMGBlocks.COAL_COKE_BLOCK.asItem(), 28800, TFMGBlocks.LAMINATED_MAGNETIC_ALLOY_BLOCK.asItem(), 0);
        List<String> problems = new ArrayList<>();
        times.forEach((item, time) -> {
            int burn = new ItemStack(item).getBurnTime(RecipeType.SMELTING);
            if (burn != time)
                problems.add(item + " burns " + burn + " ticks, " + time + " expected");
        });
        helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
        helper.succeed();
    }
}
