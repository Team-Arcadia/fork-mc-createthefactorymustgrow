package com.drmangotea.tfmg.content.machinery.misc.winding_machine;

import com.drmangotea.tfmg.registry.TFMGItems;
import com.drmangotea.tfmg.registry.TFMGTags;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Item capability for the Winding Machine that exposes BOTH logical inputs to
 * automation:
 * <ul>
 *   <li>slot 0 — the recipe item (resistor / coil / sequenced-assembly item),
 *       backed by {@code be.inventory};</li>
 *   <li>slot 1 — the spool, backed by the separate {@code be.spool} field.</li>
 * </ul>
 * Before this, only {@code be.inventory} was exposed, so pipes/funnels could
 * insert the recipe item but the spool had nowhere to go — a spool pumped in
 * simply bounced off the single full slot. Each slot only accepts the matching
 * item type, so automation routes the spool to slot 1 and everything else to
 * slot 0 on its own.
 */
public class WindingMachineItemHandler implements IItemHandlerModifiable {

    private static final int SLOT_SPOOL = 1;

    private final WindingMachineBlockEntity be;

    public WindingMachineItemHandler(WindingMachineBlockEntity be) {
        this.be = be;
    }

    @Override
    public int getSlots() {
        return 2;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == SLOT_SPOOL ? be.spool : be.inventory.getStackInSlot(0);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot == SLOT_SPOOL) {
            be.spool = stack;
            be.onSpoolChanged();
        } else {
            be.inventory.setStackInSlot(0, stack);
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty())
            return true;
        boolean isSpool = stack.getItem() instanceof SpoolItem;
        return slot == SLOT_SPOOL ? isSpool : !isSpool;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty())
            return ItemStack.EMPTY;
        if (!isItemValid(slot, stack))
            return stack;

        if (slot == SLOT_SPOOL) {
            if (!be.spool.isEmpty())
                return stack; // single-item slot already full
            if (!simulate) {
                be.spool = stack.copyWithCount(1);
                be.onSpoolChanged();
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(1);
            return remainder;
        }

        return be.inventory.insertItem(0, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0)
            return ItemStack.EMPTY;

        if (slot == SLOT_SPOOL) {
            // Only a used-up spool leaves through automation. A hopper under
            // the machine used to pull a working spool out before it wound a
            // single turn.
            if (be.spool.isEmpty() || !be.spool.is(TFMGItems.EMPTY_SPOOL.get()))
                return ItemStack.EMPTY;
            ItemStack extracted = be.spool.copy();
            if (!simulate) {
                be.spool = ItemStack.EMPTY;
                be.onSpoolChanged();
            }
            return extracted;
        }

        // Wire in the workpiece slot is fuel for the spool, not a product.
        ItemStack work = be.inventory.getStackInSlot(0);
        if (work.is(TFMGTags.TFMGItemTags.WIRES_COPPER.tag) || work.is(TFMGTags.TFMGItemTags.WIRES_ALUMINUM.tag)
                || work.is(TFMGTags.TFMGItemTags.WIRES_CONSTANTAN.tag))
            return ItemStack.EMPTY;
        return be.inventory.extractItem(0, amount, simulate);
    }
}
