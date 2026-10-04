package com.drmangotea.tfmg.content.items.weapons;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

public class LeadSwordItem extends SwordItem {
    public LeadSwordItem(Tier pTier, Properties pProperties) {
        super(pTier,pProperties);

    }

    // Since 1.21 the wear of a hit belongs in postHurtEnemy: also wearing the
    // blade here (from the target's hand slot) cost 3 durability per hit
    // instead of the lead sword's 2.
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(2, attacker, EquipmentSlot.MAINHAND);
    }

    public boolean hurtEnemy(ItemStack pStack, LivingEntity pTarget, LivingEntity pAttacker) {
        MobEffectInstance poison = pTarget.getEffect(MobEffects.POISON);

        if(poison!=null) {
            pTarget.addEffect(new MobEffectInstance(MobEffects.POISON, 100 + poison.getDuration()));
        }
        pTarget.addEffect(new MobEffectInstance(MobEffects.POISON,100));
        return true;
    }
}
