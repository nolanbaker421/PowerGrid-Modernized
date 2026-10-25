package com.nolanbaker.pgmodernized.client;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LevelRenderer;
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
     * Conduit is drawn here rather than by Power Grid's loop, which tiles its banded wire texture
     * along every straight and runs each one half a tube past its ends. Here a straight is a plain
     * flat-shaded tube drawn exactly from where it starts to where it stops, every corner with
     * room for it gets a swept elbow between the shortened legs, a corner without room gets a
     * cube. Nipples and non-conduit block wires still go through Power Grid's renderer.
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
            var end = pos.add(seg.vector());
            float rIn = i > 0 ? radii[i - 1] : 0, rOut = radii[i];
            // The straight runs between its two elbows; the first and last reach a pixel into their knockouts.
            var from = i == 0 ? pos.relative(dir, -1 / 16.0) : pos.relative(dir, rIn);
            var to = i == n - 1 ? end.relative(dir, 1 / 16.0) : end.relative(dir, -rOut);
            var mid = run.position().add(from.add(to).scale(0.5));
            int segLight = brightest(run, mid);
            if(to.subtract(from).dot(Vec3.atLowerCornerOf(dir.getNormal())) > 1e-4)
                TubeDraw.straight(pose, consumer, segLight, rgb, from, to, t);
            if(i + 1 < n) {
                var next = segs.get(i + 1).direction;
                if(rOut > 0)
                    TubeDraw.elbow(pose, consumer, segLight, rgb, end, dir, next, rOut, t);
                else
                    TubeDraw.cube(pose, consumer, segLight, rgb, end, t / 2);
            }
            pos = end;
        }
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
