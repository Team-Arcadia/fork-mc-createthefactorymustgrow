package com.drmangotea.tfmg.content.machinery.misc.winding_machine;

import com.drmangotea.tfmg.base.ThrottledSync;
import com.drmangotea.tfmg.base.lang.TFMGLang;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.recipes.WindingRecipe;
import com.drmangotea.tfmg.registry.*;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.item.SmartInventory;

import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.List;
import java.util.Optional;

import static com.drmangotea.tfmg.content.machinery.misc.winding_machine.WindingMachineBlock.POWERED;
import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

public class WindingMachineBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation, IInspectable {

    // Per-tick fluid and progress changes sync at most every few ticks.
    private final ThrottledSync throttledSync = new ThrottledSync();

    LerpedFloat spoolSpeed = LerpedFloat.linear();
    float angle;
    public SmartInventory inventory;
    public WindingMachineItemHandler itemHandler;
    public ItemStack spool = ItemStack.EMPTY;
    public WindingRecipe recipe;
    public int amountWinded = 0;
    // Turns already paid for by a consumed wire but not yet wound onto the
    // mounted spool. One wire buys 125 turns, the crafting recipe's ratio.
    public int wireTurnsPending = 0;
    // The spool metal the pending turns were paid for: copper turns must not
    // land on a constantan spool swapped in while the credit was open.
    private Item wireCreditSpool;
    public static final int TURNS_PER_WIRE = 125;
    public boolean update = false;

    protected ScrollValueBehaviour turnPercentage;

