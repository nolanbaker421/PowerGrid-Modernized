package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.conduit.tray.TrayRunEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * A galvanized ladder tray along a run: two rails twelve pixels apart standing three high on the
 * surface each leg follows, rungs every six pixels. Each leg is drawn in a frame with x across the
 * tray, y out of the surface and z along the leg. At a corner the outer rail of each leg runs on
 * to meet the other's, the inner rails stop short of each other, and a plate floors the square
 * between; where the surface changes, as off a ceiling down a wall, the ending leg runs on by the
 * tray's depth to close the gap.
 */
public class TrayRunRenderer extends EntityRenderer<TrayRunEntity> {
    private static final ResourceLocation TEXTURE = PowerGridModernized.asResource("textures/special/conduit.png");
    private static final int RAIL = 0xA9ADB2, RUNG = 0x8E9298;
    private static final double HALF = 6, DEPTH = 3, RUNG_EVERY = 6;

    public TrayRunRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TrayRunEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(TrayRunEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(1));
    }

    @Override
    public void render(TrayRunEntity entity, float yaw, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light) {
        var segs = entity.segments;
        if(segs.isEmpty())
            return;
        var corners = entity.corners();
        var origin = entity.getPosition(partialTicks);
        int n = segs.size();
        for(int i = 0; i < n; ++i) {
            var seg = segs.get(i);
            if(seg.gridLength <= 0)
                continue;
            var along = Vec3.atLowerCornerOf(seg.direction.getNormal());
            var up = Vec3.atLowerCornerOf(entity.legNormal(i).getNormal());
            var across = along.cross(up);
            var a = corners.get(i);
            ms.pushPose();
            ms.translate(a.x - origin.x, a.y - origin.y, a.z - origin.z);
            // Columns are the local axes in world space: x across, y out of the surface, z along.
            ms.mulPose(new Matrix4f(new Matrix3f(
                    (float) across.x, (float) across.y, (float) across.z,
                    (float) up.x, (float) up.y, (float) up.z,
                    (float) along.x, (float) along.y, (float) along.z)));
            double len = seg.gridLength;
            // Which side each neighbouring leg lies on: +1 the +x rail is inner, -1 the -x rail, 0 a change of surface.
            int startInner = 0, endInner = 0;
            if(i > 0)
                startInner = (int) Math.signum(Vec3.atLowerCornerOf(segs.get(i - 1).direction.getOpposite().getNormal()).dot(across));
            if(i + 1 < n)
                endInner = (int) Math.signum(Vec3.atLowerCornerOf(segs.get(i + 1).direction.getNormal()).dot(across));
            for(int side = -1; side <= 1; side += 2) {
                double from = 0, to = len;
                if(i > 0)
                    from = startInner == 0 ? 0 : side == startInner ? HALF - 1 : -HALF;
                if(i + 1 < n)
                    to = endInner == 0 ? len + DEPTH : side == endInner ? len - (HALF - 1) : len + HALF;
                if(to > from)
                    DeviceDraw.box(ms, buffer, light, side < 0 ? -HALF : HALF - 1, 0, from, side < 0 ? -HALF + 1 : HALF, DEPTH, to, RAIL);
            }
            double rungFrom = i > 0 && startInner != 0 ? HALF : RUNG_EVERY / 2;
            double rungTo = i + 1 < n && endInner != 0 ? len - HALF : len - 0.5;
            for(double z = rungFrom; z < rungTo; z += RUNG_EVERY)
                DeviceDraw.box(ms, buffer, light, -HALF + 1, 0, z, HALF - 1, 1, z + 1, RUNG);
            if(i + 1 < n && endInner != 0)
                DeviceDraw.box(ms, buffer, light, -HALF + 1, 0, len - (HALF - 1), HALF - 1, 1, len + HALF - 1, RUNG);
            ms.popPose();
        }
        super.render(entity, yaw, partialTicks, ms, buffer, light);
    }
}
