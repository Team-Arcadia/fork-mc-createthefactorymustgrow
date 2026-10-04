package com.drmangotea.tfmg.content.items.guide;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.config.TFMGConfigs;
import com.drmangotea.tfmg.registry.TFMGItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Hands every player the handbook once, on their first login to a world.
 * The flag lives in the player's persisted data, so it survives deaths and
 * relogs and is per world.
 *
 * @author vyrriox
 */
@EventBusSubscriber(modid = TFMG.MOD_ID)
public final class GuideEvents {

    private static final String GIVEN = "tfmg_handbook_given";

    private GuideEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !TFMGConfigs.common().giveHandbookOnFirstJoin.get())
            return;
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN))
            return;
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        player.getInventory().placeItemBackInInventory(TFMGItems.FACTORY_GUIDE.asStack());
    }
}
