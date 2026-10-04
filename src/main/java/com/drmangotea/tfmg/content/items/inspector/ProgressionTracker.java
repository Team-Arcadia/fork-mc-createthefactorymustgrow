package com.drmangotea.tfmg.content.items.inspector;

import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Factory Inspector used on thin air: walks the main production chain
 * of the mod in order, ticks off every step the player has reached (the item
 * was crafted, picked up, placed or is in the inventory), and explains how to
 * reach the next one and which handbook chapter covers it.
 *
 * @author vyrriox
 */
public final class ProgressionTracker {

    private record Milestone(String id, Supplier<? extends ItemLike> item) {
    }

    private static final List<Milestone> MILESTONES = List.of(
            new Milestone("coal_coke", TFMGItems.COAL_COKE),
            new Milestone("fireproof_bricks", TFMGBlocks.FIREPROOF_BRICKS),
            new Milestone("coal_coke_dust", TFMGItems.COAL_COKE_DUST),
            new Milestone("limesand", TFMGItems.LIMESAND),
            new Milestone("blast_stove", TFMGBlocks.BLAST_STOVE),
            new Milestone("blast_furnace", TFMGBlocks.BLAST_FURNACE_OUTPUT),
            new Milestone("steel_ingot", TFMGItems.STEEL_INGOT),
            new Milestone("heavy_plate", TFMGItems.HEAVY_PLATE),
            new Milestone("aluminum_ingot", TFMGItems.ALUMINUM_INGOT),
            new Milestone("steel_mechanism", TFMGItems.STEEL_MECHANISM),
            new Milestone("magnet", TFMGItems.MAGNET),
            new Milestone("generator", TFMGBlocks.GENERATOR),
            new Milestone("electric_motor", TFMGBlocks.ELECTRIC_MOTOR),
            new Milestone("chemical_vat", TFMGBlocks.STEEL_CHEMICAL_VAT),
            new Milestone("surface_scanner", TFMGBlocks.SURFACE_SCANNER),
            new Milestone("pumpjack", TFMGBlocks.PUMPJACK_BASE),
            new Milestone("distillation", TFMGBlocks.STEEL_DISTILLATION_CONTROLLER),
            new Milestone("engine", TFMGBlocks.REGULAR_ENGINE),
            new Milestone("plastic", TFMGItems.PLASTIC_SHEET)
    );

    private ProgressionTracker() {
    }

    public static List<Component> report(ServerPlayer player) {
        List<Component> out = new ArrayList<>();
        int reached = 0;
        Milestone next = null;
        List<Component> steps = new ArrayList<>();
        for (Milestone milestone : MILESTONES) {
            boolean done = reached(player, milestone.item().get().asItem());
            if (done)
                reached++;
            else if (next == null)
                next = milestone;
            steps.add(Component.literal(done ? "✔ " : "✖ ").withStyle(done ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                    .append(Component.translatable("tfmg.inspector.milestone." + milestone.id())
                            .withStyle(done ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY)));
        }
        out.add(Component.translatable("tfmg.inspector.progress.header", reached, MILESTONES.size())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        out.addAll(steps);
        if (next == null) {
            out.add(Component.translatable("tfmg.inspector.progress.complete").withStyle(ChatFormatting.GREEN));
        } else {
            out.add(Component.literal("➜ ").withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable("tfmg.inspector.progress.next",
                            Component.translatable("tfmg.inspector.milestone." + next.id()).withStyle(ChatFormatting.YELLOW))));
            out.add(Component.translatable("tfmg.inspector.milestone." + next.id() + ".how").withStyle(ChatFormatting.WHITE));
        }
        out.add(Component.translatable("tfmg.inspector.progress.footer").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return out;
    }

    private static boolean reached(ServerPlayer player, Item item) {
        if (player.getInventory().hasAnyMatching(s -> s.is(item)))
            return true;
        ServerStatsCounter stats = player.getStats();
        return stats.getValue(Stats.ITEM_CRAFTED.get(item)) > 0
                || stats.getValue(Stats.ITEM_PICKED_UP.get(item)) > 0
                || stats.getValue(Stats.ITEM_USED.get(item)) > 0;
    }
}
