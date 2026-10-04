package com.drmangotea.tfmg.content.electricity.generators;

import com.drmangotea.tfmg.config.TFMGConfigs;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.content.electricity.base.KineticElectricBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class GeneratorBlockEntity extends KineticElectricBlockEntity implements IInspectable {


    public GeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }


    @Override
    public int voltageGeneration() {
        return (int) Math.min(getMaxVoltage(), generation());
    }

    @Override
    public int getMaxVoltage() {
        return TFMGConfigs.common().machines.generatorMaxVoltage.get();
    }

    @Override
    public int powerGeneration() {
        return generation()*40;
    }

    @Override
    public void tick() {
        // super.tick() -> tickElectricity() already consumes updateNextTick;
        // the old re-check below it could never fire.
        super.tick();
    }


    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        updateNextTick();
    }

    @Override
    public void onNetworkChanged(int oldVoltage, int oldPower) {
        super.onNetworkChanged(oldVoltage, oldPower);
        updateStress();
        sendStuff();
    }

    public void updateStress(){
        if(getOrCreateNetwork() != null) {
            getOrCreateNetwork().remove(this);
            getOrCreateNetwork().add(this);
        }
    }

    @Override
    public void inspect(InspectionReport report) {
        float minSpeed = TFMGConfigs.common().machines.generatorMinSpeed.getF();
        float modifier = TFMGConfigs.common().machines.generatorModifier.getF();
        int speed = Math.abs((int) getSpeed());
        // A stopped generator is already reported by the generic rotation check.
        if (speed == 0)
            return;
        report.check(speed > minSpeed, "generator.speed_ok", "generator.too_slow", "generator.too_slow.fix", speed, (int) minSpeed);
        if (modifier > 0)
            report.info("generator.max_at", getMaxVoltage(), (int) Math.ceil(minSpeed + getMaxVoltage() / modifier));
    }

    public int generation() {
        float modifier = TFMGConfigs.common().machines.generatorModifier.getF();
        float maxSpeed = TFMGConfigs.common().machines.generatorMinSpeed.getF();
        return (int) Math.max(0,((Math.abs(getSpeed())-maxSpeed)* modifier));
    }




}
