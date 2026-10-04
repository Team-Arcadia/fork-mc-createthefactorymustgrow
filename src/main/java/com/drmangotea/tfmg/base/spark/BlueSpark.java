package com.drmangotea.tfmg.base.spark;

import com.drmangotea.tfmg.content.items.weapons.explosives.thermite_grenades.fire.BlueFireBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public class BlueSpark extends Spark{

    public BlueSpark(EntityType<? extends Spark> p_37391_, Level p_37392_) {
        super(p_37391_, p_37392_);
    }
    @Override
    public int getColor() {
        return 0xC4F2F2;
    }



    // Blue thermite fire, as the copper grenade's sparks set before the 1.21
    // port: this returned no fire at all, so copper grenades burnt nothing.
    @Override
    public Optional<BlockState> getFireState(BlockPos pos) {
        return Optional.of(BlueFireBlock.getState(this.level(), pos));
    }

    @Override
    public float[] getCustomParticleTrail() {
        return new float[]{4.1f, 60.2f, 100.3f};
    }
}
