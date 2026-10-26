package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Octagonal tube, drawn as lit quads over a brushed galvanized texture that runs along the tube:
 * straights and bends of any angle, all in one colour that the normal lighting shades. Positions
 * are in blocks in the pose's own frame. Every quad is wound to face away from the tube's centre
 * line, so nothing depends on which way round a ring was built.
 */
public final class TubeDraw {
    private static final ResourceLocation TUBE = PowerGridModernized.asResource("textures/special/conduit_tube.png");
    private static final int SIDES = 8;
    /** An octagon of apothem h has this circumradius. */
    private static final double CIRCUM = 1 / Math.cos(Math.PI / SIDES);

    private TubeDraw() {}

    public static VertexConsumer buffer(MultiBufferSource buffer) {
        return buffer.getBuffer(RenderType.entitySolid(TUBE));
    }

    /** A straight from {@code a} to {@code b}, flat-to-flat width {@code t}, capped at both ends. */
    public static void straight(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 a, Vec3 b, float t) {
        var u = b.subtract(a);
        double len = u.length();
        if(len < 1e-5)
            return;
        u = u.scale(1 / len);
        var v = perpendicular(u);
        var w = u.cross(v);
        double h = t / 2;
        // In pieces no longer than a block, so the texture never has to wrap along the tube.
        int pieces = Math.max(1, (int) Math.ceil(len - 1e-6));
        var prev = ring(a, v, w, h);
        cap(pose, c, light, rgb, prev, a.add(u.scale(h)));
        double at = 0;
        for(int k = 1; k <= pieces; ++k) {
            double next = k == pieces ? len : len * k / pieces;
            var p = a.add(u.scale(next));
            var ring = ring(p, v, w, h);
            sides(pose, c, light, rgb, prev, ring, 0, (float) (next - at), a.add(u.scale((at + next) / 2)));
            prev = ring;
            at = next;
        }
        cap(pose, c, light, rgb, prev, b.subtract(u.scale(h)));
    }

    /**
     * A bend of radius {@code r} at {@code corner}, from the leg arriving along unit vector
     * {@code u1} to the leg leaving along {@code u2}: an arc tangent to both legs, which it meets
     * {@code r·tan(θ/2)} short of the corner, swept as a few chords of tube with every ring square
     * to the arc. A right angle meets its legs {@code r} short; an offset's shallow kink much less.
     */
    public static void bend(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 corner, Vec3 u1, Vec3 u2, double r, float t) {
        double cos = Mth.clamp(u1.dot(u2), -1, 1);
        double theta = Math.acos(cos);
        if(theta < 1e-3)
            return;
        var inward = u2.subtract(u1.scale(cos));
        if(inward.lengthSqr() < 1e-10)
            return;
        inward = inward.normalize();
        var n = u1.cross(u2).normalize();
        double d = r * Math.tan(theta / 2);
        var centre = corner.subtract(u1.scale(d)).add(inward.scale(r));
        double h = t / 2;
        int steps = Math.max(2, Math.min(8, (int) Math.ceil(r * theta / Math.max(0.01, h))));
        Vec3[] prev = null;
        Vec3 prevAt = null;
        float arc = 0, chord = (float) (r * theta / steps);
        for(int k = 0; k <= steps; ++k) {
            double a = theta * k / steps;
            var radial = inward.scale(-Math.cos(a)).add(u1.scale(Math.sin(a)));
            var at = centre.add(radial.scale(r));
            var ring = ring(at, radial, n, h);
            if(prev != null) {
                sides(pose, c, light, rgb, prev, ring, arc, arc + chord, prevAt.add(at).scale(0.5));
                arc += chord;
            }
            prev = ring;
            prevAt = at;
        }
    }

    /** How far short of a corner a bend of that radius and turn meets its legs. */
    public static double setback(double r, Vec3 u1, Vec3 u2) {
        double theta = Math.acos(Mth.clamp(u1.dot(u2), -1, 1));
        return r * Math.tan(theta / 2);
    }

    private static Vec3 perpendicular(Vec3 u) {
        var v = Math.abs(u.x) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return v.subtract(u.scale(u.dot(v))).normalize();
    }

    /** The octagon's corners round {@code p} in the plane of {@code v} and {@code w}, flats square to both. */
    private static Vec3[] ring(Vec3 p, Vec3 v, Vec3 w, double h) {
        var out = new Vec3[SIDES];
        double rc = h * CIRCUM;
        for(int k = 0; k < SIDES; ++k) {
            double phi = Math.PI * 2 * (k + 0.5) / SIDES;
            out[k] = p.add(v.scale(rc * Math.cos(phi))).add(w.scale(rc * Math.sin(phi)));
        }
        return out;
    }

    /** The faces between two rings, facing away from the point between them, texture running from u0 to u1. */
    private static void sides(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3[] a, Vec3[] b, float u0, float u1, Vec3 inside) {
        for(int i = 0; i < a.length; ++i) {
            int j = (i + 1) % a.length;
            quad(pose, c, light, rgb, a[i], a[j], b[j], b[i], u0, 0, u0, 1, u1, 1, u1, 0, inside);
        }
    }

    private static void cap(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3[] ring, Vec3 inside) {
        // An octagon as a fan of quads from its first corner.
        for(int i = 1; i + 2 < ring.length; i += 2)
            quad(pose, c, light, rgb, ring[0], ring[i], ring[i + 1], ring[i + 2], 0, 0, 0, 1, 1, 1, 1, 0, inside);
    }

    /** One quad, wound so that its front faces away from {@code inside}. */
    private static void quad(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                             float s0, float t0, float s1, float t1, float s2, float t2, float s3, float t3, Vec3 inside) {
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
            float ss = s1, ts = t1;
            s1 = s3;
            t1 = t3;
            s3 = ss;
            t3 = ts;
        }
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        float nx = (float) normal.x, ny = (float) normal.y, nz = (float) normal.z;
        vertex(pose, c, light, r, g, b, p0, s0, t0, nx, ny, nz);
        vertex(pose, c, light, r, g, b, p1, s1, t1, nx, ny, nz);
        vertex(pose, c, light, r, g, b, p2, s2, t2, nx, ny, nz);
        vertex(pose, c, light, r, g, b, p3, s3, t3, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, int light, int r, int g, int b, Vec3 p, float u, float v, float nx, float ny, float nz) {
        c.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
