package com.drmangotea.tfmg.content.machinery.metallurgy.blast_furnace;

import com.drmangotea.tfmg.base.ThrottledSync;
import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.base.capability.DrainOnlyFluidHandler;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.config.TFMGConfigs;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.datagen.TFMGDamageSources;
import com.drmangotea.tfmg.recipes.IndustrialBlastingRecipe;
import com.drmangotea.tfmg.registry.*;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.item.SmartInventory;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class BlastFurnaceOutputBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, IInspectable {

    // Per-tick fluid and progress changes sync at most every few ticks.
    private final ThrottledSync throttledSync = new ThrottledSync();

    public SmartInventory inputInventory;
    public SmartInventory fluxInventory;
    public FluidTank primaryTank;
    public FluidTank secondaryTank;
    protected IFluidHandler fluidCapability;
    public IItemHandler itemCapability;
    public int fuel = 0;
    public int fuelConsumeTimer = 0;
    public float duration;
    public int timer = -1;
    public BlockPos tuyerePos;
    public BlastFurnaceHatchBlockEntity tuyereBE = null;
    public static final int STORAGE_SPACE = 64;
    public LerpedFloat coalCokeHeight = LerpedFloat.linear();
    boolean isReinforced = false;
    private int cachedSize = 0;
    private IndustrialBlastingRecipe currentRecipe;


    public BlastFurnaceOutputBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
        inputInventory = new SmartInventory(1, this)
                .forbidInsertion()
                .forbidExtraction()
                .withMaxStackSize(64);
        fluxInventory = new SmartInventory(1, this)
                .forbidInsertion()
                .forbidExtraction()
                .withMaxStackSize(64).whenContentsChanged(i -> this.onContentsChanged());

        primaryTank = new SmartFluidTank(4000, this::onFluidChanged);

        secondaryTank = new SmartFluidTank(4000, this::onFluidChanged);


        itemCapability = new InputRouter();
        // Drain-only: water from a shared pipe network used to land in the
        // metal tank and stall the furnace until someone emptied it.
        fluidCapability = new DrainOnlyFluidHandler(new CombinedTankWrapper(primaryTank, secondaryTank));
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                TFMGBlockEntities.BLAST_FURNACE_OUTPUT.get(),
                (be, context) -> be.fluidCapability
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                TFMGBlockEntities.BLAST_FURNACE_OUTPUT.get(),
                (be, context) -> be.itemCapability
        );
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    private void onFluidChanged(FluidStack stack) {
        if (!hasLevel())
            return;
        if (!level.isClientSide) {
            setChanged();
            throttledSync.request(this);
            // Do NOT invalidate capabilities here — invalidating on every
            // fluid change makes Create's mechanical pump lose its handler
            // reference between extract attempts and stutter to a halt
            // (Neymor16 saw 128 mB / break-and-replace bursts). We only
            // need to invalidate on chunk-reload / BE-re-init scenarios,
            // which are handled in read().
        }
    }

    public void onContentsChanged() {
        if (!inputInventory.isEmpty() && timer == -1) {
            executeRecipe();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

        TFMGTexts.BlastFurnace.stats(inputInventory.getStackInSlot(0).getCount()).forGoggles(tooltip, 1);

        TFMGTexts.BlastFurnace.height(cachedSize).forGoggles(tooltip, 1);
        TFMGTexts.BlastFurnace.fuelAmount(fuel).forGoggles(tooltip, 1);

        if (timer != -1)
            TFMGTexts.BlastFurnace.timer(timer).forGoggles(tooltip, 1);


        if (isReinforced)
            TFMGTexts.BlastFurnace.reinforced().forGoggles(tooltip);

        addProblems(tooltip);

        TFMGUtils.createFluidTooltip(this, tooltip);
        TFMGUtils.createItemTooltip(this, tooltip);


        return true;
    }

    /**
     * Says why the furnace is idle. Every one of these used to stop it with
     * no word at all: the timer just froze.
     */
    private void addProblems(List<Component> tooltip) {
        if (cachedSize < 3) {
            TFMGTexts.problem("blast_furnace.structure").forGoggles(tooltip);
            return;
        }
        if (inputInventory.isEmpty())
            return;
        if (fuel <= 0)
            TFMGTexts.problem("blast_furnace.fuel").forGoggles(tooltip);
        if (fluxInventory.isEmpty())
            TFMGTexts.problem("blast_furnace.flux").forGoggles(tooltip);
        if (tuyerePos == null || !(level.getBlockEntity(tuyerePos) instanceof BlastFurnaceHatchBlockEntity hatch))
            TFMGTexts.problem("blast_furnace.no_hatch").forGoggles(tooltip);
        else if (!hatch.tank.getFluid().getFluid().isSame(TFMGFluids.HOT_AIR.getSource()))
            TFMGTexts.problem("blast_furnace.no_hot_air").forGoggles(tooltip);
        if (primaryTank.getSpace() < 144 || secondaryTank.getSpace() < 144)
            TFMGTexts.problem("output_full").forGoggles(tooltip);
    }

    /** Height found by the last structure scan, refreshed every lazy tick. */
    public int getCachedSize() {
        return cachedSize;
    }

    /** Outcome of a read-only replay of {@link #getSize()}: where and why it stopped. */
    private record FurnaceScan(int height, int faultLayer, BlockPos faultPos, String fault, BlockPos hatch,
                               boolean reinforced) {
    }

    /** {@link #isValidWall} without writing tuyerePos: the first hatch found goes in {@code hatch[0]}. */
    private FurnaceBlockType inspectWall(BlockPos pos, BlockPos[] hatch) {
        BlockState state = level.getBlockState(pos);
        if (state.is(TFMGBlocks.BLAST_FURNACE_HATCH.get())) {
            if (hatch[0] != null)
                return FurnaceBlockType.NONE;
            hatch[0] = pos;
        }
        if (state.is(TFMGTags.TFMGBlockTags.REINFORCED_BLAST_FURNACE_WALL.tag))
            return FurnaceBlockType.REINFORCED;
        if (state.is(TFMGTags.TFMGBlockTags.BLAST_FURNACE_WALL.tag))
            return FurnaceBlockType.REGULAR;
        return FurnaceBlockType.NONE;
    }

    /**
     * Replays the structure scan of {@link #getSize()} step for step, without
     * touching tuyerePos or isReinforced, so the inspector can say which block
     * of which layer ended the furnace.
     */
    private FurnaceScan scanForInspection() {
        BlockPos middlePos = getBlockPos().relative(getBlockState().getValue(FACING).getOpposite());
        BlockPos[] hatch = {null};
        if (inspectWall(middlePos, hatch) == FurnaceBlockType.NONE)
            return new FurnaceScan(0, 0, middlePos, "base", null, false);

        int size = 0;
        int normalAmount = 0;
        int reinforcedAmount = 0;
        int maxHeight = TFMGConfigs.common().machines.blastFurnaceMaxHeight.get();
        for (int i = 0; i < maxHeight; i++) {
            BlockPos checkedPos = middlePos.above(i).east().south();
            for (int j = 0; j < 3; j++) {
                for (int y = 0; y < 3; y++) {
                    BlockPos hatchBefore = hatch[0];
                    FurnaceBlockType wall = inspectWall(checkedPos, hatch);
                    FurnaceBlockType support = isValidSupport(checkedPos);
                    boolean reinforced = normalAmount == 0 && reinforcedAmount > 0;
                    if (checkedPos.getX() == middlePos.getX() ^ checkedPos.getZ() == middlePos.getZ()) {
                        if (!level.getBlockState(checkedPos).is(TFMGBlocks.BLAST_FURNACE_OUTPUT.get())) {
                            if (wall == FurnaceBlockType.NONE) {
                                boolean secondHatch = hatchBefore != null
                                        && level.getBlockState(checkedPos).is(TFMGBlocks.BLAST_FURNACE_HATCH.get());
                                return new FurnaceScan(size, i, checkedPos, secondHatch ? "second_hatch" : "wall", hatch[0], reinforced);
                            }
                            if (wall == FurnaceBlockType.REGULAR)
                                normalAmount++;
                            else
                                reinforcedAmount++;
                        }
                    } else if (checkedPos.getX() == middlePos.getX() && checkedPos.getZ() == middlePos.getZ()) {
                        if (!level.getBlockState(checkedPos).isAir() && i != 0)
                            return new FurnaceScan(size, i, checkedPos, "center", hatch[0], reinforced);
                    } else if (support == FurnaceBlockType.NONE) {
                        return new FurnaceScan(size, i, checkedPos, "corner", hatch[0], reinforced);
                    } else if (support == FurnaceBlockType.REGULAR) {
                        normalAmount++;
                    } else {
                        reinforcedAmount++;
                    }
                    checkedPos = checkedPos.west();
                }
                checkedPos = checkedPos.north();
                checkedPos = checkedPos.east(3);
            }
            size++;
        }
        return new FurnaceScan(size, -1, null, null, hatch[0], normalAmount == 0 && reinforcedAmount > 0);
    }

    @Override
    public void inspect(InspectionReport report) {
        if (level == null)
            return;
        int maxHeight = TFMGConfigs.common().machines.blastFurnaceMaxHeight.get();
        BlockPos middlePos = getBlockPos().relative(getBlockState().getValue(FACING).getOpposite());

        // Structure
        BlockPos hatchPos = tuyerePos;
        boolean reinforced = isReinforced;
        if (!isScanAreaLoaded()) {
            report.info("blast_furnace.not_loaded");
            if (cachedSize < 3) {
                report.problem("blast_furnace.too_short", cachedSize, maxHeight);
                return;
            }
            report.ok("blast_furnace.height", cachedSize, maxHeight);
        } else {
            FurnaceScan scan = scanForInspection();
            hatchPos = scan.hatch();
            reinforced = scan.reinforced();
            if (scan.height() < 3) {
                report.problem("blast_furnace.too_short", scan.height(), maxHeight);
                BlockPos at = scan.faultPos();
                Component found = level.getBlockState(at).getBlock().getName();
                switch (scan.fault()) {
                    case "base" -> {
                        report.problem("blast_furnace.fault.base", at.getX(), at.getY(), at.getZ(), found);
                        report.fix("blast_furnace.fault.base.fix", at.getX(), at.getY(), at.getZ());
                    }
                    case "second_hatch" -> {
                        report.problem("blast_furnace.fault.second_hatch", scan.faultLayer() + 1, at.getX(), at.getY(), at.getZ());
                        report.fix("blast_furnace.fault.second_hatch.fix");
                    }
                    case "center" -> {
                        report.problem("blast_furnace.fault.center", scan.faultLayer() + 1, at.getX(), at.getY(), at.getZ(), found);
                        report.fix("blast_furnace.fault.center.fix");
                    }
                    case "corner" -> {
                        report.problem("blast_furnace.fault.corner", scan.faultLayer() + 1, at.getX(), at.getY(), at.getZ(), found);
                        report.fix("blast_furnace.fault.corner.fix", at.getX(), at.getY(), at.getZ());
                    }
                    default -> {
                        report.problem("blast_furnace.fault.wall", scan.faultLayer() + 1, at.getX(), at.getY(), at.getZ(), found);
                        report.fix("blast_furnace.fault.wall.fix", at.getX(), at.getY(), at.getZ());
                    }
                }
                return;
            }
            report.ok("blast_furnace.height", scan.height(), maxHeight);
            if (scan.height() < maxHeight)
                report.info("blast_furnace.taller");
        }
        report.info(reinforced ? "blast_furnace.reinforced" : "blast_furnace.not_reinforced");

        // Ore and recipe
        ItemStack ore = inputInventory.getStackInSlot(0);
        IndustrialBlastingRecipe recipe = null;
        if (ore.isEmpty()) {
            report.problem("blast_furnace.no_ore");
            report.fix("blast_furnace.no_ore.fix");
        } else {
            Optional<RecipeHolder<IndustrialBlastingRecipe>> found = TFMGRecipeTypes.INDUSTRIAL_BLASTING.find(new RecipeWrapper(inputInventory), level);
            if (found.isEmpty()) {
                report.problem("blast_furnace.no_recipe", ore.getHoverName());
                report.fix("blast_furnace.no_recipe.fix");
            } else {
                recipe = found.get().value();
                report.ok("blast_furnace.ore", ore.getCount(), ore.getHoverName(), recipe.getPrimaryResult().getHoverName());
            }
        }

        // Fuel
        if (fuel > 0)
            report.ok("blast_furnace.fuel", fuel, STORAGE_SPACE, TFMGConfigs.common().machines.blastFurnaceFuelConsumption.get());
        else {
            report.problem("blast_furnace.no_fuel");
            report.fix("blast_furnace.no_fuel.fix");
        }

        // Flux
        ItemStack flux = fluxInventory.getStackInSlot(0);
        if (recipe != null && recipe.getIngredients().size() > 1) {
            int needed = recipe.getIngredients().size() - 1;
            ItemStack[] accepted = recipe.getIngredients().get(1).getItems();
            Component fluxName = accepted.length > 0 ? accepted[0].getHoverName() : Component.literal("?");
            if (flux.isEmpty()) {
                report.problem("blast_furnace.no_flux", fluxName);
                report.fix("blast_furnace.no_flux.fix", fluxName);
            } else if (!recipe.getIngredients().get(1).test(flux)) {
                report.problem("blast_furnace.wrong_flux", flux.getHoverName(), fluxName);
                report.fix("blast_furnace.wrong_flux.fix");
            } else if (flux.getCount() < needed) {
                report.problem("blast_furnace.flux_short", flux.getCount(), needed);
                report.fix("blast_furnace.no_flux.fix", fluxName);
            } else {
                report.ok("blast_furnace.flux", flux.getCount(), flux.getHoverName(), needed);
            }
        } else if (!flux.isEmpty()) {
            report.info("blast_furnace.flux_stock", flux.getCount(), flux.getHoverName());
        }

        // Hot air hatch in the walls
        int usage = recipe == null ? 0 : recipe.hotAirUsage;
        if (recipe == null || usage > 0) {
            BlastFurnaceHatchBlockEntity hatch = hatchPos != null && level.isLoaded(hatchPos)
                    && level.getBlockEntity(hatchPos) instanceof BlastFurnaceHatchBlockEntity h ? h : null;
            if (hatch == null) {
                report.problem("blast_furnace.no_hatch");
                report.fix("blast_furnace.no_hatch.fix");
            } else {
                FluidStack air = hatch.tank.getFluid();
                if (air.isEmpty()) {
                    report.problem("blast_furnace.no_hot_air", hatchPos.getX(), hatchPos.getY(), hatchPos.getZ());
                    report.fix("blast_furnace.no_hot_air.fix");
                } else if (!air.getFluid().isSame(TFMGFluids.HOT_AIR.getSource())) {
                    report.problem("blast_furnace.hatch_wrong_fluid", air.getHoverName());
                    report.fix("blast_furnace.hatch_wrong_fluid.fix", air.getHoverName());
                } else if (usage > 0 && air.getAmount() < usage) {
                    report.problem("blast_furnace.hot_air_low", air.getAmount(), usage);
                    report.fix("blast_furnace.hot_air_low.fix", usage);
                } else if (usage > 0) {
                    report.ok("blast_furnace.hot_air", air.getAmount(), usage);
                } else {
                    report.ok("blast_furnace.hot_air_stock", air.getAmount());
                }
            }
        }

        // Output tanks
        if (recipe != null) {
            inspectOutputTank(report, primaryTank, recipe.getPrimaryResult());
            if (recipe.getFluidResults().size() > 1)
                inspectOutputTank(report, secondaryTank, recipe.getSecondaryResult());
        }

        // Furnace gas hatch on top of the shaft
        if (cachedSize >= 3) {
            BlockPos top = middlePos.above(cachedSize);
            if (level.isLoaded(top) && level.getBlockEntity(top) instanceof BlastFurnaceHatchBlockEntity gas) {
                if (gas.tank.getSpace() <= 0)
                    report.info("blast_furnace.gas_hatch_full", top.getX(), top.getY(), top.getZ());
                else
                    report.ok("blast_furnace.gas_hatch", top.getX(), top.getY(), top.getZ());
            } else if (recipe == null || !recipe.getGasByproduct().isEmpty()) {
                report.info("blast_furnace.no_gas_hatch", top.getX(), top.getY(), top.getZ());
            }
        }

        if (timer > 0)
            report.info("blast_furnace.progress", String.format("%.1f", timer / 20f));
    }

    private void inspectOutputTank(InspectionReport report, FluidTank tank, FluidStack result) {
        if (result.isEmpty())
            return;
        if (!tank.getFluid().isEmpty() && !tank.getFluid().getFluid().isSame(result.getFluid())) {
            report.problem("blast_furnace.output_other", tank.getFluid().getHoverName(), result.getHoverName());
            report.fix("blast_furnace.output_drain.fix", tank.getFluid().getHoverName());
        } else if (tank.getSpace() < result.getAmount()) {
            report.problem("blast_furnace.output_full", result.getHoverName(), tank.getFluidAmount(), tank.getCapacity());
            report.fix("blast_furnace.output_drain.fix", result.getHoverName());
        } else {
            report.ok("blast_furnace.output_space", result.getHoverName(), tank.getSpace());
        }
    }

    public void executeRecipe() {

        RecipeWrapper inventoryIn = new RecipeWrapper(inputInventory);
        Optional<RecipeHolder<IndustrialBlastingRecipe>> optional = TFMGRecipeTypes.INDUSTRIAL_BLASTING.find(inventoryIn, level);

        if (optional.isEmpty())
            return;

        IndustrialBlastingRecipe recipe = optional.get().value();
        if (recipe.getIngredients().size() > 1)
            if (!(recipe.getIngredients().get(1).test(fluxInventory.getItem(0))))
                return;

        if (fluxInventory.getItem(0).getCount() < recipe.getIngredients().size() - 1)
            return;

        int baseDuration = recipe.getProcessingDuration() * 20;
        int heigth = cachedSize > 0 ? cachedSize : getSize();
        int maxHeigth = TFMGConfigs.common().machines.blastFurnaceMaxHeight.get();
        double maxTimeModifier = TFMGConfigs.common().machines.blastFurnaceHeightSpeedModifier.get();
        double timeModifier = maxHeigth / ((baseDuration / 2) * maxTimeModifier);

        timer = (int) (baseDuration - (heigth / timeModifier));
        if (isReinforced)
            timer /= 2;
        // A speed modifier above 2 drove the timer below -1, a value neither
        // the "running" (> -1) nor the "idle" (== -1) branch handles, and the
        // furnace froze for good.
        timer = Math.max(timer, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide)
            throttledSync.tick(this);

        if (level.isClientSide) {
            coalCokeHeight.chase(Math.min(fuel + inputInventory.getStackInSlot(0).getCount(), 24), 0.1f, LerpedFloat.Chaser.EXP);
            coalCokeHeight.tickChaser();
            // The smoke used to be spawned from the smelting loop below, which
            // the client no longer runs. Timer, fuel and the input inventory
            // are all synced, so an actively smelting furnace is read right
            // here; the virtual (ponder) path still smokes from the loop.
            if (!isVirtual() && timer > 0 && fuel > 0 && !inputInventory.isEmpty() && cachedSize >= 3)
                makeParticles();
        }

        // Everything below burns fuel, shrinks the input stacks and fills the
        // result tanks. The client ran all of it against its own copies, so a
        // furnace in view consumed coke and produced metal locally on its own
        // timer until the next sync overwrote it. Fuel, the inventories, the
        // tanks and the timer are all written to the client, which therefore
        // has nothing to compute here - only the coke pile animation above,
        // which reads the synced values.
        if (level == null || (level.isClientSide && !isVirtual()))
            return;

        if (inputInventory.isEmpty())
            return;
        if (cachedSize < 3)
            return;

        if (fuelConsumeTimer >= TFMGConfigs.common().machines.blastFurnaceFuelConsumption.get() && fuel > 0) {
            fuelConsumeTimer = 0;
            fuel--;
        }

        if (timer > -1) {
            IndustrialBlastingRecipe recipe = currentRecipe;
            if (recipe == null) {
                RecipeWrapper inventoryIn = new RecipeWrapper(inputInventory);
                Optional<RecipeHolder<IndustrialBlastingRecipe>> optional = TFMGRecipeTypes.INDUSTRIAL_BLASTING.find(inventoryIn, level);
                if (optional.isEmpty()) {
                    timer = -1;
                    currentRecipe = null;
                    return;
                }
                recipe = optional.get().value();
                currentRecipe = recipe;
            }

            if (timer == 0) {
                if (canProcess(recipe)) {
                    int itemsUsed = 1;
                    int fluxUsed = 1;

                    if (!(primaryTank.getSpace() >= recipe.getPrimaryResult().getAmount()))
                        return;
                    if (recipe.getFluidResults().size() > 1)
                        if (!(secondaryTank.getSpace() >= recipe.getSecondaryResult().getAmount()))
                            return;

                    inputInventory.getItem(0).shrink(1);
                    if (recipe.getIngredients().size() > 1)
                        fluxInventory.getItem(0).shrink(recipe.getIngredients().size() - 1);
                    primaryTank.fill(recipe.getPrimaryResult(), IFluidHandler.FluidAction.EXECUTE);
                    if (recipe.getFluidResults().size() > 1)
                        secondaryTank.fill(recipe.getSecondaryResult(), IFluidHandler.FluidAction.EXECUTE);

                    timer = -1;
                    currentRecipe = null;

                    sendData();
                    setChanged();
                }
            }
            if (timer > 0 && fuel > 0) {
                if (recipe.hotAirUsage > 0 && (tuyerePos == null || !level.getBlockState(tuyerePos).is(TFMGBlocks.BLAST_FURNACE_HATCH.get()))) {
                    tuyereBE = null;
                    return;
                }
                // Invalidate the cached hatch if it was removed (chunk reload,
                // break-and-replace) or if the structure scan moved tuyerePos
                // to a different hatch; otherwise we drain a ghost tank.
                if (tuyereBE != null && (tuyereBE.isRemoved() || !tuyereBE.getBlockPos().equals(tuyerePos)))
                    tuyereBE = null;
                if (tuyereBE == null && tuyerePos != null) {
                    if (level.getBlockEntity(tuyerePos) instanceof BlastFurnaceHatchBlockEntity hatch)
                        tuyereBE = hatch;
                }
                if (tuyereBE!=null)
                    if (tuyereBE.tank.getFluidAmount() < recipe.hotAirUsage || !tuyereBE.tank.getFluid().getFluid().isSame(TFMGFluids.HOT_AIR.getSource()))
                        return;
                if (tuyereBE!=null) {
                    tuyereBE.tank.drain(recipe.hotAirUsage, IFluidHandler.FluidAction.EXECUTE);
                }
                if (!recipe.getGasByproduct().isEmpty()) {
                    // Use cachedSize (refreshed each lazyTick, guaranteed >= 3
                    // here) instead of the expensive, side-effecting getSize()
                    // rescan on the per-tick hot path.
                    if (level.getBlockEntity(getBlockPos().relative(getBlockState().getValue(FACING).getOpposite()).above(cachedSize)) instanceof BlastFurnaceHatchBlockEntity be) {
                        be.tank.fill(recipe.getGasByproduct(), IFluidHandler.FluidAction.EXECUTE);
                    }
                }
                if (level.isClientSide())
                    makeParticles();
                hurtEntities();
                timer--;
                fuelConsumeTimer++;

                if (!level.isClientSide) {
                    setChanged();
                    throttledSync.request(this);
                }
            }
        }
    }

    public void makeParticles() {
        Random random = Create.RANDOM;
        Direction direction = getBlockState().getValue(FACING).getOpposite();
        BlockPos pos = getBlockPos().above().relative(direction);
        int shouldSpawnSmoke = random.nextInt(7);
        if (shouldSpawnSmoke == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX() + random.nextFloat(0.6f) + 0.2, pos.getY() + 1, pos.getZ() + random.nextFloat(0.6f) + 0.2, 0.0D, 0.08D, 0.0D);
        }
    }

    private boolean canProcess(IndustrialBlastingRecipe recipe) {
        if (fuel == 0)
            return false;

        if (!primaryTank.getFluid().isEmpty() && !primaryTank.getFluid().getFluid().isSame(recipe.getPrimaryResult().getFluid()))
            return false;
        if (recipe.getFluidResults().size() > 1 && !secondaryTank.getFluid().isEmpty() && !secondaryTank.getFluid().getFluid().isSame(recipe.getSecondaryResult().getFluid()))
            return false;
        return true;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (isScanAreaLoaded()) {
            int previousSize = cachedSize;
            boolean wasReinforced = isReinforced;
            cachedSize = getSize();
            // Push the rescan result out when it actually moved. Both values
            // feed the goggle overlay, and nothing else here syncs them, so a
            // structure that changed between two recipe events kept showing the
            // height and reinforced status of its previous scan. Guarded on a
            // change so an idle furnace does not send a packet every lazy tick.
            if (!level.isClientSide && (cachedSize != previousSize || isReinforced != wasReinforced)) {
                setChanged();
                sendData();
            }
        }
        onContentsChanged();
        collectItems();
    }

    private boolean isScanAreaLoaded() {
        BlockPos middlePos = getBlockPos().relative(getBlockState().getValue(FACING).getOpposite());
        int maxHeight = TFMGConfigs.common().machines.blastFurnaceMaxHeight.get();
        BlockPos low = middlePos.offset(-1, 0, -1);
        BlockPos high = middlePos.offset(1, maxHeight, 1);
        return level.isLoaded(low) && level.isLoaded(high);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(getBlockPos()).setMaxY(getBlockPos().getY() + 2);
    }

    public void hurtEntities() {

        // Damage is server logic. Running it on the client also meant an entity
        // query every tick of every working furnace purely to set fire ticks
        // the server never agreed to.
        if (level == null || (level.isClientSide && !isVirtual()))
            return;

        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, new AABB(this.getBlockPos().relative(getBlockState().getValue(FACING).getOpposite()).above()));

        for (LivingEntity entity : entities) {
            if (!entity.fireImmune()) {
                entity.setRemainingFireTicks(15);
                if (entity.hurt(TFMGDamageSources.blastFurnace(level), 4.0F)) {
                    entity.playSound(SoundEvents.GENERIC_BURN, 0.4F, 2.0F + entity.getRandom().nextFloat() * 0.4F);
                }

            }
        }
    }

    public void collectItems() {

        // Consuming dropped items is server logic: on the client this shrank
        // ItemEntity stacks and filled local inventories that the next sync
        // packet overwrote, so items flickered away and came back. It also
        // cost an entity query per lazy tick on every furnace in render range.
        if (level == null || (level.isClientSide && !isVirtual()))
            return;

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(this.getBlockPos().relative(getBlockState().getValue(FACING).getOpposite()).above()));

        if (items.isEmpty())
            return;

        // Walk EVERY ItemEntity in the collection zone, not just the first
        // one. The previous code locked onto items.get(0) and looped 64
        // times against it, so when that first item could not be consumed
        // (for example, surplus coal_coke_dust hitting the fuel cap), no
        // other ItemEntity in the same zone was ever considered. Players
        // reported quartz dropped after coal_coke_dust never made it into
        // the BFO. Now we iterate items, and within each item we keep
        // pulling units until the destination slot is full, then skip to
        // the next item.
        int budget = 64;
        int fuelBefore = fuel;
        for (ItemEntity entity : items) {
            if (budget <= 0)
                return;
            ItemStack itemStack = entity.getItem();
            while (budget > 0 && !itemStack.isEmpty()) {
                budget--;
                if (itemStack.is(TFMGTags.TFMGItemTags.BLAST_FURNACE_FUEL.tag)) {
                    if (fuel < STORAGE_SPACE) {
                        fuel++;
                        itemStack.shrink(1);
                    } else {
                        // Fuel cap reached — leave this item on the ground
                        // and move on to the next ItemEntity instead of
                        // letting the surplus block other drops behind it.
                        break;
                    }
                    continue;
                }
                if (itemStack.is(TFMGTags.TFMGItemTags.FLUX.tag)) {
                    if (fluxInventory.getItem(0).getCount() < itemStack.getMaxStackSize()
                            && (fluxInventory.isEmpty() || ItemStack.isSameItemSameComponents(fluxInventory.getItem(0), itemStack))) {
                        fluxInventory.setItem(0, itemStack.copyWithCount(fluxInventory.getItem(0).getCount() + 1));
                        itemStack.shrink(1);
                    } else {
                        break;
                    }
                    continue;
                }
                // Only ore some recipe can smelt goes in: the slot cannot be
                // emptied, so one stray item used to jam the furnace for good.
                if (!isSmeltable(itemStack))
                    break;
                if (inputInventory.getItem(0).getCount() < itemStack.getMaxStackSize()
                        && (inputInventory.isEmpty() || ItemStack.isSameItemSameComponents(inputInventory.getItem(0), itemStack))) {
                    inputInventory.setItem(0, itemStack.copyWithCount(inputInventory.getItem(0).getCount() + 1));
                    itemStack.shrink(1);
                    continue;
                }
                // Cannot place this unit (input slot full or holds a
                // different item). Stop on this entity and try the next.
                break;
            }
        }
        // The fuel counter is a plain int: without this a furnace that was
        // idle (and so never marked dirty) lost the coke it had just eaten on
        // the next unload, and the goggles showed the old count.
        if (fuel != fuelBefore) {
            setChanged();
            sendData();
        }
    }

    /** True when an industrial blasting recipe takes this item as its ore. */
    private boolean isSmeltable(ItemStack stack) {
        if (level == null)
            return false;
        for (RecipeHolder<IndustrialBlastingRecipe> holder : level.getRecipeManager()
                .<RecipeInput, IndustrialBlastingRecipe>getAllRecipesFor(TFMGRecipeTypes.INDUSTRIAL_BLASTING.getType())) {
            if (!holder.value().getIngredients().isEmpty() && holder.value().getIngredients().get(0).test(stack))
                return true;
        }
        return false;
    }

    /**
     * Accepts one stack the way collectItems accepts one dropped on the furnace:
     * blast furnace fuel goes to the fuel counter, flux to the flux slot and
     * anything else to the input slot. Returns whatever did not fit.
     */
    private ItemStack routeInsert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty())
            return ItemStack.EMPTY;

        if (stack.is(TFMGTags.TFMGItemTags.BLAST_FURNACE_FUEL.tag)) {
            int moved = Math.min(Math.max(STORAGE_SPACE - fuel, 0), stack.getCount());
            if (moved <= 0)
                return stack;
            if (!simulate) {
                fuel += moved;
                setChanged();
                sendData();
            }
            return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
        }

        SmartInventory target = stack.is(TFMGTags.TFMGItemTags.FLUX.tag) ? fluxInventory : inputInventory;
        if (target == inputInventory && !isSmeltable(stack))
            return stack;
        ItemStack current = target.getItem(0);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack))
            return stack;

        int limit = Math.min(stack.getMaxStackSize(), target.getSlotLimit(0));
        int moved = Math.min(Math.max(limit - current.getCount(), 0), stack.getCount());
        if (moved <= 0)
            return stack;
        if (!simulate) {
            target.setItem(0, stack.copyWithCount(current.getCount() + moved));
            setChanged();
            sendData();
        }
        return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    /**
     * Item capability exposed to funnels, hoppers and pipes.
     *
     * Both internal inventories are built with forbidInsertion, so the handler
     * that used to be published here refused everything: the only way to load
     * the furnace was to drop items on top of it and let collectItems pick them
     * up, which is why it could not be automated. This routes an inserted stack
     * exactly like a dropped one instead of letting an inserter drop ore into
     * the flux slot.
     *
     * Extraction stays closed. The furnace has no item output — it produces
     * fluids — and opening it would let a pipe pull the ore back out.
     */
    private class InputRouter implements IItemHandler {

        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 1 ? fluxInventory.getItem(0) : inputInventory.getItem(0);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return routeInsert(stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return STORAGE_SPACE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return routeInsert(stack, true).getCount() < stack.getCount();
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);
        isReinforced = compound.getBoolean("IsReinforce");
        cachedSize = compound.getInt("CachedSize");
        inputInventory.deserializeNBT(registries,compound.getCompound("InputItems"));
        fluxInventory.deserializeNBT(registries,compound.getCompound("Flux"));
        timer = compound.getInt("Timer");
        fuel = compound.getInt("Fuel");
        fuelConsumeTimer = compound.getInt("FuelConsumeTimer");
        primaryTank.readFromNBT(registries,compound.getCompound("PrimaryTankContent"));
        secondaryTank.readFromNBT(registries,compound.getCompound("SecondaryTankContent"));
        // Force neighbours to re-fetch the IFluidHandler reference after a
        // chunk reload. Otherwise Mekanism pipes can keep a stale handler
        // and silently fail to extract from the output until the player
        // breaks-and-replaces a connector.
        if (!clientPacket && hasLevel() && !level.isClientSide)
            level.invalidateCapabilities(getBlockPos());
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries , clientPacket);
        compound.putBoolean("IsReinforce", isReinforced);
        compound.putInt("CachedSize", cachedSize);
        compound.put("InputItems", inputInventory.serializeNBT(registries));
        compound.put("Flux", fluxInventory.serializeNBT(registries));
        compound.putInt("Timer", timer);
        compound.putInt("Fuel", fuel);
        compound.putInt("FuelConsumeTimer", fuelConsumeTimer);
        compound.put("PrimaryTankContent", primaryTank.writeToNBT(registries,new CompoundTag()));
        compound.put("SecondaryTankContent", secondaryTank.writeToNBT(registries,new CompoundTag()));
    }

    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, inputInventory);
        ItemHelper.dropContents(level, worldPosition, fluxInventory);
        // Stored fuel is a counter, not a slot; hand it back as coke dust.
        int remaining = fuel;
        while (remaining > 0) {
            int count = Math.min(remaining, 64);
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    new ItemStack(TFMGItems.COAL_COKE_DUST.get(), count));
            remaining -= count;
        }
        fuel = 0;
    }

    public int getSize() {

        BlockPos middlePos = getBlockPos().relative(getBlockState().getValue(FACING).getOpposite());

        tuyerePos = null;

        // Every exit path below has to settle isReinforced. It is persisted and
        // synced, so a path that left it untouched kept whatever the field was
        // constructed with: a freshly placed output block reported "not
        // reinforced" until an unrelated event happened to rescan and sync.
        if ((isValidWall(middlePos) == FurnaceBlockType.NONE)) {
            isReinforced = false;
            return 0;
        }

        int size = 0;

        int normalAmount = 0;
        int reinforcedAmount = 0;

        for (int i = 0; i < TFMGConfigs.common().machines.blastFurnaceMaxHeight.get(); i++) {

            BlockPos checkedPos = middlePos.above(i).east().south();

            for (int j = 0; j < 3; j++) {
                for (int y = 0; y < 3; y++) {
                    FurnaceBlockType wall = isValidWall(checkedPos);
                    FurnaceBlockType support = isValidSupport(checkedPos);
                    if (checkedPos.getX() == middlePos.getX() ^ checkedPos.getZ() == middlePos.getZ()) {
                        if (!level.getBlockState(checkedPos).is(TFMGBlocks.BLAST_FURNACE_OUTPUT.get())) {
                            if (wall == FurnaceBlockType.NONE) {
                                isReinforced = normalAmount == 0 && reinforcedAmount > 0;
                                return size;
                            } else {
                                if (wall == FurnaceBlockType.REGULAR) {
                                    normalAmount++;
                                } else reinforcedAmount++;
                            }
                        }
                    } else if (checkedPos.getX() == middlePos.getX() && checkedPos.getZ() == middlePos.getZ()) {
                        if (!level.getBlockState(checkedPos).isAir() && i != 0) {
                            isReinforced = normalAmount == 0 && reinforcedAmount > 0;

                            return size;
                        }
                    } else if (support == FurnaceBlockType.NONE) {
                        isReinforced = normalAmount == 0 && reinforcedAmount > 0;
                        return size;
                    } else {
                        if (support == FurnaceBlockType.REGULAR) {
                            normalAmount++;
                        } else reinforcedAmount++;
                    }

                    checkedPos = checkedPos.west();
                }
                checkedPos = checkedPos.north();
                checkedPos = checkedPos.east(3);
            }
            size++;
        }
        isReinforced = normalAmount == 0 && reinforcedAmount > 0;
        return size;
    }

    public FurnaceBlockType isValidWall(BlockPos pos) {

        BlockState state = level.getBlockState(pos);

        if (state.is(TFMGBlocks.BLAST_FURNACE_HATCH.get())) {
            if (tuyerePos != null)
                return FurnaceBlockType.NONE;
            tuyerePos = pos;
        }

        if (state.is(TFMGTags.TFMGBlockTags.REINFORCED_BLAST_FURNACE_WALL.tag))
            return FurnaceBlockType.REINFORCED;
        if (state.is(TFMGTags.TFMGBlockTags.BLAST_FURNACE_WALL.tag))
            return FurnaceBlockType.REGULAR;
        return FurnaceBlockType.NONE;
    }

    public FurnaceBlockType isValidSupport(BlockPos pos) {

        BlockState state = level.getBlockState(pos);

        if (state.is(TFMGTags.TFMGBlockTags.REINFORCED_BLAST_FURNACE_SUPPORT.tag))
            return FurnaceBlockType.REINFORCED;
        if (state.is(TFMGTags.TFMGBlockTags.BLAST_FURNACE_SUPPORT.tag))
            return FurnaceBlockType.REGULAR;
        return FurnaceBlockType.NONE;
    }



    enum FurnaceBlockType {
        NONE,
        REGULAR,
        REINFORCED

    }
}