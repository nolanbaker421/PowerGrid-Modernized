package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.chain.CableChainEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a cable chain as an energy chain: a lower run lying from the fixed anchor toward the bend,
 * a half-circle, and an upper run coming back to the moving anchor. The bend sits half way between
 * the moving end and where it would be with the chain pulled straight, so it travels at half the
 * trolley's speed, as the real thing does. Links alternate orientation like a chain.
 */
public class CableChainRenderer extends EntityRenderer<CableChainEntity> {
    private static final ResourceLocation TEXTURE = PowerGridModernized.asResource("textures/entity/cable_chain.png");
    private static final double LINK_PITCH = 0.3, LINK_HALF_LENGTH = 0.16, LINK_HALF_WIDTH = 0.1, LINK_HALF_HEIGHT = 0.06;
    private static final double MIN_RISE = 0.5;

    public CableChainRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CableChainEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(CableChainEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(CableChainEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        var a = entity.fixedEnd();
        var b = entity.movingEnd();
        if(a == null || b == null)
            return;
        var path = path(a, b, entity.chainLength());
        if(path.size() < 2)
            return;
        var origin = entity.position();   // the run stands at the fixed post
        var consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        poseStack.pushPose();
        var pose = poseStack.last();
        var level = entity.level();
        int index = 0;
        for(int i = 0; i < path.size(); ++i) {
            var point = path.get(i);
            var next = path.get(Math.min(i + 1, path.size() - 1));
            var previous = path.get(Math.max(i - 1, 0));
            var tangent = next.subtract(previous);
            if(tangent.lengthSqr() < 1e-6)
                continue;
            tangent = tangent.normalize();
            // A horizontal normal to the chain's plane, and the in-plane "up" of the link.
            var side = tangent.cross(new Vec3(0, 1, 0));
            if(side.lengthSqr() < 1e-4)
                side = tangent.cross(new Vec3(1, 0, 0));
            side = side.normalize();
            var up = side.cross(tangent).normalize();
            boolean flat = (index++ & 1) == 0;
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(point));
            box(pose, consumer, point.subtract(origin), tangent, flat ? side : up, flat ? up : side, LINK_HALF_LENGTH, LINK_HALF_WIDTH, LINK_HALF_HEIGHT, light);
        }
        poseStack.popPose();
    }

    /** Sample points along the energy-chain path from the fixed end {@code a} to the moving end {@code b}. */
    static List<Vec3> path(Vec3 a, Vec3 b, double chainLength) {
        var horizontal = new Vec3(b.x - a.x, 0, b.z - a.z);
        double d = horizontal.length();
        var u = d > 0.01 ? horizontal.scale(1 / d) : new Vec3(1, 0, 0);
        double rise = b.y - a.y;
        if(Math.abs(rise) < MIN_RISE)
            rise = MIN_RISE;
        double r = Math.abs(rise) / 2;
        double usable = Math.max(chainLength - Math.PI * r, 0);
        // Where the bend is: half way between the moving end and the fully-pulled-out position.
        double bend = Math.max(d, Math.min(usable, (usable + d) / 2));
        var points = new ArrayList<Vec3>();
        // Lower run: from the fixed end out to the bend.
        for(double s = 0; s < bend; s += LINK_PITCH)
            points.add(a.add(u.scale(s)));
        var start = a.add(u.scale(bend));
        var centre = start.add(0, rise / 2, 0);
        // The half circle, bulging outward past the bend.
        int arcSteps = Math.max(2, (int) Math.round(Math.PI * r / LINK_PITCH));
        for(int i = 0; i <= arcSteps; ++i) {
            double theta = -Math.PI / 2 + Math.PI * i / arcSteps;
            points.add(centre.add(u.scale(r * Math.cos(theta))).add(0, r * Math.sin(theta) * Math.signum(rise), 0));
        }
        // Upper run: back from the bend to the moving end.
        var top = start.add(0, rise, 0);
        var toEnd = b.subtract(top);
        double upper = toEnd.length();
        if(upper > 0.01) {
            var v = toEnd.scale(1 / upper);
            for(double s = LINK_PITCH; s <= upper; s += LINK_PITCH)
                points.add(top.add(v.scale(s)));
        }
        points.add(b);
        return points;
    }

    static void box(PoseStack.Pose pose, VertexConsumer consumer, Vec3 c, Vec3 t, Vec3 n, Vec3 bn,
                            double hl, double hw, double hh, int light) {
        var tl = t.scale(hl);
        var nw = n.scale(hw);
        var bh = bn.scale(hh);
        // Six faces, each a quad whose corners are c ± tl ± nw ± bh.
        face(pose, consumer, light, t, c.add(tl).add(nw).add(bh), c.add(tl).add(nw).subtract(bh), c.add(tl).subtract(nw).subtract(bh), c.add(tl).subtract(nw).add(bh));
        face(pose, consumer, light, t.scale(-1), c.subtract(tl).subtract(nw).add(bh), c.subtract(tl).subtract(nw).subtract(bh), c.subtract(tl).add(nw).subtract(bh), c.subtract(tl).add(nw).add(bh));
        face(pose, consumer, light, n, c.add(nw).subtract(tl).add(bh), c.add(nw).subtract(tl).subtract(bh), c.add(nw).add(tl).subtract(bh), c.add(nw).add(tl).add(bh));
        face(pose, consumer, light, n.scale(-1), c.subtract(nw).add(tl).add(bh), c.subtract(nw).add(tl).subtract(bh), c.subtract(nw).subtract(tl).subtract(bh), c.subtract(nw).subtract(tl).add(bh));
        face(pose, consumer, light, bn, c.add(bh).subtract(tl).subtract(nw), c.add(bh).subtract(tl).add(nw), c.add(bh).add(tl).add(nw), c.add(bh).add(tl).subtract(nw));
        face(pose, consumer, light, bn.scale(-1), c.subtract(bh).add(tl).subtract(nw), c.subtract(bh).add(tl).add(nw), c.subtract(bh).subtract(tl).add(nw), c.subtract(bh).subtract(tl).subtract(nw));
    }

    private static void face(PoseStack.Pose pose, VertexConsumer consumer, int light, Vec3 normal, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4) {
        vertex(pose, consumer, light, normal, p1, 0, 0);
        vertex(pose, consumer, light, normal, p2, 0, 1);
        vertex(pose, consumer, light, normal, p3, 1, 1);
        vertex(pose, consumer, light, normal, p4, 1, 0);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, int light, Vec3 normal, Vec3 p, float u, float v) {
        consumer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
