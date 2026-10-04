package com.drmangotea.tfmg.content.engines;

import com.drmangotea.tfmg.base.lang.TFMGLang;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGItems;

import com.tterrag.registrate.util.entry.FluidEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;


import java.util.List;

public class FluidContainingItem extends Item {

    public final FluidEntry<?> fluid;

    public static final int CAPACITY = 4000;

    public FluidContainingItem(Properties p_41383_, FluidEntry<?> fluid) {
        super(p_41383_);
        this.fluid = fluid;
    }

    // Tanks hold the SOURCE fluid; the registrate entry's main object is the
    // flowing variant, which no tank ever matches.
    public Fluid sourceFluid() {
        return fluid.get().getSource();
    }

    // The can and the bottle answer the item fluid capability, so everything
    // built on it - Create's spout and tank interaction, TFMG steel tanks,
    // vats, Mekanism pipes and tanks, JEI filling recipes - can fill and
    // drain them without knowing the item.
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new Handler(stack),
                TFMGItems.OIL_CAN.get(), TFMGItems.COOLING_FLUID_BOTTLE.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(TFMGLang.translateDirect("tooltip.fluid_item", stack.getOrDefault(TFMGDataComponents.AMOUNT, 0))
                .withStyle(ChatFormatting.GREEN)
        );
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        if(!stack.has(TFMGDataComponents.AMOUNT))
            return false;

        return stack.getOrDefault(TFMGDataComponents.AMOUNT, 0) > 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        if(!stack.has(TFMGDataComponents.AMOUNT))
            stack.set(TFMGDataComponents.AMOUNT, 0);

        return 0xC7C4A4;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        if(!stack.has(TFMGDataComponents.AMOUNT))
            stack.set(TFMGDataComponents.AMOUNT, 0);

        return Math.round( 13* ((float)stack.getOrDefault(TFMGDataComponents.AMOUNT, 0)/(float)CAPACITY));
    }

    // Runs BEFORE the clicked block's own interaction. Create's tank answers
    // the click first and, in creative, drains itself while throwing the filled
    // item away, so the can came back empty however full the tank was; it also
    // refuses to fill at all unless the tank holds the can's whole capacity.
    // Taking the click first makes the can behave the same on every container.
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();

        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, context.getClickedFace());
        if (handler == null)
            handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);

        if (player != null && player.isShiftKeyDown() && stack.getOrDefault(TFMGDataComponents.AMOUNT, 0) > 0) {

            // Sneak-clicking a fluid container pours into it; the old code
            // voided the whole content with a fill sound, which on a tank
            // read as "nothing happened" and lost up to 4000 mB. Voiding
            // stays available on blocks that hold no fluid.
            int amount = stack.getOrDefault(TFMGDataComponents.AMOUNT, 0);
            // An engine's fluid handler is its fuel tank, which takes any fluid
            // and gives nothing back: pouring there filled the fuel tank with
            // oil or cooling fluid for good. On an engine, sneaking empties the
            // can as its tooltip says; the engine's own oil and coolant are
            // filled by a plain click (AbstractSmallEngineBlockEntity).
            if (level.getBlockEntity(pos) instanceof com.drmangotea.tfmg.content.engines.base.AbstractEngineBlockEntity)
                handler = null;
            if (handler != null) {
                // Decide on both sides so the click is consumed consistently,
                // but let the server own the change.
                if (handler.fill(new FluidStack(sourceFluid(), amount), IFluidHandler.FluidAction.SIMULATE) <= 0)
                    return InteractionResult.PASS;
                if (level.isClientSide)
                    return InteractionResult.SUCCESS;
                int filled = handler.fill(new FluidStack(sourceFluid(), amount), IFluidHandler.FluidAction.EXECUTE);
                if (filled <= 0)
                    return InteractionResult.PASS;
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
                stack.set(TFMGDataComponents.AMOUNT, amount - filled);
                return InteractionResult.SUCCESS;
            }

            if (level.isClientSide)
                return InteractionResult.SUCCESS;
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f);
            stack.set(TFMGDataComponents.AMOUNT, 0);
            return InteractionResult.SUCCESS;
        }

        // Any block that exposes a fluid handler will do.
        //
        // This used to accept Create's own FluidTankBlockEntity and nothing
        // else, so the bottle could not be filled from a TFMG steel tank, a
        // vat, a pipe or a spout - every container a TFMG player actually
        // builds. The item then sat permanently at 0 mB with no way to fill it
        // and no hint that a vanilla-adjacent Create tank was the one thing
        // that worked.
        if (player == null)
            return InteractionResult.PASS;

        if (handler == null)
            return InteractionResult.PASS;

        int space = CAPACITY - stack.getOrDefault(TFMGDataComponents.AMOUNT, 0);
        if (space <= 0 || player.getCooldowns().isOnCooldown(stack.getItem()))
            return InteractionResult.PASS;

        // Ask for OUR fluid by name: draining by amount alone would happily
        // pull diesel into a cooling fluid bottle out of a multi-tank block.
        // The tanks hold the SOURCE fluid; the registrate entry's main object
        // is the flowing one, which no tank ever matches - that mismatch is
        // what kept every right-click silent.
        FluidStack drained = handler.drain(new FluidStack(sourceFluid(), space), IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty())
            return InteractionResult.PASS;

        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        drained = handler.drain(new FluidStack(sourceFluid(), space), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty())
            return InteractionResult.PASS;

        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f);
        stack.set(TFMGDataComponents.AMOUNT, stack.getOrDefault(TFMGDataComponents.AMOUNT, 0) + drained.getAmount());
        player.getCooldowns().addCooldown(stack.getItem(), 20);

        return InteractionResult.SUCCESS;
    }

    // One tank of the item's fixed fluid, stored as the AMOUNT component.
    public static class Handler implements IFluidHandlerItem {

        private final ItemStack container;
        private final FluidContainingItem item;

        public Handler(ItemStack container) {
            this.container = container;
            this.item = (FluidContainingItem) container.getItem();
        }

        private int getAmount() {
            return container.getOrDefault(TFMGDataComponents.AMOUNT, 0);
        }

        private void setAmount(int amount) {
            container.set(TFMGDataComponents.AMOUNT, amount);
        }

        @Override
        public ItemStack getContainer() {
            return container;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            int amount = getAmount();
            return amount <= 0 ? FluidStack.EMPTY : new FluidStack(item.sourceFluid(), amount);
        }

        @Override
        public int getTankCapacity(int tank) {
            return CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.getFluid().isSame(item.sourceFluid());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource))
                return 0;
            int filled = Math.min(CAPACITY - getAmount(), resource.getAmount());
            if (filled <= 0)
                return 0;
            if (action.execute())
                setAmount(getAmount() + filled);
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource))
                return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            int drained = Math.min(getAmount(), maxDrain);
            if (drained <= 0)
                return FluidStack.EMPTY;
            if (action.execute())
                setAmount(getAmount() - drained);
            return new FluidStack(item.sourceFluid(), drained);
        }
    }
}
