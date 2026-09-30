package com.nolanbaker.pgmodernized.inspector;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.UUID;

/**
 * The electrical inspector. He turns up while you are wiring (or notices you working near him),
 * walks to whatever you were working on, looks it over with a long "Hmmmm", then asks for your
 * electrical license. Drop the card on the ground for him: he picks it up, looks it over and tosses
 * it back. Produce nothing and he stabs you, unless the bribe changes hands first. He asks creative
 * players too, though {@link Inspections} never sends him after one.
 */
public class ElectricalInspectorEntity extends PathfinderMob {
    public static final ResourceKey<DamageType> STAB = ResourceKey.create(Registries.DAMAGE_TYPE, PowerGridModernized.asResource("inspector"));
    /** Ticks: looking at the work, waiting for the card, stabbing, walking off, and reading the card. */
    private static final int LOOK_TICKS = 80, ASK_TICKS = 240, STAB_TICKS = 400, LEAVE_TICKS = 160, READ_TICKS = 50, FETCH_TICKS = 120;
    /** After this long without a verdict he gives up and goes home. */
    private static final int PATIENCE = 4800;
    /** How far from him or from the player a dropped card is noticed. */
    private static final double CARD_RANGE = 5;

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ElectricalInspectorEntity.class, EntityDataSerializers.INT);

    public enum Phase { IDLE, ARRIVE, INSPECT, ASK, FETCH, READ, STAB, LEAVE }

    @Nullable
    private UUID subject;
    @Nullable
    private BlockPos work;
    private int timer;
    private int patience;
    /** The card he is walking to, and the phase to fall back to if it vanishes. */
    @Nullable
    private UUID card;
    private Phase before = Phase.ASK;
    private int beforeTimer;
    /** The card in his hand while he reads it. */
    private ItemStack held = ItemStack.EMPTY;

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

    public static int bribe() {
        return PgmConfig.INSPECTOR_BRIBE.get();
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
        card = null;
        setTarget(null);
        setPhase(Phase.ARRIVE);
    }

    /** Whether a player working nearby can pull him into a new visit. */
    public boolean available() {
        return phase() == Phase.IDLE || phase() == Phase.LEAVE;
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

    private boolean asking() {
        return phase() == Phase.ASK || phase() == Phase.STAB;
    }

    // ---- the visit ----

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if(subject == null || phase() == Phase.IDLE)
            return;
        var player = level().getPlayerByUUID(subject);
        if(!(player instanceof ServerPlayer target) || !target.isAlive() || target.level() != level() || target.isSpectator()) {
            returnCard(null);
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
                    say(target, "message.inspector.ask", bribe());
                    playSound(SoundEvents.VILLAGER_TRADE, 1f, 1f);
                }
            }
            case ASK -> {
                getLookControl().setLookAt(target, 30f, 30f);
                if(noticeCard(target))
                    return;
                if(distance > 4) {
                    if(tickCount % 10 == 0)
                        getNavigation().moveTo(target, 0.75);
                } else {
                    getNavigation().stop();
                    if(--timer <= 0)
                        verdict(target);
                }
            }
            case FETCH -> fetch(target);
            case READ -> {
                getLookControl().setLookAt(target, 30f, 30f);
                if(--timer <= 0) {
                    say(target, "message.inspector.pass");
                    playSound(SoundEvents.VILLAGER_YES, 1f, 1f);
                    returnCard(target);
                    leave();
                }
            }
            case STAB -> {
                if(noticeCard(target))
                    return;
                if(--timer <= 0 || distance > 32) {
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
        say(target, "message.inspector.fail");
        playSound(SoundEvents.VILLAGER_NO, 1f, 0.8f);
        setPhase(Phase.STAB);
        timer = STAB_TICKS;
        setTarget(target);
    }

    /** A license lying on the ground near either of us: he goes to pick it up. */
    private boolean noticeCard(ServerPlayer target) {
        if(tickCount % 5 != 0)
            return false;
        var dropped = findCard(target);
        if(dropped == null)
            return false;
        before = phase();
        beforeTimer = timer;
        card = dropped.getUUID();
        setTarget(null);
        setPhase(Phase.FETCH);
        timer = FETCH_TICKS;
        getNavigation().moveTo(dropped, 0.75);
        return true;
    }

    @Nullable
    private ItemEntity findCard(ServerPlayer target) {
        var box = getBoundingBox().inflate(CARD_RANGE).minmax(target.getBoundingBox().inflate(CARD_RANGE));
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for(var item : level().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(ModItems.ELECTRICAL_LICENSE.get()))) {
            double d = item.distanceToSqr(this);
            if(d < bestDistance) {
                best = item;
                bestDistance = d;
            }
        }
        return best;
    }

    private void fetch(ServerPlayer target) {
        Entity item = card == null || !(level() instanceof ServerLevel server) ? null : server.getEntity(card);
        if(!(item instanceof ItemEntity dropped) || !dropped.isAlive() || !dropped.getItem().is(ModItems.ELECTRICAL_LICENSE.get())) {
            // Picked back up, or gone: back to what he was doing.
            card = null;
            setPhase(before);
            timer = beforeTimer;
            if(before == Phase.STAB)
                setTarget(target);
            return;
        }
        getLookControl().setLookAt(dropped);
        if(tickCount % 10 == 0)
            getNavigation().moveTo(dropped, 0.75);
        boolean reach = distanceToSqr(dropped) < 2.5 || (--timer <= 0 && distanceToSqr(dropped) < 16);
        if(!reach)
            return;
        held = dropped.getItem().copy();
        dropped.discard();
        card = null;
        getNavigation().stop();
        playSound(SoundEvents.ITEM_PICKUP, 0.6f, 1f);
        say(target, "message.inspector.checking");
        setPhase(Phase.READ);
        timer = READ_TICKS;
    }

    /** Tosses the card he holds back to the player, or just drops it. */
    private void returnCard(@Nullable ServerPlayer target) {
        if(held.isEmpty() || level().isClientSide)
            return;
        var item = new ItemEntity(level(), getX(), getEyeY() - 0.3, getZ(), held);
        held = ItemStack.EMPTY;
        Vec3 push = target == null ? Vec3.ZERO : target.position().subtract(position()).normalize().scale(0.35);
        item.setDeltaMovement(push.x, 0.25, push.z);
        item.setPickUpDelay(10);
        item.setThrower(this);
        level().addFreshEntity(item);
        if(target != null)
            say(target, "message.inspector.returned");
    }

    private void leave() {
        setTarget(null);
        setPhase(Phase.LEAVE);
        timer = LEAVE_TICKS;
        card = null;
        var player = subject == null ? null : level().getPlayerByUUID(subject);
        Vec3 away = player == null ? null : DefaultRandomPos.getPosAway(this, 12, 6, player.position());
        if(away != null)
            getNavigation().moveTo(away.x, away.y, away.z, 0.7);
    }

    private void say(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Lang.builder().translate(key, args).style(ChatFormatting.YELLOW).component(), false);
    }

    // ---- handing him the card, or the bribe ----

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        boolean mine = subject != null && subject.equals(player.getUUID()) && asking();
        if(!mine)
            return super.mobInteract(player, hand);
        if(stack.is(ModItems.ELECTRICAL_LICENSE.get())) {
            // Handed over directly: he takes it, reads it, tosses it back like a dropped one.
            if(player instanceof ServerPlayer target) {
                held = stack.copyWithCount(1);
                stack.shrink(1);
                setTarget(null);
                card = null;
                playSound(SoundEvents.ITEM_PICKUP, 0.6f, 1f);
                say(target, "message.inspector.checking");
                setPhase(Phase.READ);
                timer = READ_TICKS;
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if(stack.is(Items.EMERALD)) {
            int price = bribe();
            if(stack.getCount() < price) {
                if(player instanceof ServerPlayer target)
                    target.displayClientMessage(Lang.builder().translate("message.inspector.not_enough", price).style(ChatFormatting.RED).component(), true);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if(player instanceof ServerPlayer target) {
                if(!player.isCreative())
                    stack.shrink(price);
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
    public void die(DamageSource source) {
        super.die(source);
        // Killed with a card in hand: it falls to the ground.
        returnCard(null);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
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
        if(!held.isEmpty())
            tag.put("Held", held.save(registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        subject = tag.hasUUID("Subject") ? tag.getUUID("Subject") : null;
        work = NbtUtils.readBlockPos(tag, "Work").orElse(null);
        int phase = tag.getInt("Phase");
        var loaded = phase >= 0 && phase < Phase.values().length ? Phase.values()[phase] : Phase.IDLE;
        // A fetch cannot survive a reload (the item's id is gone): resume by asking again.
        setPhase(loaded == Phase.FETCH ? Phase.ASK : loaded);
        timer = loaded == Phase.FETCH ? ASK_TICKS : tag.getInt("Timer");
        patience = tag.getInt("Patience");
        HolderLookup.Provider registries = registryAccess();
        held = tag.contains("Held") ? ItemStack.parseOptional(registries, tag.getCompound("Held")) : ItemStack.EMPTY;
    }
}
