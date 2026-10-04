package com.drmangotea.tfmg.content.items.blueprint;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A Factory Blueprint carries one assembly line (see {@link BlueprintLines})
 * and projects that line's multiblocks into the world one layer at a time, as a ghost of the blocks to place. Each layer is checked as it
 * is built and the next one appears once it is complete.
 *
 * <ul>
 *   <li>Right-click the air: pick the next structure (sneak: previous), or
 *       while projecting, skip to the next layer (sneak: previous).</li>
 *   <li>Right-click a block: project layer 1 on top of it, facing you.</li>
 *   <li>Sneak and right-click a block: clear the projection.</li>
 * </ul>
 *
 * The projection is client-side only; the logic lives behind
 * {@link #CLIENT} so this common class never names a client class.
 *
 * @author vyrriox
 */
public class FactoryBlueprintItem extends Item {

    /** Client handlers, set by the client entrypoint. */
    public interface ClientHandler {
        void useInAir(Player player, String line, boolean sneaking);

        void useOnBlock(Player player, String line, BlockPos pos, Direction face, boolean sneaking);
    }

    public static ClientHandler CLIENT;

    public FactoryBlueprintItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null)
            return InteractionResult.PASS;
        String line = BlueprintLines.lineOf(stack);
        if (line == null)
            return InteractionResult.PASS;
        if (context.getLevel().isClientSide && CLIENT != null)
            CLIENT.useOnBlock(player, line, context.getClickedPos(), context.getClickedFace(), player.isShiftKeyDown());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String line = BlueprintLines.lineOf(stack);
        if (line == null) {
            if (level.isClientSide)
                player.displayClientMessage(Component.translatable("tfmg.blueprint.blank"), true);
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide && CLIENT != null)
            CLIENT.useInAir(player, line, player.isShiftKeyDown());
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        String line = BlueprintLines.lineOf(stack);
        if (line == null)
            return Component.translatable("item.tfmg.factory_blueprint.blank");
        return Component.translatable("item.tfmg.factory_blueprint.line", Component.translatable("tfmg.blueprint.line." + line));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String line = BlueprintLines.lineOf(stack);
        if (line == null) {
            tooltip.add(Component.translatable("tfmg.blueprint.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tfmg.blueprint.line." + line + ".contents").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.tfmg.factory_blueprint.hint.air").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tfmg.factory_blueprint.hint.block").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tfmg.factory_blueprint.hint.sneak").withStyle(ChatFormatting.GRAY));
    }
}
