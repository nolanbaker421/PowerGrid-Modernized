package com.nolanbaker.pgmodernized.client;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nolanbaker.pgmodernized.util.BodyRider;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
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

    /**
     * Conduit is drawn here rather than by Power Grid's loop: each straight is shortened by the bend
     * radius where it meets the next, and a real elbow, three chords of a quarter circle in the tube's
     * own thickness, fills the corner. Each end keeps a connector the tube's size where it enters the
     * knockout. Nipples and non-conduit block wires still go through Power Grid's renderer.
     */
    private void draw(BlockWireEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light) {
        if(!(entity instanceof ConduitRunEntity run) || run.isNipple() || run.segments.isEmpty()) {
            super.render(entity, yaw, partialTicks, poseStack, buffer, light);
            return;
        }
        var consumer = buffer.getBuffer(RenderType.entitySolid(getTextureLocation(entity)));
        var entry = run.getWireEntry();
        float t = entry != null ? entry.wireThickness() : run.size().tubeThickness();
        int color = run.getColor() | 0xFF000000;
        int rgb = run.getColor() & 0xFFFFFF;
        var segs = run.segments;
        int n = segs.size();
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
        var pos = Vec3.ZERO;
        for(int i = 0; i < n; ++i) {
            var seg = segs.get(i);
            var dir = seg.direction;
            var start = pos;
            float len = seg.length();
            if(i == 0) {
                start = start.relative(dir, -1 / 16.0);
                len += 1 / 16f;
            }
            float rIn = i > 0 ? radii[i - 1] : 0, rOut = radii[i];
            // Shortened by the bend radius at each corner. Power Grid draws every segment half a tube
            // longer at both ends, so that half is cut too, less half a pixel so the straight runs into its elbow.
            float lap = 0.5f / 16;
            float cutIn = rIn > 0 ? rIn + t / 2 - lap : 0, cutOut = rOut > 0 ? rOut + t / 2 - lap : 0;
            start = start.relative(dir, cutIn);
            len -= cutIn + cutOut;
            var mid = run.position().add(start).add(Vec3.atLowerCornerOf(dir.getNormal()).scale(len / 2));
            int segLight = brightest(run, mid);
            // Power Grid's segment call: light, colour, start, direction, thickness, length, and the entity id for its texture tile.
            if(len > 0.001f)
                renderSegment(poseStack, consumer, segLight, color, start, dir, t, len, run.getId());
            var next = pos.add(seg.vector());
            if(rOut > 0.001f)
                elbow(poseStack, buffer, segLight, next, dir, segs.get(i + 1).direction, rOut, t, rgb);
            pos = next;
        }
        connectors(run, poseStack, buffer, light, t, rgb);
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

    /**
     * A quarter-circle elbow from the incoming leg to the outgoing, drawn the way Minecraft draws a
     * curve: axis-aligned cubes the tube's own size stepped along the arc, overlapping, so the
     * elbow's section is the tube's section at every point and nothing pokes out past the straights.
     */
    private static void elbow(PoseStack poseStack, MultiBufferSource buffer, int light, Vec3 corner, Direction in, Direction out, float r, float t, int rgb) {
        var d1 = Vec3.atLowerCornerOf(in.getNormal());
        var d2 = Vec3.atLowerCornerOf(out.getNormal());
        var centre = corner.subtract(d1.scale(r)).add(d2.scale(r));
        double h = t * 16 / 2;
        // Steps no further apart than half a tube, so the cubes overlap into a continuous bend.
        int steps = Math.max(2, (int) Math.ceil(r * Math.PI / 2 / Math.max(0.02, t / 2)));
        for(int k = 0; k <= steps; ++k) {
            double a = Math.toRadians(90.0 * k / steps);
            var p = centre.subtract(d2.scale(r * Math.cos(a))).add(d1.scale(r * Math.sin(a)));
            double x = p.x * 16, y = p.y * 16, z = p.z * 16;
            DeviceDraw.box(poseStack, buffer, light, x - h, y - h, z - h, x + h, y + h, z + h, rgb);
        }
    }

    /** A connector exactly the tube's size at each end, one tube radius in from the knockout, in the tube's colour darkened. */
    private static void connectors(ConduitRunEntity run, PoseStack poseStack, MultiBufferSource buffer, int light, float t, int rgb) {
        double tube = t * 16 / 2;
        int r = (int) ((rgb >> 16 & 0xFF) * 0.8), g = (int) ((rgb >> 8 & 0xFF) * 0.8), b = (int) ((rgb & 0xFF) * 0.8);
        int shade = r << 16 | g << 8 | b;
        var first = run.segments.get(0);
        connector(poseStack, buffer, light, first.vector().normalize().scale(tube / 16), tube, shade);
        var pos = Vec3.ZERO;
        BlockWireEntity.Point last = null;
        for(var segment : run.segments) {
            pos = pos.add(segment.vector());
            last = segment;
        }
        if(last != null)
            connector(poseStack, buffer, light, pos.subtract(last.vector().normalize().scale(tube / 16)), tube, shade);
    }

    private static void connector(PoseStack poseStack, MultiBufferSource buffer, int light, Vec3 at, double half, int rgb) {
        double x = at.x * 16, y = at.y * 16, z = at.z * 16;
        DeviceDraw.box(poseStack, buffer, light, x - half, y - half, z - half, x + half, y + half, z + half, rgb);
    }

    @Override
    public boolean shouldRender(BlockWireEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return bodyOf(entity) != null || super.shouldRender(entity, frustum, camX, camY, camZ);
    }
}
