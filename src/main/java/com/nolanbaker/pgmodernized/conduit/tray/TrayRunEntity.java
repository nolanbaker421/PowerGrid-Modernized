package com.nolanbaker.pgmodernized.conduit.tray;

import com.nolanbaker.pgmodernized.network.Cat6BlockWireEntity;
import com.nolanbaker.pgmodernized.network.Cat6CableItem;
import com.nolanbaker.pgmodernized.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.collections.ModdedTags;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWire;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.ImaginaryWireEndpoint;
import org.patryk3211.powergrid.utility.BlockTrace;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.PlayerUtilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A cable tray laid along block surfaces like conduit, but with free ends: it starts and stops
 * wherever it is clicked, and a run begun or ended on another run's rails is a branch, found by
 * where it touches rather than by any shared endpoint. It carries no current. Cat6 pulled through
 * it lies in one of {@link #LANES} lanes; a tray keeps which cable holds each lane, and a tray
 * taken down drops every cable through it.
 */
public class TrayRunEntity extends BlockWireEntity {
    public static final int LANES = 10;
    /** Lateral pixel offsets of the five lanes in a layer, and the lift of the second layer. */
    public static final int[] LANE_PX = {0, -2, 2, -4, 4};
    /** The first layer's centre sits on the rungs; the second a cable's thickness above it. */
    public static final double BASE_LIFT_PX = 1.75, LAYER_LIFT_PX = 2;
    public static final int DEFAULT_COLOR = 0xA9ADB2;
    /** How far a run's end may sit from another run's rails and still be joined to it. */
    public static final double JOIN_REACH = 0.35;
    /** How far round a click to look for the trays a cable might run through. */
    public static final double NETWORK_REACH = 64;
    private static final int LANE_SWEEP = 100;

    /** Lane index to the Cat6 cable lying in it. */
    private final Map<Integer, UUID> lanes = new HashMap<>();

    public TrayRunEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static TrayRunEntity create(Level level, IWireEndpoint start, ItemStack item, List<Point> segments) {
        var entity = create(level, start.getExactPosition(level), item, segments);
        if(!(start instanceof ImaginaryWireEndpoint))
            entity.setEndpoint1(start);
        return entity;
    }

    public static TrayRunEntity create(Level level, Vec3 start, ItemStack item, List<Point> segments) {
        var entity = new TrayRunEntity(ModEntities.TRAY_RUN.get(), level);
        entity.setItem(item.getItem(), item.getCount());
        entity.setColor(DEFAULT_COLOR);
        var pos = BlockTrace.alignPosition(start);
        entity.setPosRaw(pos.x, pos.y, pos.z);
        entity.segments.addAll(segments);
        entity.bakeBoundingBoxes();
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        return entity;
    }

    // ---- not a wire ----

    @Override
    public float current() {
        return 0;
    }

    @Override
    public float measuredCurrent() {
        return 0;
    }

    @Override
    public void makeWire() {}

    @Override
    public void dropWire() {}

    @Override
    public void unloaded() {}

    /** Same as Power Grid's flip, but the replacement stays a tray and keeps its lanes. */
    @Override
    public BlockWireEntity flip() {
        var entity = new TrayRunEntity(ModEntities.TRAY_RUN.get(), level());
        entity.setItem(getItem(), getWireCount());
        entity.setEndpoint1(getEndpoint2());
        entity.setEndpoint2(getEndpoint1());
        entity.setColor(getColor());
        entity.lanes.putAll(lanes);
        var pos = position();
        for(var segment : segments) {
            pos = pos.add(segment.vector());
            if(segment.gridLength > 0)
                entity.segments.add(0, new Point(segment.direction.getOpposite(), segment.gridLength));
        }
        entity.setPosRaw(pos.x, pos.y, pos.z);
        entity.bakeBoundingBoxes();
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        discard();
        ((ServerLevel) level()).tryAddFreshEntityWithPassengers(entity);
        return entity;
    }

    // ---- geometry ----

    /** The run's corners from its start to its end, world positions. */
    public List<Vec3> corners() {
        var out = new ArrayList<Vec3>();
        var pos = position();
        out.add(pos);
        for(var segment : segments) {
            pos = pos.add(segment.vector());
            out.add(pos);
        }
        return out;
    }

    public Vec3 endPosition() {
        var pos = position();
        for(var segment : segments)
            pos = pos.add(segment.vector());
        return pos;
    }

    /** The point on the rails nearest {@code p}, with the leg it is on and how far along it in pixels. */
    public Nearest nearest(Vec3 p) {
        var corners = corners();
        Nearest best = null;
        for(int i = 0; i + 1 < corners.size(); ++i) {
            var a = corners.get(i);
            var b = corners.get(i + 1);
            var ab = b.subtract(a);
            double len2 = ab.lengthSqr();
            double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
            var q = a.add(ab.scale(t));
            double d2 = q.distanceToSqr(p);
            if(best == null || d2 < best.distance2) {
                int px = (int) Math.round(t * segments.get(i).gridLength);
                best = new Nearest(i, px, a.add(ab.scale(segments.get(i).gridLength == 0 ? 0 : (double) px / segments.get(i).gridLength)), d2);
            }
        }
        return best != null ? best : new Nearest(0, 0, position(), 0);
    }

    public record Nearest(int leg, int px, Vec3 point, double distance2) {}

    /** The face a leg lies on, as the direction from the surface out to the tray; up when nothing is found. */
    public Direction legNormal(int leg) {
        var corners = corners();
        var mid = corners.get(leg).add(corners.get(leg + 1)).scale(0.5);
        var axis = segments.get(leg).direction.getAxis();
        float t = getWireEntry() != null ? getWireEntry().wireThickness() : 0.1875f;
        for(var d : Direction.values()) {
            if(d.getAxis() == axis)
                continue;
            var probe = mid.relative(d, t / 2 + 1 / 32.0);
            if(!level().getBlockState(net.minecraft.core.BlockPos.containing(probe)).getCollisionShape(level(), net.minecraft.core.BlockPos.containing(probe)).isEmpty())
                return d.getOpposite();
        }
        return Direction.UP;
    }

    /** Every tray within reach of a point, this one included. */
    public static List<TrayRunEntity> around(Level level, Vec3 p) {
        return level.getEntitiesOfClass(TrayRunEntity.class, new AABB(p, p).inflate(NETWORK_REACH));
    }

    /** Whether this run's start or end sits on the other run's rails, and where. */
    @Nullable
    public Nearest joinOn(TrayRunEntity other) {
        for(var end : new Vec3[] {position(), endPosition()}) {
            var near = other.nearest(end);
            if(near.distance2 <= JOIN_REACH * JOIN_REACH)
                return near;
        }
        return null;
    }

    // ---- lanes ----

    public int lanesUsed() {
        return lanes.size();
    }

    public boolean laneFree(int lane) {
        return !lanes.containsKey(lane);
    }

    public void takeLane(int lane, UUID cable) {
        lanes.put(lane, cable);
    }

    public void freeLane(UUID cable) {
        lanes.values().removeIf(cable::equals);
    }

    @Override
    public void tick() {
        super.tick();
        if(level().isClientSide || tickCount % LANE_SWEEP != 0 || lanes.isEmpty())
            return;
        // A cable cut, picked up or otherwise gone gives its lane back.
        var server = (ServerLevel) level();
        lanes.values().removeIf(id -> {
            var cable = server.getEntity(id);
            return cable == null || cable.isRemoved();
        });
    }

    /** Taking the tray down drops every cable lying in it. */
    @Override
    public void kill() {
        if(!level().isClientSide) {
            var server = (ServerLevel) level();
            for(var id : new ArrayList<>(lanes.values()))
                if(server.getEntity(id) instanceof Cat6BlockWireEntity cable && !cable.isRemoved())
                    cable.kill();
            lanes.clear();
        }
        super.kill();
    }

    // ---- clicks ----

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if(hand != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;
        var stack = player.getItemInHand(hand);
        boolean client = level().isClientSide;
        if(stack.getItem() instanceof CableTrayItem)
            return client ? InteractionResult.SUCCESS : TrayPlacement.attach(this, player, stack);
        if(Cat6CableItem.isCat6(stack))
            return client ? InteractionResult.SUCCESS : Cat6TrayRouting.click(this, player, stack);
        if(stack.is(ModdedTags.Item.WIRE_CUTTERS.tag) || stack.is(ModdedTags.Item.BAD_WIRE_CUTTERS.tag)) {
            if(!client)
                kill();
            return InteractionResult.SUCCESS;
        }
        if(IWire.isWire(level(), stack.getItem())) {
            if(client)
                player.displayClientMessage(Lang.translate("message.tray.cat6_only").style(ChatFormatting.RED).component(), true);
            return InteractionResult.FAIL;
        }
        if(stack.isEmpty()) {
            if(!client)
                player.displayClientMessage(Lang.builder().translate("message.tray.lanes", lanes.size(), LANES).style(ChatFormatting.GRAY).component(), true);
            return InteractionResult.SUCCESS;
        }
        return super.interact(player, hand);
    }

    /** Where the player's look hits the rails, or where they stand if it does not. */
    public Vec3 hitBy(Player player) {
        var eye = player.getEyePosition();
        var hit = raycast(eye, eye.add(player.getLookAngle().scale(PlayerUtilities.getReachDistance(player) + 1)));
        return hit != null ? hit : eye;
    }

    // ---- saving ----

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        var list = new ListTag();
        for(var e : lanes.entrySet()) {
            var tag = new CompoundTag();
            tag.putInt("Lane", e.getKey());
            tag.putUUID("Cable", e.getValue());
            list.add(tag);
        }
        nbt.put("Lanes", list);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        lanes.clear();
        for(var tag : nbt.getList("Lanes", Tag.TAG_COMPOUND)) {
            var c = (CompoundTag) tag;
            if(c.hasUUID("Cable"))
                lanes.put(c.getInt("Lane"), c.getUUID("Cable"));
        }
    }
}
