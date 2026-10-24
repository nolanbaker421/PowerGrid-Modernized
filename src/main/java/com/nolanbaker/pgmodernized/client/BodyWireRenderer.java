package com.nolanbaker.pgmodernized.client;

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
            super.render(entity, yaw, partialTicks, poseStack, buffer, light);
            collars(entity, poseStack, buffer, light);
            return;
        }
        var pose = body.renderPose(partialTicks);
        var where = pose.transformPosition(origin);
        var drawn = entity.getPosition(partialTicks);
        var q = pose.orientation();
        poseStack.pushPose();
        poseStack.translate(where.x - drawn.x, where.y - drawn.y, where.z - drawn.z);
        poseStack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
        collars(entity, poseStack, buffer, light);
        poseStack.popPose();
    }

    /**
     * A coupling at every corner and both ends of a conduit run: a cube a pixel proud of the tube,
     * in the tube's colour darkened, so joints read as fittings instead of bare ends meeting.
     */
    private static void collars(BlockWireEntity entity, PoseStack poseStack, MultiBufferSource buffer, int light) {
        if(!(entity instanceof ConduitRunEntity run) || run.isNipple() || run.segments.isEmpty())
            return;
        double half = run.size().tubeThickness() * 16 / 2 + 1;
        int rgb = entity.getColor() & 0xFFFFFF;
        int r = (int) ((rgb >> 16 & 0xFF) * 0.8), g = (int) ((rgb >> 8 & 0xFF) * 0.8), b = (int) ((rgb & 0xFF) * 0.8);
        int shade = r << 16 | g << 8 | b;
        var pos = Vec3.ZERO;
        BlockWireEntity.Point last = null;
        for(var segment : run.segments) {
            if(last == null || last.direction != segment.direction)
                collar(poseStack, buffer, light, pos, half, shade);
            pos = pos.add(segment.vector());
            last = segment;
        }
        collar(poseStack, buffer, light, pos, half, shade);
    }

    private static void collar(PoseStack poseStack, MultiBufferSource buffer, int light, Vec3 at, double half, int rgb) {
        double x = at.x * 16, y = at.y * 16, z = at.z * 16;
        DeviceDraw.box(poseStack, buffer, light, x - half, y - half, z - half, x + half, y + half, z + half, rgb);
    }

    @Override
    public boolean shouldRender(BlockWireEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return bodyOf(entity) != null || super.shouldRender(entity, frustum, camX, camY, camZ);
    }
}
