package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.device.pole.LadderEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * An orange fiberglass extension ladder from its feet to its top: a base section and a narrower fly
 * section overlapping it, rungs every 0.3 m. Drawn in a frame with x across the rungs, y up the
 * rails and z out of the ladder's plane.
 */
public class LadderRenderer extends EntityRenderer<LadderEntity> {
    private static final ResourceLocation TEXTURE = PowerGridModernized.asResource("textures/special/conduit.png");
    private static final int RAIL = 0xE8731E, RUNG = 0xC2C6CC;
    private static final double RUNG_PITCH = 0.3 * 16;

    public LadderRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(LadderEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(LadderEntity entity, float yaw, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light) {
        var foot = entity.foot();
        var top = entity.top();
        var along = top.subtract(foot);
        double length = along.length();
        if(length < 0.5)
            return;
        var drawn = entity.getPosition(partialTicks);
        var flat = new Vec3(along.x, 0, along.z);
        // Side runs along the leaned-on face; the lean tilts the rails about it toward the pole.
        var side = flat.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0).cross(entity.outward().scale(-1)) : new Vec3(0, 1, 0).cross(flat.normalize());
        if(side.lengthSqr() < 1e-6)
            side = new Vec3(1, 0, 0);
        side = side.normalize();
        double lean = Math.atan2(flat.length(), along.y);
        double turn = Math.atan2(-side.z, side.x);

        ms.pushPose();
        ms.translate(foot.x - drawn.x, foot.y - drawn.y, foot.z - drawn.z);
        ms.mulPose(new Quaternionf().rotationAxis((float) lean, (float) side.x, 0, (float) side.z));
        ms.mulPose(new Quaternionf().rotationY((float) turn));

        double px = length * 16;
        double baseTop = px * 0.6, flyFrom = px * 0.4;
        // The base section: wide rails, rungs between them.
        DeviceDraw.box(ms, buffer, light, -5, 0, -0.75, -4, baseTop, 0.75, RAIL);
        DeviceDraw.box(ms, buffer, light, 4, 0, -0.75, 5, baseTop, 0.75, RAIL);
        for(double y = RUNG_PITCH / 2; y < baseTop - 1; y += RUNG_PITCH)
            DeviceDraw.box(ms, buffer, light, -4, y, -0.5, 4, y + 1, 0.5, RUNG);
        // The fly section, a touch narrower and a touch out from the base, to the top.
        DeviceDraw.box(ms, buffer, light, -4, flyFrom, 0.9, -3, px, 2.4, RAIL);
        DeviceDraw.box(ms, buffer, light, 3, flyFrom, 0.9, 4, px, 2.4, RAIL);
        for(double y = flyFrom + RUNG_PITCH / 2; y < px - 1; y += RUNG_PITCH)
            DeviceDraw.box(ms, buffer, light, -3, y, 1.15, 3, y + 1, 2.15, RUNG);
        ms.popPose();
        super.render(entity, yaw, partialTicks, ms, buffer, light);
    }
}
