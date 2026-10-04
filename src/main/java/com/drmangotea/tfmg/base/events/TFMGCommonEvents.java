package com.drmangotea.tfmg.base.events;


import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.TFMGRegistries;
import com.drmangotea.tfmg.content.decoration.tanks.TFMGFluidTankBlockEntity;
import com.drmangotea.tfmg.content.decoration.tanks.steel.SteelTankBlockEntity;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.content.electricity.storage.AccumulatorBlockEntity;
import com.drmangotea.tfmg.content.electricity.utilities.converter.ConverterBlockEntity;
import com.drmangotea.tfmg.content.electricity.utilities.polarizer.PolarizerBlockEntity;
import com.drmangotea.tfmg.content.engines.FluidContainingItem;
import com.drmangotea.tfmg.content.engines.base.AbstractEngineBlockEntity;
import com.drmangotea.tfmg.content.engines.fuels.EngineFuelTypeManager;
import com.drmangotea.tfmg.content.engines.types.large_engine.LargeEngineBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.blast_furnace.BlastFurnaceHatchBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.blast_furnace.BlastFurnaceOutputBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.blast_stove.BlastStoveBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.casting_basin.CastingBasinBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.coke_oven.CokeOvenBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.air_intake.AirIntakeBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.concrete_hose.ConcreteHoseBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.exhaust.ExhaustBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.firebox.FireboxBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.flarestack.FlarestackBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.gas_lamp.GasLampBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.smokestack.SmokestackBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.winding_machine.WindingMachineBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.controller.DistillationControllerBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.output.DistillationOutputBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.base.PumpjackBaseBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;


@EventBusSubscriber
public class TFMGCommonEvents {



    @SubscribeEvent
    public static void onUnloadWorld(LevelEvent.Unload event) {
        LevelAccessor world = event.getLevel();
        TFMG.NETWORK_MANAGER.onUnloadWorld(world);


    }

    // Runs the electrical network recomputes queued during this level tick.
    @SubscribeEvent
    public static void onLevelTickEnd(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)
            com.drmangotea.tfmg.content.electricity.base.ElectricNetworkManager.flushUpdates(serverLevel);
    }

    @SubscribeEvent
    public static void onLoadWorld(LevelEvent.Load event) {
        LevelAccessor world = event.getLevel();
        TFMG.NETWORK_MANAGER.onLoadWorld(world);
        TFMG.DEPOSITS.levelLoaded(world);
    }



    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(EngineFuelTypeManager.ReloadListener.INSTANCE);
    }

    @SubscribeEvent
    public static void onLivingDamagePost(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        // Item#hurtEnemy only fires for PLAYER attacks, so a mob wielding the
        // lit lithium blade never applied Hellfire. Mirror the blade's
        // hurtEnemy behaviour (140 ticks, stacking) for non-player attackers.
        if (event.getEntity().level().isClientSide)
            return;
        if (!(event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)
                || attacker instanceof Player)
            return;
        if (!attacker.getMainHandItem().is(TFMGItems.LIT_LITHIUM_BLADE.get()))
            return;
        net.minecraft.world.entity.LivingEntity target = event.getEntity();
        net.minecraft.world.effect.MobEffectInstance existing =
                target.getEffect(com.drmangotea.tfmg.registry.TFMGMobEffects.HELLFIRE);
        int duration = 140 + (existing != null ? existing.getDuration() : 0);
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.drmangotea.tfmg.registry.TFMGMobEffects.HELLFIRE, duration));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        // Must run on the SERVER: a player disconnecting while driving an
        // engine controller keeps this persistent flag otherwise and is then
        // rejected by tryStartUsing on every controller forever.
        Player player = event.getEntity();
        if (player != null)
            player.getPersistentData().remove("IsUsingEngineController");
    }
    @EventBusSubscriber
    public static class ModBusEvents {
        @net.neoforged.bus.api.SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {

            AbstractEngineBlockEntity.registerCapabilities(event);
            DistillationOutputBlockEntity.registerCapabilities(event);
            ConcreteHoseBlockEntity.registerCapabilities(event);
            PumpjackBaseBlockEntity.registerCapabilities(event);
            PolarizerBlockEntity.registerCapabilities(event);
            LargeEngineBlockEntity.registerCapabilities(event);
            ConverterBlockEntity.registerCapabilities(event);
            CastingBasinBlockEntity.registerCapabilities(event);
            FireboxBlockEntity.registerCapabilities(event);
            DistillationControllerBlockEntity.registerCapabilities(event);
            AccumulatorBlockEntity.registerCapabilities(event);
            SteelTankBlockEntity.registerCapabilities(event);
            TFMGFluidTankBlockEntity.registerCapabilities(event);
            VatBlockEntity.registerCapabilities(event);
            BlastStoveBlockEntity.registerCapabilities(event);
            SmokestackBlockEntity.registerCapabilities(event);
            ExhaustBlockEntity.registerCapabilities(event);
            BlastFurnaceHatchBlockEntity.registerCapabilities(event);
            FlarestackBlockEntity.registerCapabilities(event);
            GasLampBlockEntity.registerCapabilities(event);
            BlastFurnaceOutputBlockEntity.registerCapabilities(event);
            CokeOvenBlockEntity.registerCapabilities(event);
            AirIntakeBlockEntity.registerCapabilities(event);
            WindingMachineBlockEntity.registerCapabilities(event);
            FluidContainingItem.registerCapabilities(event);
        }

        @SubscribeEvent
        public static void newRegistry(NewRegistryEvent event) {
            event.register(TFMGRegistries.CABLE_TYPE_REGISTRY);
            event.register(TFMGRegistries.ELECTRODE_REGISTRY);
        }
    }


}
