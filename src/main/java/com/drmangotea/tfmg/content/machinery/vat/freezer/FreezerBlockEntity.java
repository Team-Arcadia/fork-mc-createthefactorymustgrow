package com.drmangotea.tfmg.content.machinery.vat.freezer;

import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.content.electricity.base.ElectricBlockEntity;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.content.machinery.vat.base.IVatMachine;
import com.drmangotea.tfmg.content.machinery.vat.base.VatBlock;
import com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class FreezerBlockEntity extends ElectricBlockEntity implements IVatMachine, IInspectable {



    public FreezerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public int getMaxVoltage() {
        return 20000;
    }

    @Override
    public int getMaxCurrent() {
        return 400;
    }

    @Override
    public boolean hasElectricitySlot(Direction direction) {
        return true;
    }

    public boolean isOperational(){
        return getCurrent()>3&&!data.notEnoughPower;
    }

    @Override
    public String getOperationId() {
        // Identity, not health: an unpowered freezer must still be listed in
        // the vat's attachments and satisfy the recipe's machine list —
        // canOperate() below is what gates actual operation. Returning ""
        // here made evaluate() skip the freezer entirely whenever it was
        // momentarily under-powered at scan time, so it never appeared in
        // the attachments and freeze recipes never matched.
        return "tfmg:freezing";
    }

    @Override
    public boolean canOperate(VatBlockEntity vat) {
        return isOperational();
    }

    @Override
    public PositionRequirement getPositionRequirement() {
        // ANY: a freezer counts whether it sits under the vat (where players
        // naturally place it, like a heat source) or on top. updateTemperature()
        // reads its heat delta from the position-validated machineMap, so either
        // placement cools the vat AND satisfies the recipe's machine list. The
        // old TOP requirement meant a freezer placed below cooled the vat but
        // was never counted as a machine, so freeze recipes never matched.
        return PositionRequirement.ANY;
    }


    @Override
    public boolean makeMultimeterTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

        super.makeMultimeterTooltip(tooltip, isPlayerSneaking);
        if (!isOperational())
            TFMGTexts.Multimeter.notEnoughCurrent(3).forGoggles(tooltip);

        return true;
    }


    @Override
    public float resistance() {
        return 75;
    }

    @Override
    public void inspect(InspectionReport report) {
        // Power shortage is reported by the generic electricity check.
        if (!data.notEnoughPower)
            report.check(getCurrent() > 3, "freezer.current_ok", "freezer.low_current", "freezer.low_current.fix",
                    String.format("%.1f", getCurrent()), 3);
        if (VatBlockEntity.appendVatReport(level, getBlockPos(), report, Direction.DOWN, Direction.UP) == null) {
            report.problem("freezer.no_vat");
            report.fix("freezer.no_vat.fix");
        }
    }



    @Override
    public void onNetworkChanged(int oldVoltage, int oldPower) {
        super.onNetworkChanged(oldVoltage, oldPower);
        VatBlock.updateVatState(getBlockState(), level, getBlockPos().relative(Direction.DOWN));
    }




    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(getBlockPos()).setMinY(getBlockPos().getY() - 2);
    }





}
