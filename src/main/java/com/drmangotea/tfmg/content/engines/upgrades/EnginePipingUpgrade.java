package com.drmangotea.tfmg.content.engines.upgrades;


import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.engines.types.AbstractSmallEngineBlockEntity;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.Optional;

public class EnginePipingUpgrade extends EngineUpgrade {

    public Optional<FluidTankBlockEntity> tank = Optional.empty();


    public void findTank(AbstractSmallEngineBlockEntity be) {
        Level level = be.getLevel();

        for (Direction direction : Direction.values()) {
            BlockPos pos = be.getBlockPos().relative(direction);
            if (level.getBlockEntity(pos) instanceof FluidTankBlockEntity foundTank) {

                tank = Optional.of(foundTank);
                return;
            }
        }
        tank = Optional.empty();
    }

    @Override
    public void updateUpgrade(AbstractSmallEngineBlockEntity be) {
        findTank(be);
    }

    @Override
    public void lazyTickUpgrade(AbstractSmallEngineBlockEntity engine) {

        // Fluid transfer is server logic; the client copy desynced its tanks.
        if (engine.getLevel() == null || engine.getLevel().isClientSide)
            return;

        // Drop the cached tank when its block entity was removed or replaced
        // (chunk reload, break) — draining the orphaned instance voided or
        // duplicated fuel.
        if (tank.isPresent() && tank.get().isRemoved())
            tank = Optional.empty();

        if (tank.isPresent()) {

            AbstractSmallEngineBlockEntity controller = engine.getControllerBE();

            FluidTankBlockEntity tankBE = tank.get();
            // A multiblock tank keeps its fluid on its controller block.
            FluidTankBlockEntity tankController = tankBE.getControllerBE();
            if (tankController != null)
                tankBE = tankController;
            if(controller == null)
                return;
            if(controller.fuelTank == null)
                return;

            FluidStack available = tankBE.getTankInventory().drain(500, IFluidHandler.FluidAction.SIMULATE);
            if (available.isEmpty())
                return;
            // Ask the engine first and move only what it accepts. The old code
            // drained up to 500 mB and ignored the fill, voiding the fuel when
            // the engine held a different one, and capped the move by the
            // SOURCE tank's free space, so a full tank never fed anything.
            int amount = controller.fuelTank.fill(available, IFluidHandler.FluidAction.SIMULATE);
            if (amount <= 0)
                return;
            FluidStack moved = tankBE.getTankInventory().drain(amount, IFluidHandler.FluidAction.EXECUTE);
            controller.fuelTank.fill(moved, IFluidHandler.FluidAction.EXECUTE);

        } else findTank(engine);

    }

    @Override
    public Optional<? extends EngineUpgrade> createUpgrade() {
        return Optional.of(new EnginePipingUpgrade());
    }

    @Override
    public Item getItem() {
        return TFMGBlocks.INDUSTRIAL_PIPE.asItem();
    }
}
