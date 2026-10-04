package com.drmangotea.tfmg.content.engines.engine_controller;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.base.lang.TFMGLang;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.content.engines.types.AbstractSmallEngineBlockEntity;
import com.drmangotea.tfmg.content.engines.upgrades.TransmissionUpgrade;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;


import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;


public class EngineControllerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, MenuProvider, com.drmangotea.tfmg.content.items.inspector.IInspectable {

    private UUID user;
    private UUID prevUser;
    private boolean deactivatedThisTick;

    public TransmissionUpgrade.TransmissionState shift = TransmissionUpgrade.TransmissionState.NEUTRAL;
    public int accelerationRate = 0;
    public AbstractSmallEngineBlockEntity engine = null;
    public BlockPos enginePos = null;
    public boolean engineStarted = false;

    public boolean clutch = false;
    public boolean brake = false;
    public boolean gas = false;

    public ItemStackHandler frequencyItems = new ItemStackHandler(6);

    /// rendering
    public LerpedFloat transmissionLeverAngle = LerpedFloat.angular();
    public LerpedFloat steeringWheelAngle = LerpedFloat.angular();
    public LerpedFloat clutchPedalMotion = LerpedFloat.linear();
    public LerpedFloat gasPedalMotion = LerpedFloat.linear();
    public LerpedFloat brakePedalMotion = LerpedFloat.linear();
    public LerpedFloat fuelDial = LerpedFloat.angular();
    public LerpedFloat rpmDial = LerpedFloat.angular();

    public EngineControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ItemStack getController() {
        return ItemStack.EMPTY;
    }

    public static Couple<RedstoneLinkNetworkHandler.Frequency> toFrequency(EngineControllerBlockEntity controller, int slot) {
        ItemStackHandler frequencyItems = controller.frequencyItems;
        return Couple.create(RedstoneLinkNetworkHandler.Frequency.of(frequencyItems.getStackInSlot(slot * 2)),
                RedstoneLinkNetworkHandler.Frequency.of(frequencyItems.getStackInSlot(slot * 2 + 1)));
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries , clientPacket);

        if (enginePos != null)
            compound.putLong("EnginePos", enginePos.asLong());
        compound.putString("Shift", shift.name());

        compound.putBoolean("EngineStarted", engineStarted);

        compound.put("FrequencyItems", frequencyItems.serializeNBT(registries));

