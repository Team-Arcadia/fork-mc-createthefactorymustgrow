package com.drmangotea.tfmg.mixin.accessor;

import com.simibubi.create.content.equipment.potatoCannon.PotatoProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the quad potato cannon clear the ammo recovery chance of the extra
 * projectiles of a split shot, as Create's own cannon does from inside its
 * package.
 *
 * @author vyrriox
 */
@Mixin(PotatoProjectileEntity.class)
public interface PotatoProjectileEntityAccessor {

    @Accessor("recoveryChance")
    void tfmg$setRecoveryChance(float chance);
}
