package com.drmangotea.tfmg.content.machinery.vat.base;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

/**
 * The item face the vat shows to funnels, hoppers and pipes: ingredients can
 * only go in, products can only come out. The plain combined wrapper let a
 * funnel meant to collect products pull the ingredients first (extraction
 * scans from slot 0), and let insertion spill into output slots nothing reads.
 * The fluid side already works this way through forbidExtraction and
 * forbidInsertion on its two tank behaviours.
 *
 * @author vyrriox
 */
public class VatItemHandler extends CombinedInvWrapper {

    private final int inputSlots;

    public VatItemHandler(IItemHandlerModifiable input, IItemHandlerModifiable output) {
        super(input, output);
        this.inputSlots = input.getSlots();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot >= inputSlots)
            return stack;
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < inputSlots)
            return ItemStack.EMPTY;
        return super.extractItem(slot, amount, simulate);
    }
}
