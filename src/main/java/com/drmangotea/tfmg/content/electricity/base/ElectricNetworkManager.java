package com.drmangotea.tfmg.content.electricity.base;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ElectricNetworkManager {

    public static Map<LevelAccessor, Map<Long, ElectricalNetwork>> networks = new HashMap<>();

    public void onLoadWorld(LevelAccessor world) {
        networks.put(world, new HashMap<>());
    }
    public void onUnloadWorld(LevelAccessor world) {
        networks.remove(world);
        PENDING_UPDATES.remove(world);
    }

    // Network recomputes requested during a server tick, run once per network
    // at the end of that tick (see flushUpdates).
    private static final Map<LevelAccessor, Set<IElectric>> PENDING_UPDATES = new HashMap<>();

    /** The level's game time, or Long.MIN_VALUE when it has none. */
    public static long gameTime(LevelAccessor level) {
        return level instanceof Level l ? l.getGameTime() : Long.MIN_VALUE;
    }

    /**
     * Queues a network recompute for the end of the server tick. Returns false
     * when the update has to run now instead: on the client, and in virtual
     * levels such as ponder scenes, which never fire the tick event.
     */
    public static boolean deferUpdate(IElectric electric) {
        if (!(electric.getLevelAccessor() instanceof ServerLevel level))
            return false;
        if (electric instanceof SmartBlockEntity sbe && sbe.isVirtual())
            return false;
        PENDING_UPDATES.computeIfAbsent(level, $ -> new LinkedHashSet<>()).add(electric);
        return true;
    }

    /**
     * Runs the queued recomputes of a level: each network once, however many
     * of its members asked, then one sync packet per chunk that asked. Every
     * member used to recompute the whole network and send its own packet, so a
     * grid of n blocks cost n full passes and n packets whenever it changed.
     */
    public static void flushUpdates(ServerLevel level) {
        Set<IElectric> requesters = PENDING_UPDATES.remove(level);
        if (requesters == null || requesters.isEmpty())
            return;
        Set<ElectricalNetwork> done = Collections.newSetFromMap(new IdentityHashMap<>());
        LongSet chunks = new LongOpenHashSet();
        for (IElectric electric : requesters) {
            if (isStale(electric))
                continue;
            ElectricalNetwork network = electric.getOrCreateElectricNetwork();
            if (done.add(network))
                network.updateNetwork();
            BlockPos pos = BlockPos.of(electric.getPos());
            if (chunks.add(ChunkPos.asLong(pos)))
                CatnipServices.NETWORK.sendToClientsTrackingChunk(level, new ChunkPos(pos), new NetworkUpdatePacket(pos));
            electric.sendStuff();
        }
    }
    public ElectricalNetwork getOrCreateNetworkFor(IElectric be) {
        Long id = be.getData().getId();
        Map<Long, ElectricalNetwork> map = networks.computeIfAbsent(be.getLevelAccessor(), $ -> new HashMap<>());

        ElectricalNetwork network = map.get(id);
        if (network != null) {
            // Sweep at most once per tick: this runs on every network lookup,
            // and lookups sit inside loops over the members.
            long now = gameTime(be.getLevelAccessor());
            if (now == Long.MIN_VALUE || network.lastSweepTick != now) {
                network.members.removeIf(ElectricNetworkManager::isStale);
                network.lastSweepTick = now;
            }
            if (network.members.isEmpty()) {
                map.remove(id);
                network = null;
            }
        }

        if (network == null) {
            network = new ElectricalNetwork(id);
            network.add(be);
            be.setNetwork(be.getData().getId());
            map.put(id, network);
        }
        return network;
    }

    public static boolean isStale(IElectric member) {
        if (member == null)
            return true;
        if (member.destroyed())
            return true;
        if (member instanceof BlockEntity be) {
            return be.isRemoved() || be.getLevel() == null;
        }
        return false;
    }
}
