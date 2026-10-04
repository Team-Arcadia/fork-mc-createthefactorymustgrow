package com.drmangotea.tfmg.content.items.blueprint;

import com.drmangotea.tfmg.TFMG;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.Optional;

/**
 * Master cartographers sell one Factory Blueprint, of a random assembly line,
 * for 32 emeralds and a compass, once. With the rare village chest loot it is
 * the only way to get blueprints, so players hunt and trade for them.
 *
 * @author vyrriox
 */
@EventBusSubscriber(modid = TFMG.MOD_ID)
public final class BlueprintTrades {

    private BlueprintTrades() {
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CARTOGRAPHER)
            return;
        VillagerTrades.ItemListing listing = (trader, random) -> new MerchantOffer(
                new ItemCost(Items.EMERALD, 32),
                Optional.of(new ItemCost(Items.COMPASS)),
                BlueprintLines.stack(BlueprintLines.LINES.get(random.nextInt(BlueprintLines.LINES.size()))),
                1, 30, 0.05f);
        event.getTrades().get(5).add(listing);
    }
}
