package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.registry.ModEntities;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A flexible conduit whip hung straight from one knockout to another. It keeps no segments: its
 * two ends are the world positions of its two hub terminals, looked up every tick wherever their
 * blocks' bodies are now, so one end can ride a crane boom while the other stays on the ground,
 * or both can ride different bodies. The whip is drawn as a sagging tube between them, and the
 * wires pulled through it connect their two terminals electrically with no geometry of their own.
 */
public class FlexConduitEntity extends ConduitRunEntity {
    /** Conduit bought per metre of straight distance: the whip is never pulled taut. */
    public static final float SLACK = 1.1f;
    public static final int RENDER_SEGMENTS = 12;

    private Vec3 end1 = Vec3.ZERO, end2 = Vec3.ZERO;
    private boolean placed;

    public FlexConduitEntity(EntityType<?> type, Level level) {
        super(type, level);
        ride().disable();
    }

    public static FlexConduitEntity create(Level level, BlockWireEndpoint a, BlockWireEndpoint b, ItemStack item) {
        var entity = new FlexConduitEntity(ModEntities.FLEX_CONDUIT.get(), level);
        entity.setItem(item.getItem(), item.getCount());
        entity.setColor(DEFAULT_COLOR);
        entity.setEndpoint1(a);
        entity.setEndpoint2(b);
        entity.refreshEnds();
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        return entity;
    }

    // ---- the two ends ----

    /** World position of a hub terminal, wherever its body holds it now. */
    private Vec3 endPos(@Nullable IWireEndpoint endpoint) {
        if(endpoint instanceof BlockWireEndpoint hub) {
            var p = IElectric.getTerminalPos(level(), hub.getPos(), hub.getTerminal());
            if(p != null) {
                var world = SableCompanion.INSTANCE.projectOutOfSubLevel(level(), p);
                return world != null ? world : p;
            }
        }
        return position();
    }

    /** Looks both ends up again; moves the entity and its boxes when either has moved. */
    public void refreshEnds() {
        var a = endPos(getEndpoint1());
        var b = endPos(getEndpoint2());
        boolean moved = !placed || a.distanceToSqr(end1) > 1e-6 || b.distanceToSqr(end2) > 1e-6;
        end1 = a;
        end2 = b;
        placed = true;
        if(moved) {
            setPos(a);
            bakeBoundingBoxes();
        }
    }

    public boolean placed() {
        return placed;
    }

    public Vec3 end1() {
        return end1;
    }

    public Vec3 end2() {
        return end2;
    }

    /** How far the middle hangs below the straight line, blocks. */
    public float sag() {
        return Math.min(1, (float) end1.distanceTo(end2) * 0.15f);
    }

    /** A point along the whip, t from 0 at the first end to 1 at the second. */
    public Vec3 point(float t) {
        return end1.lerp(end2, t).subtract(0, sag() * 4 * t * (1 - t), 0);
    }

    private AABB bounds() {
        if(!placed)
            return new AABB(position(), position()).inflate(0.5);
        return new AABB(end1, end2).expandTowards(0, -sag(), 0).inflate(size().tubeThickness() + 0.1);
    }

    @Override
    public void bakeBoundingBoxes() {
        var box = bounds();
        mainBoundingBox = box;
        boundingBoxes.clear();
        boundingBoxes.add(box);
        setBoundingBox(box);
    }

    @Override
    protected AABB makeBoundingBox() {
        return bounds();
    }

    @Override
    public float getTotalLength() {
        return placed ? (float) (end1.distanceTo(end2) * SLACK) : 0;
    }

    @Override
    public void tick() {
        super.tick();
        refreshEnds();
    }

    @Override
    public void onEntityDataPacket(CompoundTag tag) {
        super.onEntityDataPacket(tag);
        refreshEnds();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        placed = false;
    }

    // ---- picking ----

    @Override
    public @Nullable Vec3 raycast(Vec3 from, Vec3 to) {
        if(!placed)
            return null;
        double reach = size().tubeThickness() + 0.05;
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        var prev = point(0);
        for(int i = 1; i <= RENDER_SEGMENTS; ++i) {
            var next = point(i / (float) RENDER_SEGMENTS);
            var hit = closest(from, to, prev, next, reach);
            if(hit != null) {
                double d = hit.distanceToSqr(from);
                if(d < bestD) {
                    bestD = d;
                    best = hit;
                }
            }
            prev = next;
        }
        return best;
    }

    /** The point on the ray nearest the segment, when the two pass within reach of each other. */
    @Nullable
    private static Vec3 closest(Vec3 a0, Vec3 a1, Vec3 b0, Vec3 b1, double reach) {
        Vec3 d1 = a1.subtract(a0), d2 = b1.subtract(b0), r = a0.subtract(b0);
        double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r), c = d1.dot(r), b = d1.dot(d2);
        if(a < 1e-9 || e < 1e-9)
            return null;
        double denom = a * e - b * b;
        double s = denom > 1e-9 ? Mth.clamp((b * f - c * e) / denom, 0, 1) : 0;
        double t = (b * s + f) / e;
        if(t < 0) {
            t = 0;
            s = Mth.clamp(-c / a, 0, 1);
        } else if(t > 1) {
            t = 1;
            s = Mth.clamp((b - c) / a, 0, 1);
        }
        var p = a0.add(d1.scale(s));
        var q = b0.add(d2.scale(t));
        return p.distanceTo(q) <= reach ? p : null;
    }

    // ---- as a run ----

    /** The wires inside need no route of their own: a token segment keeps their boxes small. */
    @Override
    protected List<Point> conductorPath(Vec3 start, Vec3 end) {
        return List.of(Point.x(0.5f));
    }

    @Override
    public BlockWireEntity flip() {
        var entity = new FlexConduitEntity(ModEntities.FLEX_CONDUIT.get(), level());
        entity.setItem(getItem(), getWireCount());
        entity.setEndpoint1(getEndpoint2());
        entity.setEndpoint2(getEndpoint1());
        entity.getEntityData().set(TEMPERATURE, getTemperature());
        entity.setColor(getColor());
        entity.refreshEnds();
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setOldPosAndRot();
        entity.reapplyPosition();
        discard();
        ((ServerLevel) level()).tryAddFreshEntityWithPassengers(entity);
        return entity;
    }

    /** A whip is complete as laid: nothing continues from it. Wire, cutters and the empty hand work as on any run. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if(hand == InteractionHand.MAIN_HAND && player.getItemInHand(hand).getItem() instanceof ConduitItem) {
            if(!level().isClientSide)
                player.displayClientMessage(Lang.translate("message.conduit.no_tee").style(ChatFormatting.RED).component(), true);
            return level().isClientSide ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return super.interact(player, hand);
    }
}
