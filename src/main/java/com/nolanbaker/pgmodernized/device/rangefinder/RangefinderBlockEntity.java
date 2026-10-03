package com.nolanbaker.pgmodernized.device.rangefinder;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.tterrag.registrate.builders.BlockEntityBuilder.BlockEntityFactory;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * Fires a ray along the block's facing every few ticks and keeps the distance to the first thing
 * it meets: a block of the world, a block of a physics body (the ray is carried into each body's
 * own coordinates), or an entity such as a contraption, a vehicle or a mob. Wires are ignored. A
 * rangefinder on a body measures from where the body currently holds it.
 */
public class RangefinderBlockEntity extends SmartBlockEntity implements INetworkJack, IHaveGoggleInformation {
    /** Replaced by the computer bridges with their network-node subclasses. */
    public static BlockEntityFactory<RangefinderBlockEntity> FACTORY = RangefinderBlockEntity::new;

    public static final int HIT_NONE = 0, HIT_BLOCK = 1, HIT_BODY = 2, HIT_ENTITY = 3;

    private final JackSupport jack = new JackSupport(this, true);
    private float range = -1;
    private float distance = -1;
    private int hit = HIT_NONE;
    private int timer;
    private float syncedDistance = -2;
    private int lastSignal = -1;

    public RangefinderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    // ---- measuring ----

    /** The range limit in blocks: this device's own if set, else the configured default. */
    public float range() {
        float max = PgmConfig.RANGEFINDER_MAX_RANGE.get().floatValue();
        return range > 0 ? Math.min(range, max) : Math.min(PgmConfig.RANGEFINDER_DEFAULT_RANGE.get().floatValue(), max);
    }

    public void setRange(double blocks) {
        range = (float) Math.max(1, blocks);
        setChanged();
    }

    /** Distance to the target in blocks, or -1 when nothing is within range. */
    public float distance() {
        return distance;
    }

    public int hitKind() {
        return hit;
    }

    public int comparatorSignal() {
        if(distance < 0)
            return 0;
        return Mth.clamp((int) Math.round(15 * (1 - distance / range())), 0, 15);
    }

    @Override
    public void tick() {
        super.tick();
        jack.tick();
        if(level == null || level.isClientSide)
            return;
        if(++timer < PgmConfig.RANGEFINDER_INTERVAL.get())
            return;
        timer = 0;
        measure();
        int signal = comparatorSignal();
        if(signal != lastSignal) {
            lastSignal = signal;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        if(Math.abs(distance - syncedDistance) > 0.02f) {
            syncedDistance = distance;
            sendData();
        }
    }

    private void measure() {
        var state = getBlockState();
        if(!(state.getBlock() instanceof RangefinderBlock)) {
            distance = -1;
            hit = HIT_NONE;
            return;
        }
        var facing = state.getValue(RangefinderBlock.FACING);
        var origin = RangefinderBlock.beamOrigin(state, worldPosition);
        var direction = Vec3.atLowerCornerOf(facing.getNormal());
        // A rangefinder on a body fires from where the body holds it, the way the body points it.
        var mine = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        if(mine != null) {
            var pose = mine.logicalPose();
            origin = pose.transformPosition(origin);
            direction = pose.transformNormal(direction).normalize();
        }
        float limit = range();
        var end = origin.add(direction.scale(limit));
        double best = limit;
        int kind = HIT_NONE;

        // The world's blocks.
        var worldHit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if(worldHit.getType() == HitResult.Type.BLOCK) {
            best = worldHit.getLocation().distanceTo(origin);
            kind = HIT_BLOCK;
        }

        // Blocks of other bodies: only the part of the ray inside each body's bounds, carried into its coordinates.
        var beam = new AABB(origin, end).inflate(0.5);
        for(var other : SableCompanion.INSTANCE.getAllIntersecting(level, new BoundingBox3d(beam))) {
            if(mine != null && other.getUniqueId().equals(mine.getUniqueId()))
                continue;
            var bounds = other.boundingBox();
            var box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()).inflate(1);
            var entry = box.clip(origin, end);
            var start = box.contains(origin) ? origin : entry.orElse(null);
            if(start == null)
                continue;
            var pose = other.logicalPose();
            var localStart = pose.transformPositionInverse(start);
            var localEnd = pose.transformPositionInverse(end);
            var bodyHit = level.clip(new ClipContext(localStart, localEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if(bodyHit.getType() != HitResult.Type.BLOCK)
                continue;
            // Only trust a hit inside this body's own plot; past it the ray would read the next plot's blocks.
            var hitWorld = pose.transformPosition(bodyHit.getLocation());
            if(!box.contains(hitWorld))
                continue;
            double d = hitWorld.distanceTo(origin);
            if(d < best) {
                best = d;
                kind = HIT_BODY;
            }
        }

        // Entities: contraptions, vehicles, mobs. Not items, not wires.
        for(var entity : level.getEntities((Entity) null, beam, e -> e.isAlive() && !e.isSpectator() && !(e instanceof ItemEntity) && !(e instanceof BaseWireEntity))) {
            var clipped = entity.getBoundingBox().clip(origin, end);
            if(clipped.isEmpty())
                continue;
            double d = clipped.get().distanceTo(origin);
            if(d < best) {
                best = d;
                kind = HIT_ENTITY;
            }
        }

        distance = kind == HIT_NONE ? -1 : (float) best;
        hit = kind;
    }

    // ---- jack ----

    @Override
    public JackSupport networkJack() {
        return jack;
    }

    @Override
    public Vec3 jackPosition(int port) {
        return RangefinderBlock.jackPosition(getBlockState(), getBlockPos());
    }

    @Override
    public void remove() {
        super.remove();
        jack.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        jack.unload();
    }

    // ---- save and sync ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Range", range);
        tag.putFloat("Distance", distance);
        tag.putInt("Hit", hit);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        range = tag.contains("Range") ? tag.getFloat("Range") : -1;
        distance = tag.contains("Distance") ? tag.getFloat("Distance") : -1;
        hit = tag.getInt("Hit");
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.rangefinder.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(distance < 0) {
            Lang.builder().translate("gui.rangefinder.none", String.format("%.0f", range())).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        } else {
            String what = switch(hit) {
                case HIT_BODY -> "gui.rangefinder.body";
                case HIT_ENTITY -> "gui.rangefinder.entity";
                default -> "gui.rangefinder.block";
            };
            Lang.builder().translate("gui.rangefinder.distance", String.format("%.2f", distance)).style(ChatFormatting.AQUA)
                    .text(" ").add(Lang.builder().translate(what).style(ChatFormatting.WHITE)).forGoggles(tooltip, 1);
        }
        return true;
    }
}
