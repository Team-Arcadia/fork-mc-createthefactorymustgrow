package com.drmangotea.tfmg.base;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

/**
 * Caps how often a block entity sends its data to clients. A machine whose
 * tanks change every tick used to send a full block entity packet every tick;
 * with this it sends at most one every {@link #RATE} ticks, and the last
 * change of a burst is never lost (it goes out when the cooldown ends).
 * Saving is unaffected: callers still mark the block entity changed at once.
 *
 * @author vyrriox
 */
public final class ThrottledSync {

    public static final int RATE = 5;

    private int cooldown;
    private boolean queued;

    /** Sends now if allowed, otherwise queues one send for later. */
    public void request(SmartBlockEntity be) {
        if (cooldown > 0) {
            queued = true;
            return;
        }
        be.sendData();
        queued = false;
        cooldown = RATE;
    }

    /** Call once per tick on the server. */
    public void tick(SmartBlockEntity be) {
        if (cooldown <= 0)
            return;
        cooldown--;
        if (cooldown == 0 && queued) {
            queued = false;
            be.sendData();
            cooldown = RATE;
        }
    }
}
