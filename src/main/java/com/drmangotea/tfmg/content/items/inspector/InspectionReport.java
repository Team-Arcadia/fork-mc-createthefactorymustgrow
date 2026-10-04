package com.drmangotea.tfmg.content.items.inspector;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Factory Inspector found on a machine. Lines are added under
 * translation keys {@code tfmg.inspector.<key>}, so every report reads in the
 * player's own language (English and French are shipped).
 *
 * <ul>
 *   <li>{@link #ok}: a requirement that is met (green),</li>
 *   <li>{@link #problem}: what stops the machine (red),</li>
 *   <li>{@link #fix}: what to do about it (gold),</li>
 *   <li>{@link #info}: a reading such as a speed or a tank level (grey).</li>
 * </ul>
 *
 * @author vyrriox
 */
public class InspectionReport {

    public enum Kind {
        OK("✔ ", ChatFormatting.GREEN),
        PROBLEM("✖ ", ChatFormatting.RED),
        FIX("➜ ", ChatFormatting.GOLD),
        INFO("• ", ChatFormatting.GRAY);

        final String bullet;
        final ChatFormatting color;

        Kind(String bullet, ChatFormatting color) {
            this.bullet = bullet;
            this.color = color;
        }
    }

    public record Line(Kind kind, Component text) {
    }

    private final List<Line> lines = new ArrayList<>();

    public InspectionReport ok(String key, Object... args) {
        return add(Kind.OK, key, args);
    }

    public InspectionReport problem(String key, Object... args) {
        return add(Kind.PROBLEM, key, args);
    }

    public InspectionReport fix(String key, Object... args) {
        return add(Kind.FIX, key, args);
    }

    public InspectionReport info(String key, Object... args) {
        return add(Kind.INFO, key, args);
    }

    /** A requirement: ok line when met, problem plus fix lines when not. */
    public InspectionReport check(boolean met, String okKey, String problemKey, String fixKey, Object... args) {
        if (met)
            return ok(okKey, args);
        problem(problemKey, args);
        if (fixKey != null)
            fix(fixKey, args);
        return this;
    }

    public InspectionReport add(Kind kind, String key, Object... args) {
        lines.add(new Line(kind, Component.translatable("tfmg.inspector." + key, args)));
        return this;
    }

    public InspectionReport raw(Kind kind, Component text) {
        lines.add(new Line(kind, text));
        return this;
    }

    public boolean hasProblems() {
        for (Line line : lines)
            if (line.kind() == Kind.PROBLEM)
                return true;
        return false;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public List<Component> render() {
        List<Component> out = new ArrayList<>();
        for (Line line : lines) {
            MutableComponent text = Component.literal(line.kind().bullet).withStyle(line.kind().color)
                    .append(line.text().copy().withStyle(line.kind() == Kind.INFO ? ChatFormatting.GRAY : ChatFormatting.WHITE));
            out.add(text);
        }
        return out;
    }
}
