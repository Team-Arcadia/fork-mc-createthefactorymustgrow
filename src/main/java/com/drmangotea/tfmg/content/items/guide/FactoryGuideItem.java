package com.drmangotea.tfmg.content.items.guide;

import com.drmangotea.tfmg.TFMG;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import vazkii.patchouli.api.PatchouliAPI;

import java.util.List;

/**
 * The Factory Handbook: right-click to open the in-game guide to the whole
 * mod, a Patchouli book with a chapter per production step and a 3D view of
 * every multiblock.
 *
 * @author vyrriox
 */
public class FactoryGuideItem extends Item {

    public static final ResourceLocation BOOK = TFMG.asResource("handbook");

    public FactoryGuideItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer)
            PatchouliAPI.get().openBookGUI(serverPlayer, BOOK);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tfmg.factory_guide.hint").withStyle(ChatFormatting.GRAY));
    }
}
