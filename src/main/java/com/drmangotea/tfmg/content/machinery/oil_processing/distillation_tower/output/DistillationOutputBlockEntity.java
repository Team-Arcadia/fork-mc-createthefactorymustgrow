package com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.output;

import com.drmangotea.tfmg.base.ThrottledSync;
import com.drmangotea.tfmg.base.capability.DrainOnlyFluidHandler;
import com.drmangotea.tfmg.base.TFMGIcons;
import com.drmangotea.tfmg.base.lang.TFMGLang;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.controller.DistillationControllerBlockEntity;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.gui.AllIcons;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;
/**
    this block doesn't do anything, it only holds fluids create in the distillation tower
 */
public class DistillationOutputBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, IInspectable {

    // Per-tick fluid and progress changes sync at most every few ticks.
    private final ThrottledSync throttledSync = new ThrottledSync();


    protected IFluidHandler fluidCapability;

    public ScrollOptionBehaviour<DistillationOutputMode> mode;

    public final FluidTank tank = new SmartFluidTank(8000,this::onFluidStackChanged);
    public DistillationOutputBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        // Pipes only take fractions out. An output used to accept anything
        // pumped in, and a foreign fluid there refused the tower's fraction:
        // kept, the stage stalled; voided, the fraction was lost.
        fluidCapability = new DrainOnlyFluidHandler(tank);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        mode = new ScrollOptionBehaviour<>(DistillationOutputMode.class,
                TFMGLang.translateDirect("distillation_output.when_tank_is_full"), this, new DistillationOutputValueBox());
        behaviours.add(mode);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide)
            throttledSync.tick(this);
    }

    protected void onFluidStackChanged(FluidStack newFluidStack) {
        if (!hasLevel())
            return;
        if (!level.isClientSide) {
            setChanged();
            throttledSync.request(this);
        }
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                TFMGBlockEntities.DISTILLATION_OUTPUT.get(),
                (be, context) -> be.fluidCapability
        );
    }
    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);

        tank.readFromNBT(registries,compound.getCompound("TankContent"));

    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries,clientPacket);
        compound.put("TankContent", tank.writeToNBT(registries,new CompoundTag()));

    }
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability);
    }

    @Override
    public void inspect(InspectionReport report) {
        if (level == null)
            return;
        boolean keep = mode.get() == DistillationOutputMode.KEEP_FLUID;
        report.info(keep ? "distillation_output.keep" : "distillation_output.void");
        // Stages sit on every second block above the controller, pipes between.
        for (int i = 1; i <= 11; i++) {
            BlockPos pos = getBlockPos().below(i);
            if (!level.isLoaded(pos))
                break;
            if (level.getBlockEntity(pos) instanceof DistillationControllerBlockEntity controller) {
                int stage = controller.getOutputs().indexOf(this);
                if (stage < 0)
                    break;
                report.info("distillation_output.stage", stage + 1, pos.getX(), pos.getY(), pos.getZ());
                controller.inspect(report);
                return;
            }
        }
        report.problem("distillation_output.unlinked");
        report.fix("distillation_output.unlinked.fix");
    }

    public static class DistillationOutputValueBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 16.05);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction.getAxis().isHorizontal();
        }
    }

    public enum DistillationOutputMode implements INamedIconOptions {
        KEEP_FLUID(TFMGIcons.DISTILLATION_OUTPUT_ICON_DO_NOT_VOID),
        VOID_WHEN_FULL(TFMGIcons.DISTILLATION_OUTPUT_ICON_VOID);

        final AllIcons icon;

        DistillationOutputMode(AllIcons icon){
            this.icon = icon;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return "distillation_output.mode."+ TFMGLang.asId(name());
        }
    }
}
