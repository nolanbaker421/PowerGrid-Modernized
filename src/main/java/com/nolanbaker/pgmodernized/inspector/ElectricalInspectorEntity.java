package com.nolanbaker.pgmodernized.inspector;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.UUID;

/**
 * The electrical inspector. He turns up while you are wiring, walks to whatever you were working
 * on, looks it over with a long "Hmmmm", then asks for your electrical license. Have the card in
 * your inventory and he nods and leaves. Have none and he stabs you, unless you hand him
 * {@link #BRIBE} emeralds first. Spawned by {@link Inspections}; a spawn egg gives an idle one.
 */
public class ElectricalInspectorEntity extends PathfinderMob {
    public static final ResourceKey<DamageType> STAB = ResourceKey.create(Registries.DAMAGE_TYPE, PowerGridModernized.asResource("inspector"));
    /** Emeralds that make him forget he ever asked. */
    public static final int BRIBE = 8;
    /** Ticks he spends looking at the work, then waiting for the card. */
    private static final int LOOK_TICKS = 80, ASK_TICKS = 120, STAB_TICKS = 400, LEAVE_TICKS = 160;
    /** After this long without a verdict he gives up and goes home. */
    private static final int PATIENCE = 4800;

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ElectricalInspectorEntity.class, EntityDataSerializers.INT);

    public enum Phase { IDLE, ARRIVE, INSPECT, ASK, STAB, LEAVE }

    @Nullable
    private UUID subject;
    @Nullable
    private BlockPos work;
    private int timer;
    private int patience;

    public ElectricalInspectorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, Phase.IDLE.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Sends him after a player, to look at the block they were working on if there is one. */
    public void assign(ServerPlayer player, @Nullable BlockPos work) {
        subject = player.getUUID();
        this.work = work;
        timer = 0;
        patience = 0;
        setPhase(Phase.ARRIVE);
    }

    public Phase phase() {
        return Phase.values()[entityData.get(PHASE)];
    }

    private void setPhase(Phase phase) {
        entityData.set(PHASE, phase.ordinal());
    }

    @Nullable
    public UUID subject() {
        return subject;
    }

    public static boolean hasLicense(Player player) {
        return player.getInventory().contains(stack -> stack.is(ModItems.ELECTRICAL_LICENSE.get()));
    }

    // ---- the visit ----

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if(subject == null || phase() == Phase.IDLE)
            return;
        var player = level().getPlayerByUUID(subject);
        if(!(player instanceof ServerPlayer target) || !target.isAlive() || target.level() != level() || target.isCreative() || target.isSpectator()) {
            discard();
            return;
        }
        if(++patience > PATIENCE && phase() != Phase.LEAVE) {
            say(target, "message.inspector.gives_up");
            leave();
            return;
        }
        double distance = distanceTo(target);
        switch(phase()) {
            case ARRIVE -> {
                Vec3 goal = work != null ? work.getCenter() : target.position();
                if(tickCount % 10 == 0)
                    getNavigation().moveTo(goal.x, goal.y, goal.z, 0.75);
                boolean there = work != null ? blockPosition().distSqr(work) <= 9 || (distance < 3 && getNavigation().isDone()) : distance < 3;
                if(there) {
                    getNavigation().stop();
                    setPhase(Phase.INSPECT);
                    timer = LOOK_TICKS;
                    say(target, "message.inspector.hmm");
                    playSound(SoundEvents.VILLAGER_AMBIENT, 1f, 0.7f);
                }
            }
            case INSPECT -> {
                if(work != null)
                    getLookControl().setLookAt(work.getX() + 0.5, work.getY() + 0.5, work.getZ() + 0.5);
                if(--timer <= 0) {
                    setPhase(Phase.ASK);
                    timer = ASK_TICKS;
                    say(target, "message.inspector.ask", BRIBE);
                    playSound(SoundEvents.VILLAGER_TRADE, 1f, 1f);
                }
            }
            case ASK -> {
                getLookControl().setLookAt(target, 30f, 30f);
                if(distance > 4) {
                    if(tickCount % 10 == 0)
                        getNavigation().moveTo(target, 0.75);
                } else {
                    getNavigation().stop();
                    if(--timer <= 0)
                        verdict(target);
                }
            }
            case STAB -> {
                if(hasLicense(target)) {
                    say(target, "message.inspector.relents");
                    leave();
                } else if(--timer <= 0 || distance > 32) {
                    say(target, "message.inspector.done");
                    leave();
                }
            }
            case LEAVE -> {
                setTarget(null);
                if(--timer <= 0)
                    discard();
            }
            default -> {}
        }
    }

    private void verdict(ServerPlayer target) {
        if(hasLicense(target)) {
            say(target, "message.inspector.pass");
            playSound(SoundEvents.VILLAGER_YES, 1f, 1f);
            leave();
        } else {
            say(target, "message.inspector.fail");
            playSound(SoundEvents.VILLAGER_NO, 1f, 0.8f);
            setPhase(Phase.STAB);
            timer = STAB_TICKS;
            setTarget(target);
        }
    }

    private void leave() {
        setTarget(null);
        setPhase(Phase.LEAVE);
        timer = LEAVE_TICKS;
        var player = subject == null ? null : level().getPlayerByUUID(subject);
        Vec3 away = player == null ? null : DefaultRandomPos.getPosAway(this, 12, 6, player.position());
        if(away != null)
            getNavigation().moveTo(away.x, away.y, away.z, 0.7);
    }

    private void say(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Lang.builder().translate(key, args).style(ChatFormatting.YELLOW).component(), false);
    }

    // ---- the bribe, or showing the card ----

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        boolean mine = subject != null && subject.equals(player.getUUID()) && (phase() == Phase.ASK || phase() == Phase.STAB);
        if(!mine)
            return super.mobInteract(player, hand);
        if(stack.is(ModItems.ELECTRICAL_LICENSE.get())) {
            if(player instanceof ServerPlayer target) {
                say(target, "message.inspector.pass");
                playSound(SoundEvents.VILLAGER_YES, 1f, 1f);
                leave();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if(stack.is(Items.EMERALD)) {
            if(stack.getCount() < BRIBE) {
                if(player instanceof ServerPlayer target)
                    target.displayClientMessage(Lang.builder().translate("message.inspector.not_enough", BRIBE).style(ChatFormatting.RED).component(), true);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if(player instanceof ServerPlayer target) {
                if(!player.isCreative())
                    stack.shrink(BRIBE);
                say(target, "message.inspector.bribed");
                playSound(SoundEvents.VILLAGER_CELEBRATE, 1f, 1f);
                leave();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    // ---- the stab ----

    @Override
    public boolean doHurtTarget(Entity target) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource source = damageSources().source(STAB, this);
        boolean hit = target.hurt(source, damage);
        if(hit) {
            swing(InteractionHand.MAIN_HAND);
            playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1f);
        }
        return hit;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if(subject != null)
            tag.putUUID("Subject", subject);
        if(work != null)
            tag.put("Work", NbtUtils.writeBlockPos(work));
        tag.putInt("Phase", phase().ordinal());
        tag.putInt("Timer", timer);
        tag.putInt("Patience", patience);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        subject = tag.hasUUID("Subject") ? tag.getUUID("Subject") : null;
        work = NbtUtils.readBlockPos(tag, "Work").orElse(null);
        int phase = tag.getInt("Phase");
        setPhase(phase >= 0 && phase < Phase.values().length ? Phase.values()[phase] : Phase.IDLE);
        timer = tag.getInt("Timer");
        patience = tag.getInt("Patience");
    }

    /** Emeralds also settle things when dropped at his feet; keeps the item off the ground. */
    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    public static boolean isEmeralds(ItemStack stack) {
        return stack.is(Items.EMERALD);
    }
}
