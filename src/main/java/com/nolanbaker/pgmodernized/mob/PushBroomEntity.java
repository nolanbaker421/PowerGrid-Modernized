package com.nolanbaker.pgmodernized.mob;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The push broom. Every apprentice's first tool, and it has had enough. It wanders the night like
 * any other monster, hops at whoever it finds and sweeps them. Drops sticks, and now and then the
 * copper it swept off the shop floor.
 */
public class PushBroomEntity extends Monster {
    public PushBroomEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** It gets about by hopping: a little jump whenever it is on the ground and going somewhere. */
    @Override
    public void aiStep() {
        super.aiStep();
        if(!level().isClientSide && onGround() && getNavigation().isInProgress() && random.nextInt(8) == 0) {
            setDeltaMovement(getDeltaMovement().add(0, 0.28, 0));
            playSound(SoundEvents.WOOD_STEP, 0.6f, 0.8f + random.nextFloat() * 0.4f);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BRUSH_GENERIC;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8f;
    }
}
