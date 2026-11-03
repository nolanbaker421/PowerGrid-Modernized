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
 * Orange fiberglass ladders with aluminium rungs. An extension ladder is a base section and a
 * narrower fly section overlapping it from its feet to its top; an A-frame is a front section
 * with rungs and a pair of back legs with braces, meeting under a cap. Each section is drawn in
 * a frame with x across the rungs, y up the rails and z out of the section's plane.
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
        if(top.distanceTo(foot) < 0.5)
            return;
        var drawn = entity.getPosition(partialTicks);
        ms.pushPose();
        ms.translate(-drawn.x, -drawn.y, -drawn.z);
        if(entity.frame() > 0) {
            var back = entity.backFoot();
            double px = section(ms, foot, top, entity.outward());
            rails(ms, buffer, light, px, 5, 0);
            rungs(ms, buffer, light, RUNG_PITCH / 2, px - 1, 4, 0);
            ms.popPose();
            px = section(ms, back, top, entity.outward().scale(-1));
            rails(ms, buffer, light, px, 5, 0);
            rungs(ms, buffer, light, px * 0.3, px * 0.3 + 1, 4, 0);
            rungs(ms, buffer, light, px * 0.7, px * 0.7 + 1, 4, 0);
            ms.popPose();
            // The cap over the apex.
            ms.pushPose();
            ms.translate(top.x, top.y, top.z);
            ms.mulPose(new Quaternionf().rotationY((float) Math.atan2(-entity.outward().x, entity.outward().z)));
            DeviceDraw.box(ms, buffer, light, -5.5, -0.5, -2, 5.5, 1, 2, RUNG);
            ms.popPose();
        } else {
            double px = section(ms, foot, top, entity.outward());
            double baseTop = px * 0.6, flyFrom = px * 0.4;
            rails(ms, buffer, light, baseTop, 5, 0);
            rungs(ms, buffer, light, RUNG_PITCH / 2, baseTop - 1, 4, 0);
            ms.pushPose();
            ms.translate(0, 0, 1.65 / 16);
            railsFrom(ms, buffer, light, flyFrom, px, 4);
            rungs(ms, buffer, light, flyFrom + RUNG_PITCH / 2, px - 1, 3, 0);
            ms.popPose();
            ms.popPose();
        }
        ms.popPose();
        super.render(entity, yaw, partialTicks, ms, buffer, light);
    }

    /**
     * Pushes a pose at {@code from} with y running up to {@code to} and x across the rails, and
     * returns the section's length in sixteenths. The lean tilts the rails about the across axis,
     * which lies along the leaned-on face; a dead-vertical section takes it from {@code outward}.
     */
    private static double section(PoseStack ms, Vec3 from, Vec3 to, Vec3 outward) {
        var along = to.subtract(from);
        var flat = new Vec3(along.x, 0, along.z);
        var up = new Vec3(0, 1, 0);
        var side = flat.lengthSqr() < 1e-6 ? up.cross(outward.scale(-1)) : up.cross(flat.normalize());
        if(side.lengthSqr() < 1e-6)
            side = new Vec3(1, 0, 0);
        side = side.normalize();
        double lean = Math.atan2(flat.length(), along.y);
        double turn = Math.atan2(-side.z, side.x);
        ms.pushPose();
        ms.translate(from.x, from.y, from.z);
        ms.mulPose(new Quaternionf().rotationAxis((float) lean, (float) side.x, 0, (float) side.z));
        ms.mulPose(new Quaternionf().rotationY((float) turn));
        return along.length() * 16;
    }

    private static void rails(PoseStack ms, MultiBufferSource buffer, int light, double length, double half, double z) {
        DeviceDraw.box(ms, buffer, light, -half, 0, z - 0.75, -half + 1, length, z + 0.75, RAIL);
        DeviceDraw.box(ms, buffer, light, half - 1, 0, z - 0.75, half, length, z + 0.75, RAIL);
    }

    private static void railsFrom(PoseStack ms, MultiBufferSource buffer, int light, double from, double to, double half) {
        DeviceDraw.box(ms, buffer, light, -half, from, -0.75, -half + 1, to, 0.75, RAIL);
        DeviceDraw.box(ms, buffer, light, half - 1, from, -0.75, half, to, 0.75, RAIL);
    }

    private static void rungs(PoseStack ms, MultiBufferSource buffer, int light, double from, double to, double half, double z) {
        for(double y = from; y < to; y += RUNG_PITCH)
            DeviceDraw.box(ms, buffer, light, -half, y, z - 0.5, half, y + 1, z + 0.5, RUNG);
    }
}
