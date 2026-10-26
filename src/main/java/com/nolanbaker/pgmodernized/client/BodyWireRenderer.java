package com.nolanbaker.pgmodernized.client;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nolanbaker.pgmodernized.util.BodyRider;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireRenderer;

/**
 * Power Grid's block wire renderer, for runs that may ride a Sable body. A run keeps its segments
 * in the body's own frame and its entity at the body's current position for its origin, so on a
 * body the segments are drawn turned by the body's render orientation and pinned to where the
 * body's render pose puts the origin this frame, not where the entity's tick-rate position was.
 * A run on a turned body never fits its own axis-aligned culling box, so it is always drawn.
 */
public class BodyWireRenderer extends BlockWireRenderer {
    public BodyWireRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Nullable
    private static ClientSubLevelAccess bodyOf(BlockWireEntity entity) {
        if(!(entity instanceof BodyRider rider))
            return null;
        var origin = rider.ride().origin();
        return origin == null ? null : SableCompanion.INSTANCE.getContainingClient(origin);
    }

    @Override
    public void render(BlockWireEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light) {
        var body = bodyOf(entity);
        var origin = entity instanceof BodyRider rider ? rider.ride().origin() : null;
        if(body == null || origin == null) {
            draw(entity, yaw, partialTicks, poseStack, buffer, light);
            return;
        }
        var pose = body.renderPose(partialTicks);
        var where = pose.transformPosition(origin);
        var drawn = entity.getPosition(partialTicks);
        var q = pose.orientation();
        poseStack.pushPose();
        poseStack.translate(where.x - drawn.x, where.y - drawn.y, where.z - drawn.z);
        poseStack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
        draw(entity, yaw, partialTicks, poseStack, buffer, light);
        poseStack.popPose();
    }

    /** How short a sideways jog can be and still be drawn as one smooth offset instead of two elbows, in pixels. */
    private static final int OFFSET_MAX_PX = 4;
    /** A coupling every so many blocks of tube: EMT comes in ten-foot sticks. */
    private static final double COUPLING_EVERY = 3;
    /** Short legs an offset may run through: sideways in two directions and an inline step between. */
    private static final int OFFSET_MAX_LEGS = 4;

