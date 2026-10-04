package com.drmangotea.tfmg.content.electricity.base;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.base.lang.TFMGLang;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.content.electricity.connection.cables.CableConnection;
import com.drmangotea.tfmg.content.electricity.connection.cables.CableConnectorBlockEntity;
import com.drmangotea.tfmg.content.electricity.network.large_switch.LargeSwitchBlockEntity;
import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeTransformerBlockEntity;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * data and actions for electric blocks
 */
public interface IElectric {

    /**
     * block's world position as a long
     */
    long getPos();

    /**
     * world the blocks is in
     */
    LevelAccessor getLevelAccessor();

    /**
     * @return true if the block is marked as removed
     */
    default boolean destroyed() {
        return getData().destroyed();
    }

    /**
     * checks if this block is part of a valid network
     *
     * @return block's network if valid, newly created network for this block otherwise
     */
    default ElectricalNetwork getOrCreateElectricNetwork() {
        LevelAccessor level = getLevelAccessor();
        long networkId = getData().electricalNetworkId;
        BlockPos ownerPos = BlockPos.of(networkId);
        boolean ownerChunkLoaded = level != null && level.hasChunkAt(ownerPos);

        if (ownerChunkLoaded && level.getBlockEntity(ownerPos) instanceof IElectric owner)
            return TFMG.NETWORK_MANAGER.getOrCreateNetworkFor(owner);

        java.util.Map<Long, ElectricalNetwork> map = ElectricNetworkManager.networks.get(level);
        ElectricalNetwork existing = map == null ? null : map.get(networkId);
        if (existing != null) {
            // Owner chunk is unloaded: keep the shared network alive instead
            // of deleting it, and never force-load the chunk just to check.
            if (!ownerChunkLoaded)
                return existing;
            // Owner chunk is loaded but the owner block entity is gone:
            // re-key the surviving network to this loaded member instead of
            // deleting it and rebuilding it one member at a time.
            map.remove(networkId);
            existing.members.removeIf(ElectricNetworkManager::isStale);
            existing.add(this);
            long newId = getPos();
            existing.id = newId;
            for (IElectric member : existing.members) {
                member.getData().electricalNetworkId = newId;
                // Drop any stale singleton entry keyed by the member's own
                // position (mirrors what setNetwork() does), so re-keying
                // does not leak orphaned network objects in the map.
                if (member.getPos() != newId)
                    map.remove(member.getPos());
            }
            map.put(newId, existing);
            return existing;
        }
        return TFMG.NETWORK_MANAGER.getOrCreateNetworkFor(this);
    }

    /**
     * block entity lookup that never force-loads unloaded chunks
     */
    default BlockEntity getBlockEntitySafe(BlockPos pos) {
        LevelAccessor level = getLevelAccessor();
        if (level == null || !level.hasChunkAt(pos))
            return null;
        return level.getBlockEntity(pos);
    }

    /**
     * tells the block which sides of it can be attached to the grid
     */
    default boolean hasElectricitySlot(Direction direction) {
        return true;
    }

    /**
     * initialization, called when the block is placed
     */
    default void onPlaced() {

        // A flood fill that already ran this tick absorbed this block into its
        // network. Flooding again from here would re-key the whole grid to this
        // block and walk it once more: on a chunk load every member did that in
        // turn, n floods over n members. Keep the scheduling the full path does
        // (loop check, network update, sync); subclasses still run their own
        // onPlaced logic after this returns.
        long now = ElectricNetworkManager.gameTime(getLevelAccessor());
        if (now != Long.MIN_VALUE && getData().floodedTick == now && getData().electricalNetworkId != getPos()
                && getOrCreateElectricNetwork().containsMember(this)) {
            getData().checkForLoopsNextTick = true;
            updateNextTick();
            sendStuff();
            return;
        }
        getData().floodedTick = now;

        ElectricalNetwork network = TFMG.NETWORK_MANAGER.getOrCreateNetworkFor(this);
        setNetwork(getPos());
        getData().electricalNetworkId = getPos();
        network.add(this);


        getData().checkForLoopsNextTick = true;
        /// ////


        updateNextTick();

        onConnected();
        sendStuff();

    }