    public WindingMachineBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        setLazyTickRate(10);
        inventory = new SmartInventory(1, this)
                .withMaxStackSize(1)
                .whenContentsChanged(i -> this.onContentsChanged());
        itemHandler = new WindingMachineItemHandler(this);

    }

    /** Mark the spool field dirty and sync it to the client. */
    public void onSpoolChanged() {
        if (level != null && !level.isClientSide) {
            setChanged();
            sendData();
        }
    }


    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                TFMGBlockEntities.WINDING_MACHINE.get(),
                (be, context) -> be.itemHandler
        );
    }


    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        int max = 100;
        turnPercentage = new ScrollValueBehaviour(TFMGLang.translateDirect("winding_machine.turn_percentage"),
                this, new WindingMachineValueBox());
        turnPercentage.between(1, max);
        turnPercentage.value = 20;
        behaviours.add(turnPercentage);

    }

    public void onContentsChanged() {

        findRecipe();
        if (inventory.isEmpty())
            amountWinded = 0;
    }

    public void findRecipe() {

        // An empty slot cannot match anything, and this runs from lazyTick on
        // both sides whether or not the machine is doing something: two recipe
        // manager scans every ten ticks per machine, per side, for a machine
        // sitting idle. Clearing the recipe here is exactly what the two scans
        // below would have concluded. The coke oven caches its lookup for the
        // same reason.
        if (inventory.isEmpty()) {
            recipe = null;
            return;
        }

        Optional<RecipeHolder<WindingRecipe>> optional = TFMGRecipeTypes.WINDING.find(new RecipeWrapper(inventory), level);
        Optional<RecipeHolder<WindingRecipe>> assemblyRecipe = SequencedAssemblyRecipe.getRecipe(this.level, new RecipeWrapper(inventory), TFMGRecipeTypes.WINDING.getType(), WindingRecipe.class);

        if (assemblyRecipe.isPresent()) {
            recipe = assemblyRecipe.get().value();
            return;
        }
        if (optional.isEmpty()) {
            recipe = null;
            return;
        }
        WindingRecipe windingRecipe = optional.get().value();

        if (windingRecipe.getIngredient().test(inventory.getItem(0))) {
            recipe = windingRecipe;
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        onContentsChanged();

        // Blockstate writes are server-side; flag-2 setBlock syncs them.
        if (level == null || level.isClientSide)
            return;
        if (spool.is(TFMGItems.EMPTY_SPOOL.get()) && !getBlockState().getValue(POWERED)) {
            level.setBlock(getBlockPos(), getBlockState().setValue(POWERED, true), 2);
            update = true;
        }
        if (!spool.is(TFMGItems.EMPTY_SPOOL.get()) && getBlockState().getValue(POWERED)) {
            level.setBlock(getBlockPos(), getBlockState().setValue(POWERED, false), 2);
            update = true;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        TFMGTexts.header("winding_machine")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        if (!spool.isEmpty()) {
            TFMGLang.text(spool.getDisplayName().getString().replace("[","").replace("]",""))
                    .color(spool.getBarColor())
                    .forGoggles(tooltip);
            if(spool.get(TFMGDataComponents.SPOOL_AMOUNT)!=null)
                TFMGTexts.turnsLeft(spool.getOrDefault(TFMGDataComponents.SPOOL_AMOUNT, 0))
                    .color(spool.getBarColor())
                    .forGoggles(tooltip);
        }

        // Render progress whenever a recipe is active, even if the spool slot
        // is momentarily empty (it was previously nested in the spool guard, so
        // the progress line vanished while a generic recipe was matched).
        if (recipe != null)
            TFMGTexts.progress(amountWinded + "/" + currentRequiredDuration())
                    .color(spool.isEmpty() ? ChatFormatting.GRAY.getColor() : spool.getBarColor())
                    .forGoggles(tooltip);
        return true;
    }

    /**
     * Effective denominator for a winding recipe. Resistor/coil-producing
     * recipes scale with the scroll-value target so the goggle progress and
     * the actual spool drain agree (1 unit = 1 ohm / 1 turn).
     */
    private int currentRequiredDuration() {
        if (recipe == null)
            return 0;
        int duration = recipe.getProcessingDuration();
        if (!recipe.getRollableResults().isEmpty()) {
            ItemStack template = recipe.getRollableResults().get(0).getStack();
            if (template.is(TFMGBlocks.RESISTOR.asItem())
                    || template.is(TFMGItems.ELECTROMAGNETIC_COIL.get())
                    || template.is(TFMGBlocks.LARGE_COIL.get().asItem())) {
                duration = turnPercentage.getValue() * 10;
            }
        }
        return duration;
    }

    /**
     * True when the workpiece slot holds nothing the machine will still wind:
     * a resistor or coil that reached its target, or an item no winding
     * recipe takes. Automation may only take the slot's item out then.
     */
    public boolean isWorkpieceDone() {
        ItemStack work = inventory.getStackInSlot(0);
        if (work.isEmpty())
            return true;
        int target = turnPercentage.getValue() * 10;
        if (work.is(TFMGBlocks.RESISTOR.asItem()))
            return work.getOrDefault(TFMGDataComponents.RESISTANCE, 0) >= target;
        if (work.is(TFMGItems.ELECTROMAGNETIC_COIL.get()) || work.is(TFMGBlocks.LARGE_COIL.get().asItem()))
            return work.getOrDefault(TFMGDataComponents.COIL_TURNS, 0) >= target;
        return recipe == null;
    }

    @Override
    public void inspect(InspectionReport report) {
        if (level == null)
            return;
        ItemStack slotItem = inventory.getItem(0);
        int spoolTurns = spool.getOrDefault(TFMGDataComponents.SPOOL_AMOUNT, 0);
        boolean emptySpool = spool.is(TFMGItems.EMPTY_SPOOL.get());
        int target = turnPercentage.getValue() * 10;

        if (spool.isEmpty())
            report.info("winding_machine.no_spool_mounted");
        else if (emptySpool)
            report.info("winding_machine.spool_empty", spool.getHoverName());
        else
            report.info("winding_machine.spool", spool.getHoverName(), spoolTurns);
        if (wireTurnsPending > 0)
            report.info("winding_machine.wire_credit", wireTurnsPending);

        // Winding wire onto a spool
        ItemStack wireSpool = spoolForWire(slotItem);
        if (!wireSpool.isEmpty()) {
            if (spool.isEmpty()) {
                report.problem("winding_machine.wire_no_spool", slotItem.getHoverName());
                report.fix("winding_machine.wire_no_spool.fix", wireSpool.getHoverName());
            } else if (!emptySpool && !spool.is(wireSpool.getItem())) {
                report.problem("winding_machine.wire_wrong_spool", slotItem.getHoverName(), spool.getHoverName());
                report.fix("winding_machine.wire_no_spool.fix", wireSpool.getHoverName());
            } else if (!emptySpool && spoolTurns >= 1000) {
                report.problem("winding_machine.spool_full");
                report.fix("winding_machine.spool_full.fix");
            } else {
                report.ok("winding_machine.winding_wire", slotItem.getHoverName(), emptySpool ? 0 : spoolTurns, TURNS_PER_WIRE);
            }
            return;
        }

        if (slotItem.isEmpty()) {
            if (wireTurnsPending > 0 && !spool.isEmpty() && !emptySpool && spoolTurns < 1000) {
                report.ok("winding_machine.winding_credit", spoolTurns);
                return;
            }
            report.problem("winding_machine.no_workpiece");
            report.fix("winding_machine.no_workpiece.fix");
            return;
        }

        boolean isResistor = slotItem.is(TFMGBlocks.RESISTOR.asItem());
        boolean isCoil = slotItem.is(TFMGItems.ELECTROMAGNETIC_COIL.get()) || slotItem.is(TFMGBlocks.LARGE_COIL.get().asItem());
        if (isResistor || isCoil) {
            ItemStack needed = isResistor ? TFMGItems.CONSTANTAN_SPOOL.asStack() : TFMGItems.COPPER_SPOOL.asStack();
            int done = isResistor ? slotItem.getOrDefault(TFMGDataComponents.RESISTANCE, 0) : slotItem.getOrDefault(TFMGDataComponents.COIL_TURNS, 0);
            if (done >= target) {
                report.ok("winding_machine.target_reached", slotItem.getHoverName(), done, target);
                report.fix("winding_machine.take_out.fix");
            } else if (!spool.is(needed.getItem())) {
                report.problem("winding_machine.needs_spool", slotItem.getHoverName(), needed.getHoverName());
                report.fix("winding_machine.mount_spool.fix", needed.getHoverName());
            } else if (spoolTurns <= 0) {
                report.problem("winding_machine.spool_out");
                report.fix("winding_machine.spool_out.fix");
            } else {
                report.ok("winding_machine.adjusting", slotItem.getHoverName(), done, target);
            }
            report.info("winding_machine.target", target);
            return;
        }

        if (recipe == null || recipe.getRollableResults().isEmpty()) {
            report.problem("winding_machine.no_recipe", slotItem.getHoverName());
            report.fix("winding_machine.no_recipe.fix");
            return;
        }
        Component result = recipe.getRollableResults().get(0).getStack().getHoverName();
        int required = currentRequiredDuration();
        ItemStack[] spools = recipe.getSpool().getItems();
        Component spoolName = spools.length > 0 ? spools[0].getHoverName() : Component.literal("?");
        if (amountWinded >= required) {
            report.ok("winding_machine.recipe_done", result);
        } else if (spool.isEmpty() || emptySpool) {
            report.problem("winding_machine.needs_spool", slotItem.getHoverName(), spoolName);
            report.fix("winding_machine.mount_spool.fix", spoolName);
        } else if (!recipe.getSpool().isEmpty() && !recipe.getSpool().test(spool)) {
            report.problem("winding_machine.wrong_spool", spool.getHoverName(), spoolName);
            report.fix("winding_machine.mount_spool.fix", spoolName);
        } else if (spoolTurns <= 0) {
            report.problem("winding_machine.spool_out");
            report.fix("winding_machine.spool_out.fix");
        } else {
            report.ok("winding_machine.recipe", slotItem.getHoverName(), result, amountWinded, required);
            if (spoolTurns < required - amountWinded)
                report.info("winding_machine.spool_short", spoolTurns, required - amountWinded);
        }
    }

    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, inventory);
        Containers.dropItemStack(getLevel(), getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(), spool);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide)
            throttledSync.tick(this);
        performRecipe();
        if (update) {
            level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
            update = false;
        }

        if (level.isClientSide)
            manageRotation();
    }

    public void performRecipe() {
        // Server-only. tick() runs on both sides; this method mutates
        // authoritative state (spool component, slot stack, amountWinded)
        // so it must not run client-side.
        if (level == null || level.isClientSide)
            return;
        if (getSpeed() == 0)
            return;

        ItemStack slotItem = inventory.getItem(0);

        // Winding a spool: wire in the workpiece slot, an empty or matching
        // spool mounted. The machine could previously only UNWIND spools,
        // which made a winding machine that cannot wind one.
        if (windSpool(slotItem))
            return;

        int target = turnPercentage.getValue() * 10;
        boolean isResistor = slotItem.is(TFMGBlocks.RESISTOR.asItem());
        boolean isCoil = slotItem.is(TFMGItems.ELECTROMAGNETIC_COIL.get())
                || slotItem.is(TFMGBlocks.LARGE_COIL.get().asItem());

        // Resistor + constantan spool path. ONE drain per tick, no
        // fall-through into the generic branch below. Returns after a
        // successful drain and also returns if the target is already met.
        if (isResistor && spool.is(TFMGItems.CONSTANTAN_SPOOL.get())) {
            int resistance = slotItem.getOrDefault(TFMGDataComponents.RESISTANCE, 0);
            if (resistance >= target)
                return;
            int spoolAmount = spool.getOrDefault(TFMGDataComponents.SPOOL_AMOUNT, 0);
            if (spoolAmount <= 0)
                return;
            spool.set(TFMGDataComponents.SPOOL_AMOUNT, spoolAmount - 1);
            ItemStack copy = slotItem.copy();
            copy.set(TFMGDataComponents.RESISTANCE, resistance + 1);
            inventory.setStackInSlot(0, copy);
            convertEmptyIfDrained();
            setChanged();
            sendData();
            return;
        }

        // Coil + copper spool path. Symmetric to the resistor path.
        if (isCoil && spool.is(TFMGItems.COPPER_SPOOL.get())) {
            int turns = slotItem.getOrDefault(TFMGDataComponents.COIL_TURNS, 0);
            if (turns >= target)
                return;
            int spoolAmount = spool.getOrDefault(TFMGDataComponents.SPOOL_AMOUNT, 0);
            if (spoolAmount <= 0)
                return;
            spool.set(TFMGDataComponents.SPOOL_AMOUNT, spoolAmount - 1);
            ItemStack copy = slotItem.copy();
            copy.set(TFMGDataComponents.COIL_TURNS, turns + 1);
            inventory.setStackInSlot(0, copy);
            convertEmptyIfDrained();
            setChanged();
            sendData();
            return;
        }

        // Generic winding recipe path (sequenced assembly etc.).
        if (recipe == null)
            return;
        // Both exits of this branch take result 0. A datapack recipe declaring
        // no item result would throw out of the machine tick rather than simply
        // not matching, so refuse it here instead.
        if (recipe.getRollableResults().isEmpty())
            return;
        // Resistor / coil items must NEVER take the generic branch; their
        // dedicated branches above are the only paths that should drain
        // their spool. The generic branch runs amountWinded++ which is
        // unrelated to RESISTANCE / COIL_TURNS targeting.
        if (isResistor || isCoil)
            return;

        int requiredDuration = currentRequiredDuration();

        if (amountWinded >= requiredDuration) {
            // Stamp the freshly-crafted output with the scroll-value target
            // if it's a resistor / coil. Otherwise the recipe's static
            // default (RESISTANCE = 10, COIL_TURNS = 100) would be applied
            // and the dedicated branch above would then drain extra spool
            // climbing back up to the player's target — the '+40 leak' the
            // testers reported. Bake the target into the result.
            // The declared-results guard above does not cover the rolled list:
            // rollResults drops results whose chance roll failed, so a datapack
            // recipe with a single sub-1.0 chance result can hand back an empty
            // list, and get(0) would crash the server tick. An empty roll wins
            // nothing this cycle; the wound turns stand and the next tick
            // finishes the recipe with a fresh roll.
            // A sequenced assembly sub-recipe is one shared instance whose
            // result Create rebinds to whichever item looked it up last. With
            // two machines on the same sequence the cached recipe could hand
            // this machine the other one's step, so look it up again right
            // before rolling, as Create's own machines do.
            findRecipe();
            if (recipe == null)
                return;
            List<ItemStack> rolled = recipe.rollResults(level.random);
            if (rolled.isEmpty())
                return;
            ItemStack result = rolled.get(0);
            if (result.is(TFMGBlocks.RESISTOR.asItem()))
                result.set(TFMGDataComponents.RESISTANCE, target);
            else if (result.is(TFMGItems.ELECTROMAGNETIC_COIL.get())
                    || result.is(TFMGBlocks.LARGE_COIL.get().asItem()))
                result.set(TFMGDataComponents.COIL_TURNS, target);
            inventory.setStackInSlot(0, result);
            recipe = null;
            amountWinded = 0;
            sendData();
            setChanged();
            return;
        }
        if (spool.isEmpty() || spool.is(TFMGItems.EMPTY_SPOOL.get()))
            return;
        // The recipe's own spool must be the one mounted. Checking this only
        // inside the "has turns left" branch let the machine fall through to a
        // free completion with the wrong spool entirely.
        if (!recipe.getSpool().isEmpty() && !recipe.getSpool().test(spool))
            return;
        int spoolAmount = spool.getOrDefault(TFMGDataComponents.SPOOL_AMOUNT, 0);
        // A spool with no turns left — or a spool item that never carried a
        // SPOOL_AMOUNT, which is what /give hands out — used to take an else
        // branch that finished the recipe outright. For a sequenced assembly
        // that meant the winding step was granted for free and the transitional
        // item advanced without a single turn being wound, putting the whole
        // sequence out of phase with what the item asks for next: the reported
        // potentiometer that wanted a steel cogwheel where the winding machine
        // was due. Stalling is the correct answer; the player refills the spool.
        if (spoolAmount <= 0)
            return;
        spool.set(TFMGDataComponents.SPOOL_AMOUNT, spoolAmount - 1);
        amountWinded++;
        convertEmptyIfDrained();
        // Without this the client never receives the per-tick
        // SPOOL_AMOUNT / amountWinded updates, so the goggle tooltip
        // and the spool durability bar stay frozen until the recipe
        // finishes. The dedicated resistor/coil branches already
        // send data per drain — the generic branch was the outlier.
        setChanged();
        throttledSync.request(this);
    }

    /** The spool a wire winds onto, or empty if the stack is not a wire. */
    private ItemStack spoolForWire(ItemStack stack) {
        if (stack.is(TFMGTags.TFMGItemTags.WIRES_COPPER.tag))
            return TFMGItems.COPPER_SPOOL.asStack();
        if (stack.is(TFMGTags.TFMGItemTags.WIRES_ALUMINUM.tag))
            return TFMGItems.ALUMINUM_SPOOL.asStack();
        if (stack.is(TFMGTags.TFMGItemTags.WIRES_CONSTANTAN.tag))
            return TFMGItems.CONSTANTAN_SPOOL.asStack();
        return ItemStack.EMPTY;
    }

    /**
     * Wind wire from the workpiece slot onto the mounted spool, one turn per
     * tick. A wire is consumed UP FRONT for each 125-turn tranche (the
     * crafting recipe binds 8 wires per 1000-turn spool), so pulling the wire
     * stack back out never yields free turns. Returns true when the slot
     * holds a wire, whether or not a turn was wound, so the recipe paths
     * below never see wire items.
     */
    private boolean windSpool(ItemStack wire) {
        ItemStack spoolType = spoolForWire(wire);
        boolean hasWire = !spoolType.isEmpty();
        // Turns already paid for keep winding after the slot runs dry. Winding
        // used to need wire in the slot to advance at all, so the last wire of
        // a stack - or a single wire inserted on its own - delivered one turn
        // and stranded the other 124.
        boolean creditOnly = !hasWire && wire.isEmpty() && wireTurnsPending > 0;
        if (!hasWire && !creditOnly)
            return false;

        boolean emptyMounted = spool.is(TFMGItems.EMPTY_SPOOL.get());

        if (creditOnly) {
            // The credit belongs to the spool its wire was spent on. An empty
            // or missing spool cannot say which metal that was, so it lapses
            // rather than winding the wrong one.
            if (emptyMounted || spool.isEmpty() || !(spool.getItem() instanceof SpoolItem)
                    || (wireCreditSpool != null && !spool.is(wireCreditSpool))) {
                wireTurnsPending = 0;
                return false;
            }
        } else if (!emptyMounted && !spool.is(spoolType.getItem())) {
            return true;
        }

        int turns = emptyMounted ? 0 : spool.getOrDefault(TFMGDataComponents.SPOOL_AMOUNT, 0);
        // Full spools hold 1000 turns (see SpoolItem's durability bar). Credit
        // that outlives a full spool waits for the next one instead of blocking
        // the recipes below.
        if (turns >= 1000)
            return !creditOnly;

        if (wireTurnsPending <= 0) {
            wire.shrink(1);
            inventory.setStackInSlot(0, wire.isEmpty() ? ItemStack.EMPTY : wire);
            wireTurnsPending = TURNS_PER_WIRE;
            wireCreditSpool = spoolType.getItem();
        }
        if (emptyMounted)
            spool = spoolType;
        spool.set(TFMGDataComponents.SPOOL_AMOUNT, Math.min(1000, turns + 1));
        wireTurnsPending--;
        setChanged();
        throttledSync.request(this);
        return true;
    }

    /** Promote a depleted SpoolItem to an empty_spool. */
    private void convertEmptyIfDrained() {
        if (!spool.has(TFMGDataComponents.SPOOL_AMOUNT))
            return;
        if (spool.getOrDefault(TFMGDataComponents.SPOOL_AMOUNT, 0) == 0
                && !spool.is(TFMGItems.EMPTY_SPOOL.get())
                && spool.getItem() instanceof SpoolItem) {
            spool = TFMGItems.EMPTY_SPOOL.asStack();
        }
    }

   //@Override
   //public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {

   //    if (cap == ForgeCapabilities.ITEM_HANDLER)
   //        return itemCapability.cast();

   //    return super.getCapability(cap, side);
   //}

    public void manageRotation() {
        float targetSpeed = (float) Math.min(Math.abs(getSpeed() * 1.5), 30);
        spoolSpeed.updateChaseTarget(targetSpeed);
        spoolSpeed.tickChaser();

    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries , clientPacket);
        compound.put("Inventory", inventory.serializeNBT(registries));

        compound.put("Spool", spool.saveOptional(registries));
        compound.putInt("AmountWinded", amountWinded);
        compound.putInt("WireTurnsPending", wireTurnsPending);
        if (wireCreditSpool != null)
            compound.putString("WireCreditSpool", BuiltInRegistries.ITEM.getKey(wireCreditSpool).toString());
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);
        inventory.deserializeNBT(registries,compound.getCompound("Inventory"));

  
        // saveOptional writes an empty spool as {}, which parse() rejects with
        // a logged error and then leaves the previous spool in place, so a
        // client kept rendering a spool that had been taken out.
        if (compound.contains("Spool"))
            spool = ItemStack.parseOptional(registries, compound.getCompound("Spool"));
        amountWinded = compound.getInt("AmountWinded");
        wireTurnsPending = compound.getInt("WireTurnsPending");
        wireCreditSpool = compound.contains("WireCreditSpool")
                ? BuiltInRegistries.ITEM.get(ResourceLocation.parse(compound.getString("WireCreditSpool")))
                : null;
        if (clientPacket)
            spoolSpeed.chase(getGeneratedSpeed(), 1 / 16f, LerpedFloat.Chaser.EXP);
    }

    public static class WindingMachineValueBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 4, 16.05);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction == state.getValue(HORIZONTAL_FACING);
        }
    }


}
