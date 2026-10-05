package com.drmangotea.tfmg.content.machinery.metallurgy.blast_stove;


import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.base.capability.FluidSlots;
import com.drmangotea.tfmg.base.lang.TFMGLang;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.recipes.HotBlastRecipe;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.drmangotea.tfmg.registry.TFMGRecipeTypes;
import com.drmangotea.tfmg.registry.TFMGTags;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import com.simibubi.create.foundation.recipe.RecipeConditions;
import com.simibubi.create.foundation.recipe.RecipeFinder;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;


public class BlastStoveBlockEntity extends FluidTankBlockEntity implements IHaveGoggleInformation, IMultiBlockEntityContainer.Fluid, IInspectable {

    private static final int MAX_SIZE = 2;

    protected IFluidHandler primaryCapability;
    protected IFluidHandler secondaryCapability;
    public FluidTank primaryOutputInventory;
    public FluidTank secondaryOutputInventory;
    public FluidTank primaryInputInventory;
    public FluidTank secondaryInputInventory;
    // controller / lastKnownPos / updateConnectivity / syncCooldown /
    // queuedSync are inherited (protected) from FluidTankBlockEntity.
    // Re-declaring them here shadowed the parent copies: super.read()
    // populated the parent fields from the same NBT keys and super.tick()
    // ran the parent connectivity state machine on that ghost state,
    // triggering formMulti/refreshCapability every tick after reload.
    private static final Object HotBlastRecipesKey = new Object();
    private static final int SYNC_RATE = 8;
    public int timer = 0;
    // FluidTankBlockEntity#refreshCapability is package-private, so the
    // same-named method below does NOT override it: the parent's own
    // updateCapability hook in super.tick() only ever rebuilds the parent's
    // fluidCapability field, never this stove's primary/secondary handlers.
    // Those need their own deferred refresh after every read, or a reloaded
    // slave keeps serving the tanks its constructor built while controller was
    // still null — its own, permanently empty ones. Every pipe on the
    // multiblock then goes dead, the heated-air output on top first of all,
    // until a block is broken and replaced to force a formMulti.
    private boolean refreshStoveCapability;
    // Set by a world load: the inputs are checked for a fluid they do not take
    // on the next server tick, once the level and its recipes are available.
    private boolean checkForeignFluids;

