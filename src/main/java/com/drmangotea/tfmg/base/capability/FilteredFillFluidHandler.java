package com.drmangotea.tfmg.base.capability;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.function.Predicate;

/**
 * Lets pipes fill a machine's input tanks with the fluids it uses and nothing
 * else. Draining and the machine's own access to the wrapped tanks are left
 * as they are.
 *
 * @author vyrriox
 */
public record FilteredFillFluidHandler(IFluidHandler inner, Predicate<FluidStack> accepts) implements IFluidHandler {

    @Override
    public int getTanks() {
        return inner.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return inner.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return inner.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return accepts.test(stack) && inner.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !accepts.test(resource))
            return 0;
        return inner.fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return inner.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return inner.drain(maxDrain, action);
    }
}
