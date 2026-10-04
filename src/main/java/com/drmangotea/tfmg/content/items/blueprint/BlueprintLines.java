package com.drmangotea.tfmg.content.items.blueprint;

import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGItems;
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
}
