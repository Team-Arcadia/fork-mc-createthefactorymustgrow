package com.drmangotea.tfmg.content.items.blueprint.client;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.config.TFMGClientConfig.BlueprintHudPosition;
import com.drmangotea.tfmg.config.TFMGConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Map;

/**
 * The Factory Blueprint's own panel. Patchouli draws its multiblock progress at
 * the top centre of the screen, where Jade and similar mods draw too, and only
 * names the block in view when the crosshair hits a real block, so looking at
 * an empty ghost spot showed nothing. While a blueprint is projected this
 * panel replaces Patchouli's: layer, progress, and the ghost block in view,
 * found by walking the view ray through the ghost itself. Its corner is a
 * client setting.
 *
 * @author vyrriox
 */
@OnlyIn(Dist.CLIENT)
public final class BlueprintHud {

    private static final ResourceLocation LAYER = TFMG.asResource("blueprint_hud");
    private static final ResourceLocation PATCHOULI_LAYER = ResourceLocation.fromNamespaceAndPath("patchouli", "multiblock_progress");
    private static final int WIDTH = 182, MARGIN = 6;

    /** The ghost position named on the last frame, for the dev showcase. */
    public static BlockPos lastLooking;

    private BlueprintHud() {
    }

    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, BlueprintHud::render);
    }

    /** Hides Patchouli's progress overlay while it shows one of our layers. */
    public static void hidePatchouliOverlay(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(PATCHOULI_LAYER) && BlueprintProjector.INSTANCE.isProjecting())
            event.setCanceled(true);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        BlueprintProjector projector = BlueprintProjector.INSTANCE;
        if (mc.level == null || mc.player == null || mc.options.hideGui || !projector.isProjecting())
            return;
        Map<BlockPos, BlockState> layer = projector.currentLayerInWorld(mc.level);
        if (layer.isEmpty())
            return;
        int done = 0;
        for (Map.Entry<BlockPos, BlockState> e : layer.entrySet())
            if (mc.level.getBlockState(e.getKey()).is(e.getValue().getBlock()))
                done++;
        BlockPos looking = ghostInView(mc, layer);
        lastLooking = looking;

        Font font = mc.font;
        int height = looking != null ? 46 : 30;
        int screenW = mc.getWindow().getGuiScaledWidth(), screenH = mc.getWindow().getGuiScaledHeight();
        BlueprintHudPosition position = TFMGConfigs.client() == null ? BlueprintHudPosition.TOP_LEFT
                : TFMGConfigs.client().blueprintHudPosition.get();
        int x = switch (position) {
            case TOP_LEFT -> MARGIN;
            case TOP_RIGHT -> screenW - WIDTH - MARGIN;
            case TOP_CENTER, ABOVE_HOTBAR -> (screenW - WIDTH) / 2;
        };
        int y = position == BlueprintHudPosition.ABOVE_HOTBAR ? screenH - 72 - height : MARGIN;

        graphics.fill(x - 3, y - 3, x + WIDTH + 3, y + height, 0xA0101418);
        graphics.drawString(font, font.plainSubstrByWidth(projector.title().getString(), WIDTH), x, y, 0xFFC864, true);

        int barY = y + 12, barH = 7;
        float fraction = (float) done / layer.size();
        graphics.fill(x - 1, barY - 1, x + WIDTH + 1, barY + barH + 1, 0xFF000000);
        graphics.fill(x, barY, x + WIDTH, barY + barH, 0xFF4A4A4A);
        int color = Mth.hsvToRgb(fraction / 3f, 0.9f, 0.95f) | 0xFF000000;
        graphics.fill(x, barY, x + (int) (WIDTH * fraction), barY + barH, color);
        String count = done + "/" + layer.size();
        graphics.drawString(font, count, x + WIDTH - font.width(count), barY + barH + 3, 0xFFFFFF, true);

        if (looking != null) {
            BlockState wanted = layer.get(looking);
            ItemStack icon = new ItemStack(wanted.getBlock());
            int rowY = barY + barH + 14;
            if (!icon.isEmpty())
                graphics.renderItem(icon, x, rowY - 4);
            Component name = wanted.getBlock().getName().copy().withStyle(ChatFormatting.WHITE);
            graphics.drawString(font, font.plainSubstrByWidth(name.getString(), WIDTH - 20), x + 20, rowY, 0xFFFFFF, true);
        }
    }

    /**
     * The first ghost position along the view ray that still needs its
     * block, stopping at the first real block the ray hits. Works in mid-air,
     * where the vanilla crosshair finds nothing.
     */
    private static BlockPos ghostInView(Minecraft mc, Map<BlockPos, BlockState> layer) {
        Level level = mc.level;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1f);
        double reach = mc.player.blockInteractionRange() + 1;
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = ((BlockHitResult) hit).getBlockPos();
            // A correctly placed block in the layer does not stop the ray.
            if (!(layer.containsKey(hitPos) && level.getBlockState(hitPos).is(layer.get(hitPos).getBlock())))
                reach = Math.min(reach, hit.getLocation().distanceTo(eye) + 0.01);
        }
        for (double t = 0; t <= reach; t += 0.05) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(t)));
            BlockState wanted = layer.get(pos);
            if (wanted != null && !level.getBlockState(pos).is(wanted.getBlock()))
                return pos;
        }
        return null;
    }
}