    public BlastStoveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
        primaryOutputInventory = TFMGUtils.createTank(8000, true, false, this::onFluidStackChanged);
        secondaryOutputInventory = TFMGUtils.createTank(8000, true, false, this::onFluidStackChanged);
        // Each input only takes what the hot blast recipes read from it. Both
        // used to take any fluid: the moment the air ran dry, creosote from a
        // fuel pipe touching a side of the bottom layer, or hot air flowing
        // back into the top, filled the empty slot, no recipe matched again,
        // and the slot could not be pumped out, so the stove was dead for good.
        primaryInputInventory = TFMGUtils.createTank(8000, false, true, this::onFluidStackChanged, this::acceptsAir);
        secondaryInputInventory = TFMGUtils.createTank(8000, false, true, this::onFluidStackChanged, this::acceptsFuel);
        primaryCapability = new CombinedTankWrapper(primaryOutputInventory, secondaryInputInventory);
        secondaryCapability = new CombinedTankWrapper(primaryInputInventory, secondaryOutputInventory);
        updateConnectivity = false;
        height = 1;
        width = 1;
        refreshCapability();
    }

    public void updateBoilerState() {
    }

    public void updateConnectivity() {
        updateConnectivity = false;
        if (!isController())
            return;

        for (int yOffset = 0; yOffset < height; yOffset++)
            for (int xOffset = 0; xOffset < width; xOffset++)
                for (int zOffset = 0; zOffset < width; zOffset++)
                    if (level.getBlockEntity(
                            worldPosition.offset(xOffset, yOffset, zOffset)) instanceof BlastStoveBlockEntity fbe)
                        fbe.refreshCapability();


        if (level.isClientSide)
            return;
        refreshCapability();

        ConnectivityHandler.formMulti(this);
    }


    @Override
    @SuppressWarnings("removal")
    public void tick() {
        // Sync cooldown, lastKnownPos tracking and the updateConnectivity
        // check are handled by super.tick() now that the parent fields are
        // no longer shadowed. Running them here as well would process the
        // same state twice per tick.
        super.tick();

        if (refreshStoveCapability) {
            refreshStoveCapability = false;
            refreshCapability();
        }

        if (checkForeignFluids && level != null && !level.isClientSide) {
            checkForeignFluids = false;
            if (isController()) {
                boolean voided = FluidSlots.voidForeignFluid(primaryInputInventory, this, "air input");
                voided |= FluidSlots.voidForeignFluid(secondaryInputInventory, this, "fuel input");
                if (voided)
                    timer = 0;
            }
        }

        // Running the recipe drains the input tanks and fills the output ones,
        // and advances the timer that paces it. All of that is authoritative
        // state: the client used to run it against its own copy of the tanks,
        // on its own timer, so the amounts it showed drifted from the server
        // and jumped back on every sync. Both the tanks and Timer are written
        // to the client, so it has nothing to compute here. The casting basin,
        // firebox, winding machine and polarizer were taken off the client for
        // exactly this reason; the blast stove was missed.
        if (level == null || (level.isClientSide && !isVirtual()))
            return;

        if (isController() && !primaryInputInventory.isEmpty() && !secondaryInputInventory.isEmpty() && primaryOutputInventory.getSpace() != 0 && secondaryOutputInventory.getSpace() != 0) {
            HotBlastRecipe recipe = getMatchingRecipes();
            if (recipe != null) {
                if (timer >= getSpeedModifier() / (getTotalTankSize() * 0.3f)) {
                    if ((primaryOutputInventory.isEmpty() || primaryOutputInventory.getFluid().isFluidEqual(recipe.getPrimaryResult())) && (secondaryOutputInventory.isEmpty() || secondaryOutputInventory.getFluid().isFluidEqual(recipe.getSecondaryResult()))) {

                        // Every block of the stove converts its own share of the
                        // recipe per cycle; the flat single share of before could
                        // not keep up with one blast furnace whatever the stove
                        // size. The batch is clamped to what the input tanks can
                        // back and the output tanks can absorb, so a partly
                        // supplied stove processes what it can instead of
                        // stalling: setFluid() does not clamp to capacity, so a
                        // near-full tank must never be handed a full batch.
                        int batch = getTotalTankSize();
                        batch = clampBatch(batch, primaryInputInventory.getFluidAmount(), recipe.getPrimaryIngredient().amount());
                        batch = clampBatch(batch, secondaryInputInventory.getFluidAmount(), recipe.getSecondaryIngredient().amount());
                        batch = clampBatch(batch, primaryOutputInventory.getSpace(), recipe.getPrimaryResult().getAmount());
                        batch = clampBatch(batch, secondaryOutputInventory.getSpace(), recipe.getSecondaryResult().getAmount());

                        if (batch >= 1) {
                            primaryInputInventory.setFluid(new FluidStack(primaryInputInventory.getFluid().copy().getFluidHolder(), primaryInputInventory.getFluidAmount() - recipe.getPrimaryIngredient().amount() * batch));
                            secondaryInputInventory.setFluid(new FluidStack(secondaryInputInventory.getFluid().copy().getFluidHolder(), secondaryInputInventory.getFluidAmount() - recipe.getSecondaryIngredient().amount() * batch));


                            primaryOutputInventory.setFluid(new FluidStack(recipe.getPrimaryResult().getFluidHolder(), primaryOutputInventory.getFluidAmount() + recipe.getPrimaryResult().getAmount() * batch));
                            secondaryOutputInventory.setFluid(new FluidStack(recipe.getSecondaryResult().getFluidHolder(), secondaryOutputInventory.getFluidAmount() + recipe.getSecondaryResult().getAmount() * batch));
                            timer = 0;
                        }
                    }
                } else {
                    timer++;
                }

            }
        }

    }

    public int getSpeedModifier() {
        return 100;
    }

    @Override
    public void inspect(InspectionReport report) {
        if (level == null)
            return;
        if (!isController() && !level.isLoaded(controller)) {
            report.info("multiblock.controller_unloaded");
            return;
        }
        BlastStoveBlockEntity stove = getControllerBE();
        if (stove == null) {
            report.problem("blast_stove.no_controller");
            report.fix("blast_stove.no_controller.fix");
            return;
        }
        int size = stove.getTotalTankSize();
        report.info("blast_stove.size", stove.getWidth(), stove.getWidth(), stove.getHeight(), size);

        HotBlastRecipe recipe = stove.getMatchingRecipes();
        FluidStack air = stove.primaryInputInventory.getFluid();
        FluidStack fuel = stove.secondaryInputInventory.getFluid();

        // Air: sides of the bottom layer
        int airNeeded = recipe != null ? recipe.getPrimaryIngredient().amount() : 1;
        if (air.isEmpty()) {
            report.problem("blast_stove.no_air");
            report.fix("blast_stove.no_air.fix");
        } else if (!air.getFluid().isSame(TFMGFluids.AIR.getSource()) && recipe == null) {
            report.problem("blast_stove.wrong_air", air.getHoverName());
            report.fix("blast_stove.wrong_input.fix");
        } else if (air.getAmount() < airNeeded) {
            report.problem("blast_stove.low_air", air.getAmount(), airNeeded);
            report.fix("blast_stove.no_air.fix");
        } else {
            report.ok("blast_stove.air", air.getAmount());
        }

        // Fuel: top or bottom face
        int fuelNeeded = recipe != null ? recipe.getSecondaryIngredient().amount() : 1;
        if (fuel.isEmpty()) {
            report.problem("blast_stove.no_fuel");
            report.fix("blast_stove.no_fuel.fix");
        } else if (!fuel.getFluid().is(TFMGTags.TFMGFluidTags.BLAST_STOVE_FUEL.tag) && recipe == null) {
            report.problem("blast_stove.wrong_fuel", fuel.getHoverName());
            report.fix("blast_stove.wrong_input.fix");
        } else if (fuel.getAmount() < fuelNeeded) {
            report.problem("blast_stove.low_fuel", fuel.getAmount(), fuelNeeded);
            report.fix("blast_stove.no_fuel.fix");
        } else {
            report.ok("blast_stove.fuel", fuel.getAmount(), fuel.getHoverName());
        }

        if (recipe == null && !air.isEmpty() && !fuel.isEmpty()
                && air.getFluid().isSame(TFMGFluids.AIR.getSource()) && fuel.getFluid().is(TFMGTags.TFMGFluidTags.BLAST_STOVE_FUEL.tag)) {
            report.problem("blast_stove.no_recipe", air.getHoverName(), fuel.getHoverName());
            report.fix("blast_stove.no_recipe.fix");
        }

        // Outputs: hot air out of the top or bottom, CO2 out of the bottom sides
        FluidTank hotAir = stove.primaryOutputInventory;
        FluidTank co2 = stove.secondaryOutputInventory;
        int hotAirShare = recipe != null ? recipe.getPrimaryResult().getAmount() : 1;
        int co2Share = recipe != null ? recipe.getSecondaryResult().getAmount() : 1;
        if (hotAir.getSpace() == 0 || hotAir.getSpace() < hotAirShare) {
            report.problem("blast_stove.hot_air_full", hotAir.getFluidAmount(), hotAir.getCapacity());
            report.fix("blast_stove.hot_air_full.fix");
        } else {
            report.ok("blast_stove.hot_air_space", hotAir.getFluidAmount(), hotAir.getCapacity());
        }
        if (co2.getSpace() == 0 || co2.getSpace() < co2Share) {
            report.problem("blast_stove.co2_full", co2.getFluidAmount(), co2.getCapacity());
            report.fix("blast_stove.co2_full.fix");
        } else {
            report.ok("blast_stove.co2_space", co2.getFluidAmount(), co2.getCapacity());
        }

        // Expected hot air rate: one batch of one share per block every cycle.
        if (recipe != null && size > 0) {
            int wait = (int) Math.ceil(getSpeedModifier() / (size * 0.3f));
            int cycle = wait + 1;
            float rate = (float) recipe.getPrimaryResult().getAmount() * size / cycle;
            report.info("blast_stove.rate", String.format("%.1f", rate), cycle);
            if (stove.getWidth() < MAX_SIZE)
                report.info("blast_stove.grow");
        }
    }

    // How many recipe shares an amount of fluid (or of tank space) can back.
    // A zero recipe amount backs any batch; datapacks may omit a side.
    private static int clampBatch(int batch, int available, int perShare) {
        if (perShare <= 0)
            return batch;
        return Math.min(batch, available / perShare);
    }


    protected Object getRecipeCacheKey() {
        return HotBlastRecipesKey;
    }

    /** Whether some hot blast recipe takes this fluid as its air (first) ingredient. */
    public boolean acceptsAir(FluidStack stack) {
        return FluidSlots.acceptedByRecipes(level, getRecipeCacheKey(), TFMGRecipeTypes.HOT_BLAST.getType(),
                recipe -> List.of(((HotBlastRecipe) recipe).getPrimaryIngredient()), stack);
    }

    /** Whether some hot blast recipe takes this fluid as its fuel (second) ingredient. */
    public boolean acceptsFuel(FluidStack stack) {
        return FluidSlots.acceptedByRecipes(level, getRecipeCacheKey(), TFMGRecipeTypes.HOT_BLAST.getType(),
                recipe -> List.of(((HotBlastRecipe) recipe).getSecondaryIngredient()), stack);
    }

    protected HotBlastRecipe getMatchingRecipes() {

        List<RecipeHolder<? extends Recipe<?>>> list = RecipeFinder.get(getRecipeCacheKey(), level, RecipeConditions.isOfType(TFMGRecipeTypes.HOT_BLAST.getType()));

        FluidStack primary = primaryInputInventory.getFluid();
        FluidStack secondary = secondaryInputInventory.getFluid();
        for (RecipeHolder<? extends Recipe<?>> holder : list) {
            HotBlastRecipe recipe = (HotBlastRecipe) holder.value();
            if (recipe.getPrimaryIngredient().test(primary) && recipe.getSecondaryIngredient().test(secondary))
                return recipe;
        }

        return null;
    }

    @Override
    public BlockPos getLastKnownPos() {
        return lastKnownPos;
    }

    @Override
    public boolean isController() {
        return controller == null || worldPosition.getX() == controller.getX()
                && worldPosition.getY() == controller.getY() && worldPosition.getZ() == controller.getZ();
    }

    @Override
    public void initialize() {
        super.initialize();
        sendData();
        if (level.isClientSide)
            invalidateRenderBoundingBox();
    }

    private void onPositionChanged() {
        removeController(true);
        lastKnownPos = worldPosition;
    }

    protected void onFluidStackChanged(FluidStack newFluidStack) {
        if (!hasLevel())
            return;
        if (!level.isClientSide) {
            setChanged();
            sendData();
        }

    }


    @SuppressWarnings("unchecked")
    @Override
    public BlastStoveBlockEntity getControllerBE() {
        if (isController())
            return this;
        BlockEntity tileEntity = level.getBlockEntity(controller);
        if (tileEntity instanceof BlastStoveBlockEntity)
            return (BlastStoveBlockEntity) tileEntity;
        return null;
    }

    public void applyFluidTankSize(int blocks) {

    }

    /** Moves this block's four tanks into another stove's, as far as they fit. */
    public void transferTanksTo(BlastStoveBlockEntity other) {
        TFMGUtils.moveFluid(primaryInputInventory, other.primaryInputInventory);
        TFMGUtils.moveFluid(secondaryInputInventory, other.secondaryInputInventory);
        TFMGUtils.moveFluid(primaryOutputInventory, other.primaryOutputInventory);
        TFMGUtils.moveFluid(secondaryOutputInventory, other.secondaryOutputInventory);
    }

    public void removeController(boolean keepFluids) {
        if (level.isClientSide)
            return;
        updateConnectivity = true;
        if (!keepFluids)
            applyFluidTankSize(1);
        controller = null;
        width = 1;
        height = 1;

        onFluidStackChanged(primaryOutputInventory.getFluid());

        refreshCapability();
        setChanged();
        sendData();
    }

    public void sendDataImmediately() {
        syncCooldown = 0;
        queuedSync = false;
        sendData();
    }

    @Override
    public void sendData() {
        if (syncCooldown > 0) {
            queuedSync = true;
            return;
        }
        super.sendData();
        queuedSync = false;
        syncCooldown = SYNC_RATE;
    }


    @Override
    public void setController(BlockPos controller) {

        if (level.isClientSide && !isVirtual())
            return;
        if (controller.equals(this.controller))
            return;
        // A former controller joining a bigger stove used to keep its four
        // tanks where nothing could reach them, and its next save dropped them
        // because only controllers write their tanks. Hand them over first.
        if (isController() && !controller.equals(worldPosition)
                && level.getBlockEntity(controller) instanceof BlastStoveBlockEntity newController)
            transferTanksTo(newController);
        this.controller = controller;
        refreshCapability();
        setChanged();
        sendData();
    }

    public void refreshCapability() {
        primaryCapability = handlerForCapability();
        secondaryCapability = handlerForSecondaryCapability();
        invalidateCapabilities();

    }


    private IFluidHandler handlerForCapability() {
        return isController() ?
                new CombinedTankWrapper(primaryOutputInventory, secondaryInputInventory)
                : getControllerBE() != null ? getControllerBE().handlerForCapability() : new CombinedTankWrapper(primaryOutputInventory, secondaryInputInventory);
    }

    private IFluidHandler handlerForSecondaryCapability() {
        return isController() ?
                new CombinedTankWrapper(primaryInputInventory, secondaryOutputInventory)
                : getControllerBE() != null ? getControllerBE().handlerForSecondaryCapability() : new CombinedTankWrapper(primaryInputInventory, secondaryOutputInventory);
    }

    @Override
    public BlockPos getController() {
        return isController() ? worldPosition : controller;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        if (isController())
            return super.createRenderBoundingBox().expandTowards(width - 1, height - 1, width - 1);
        else
            return super.createRenderBoundingBox();
    }


    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);

        BlockPos controllerBefore = controller;
        int prevSize = width;
        int prevHeight = height;

        updateConnectivity = compound.contains("Uninitialized");
        controller = null;
        lastKnownPos = null;

        if (compound.contains("LastKnownPos"))
            lastKnownPos = NbtUtils.readBlockPos(compound, "LastKnownPos").get();
        if (compound.contains("Controller"))
            controller = NbtUtils.readBlockPos(compound, "Controller").get();

        if (isController()) {
            width = compound.getInt("Size");
            height = compound.getInt("Height");
            primaryOutputInventory.readFromNBT(registries, compound.getCompound("primaryOutputInventory"));
            primaryInputInventory.readFromNBT(registries, compound.getCompound("primaryInputInventory"));
            secondaryOutputInventory.readFromNBT(registries, compound.getCompound("secondaryOutputInventory"));
            secondaryInputInventory.readFromNBT(registries, compound.getCompound("secondaryInputInventory"));
            if (primaryOutputInventory.getSpace() < 0)
                primaryOutputInventory.drain(-primaryOutputInventory.getSpace(), IFluidHandler.FluidAction.EXECUTE);
        }

        // Missing key reads as 0, matching the previous behavior of old saves.
        timer = compound.getInt("Timer");

        if (!clientPacket)
            checkForeignFluids = true;

        // Deferred to the next tick rather than refreshed here: the controller
        // BE is not necessarily loaded yet at read time, and
        // handlerForCapability() silently falls back to this BE's own tanks
        // whenever getControllerBE() comes back null.
        refreshStoveCapability = true;

        if (!clientPacket)
            return;

        boolean changeOfController =
                controllerBefore == null ? controller != null : !controllerBefore.equals(controller);
        if (changeOfController || prevSize != width || prevHeight != height) {
            if (hasLevel())
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
            invalidateRenderBoundingBox();
        }

    }

    public float getFillState() {
        return (float) primaryOutputInventory.getFluidAmount() / primaryOutputInventory.getCapacity();
    }


    @Override
    @SuppressWarnings("removal")
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

        if (getControllerBE() == null) {
            return false;
        }

        LangBuilder mb = CreateLang.translate("generic.unit.millibuckets");

        TFMGTexts.header("blast_stove").forGoggles(tooltip);
        TFMGLang.builder()
                .add(TFMGLang.translate("goggles.blast_stove.tank1"))
                .add(TFMGLang.number(getControllerBE().secondaryCapability.getFluidInTank(0).getAmount())
                        .add(mb)
                        .add(getControllerBE().secondaryCapability.getFluidInTank(0).getFluid() == Fluids.EMPTY ? TFMGLang.text("") :  TFMGLang.text(" "+getControllerBE().secondaryCapability.getFluidInTank(0).getDisplayName().getString()))
                        .style(ChatFormatting.DARK_GREEN))
                .text(ChatFormatting.GRAY, " / ")
                .add(TFMGLang.number(8000)
                        .add(mb)
                        .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
        TFMGLang.builder()
                .add(TFMGLang.translate("goggles.blast_stove.tank2"))
                .add(TFMGLang.number(getControllerBE().primaryCapability.getFluidInTank(1).getAmount())
                        .add(mb)
                        .add(getControllerBE().primaryCapability.getFluidInTank(1).getFluid() == Fluids.EMPTY ? TFMGLang.text("") :  TFMGLang.text(" "+getControllerBE().primaryCapability.getFluidInTank(1).getDisplayName().getString()))
                        .style(ChatFormatting.DARK_GREEN))
                .text(ChatFormatting.GRAY, " / ")
                .add(TFMGLang.number(8000)
                        .add(mb)
                        .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
        TFMGLang.builder()
                .add(TFMGLang.translate("goggles.blast_stove.tank3"))
                .add(TFMGLang.number(getControllerBE().primaryCapability.getFluidInTank(0).getAmount())
                        .add(mb)
                        .add(getControllerBE().primaryCapability.getFluidInTank(0).getFluid() == Fluids.EMPTY ? TFMGLang.text("") :  TFMGLang.text(" "+getControllerBE().primaryCapability.getFluidInTank(0).getDisplayName().getString()))
                        .style(ChatFormatting.YELLOW))
                .text(ChatFormatting.GRAY, " / ")
                .add(TFMGLang.number(8000)
                        .add(mb)
                        .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
        TFMGLang.builder()
                .add(TFMGLang.translate("goggles.blast_stove.tank4"))
                .add(TFMGLang.number(getControllerBE().secondaryCapability.getFluidInTank(1).getAmount())
                        .add(mb)
                        .add(getControllerBE().secondaryCapability.getFluidInTank(1).getFluid() == Fluids.EMPTY ? TFMGLang.text("") :  TFMGLang.text(" "+getControllerBE().secondaryCapability.getFluidInTank(1).getDisplayName().getString()))
                        .style(ChatFormatting.YELLOW))
                .text(ChatFormatting.GRAY, " / ")
                .add(TFMGLang.number(8000)
                        .add(mb)
                        .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
        return true;
    }


    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {

        if (updateConnectivity)
            compound.putBoolean("Uninitialized", true);

        if (lastKnownPos != null)
            compound.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
        if (!isController())
            compound.put("Controller", NbtUtils.writeBlockPos(controller));
        if (isController()) {
            compound.put("primaryOutputInventory", primaryOutputInventory.writeToNBT(registries, new CompoundTag()));
            compound.put("primaryInputInventory", primaryInputInventory.writeToNBT(registries, new CompoundTag()));
            compound.put("secondaryOutputInventory", secondaryOutputInventory.writeToNBT(registries, new CompoundTag()));
            compound.put("secondaryInputInventory", secondaryInputInventory.writeToNBT(registries, new CompoundTag()));
            compound.putInt("Size", width);
            compound.putInt("Height", height);
        }
        compound.putInt("Timer", timer);

        forEachBehaviour(tb -> tb.write(compound, registries, clientPacket));

        if (!clientPacket)
            return;
        if (queuedSync)
            compound.putBoolean("LazySync", true);

    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                TFMGBlockEntities.BLAST_STOVE.get(),
                (be, context) -> {
                    // Hand out the controller's handlers, never this block's
                    // own. A part block keeps empty local tanks, so after a
                    // chunk reload every pipe attached to a multi-block stove
                    // was talking to an inventory that led nowhere.
                    BlastStoveBlockEntity controller = be.getControllerBE();
                    if (controller == null)
                        return null;

                    if (controller.primaryCapability == null || controller.secondaryCapability == null)
                        controller.refreshCapability();

                    // A null context means "no particular side" — e.g.
                    // ComputerCraft's peripheral scan queries the block
                    // capability without a direction. Dereferencing it
                    // (context.getAxis()) instantly crashed the server the
                    // moment a Computer was placed next to the blast stove.
                    // Treat null as the top/Y face and fall back to the
                    // primary handler.
                    if (context == null || context.getAxis() == Direction.Axis.Y)
                        return controller.primaryCapability;
                    if (be.getController().getY() == be.getBlockPos().getY())
                        return controller.secondaryCapability;

                    return null;
                }
        );
    }


    public FluidTank getTankInventory() {
        return primaryOutputInventory;
    }


    public static int getCapacityMultiplier() {
        return AllConfigs.server().fluids.fluidTankCapacity.get() * 1000;
    }

    public static int getMaxHeight() {
        return AllConfigs.server().fluids.fluidTankMaxHeight.get();
    }


    @Override
    public void preventConnectivityUpdate() {
        updateConnectivity = false;
    }

    @Override
    public void notifyMultiUpdated() {
        // Do NOT re-set updateConnectivity here: ConnectivityHandler calls
        // preventConnectivityUpdate() right before notifyMultiUpdated(), and
        // re-arming the flag forced a redundant formMulti on the next tick
        // and leaked "Uninitialized" into every post-formation sync packet.
        onFluidStackChanged(primaryOutputInventory.getFluid());
        updateBoilerState();
        setChanged();

        sendData();
        setChanged();
    }

    @Override
    public Object modifyExtraData(Object data) {
        if (data instanceof Boolean windows) {
            windows |= window;
            return windows;
        }
        return data;
    }

    @Override
    public Direction.Axis getMainConnectionAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public int getMaxLength(Direction.Axis longAxis, int width) {
        if (longAxis == Direction.Axis.Y)
            return getMaxHeight();
        return getMaxWidth();
    }

    // MAX_SIZE was declared and never read, so the width fell through to
    // Create's fluid tank config and a stove three blocks wide could form.
    @Override
    public int getMaxWidth() {
        return MAX_SIZE;
    }
}