    /**
     * Conduit is drawn here rather than by Power Grid's loop, which tiles its banded wire texture
     * along every straight and runs each one half a tube past its ends. Here a straight is an
     * octagonal tube drawn exactly from where it starts to where it stops, every corner with room
     * for it gets a swept elbow between the shortened legs, a corner without room is a plain
     * overlapped joint, and a short sideways jog between two legs the same way is drawn as one
     * smooth offset. The first and last straights start and end on the exact centre of their
     * knockouts, which Power Grid's pixel-centre snapping can miss by half a pixel. Nipples and
     * non-conduit block wires still go through Power Grid's renderer.
     */
    private void draw(BlockWireEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light) {
        if(!(entity instanceof ConduitRunEntity run) || run.isNipple() || run.segments.isEmpty()) {
            super.render(entity, yaw, partialTicks, poseStack, buffer, light);
            return;
        }
        var consumer = TubeDraw.buffer(buffer);
        var pose = poseStack.last();
        var entry = run.getWireEntry();
        float t = entry != null ? entry.wireThickness() : run.size().tubeThickness();
        float h = t / 2;
        int rgb = run.getColor() & 0xFFFFFF;
        var segs = run.segments;
        int n = segs.size();
        if(run.isDirect()) {
            // One straight piece from knockout to knockout, a pixel into each, whatever the legs say.
            boolean body = bodyOf(run) != null;
            var endPos = Vec3.ZERO;
            for(var seg : segs)
                endPos = endPos.add(seg.vector());
            var from = body ? Vec3.ZERO : exactNudge(run, run.getEndpoint1(), Vec3.ZERO);
            var to = endPos.add(body ? Vec3.ZERO : exactNudge(run, run.getEndpoint2(), endPos));
            var dir = to.subtract(from);
            if(dir.lengthSqr() > 1e-8) {
                double len = dir.length();
                dir = dir.normalize();
                int light2 = brightest(run, run.position().add(from.add(to).scale(0.5)));
                TubeDraw.straight(pose, consumer, light2, rgb, from.subtract(dir.scale(1 / 16.0)), to.add(dir.scale(1 / 16.0)), t);
                couplings(pose, consumer, light2, rgb, from, dir, 0, len, t);
            }
            return;
        }
        // Bend radius at each corner: one and a half tubes, never under two pixels nor over six, nor more than half of either leg.
        float wanted = Math.max(2 / 16f, Math.min(6 / 16f, 1.5f * t));
        float[] radii = new float[n];
        for(int i = 0; i + 1 < n; ++i) {
            var a = segs.get(i);
            var b = segs.get(i + 1);
            // A bend needs a real corner (not a reversal) and room: under a pixel it stays a sharp joint.
            if(a.direction != b.direction && a.direction != b.direction.getOpposite()) {
                float r = Math.min(wanted, Math.min(a.length() / 2, b.length() / 2));
                radii[i] = r >= 1 / 16f ? r : 0;
            }
        }
        // A bend under a pixel and a half reads as a notch; such a corner is filled with a cube instead.
        for(int i = 0; i < n; ++i)
            if(radii[i] > 0 && radii[i] < 1.5f / 16)
                radii[i] = 0;
        // Offsets: two legs the same way with a few short legs between, sideways or inline, become one
        // smooth S: a diagonal that advances two jogs for one jog across, its kinks a setback short of
        // the legs. Knockouts a few pixels apart in two or three directions come out as one offset.
        int[] offsetSpan = new int[n];
        double[] offsetCut = new double[n], startCut = new double[n];
        for(int i = 0; i + 2 < n; ++i) {
            var a = segs.get(i);
            var along = Vec3.atLowerCornerOf(a.direction.getNormal());
            int span = 0;
            var jog = Vec3.ZERO;
            boolean ok = false;
            for(int k = i + 1; k < n && span < OFFSET_MAX_LEGS; ++k) {
                var leg = segs.get(k);
                if(leg.direction == a.direction) {
                    ok = span > 0;
                    break;
                }
                if(leg.direction == a.direction.getOpposite() || leg.gridLength > OFFSET_MAX_PX)
                    break;
                jog = jog.add(leg.vector());
                ++span;
            }
            if(!ok)
                continue;
            var across = jog.subtract(along.scale(jog.dot(along)));
            double m = across.length();
            if(m < 0.5 / 16)
                continue;
            var c = segs.get(i + span + 1);
            var diagonal = along.scale(2 * m).add(jog).normalize();
            double cut = m + TubeDraw.setback(wanted, along, diagonal);
            // The legs either side must hold the cut; a bend at their far ends gives way to it,
            // down to a sharp joint, since a kink drawn as two sharp joints looks worse.
            double roomA = a.length() - cut - startCut[i], roomC = c.length() - cut;
            if(roomA < -1e-6 || roomC < -1e-6)
                continue;
            if(i > 0 && radii[i - 1] > roomA)
                radii[i - 1] = roomA >= 1.5f / 16 ? (float) roomA : 0;
            if(radii[i + span + 1] > roomC)
                radii[i + span + 1] = roomC >= 1.5f / 16 ? (float) roomC : 0;
            offsetSpan[i] = span;
            offsetCut[i] = cut;
            startCut[i + span + 1] = cut;
            i += span;
        }
        // What each straight gives up at each end: a bend radius, an offset's setback, or a negative cut
        // (an overlap of half a tube) where two legs just meet in a cube.
        double[] cutStart = new double[n], cutEnd = new double[n];
        boolean[] skip = new boolean[n];
        for(int i = 0; i + 1 < n; ++i) {
            if(offsetSpan[i] > 0) {
                cutEnd[i] = offsetCut[i];
                for(int k = 1; k <= offsetSpan[i]; ++k)
                    skip[i + k] = true;
                cutStart[i + offsetSpan[i] + 1] = offsetCut[i];
                i += offsetSpan[i];
            } else if(radii[i] > 0) {
                cutEnd[i] = radii[i];
                cutStart[i + 1] = radii[i];
            } else {
                cutEnd[i] = -h;
                cutStart[i + 1] = -h;
            }
        }
        boolean onBody = bodyOf(run) != null;
        var startNudge = onBody ? Vec3.ZERO : exactNudge(run, run.getEndpoint1(), Vec3.ZERO);
        var pos = Vec3.ZERO;
        double travelled = 0;
        for(int i = 0; i < n; ++i) {
            var seg = segs.get(i);
            var dir = seg.direction;
            var along = Vec3.atLowerCornerOf(dir.getNormal());
            var end = pos.add(seg.vector());
            if(!skip[i]) {
                // The first and last reach a pixel into their knockouts, from the knockout's true centre;
                // a leg through a strut's clamp is drawn in the slot, which may be off the pixel grid.
                var side = onBody ? Vec3.ZERO : strutNudge(run, pos, end, dir, t, i == 0 ? startNudge : Vec3.ZERO);
                var endNudge = i == n - 1 && !onBody ? exactNudge(run, run.getEndpoint2(), end) : Vec3.ZERO;
                var from = i == 0 ? pos.add(startNudge.lengthSqr() > 0 ? startNudge : side).relative(dir, -1 / 16.0) : pos.add(side).relative(dir, cutStart[i]);
                var to = i == n - 1 ? end.add(endNudge.lengthSqr() > 0 ? endNudge : side).relative(dir, 1 / 16.0) : end.add(side).relative(dir, -cutEnd[i]);
                int segLight = brightest(run, run.position().add(from.add(to).scale(0.5)));
                if(to.subtract(from).dot(along) > 1e-4) {
                    TubeDraw.straight(pose, consumer, segLight, rgb, from, to, t);
                    couplings(pose, consumer, segLight, rgb, pos, along, travelled, seg.length(), t, cutStart[i], cutEnd[i]);
                }
                if(offsetSpan[i] > 0) {
                    var c1 = end;
                    var c2 = end;
                    for(int k = 1; k <= offsetSpan[i]; ++k)
                        c2 = c2.add(segs.get(i + k).vector());
                    double m = c2.subtract(c1).length();
                    var p = c1.subtract(along.scale(m));
                    var q = c2.add(along.scale(m));
                    var diagonal = q.subtract(p).normalize();
                    double d = TubeDraw.setback(wanted, along, diagonal);
                    TubeDraw.bend(pose, consumer, segLight, rgb, p, along, diagonal, wanted, t);
                    TubeDraw.straight(pose, consumer, segLight, rgb, p.add(diagonal.scale(d)), q.subtract(diagonal.scale(d)), t);
                    TubeDraw.bend(pose, consumer, segLight, rgb, q, diagonal, along, wanted, t);
                } else if(i + 1 < n && radii[i] > 0) {
                    TubeDraw.bend(pose, consumer, segLight, rgb, end, along, Vec3.atLowerCornerOf(segs.get(i + 1).direction.getNormal()), radii[i], t);
                } else if(i + 1 < n) {
                    TubeDraw.joint(pose, consumer, segLight, rgb, end, t);
                }
            }
            pos = end;
            travelled += seg.length();
        }
    }

