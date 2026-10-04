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

    private static final int SPACING = 10;

    private enum Step { BUILD, VIEW, SHOOT_VIEW, INSPECT, SHOOT_INSPECT, BLUEPRINT, SHOOT_BLUEPRINT, EXIT, DONE }

    private static Step step = Step.BUILD;
    private static int wait = 120;
    private static int index;
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
                teleport(server, mc, pad.getX() + 2.5, pad.getY() + 5, pad.getZ() - 7, 0, 25);
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
                // Project the steel line's first structure on an empty spot.
                BlockPos ground = origin.offset(0, -1, 14 + 30);
                server.execute(() -> server.overworld().setBlock(ground, net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3));
                teleport(server, mc, ground.getX() + 0.5, ground.getY() + 4, ground.getZ() - 6, 0, 30);
                mc.player.setItemInHand(InteractionHand.MAIN_HAND, BlueprintLines.stack("steel"));
                BlueprintProjector.INSTANCE.useOnBlock(mc.player, "steel", ground, Direction.UP, false);
                step = Step.SHOOT_BLUEPRINT;
                wait = 40;
            }
            case SHOOT_BLUEPRINT -> {
                shoot(mc, "showcase_blueprint_projection.png");
                // Screenshots are written off-thread: give the last one time.
                step = Step.EXIT;
                wait = 60;
            }
            case EXIT -> {
                step = Step.DONE;
                TFMG.LOGGER.info("[showcase] done, {} structures", structures.size());
                if (Boolean.getBoolean("tfmg.gametest.exit"))
                    new Thread(() -> Runtime.getRuntime().halt(0), "tfmg-showcase-exit").start();
            }
            default -> {
            }
        }
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
