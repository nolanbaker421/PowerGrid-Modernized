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
import org.jetbrains.annotations.Nullable;

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

    /** For previews: colours carry their alpha in the high byte. */
    public static VertexConsumer translucentBuffer(MultiBufferSource buffer) {
        return buffer.getBuffer(RenderType.entityTranslucent(TUBE));
    }

    /** A straight from {@code a} to {@code b}, flat-to-flat width {@code t}, capped at both ends, flats square to the axes where it can. */
    public static void straight(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 a, Vec3 b, float t) {
        straight(pose, c, light, rgb, a, b, t, null);
    }

    /**
     * The same with the octagon turned so one flat faces {@code flat}, when given: the straight
     * between an offset's two kinks, or a diagonal's two bends, keeps the turn the bends' rings
     * have, so the tube shows no step where they meet.
     */
    public static void straight(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 a, Vec3 b, float t, @Nullable Vec3 flat) {
        var u = b.subtract(a);
        double len = u.length();
        if(len < 1e-5)
            return;
        u = u.scale(1 / len);
        var v = flat(u, flat);
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
    public static Vec3 bend(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 corner, Vec3 u1, Vec3 u2, double r, float t) {
        return bend(pose, c, light, rgb, corner, u1, u2, r, t, null);
    }

    /**
     * The same with the rings' turn given: {@code flat0} is the way a flat of the arriving leg
     * faces (an axis tube's own when null), and the rings turn with the arc from there, so the
     * first ring matches the leg before and the tube does not step at the joint. Returns the way
     * that flat faces at the end, for the leg after. A plain axis elbow needs none of this: a
     * quarter turn takes an axis tube's flats to an axis tube's flats.
     */
    public static Vec3 bend(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 corner, Vec3 u1, Vec3 u2, double r, float t, @Nullable Vec3 flat0) {
        double cos = Mth.clamp(u1.dot(u2), -1, 1);
        double theta = Math.acos(cos);
        var v0 = flat(u1, flat0);
        if(theta < 1e-3)
            return v0;
        var inward = u2.subtract(u1.scale(cos));
        if(inward.lengthSqr() < 1e-10)
            return v0;
        inward = inward.normalize();
        var n = u1.cross(u2).normalize();
        double d = r * Math.tan(theta / 2);
        var centre = corner.subtract(u1.scale(d)).add(inward.scale(r));
        double h = t / 2;
        // Chords no longer than half the tube's half-width, so a fat tube's elbow is not a few flat facets.
        int steps = Math.max(3, Math.min(12, (int) Math.ceil(r * theta / Math.max(0.005, h / 2))));
        Vec3[] prev = null;
        Vec3 prevAt = null;
        float arc = 0, chord = (float) (r * theta / steps);
        for(int k = 0; k <= steps; ++k) {
            double a = theta * k / steps;
            var radial = inward.scale(-Math.cos(a)).add(u1.scale(Math.sin(a)));
            var tangent = u1.scale(Math.cos(a)).add(inward.scale(Math.sin(a)));
            var at = centre.add(radial.scale(r));
            var v = turned(v0, n, a);
            var ring = ring(at, v, tangent.cross(v), h);
            if(prev != null) {
                sides(pose, c, light, rgb, prev, ring, arc, arc + chord, prevAt.add(at).scale(0.5));
                arc += chord;
            }
            prev = ring;
            prevAt = at;
        }
        return turned(v0, n, theta);
    }

    /** The way one flat of a tube along {@code u} faces: {@code wanted} squared to the tube when it can be, else the axis-square default. */
    public static Vec3 flat(Vec3 u, @Nullable Vec3 wanted) {
        if(wanted != null) {
            var v = wanted.subtract(u.scale(u.dot(wanted)));
            if(v.lengthSqr() > 1e-8)
                return v.normalize();
        }
        return perpendicular(u);
    }

    /** {@code v} turned by {@code angle} about the unit axis {@code n}. */
    private static Vec3 turned(Vec3 v, Vec3 n, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return v.scale(cos).add(n.cross(v).scale(sin)).add(n.scale(n.dot(v) * (1 - cos)));
    }

    /**
     * A sharp joint: three short lengths of tube, one along each axis, through the corner point.
     * Their union fills the corner the two legs' chamfers leave open without a cube's corners
     * standing out past the tube.
     */
    public static void joint(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 at, float t) {
        double h = t / 2;
        straight(pose, c, light, rgb, at.add(-h, 0, 0), at.add(h, 0, 0), t);
        straight(pose, c, light, rgb, at.add(0, -h, 0), at.add(0, h, 0), t);
        straight(pose, c, light, rgb, at.add(0, 0, -h), at.add(0, 0, h), t);
    }

    /** A cube of half-side {@code h} at a sharp joint, filling the corner the two legs' chamfers leave open. */
    public static void cube(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, Vec3 at, double h) {
        var lo = new Vec3[] {at.add(-h, -h, -h), at.add(h, -h, -h), at.add(h, -h, h), at.add(-h, -h, h)};
        var hi = new Vec3[] {at.add(-h, h, -h), at.add(h, h, -h), at.add(h, h, h), at.add(-h, h, h)};
        sides(pose, c, light, rgb, lo, hi, 0, 1, at);
        cap(pose, c, light, rgb, lo, at);
        cap(pose, c, light, rgb, hi, at);
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
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF, a = rgb >>> 24 == 0 ? 255 : rgb >>> 24;
        float nx = (float) normal.x, ny = (float) normal.y, nz = (float) normal.z;
        vertex(pose, c, light, r, g, b, a, p0, s0, t0, nx, ny, nz);
        vertex(pose, c, light, r, g, b, a, p1, s1, t1, nx, ny, nz);
        vertex(pose, c, light, r, g, b, a, p2, s2, t2, nx, ny, nz);
        vertex(pose, c, light, r, g, b, a, p3, s3, t3, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, int light, int r, int g, int b, int a, Vec3 p, float u, float v, float nx, float ny, float nz) {
        c.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
