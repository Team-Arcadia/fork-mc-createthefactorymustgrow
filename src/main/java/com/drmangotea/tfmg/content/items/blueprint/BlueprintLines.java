package com.drmangotea.tfmg.content.items.blueprint;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The assembly lines a Factory Blueprint can carry. Each blueprint shows the
 * multiblocks of one line only, so players have to find, buy or trade one per
 * line. The structures of each line are tagged in the generated blueprint
 * data (tools/handbook/build_handbook.py).
 *
 * @author vyrriox
 */
public final class BlueprintLines {

    /**
     * In the order of a factory's progression. The ids are also the values of
     * the item model's line property and of the generated blueprints' "line".
     */
    public static final List<String> LINES = List.of("coke", "steel", "aluminium", "chemistry", "oil", "refining",
            "engines", "power", "electricity");

    /**
     * Lines with their own item texture, in model override order. A line at
     * position i is drawn by textures/item/factory_blueprint_&lt;line&gt;.png and
     * selected by the {@link #TEXTURE_PROPERTY} value i + 1; a blank blueprint
     * or a line missing from this list keeps the base texture (value 0).
     * To give a new line its own look, append its id here, add the texture
     * and rerun datagen. Only append, so existing indices stay put.
     */
    public static final List<String> TEXTURED = List.of(
            "steel", "chemistry", "oil", "power",
            "coke", "aluminium", "refining", "engines", "electricity");

    /** Item property the blueprint model overrides switch on. */
    public static final ResourceLocation TEXTURE_PROPERTY = TFMG.asResource("line");

    private BlueprintLines() {
    }

    public static ItemStack stack(String line) {
        ItemStack stack = TFMGItems.FACTORY_BLUEPRINT.asStack();
        stack.set(TFMGDataComponents.BLUEPRINT_LINE, line);
        return stack;
    }

    /** The line of a blueprint stack, or null for a blank one. */
    public static String lineOf(ItemStack stack) {
        String line = stack.get(TFMGDataComponents.BLUEPRINT_LINE);
        return line != null && LINES.contains(line) ? line : null;
    }

    /** The {@link #TEXTURE_PROPERTY} value of a stack: 0 for the base texture. */
    public static int textureIndex(ItemStack stack) {
        // List.of rejects null in indexOf: a blank blueprint has no line and
        // crashed every screen that drew it (the creative tab first).
        String line = lineOf(stack);
        return line == null ? 0 : TEXTURED.indexOf(line) + 1;
    }
}
