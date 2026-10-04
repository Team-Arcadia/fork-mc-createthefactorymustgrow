package com.drmangotea.tfmg.content.engines.types;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.base.lang.TFMGLang;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.content.engines.base.AbstractEngineBlockEntity;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.content.engines.base.EngineComponentsInventory;
import com.drmangotea.tfmg.content.engines.base.EngineProperties;
import com.drmangotea.tfmg.content.engines.engine_controller.EngineControllerBlockEntity;
import com.drmangotea.tfmg.content.engines.types.regular_engine.RegularEngineBlockEntity;
import com.drmangotea.tfmg.content.engines.upgrades.EnginePipingUpgrade;
import com.drmangotea.tfmg.content.engines.upgrades.EngineUpgrade;
import com.drmangotea.tfmg.content.engines.upgrades.TransmissionUpgrade;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.drmangotea.tfmg.content.engines.base.EngineBlock.ENGINE_STATE;
import static com.drmangotea.tfmg.content.engines.base.EngineBlock.EngineState.NORMAL;
import static com.drmangotea.tfmg.content.engines.base.EngineBlock.EngineState.SHAFT;
import static com.drmangotea.tfmg.content.engines.base.EngineBlock.SHAFT_FACING;
import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

public abstract class AbstractSmallEngineBlockEntity extends AbstractEngineBlockEntity implements IInspectable {

    public Optional<? extends EngineUpgrade> upgrade = Optional.empty();
    public TransmissionUpgrade.TransmissionState shift = TransmissionUpgrade.TransmissionState.NEUTRAL;
    public boolean clutchPressed = false;


    public int oil = 0;
    public int coolingFluid = 0;

    public EngineComponentsInventory componentsInventory;

    public BlockPos controller = getBlockPos();
    public boolean connectNextTick = true;
    public boolean delayedConnect = false;
    public List<Long> engines = new ArrayList<>();
    public int engineNumber = 0;

