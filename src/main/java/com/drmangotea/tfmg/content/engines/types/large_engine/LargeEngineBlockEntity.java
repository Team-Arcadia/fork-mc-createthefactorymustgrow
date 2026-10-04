package com.drmangotea.tfmg.content.engines.types.large_engine;


import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.base.lang.TFMGLang;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.config.TFMGConfigs;
import com.drmangotea.tfmg.content.engines.base.AbstractEngineBlockEntity;
import com.drmangotea.tfmg.content.engines.base.EngineFluidTank;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGSoundEvents;
import com.drmangotea.tfmg.registry.TFMGTags;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;


import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.List;

public class LargeEngineBlockEntity extends AbstractEngineBlockEntity implements com.drmangotea.tfmg.content.items.inspector.IInspectable {


    public WeakReference<PoweredShaftBlockEntity> target;


    public EngineFluidTank airTank;

    public IFluidHandler fluidCapabilityy;


    public LargeEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        target = new WeakReference<>(null);
        exhaustTank = new EngineFluidTank(2000, true, false, f->tankUpdated(f,false));
        fuelTank = new EngineFluidTank(2000, false, true, f->tankUpdated(f,true), TFMGTags.TFMGFluidTags.AIR.tag);
        airTank = new EngineFluidTank(1000, false, true, TFMGTags.TFMGFluidTags.AIR.tag, f->tankUpdated(f,true));
        fluidCapabilityy = new CombinedTankWrapper(exhaustTank,fuelTank,airTank);
    }

    @Override
    public void tankUpdated(FluidStack stack, boolean fuelTank) {
        super.tankUpdated(stack, fuelTank);
        sendStuff();
    }

    @Override
    public void refreshCapability() {}

    @Override
    public List<TagKey<Fluid>> getSupportedFuels() {
        return List.of(TFMGTags.TFMGFluidTags.DIESEL.tag, TFMGTags.TFMGFluidTags.KEROSENE.tag, TFMGTags.TFMGFluidTags.NAPHTHA.tag, TFMGTags.TFMGFluidTags.FURNACE_GAS.tag);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this));
    }


    public boolean isSimpleEngine(){
        return TFMGBlocks.SIMPLE_LARGE_ENGINE.has(getBlockState());
    }

    @Override
    public IFluidHandler handlerForCapability() {
        return new CombinedTankWrapper(fuelTank, exhaustTank, airTank);
    }

    @Override
    public void manageFuelAndExhaust() {
        super.manageFuelAndExhaust();

        if (fuelConsumptionTimer > 2) {
            airTank.forceDrain(150, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        // The engine only turned a shaft into a powered shaft when the engine
        // itself was placed. A shaft placed afterwards, as a blueprint or any
        // bottom-up build does, stayed a plain shaft and the engine never ran:
        // Create's own shaft placement only knows the steam engine.
        if (level == null || level.isClientSide || getShaft() != null)
            return;
        BlockState state = getBlockState();
        BlockPos shaftPos = LargeEngineBlock.getShaftPos(state, worldPosition);
        BlockState shaftState = level.getBlockState(shaftPos);
        if (com.simibubi.create.AllBlocks.SHAFT.has(shaftState) && LargeEngineBlock.isShaftValid(state, shaftState))
            level.setBlock(shaftPos, com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlock.getEquivalent(shaftState), 3);
    }

    @Override
    public void tick() {
        super.tick();

        PoweredShaftBlockEntity shaft = getShaft();

        if (shaft == null && !level.isClientSide())
            return;

        BlockState blockState = getBlockState();
        if (!TFMGBlocks.LARGE_ENGINE.has(blockState) && !TFMGBlocks.SIMPLE_LARGE_ENGINE.has(blockState))
            return;

        if(level.isClientSide)
            makeSound();


        if (!level.isClientSide)
            if (getShaft() != null)
                engineProcess();

    }

    @Override
    public float efficiencyModifier() {
        return 0.5f;
    }

    @Override
    public float speedModifier() {
        return 1;
    }

    @Override
    public float torqueModifier() {
        return 1;
    }

    
    @OnlyIn(Dist.CLIENT)
    private void makeSound() {
        Float targetAngle = getTargetAngle();
        PoweredShaftBlockEntity ste = target.get();
        if (ste == null)
            return;
        if(getShaft().getSpeed()==0)
            return;
        if(fuelTank.isEmpty()||airTank.isEmpty()||exhaustTank.getSpace() == 0)
            return;
        //if (engineStrength == 0)
        //	return;
        PoweredShaftBlockEntity shaft = getShaft();


        if (targetAngle == null)
            return;

        float angle = AngleHelper.deg(targetAngle);
        angle += (angle < 0) ? -180 + 75 : 360 - 75;
        angle %= 360;


        if (shaft == null || shaft.getSpeed() == 0)
            return;

        if (angle >= 0 && !(prevAngle > 180 && angle < 180)) {
            prevAngle = angle;
            return;
        }

        if (angle < 0 && !(prevAngle < -180 && angle > -180)) {
            prevAngle = angle;
            return;
        }

        TFMGSoundEvents.DIESEL_ENGINE.playAt(level, worldPosition, 0.4f * TFMGConfigs.common().machines.engineLoudness.getF(), 1f, false);

        prevAngle = angle;
    }



    @Override
    public boolean canWork() {

        if (airTank.isEmpty())
            return false;


        return super.canWork();
    }

    private float lastShaftStress = Float.NaN;

    private void engineProcess() {
        PoweredShaftBlockEntity shaft = getShaft();


        if (!canWork()) {
            shaft.update(worldPosition, 0, 0);
            lastShaftStress = 0;
            return;
        }

        boolean isFuelValid = false;
        for(TagKey<Fluid> tag : getSupportedFuels()){
            if(fuelTank.getFluid().getFluid().is(tag))
                isFuelValid = true;
        }


        float stress = 15 * getFuelType().getStress() * (isFuelValid ? 1 : 0);
        shaft.update(worldPosition, 2, stress);
        // Only broadcast when the applied stress changed — this used to fire
        // a full BE sync packet and mark the chunk dirty 20x/s per engine.
        if (stress != lastShaftStress) {
            lastShaftStress = stress;
            sendData();
            setChanged();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

        // The large engine used to show nothing but its tanks, and nothing at
        // all without a shaft; each reason it can sit idle now has a line.
        TFMGLang.text("").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if (getShaft() == null)
            TFMGTexts.problem("large_engine.no_shaft").forGoggles(tooltip);
        if (airTank.isEmpty())
            TFMGTexts.problem("large_engine.no_air").forGoggles(tooltip);
        if (fuelTank.isEmpty()) {
            TFMGTexts.problem("engine.no_fuel").forGoggles(tooltip);
        } else {
            boolean supported = false;
            for (TagKey<Fluid> tag : getSupportedFuels())
                supported |= fuelTank.getFluid().getFluid().is(tag);
            if (!supported)
                TFMGTexts.problem("large_engine.wrong_fuel").forGoggles(tooltip);
        }
        if (exhaustTank.getSpace() <= 0)
            TFMGTexts.problem("engine.exhaust_full").forGoggles(tooltip);

        TFMGUtils.createFluidTooltip(this,tooltip);

        return true;
    }

    // Never driven by a shaft: skip the inspector's generic "not turning" check.
    @Override
    public boolean wantsRotationCheck() {
        return false;
    }

    // Takes no electricity and makes none; it is electric only by inheritance.
    @Override
    public boolean wantsElectricCheck() {
        return false;
    }

    @Override
    public void inspect(com.drmangotea.tfmg.content.items.inspector.InspectionReport report) {
        // The engine block itself never turns; it drives the shaft in front.
        report.info("large_engine.drives_shaft");
        Direction facing = LargeEngineBlock.getFacing(getBlockState());
        BlockPos shaftPos = worldPosition.relative(facing, 2);
        if (!level.isLoaded(shaftPos)) {
            report.info("large_engine.shaft_unloaded", coords(shaftPos));
        } else if (level.getBlockEntity(shaftPos) instanceof PoweredShaftBlockEntity ps && ps.canBePoweredBy(worldPosition)) {
            report.ok("large_engine.shaft_ok", coords(shaftPos));
        } else {
            BlockState shaftState = level.getBlockState(shaftPos);
            if ((com.simibubi.create.AllBlocks.SHAFT.has(shaftState) || com.simibubi.create.AllBlocks.POWERED_SHAFT.has(shaftState))
                    && !LargeEngineBlock.isShaftValid(getBlockState(), shaftState)) {
                report.problem("large_engine.shaft_axis", coords(shaftPos));
                report.fix("large_engine.shaft_axis.fix");
            } else {
                report.problem("large_engine.no_shaft", coords(shaftPos));
                report.fix("large_engine.no_shaft.fix", coords(shaftPos));
            }
        }

        if (airTank.isEmpty()) {
            report.problem("large_engine.no_air");
            report.fix("large_engine.no_air.fix");
        } else {
            report.ok("large_engine.air_ok", airTank.getFluidAmount(), airTank.getCapacity());
        }

        if (fuelTank.isEmpty()) {
            report.problem("engine.no_fuel");
            report.fix("engine.no_fuel.fix", fuelList(getSupportedFuels()));
        } else if (isFuelSupported()) {
            report.ok("engine.fuel_ok", fuelTank.getFluid().getHoverName(), fuelTank.getFluidAmount());
        } else {
            report.problem("engine.wrong_fuel", fuelTank.getFluid().getHoverName(), getBlockState().getBlock().getName());
            report.fix("engine.wrong_fuel.fix", fuelList(getSupportedFuels()));
        }

        if (exhaustTank.getSpace() <= 0) {
            report.problem("engine.exhaust_full");
            report.fix("engine.exhaust_full.fix");
        }
    }

    @Override
    public void remove() {
        PoweredShaftBlockEntity shaft = getShaft();
        if (shaft != null)
            shaft.remove(worldPosition);
        super.remove();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().inflate(2);
    }

    public PoweredShaftBlockEntity getShaft() {
        PoweredShaftBlockEntity shaft = target.get();
        if (shaft == null || shaft.isRemoved() || !shaft.canBePoweredBy(worldPosition)) {
            if (shaft != null)
                target = new WeakReference<>(null);
            Direction facing = LargeEngineBlock.getFacing(getBlockState());
            BlockEntity anyShaftAt = level.getBlockEntity(worldPosition.relative(facing, 2));
            if (anyShaftAt instanceof PoweredShaftBlockEntity ps && ps.canBePoweredBy(worldPosition))
                target = new WeakReference<>(shaft = ps);
        }
        return shaft;
    }


    float prevAngle = 0;


    @Nullable
    @OnlyIn(Dist.CLIENT)
    public Float getTargetAngle() {
        float angle = 0;
        BlockState blockState = getBlockState();
        if (!TFMGBlocks.LARGE_ENGINE.has(blockState)&&!TFMGBlocks.SIMPLE_LARGE_ENGINE.has(blockState))
            return null;

        Direction facing = SteamEngineBlock.getFacing(blockState);
        PoweredShaftBlockEntity shaft = getShaft();
        Axis facingAxis = facing.getAxis();
        Axis axis = Axis.Y;

        if (shaft == null)
            return null;

        axis = KineticBlockEntityRenderer.getRotationAxisOf(shaft);
        angle = KineticBlockEntityRenderer.getAngleForBe(shaft, shaft.getBlockPos(), axis);

        if (axis == facingAxis)
            return null;
        if (axis.isHorizontal() && (facingAxis == Axis.X ^ facing.getAxisDirection() == AxisDirection.POSITIVE))
            angle *= -1;
        if (axis == Axis.X && facing == Direction.DOWN)
            angle *= -1;
        return angle;
    }


    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put("Air", airTank.writeToNBT(registries,new CompoundTag()));
        super.write(compound,registries , clientPacket);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                TFMGBlockEntities.LARGE_ENGINE.get(),
                (be, context) -> be.fluidCapabilityy
        );
    }
    @Override
    public int getFuelConsumption() {
        PoweredShaftBlockEntity shaft = getShaft();
        if (shaft == null)
            return 0;
        float speed = Math.abs(shaft.getGeneratedSpeed());
        if (speed <= 0)
            return 0;
        if (isSimpleEngine())
            return Math.max(1, (int) (speed / 10f));
        return Math.max(1, (int) (speed / 40f));
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        airTank.readFromNBT(registries,compound.getCompound("Air"));


        super.read(compound,registries , clientPacket);
    }


    @Override
    public void invalidate() {
        super.invalidate();

        invalidateCapabilities();
    }



    @Override
    public void notifyUpdate() {
        super.notifyUpdate();
    }


}