    private static void couplings(PoseStack.Pose pose, VertexConsumer consumer, int light, int rgb, Vec3 segStart, Vec3 along, double travelled, double length, float t) {
        couplings(pose, consumer, light, rgb, segStart, along, travelled, length, t, 0, 0);
    }

    /**
     * A coupling ring every few blocks along the run, counted from its start: a ring a quarter
     * pixel proud and two pixels long, a shade darker, only where it lands on a straight well
     * clear of the bends.
     */
    private static void couplings(PoseStack.Pose pose, VertexConsumer consumer, int light, int rgb, Vec3 segStart, Vec3 along,
                                  double travelled, double length, float t, double cutStart, double cutEnd) {
        int r = (int) ((rgb >> 16 & 0xFF) * 0.93), g = (int) ((rgb >> 8 & 0xFF) * 0.93), b = (int) ((rgb & 0xFF) * 0.93);
        int shade = r << 16 | g << 8 | b;
        double margin = 3 / 16.0;
        for(double at = Math.ceil(travelled / COUPLING_EVERY) * COUPLING_EVERY; at <= travelled + length; at += COUPLING_EVERY) {
            if(at <= 0)
                continue;
            double local = at - travelled;
            if(local < cutStart + margin || local > length - cutEnd - margin)
                continue;
            var centre = segStart.add(along.scale(local));
            TubeDraw.straight(pose, consumer, light, shade, centre.subtract(along.scale(1 / 16.0)), centre.add(along.scale(1 / 16.0)), t + 0.5f / 16);
        }
    }