    public AbstractSmallEngineBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        componentsInventory = new EngineComponentsInventory(this, EngineProperties.commonRegularComponents());
    }

    public int getFuelConsumption() {

        if (rpm == 0)
            return 0;

        float oilModifier = oil > 0 ? 0.7f : 1f;

        float coolingFluidModifier = coolingFluid > 0 ? 0.7f : 1f;

        // Truncate after the length multiply, not before: the per-cylinder
        // term sits between 0.4 and 8, and casting it alone floored the most
        // efficient type/fuel pairs (boxer and radial on gasoline, kerosene,
        // propane) to a flat zero whatever the engine length. The floor keeps
        // a running engine burning at least 1 mB per drain even under the
        // oil and cooling discounts - no rotation from an empty tank.
        float consumption = 12.5f * (1 / efficiencyModifier()) * getSpeedEfficiency() * highestSignal / 15 * oilModifier * coolingFluidModifier * (engineLength() + 1);
        return Math.max(1, (int) consumption);
    }

    public void detashEngines() {
    }

    public void setBlockStates(AbstractSmallEngineBlockEntity be, BlockPos last) {


        if (!be.isController()) {
            level.setBlock(be.getBlockPos(), level.getBlockState(be.getBlockPos()).setValue(SHAFT_FACING, getBlockState().getValue(SHAFT_FACING).getOpposite()), 2);
        }

    }

    public boolean hasAllComponents() {

        if (level.getBlockEntity(controller) instanceof AbstractSmallEngineBlockEntity be) {
            return be.nextComponent() == Ingredient.EMPTY;
        }

        return false;
    }

    public boolean hasUpgrade() {
        return upgrade.isPresent();
    }

    @Override
    public int voltageGeneration() {

        if (upgrade.isPresent() && upgrade.get().getItem() == TFMGBlocks.GENERATOR.asItem())
            return (int) (20 * (rpm / 500));

        return 0;
    }

    @Override
    public int powerGeneration() {
        if (upgrade.isPresent() && upgrade.get().getItem() == TFMGBlocks.GENERATOR.asItem())
            return (int) rpm;

        return 0;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        upgrade.ifPresent(engineUpgrade -> engineUpgrade.lazyTickUpgrade(this));

        if (!canWork())
            return;
        if(rpm==0)
            return;
        if (level.random.nextInt(45) == 0) {
            if (oil > 0)
                oil--;
        }
        if (level.random.nextInt(45) == 0) {
            if (coolingFluid > 0)
                coolingFluid--;
        }


    }

    @Override
    public float calculateAddedStressCapacity() {
        float stress = super.calculateAddedStressCapacity() + (torque);

        return hasTwoShafts() ? stress / 2 : stress;
    }

    public boolean hasTwoShafts() {

        if (!isController())
            return getControllerBE().hasTwoShafts();
        if (this.getBlockState().getValue(ENGINE_STATE) == SHAFT) {
            BlockPos pos = getBlockPos().relative(this.getBlockState().getValue(SHAFT_FACING).getOpposite(), engineLength() );
            // The far end is derived from engines.size(), so a half-built or
            // partially broken engine can point this at air. getValue would then
            // throw out of the stress calculation, which runs on the kinetic
            // path; a block without the property is simply not a second shaft.
            BlockState farEnd = level.getBlockState(pos);
            if (farEnd.hasProperty(ENGINE_STATE) && farEnd.getValue(ENGINE_STATE) == SHAFT)
                return true;
        }
        return false;
    }

    @Override
    public void neighbourChanged() {
        if (controller == null)
            return;

        super.neighbourChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.hasUpgrade() && this.upgrade.get().getItem() == TFMGBlocks.INDUSTRIAL_PIPE.asItem()) {
            ((EnginePipingUpgrade) this.upgrade.get()).findTank(this);
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putLong("Controller", controller.asLong());
        compound.putString("Shift", shift.name());

        if (upgrade.isPresent())
            compound.put("UpgradeItem", upgrade.get().getItem().getDefaultInstance().saveOptional(registries));
        compound.put("Components", componentsInventory.serializeNBT(registries));
        compound.putInt("Oil", oil);
        compound.putInt("CoolingFluid", coolingFluid);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (compound.contains("UpgradeItem") && ItemStack.parse(registries, compound.getCompound("UpgradeItem")).isPresent()) {
            ItemStack stack = ItemStack.parse(registries, compound.getCompound("UpgradeItem")).get();
            // ofNullable: an unknown saved upgrade item (removed mod content,
            // datapack change) must degrade to "no upgrade", not NPE while
            // deserializing the block entity and corrupt the chunk load.
            upgrade = Optional.ofNullable(EngineUpgrade.getUpgrades().get(stack.getItem()));

        } else {
            // A removed upgrade writes no key; without this, other players
            // kept seeing it on the engine until they relogged.
            upgrade = Optional.empty();
        }
        // An unknown gear name (older save, hand-edited data) falls back to
        // neutral instead of throwing out of the chunk load.
        if (!compound.getString("Shift").isEmpty()) {
            try {
                shift = TransmissionUpgrade.TransmissionState.valueOf(compound.getString("Shift"));
            } catch (IllegalArgumentException e) {
                shift = TransmissionUpgrade.TransmissionState.NEUTRAL;
            }
        }
        oil = compound.getInt("Oil");
        coolingFluid = compound.getInt("CoolingFluid");
        componentsInventory.deserializeNBT(registries, compound.getCompound("Components"));
        super.read(compound, registries, clientPacket);
        controller = BlockPos.of(compound.getLong("Controller"));
    }

    public int engineLength() {
        return engines.size();
    }

    @Override
    public boolean canWork() {

        if (!nextComponent().isEmpty())
            return false;

        return super.canWork();
    }

    // Every block of a multiblock engine carries and requires its own set of
    // components: five engines joined end to end used to cost the same five
    // items as one, because only the master's inventory existed. Per-block
    // progress also survives the master re-election a merge triggers, which
    // used to strand the old master's items in an inventory nothing read.
    public AbstractSmallEngineBlockEntity nextIncompleteEngine() {
        if (!isController())
            return null;
        for (AbstractSmallEngineBlockEntity be : getEnginesInOrder()) {
            for (int i = 0; i < be.componentsInventory.getSlots(); i++) {
                if (be.componentsInventory.getStackInSlot(i).isEmpty())
                    return be;
            }
        }
        return null;
    }

    // The chain in build order: the master first, then each block behind it.
    // getEngines() lists the satellites before the master, which made the
    // components fill back to front for no visible reason.
    public List<AbstractSmallEngineBlockEntity> getEnginesInOrder() {
        List<AbstractSmallEngineBlockEntity> ordered = new ArrayList<>();
        AbstractSmallEngineBlockEntity master = getControllerBE();
        ordered.add(master);
        for (Long l : master.engines)
            if (level.getBlockEntity(BlockPos.of(l)) instanceof AbstractSmallEngineBlockEntity be && be != master)
                ordered.add(be);
        return ordered;
    }

    // 1-based position of the block still waiting for components, 0 when the
    // whole chain is built. A five block engine asks for five sets now, and
    // without saying which set it is on the repeats looked like a stuck machine.
    public int nextIncompleteIndex() {
        List<AbstractSmallEngineBlockEntity> ordered = getEnginesInOrder();
        for (int i = 0; i < ordered.size(); i++) {
            AbstractSmallEngineBlockEntity be = ordered.get(i);
            for (int s = 0; s < be.componentsInventory.getSlots(); s++)
                if (be.componentsInventory.getStackInSlot(s).isEmpty())
                    return i + 1;
        }
        return 0;
    }

    // Does any block of the chain carry a shaft to export rotation? Without one
    // the engine burns fuel and delivers nothing, with nothing on screen to say so.
    public boolean hasAnyOutputShaft() {
        for (Long l : getAllEngines())
            if (level.getBlockEntity(BlockPos.of(l)) instanceof AbstractSmallEngineBlockEntity be && be.hasOutputShaft())
                return true;
        return false;
    }

    public boolean hasAnyUpgrade() {
        for (AbstractSmallEngineBlockEntity be : getControllerBE().getEngines())
            if (be.upgrade.isPresent())
                return true;
        return false;
    }

    public Ingredient nextComponent() {
        AbstractSmallEngineBlockEntity be = nextIncompleteEngine();
        if (be == null)
            return Ingredient.EMPTY;
        for (int i = 0; i < be.componentsInventory.getSlots(); i++) {
            if (be.componentsInventory.getStackInSlot(i).isEmpty()) {
                return be.componentsInventory.components.get(i);
            }
        }

        return Ingredient.EMPTY;
    }

    // Re-read the chain's redstone signal from the world. Breaking a block in
    // the middle of a row splits it into two engines, and neither half re-read
    // its own signal: the half holding the lever dropped to zero while the
    // other half kept a stale 15 and went on turning with nothing driving it.
    public void refreshRedstoneSignal() {
        if (!isController() || hasEngineController())
            return;
        int newSignal = level.getBestNeighborSignal(getBlockPos());
        for (long posLong : engines)
            newSignal = Math.max(level.getBestNeighborSignal(BlockPos.of(posLong)), newSignal);
        highestSignal = newSignal / 15f;
    }

    protected void analogSignalChanged() {

        if (controller == null)
            return;



        if (hasEngineController()) {
            return;
        }

        getControllerBE().updateRotation();
        getControllerBE().updateGeneratedRotation();
        int newSignal = level.getBestNeighborSignal(getBlockPos());

        signal = newSignal;

        if (!isController()) {

            if (level.getBlockEntity(controller) instanceof AbstractSmallEngineBlockEntity be) {
                be.analogSignalChanged();
                return;
            }
        }

        for (long posLong : engines) {
            BlockPos pos = BlockPos.of(posLong);
            newSignal = Math.max(level.getBestNeighborSignal(pos), newSignal);

        }

        newSignal = Math.max(level.getBestNeighborSignal(controller), newSignal);
        highestSignal = newSignal/15f;
        updateRotation();

    }

    @Override
    public IFluidHandler handlerForCapability() {


        return isController() || getControllerBE() == this ? new CombinedTankWrapper(fuelTank, exhaustTank)
                : getControllerBE().handlerForCapability();
    }


    public void updateRotation() {

        if (!isController()) {
            if (level.getBlockEntity(controller) instanceof AbstractSmallEngineBlockEntity be)
                be.updateRotation();
            return;
        }

        if (fuelTank.isEmpty()) {
            rpm = 0;
            torque = 0;
        }

        List<Long> allEngines = new ArrayList<>(engines);
        allEngines.add(controller.asLong());
        for (TagKey<Fluid> fluidTag : getSupportedFuels()) {


            if (fuelTank.getFluid().getFluid().is(fluidTag)) {
                if (!canWork()) {
                    allEngines.forEach(l -> {
                        BlockPos pos = BlockPos.of(l);
                        if (level.getBlockEntity(pos) instanceof AbstractEngineBlockEntity be) {
                            be.rpm = 0;
                            be.torque = 0;
                            be.updateGeneratedRotation();
                        }

                    });
                    return;
                }
                allEngines.forEach(l -> {
                    BlockPos pos = BlockPos.of(l);
                    if (level.getBlockEntity(pos) instanceof AbstractEngineBlockEntity be) {
                        be.rpm = 4000 * speedModifier() * highestSignal ;
                        be.torque = 15 * torqueModifier() * highestSignal* engineLength();
                        be.updateGeneratedRotation();
                    }
                });
                return;
            }
        }
        updateGeneratedRotation();
        getAllEngines().forEach(l -> {
            if (hasLevel())
                if (level.getBlockEntity(BlockPos.of(l)) instanceof AbstractEngineBlockEntity be) {
                    be.updateGeneratedRotation();
                }
        });
    }

    public boolean canGenerateSpeed() {
        if (getBlockState().getValue(ENGINE_STATE) != SHAFT)
            return false;


        return true;
    }


    @Override
    public float getGeneratedSpeed() {

        if (!canGenerateSpeed())
            return 0;
        float speed;


        if (hasLevel()) {
            if (getControllerBE().fuelTank.isEmpty())
                return 0;

            if (!getControllerBE().canWork())
                return 0;

            speed = rpm / 40;
            if (reverse)
                speed = speed * -1;

            if (getControllerBE().hasEngineController()) {

                if (getControllerBE().hasTwoShafts())


                    speed = switch (getControllerBE().shift) {
                        case REVERSE -> speed * -0.3f;
                        case NEUTRAL -> 0;
                        case SHIFT_1 -> speed * 0.2f;
                        case SHIFT_2 -> speed * 0.4f;
                        case SHIFT_3 -> speed * 0.6f;
                        case SHIFT_4 -> speed * 0.8f;
                        case SHIFT_5 -> speed;
                        case SHIFT_6 -> speed * 1.2f;
                    };


            }

            return convertToDirection(Math.min((int) speed, 256), getBlockState().getValue(HORIZONTAL_FACING));
        }
        return 0;
    }

    // What the shaft actually turns at, as a Create multimeter would read it.
    // The raw rpm field is an internal figure 40 times larger; showing it in
    // the goggles made a 181 RPM engine claim 7280.
    public float outputSpeed() {
        for (Long l : getAllEngines())
            if (level.getBlockEntity(BlockPos.of(l)) instanceof AbstractSmallEngineBlockEntity be) {
                float speed = be.getGeneratedSpeed();
                if (speed != 0)
                    return Math.abs(speed);
            }
        return 0;
    }

    // Stress capacity this engine actually delivers. Create scales a source's
    // capacity by its speed, but only a block carrying an output shaft sits on
    // the network the player taps. A turbine answers canGenerateSpeed on every
    // one of its blocks, so summing them all claimed five times the real figure
    // on a five-block turbine, while a regular engine, which generates from the
    // shafted block alone, happened to read correctly. Both ends of a two-shaft
    // engine count, and each already carries half the capacity.
    public float outputStress() {
        float total = 0;
        for (Long l : getAllEngines())
            if (level.getBlockEntity(BlockPos.of(l)) instanceof AbstractSmallEngineBlockEntity be
                    && be.hasOutputShaft())
                total += be.calculateAddedStressCapacity() * Math.abs(be.getGeneratedSpeed());
        return total;
    }

    // A block exports rotation only through an installed shaft.
    public boolean hasOutputShaft() {
        BlockState state = getBlockState();
        return state.hasProperty(ENGINE_STATE) && state.getValue(ENGINE_STATE) == SHAFT;
    }

    @Override
    public void tankUpdated(FluidStack stack, boolean fuel) {
        if (stack.getFluid().isSame(TFMGFluids.CARBON_DIOXIDE.get()) && stack.getAmount() >= exhaustTank.getSpace())
            updateRotation();
        super.tankUpdated(stack, fuel);
    }

    public boolean insertItem(ItemStack itemStack, boolean shifting, Player player, InteractionHand hand) {
        Direction shaft_facing = getBlockState().getValue(SHAFT_FACING);
        if (itemStack.is(AllBlocks.SHAFT.asItem()) && getBlockState().getValue(ENGINE_STATE) == NORMAL && !(level.getBlockEntity(getBlockPos().relative(shaft_facing)) instanceof AbstractEngineBlockEntity)) {
            playInsertionSound();
            level.setBlock(getBlockPos(), getBlockState().setValue(ENGINE_STATE, SHAFT), 2);
            itemStack.shrink(1);
            updateRotation();
            setChanged();
            sendData();
            return true;
        }
        if (itemStack.is(TFMGItems.SCREWDRIVER.get())) {
            for (int i = componentsInventory.components.size() - 1; i >= 0; i--) {
                if (!componentsInventory.getItem(i).isEmpty()) {
                    dropItem(componentsInventory.getItem(i));
                    componentsInventory.setStackInSlot(i, ItemStack.EMPTY);
                    playRemovalSound();
                    updateRotation();
                    setChanged();
                    sendData();
                    return true;
                }
            }
        }

        if (itemStack.is(TFMGItems.COOLING_FLUID_BOTTLE.get())) {

            if (level.getBlockEntity(controller) instanceof AbstractSmallEngineBlockEntity be) {

                // A bottle that has never been filled carries no AMOUNT at all,
                // and unboxing it crashed the game. The capacity check also has
                // to read the controller's level, not this block's copy of it,
                // or the engine takes far more than the 2000 mB it can hold.
                Integer amount = itemStack.get(TFMGDataComponents.AMOUNT);
                if (amount == null)
                    return false;

                int toDrain = Math.min(2000 - be.coolingFluid, amount);
                itemStack.set(TFMGDataComponents.AMOUNT, amount - toDrain);
                be.coolingFluid += toDrain;
                level.playSound(null, getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f);
                be.setChanged();
                be.sendData();
                return true;
            }
        }
        if (itemStack.is(TFMGItems.OIL_CAN.get())) {
            if (level.getBlockEntity(controller) instanceof AbstractSmallEngineBlockEntity be) {
                Integer amount = itemStack.get(TFMGDataComponents.AMOUNT);
                if (amount == null)
                    return false;

                int toDrain = Math.min(2000 - be.oil, amount);
                itemStack.set(TFMGDataComponents.AMOUNT, amount - toDrain);
                be.oil += toDrain;
                level.playSound(null, getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f);
                updateRotation();
                be.setChanged();
                be.sendData();
                return true;
            }
        }
        // The bucket branches used to fill this block instead of the controller,
        // so a bucket emptied into any block but the first was simply lost.
        // consumeBucket takes ONE bucket and hands back the empty: replacing
        // the whole held stack destroyed up to 15 buckets in one click.
        if (itemStack.is(TFMGFluids.COOLING_FLUID.getBucket().get())) {
            if (player == null)
                return false;
            if (level.getBlockEntity(controller) instanceof AbstractSmallEngineBlockEntity be) {
                if (be.coolingFluid > 1000)
                    return false;
                be.coolingFluid += 1000;
                consumeBucket(itemStack, player, hand);
                level.playSound(null, getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f);
                updateRotation();
                be.setChanged();
                be.sendData();
                return true;
            }
        }
        if (itemStack.is(TFMGFluids.LUBRICATION_OIL.getBucket().get())) {
            if (player == null)
                return false;
            if (level.getBlockEntity(controller) instanceof AbstractSmallEngineBlockEntity be) {
                if (be.oil > 1000)
                    return false;
                be.oil += 1000;
                consumeBucket(itemStack, player, hand);
                level.playSound(null, getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f);
                updateRotation();
                be.setChanged();
                be.sendData();
                return true;
            }
        }
        if (upgrade.isEmpty())
            if (EngineUpgrade.getUpgrades().containsKey(itemStack.getItem())) {
                Optional<? extends EngineUpgrade> itemUpgrade = EngineUpgrade.getUpgrades().get(itemStack.getItem()).createUpgrade();

                if (itemUpgrade.isPresent() && isUpgradeFirst(itemUpgrade.get())) {
                    // Read the stored controller link BEFORE the stack is spent:
                    // shrink(1) empties a lone transmission, and an empty stack
                    // carries no data components, so the link read as absent and
                    // the controller was never bound to the engine at all.
                    Long linkedController = itemStack.get(TFMGDataComponents.POSITION);
                    upgrade = itemUpgrade;
                    playInsertionSound();
                    updateRotation();
                    upgrade.ifPresent(u -> u.updateUpgrade(this));
                    itemStack.shrink(1);
                    if (upgrade.isPresent())
                        if (upgrade.get() instanceof TransmissionUpgrade) {
                            if (linkedController != null) {
                                BlockPos pos = BlockPos.of(linkedController);
                                if (level.getBlockEntity(pos) instanceof EngineControllerBlockEntity engineControllerBE) {

                                    this.getControllerBE().updateGeneratedRotation();

                                    // The multiblock master pointer and the linked
                                    // engine controller are different fields; writing
                                    // the controller block's position into `controller`
                                    // detached this engine from its own multiblock.
                                    getControllerBE().engineController = pos;
                                    getControllerBE().setChanged();
                                    getControllerBE().sendData();
                                    engineControllerBE.enginePos = this.getBlockPos();
                                    engineControllerBE.engine = null;
                                    engineControllerBE.setChanged();
                                    engineControllerBE.sendData();
                                    getControllerBE().highestSignal = 0;
                                }
                            }
                        }
                    setChanged();
                    sendData();
                    return true;
                }
            }

        if (!isController())
            return false;
        if (nextComponent().test(itemStack)) {
            AbstractSmallEngineBlockEntity target = nextIncompleteEngine();
            if (target != null && target.componentsInventory.insertItem(itemStack)) {
                if (!itemStack.is(TFMGItems.SCREWDRIVER.get()))
                    itemStack.shrink(1);
                playInsertionSound();
                updateRotation();
                target.setChanged();
                target.sendData();
                setChanged();
                sendData();
                return true;
            }
        }
        return false;
    }

    public List<AbstractSmallEngineBlockEntity> getEngines() {

        List<AbstractSmallEngineBlockEntity> values = new ArrayList<>();

        for (Long position : getAllEngines()) {
            BlockPos pos = BlockPos.of(position);
            if (level.getBlockEntity(pos) instanceof AbstractSmallEngineBlockEntity be)
                values.add(be);
        }
        return values;

    }

    public boolean isController() {

        if (controller == null)
            controller = getBlockPos();

        if (engineNumber == 0)
            controller = getBlockPos();

        return controller.equals(getBlockPos());
    }


    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

        if (controller.asLong() == getBlockPos().asLong())
            TFMGTexts.header("engine_controller").forGoggles(tooltip);

        TFMGTexts.Engine.shift(shift.langKey).forGoggles(tooltip);
        TFMGTexts.Engine.speedEfficiency(getSpeedEfficiency()).forGoggles(tooltip);
        TFMGTexts.Engine.efficiency(efficiencyModifier()).forGoggles(tooltip);
        // The drain fires every 4th lazy tick, i.e. every 2 seconds.
        TFMGTexts.Engine.fuelConsumption(getFuelConsumption()/2f).forGoggles(tooltip);
        TFMGTexts.Engine.rpm(outputSpeed()).forGoggles(tooltip);
        TFMGTexts.Engine.length(engineLength()).forGoggles(tooltip);
        TFMGTexts.Engine.torque(torque).forGoggles(tooltip);
        TFMGTexts.Engine.stressCapacity(outputStress()).forGoggles(tooltip);
        if (isController() && !hasAnyOutputShaft())
            TFMGLang.translate("engine.no_shaft").style(ChatFormatting.GOLD).forGoggles(tooltip);
        TFMGTexts.Engine.signal((int) (highestSignal*15)).forGoggles(tooltip);
        TFMGLang.number(engineNumber).style(ChatFormatting.DARK_GREEN).forGoggles(tooltip);
        // A missing component silently blocks canWork, so say so instead of
        // dropping a bare item name into the tooltip with nothing to explain it.
        // RegularEngineBlockEntity already words it this way; the engines that
        // fall through to this shared tooltip were the ones left mute.
        if (isController() && !nextComponent().isEmpty()) {
            TFMGTexts.Engine.unfinished().forGoggles(tooltip);
            TFMGTexts.Engine.nextComponent(nextComponent().getItems()[0]).forGoggles(tooltip);
        } else if (isController() && highestSignal <= 0) {
            // Fuel alone never starts an engine: rpm is a direct multiple of the
            // redstone signal, so a fully built and fuelled engine sits at zero
            // with nothing on screen pointing at the missing signal.
            TFMGTexts.Engine.noSignal().forGoggles(tooltip);
        }

        TFMGUtils.createFluidTooltip(this, tooltip);

        return true;
    }

    private static void consumeBucket(ItemStack itemStack, Player player, InteractionHand hand) {
        if (player.getAbilities().instabuild)
            return;
        itemStack.shrink(1);
        ItemStack empty = Items.BUCKET.getDefaultInstance();
        if (itemStack.isEmpty())
            player.setItemInHand(hand, empty);
        else if (!player.getInventory().add(empty))
            player.drop(empty, false);
    }

    public boolean isUpgradeFirst(EngineUpgrade itemUpgrade) {

        // Ask the master for the chain. Called on a satellite, getEngines only
        // ever saw the master, because a satellite keeps an empty engines list:
        // the same one-per-engine upgrade could therefore be mounted on every
        // block of a multiblock, which is how four golden turbos ended up on a
        // single five block turbine.
        for (AbstractSmallEngineBlockEntity be : getControllerBE().getEngines()) {

            if (be.upgrade.isPresent() && be.upgrade.get().getItem() == itemUpgrade.getItem())
                return false;
        }
        return true;
    }

    public List<Long> getAllEngines() {
        List<Long> list = new ArrayList<>(engines);
        list.add(controller.asLong());
        return list;
    }

    public AbstractSmallEngineBlockEntity getControllerBE() {
        if (isController())
            return this;
        BlockEntity blockEntity = level.getBlockEntity(controller);
        if (blockEntity instanceof AbstractSmallEngineBlockEntity)
            return (AbstractSmallEngineBlockEntity) blockEntity;
        return this;
    }


    @Override
    public void tick() {

        upgrade.ifPresent(engineUpgrade -> engineUpgrade.tickUpgrade(this));

        if (connectNextTick) {
            if (isController()) {
                connect();
                connectNextTick = false;
            }
        }

        super.tick();
    }

    // Two adjacent engine blocks belong to one chain only when they share the
    // block, the facing AND the engine type. Mixed-type chains used to form on
    // merge (the old guard was commented out), leaving a multiblock that could
    // never change type again; and the forward delegation recursed forever on
    // two engines facing each other.
    public boolean canChainWith(AbstractSmallEngineBlockEntity other) {
        if (other.getBlockState().getBlock() != this.getBlockState().getBlock())
            return false;
        if (this instanceof RegularEngineBlockEntity a && other instanceof RegularEngineBlockEntity b)
            return a.type == b.type;
        return true;
    }

    public void connect() {

        try {
            Direction facing = getBlockState().getValue(HORIZONTAL_FACING);
            Direction updateDirection = facing.getOpposite();

            if (level.getBlockEntity(getBlockPos().relative(facing)) instanceof AbstractSmallEngineBlockEntity be
                    && be.getBlockState().getValue(HORIZONTAL_FACING) == facing
                    && canChainWith(be)) {
                be.connect();
                return;
            }

            engines = new ArrayList<>();

            for (int i = 0; i < getMaxLength(); i++) {
                BlockPos pos = getBlockPos().relative(updateDirection, i);
                if (level.getBlockEntity(pos) instanceof AbstractSmallEngineBlockEntity be) {
                    if (be.getBlockState().getValue(HORIZONTAL_FACING) != facing) {
                        return;
                    }
                    if (i != 0 && !canChainWith(be)) {
                        // The chain ends where the type or the block changes;
                        // the far side keeps forming its own engine.
                        setBlockStates(this, getBlockPos().relative(updateDirection, i - 1));
                        break;
                    }

                    level.setBlock(be.getBlockPos(), be.getBlockState().setValue(SHAFT_FACING, be.getBlockPos().equals(this.getBlockPos()) ? facing : updateDirection), 2);

                    be.detashEngines();
                    engines.add(pos.asLong());

                    // level.setBlock(be.getBlockPos().above(), Blocks.GOLD_BLOCK.defaultBlockState(),3);

                    be.engineNumber = i;
                    be.engines = new ArrayList<>();
                    be.controller = getBlockPos();
                    be.refreshCapability();

                    setBlockStates(be, null);
                    be.setChanged();

                    if (be.getBlockState().getValue(ENGINE_STATE) != NORMAL && i != 0) {
                        setBlockStates(this, getBlockPos().relative(updateDirection, i - 1));
                        break;
                    }
                    if (i == getMaxLength() - 1)
                        setBlockStates(this, getBlockPos().relative(updateDirection, i));


                } else {
                    setBlockStates(this, getBlockPos().relative(updateDirection, i - 1));
                    break;
                }
            }

            refreshRedstoneSignal();
            updateGeneratedRotation();
            updateRotation();
            setChanged();
            sendData();

        } catch (StackOverflowError ignored) {

        }

    }

    @Override
    public void destroy() {
        super.destroy();
        // Each block owns its share of the build cost now, so breaking one
        // must give that share back; it used to vanish with the block.
        for (int i = 0; i < componentsInventory.getSlots(); i++) {
            if (!componentsInventory.getStackInSlot(i).isEmpty())
                dropItem(componentsInventory.getStackInSlot(i));
        }
        // A turbo, generator or transmission mounted on this block is part of
        // what the player paid for. Only the wrench used to hand it back, so
        // breaking the block simply ate it.
        if (upgrade.isPresent()) {
            dropItem(upgradeDropStack(getControllerBE().engineController));
            upgrade = Optional.empty();
        }
    }

    // The upgrade item as it should be handed back. A transmission keeps the
    // controller it was bound to: a plain default instance came back blank, so
    // the link had to be made again after every removal.
    public ItemStack upgradeDropStack(BlockPos linkedController) {
        if (upgrade.isEmpty())
            return ItemStack.EMPTY;
        ItemStack stack = upgrade.get().getItem().getDefaultInstance();
        if (upgrade.get() instanceof TransmissionUpgrade && linkedController != null)
            stack.set(TFMGDataComponents.POSITION, linkedController.asLong());
        return stack;
    }

    @Override
    public void remove() {
        super.remove();
        updateOthers();
    }

    public void updateOthers() {

        if (!isController()) {
            getControllerBE().connectNextTick = true;
        }


        Direction facing = getBlockState().getValue(HORIZONTAL_FACING);

        for (Direction direction : Direction.values()) {

            if (direction.getAxis() != facing.getAxis())
                continue;

            if (level.getBlockEntity(getBlockPos().relative(direction)) instanceof AbstractSmallEngineBlockEntity be) {
                level.setBlockAndUpdate(be.getBlockPos(), be.getBlockState().setValue(SHAFT_FACING, direction.getOpposite()));
                be.delayedConnect = true;
                be.connectNextTick = true;
                be.connect();
            }

        }

    }


    // Factory Inspector

    @Override
    public void inspect(InspectionReport report) {
        AbstractSmallEngineBlockEntity master = this;
        if (!isController()) {
            if (controller == null || !level.isLoaded(controller)
                    || !(level.getBlockEntity(controller) instanceof AbstractSmallEngineBlockEntity be)) {
                report.problem("engine.no_master");
                report.fix("engine.no_master.fix");
                return;
            }
            master = be;
        }
        master.inspectEngine(report);
    }

    /** The chain in build order, master first, skipping blocks in unloaded chunks. */
    protected List<AbstractSmallEngineBlockEntity> loadedChain() {
        List<AbstractSmallEngineBlockEntity> chain = new ArrayList<>();
        chain.add(this);
        for (Long l : engines) {
            BlockPos pos = BlockPos.of(l);
            if (pos.equals(getBlockPos()) || !level.isLoaded(pos))
                continue;
            if (level.getBlockEntity(pos) instanceof AbstractSmallEngineBlockEntity be && be != this && !chain.contains(be))
                chain.add(be);
        }
        return chain;
    }

    protected Component engineTypeName() {
        return getBlockState().getBlock().getName();
    }

    /** Pistons, cylinders or turbine blades; engines without them have nothing to check. */
    protected void inspectCylinders(InspectionReport report, List<AbstractSmallEngineBlockEntity> chain) {
    }

    /** What decides which fuels this engine burns, for the fuel lines. */
    protected Component fuelSourceName() {
        return engineTypeName();
    }

    protected void inspectEngine(InspectionReport report) {
        List<AbstractSmallEngineBlockEntity> chain = loadedChain();
        report.info("engine.summary", engineTypeName(), chain.size());

        components:
        for (int i = 0; i < chain.size(); i++) {
            AbstractSmallEngineBlockEntity be = chain.get(i);
            for (int s = 0; s < be.componentsInventory.getSlots(); s++) {
                if (!be.componentsInventory.getStackInSlot(s).isEmpty())
                    continue;
                ItemStack[] items = be.componentsInventory.components.get(s).getItems();
                Component name = items.length > 0 ? items[0].getHoverName() : Component.literal("?");
                report.problem("engine.missing_component", i + 1, chain.size(), name, coords(be.getBlockPos()));
                report.fix("engine.missing_component.fix", name);
                break components;
            }
        }

        inspectCylinders(report, chain);

        List<TagKey<Fluid>> fuels = getSupportedFuels();
        if (fuelTank.isEmpty()) {
            report.problem("engine.no_fuel");
            if (fuels.isEmpty())
                report.fix("engine.no_fuel.fix_unknown");
            else
                report.fix("engine.no_fuel.fix", fuelList(fuels));
        } else if (isFuelSupported()) {
            report.ok("engine.fuel_ok", fuelTank.getFluid().getHoverName(), fuelTank.getFluidAmount());
        } else if (fuels.isEmpty()) {
            report.problem("engine.no_fuel_list", fuelSourceName());
            report.fix("engine.no_fuel_list.fix");
        } else {
            report.problem("engine.wrong_fuel", fuelTank.getFluid().getHoverName(), fuelSourceName());
            report.fix("engine.wrong_fuel.fix", fuelList(fuels));
        }

        if (exhaustTank.getSpace() == 0) {
            report.problem("engine.exhaust_full");
            report.fix("engine.exhaust_full.fix");
        }

        if (engineController != null) {
            if (!level.isLoaded(engineController)
                    || !(level.getBlockEntity(engineController) instanceof EngineControllerBlockEntity linked)) {
                report.problem("engine.controller_missing", coords(engineController));
                report.fix("engine.controller_missing.fix");
            } else {
                report.info("engine.controlled_by", coords(engineController));
                if (!linked.engineStarted) {
                    report.problem("engine.controller_stopped");
                    report.fix("engine.controller_stopped.fix");
                } else if (highestSignal <= 0) {
                    report.problem("engine.no_throttle");
                    report.fix("engine.no_throttle.fix");
                }
                BlockPos farEnd = getBlockPos().relative(getBlockState().getValue(SHAFT_FACING).getOpposite(), engineLength());
                if (shift == TransmissionUpgrade.TransmissionState.NEUTRAL && level.isLoaded(farEnd) && hasTwoShafts()) {
                    report.problem("engine.neutral");
                    report.fix("engine.neutral.fix");
                }
            }
        } else if (highestSignal <= 0) {
            report.problem("engine.no_signal");
            report.fix("engine.no_signal.fix");
        } else {
            report.ok("engine.signal", Math.round(highestSignal * 15));
        }

        boolean shaft = false;
        for (AbstractSmallEngineBlockEntity be : chain)
            shaft |= be.hasOutputShaft();
        if (!shaft) {
            report.problem("engine.no_shaft");
            report.fix("engine.no_shaft.fix");
        }

        report.info("engine.lubrication", oil, coolingFluid);
        if (oil <= 0 || coolingFluid <= 0)
            report.fix("engine.lubrication.fix");

        for (AbstractSmallEngineBlockEntity be : chain)
            be.upgrade.ifPresent(u -> report.info("engine.upgrade", u.getItem().getDescription()));
    }

    public float getUpgradeSpeedModifier() {
        float modifier = 1;
        for (AbstractSmallEngineBlockEntity be : getEngines()) {
            if (be.upgrade.isPresent())
                modifier *= be.upgrade.get().getSpeedModifier(this);
        }
        return modifier;
    }

    public float getUpgradeTorqueModifier() {
        float modifier = 1;
        for (AbstractSmallEngineBlockEntity be : getEngines()) {
            if (be.upgrade.isPresent())
                modifier *= be.upgrade.get().getTorqueModifier(this);
        }
        return modifier;
    }

    public float getUpgradeEfficiencyModifier() {
        float modifier = 1;
        for (AbstractSmallEngineBlockEntity be : getEngines()) {
            if (be.upgrade.isPresent())
                modifier *= be.upgrade.get().getEfficiencyModifier(this);
        }
        return modifier;
    }

}
