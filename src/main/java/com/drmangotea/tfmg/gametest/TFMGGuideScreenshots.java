package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.items.guide.FactoryGuideItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import vazkii.patchouli.api.PatchouliAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev tool: with {@code -Dtfmg.guide.screenshots=true}, once in a world the
 * client opens every entry of The Factory Handbook, spread by spread, and
 * saves a screenshot of each to {@code screenshots/handbook_*.png} so the
 * layout can be reviewed without clicking through the book by hand.
 * {@code -Dtfmg.guide.screenshots.filter=blast_furnace} limits it to the
 * entries whose id contains that text.
 *
 * Dev only: this package is excluded from the release jar.
 *
 * @author vyrriox
 */
@EventBusSubscriber(modid = TFMG.MOD_ID, value = Dist.CLIENT)
public final class TFMGGuideScreenshots {

    private static final int SETTLE_TICKS = 8;
    private static final int SPREADS_PER_ENTRY = 4;

    private static List<ResourceLocation> entries;
    private static int entry;
    private static int spread;
    private static int wait = 100;
    private static boolean done;

    private TFMGGuideScreenshots() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (done || !Boolean.getBoolean("tfmg.guide.screenshots"))
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;
        if (wait-- > 0)
            return;
        if (entries == null) {
            entries = listEntries(mc);
            TFMG.LOGGER.info("[guide-screenshots] {} entries", entries.size());
            PatchouliAPI.get().openBookGUI(FactoryGuideItem.BOOK);
            wait = SETTLE_TICKS;
            return;
        }
        if (entry == 0 && spread == 0 && mc.screen != null && !shotLanding) {
            Screenshot.grab(mc.gameDirectory, "handbook_000_landing.png", mc.getMainRenderTarget(), msg -> {
            });
            shotLanding = true;
            open();
            return;
        }
        if (entry >= entries.size()) {
            done = true;
            TFMG.LOGGER.info("[guide-screenshots] done");
            if (Boolean.getBoolean("tfmg.gametest.exit"))
                new Thread(() -> Runtime.getRuntime().halt(0), "tfmg-guide-exit").start();
            return;
        }
        if (mc.screen == null) {
            // The entry has fewer spreads than requested: move on.
            next();
            open();
            return;
        }
        ResourceLocation id = entries.get(entry);
        String name = String.format("handbook_%03d_%s_s%d.png", entry + 1, id.getPath().replace('/', '_'), spread + 1);
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {
        });
        next();
        open();
    }

    private static boolean shotLanding;

    private static void next() {
        spread++;
        if (spread >= SPREADS_PER_ENTRY) {
            spread = 0;
            entry++;
        }
    }

    private static void open() {
        if (entry < entries.size())
            PatchouliAPI.get().openBookEntry(FactoryGuideItem.BOOK, entries.get(entry), spread * 2);
        wait = SETTLE_TICKS;
    }

    private static List<ResourceLocation> listEntries(Minecraft mc) {
        String filter = System.getProperty("tfmg.guide.screenshots.filter", "");
        String prefix = "patchouli_books/handbook/en_us/entries/";
        List<ResourceLocation> out = new ArrayList<>();
        mc.getResourceManager().listResources("patchouli_books/handbook/en_us/entries", id -> id.getPath().endsWith(".json"))
                .keySet().stream()
                .filter(id -> id.getNamespace().equals(TFMG.MOD_ID))
                .map(id -> id.getPath().substring(prefix.length(), id.getPath().length() - ".json".length()))
                .filter(path -> path.contains(filter))
                .sorted()
                .forEach(path -> out.add(TFMG.asResource(path)));
        return out;
    }
}
