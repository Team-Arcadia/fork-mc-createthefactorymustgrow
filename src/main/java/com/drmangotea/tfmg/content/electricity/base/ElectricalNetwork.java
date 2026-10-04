package com.drmangotea.tfmg.content.electricity.base;

import com.drmangotea.tfmg.content.electricity.network.large_switch.LargeSwitchBlockEntity;
import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeTransformerBlockEntity;
import com.drmangotea.tfmg.content.electricity.utilities.electric_motor.ElectricMotorBlockEntity;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElectricalNetwork {

    /**
     * This class manages individual networks
     */

    public ElectricalNetwork(long id) {
        this.id = id;
    }

    //blocks in the network, indexed by position (see MemberList)
    public final MemberList members = new MemberList();

    // Game time of the last stale-member sweep, so lookups sweep at most once
    // per tick instead of on every call.
    long lastSweepTick = Long.MIN_VALUE;

    /** True when this very block entity is a member of the network. */
    public boolean containsMember(IElectric electric) {
        return members.containsInstance(electric);
    }

    //network's id
    public long id;

    public long getId() {
        return id;
    }

    //adds a new block to the network if it is not in it already
    public void add(IElectric be) {
        // Identity here is the BLOCK's position, not getData().getId(): that
        // one is the NETWORK's id, which every member of a network shares. The
        // old test therefore asked "does any member belong to this network",
        // which is true of every member past the first, so a block already
        // carrying the network id could never be added back once it had left
        // the list - after a stale sweep or a re-key, for instance. It took the
        // repair in lazyTickElectricity to notice and rebuild it from scratch.
        // The position index makes this O(1); it was a scan of the whole
        // list for every block a flood fill added.
        long pos = be.getPos();
        IElectric member = members.at(pos);
        if (member == null) {
            members.add(be);
            return;
        }
        if (member == be)
            return;
        // Same position, different instance: after a chunk reload the old
        // block entity is dead and must not keep the seat.
        if (ElectricNetworkManager.isStale(member)) {
            int index = members.indexOf(member);
            if (index >= 0)
                members.set(index, be);
            else
                members.add(be);
        }
    }

    /**
     * Called when a block is removed or added to the network or when loading chunks the network is in
     * Sets up blocks in the network
     */
    public void updateNetwork() {

        members.removeIf(ElectricNetworkManager::isStale);

        int maxVoltage = 0;
        float resistance = 0;
        int powerGeneration = 0;


        Map<Integer, Float> groups = new HashMap<>();

        /**
         *  Phase I:
         *  1) gives each blocks the networks id
         *  2) finds the highest voltage generated
         *  3) counts the resistance and power generation of the network
         *  4) creates groups
         */
        for (IElectric member : members) {
            member.getData().notEnoughPower = false;
            member.getData().highestCurrent = 0;

            maxVoltage = Math.max(member.voltageGeneration(), maxVoltage);
            if (member.resistance() != 0)
                resistance += 1f / member.resistance();
            powerGeneration += member.powerGeneration();
        }
        /**
         *  Phase II:
         * 1) informs blocks about voltage and power change
         * 2) sets network's resistance
         * 3) informs blocks about their group's resistance
         */
        List<IElectric> list = new ArrayList<>(members);
        if (!members.isEmpty()) {

            for (IElectric member : list) {

                int oldVoltage = member.getData().getVoltage();
                int oldPower = member.getPowerUsage();
                member.getData().voltageSupply = maxVoltage;
                member.setVoltage(maxVoltage);
                member.getData().setVoltageNextTick = true;

                member.getData().networkPowerGeneration = powerGeneration;
                if (resistance != 0) {
                    member.setNetworkResistance(1f / resistance);
                }else member.setNetworkResistance(0);
                member.onNetworkChanged(oldVoltage, oldPower);


            }
        }
        /**
         * Phase III:
         * 1) sets the current of wires
         * 2) informs subnetworks
         */
        float networkCurrent = 0;
        for (IElectric member : members)
            networkCurrent += member.getCurrent();

        for (IElectric member : members) {

            if (member.resistance() == 0) {

                member.getData().highestCurrent = networkCurrent;
            }
            if (member instanceof VoltageAlteringBlockEntity be) {
                be.updateInFront();
            }


        }
        /**
         * Phase IV:
         * 1) stops the network from functioning if it consumes more power than it creates
         */
        handleInsufficientPower();

    }

    public void handleInsufficientPower() {
        if (!members.isEmpty())
            if (members.get(0).getNetworkPowerUsage() > members.get(0).getNetworkPowerGeneration()) {
                for (IElectric member : members) {
                    member.getData().notEnoughPower = true;
                    if (member instanceof ElectricMotorBlockEntity be) {
                        be.updateGeneratedRotation();
                    }
                    if (member instanceof VoltageAlteringBlockEntity be)
                        be.updateInFront = true;
                    if (member instanceof LargeSwitchBlockEntity be)
                        be.updateInFront = true;
                    if (member instanceof LargeTransformerBlockEntity be)
                        be.updateInFront = true;
                }
            }
    }

    public static float getCableCurrent(IElectric be) {

        float current = 0;


        for (IElectric member : be.getOrCreateElectricNetwork().members) {
            current += member.getCurrent();
        }


        return current;
    }


    public void checkForLoops(BlockPos pos) {

        members.forEach(member -> {
            if (member instanceof VoltageAlteringBlockEntity be) {
                if (be.getControlledBlock() != null) {
                    List<ElectricalNetwork> list = new ArrayList<>();
                    list.add(this);
                    be.getControlledBlock().getOrCreateElectricNetwork().checkForLoops(list, pos);
                }
            }
        });


    }

    public void checkForLoops(List<ElectricalNetwork> network, BlockPos pos) {

        if (network.contains(this)) {
            if (!members.isEmpty() && !members.get(0).getLevelAccessor().isClientSide())
                members.get(0).getLevelAccessor().destroyBlock(pos, false);
            return;
        }
        network.add(this);
        members.forEach(member -> {
            if (member instanceof VoltageAlteringBlockEntity be) {
                if (be.getControlledBlock() != null) {
                    be.getControlledBlock().getOrCreateElectricNetwork().checkForLoops(network, pos);
                }
            }
        });
    }

    public MemberList getMembers() {
        return members;
    }
}