    /**
     * manages removal of this block after it is destroyed
     */
    default void onRemoved() {
        this.getData().destroyed = true;
        java.util.Map<Long, ElectricalNetwork> map = ElectricNetworkManager.networks.get(getLevelAccessor());
        for (Direction d : Direction.values()) {
            if (hasElectricitySlot(d))
                if (getLevelAccessor().getBlockEntity(BlockPos.of(getPos()).relative(d)) instanceof IElectric be && be.hasElectricitySlot(d.getOpposite())) {
                    if (map != null)
                        map.remove(be.getPos());
                    be.setNetwork(be.getPos());
                    be.getData().connectNextTick = true;
                    be.updateNextTick();
                }
        }
        if (getData().electricalNetworkId != getPos()) {
            ElectricalNetwork network = getOrCreateElectricNetwork();
            network.getMembers().remove(this);
            // Recompute the surviving network now that this consumer is gone.
            // Phase I of updateNetwork clears notEnoughPower on EVERY remaining
            // member, then Phase IV re-checks supply with the reduced draw.
            // Without this, only the destroyed block's direct neighbours were
            // refreshed and the rest of the grid stayed latched on the stale
            // "not enough power" state set while this (possibly high-draw) block
            // was still attached.
            network.updateNetwork();
        }
//
        if (getData().electricalNetworkId == getPos() && map != null)
            map.remove(getData().getId());
    }

    /**
     * loads data
     */
    default void readElectricity(CompoundTag compound, boolean clientPacket) {
        if (!clientPacket)
            getData().connectNextTick = true;
    }

    /**
     * saves data
     */


    /**
     * handles action that need to happen every tick and checks for scheduled updates
     */
    default void tickElectricity() {
        if (getData().checkForLoopsNextTick) {
            getOrCreateElectricNetwork().checkForLoops(getBlockPos());
            getData().checkForLoopsNextTick = false;
        }
        if (getData().connectNextTick) {
            onPlaced();
            getData().connectNextTick = false;
        }
        if (getData().updateNextTick) {
            updateNetwork();
            getData().updateNextTick = false;
        }

        if (getData().updatePowerNextTick) {
            updateUnpowered(new ArrayList<>());
            getData().updatePowerNextTick = false;
        }
        if (getData().setVoltageNextTick) {
            setVoltage(getData().voltageSupply);
            getData().setVoltageNextTick = false;
        }
    }

    default boolean isCable() {
        return false;
    }

    /**
     * handles actions that need to repeat every second
     */
    default void lazyTickElectricity() {

        if (getPowerUsage() > getData().networkPowerGeneration && !getData().notEnoughPower)
            getData().connectNextTick = true;

        // Destructive checks below are server-only; the client is mirrored
        // through ElectricalBlockFailPacket.
        if (getLevelAccessor().isClientSide())
            return;

        java.util.Map<Long, ElectricalNetwork> map = ElectricNetworkManager.networks.get(getLevelAccessor());
        ElectricalNetwork myNetwork = map == null ? null : map.get(getData().getId());
        if (myNetwork == null || !myNetwork.containsMember(this))
            getData().connectNextTick = true;

        if (getData().failTimer >= 4) {

            this.blockFail();
            if (getLevelAccessor() instanceof ServerLevel serverLevel)
                CatnipServices.NETWORK.sendToClientsTrackingChunk(serverLevel, new ChunkPos(getBlockPos()), new ElectricalBlockFailPacket(BlockPos.of(getPos())));
            getData().failTimer = 0;
            sendStuff();
        } else if ((getData().voltage > getMaxVoltage() && getMaxVoltage() > 0) || (getCurrent() > getMaxCurrent() && getMaxCurrent() > 0) || ((getData().highestCurrent > getMaxCurrent() && getMaxCurrent() > 0) && isCable())) {

            getData().failTimer++;
        }

    }


    default int getMaxVoltage() {
        return 1000;
    }

    default int getMaxCurrent() {
        return 0;
    }

