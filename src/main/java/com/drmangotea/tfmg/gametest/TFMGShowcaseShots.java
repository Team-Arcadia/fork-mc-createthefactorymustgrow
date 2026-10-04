package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.items.blueprint.BlueprintLines;
import com.drmangotea.tfmg.content.items.blueprint.client.BlueprintProjector;
import com.drmangotea.tfmg.content.items.inspector.FactoryInspectorItem;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dev tool: with {@code -Dtfmg.showcase.shots=true}, once in a singleplayer
 * world the client builds the showcase map, then for every handbook
 * multiblock moves the player in front of it, screenshots it, runs the
 * Factory Inspector on it (report in chat, screenshotted too), and finally
 * projects a Factory Blueprint and screenshots the ghost. Pictures land in
 * {@code screenshots/showcase_*.png}; inspector reports are also logged.
 *
 * Dev only: this package is excluded from the release jar.
 *
 * @author vyrriox
 */
@EventBusSubscriber(modid = TFMG.MOD_ID, value = Dist.CLIENT)
public final class TFMGShowcaseShots {

    private static final int SPACING = TFMGShowcaseCommand.SPACING;

    private enum Step { BUILD, VIEW, SHOOT_VIEW, INSPECT, SHOOT_INSPECT, BLUEPRINT, SHOOT_BLUEPRINT, BUILD_LAYER, CHECK_LAYER, SHOOT_TOOLS, EXIT, DONE }

    private static Step step = Step.BUILD;
    private static int wait = 120;
    private static int index;
    private static int builtLayer;
    private static BlockPos origin;
    private static List<Map.Entry<String, JsonObject>> structures;

