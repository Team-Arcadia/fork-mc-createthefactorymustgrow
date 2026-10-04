package com.drmangotea.tfmg.content.engines.engine_gearbox;

import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class EngineGearboxBlockEntity extends KineticBlockEntity implements IInspectable {
    public EngineGearboxBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void inspect(InspectionReport report) {
        net.minecraft.core.Direction facing = getBlockState().getValue(EngineGearboxBlock.HORIZONTAL_FACING);
        report.info("gearbox.faces",
                net.minecraft.network.chat.Component.translatable("tfmg.inspector.dir." + facing.getName()),
                net.minecraft.network.chat.Component.translatable("tfmg.inspector.dir." + facing.getClockWise().getName()),
                net.minecraft.network.chat.Component.translatable("tfmg.inspector.dir." + facing.getCounterClockWise().getName()));
    }


}