    /**
     * handles connecting blocks into a network
     */
    default void onConnected() {

        BlockPos pos = BlockPos.of(getPos());
        for (Direction d : Direction.values()) {
            if (hasElectricitySlot(d))
                if (getBlockEntitySafe(pos.relative(d)) instanceof IElectric be) {
                    if (be.hasElectricitySlot(d.getOpposite())) {
                        if (!be.destroyed()) {
                            getOrCreateElectricNetwork().add(be);
                            if (be.getData().getId() != getData().getId()) {
                                be.setNetwork(getData().getId());
                                be.getData().floodedTick = ElectricNetworkManager.gameTime(getLevelAccessor());
                                be.onConnected();
                                if (!getLevelAccessor().isClientSide())
                                    sendStuff();
                            }
                        }
                    } else if (be.getData().getId() != getData().getId()) {
                        be.updateNextTick();
                    }
                }
        }
        sendStuff();

    }

    /**
     * @return the block's world position
     */
    default BlockPos getBlockPos() {
        return BlockPos.of(getPos());
    }

    /**
     * tells blocks when the network doesn't have enough power
     */
    default void updateUnpowered(List<BlockPos> alreadyChecked) {
        alreadyChecked.add(BlockPos.of(getPos()));
        updateNextTick();

        if (this instanceof CableConnectorBlockEntity connectorBE) {
            for (CableConnection connection : connectorBE.connections) {

                if (getBlockEntitySafe(connection.blockPos1) instanceof CableConnectorBlockEntity be2 && !alreadyChecked.contains(BlockPos.of(be2.getPos()))
                ) {
                    be2.updateUnpowered(alreadyChecked);
                }
            }
        }

        for (Direction direction : Direction.values()) {
            if (getBlockEntitySafe(BlockPos.of(getPos()).relative(direction)) instanceof IElectric be && !alreadyChecked.contains(BlockPos.of(be.getPos()))) {
                be.updateUnpowered(alreadyChecked);
            }
        }
    }

    /**
     * the multimeter tooltip
     */
    default boolean makeMultimeterTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        TFMGTexts.header("multimeter").style(ChatFormatting.WHITE)
                .forGoggles(tooltip);


        if(getMaxVoltage()!=0&&getMaxVoltage()*0.8f < getData().getVoltage())
            TFMGLang.translate("multimeter.approaching_overvoltage").add(TFMGLang.text("("+ TFMGUtils.formatUnits(getMaxVoltage(),"V" +")"))).style(ChatFormatting.RED).forGoggles(tooltip);
        if(getMaxCurrent()!=0&&getMaxCurrent()*0.8f <getCurrent())
            TFMGLang.translate("multimeter.approaching_overcurrent").add(TFMGLang.text("("+ TFMGUtils.formatUnits(getMaxCurrent(),"A" +")"))).style(ChatFormatting.RED).forGoggles(tooltip);


        if (getData().notEnoughPower) TFMGTexts.Multimeter.notEnoughPower().forGoggles(tooltip, 1);

        if (voltageGeneration() > 0) {
            TFMGTexts.Multimeter.powerGenerated(powerGeneration()).forGoggles(tooltip, 1);
            TFMGTexts.Multimeter.voltageGenerated(voltageGeneration()).forGoggles(tooltip, 1);
            TFMGTexts.Multimeter.separator().forGoggles(tooltip);
        }
        if (resistance() != 0)
            TFMGTexts.Multimeter.resistance(voltageGeneration() > 0 ? getGeneratorResistance() : resistance()).forGoggles(tooltip, 1);
        else if (getNetworkResistance() > 0)
            TFMGTexts.Multimeter.resistance(getNetworkResistance()).forGoggles(tooltip, 1);
        TFMGTexts.Multimeter.voltage(getData().getVoltage()).forGoggles(tooltip, 1);
        TFMGTexts.Multimeter.current(resistance() == 0 ? getData().highestCurrent : getCurrent()).forGoggles(tooltip, 1);
        if (resistance() != 0)
            TFMGTexts.Multimeter.power(getPowerUsage()).forGoggles(tooltip, 1);