    /**
     * How far the run's end sits from the true centre of its knockout, when it is on one: Power
     * Grid snaps run ends to pixel centres, and a knockout centred on a whole pixel is half a pixel
     * off that. Anything bigger than a pixel and a half is not that, and is left alone.
     */
    private static Vec3 exactNudge(ConduitRunEntity run, @Nullable IWireEndpoint endpoint, Vec3 runEnd) {
        if(!(endpoint instanceof BlockWireEndpoint knockout) || !knockout.isValid(run.level()))
            return Vec3.ZERO;
        var nudge = knockout.getExactPosition(run.level()).subtract(run.position().add(runEnd));
        return nudge.lengthSqr() <= (1.5 / 16) * (1.5 / 16) ? nudge : Vec3.ZERO;
    }

    /**
     * The sideways shift that draws a leg crossing a strut's rail in the slot it was clipped to:
     * the slots sit where a panel's knockouts do, a fraction of a pixel off the run's pixel grid.
     * Zero for a leg that crosses no strut, runs along one, or is not near a slot: within a pixel
     * of the leg as laid, or within half a pixel of a first leg already drawn from its knockout's
     * true centre, so a leg is never pulled one way at the knockout and the other at the strut.
     */
    private static Vec3 strutNudge(ConduitRunEntity run, Vec3 start, Vec3 end, Direction dir, float t, Vec3 startNudge) {
        var level = run.level();
        var origin = run.position();
        var leg = end.subtract(start);
        double len = leg.length();
        if(len < 1e-6)
            return Vec3.ZERO;
        var along = Vec3.atLowerCornerOf(dir.getNormal());
        BlockPos last = null;
        for(int k = 0; k * 0.5 <= len + 0.5; ++k) {
            var world = origin.add(start.add(leg.scale(Math.min(k * 0.5, len) / len)));
            var bp = BlockPos.containing(world);
            if(bp.equals(last))
                continue;
            last = bp;
            var state = level.getBlockState(bp);
            if(!(state.getBlock() instanceof com.nolanbaker.pgmodernized.conduit.StrutBlock))
                continue;
            var facing = state.getValue(com.nolanbaker.pgmodernized.conduit.StrutBlock.FACING);
            var axis = com.nolanbaker.pgmodernized.conduit.StrutBlock.axisFor(facing, state.getValue(com.nolanbaker.pgmodernized.conduit.StrutBlock.AXIS));
            if(dir.getAxis() == axis || dir.getAxis() == facing.getAxis())
                continue;
            var local = world.subtract(bp.getX(), bp.getY(), bp.getZ());
            var nudge = com.nolanbaker.pgmodernized.conduit.StrutBlock.clamp(state, local, t * 16 / 2).subtract(local);
            nudge = nudge.subtract(along.scale(nudge.dot(along)));
            boolean fromKnockout = startNudge.lengthSqr() > 0;
            double limit = (fromKnockout ? 0.5 : 0.9) / 16;
            if(nudge.subtract(fromKnockout ? startNudge : Vec3.ZERO).lengthSqr() <= limit * limit)
                return nudge;
        }
        return Vec3.ZERO;
    }

    /**
     * The light for a piece of tube at that point: the brightest of its block and the six around it,
     * as Power Grid samples, since a run along a wall has its centre line inside the wall's block.
     */
    private static int brightest(ConduitRunEntity run, Vec3 at) {
        var level = run.level();
        var centre = BlockPos.containing(at);
        int best = LevelRenderer.getLightColor(level, centre);
        for(var d : Direction.values()) {
            int light = LevelRenderer.getLightColor(level, centre.relative(d));
            if((light & 0xFFFF) > (best & 0xFFFF) || (light >> 16) > (best >> 16))
                best = Math.max(best & 0xFFFF, light & 0xFFFF) | Math.max(best >> 16 & 0xFFFF, light >> 16 & 0xFFFF) << 16;
        }
        return best;
    }

    @Override
    public boolean shouldRender(BlockWireEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return bodyOf(entity) != null || super.shouldRender(entity, frustum, camX, camY, camZ);
    }
}
