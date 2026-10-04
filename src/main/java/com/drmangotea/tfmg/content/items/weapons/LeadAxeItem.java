package com.drmangotea.tfmg.content.items.weapons;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;

public class LeadAxeItem extends AxeItem {
    public LeadAxeItem(Tier pTier, Properties pProperties) {
        super(pTier,pProperties);
    }
    // The axe's 2 durability per hit comes from DiggerItem#postHurtEnemy since
    // 1.21; also wearing it here cost 4 per hit.
    public boolean hurtEnemy(ItemStack pStack, LivingEntity pTarget, LivingEntity pAttacker) {
        MobEffectInstance poison = pTarget.getEffect(MobEffects.POISON);

        if(poison!=null) {
            pTarget.addEffect(new MobEffectInstance(MobEffects.POISON, 160 + poison.getDuration()));
        }
        pTarget.addEffect(new MobEffectInstance(MobEffects.POISON,160));
        return true;
    }
}