        if (isPlayerSneaking) {
            TFMGTexts.Multimeter.separator().forGoggles(tooltip);
            TFMGTexts.Multimeter.networkGeneration(getNetworkPowerGeneration()).forGoggles(tooltip, 1);
            TFMGTexts.Multimeter.networkConsumption(getNetworkPowerUsage()).forGoggles(tooltip, 1);
        }

        return true;
    }


    /**
     * contains data related to electricity
     */
    ElectricBlockValues getData();

    /**
     * @return true if the network has enough power
     */
    default boolean canWork() {
        return !getData().notEnoughPower;
    }

    default void blockFail() {

        getLevelAccessor().destroyBlock(BlockPos.of(getPos()), false);

    }

    default int getPowerUsage() {
        return (int) (getData().getVoltage() * getCurrent());
    }

    default int getNetworkPowerUsage(IElectric blocked) {
        int power = 0;
        for (IElectric member : getOrCreateElectricNetwork().members)
            if (member.getPos() != blocked.getPos()) {
                power += member.getPowerUsage();
            } else blocked.updateNextTick();
        return power;
    }

    default int getNetworkPowerUsage() {
        int power = 0;
        for (IElectric member : getOrCreateElectricNetwork().members)
            power += member.getPowerUsage();
        return power;
    }


    default int getNetworkPowerGeneration() {
        int power = 0;
        int voltage = getData().getVoltage();
        for (IElectric member : getOrCreateElectricNetwork().members)
            power += member.powerGeneration(voltage);
        return power;
    }

    default void onNetworkChanged(int oldVoltage, int oldPower) {
    }

    default float getGeneratorResistance() {
        if (getData().voltageSupply == 0)
            return 0;

        if ((float) getData().networkPowerGeneration * (float) getNetworkResistance() == 0)
            return 0;

        return (float) powerGeneration() / (float) getData().networkPowerGeneration * (float) getNetworkResistance();
    }

    default float getGeneratorLoad() {
        if (getNetworkPowerUsage() == 0)
            return 0;
        return (float) powerGeneration() / (float) getData().networkPowerGeneration * getNetworkPowerUsage();
    }

    default int getBlocksConnectedToNetworkCount(long id) {

        int count = 0;

        for (IElectric member : getOrCreateElectricNetwork().members) {
            if (member instanceof VoltageAlteringBlockEntity be && be.getControlledBlock() != null) {
                if (be.getControlledBlock().getData().getId() == id)
                    count++;
            }
            if (member instanceof LargeSwitchBlockEntity be && be.getControlledBlock() != null) {
                if (be.getControlledBlock().getData().getId() == id)
                    count++;
            }
            if (member instanceof LargeTransformerBlockEntity be && be.getControlledBlock() != null) {
                if (be.getControlledBlock().getData().getId() == id)
                    count++;
            }

        }

        return count;

    }


    default float resistance() {
        return 0;
    }


    default int voltageGeneration() {

        int voltageGeneration = 0;

        for (Direction direction : Direction.values()) {
            if (hasElectricitySlot(direction)) {

                if (getBlockEntitySafe(getBlockPos().relative(direction)) instanceof VoltageAlteringBlockEntity be)
                    if (be.getData().getId() != getData().getId())
                        if (be.getData().getVoltage() != 0)
                            if (be.hasElectricitySlot(direction)) {
                                voltageGeneration = Math.max(voltageGeneration, be.getOutputVoltage());
                                getData().getsOutsidePower = true;
                            }
                // Recognise IVoltageSource neighbours (Accumulator, Converter)
                // without forcing them to extend VoltageAlteringBlockEntity,
                // which would re-introduce the StackOverflow in
                // VoltageAlteringBlockEntity.getPowerUsage.
                if (getBlockEntitySafe(getBlockPos().relative(direction)) instanceof IVoltageSource src
                        && !(src instanceof VoltageAlteringBlockEntity)) {
                    if (src.getData().getId() != getData().getId())
                        if (src.getOutputVoltage() != 0)
                            // The neighbour touches us through the OPPOSITE face:
                            // "direction" points from here to it. Asking it for a
                            // slot on "direction" only ever matched sources whose
                            // slot happens to be symmetric on an axis, like the
                            // accumulator. The converter exposes one single face,
                            // so a correctly oriented one was never seen and
                            // FE->TFMG published no voltage at all.
                            if (src.hasElectricitySlot(direction.getOpposite())) {
                                voltageGeneration = Math.max(voltageGeneration, src.getOutputVoltage());
                                getData().getsOutsidePower = true;
                            }
                }
            }
        }

        if (voltageGeneration == 0)
            getData().getsOutsidePower = false;

        return voltageGeneration;
    }


    /**
     * Power this block adds to its own network when that network runs at
     * {@code networkVoltage}. Sources answer {@link #powerGeneration()}; a
     * storage block overrides it to supply nothing while a higher voltage is
     * charging it, otherwise it counts as a source and as a load at once.
     */
    default int powerGeneration(int networkVoltage) {
        return powerGeneration();
    }

    default int powerGeneration() {

        if (!getData().getsOutsidePower)
            return 0;

        int powerGeneration = 0;

        for (Direction direction : Direction.values()) {
            if (hasElectricitySlot(direction)) {

                if (getBlockEntitySafe(getBlockPos().relative(direction)) instanceof VoltageAlteringBlockEntity be && be.canWork()) {

                    if (be.getData().getId() != getData().getId())
                        if (be.getData().getVoltage() != 0)
                            if (be.hasElectricitySlot(direction)) {
                                int cachedGen = be.getData().networkPowerGeneration;
                                int maxOut = be.getMaxPowerOutput();
                                int available = cachedGen > 0 ? Math.min(maxOut, cachedGen) : maxOut;
                                powerGeneration = Math.max(powerGeneration, available);
                            }
                }
                // Same direct-source path for IVoltageSource neighbours.
                if (getBlockEntitySafe(getBlockPos().relative(direction)) instanceof IVoltageSource src
                        && !(src instanceof VoltageAlteringBlockEntity)
                        && src.canWork()) {
                    if (src.getData().getId() != getData().getId())
                        if (src.getOutputVoltage() != 0)
                            // Same opposite-face correction as in voltageGeneration:
                            // without it a single-face source published no power
                            // either, so even a converter that did expose voltage
                            // would have been treated as able to deliver nothing.
                            if (src.hasElectricitySlot(direction.getOpposite())) {
                                int cachedGen = src.getData().networkPowerGeneration;
                                int maxOut = src.getMaxPowerOutput();
                                int available = cachedGen > 0 ? Math.min(maxOut, cachedGen) : maxOut;
                                powerGeneration = Math.max(powerGeneration, available);
                            }
                }
            }
        }

        return powerGeneration;
    }

    default int getNetworkResistance() {
        return getData().networkResistance;
    }


    default boolean networkUndersupplied() {
        return getNetworkPowerUsage() > getData().networkPowerGeneration;
    }


    default float getCurrent() {
        return getData().getVoltage() == 0 || resistance() == 0 ? 0 : ((float) getData().getVoltage() / (float) resistance());
    }

    default void updateNextTick() {
        getData().updateNextTick = true;
    }

    default void updateNetwork() {
        // On the server the recompute runs once per network at the end of the
        // tick (ElectricNetworkManager.flushUpdates), which also sends the
        // client packet and this block's sync.
        if (ElectricNetworkManager.deferUpdate(this))
            return;
        getOrCreateElectricNetwork().updateNetwork();
        if (getLevelAccessor() instanceof ServerLevel serverLevel)
            CatnipServices.NETWORK.sendToClientsTrackingChunk(serverLevel, new ChunkPos(getBlockPos()), new NetworkUpdatePacket(BlockPos.of(getPos())));
        sendStuff();
    }

    void sendStuff();


    default void setVoltage(int newVoltage) {

        getData().voltage = newVoltage;
    }


    default void setNetworkResistance(float newUsage) {

        getData().networkResistance = newUsage > 0 && newUsage < 1 ? 1 : (int) newUsage;
    }


    default void setNetwork(long network) {
        getData().electricalNetworkId = network;
        if (network != getPos())
            ElectricNetworkManager.networks.get(getLevelAccessor())
                    .remove(getPos());
    }


}