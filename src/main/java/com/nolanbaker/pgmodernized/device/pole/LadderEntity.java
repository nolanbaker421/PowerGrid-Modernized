package com.nolanbaker.pgmodernized.device.pole;

import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * An extension ladder stood on the ground and leaned on a pole or wall. Climbing is riding: a
 * right-click puts the player on the rungs at the clicked height, forward climbs, back descends,
 * back at the feet steps off, forward at the top steps onto whatever is above the support if
 * there is room, and sneaking lets go wherever you are. Riding keeps the server's anti-flying
 * check off the climber. Sneak-click or punch takes the ladder down; it falls on its own if the
 * ground under its feet or the block it leans on goes.
 */
public class LadderEntity extends Entity {
    public static final double MAX_HEIGHT = 15, MIN_HEIGHT = 2;
    /** Blocks along the rails per tick while climbing. */
    public static final double CLIMB_PER_TICK = 0.1;
    /** How far the climber stands out from the rungs, blocks. */
    public static final double STAND_OFF = 0.45;

    private static final EntityDataAccessor<Vector3f> FOOT = SynchedEntityData.defineId(LadderEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> TOP = SynchedEntityData.defineId(LadderEntity.class, EntityDataSerializers.VECTOR3);
    /** Index of the face the top leans on; its normal points out of the support toward the feet. */
    private static final EntityDataAccessor<Integer> FACE = SynchedEntityData.defineId(LadderEntity.class, EntityDataSerializers.INT);
    /** The climber's place along the rails, 0 at the feet to 1 at the top. */
    private static final EntityDataAccessor<Float> CLIMB = SynchedEntityData.defineId(LadderEntity.class, EntityDataSerializers.FLOAT);

    /** Parts cover one block of rails each; the ladder is clicked and hit through them. */
    public static final int MAX_PARTS = (int) MAX_HEIGHT + 1;

    private final LadderPart[] parts = new LadderPart[MAX_PARTS];
    private BlockPos support = BlockPos.ZERO;
    @Nullable
    private Vec3 dismount;

    public LadderEntity(EntityType<? extends LadderEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        // Built right after the parent so their ids follow its own, which is what setId gives them on the client.
        for(int i = 0; i < MAX_PARTS; ++i)
            parts[i] = new LadderPart(this);
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for(int i = 0; i < parts.length; ++i)
            parts[i].setId(id + i + 1);
    }

    /** Lays the parts along the rails, one per block of length; the rest sit idle at the feet. */
    private void layoutParts() {
        var foot = foot();
        double length = length();
        int n = length < 0.5 ? 0 : (int) Math.min(MAX_PARTS, Math.ceil(length));
        for(int i = 0; i < parts.length; ++i) {
            if(i < n) {
                var centre = rung((i + 0.5) / n);
                parts[i].set(new AABB(centre, centre).inflate(0.5), true);
            } else {
                parts[i].set(new AABB(foot, foot), false);
            }
        }
    }

    /** Stands a ladder with its feet at {@code foot} and its top at {@code top} on the given face of {@code support}. */
    public static LadderEntity place(EntityType<? extends LadderEntity> type, Level level, Vec3 foot, Vec3 top, Direction face, BlockPos support) {
        var ladder = new LadderEntity(type, level);
        ladder.setPos(foot);
        ladder.entityData.set(FOOT, foot.toVector3f());
        ladder.entityData.set(TOP, top.toVector3f());
        ladder.entityData.set(FACE, face.get3DDataValue());
        ladder.support = support;
        ladder.setBoundingBox(ladder.makeBoundingBox());
        ladder.layoutParts();
        return ladder;
    }

    // ---- geometry ----

    public Vec3 foot() {
        return new Vec3(entityData.get(FOOT));
    }

    public Vec3 top() {
        return new Vec3(entityData.get(TOP));
    }

    public Direction face() {
        return Direction.from3DDataValue(entityData.get(FACE));
    }

    /** Unit vector out of the leaned-on face: the side the climber stands on. */
    public Vec3 outward() {
        return Vec3.atLowerCornerOf(face().getNormal());
    }

    public double length() {
        return top().distanceTo(foot());
    }

    public float climb() {
        return entityData.get(CLIMB);
    }

    /** The point on the rungs at {@code t} along the rails. */
    public Vec3 rung(double t) {
        return foot().lerp(top(), t);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FOOT, new Vector3f());
        builder.define(TOP, new Vector3f());
        builder.define(FACE, Direction.NORTH.get3DDataValue());
        builder.define(CLIMB, 0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if(key.equals(FOOT) || key.equals(TOP)) {
            setBoundingBox(makeBoundingBox());
            layoutParts();
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox();
    }

    @Override
    protected AABB makeBoundingBox() {
        var foot = foot();
        var top = top();
        if(foot.distanceToSqr(top) < 1e-4)
            return new AABB(position(), position()).inflate(0.5);
        return new AABB(foot, top).inflate(0.5);
    }

    // ---- standing and falling ----

    @Override
    public void tick() {
        super.tick();
        if(tickCount == 1)
            layoutParts();
        if(level().isClientSide)
            return;
        if(tickCount % 10 == 0 && !supported()) {
            dismount = foot().add(outward().scale(0.6));
            ejectPassengers();
            drop();
            discard();
            return;
        }
        if(getFirstPassenger() instanceof Player climber)
            climb(climber);
    }

    private boolean supported() {
        var ground = BlockPos.containing(foot().subtract(0, 0.1, 0));
        return !level().getBlockState(ground).isAir() && !level().getBlockState(support).isAir();
    }

    private void drop() {
        spawnAtLocation(new ItemStack(ModItems.EXTENSION_LADDER.get()));
    }

    private void takeDown(Player player) {
        dismount = foot().add(outward().scale(0.6));
        ejectPassengers();
        if(!player.isCreative())
            player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.EXTENSION_LADDER.get()));
        discard();
    }

