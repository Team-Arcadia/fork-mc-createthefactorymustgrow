package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Shared helpers for the TFMG game tests.
 *
 * @author vyrriox
 */
public final class TFMGGameTestUtil {

    /** 5x5x5 smooth stone floor at y=0, air above. Tests build on y=1. */
    public static final String PLATFORM = TFMG.MOD_ID + ":gametest/platform";
    /** 12x8x12 smooth stone floor at y=0, air above. For multiblocks. */
    public static final String PLATFORM_LARGE = TFMG.MOD_ID + ":gametest/platform_large";

    private TFMGGameTestUtil() {
    }

    public static List<Block> tfmgBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id.getNamespace().equals(TFMG.MOD_ID))
                blocks.add(block);
        }
        return blocks;
    }

    public static TestFunction test(String batch, String name, String template, int timeoutTicks, Consumer<GameTestHelper> body) {
        return new TestFunction(batch, name, template, Rotation.NONE, timeoutTicks, 0L, true, false, 1, 1, false, body);
    }

    /** Lists the keys whose values differ between two tags, recursing into compounds and lists of compounds. */
    public static Set<String> diffKeys(CompoundTag a, CompoundTag b) {
        Set<String> out = new TreeSet<>();
        Set<String> keys = new TreeSet<>(a.getAllKeys());
        keys.addAll(b.getAllKeys());
        for (String key : keys) {
            // Create flags every freshly loaded kinetic block for a speed
            // re-check, and every freshly loaded tank level or lerped value
            // for a forced client sync ("Force"). Both are load behaviour,
            // not lost state.
            if (key.equals("NeedsSpeedUpdate") || key.equals("Force"))
                continue;
            Tag va = a.get(key);
            Tag vb = b.get(key);
            if (va == null || vb == null) {
                out.add(key + (va == null ? " (only after reload)" : " (lost on reload)"));
                continue;
            }
            if (va.equals(vb))
                continue;
            if (va instanceof CompoundTag ca && vb instanceof CompoundTag cb) {
                for (String inner : diffKeys(ca, cb))
                    out.add(key + "." + inner);
            } else if (va instanceof ListTag la && vb instanceof ListTag lb && la.size() == lb.size()) {
                for (int i = 0; i < la.size(); i++) {
                    Tag ea = la.get(i);
                    Tag eb = lb.get(i);
                    if (ea instanceof CompoundTag ca && eb instanceof CompoundTag cb) {
                        for (String inner : diffKeys(ca, cb))
                            out.add(key + "[" + i + "]." + inner);
                    } else if (!ea.equals(eb)) {
                        out.add(key + "[" + i + "] (" + ea + " -> " + eb + ")");
                    }
                }
            } else {
                out.add(key + " (" + va + " -> " + vb + ")");
            }
        }
        return out;
    }

    public static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition)
            helper.fail(message);
    }
}
