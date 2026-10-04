package com.drmangotea.tfmg;

import com.drmangotea.tfmg.content.items.blueprint.BlueprintLines;
import com.drmangotea.tfmg.content.items.weapons.advanced_potato_cannon.AdvancedPotatoCannonRenderHandler;
import com.drmangotea.tfmg.content.items.weapons.explosives.thermite_grenades.fire.TFMGColoredFires;
import com.drmangotea.tfmg.content.items.weapons.flamethrover.FlamethrowerRenderHandler;
import com.drmangotea.tfmg.content.items.weapons.quad_potato_cannon.QuadPotatoCannonRenderHandler;
import com.drmangotea.tfmg.ponder.TFMGPonderPlugin;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.drmangotea.tfmg.registry.TFMGParticleTypes;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = TFMG.MOD_ID, dist = Dist.CLIENT)
public class TFMGClient {

    /**
     * does not work, too bad!
     */
    public static final QuadPotatoCannonRenderHandler QUAD_POTATO_CANNON_RENDER_HANDLER = new QuadPotatoCannonRenderHandler();
    public static final AdvancedPotatoCannonRenderHandler ADVANCED_POTATO_CANNON_RENDER_HANDLER = new AdvancedPotatoCannonRenderHandler();

    public static final FlamethrowerRenderHandler FLAMETHROWER_RENDER_HANDLER = new FlamethrowerRenderHandler();

    public TFMGClient(IEventBus modEventBus) {
        onCtorClient(modEventBus);
    }

    public static void onCtorClient(IEventBus modEventBus) {
        IEventBus neoEventBus = NeoForge.EVENT_BUS;

        modEventBus.addListener(TFMGClient::clientInit);
        // The Factory Blueprint's projection is client-side; the item reaches
        // it through this handler and it advances layers on the client tick.
        com.drmangotea.tfmg.content.items.blueprint.FactoryBlueprintItem.CLIENT =
                com.drmangotea.tfmg.content.items.blueprint.client.BlueprintProjector.INSTANCE;
        neoEventBus.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) ->
                com.drmangotea.tfmg.content.items.blueprint.client.BlueprintProjector.INSTANCE.tick());
        modEventBus.addListener(TFMGParticleTypes::registerFactories);

        QUAD_POTATO_CANNON_RENDER_HANDLER.registerListeners(neoEventBus);
        ADVANCED_POTATO_CANNON_RENDER_HANDLER.registerListeners(neoEventBus);
    }

    @SuppressWarnings("deprecation")
    public static void clientInit(final FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new TFMGPonderPlugin());
        ItemBlockRenderTypes.setRenderLayer(TFMGColoredFires.GREEN_FIRE.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(TFMGColoredFires.BLUE_FIRE.get(), RenderType.cutout());
        // Each Factory Blueprint line shows its own texture (see BlueprintLines.TEXTURED).
        event.enqueueWork(() -> ItemProperties.register(TFMGItems.FACTORY_BLUEPRINT.get(),
                BlueprintLines.TEXTURE_PROPERTY, (stack, level, entity, seed) -> BlueprintLines.textureIndex(stack)));
    }
}
