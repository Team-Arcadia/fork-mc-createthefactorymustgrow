package com.drmangotea.tfmg.content.electricity.storage;

import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.config.TFMGConfigs;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.content.electricity.base.ElectricBlockEntity;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.content.electricity.base.IVoltageSource;
import com.drmangotea.tfmg.content.electricity.utilities.converter.ConverterBlockEntity;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

import static net.minecraft.world.level.block.DirectionalBlock.FACING;

public class AccumulatorBlockEntity extends ElectricBlockEntity implements IVoltageSource, IInspectable {

    public TFMGForgeEnergyStorage energy = createEnergyStorage(1);
    private IEnergyStorage energyCapability;
    public int length = 1;
    boolean refreshNextTick = true;
    public BlockPos controller = getBlockPos();
    int signal;
    boolean signalChanged;
    // Set while this block is being destroyed: chain scans skip it.
    private boolean removing;

    public AccumulatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        energyCapability = energy;

    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                TFMGBlockEntities.ACCUMULATOR.get(),
                (be, context) -> be.energyCapability
        );
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        neighbourChanged();
    }

    public void neighbourChanged() {

        if (!hasLevel())
            return;
        // Single 6-neighbour redstone scan; this ran twice per lazyTick.
        int power = level.getBestNeighborSignal(worldPosition);
        if (isController()) {
            if (power != signal)
                signalChanged = true;
        } else if (level.getBlockEntity(controller) instanceof AccumulatorBlockEntity be) {
            if (power != be.signal)
                be.signalChanged = true;
        }
    }


    @Override
    public void remove() {
        super.remove();
    }

    @Override
    public void destroy() {
        super.destroy();
        // Hand off this block's stored energy + the entire chain's energy to
        // the surviving sub-chains BEFORE the chain rebuild kicks in. The
        // previous code just called refreshController which scanned the
        // partial chain from the destroyed block's pos and never carried
        // this BE's energy anywhere — every break voided up to one slave's
        // worth of charge.
        //
        // The block entity is still in the chunk's map while onRemove runs,
        // so the sub-chain scans walk straight through this block and count
        // its energy a second time on top of the donation. getDrops then
        // stamped that doubled total onto the dropped item, and a plain
        // break-and-replace of a charged controller duplicated the whole
        // chain's charge. Zero it first so the donation is the only copy;
        // if no sub-chain absorbed it (a lone accumulator), put it back so
        // the dropped item carries the charge instead of voiding it.
        //
        // A non-controller holds nothing itself: the bank's charge lives in
        // its controller, and the rebuild below clamped it to the shorter
        // surviving chain, so breaking the middle of a full bank voided the
        // broken block's share and the whole share of the far side. Take the
        // bank's charge out of the controller too, let the controller side
        // fill up, hand the rest to the far side, and keep whatever still
        // does not fit in this block so the dropped item carries it.
        removing = true;
        int stored = energy.getEnergyStored();
        energy.setEnergy(0);
        AccumulatorBlockEntity head = chainController();
        if (head != null) {
            stored += head.energy.getEnergyStored();
            head.energy.setEnergy(0);
        }
        int left = rebuildChainAround(getBlockPos(), stored);
        length = Math.max(length, 1);
        energy = createEnergyStorage(1);
        energy.setEnergy(left);
    }

    /** The controller of this block's bank when it is another block, or null. */
    private AccumulatorBlockEntity chainController() {
        if (isController() || level == null)
            return null;
        return level.getBlockEntity(controller) instanceof AccumulatorBlockEntity be && be != this ? be : null;
    }

    /**
     * The charge that stays with this block when it is removed, worked out
     * the way {@link #destroy()} shares it out. Drops are rolled before the
     * block entity is destroyed on most paths (drills, explosions, the
     * wrench), so the dropped item must not carry charge the surviving banks
     * are about to receive.
     */
    public int chargeKeptOnRemoval() {
        if (level == null)
            return energy.getEnergyStored();
        int total = energy.getEnergyStored();
        AccumulatorBlockEntity head = chainController();
        if (head != null)
            total += head.energy.getEnergyStored();
        Direction facing = getBlockState().getValue(FACING);
        long room = (long) (chainLength(facing.getOpposite(), facing) + chainLength(facing, facing))
                * TFMGConfigs.common().machines.accumulatorStorage.get();
        return (int) Math.max(0, total - room);
    }

    private int chainLength(Direction direction, Direction facing) {
        int count = 0;
        for (int i = 1; i < 15; i++) {
            if (level.getBlockEntity(getBlockPos().relative(direction, i)) instanceof AccumulatorBlockEntity be
                    && be.getBlockState().getValue(FACING) == facing)
                count++;
            else
                break;
        }
        return count;
    }

    @Override
    public void onPlaced() {
        super.onPlaced();
        rebuildChainIncludingSelf(0);
    }

    @Override
    public boolean makeMultimeterTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!isController())
            if (level.getBlockEntity(controller) instanceof AccumulatorBlockEntity be)
                return be.makeMultimeterTooltip(tooltip, isPlayerSneaking);
        super.makeMultimeterTooltip(tooltip, isPlayerSneaking);

        TFMGTexts.electricalCapacity(energy.getEnergyStored()).forGoggles(tooltip, 1);
        TFMGTexts.chargingRate(getChargingRate()).forGoggles(tooltip, 1);
        TFMGTexts.electricalMaxCapacity(getMaxCapacity()).forGoggles(tooltip, 1);

        return true;
    }

    @Override
    public void inspect(InspectionReport report) {
        if (!isController()) {
            if (level.isLoaded(controller) && level.getBlockEntity(controller) instanceof AccumulatorBlockEntity be && be.isController()) {
                report.info("accumulator.member", controller.getX() + " " + controller.getY() + " " + controller.getZ());
                be.inspect(report);
            } else {
                report.problem("accumulator.no_controller");
                report.fix("accumulator.no_controller.fix");
            }
            return;
        }
        int stored = energy.getEnergyStored();
        int max = getMaxCapacity();
        report.info("accumulator.charge", stored, max, max <= 0 ? 0 : (int) (100L * stored / max));
        Direction facing = getBlockState().getValue(FACING);
        report.info("accumulator.chain", length, getOutputVoltage(),
                net.minecraft.network.chat.Component.translatable("tfmg.inspector.dir." + facing.getName()),
                net.minecraft.network.chat.Component.translatable("tfmg.inspector.dir." + facing.getOpposite().getName()));

        int threshold = TFMGConfigs.common().machines.accumulatorVoltage.get() * length;
        if (getData().getVoltage() > threshold) {
            if (stored >= max)
                report.ok("accumulator.full");
            else if (data.notEnoughPower)
                report.problem("accumulator.no_spare_power");
            else
                report.ok("accumulator.charging", getChargingRate());
            return;
        }
        if (signal > 0) {
            report.problem("accumulator.redstone", signal);
            report.fix("accumulator.redstone.fix");
        } else if (stored <= 0) {
            report.problem("accumulator.empty");
        } else if (canPower()) {
            report.ok("accumulator.discharging", getOutputVoltage());
        } else {
            report.info("accumulator.idle");
        }
        report.fix("accumulator.charge.fix", threshold, TFMGConfigs.common().machines.accumulatorVoltage.get());
    }

    public void refreshController() {
        rebuildChainIncludingSelf(0);
    }

    /**
     * Walks the chain in BOTH directions from a seed position, treating the
     * seed as either present (just placed) or absent (just destroyed). For
     * a destroy, the seed pos returns null from level.getBlockEntity so the
     * scan stops there — which means the chain is split in two. We rebuild
     * each side independently. donatedEnergy is added to whichever sub-chain
     * touches the seed; if both sub-chains exist (middle break), it goes
     * into the side facing.opposite (the controller side, where storage
     * lives anyway).
     */
    /**
     * Rebuild from a block that is STILL THERE.
     *
     * rebuildChainAround deliberately starts one step away from the seed
     * because it is written for the destroy case, where the block is already
     * gone and the chain has split in two. Reusing it from place, refresh and
     * controller-refresh made the block skip itself: a lone accumulator matched
     * no sub-chain at all, so it was never promoted, its storage was never
     * rebuilt and refreshCapability never ran — its charge simply went nowhere.
     *
     * Scanning from this block covers both cases: rebuildSubChainStartingFrom
     * walks facing.opposite to find the tail and then sums forward, so a chain
     * of one and a chain of ten are handled the same way.
     */
    private void rebuildChainIncludingSelf(int donatedEnergy) {
        if (level == null)
            return;
        if (rebuildSubChainStartingFrom(getBlockPos(), getBlockState().getValue(FACING), donatedEnergy) >= 0)
            return;
        rebuildChainAround(getBlockPos(), donatedEnergy);
    }

    /** Returns the donated energy that found no room in either side. */
    private int rebuildChainAround(BlockPos seed, int donatedEnergy) {
        if (level == null)
            return donatedEnergy;
        Direction facing = getBlockState().getValue(FACING);
        // Find the tail (most-facing.opposite) and head (most-facing) of each
        // sub-chain neighbouring the seed. Scan each side starting one step
        // away from the seed so a break in the middle creates two sub-chains.
        BlockPos tailSide = seed.relative(facing.getOpposite());
        BlockPos headSide = seed.relative(facing);
        // The opposite-side sub-chain (controller side) fills up first, what
        // does not fit goes to the head side, so no energy is voided.
        int left = rebuildSubChainStartingFrom(tailSide, facing, donatedEnergy);
        if (left < 0)
            left = donatedEnergy;
        int headLeft = rebuildSubChainStartingFrom(headSide, facing, left);
        return headLeft < 0 ? left : headLeft;
    }

    /**
     * Given an arbitrary accumulator pos, walk the chain in facing/opposite
     * directions, find the controller (most-facing.opposite end), and
     * promote it. Returns -1 if no accumulator chain was found here, else the
     * energy that did not fit in the chain.
     */
    /**
     * The accumulator at pos when it belongs to a chain facing this way. A
     * block being destroyed is not one: Create's IBE.onRemove runs destroy()
     * while the block entity is still in the chunk, so without this the
     * rebuild walked straight through the broken block, kept the old chain
     * whole, and the split only happened on the next neighbour refresh,
     * which clamped the charge to the shorter half.
     */
    private AccumulatorBlockEntity chainMember(BlockPos pos, Direction facing) {
        if (level.getBlockEntity(pos) instanceof AccumulatorBlockEntity be && !be.removing
                && be.getBlockState().getValue(FACING) == facing)
            return be;
        return null;
    }

    private int rebuildSubChainStartingFrom(BlockPos anchor, Direction facing, int extraEnergy) {
        if (chainMember(anchor, facing) == null)
            return -1;
        // Find the tail (the most-facing.opposite block belonging to this chain).
        BlockPos tailPos = anchor;
        for (int i = 1; i < 15; i++) {
            BlockPos probe = anchor.relative(facing.getOpposite(), i);
            if (chainMember(probe, facing) != null) {
                tailPos = probe;
            } else break;
        }
        AccumulatorBlockEntity tail = chainMember(tailPos, facing);
        if (tail == null)
            return -1;
        // Sum every block's energy from tailPos toward facing direction.
        int totalEnergy = extraEnergy;
        int newLength = 0;
        java.util.List<AccumulatorBlockEntity> members = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            BlockPos pos = tailPos.relative(facing, i);
            AccumulatorBlockEntity be = chainMember(pos, facing);
            if (be != null) {
                totalEnergy += be.energy.getEnergyStored();
                members.add(be);
                newLength++;
            } else break;
        }
        // Promote tail to controller, demote everyone else to slaves.
        tail.controller = tail.getBlockPos();
        tail.length = newLength;
        tail.energy = tail.createEnergyStorage(1);
        int kept = Math.min(totalEnergy, tail.energy.getMaxEnergyStored());
        tail.energy.setEnergy(kept);
        tail.refreshCapability();
        tail.updateNextTick();
        for (AccumulatorBlockEntity be : members) {
            if (be == tail)
                continue;
            be.controller = tail.getBlockPos();
            be.length = 0;
            be.energy.setEnergy(0);
            be.refreshCapability();
            be.sendStuff();
        }
        tail.sendStuff();
        return totalEnergy - kept;
    }

    public void refreshMultiblock() {
        // Old refresh path: only ran when this BE was the chain head, used
        // its own pos as the controller, and disagreed with rebuildSubChain
        // (which promotes the tail) about which end owns the energy. Both
        // could fire on the same chain across different events (lazyTick vs
        // place/destroy) and leave members pointing at conflicting
        // controllers — the resulting drift was the source of 'discharge
        // doesn't work'. Route everything through one canonical pass that picks
        // the tail and resets every member, this block included.
        rebuildChainIncludingSelf(0);
    }

    public void refreshCapability() {
        IEnergyStorage oldCap = energyCapability;
        if (level.getBlockEntity(controller) instanceof AccumulatorBlockEntity be) {
            energyCapability = be.energy;
        } else energyCapability = energy;
        invalidateCapabilities();
    }

    public boolean isController() {
        return controller == null || controller.equals(getBlockPos());
    }

    public TFMGForgeEnergyStorage createEnergyStorage(int multiplier) {
        return new TFMGForgeEnergyStorage(getMaxCapacity() * multiplier, 10000) {
            @Override
            public void onEnergyChanged(int amount, int oldAmount) {

                if ((oldAmount == 0 && amount > 0) || (this.energy == 0)) {
                    updateNextTick();
                }
                sendStuff();
            }
        };
    }

    /**
     * Restores the charge carried by the placed item.
     *
     * Writing it straight into `energy` lost it twice over. The field
     * initializer `energy = createEnergyStorage(1)` runs before `length = 1` is
     * assigned, so the storage a freshly built BE carries has a capacity of
     * accumulatorStorage * 0 and setEnergy clamped the whole charge away — read()
     * already rebuilds the storage for exactly that reason on load, and
     * placement had no equivalent. And a block placed onto an existing column is
     * a slave whose own storage the chain ignores and zeroes on its next pass,
     * so even an uncapped write would have been stranded.
     *
     * Donating through the chain rebuild covers both: the sub-chain scan sets
     * `length` before it builds the storage, and folds the donation into
     * whichever block actually owns the energy.
     */
    public void setCapacity(ItemStack stack) {
        Integer stored = stack.get(TFMGDataComponents.ACCUMULATOR_STORAGE);
        if (stored == null || stored <= 0)
            return;
        rebuildChainIncludingSelf(stored);
    }

    protected void analogSignalChanged() {

        if (!isController()) {
            signal = level.getBestNeighborSignal(controller);
            return;
        }

        int newSignal = 0;

        for (int i = 0; i < length; i++) {
            BlockPos pos = getBlockPos().relative(getBlockState().getValue(FACING).getOpposite(), i);

            newSignal = Math.max(newSignal, level.getBestNeighborSignal(pos));


        }

        updateNextTick();

        signal = newSignal;
    }

    @Override
    public void tick() {
        super.tick();
        if (signalChanged) {
            signalChanged = false;
            analogSignalChanged();
        }
        if (!isController())
            return;

        if (refreshNextTick) {
            refreshMultiblock();
            refreshNextTick = false;
        }

        // Charging and discharging stay mutually exclusive for the tick, as the
        // early return used to enforce, so that the push below runs either way.
        if (getData().getVoltage() > TFMGConfigs.common().machines.accumulatorVoltage.get() * length) {
            energy.receiveEnergy((int) (getChargingRate() / TFMGConfigs.common().machines.FEtoWattTickConversionRate.get()), false);
        } else if (canPower()) {

            int energyToExtract = data.networkPowerGeneration == 0 ? getNetworkPowerUsage() : (int) Math.max(0, Math.max(((float) powerGeneration() / (float) data.networkPowerGeneration) * (float) getNetworkPowerUsage(), 0));
            energyToExtract /= TFMGConfigs.common().machines.FEtoWattTickConversionRate.get();
            energy.extractEnergy(Math.max(energyToExtract, 1), false);
            if (energy.getEnergyStored() == 0)
                updateNextTick();
        }

        pushForgeEnergy();
    }

    /**
     * Feeds stored FE to adjacent consumers. Exposing the energy capability
     * alone only served machines and cables that pull; a passive consumer
     * received nothing, which read as "the accumulator gives no FE" unless the
     * player switched their cable to pull mode. Mirrors the converter's push.
     */
    private void pushForgeEnergy() {
        if (level == null || level.isClientSide || energy.getEnergyStored() <= 0)
            return;
        for (Direction direction : Direction.values()) {
            // Never push out of a TFMG electricity slot: those faces carry the
            // mod's own network, not Forge Energy.
            if (hasElectricitySlot(direction))
                continue;
            // Never push into another accumulator. A bank shares its charge
            // through its own chain, along the facing axis; pushing Forge
            // Energy sideways let two banks that were never wired together
            // drain into each other through faces that carry no port.
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof AccumulatorBlockEntity)
                continue;
            if (energy.getEnergyStored() <= 0)
                break;
            IEnergyStorage neighbour = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(direction), direction.getOpposite());
            if (neighbour == null || !neighbour.canReceive())
                continue;
            int simulated = neighbour.receiveEnergy(energy.getEnergyStored(), true);
            if (simulated <= 0)
                continue;
            int extracted = energy.extractEnergy(simulated, false);
            if (extracted > 0)
                neighbour.receiveEnergy(extracted, false);
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);

        compound.putInt("ForgeEnergy", energy.getEnergyStored());
        compound.putInt("Length", length);
        compound.putLong("ControllerPos", controller.asLong());

    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        length = compound.getInt("Length");
        // Restore the chain link before the first tick: without it every
        // block of a reloaded chain briefly acted as its own controller and
        // capability lookups pointed at the wrong storage.
        if (compound.contains("ControllerPos"))
            controller = BlockPos.of(compound.getLong("ControllerPos"));
        // The field initializer built the storage with length=1 capacity;
        // rebuild it at the persisted length so setEnergy (which clamps to
        // capacity) does not truncate a full chain's energy on load.
        if (energy.getMaxEnergyStored() != getMaxCapacity()) {
            energy = createEnergyStorage(1);
            refreshNextTick = true;
        }
        energy.setEnergy(compound.getInt("ForgeEnergy"));


    }

    @Override
    public float resistance() {
        if (voltageGeneration() > 0)
            return 0;
        if (!isController())
            return 0;

        int power = 0;
        for (IElectric member : getOrCreateElectricNetwork().members)
            if (!(member instanceof ConverterBlockEntity) && !(member instanceof AccumulatorBlockEntity))
                power += member.getPowerUsage();
        if (energy.getEnergyStored() == getMaxCapacity() || getData().getVoltage() <= getOutputVoltage() || canPower())
            return 0;
        if (Math.min(Math.max((data.networkPowerGeneration - power), 0), getMaxChargingRate()) == 0) {
            return 0;
        }

        return (float) Math.min((Math.pow(data.voltage, 2)) / Math.min(Math.max((data.networkPowerGeneration - power), 0), getMaxChargingRate()), 750);
    }

    public boolean canPower() {
        return getData().networkResistance > 0 && (getData().getVoltage() <= getOutputVoltage()) && energy.getEnergyStored() > 0 && signal == 0;
    }

    /**
     * Returns true if the accumulator should be exposing its voltage to the
     * network. canPower also requires a non-zero networkResistance, but on
     * the very first updateNetwork tick the network resistance is still 0,
     * which made canPower false, which made voltageGeneration return 0,
     * which made maxVoltage stay 0, which kept setNetworkResistance at 0.
     * The chicken-and-egg loop meant a fresh accumulator + load chain never
     * energised. Loosen the gate to 'energy stored AND no redstone signal'
     * so the first tick already publishes a voltage and the network can
     * compute its resistance from there.
     */
    public boolean canExposeVoltage() {
        return energy.getEnergyStored() > 0 && signal == 0 && isController();
    }


    public int getChargingRate() {
        //
        // int chargingRate = Math.max((data.networkPowerGeneration - getNetworkPowerUsage()), 0);
        if (energy.getEnergyStored() >= getMaxCapacity() || getData().getVoltage() < getOutputVoltage() || canPower() || data.notEnoughPower)
            return 0;

        //return Math.min(chargingRate, getMaxChargingRate());
        return getMaxChargingRate();
    }

    @Override
    public int powerGeneration() {
        return canExposeVoltage() ? maxPowerOutput() : 0;
    }

    /**
     * A bank driven above its own voltage is charging, not discharging (see
     * tick), so it supplies nothing. Counting its full output anyway let a
     * network run loads its generators could not feed while the bank kept
     * filling up: energy out of nowhere.
     */
    @Override
    public int powerGeneration(int networkVoltage) {
        return networkVoltage > getOutputVoltage() ? 0 : powerGeneration();
    }

    public int maxPowerOutput() {
        return getOutputVoltage() * TFMGConfigs.common().machines.accumulatorMaxAmpOutput.get();
    }

    @Override
    public int getMaxPowerOutput() {
        return maxPowerOutput();
    }

    public int getMaxCapacity() {
        return TFMGConfigs.common().machines.accumulatorStorage.get() * length;
    }

    //in FE per tick
    public int getMaxChargingRate() {
        return TFMGConfigs.common().machines.accumulatorChargingRate.get();
    }


    public int getOutputVoltage() {


        return TFMGConfigs.common().machines.accumulatorVoltage.get() * length;
    }

    @Override
    public int voltageGeneration() {
        return canExposeVoltage() ? getOutputVoltage() : 0;
    }


    @Override
    public boolean hasElectricitySlot(Direction direction) {
        return direction.getAxis() == getBlockState().getValue(FACING).getAxis();
    }
}
