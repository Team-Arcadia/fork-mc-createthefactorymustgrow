package com.drmangotea.tfmg.gametest;

import net.neoforged.fml.loading.FMLEnvironment;
import com.drmangotea.tfmg.TFMG;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every TFMG game test inside a normal server - the integrated server of
 * a singleplayer client, or a dedicated server - when the JVM is started with
 * {@code -Dtfmg.gametest.autorun=true}. The dedicated gameTestServer run
 * already covers the bare server; this covers the paths a real game takes
 * (client present, integrated server, regular world).
 *
 * The result goes to the log and to {@code tfmg-gametest-result.txt} in the
 * game directory. With {@code -Dtfmg.gametest.exit=true} the process then
 * exits with the number of failed tests as its status.
 *
 * Dev only: this package is excluded from the release jar.
 *
 * @author vyrriox
 */
@EventBusSubscriber(modid = TFMG.MOD_ID)
public final class TFMGGameTestAutorun {

    private TFMGGameTestAutorun() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (server instanceof GameTestServer || !Boolean.getBoolean("tfmg.gametest.autorun"))
            return;
        ServerLevel level = server.overworld();
        List<GameTestInfo> infos = new ArrayList<>();
        GameTestRegistry.getAllTestFunctions().stream()
                .filter(f -> f.structureName().startsWith(TFMG.MOD_ID + ":"))
                .forEach(f -> infos.add(new GameTestInfo(f, Rotation.NONE, level, RetryOptions.noRetries())));
        TFMG.LOGGER.info("[gametest-autorun] running {} TFMG tests on {}", infos.size(),
                server.isDedicatedServer() ? "a dedicated server" : "the integrated server");

        BlockPos origin = level.getSharedSpawnPos().offset(0, 0, 64).atY(level.getMinBuildHeight() + 70);
        GameTestRunner runner = GameTestRunner.Builder.fromInfo(infos, level)
                .newStructureSpawner(new StructureGridSpawner(origin, 8, false))
                .build();
        MultipleTestTracker tracker = new MultipleTestTracker(runner.getTestInfos());
        List<String> failures = new ArrayList<>();
        tracker.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {
            }

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner r) {
                finishIfDone(server, tracker, failures);
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner r) {
                failures.add(info.getTestName() + ": " + (info.getError() == null ? "?" : info.getError().getMessage()));
                finishIfDone(server, tracker, failures);
            }

            @Override
            public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner r) {
            }
        });
        runner.start();
    }

    private static void finishIfDone(MinecraftServer server, MultipleTestTracker tracker, List<String> failures) {
        if (!tracker.isDone())
            return;
        int failed = failures.size();
        String summary = "[gametest-autorun] " + (tracker.getTotalCount() - failed) + "/" + tracker.getTotalCount()
                + " TFMG tests passed on " + (server.isDedicatedServer() ? "dedicated server" : "integrated server");
        TFMG.LOGGER.info(summary);
        for (String failure : failures)
            TFMG.LOGGER.error("[gametest-autorun] FAILED {}", failure);
        List<String> lines = new ArrayList<>();
        lines.add(summary);
        failures.forEach(f -> lines.add("FAILED " + f));
        try {
            Files.write(server.getServerDirectory().resolve("tfmg-gametest-result.txt"), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            TFMG.LOGGER.error("[gametest-autorun] could not write the result file", e);
        }
        if (Boolean.getBoolean("tfmg.gametest.exit")) {
            if (failed == 0 && FMLEnvironment.dist.isClient()) {
                // Halting under a live render thread crashes in the graphics
                // driver; a clean stop exits with status 0.
                ClientExit.stop();
                return;
            }
            // Off the server thread: exiting from inside the tick would wait
            // on the very thread that is shutting down.
            Thread exit = new Thread(() -> Runtime.getRuntime().halt(failed), "tfmg-gametest-exit");
            exit.start();
        }
    }
}