        if (user != null)
            compound.putUUID("User", user);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);
        enginePos = compound.contains("EnginePos") ? BlockPos.of(compound.getLong("EnginePos")) : null;

        engineStarted = compound.getBoolean("EngineStarted");
        if (engineStarted && accelerationRate == 0)
            accelerationRate = 4;
        frequencyItems.deserializeNBT(registries,compound.getCompound("FrequencyItems"));
        String shiftName = compound.getString("Shift");
        if (!shiftName.isEmpty()) {
            try {
                shift = TransmissionUpgrade.TransmissionState.valueOf(shiftName);
            } catch (IllegalArgumentException ignored) {
                // tag was written with a value that no longer exists in the enum
            }
        }
        user = compound.hasUUID("User") ? compound.getUUID("User") : null;
        updateEngine();
    }

    public void shiftForward() {
        int max = TransmissionUpgrade.TransmissionState.values().length - 1;
        for (int i = 0; i < max; i++) {
            TransmissionUpgrade.TransmissionState state = TransmissionUpgrade.TransmissionState.values()[i];
            if (state == shift && i + 1 <= max) {
                shift = TransmissionUpgrade.TransmissionState.values()[i + 1];
                updateShift();
                break;
            }

        }
        sendData();
        setChanged();
    }

    public void updateShift() {
        if (enginePos != null)
            if (level.getBlockEntity(enginePos) instanceof AbstractSmallEngineBlockEntity be) {
                AbstractSmallEngineBlockEntity controller = be.getControllerBE();
                if (controller == null)
                    return;
                controller.shift = shift;
                controller.clutchPressed = clutch;
                controller.updateGeneratedRotation();
                if (controller.engineLength() > 1) {
                    if (level.getBlockEntity(BlockPos.of(controller.engines.get(controller.engineLength() - 1))) instanceof AbstractSmallEngineBlockEntity be2) {
                        be2.updateGeneratedRotation();
                    }
                }
            }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (user == null || !(level instanceof ServerLevel sl))
            return;
        Entity playerEntity = sl.getEntity(user);
        if (playerEntity instanceof Player)
            stopUsing((Player) playerEntity);
    }

    public void shiftBack() {
        int max = TransmissionUpgrade.TransmissionState.values().length;
        for (int i = 0; i < max; i++) {
            TransmissionUpgrade.TransmissionState state = TransmissionUpgrade.TransmissionState.values()[i];
            if (state == shift && i - 1 >= 0) {
                shift = TransmissionUpgrade.TransmissionState.values()[i - 1];
                updateShift();
                break;
            }
        }
    }

    public void tickAcceleration() {

        if (gas) {
            if (engineStarted && accelerationRate < 15) {
                accelerationRate++;
                this.updateEngine();
            }
        } else {
            if ((accelerationRate > 4 || !engineStarted) && accelerationRate > 0) {
                accelerationRate--;
                this.updateEngine();
            }
        }


    }

    public void tickBraking() {

        if (brake && accelerationRate > 4) {
            accelerationRate--;
            updateEngine();
        }

    }

    @Override
    public void lazyTick() {
        super.lazyTick();

        tickAcceleration();
    }

    public static ItemStackHandler getFrequencyItems(EngineControllerBlockEntity be) {
        return be.frequencyItems;
    }

    public void handleInput(Collection<Integer> currentlyPressed, boolean press) {
        if (currentlyPressed.contains(4)) {
            this.clutch = press;
            this.sendData();
            this.setChanged();
        }
        if (currentlyPressed.contains(0)) {
            this.gas = press;
            this.sendData();
            this.setChanged();
        }
        if (currentlyPressed.contains(1)) {
            this.brake = press;
            this.sendData();
            this.setChanged();
        }
    }

    public void toggleEngine() {
        this.engineStarted = !this.engineStarted;
        this.accelerationRate = this.engineStarted ? 4 : 0;
        this.updateEngine();
        this.sendData();
        this.setChanged();

    }

    @Override
    public void remove() {
        super.remove();
        // Breaking the controller under a seated player must release them,
        // or the persistent flag locks them out of every other controller.
        if (level != null && !level.isClientSide && user != null && level instanceof ServerLevel serverLevel) {
            Player seated = serverLevel.getServer().getPlayerList().getPlayer(user);
            if (seated != null)
                seated.getPersistentData().remove("IsUsingEngineController");
        }
        disconnectEngine();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

        TFMGTexts.header("engine_controller").forGoggles(tooltip);

        // Whether a transmission actually bound an engine here was invisible:
        // "start" on an unlinked controller is a silent no-op, which read as
        // a controller that just does not work.
        if (enginePos != null) {
            TFMGLang.translate("engine_controller.linked_engine",
                    enginePos.getX() + " " + enginePos.getY() + " " + enginePos.getZ())
                    .style(ChatFormatting.AQUA).forGoggles(tooltip);
            TFMGLang.translate(engineStarted ? "engine_controller.engine_started" : "engine_controller.engine_stopped")
                    .style(engineStarted ? ChatFormatting.GREEN : ChatFormatting.GOLD).forGoggles(tooltip);
            TFMGTexts.Engine.shift(shift.langKey).forGoggles(tooltip);
        } else {
            TFMGLang.translate("engine_controller.no_engine")
                    .style(ChatFormatting.GRAY).forGoggles(tooltip);
        }

        return true;
    }


    @Override
    public void tick() {
        super.tick();

        if (level.isClientSide) {
            CatnipServices.PLATFORM.executeOnClientOnly(() -> this::tryToggleActive);
            prevUser = user;
            tickRendering();
        }

        // Drop the cached engine when its block entity was replaced (chunk
        // reload, break-and-replace) — it used to be re-resolved only when
        // null, so throttle input silently went to a dead instance.
        if (engine != null && (engine.isRemoved() || !engine.getBlockPos().equals(enginePos)))
            engine = null;
        if (enginePos != null && (engine == null)) {
            if (level.getBlockEntity(enginePos) instanceof AbstractSmallEngineBlockEntity be) {
                engine = be;
                // Push the actual acceleration rate instead of a hardcoded
                // 4/15 idle value.
                updateEngine();
            }
        }

        tickBraking();
        if (!level.isClientSide) {
            deactivatedThisTick = false;

            if (!(level instanceof ServerLevel))
                return;
            if (user == null)
                return;

            Entity entity = ((ServerLevel) level).getEntity(user);
            if (!(entity instanceof Player)) {
                // The seated player left this dimension: resolve them through
                // the player list so their persistent flag is cleared too, or
                // every controller rejects them until they relog.
                stopUsing(((ServerLevel) level).getServer().getPlayerList().getPlayer(user));
                return;
            }

            Player player = (Player) entity;
            if (!playerInRange(player, level, worldPosition) || !playerIsUsingEngineController(player))
                stopUsing(player);
        }
    }

    @Override
    public void inspect(com.drmangotea.tfmg.content.items.inspector.InspectionReport report) {
        if (enginePos == null) {
            report.problem("controller.no_engine");
            report.fix("controller.no_engine.fix");
            return;
        }
        String where = enginePos.getX() + " " + enginePos.getY() + " " + enginePos.getZ();
        if (!level.isLoaded(enginePos)) {
            report.info("controller.engine_unloaded", where);
            return;
        }
        if (!(level.getBlockEntity(enginePos) instanceof AbstractSmallEngineBlockEntity linked)) {
            report.problem("controller.engine_missing", where);
            report.fix("controller.no_engine.fix");
            return;
        }
        report.ok("controller.linked", where);
        AbstractSmallEngineBlockEntity master = linked;
        if (!linked.isController()) {
            if (linked.controller == null || !level.isLoaded(linked.controller)
                    || !(level.getBlockEntity(linked.controller) instanceof AbstractSmallEngineBlockEntity be))
                return;
            master = be;
        }
        if (!getBlockPos().equals(master.engineController)) {
            report.problem("controller.link_lost");
            report.fix("controller.no_engine.fix");
        }
        report.info("controller.state", net.minecraft.network.chat.Component.translatable("tfmg." + shift.langKey), accelerationRate);
        report.info("engine.section");
        // The engine's own report covers start, throttle and neutral gear.
        master.inspect(report);
    }

    public void updateEngine() {
        if (engine == null)
            return;
        AbstractSmallEngineBlockEntity controller = engine.getControllerBE();
        if (controller == null)
            return;
        controller.engineController = this.getBlockPos();
        controller.highestSignal = accelerationRate / 15f;
        controller.updateRotation();
    }

    public void disconnectEngine() {
        if (engine == null)
            return;

        AbstractSmallEngineBlockEntity controller = engine.getControllerBE();
        if (controller == null)
            return;
        controller.highestSignal = 0;
        controller.engineController = null;
        controller.updateGeneratedRotation();
    }

    public void tickRendering() {
        if (Minecraft.getInstance()
                .isPaused())
            return;


        steeringWheelAngle.chase(isPressed(2) ? -40 : isPressed(3) ? 40 : 0, 0.25, LerpedFloat.Chaser.EXP);
        clutchPedalMotion.chase(isPressed(4) ? 2 / 16f : 0, 0.25, LerpedFloat.Chaser.EXP);
        gasPedalMotion.chase(isPressed(0) ? 2 / 16f : 0, 0.25, LerpedFloat.Chaser.EXP);
        brakePedalMotion.chase(isPressed(1) ? 2 / 16f : 0, 0.25, LerpedFloat.Chaser.EXP);
        transmissionLeverAngle.chase(shift == TransmissionUpgrade.TransmissionState.REVERSE ? -20 : (shift.value * 20), 0.25, LerpedFloat.Chaser.EXP);
        fuelDial.chase(engine == null ? 0 : ((double) engine.getControllerBE().fuelTank.getFluidAmount() / (double) engine.fuelTank.getCapacity()) * 180f, 0.25, LerpedFloat.Chaser.EXP);
        rpmDial.chase(engine == null ? 0 : ((double) engine.getControllerBE().rpm / 6000f) * 180f, 0.25, LerpedFloat.Chaser.EXP);

        transmissionLeverAngle.tickChaser();
        steeringWheelAngle.tickChaser();
        clutchPedalMotion.tickChaser();
        gasPedalMotion.tickChaser();
        brakePedalMotion.tickChaser();
        fuelDial.tickChaser();
        rpmDial.tickChaser();
    }

    public boolean isPressed(int id) {
        return EngineControllerClientHandler.currentlyPressed.contains(id);
    }

    @OnlyIn(Dist.CLIENT)
    private void tryToggleActive() {
        if (user == null && Minecraft.getInstance().player.getUUID().equals(prevUser)) {
            EngineControllerClientHandler.deactivateInLectern();
        } else if (prevUser == null && Minecraft.getInstance().player.getUUID().equals(user)) {
            EngineControllerClientHandler.activateInLectern(worldPosition);
        }
    }

    public void tryStopUsing(Player player) {
        if (isUsedBy(player))
            stopUsing(player);
    }

    public static boolean playerIsUsingEngineController(Player player) {
        return player.getPersistentData().contains("IsUsingEngineController");
    }

    public void tryStartUsing(Player player) {


        if (!deactivatedThisTick && !hasUser() && !playerIsUsingEngineController(player) && playerInRange(player, level, worldPosition)) {
            startUsing(player);
        }
    }


    private void startUsing(Player player) {

        user = player.getUUID();
        player.getPersistentData().putBoolean("IsUsingEngineController", true);
        sendData();
    }

    private void stopUsing(Player player) {


        user = null;
        if (player != null)
            player.getPersistentData().remove("IsUsingEngineController");
        deactivatedThisTick = true;
        sendData();
    }

    public InteractionResult use(Player player) {
        if (player == null)
            return InteractionResult.PASS;
        if (player instanceof FakePlayer)
            return InteractionResult.PASS;
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        player.openMenu(this,getBlockPos());
        return InteractionResult.SUCCESS;
    }

    public boolean isUsedBy(Player player) {
        return hasUser() && user.equals(player.getUUID());
    }

    public boolean hasUser() {
        return user != null;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public static boolean playerInRange(Player player, Level world, BlockPos pos) {
        double reach = 0.4 * player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        return player.distanceToSqr(Vec3.atCenterOf(pos)) < reach * reach;
    }

    @Override
    public Component getDisplayName() {
        return Component.empty();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return EngineControllerMenu.create(pContainerId, pPlayerInventory, this);
    }
}
