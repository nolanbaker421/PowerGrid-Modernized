package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Square-section tube, drawn as plain lit quads on a white texture: straights, swept elbows and
 * cubes, all in one flat colour that the normal lighting shades. Positions are in blocks in the
 * pose's own frame. Every quad is wound to face away from the tube's centre line, so nothing
 * depends on which way round a ring was built.
 */
public final class TubeDraw {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private TubeDraw() {}

    public static VertexConsumer buffer(MultiBufferSource buffer) {
        return buffer.getBuffer(RenderType.entitySolid(WHITE));
    }

    /** A straight from {@code a} to {@code b} of side {@code t}, capped at both ends. */
    public static void straight(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 a, Vec3 b, float t) {
        var u = b.subtract(a);
        double len = u.length();
        if(len < 1e-5)
            return;
        u = u.scale(1 / len);
        var v = perpendicular(u);
        var w = u.cross(v);
        double h = t / 2;
        var ra = ring(a, v, w, h);
        var rb = ring(b, v, w, h);
        sides(pose, c, light, rgb, ra, rb, a.add(b).scale(0.5));
        cap(pose, c, light, rgb, ra, a.add(u.scale(h)));
        cap(pose, c, light, rgb, rb, b.subtract(u.scale(h)));
    }

    /**
     * A quarter-circle elbow of bend radius {@code r} at {@code corner}, turning from the leg that
     * arrives along {@code in} to the leg that leaves along {@code out}: a few chords of tube swept
     * round the bend, each ring square to the arc, so the section is the tube's own all the way.
     */
    public static void elbow(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 corner, Direction in, Direction out, float r, float t) {
        var d1 = Vec3.atLowerCornerOf(in.getNormal());
        var d2 = Vec3.atLowerCornerOf(out.getNormal());
        var centre = corner.subtract(d1.scale(r)).add(d2.scale(r));
        var n = d1.cross(d2);
        double h = t / 2;
        int steps = Math.max(3, Math.min(8, (int) Math.ceil(r * Math.PI / 2 / Math.max(0.01, h))));
        Vec3[] prev = null;
        Vec3 prevAt = null;
        for(int k = 0; k <= steps; ++k) {
            double a = Math.PI / 2 * k / steps;
            var radial = d1.scale(Math.sin(a)).subtract(d2.scale(Math.cos(a)));
            var at = centre.add(radial.scale(r));
            var ring = ring(at, radial, n, h);
            if(prev != null)
                sides(pose, c, light, rgb, prev, ring, prevAt.add(at).scale(0.5));
            prev = ring;
            prevAt = at;
        }
    }

    /** A cube of half-side {@code h} centred at {@code at}: a sharp joint, or a reversal. */
    public static void cube(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 at, double h) {
        var x = new Vec3(1, 0, 0);
        var z = new Vec3(0, 0, 1);
        var lo = ring(at.add(0, -h, 0), x, z, h);
        var hi = ring(at.add(0, h, 0), x, z, h);
        sides(pose, c, light, rgb, lo, hi, at);
        cap(pose, c, light, rgb, lo, at);
        cap(pose, c, light, rgb, hi, at);
    }

    private static Vec3 perpendicular(Vec3 u) {
        var v = Math.abs(u.x) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return v.subtract(u.scale(u.dot(v))).normalize();
    }

    private static Vec3[] ring(Vec3 p, Vec3 v, Vec3 w, double h) {
        return new Vec3[] {
                p.add(v.scale(h)).add(w.scale(h)),
                p.add(v.scale(-h)).add(w.scale(h)),
                p.add(v.scale(-h)).add(w.scale(-h)),
                p.add(v.scale(h)).add(w.scale(-h))};
    }

    /** The four faces between two rings, facing away from the point between them. */
    private static void sides(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3[] a, Vec3[] b, Vec3 inside) {
        for(int i = 0; i < 4; ++i) {
            int j = (i + 1) % 4;
            quad(pose, c, light, rgb, a[i], a[j], b[j], b[i], inside);
        }
    }

    private static void cap(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3[] ring, Vec3 inside) {
        quad(pose, c, light, rgb, ring[0], ring[1], ring[2], ring[3], inside);
    }

    /** One quad, wound so that its front faces away from {@code inside}. */
    private static void quad(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 inside) {
        var normal = p1.subtract(p0).cross(p2.subtract(p0));
        if(normal.lengthSqr() < 1e-14)
            normal = p2.subtract(p1).cross(p3.subtract(p1));
        if(normal.lengthSqr() < 1e-14)
            return;
        normal = normal.normalize();
        var mid = p0.add(p1).add(p2).add(p3).scale(0.25);
        if(normal.dot(mid.subtract(inside)) < 0) {
            normal = normal.scale(-1);
            var swap = p1;
            p1 = p3;
            p3 = swap;
        }
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        float nx = (float) normal.x, ny = (float) normal.y, nz = (float) normal.z;
        vertex(pose, c, light, r, g, b, p0, 0, 0, nx, ny, nz);
        vertex(pose, c, light, r, g, b, p1, 0, 1, nx, ny, nz);
        vertex(pose, c, light, r, g, b, p2, 1, 1, nx, ny, nz);
        vertex(pose, c, light, r, g, b, p3, 1, 0, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, int light, int r, int g, int b, Vec3 p, float u, float v, float nx, float ny, float nz) {
        c.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
