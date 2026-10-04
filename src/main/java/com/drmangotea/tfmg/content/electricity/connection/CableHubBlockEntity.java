package com.drmangotea.tfmg.content.electricity.connection;

import com.drmangotea.tfmg.content.electricity.base.ElectricBlockEntity;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CableHubBlockEntity extends ElectricBlockEntity implements IInspectable {
    public CableHubBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public int getMaxCurrent() {
        return ((CableHubBlock)getBlockState().getBlock()).maxCurrent;
    }

    @Override
    public int getMaxVoltage() {
        return 5000;
    }

    @Override
    public boolean isCable() {
        return true;
    }

    /**
     * A hub has no resistance, so the generic current check reads 0 A; it
     * breaks on the highest current flowing through its network instead.
     */
    @Override
    public void inspect(InspectionReport report) {
        float current = getData().highestCurrent;
        int max = getMaxCurrent();
        String shown = String.format("%.1f", current);
        if (current > max) {
            report.problem("hub.over", shown, max);
            report.fix("hub.over.fix");
        } else if (current > max * 0.8f) {
            report.problem("hub.near", shown, max);
            report.fix("hub.over.fix");
        } else {
            report.ok("hub.current", shown, max);
        }
    }
}
