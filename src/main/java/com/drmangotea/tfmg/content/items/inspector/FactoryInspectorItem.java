package com.drmangotea.tfmg.content.items.inspector;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Factory Inspector. Right-click a machine: it reports what is in place
 * (green), what stops the machine (red) and what to do about it (gold), in the
 * player's language. Right-click the air: it shows how far the player has
 * come along the mod's production chain and what the next step needs.
 *
 * @author vyrriox
 */
public class FactoryInspectorItem extends Item {

    public FactoryInspectorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /**
     * Runs before the clicked block's own interaction: several machines
     * take whatever item is used on them (the winding machine would swallow
     * the inspector into its workpiece slot).
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null)
            return InteractionResult.PASS;
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        InspectionReport report = new InspectionReport();
        for (Component line : inspect(level, pos, report))
            player.sendSystemMessage(line);
        level.playSound(null, pos, report.hasProblems() ? SoundEvents.NOTE_BLOCK_BASS.value() : SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS, 0.6f, report.hasProblems() ? 0.7f : 1.4f);
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResult.CONSUME;
    }

    /**
     * Inspects the block at {@code pos} and returns the full report as chat
     * lines, header and closing verdict included. {@code report} receives the
     * raw lines, so callers can tell whether problems were found.
     */
    public static List<Component> inspect(Level level, BlockPos pos, InspectionReport report) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        boolean specific = be instanceof IInspectable;
        if (be instanceof IInspectable inspectable)
            inspectable.inspect(report);
        if (be instanceof KineticBlockEntity kinetic)
            GenericInspections.kinetic(kinetic, report);
        if (be instanceof IElectric electric)
            GenericInspections.electric(electric, report);
        if (be != null)
            GenericInspections.tanks(be, report);

        boolean tfmg = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(TFMG.MOD_ID);
        List<Component> out = new java.util.ArrayList<>();
        out.add(Component.translatable("tfmg.inspector.header", state.getBlock().getName())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        out.addAll(report.render());
        if (report.isEmpty()) {
            out.add(Component.translatable(tfmg ? "tfmg.inspector.nothing" : "tfmg.inspector.not_tfmg")
                    .withStyle(ChatFormatting.GRAY));
        } else if (!report.hasProblems()) {
            out.add(Component.translatable(specific ? "tfmg.inspector.all_good" : "tfmg.inspector.readings_only")
                    .withStyle(specific ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        return out;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            for (Component line : ProgressionTracker.report(serverPlayer))
                serverPlayer.sendSystemMessage(line);
            player.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tfmg.factory_inspector.hint.block").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tfmg.factory_inspector.hint.air").withStyle(ChatFormatting.GRAY));
    }
}
