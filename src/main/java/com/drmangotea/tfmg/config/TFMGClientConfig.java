package com.drmangotea.tfmg.config;

/**
 * Client-only settings.
 *
 * @author vyrriox
 */
public class TFMGClientConfig extends net.createmod.catnip.config.ConfigBase {

    public final ConfigEnum<BlueprintHudPosition> blueprintHudPosition = e(BlueprintHudPosition.TOP_LEFT, "blueprintHudPosition",
            "Where the Factory Blueprint panel (layer, progress, block in view) is drawn. "
                    + "Top centre is where Patchouli and Jade draw their own overlays.");

    @Override
    public String getName() {
        return "client";
    }

    public enum BlueprintHudPosition {
        TOP_LEFT, TOP_RIGHT, TOP_CENTER, ABOVE_HOTBAR
    }
}
