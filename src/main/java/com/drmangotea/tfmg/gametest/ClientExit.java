package com.drmangotea.tfmg.gametest;

import net.minecraft.client.Minecraft;

/**
 * Client-only half of the game test autorun exit, kept in its own class so the
 * dedicated server never loads client code.
 *
 * @author vyrriox
 */
final class ClientExit {

    private ClientExit() {
    }

    static void stop() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(mc::stop);
    }
}