    private TFMGShowcaseShots() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (step == Step.DONE || !Boolean.getBoolean("tfmg.showcase.shots"))
            return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null)
            return;
        if (wait-- > 0)
            return;
        switch (step) {
            case BUILD -> {
                structures = new ArrayList<>(TFMGStructureTests.readBlueprints().entrySet());
                BlockPos at = mc.player.blockPosition().above(20);
                origin = at.offset(4, 0, 4);
                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack()
                            .withLevel(level).withPosition(at.getCenter()).withSuppressedOutput(), "tfmgtest showcase");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                            "time set noon");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                            "gamerule doDaylightCycle false");
                });
                step = Step.VIEW;
                wait = 60;
            }
            case VIEW -> {
                if (index >= structures.size()) {
                    step = Step.BLUEPRINT;
                    return;
                }
                BlockPos pad = origin.offset(index * SPACING, 0, 0);
                teleport(server, mc, pad.getX() + 2.5, pad.getY() + 8, pad.getZ() - 11, 0, 28);
                mc.gui.getChat().clearMessages(false);
                step = Step.SHOOT_VIEW;
                wait = 60;
            }
            case SHOOT_VIEW -> {
                shoot(mc, String.format("showcase_%02d_%s.png", index + 1, structures.get(index).getKey()));
                step = Step.INSPECT;
                wait = 4;
            }
            case INSPECT -> {
                // Inspect the most interesting block: the first block entity found.
                BlockPos pad = origin.offset(index * SPACING, 0, 0);
                Map<BlockPos, BlockState> blocks = TFMGStructureTests.blocksOf(structures.get(index).getValue());
                String id = structures.get(index).getKey();
                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    for (BlockPos rel : blocks.keySet()) {
                        BlockPos pos = pad.offset(rel);
                        if (level.getBlockEntity(pos) == null)
                            continue;
                        for (Component line : FactoryInspectorItem.inspect(level, pos, new InspectionReport())) {
                            player.sendSystemMessage(line);
                            TFMG.LOGGER.info("[showcase] {} {}", id, line.getString());
                        }
                        break;
                    }
                });
                mc.gui.getChat().clearMessages(false);
                step = Step.SHOOT_INSPECT;
                wait = 20;
            }
            case SHOOT_INSPECT -> {
                mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
                shoot(mc, String.format("showcase_%02d_%s_inspector.png", index + 1, structures.get(index).getKey()));
                mc.setScreen(null);
                index++;
                step = Step.VIEW;
                wait = 2;
            }
            case BLUEPRINT -> {
                // Project the steel line's first structure on an empty spot,
                // in front of the map (the block rows fill the space behind it).
                BlockPos ground = origin.offset(0, -1, -30);
                server.execute(() -> server.overworld().setBlock(ground, net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3));
                teleport(server, mc, ground.getX() + 0.5, ground.getY() + 4, ground.getZ() - 6, 0, 30);
                mc.player.setItemInHand(InteractionHand.MAIN_HAND, BlueprintLines.stack("steel"));
                BlueprintProjector.INSTANCE.useOnBlock(mc.player, "steel", ground, Direction.UP, false);
                step = Step.SHOOT_BLUEPRINT;
                wait = 40;
            }
            case SHOOT_BLUEPRINT -> {
                shoot(mc, "showcase_blueprint_projection.png");
                step = Step.BUILD_LAYER;
                wait = 20;
            }
            case BUILD_LAYER -> {
                // Place the shown layer exactly where the ghost is drawn: the
                // projector must then move on to the next layer by itself.
                builtLayer = BlueprintProjector.INSTANCE.currentLayer();
                if (builtLayer < 0) {
                    TFMG.LOGGER.info("[showcase] blueprint walkthrough finished");
                    showTools(mc);
                    step = Step.SHOOT_TOOLS;
                    wait = 20;
                    return;
                }
                Map<BlockPos, BlockState> layer = BlueprintProjector.INSTANCE.currentLayerInWorld(mc.level);
                // Default states, as a player would place them: the facing must not matter.
                server.execute(() -> layer.forEach((pos, state) ->
                        server.overworld().setBlock(pos, state.getBlock().defaultBlockState(), 2)));
                step = Step.CHECK_LAYER;
                wait = 20;
            }
            case CHECK_LAYER -> {
                int now = BlueprintProjector.INSTANCE.currentLayer();
                if (now == builtLayer) {
                    TFMG.LOGGER.error("[showcase] blueprint stuck on layer {}", builtLayer + 1);
                    BlueprintProjector.INSTANCE.currentLayerInWorld(mc.level).forEach((pos, state) ->
                            TFMG.LOGGER.error("[showcase]   {} wants {} has {}", pos, state, mc.level.getBlockState(pos)));
                    step = Step.EXIT;
                    wait = 60;
                    return;
                }
                TFMG.LOGGER.info("[showcase] blueprint layer {} built, projector moved to {}", builtLayer + 1,
                        now < 0 ? "done" : "layer " + (now + 1));
                shoot(mc, String.format("showcase_blueprint_layer_%02d.png", builtLayer + 1));
                // Screenshots are written off-thread: give each one time.
                step = Step.BUILD_LAYER;
                wait = 20;
            }
            case SHOOT_TOOLS -> {
                shoot(mc, "showcase_tools_hotbar.png");
                step = Step.EXIT;
                wait = 60;
            }
            case EXIT -> {
                step = Step.DONE;
                TFMG.LOGGER.info("[showcase] done, {} structures", structures.size());
                // A clean stop: halting under a live render thread crashes in
                // the graphics driver.
                if (Boolean.getBoolean("tfmg.gametest.exit"))
                    mc.stop();
            }
            default -> {
            }
        }
    }

    /** Handbook, inspector and one blueprint per line in the hotbar, to see their textures. */
    private static void showTools(Minecraft mc) {
        net.minecraft.world.entity.player.Inventory inventory = mc.player.getInventory();
        inventory.setItem(0, new net.minecraft.world.item.ItemStack(com.drmangotea.tfmg.registry.TFMGItems.FACTORY_GUIDE.get()));
        inventory.setItem(1, new net.minecraft.world.item.ItemStack(com.drmangotea.tfmg.registry.TFMGItems.FACTORY_INSPECTOR.get()));
        for (int i = 0; i < 7 && i < BlueprintLines.LINES.size(); i++)
            inventory.setItem(2 + i, BlueprintLines.stack(BlueprintLines.LINES.get(i)));
        inventory.selected = 0;
    }

    private static void teleport(MinecraftServer server, Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.teleportTo(server.overworld(), x, y, z, yaw, pitch);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {
        });
    }
}
