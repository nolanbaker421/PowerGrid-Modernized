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
 * A galvanized ladder tray along a run: two rails twelve pixels apart and three deep, rungs
 * every six pixels, lying on the surface each leg follows. Each leg is drawn in a frame with x
 * across the tray, y out of the surface and z along the leg; legs overrun their corners by half
 * a width so the corners close.
 */
public class TrayRunRenderer extends EntityRenderer<TrayRunEntity> {
    private static final ResourceLocation TEXTURE = PowerGridModernized.asResource("textures/special/conduit.png");
    private static final int RAIL = 0xA9ADB2, RUNG = 0x8E9298;
    private static final double HALF_WIDTH = 6, DEPTH = 3, RUNG_EVERY = 6;

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
        for(int i = 0; i < segs.size(); ++i) {
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
            double from = i > 0 ? -HALF_WIDTH : 0;
            double to = seg.gridLength + (i + 1 < segs.size() ? HALF_WIDTH : 0);
            DeviceDraw.box(ms, buffer, light, -HALF_WIDTH, -DEPTH / 2, from, -HALF_WIDTH + 1, DEPTH / 2, to, RAIL);
            DeviceDraw.box(ms, buffer, light, HALF_WIDTH - 1, -DEPTH / 2, from, HALF_WIDTH, DEPTH / 2, to, RAIL);
            for(double z = RUNG_EVERY / 2; z < seg.gridLength - 0.5; z += RUNG_EVERY)
                DeviceDraw.box(ms, buffer, light, -HALF_WIDTH + 1, -DEPTH / 2, z, HALF_WIDTH - 1, -DEPTH / 2 + 1, z + 1, RUNG);
            ms.popPose();
        }
        super.render(entity, yaw, partialTicks, ms, buffer, light);
    }
}