    // ---- climbing ----

    private void climb(Player climber) {
        double step = CLIMB_PER_TICK / Math.max(1, length());
        float t = climb();
        if(climber.zza > 0.1f) {
            if(t >= 1) {
                stepOffTop(climber);
                return;
            }
            entityData.set(CLIMB, (float) Math.min(1, t + step));
        } else if(climber.zza < -0.1f) {
            if(t <= 0) {
                dismount = foot().add(outward().scale(0.6));
                climber.stopRiding();
                return;
            }
            entityData.set(CLIMB, (float) Math.max(0, t - step));
        }
    }

    /** Forward at the top: onto the block above the support if a player fits there. */
    private void stepOffTop(Player climber) {
        var landing = Vec3.atBottomCenterOf(support.above());
        if(!level().noCollision(climber, climber.getDimensions(Pose.STANDING).makeBoundingBox(landing)))
            return;
        dismount = landing;
        climber.stopRiding();
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if(!hasPassenger(passenger))
            return;
        var p = rung(climb()).add(outward().scale(STAND_OFF));
        callback.accept(passenger, p.x, p.y, p.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        var at = dismount != null ? dismount : rung(climb()).add(outward().scale(STAND_OFF + 0.2));
        dismount = null;
        return at;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    public boolean canRiderInteract() {
        return true;
    }

    /** A click on the rails at {@code hit}: sneaking takes the ladder down, otherwise the player steps on there. */
    public InteractionResult climbOn(Player player, Vec3 hit) {
        if(level().isClientSide)
            return InteractionResult.SUCCESS;
        if(player.isShiftKeyDown()) {
            takeDown(player);
            return InteractionResult.CONSUME;
        }
        if(!getPassengers().isEmpty() || player.isPassenger())
            return InteractionResult.PASS;
        var foot = foot();
        var along = top().subtract(foot);
        double t = hit.subtract(foot).dot(along) / along.lengthSqr();
        entityData.set(CLIMB, (float) Mth.clamp(t, 0.02, 0.98));
        player.startRiding(this);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        return climbOn(player, position().add(hit));
    }

    // ---- picking up ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if(level().isClientSide || isRemoved())
            return false;
        if(source.getEntity() instanceof Player player) {
            takeDown(player);
            return true;
        }
        return false;
    }

    /** The parts are what gets picked; the whole-ladder box only sizes the render cull. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.EXTENSION_LADDER.get());
    }

    // ---- saving ----

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(FOOT, new Vector3f(tag.getFloat("FootX"), tag.getFloat("FootY"), tag.getFloat("FootZ")));
        entityData.set(TOP, new Vector3f(tag.getFloat("TopX"), tag.getFloat("TopY"), tag.getFloat("TopZ")));
        entityData.set(FACE, tag.getInt("Face"));
        entityData.set(CLIMB, tag.getFloat("Climb"));
        support = NbtUtils.readBlockPos(tag, "Support").orElse(BlockPos.ZERO);
        setBoundingBox(makeBoundingBox());
        layoutParts();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        var foot = foot();
        var top = top();
        tag.putFloat("FootX", (float) foot.x);
        tag.putFloat("FootY", (float) foot.y);
        tag.putFloat("FootZ", (float) foot.z);
        tag.putFloat("TopX", (float) top.x);
        tag.putFloat("TopY", (float) top.y);
        tag.putFloat("TopZ", (float) top.z);
        tag.putInt("Face", entityData.get(FACE));
        tag.putFloat("Climb", climb());
        tag.put("Support", NbtUtils.writeBlockPos(support));
    }
}
