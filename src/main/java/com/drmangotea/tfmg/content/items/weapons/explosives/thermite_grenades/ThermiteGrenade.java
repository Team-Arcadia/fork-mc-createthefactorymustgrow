package com.drmangotea.tfmg.content.items.weapons.explosives.thermite_grenades;

import com.drmangotea.tfmg.base.spark.Spark;
import com.drmangotea.tfmg.registry.TFMGEntityTypes;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.simibubi.create.Create;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class ThermiteGrenade extends ThrowableItemProjectile {
    public final ChemicalColor flameColor;

    public ThermiteGrenade(EntityType<? extends ThermiteGrenade> p_37391_, Level p_37392_) {
        super(p_37391_, p_37392_);
        // This constructor builds every grenade loaded from a save (and every
        // client copy). The colour is not saved, so it has to come from the
        // entity type: it used to be blue for all three, and a thermite or
        // zinc grenade reloaded in flight burst into blue fire.
        this.flameColor = colorOf(p_37391_);
    }

    public static ChemicalColor colorOf(EntityType<?> type) {
        if (type == TFMGEntityTypes.ZINC_GRENADE.get())
            return ChemicalColor.GREEN;
        if (type == TFMGEntityTypes.COPPER_GRENADE.get())
            return ChemicalColor.BLUE;
        return ChemicalColor.BASE;
    }

    public ThermiteGrenade(Level p_37399_, LivingEntity p_37400_, ChemicalColor color,EntityType grenade) {
        super(grenade, p_37400_, p_37399_);
        this.flameColor = color;
    }

    protected Item getDefaultItem() {
        return TFMGItems.THERMITE_GRENADE.get();
    }

    private ParticleOptions getParticle() {

        return ParticleTypes.FLAME;
    }

    public void handleEntityEvent(byte p_37402_) {
        if (p_37402_ == 3) {
            ParticleOptions particleoptions = this.getParticle();

            for(int i = 0; i < 8; ++i) {
                this.level().addParticle(particleoptions, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        }

    }

    protected void onHitEntity(EntityHitResult p_37404_) {
        super.onHitEntity(p_37404_);
        Entity entity = p_37404_.getEntity();

    }

    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);

        if (this.level().isClientSide)
            return;

            this.level().broadcastEntityEvent(this, (byte) 3);

            for (int i=0; i<20;i++){
                float x= Create.RANDOM.nextFloat(360);
                float y= Create.RANDOM.nextFloat(360);
                float z= Create.RANDOM.nextFloat(360);

                Spark spark;
                if (flameColor == ChemicalColor.GREEN) {
                    spark = TFMGEntityTypes.GREEN_SPARK.create(level());
                } else if (flameColor == ChemicalColor.BLUE) {
                    spark = TFMGEntityTypes.BLUE_SPARK.create(level());
                } else {
                    spark = TFMGEntityTypes.SPARK.create(level());
                }
                // EntityType.create is @Nullable (disabled entity types).
                if (spark == null)
                    break;
                spark.moveTo(this.getX(), this.getY()+1, this.getZ());
                spark.shootFromRotation( this,x,y,z,0.2f,1);
                this.level().addFreshEntity(spark);







        }


            this.level().explode(this, this.getX(), this.getY(0.0625D), this.getZ(), 2.0F, Level.ExplosionInteraction.NONE);
            this.discard();


    }

    @SuppressWarnings("unchecked")
    public static EntityType.Builder<?> build(EntityType.Builder<?> builder) {
        EntityType.Builder<ThermiteGrenade> entityBuilder = (EntityType.Builder<ThermiteGrenade>) builder;
        return entityBuilder.sized(.25f, .25f);
    }

    public enum ChemicalColor {
        BASE,
        GREEN,
        BLUE
    }

}