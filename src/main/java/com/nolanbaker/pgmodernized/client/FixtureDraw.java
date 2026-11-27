package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.fixture.FixtureDesign;
import com.nolanbaker.pgmodernized.device.fixture.FixturePart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a designed fixture from its parts, for the placed fixture and the designer's preview
 * alike. The pose is at the anchor block's minimum corner in the north frame; coordinates are
 * the design's pixels, the anchor block at 16 to 32. Bulbs are drawn by whoever calls, since the
 * world has a real bulb and the preview a stand-in.
 */
public final class FixtureDraw {
    private FixtureDraw() {}

    /** Draws a bulb standing up from its base at the pose origin, in a 16-pixel frame. */
    public interface BulbDrawer {
        void draw(PoseStack ms, MultiBufferSource buffer, int light, boolean lit);
    }

    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float BULB_SCALE = 0.5f;

    /**
     * @param textRange how far, in blocks, lettering is still drawn; the designer's preview passes
     *                  {@link Double#MAX_VALUE}, the world renderer a few dozen blocks, since
     *                  font draws are dear and a sign is unreadable past that
     */
    public static void draw(PoseStack ms, MultiBufferSource buffer, int light, FixtureDesign design, boolean lit, BulbDrawer bulbs, int selected, double textRange) {
        for(int i = 0; i < design.parts.size(); ++i) {
            var p = design.parts.get(i);
            int color = i == selected ? blend(p.color, 0xFFE040, 0.45f) : p.color;
            int partLight = p.material == FixturePart.Material.GLOW && lit ? LightTexture.FULL_BRIGHT : light;
            if(p.material == FixturePart.Material.GLOW && !lit)
                color = shade(color, 0.55f);
            float alpha = p.material == FixturePart.Material.GLASS ? 0.45f : 1;
            switch(p.kind) {
                case BOX -> box(ms, buffer, partLight, p.x, p.y, p.z, p.x + p.w, p.y + p.h, p.z + p.d, color, alpha, p.material);
                case CONE -> cone(ms, buffer, partLight, p, color, alpha);
                case GLOBE -> globe(ms, buffer, partLight, p, color, alpha);
                case CHAIN -> chain(ms, buffer, partLight, p, color);
                case SOCKET -> socket(ms, buffer, partLight, light, p, color, lit, bulbs);
                case TEXT -> text(ms, buffer, partLight, p, color, textRange);
                case CYLINDER -> cylinder(ms, buffer, partLight, p, color, alpha);
            }
        }
    }

    /** A frustum along the part's face: slabs from the full base to the tapered far end. */
    private static void cone(PoseStack ms, MultiBufferSource buffer, int light, FixturePart p, int color, float alpha) {
        var axis = p.face.getAxis();
        int steps = 4;
        double length = axis == Direction.Axis.Y ? p.h : axis == Direction.Axis.X ? p.w : p.d;
        double a0 = axis == Direction.Axis.Y ? p.w : axis == Direction.Axis.X ? p.h : p.w;
        double b0 = axis == Direction.Axis.Y ? p.d : axis == Direction.Axis.X ? p.d : p.h;
        double cx = p.x + p.w / 2.0, cy = p.y + p.h / 2.0, cz = p.z + p.d / 2.0;
        boolean positive = p.face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        for(int i = 0; i < steps; ++i) {
            double t0 = (double) i / steps, t1 = (double) (i + 1) / steps;
            double f = 1 - (1 - p.taper / 100.0) * (t0 + t1) / 2;
            double a = a0 * f, b = b0 * f;
            double from = positive ? t0 * length : (1 - t1) * length, to = positive ? t1 * length : (1 - t0) * length;
            switch(axis) {
                case Y -> box(ms, buffer, light, cx - a / 2, p.y + from, cz - b / 2, cx + a / 2, p.y + to, cz + b / 2, color, alpha, p.material);
                case X -> box(ms, buffer, light, p.x + from, cy - a / 2, cz - b / 2, p.x + to, cy + a / 2, cz + b / 2, color, alpha, p.material);
                default -> box(ms, buffer, light, cx - a / 2, cy - b / 2, p.z + from, cx + a / 2, cy + b / 2, p.z + to, color, alpha, p.material);
            }
        }
    }

    /**
     * Lettering in the game's font on the part's {@code face} side, centred, scaled to fit the face
     * both ways, standing a twentieth of a pixel off it.
     */
    private static void text(PoseStack ms, MultiBufferSource buffer, int light, FixturePart p, int color, double textRange) {
        if(p.text.isEmpty())
            return;
        if(textRange != Double.MAX_VALUE) {
            var origin = ms.last().pose().transformPosition(new org.joml.Vector3f());
            if(origin.lengthSquared() > textRange * textRange)
                return;
        }
        var font = Minecraft.getInstance().font;
        int width = Math.max(1, font.width(p.text));
        double cx = p.x + p.w / 2.0, cy = p.y + p.h / 2.0, cz = p.z + p.d / 2.0;
        double faceW = p.face.getAxis() == Direction.Axis.X ? p.d : p.w;
        double faceH = p.face.getAxis() == Direction.Axis.Y ? p.d : p.h;
        double s = Math.min(faceH / 9.0, faceW / width);   // design pixels per font pixel
        double out = 0.05;
        ms.pushPose();
        switch(p.face) {
            case NORTH -> ms.translate(at(cx), at(cy), at(p.z - out));
            case SOUTH -> { ms.translate(at(cx), at(cy), at(p.z + p.d + out)); ms.mulPose(Axis.YP.rotationDegrees(180)); }
            case EAST -> { ms.translate(at(p.x + p.w + out), at(cy), at(cz)); ms.mulPose(Axis.YP.rotationDegrees(-90)); }
            case WEST -> { ms.translate(at(p.x - out), at(cy), at(cz)); ms.mulPose(Axis.YP.rotationDegrees(90)); }
            case UP -> { ms.translate(at(cx), at(p.y + p.h + out), at(cz)); ms.mulPose(Axis.XP.rotationDegrees(90)); }
            case DOWN -> { ms.translate(at(cx), at(p.y - out), at(cz)); ms.mulPose(Axis.XP.rotationDegrees(-90)); }
        }
        float scale = (float) (s / 16);
        ms.scale(-scale, -scale, scale);
        font.drawInBatch(p.text, -width / 2f, -4.5f, 0xFF000000 | color, false, ms.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, light);
        ms.popPose();
    }

    /** A design pixel as a pose coordinate: the pose sits at the anchor block's corner, pixel 16. */
    private static double at(double px) {
        return (px - 16) / 16;
    }

    /** A ball from three crossing slabs and a core, which reads round enough at a lamp's size. */
    private static void globe(PoseStack ms, MultiBufferSource buffer, int light, FixturePart p, int color, float alpha) {
        double cx = p.x + p.w / 2.0, cy = p.y + p.h / 2.0, cz = p.z + p.d / 2.0;
        double rx = p.w / 2.0, ry = p.h / 2.0, rz = p.d / 2.0;
        double k = 0.72, m = 0.9;
        box(ms, buffer, light, cx - rx * k, cy - ry * k, cz - rz * k, cx + rx * k, cy + ry * k, cz + rz * k, color, alpha, p.material);
        box(ms, buffer, light, cx - rx, cy - ry * k * m, cz - rz * k * m, cx + rx, cy + ry * k * m, cz + rz * k * m, color, alpha, p.material);
        box(ms, buffer, light, cx - rx * k * m, cy - ry, cz - rz * k * m, cx + rx * k * m, cy + ry, cz + rz * k * m, color, alpha, p.material);
        box(ms, buffer, light, cx - rx * k * m, cy - ry * k * m, cz - rz, cx + rx * k * m, cy + ry * k * m, cz + rz, color, alpha, p.material);
    }

    /** Links two pixels long, turned in alternation, along the part's face from its base. */
    private static void chain(PoseStack ms, MultiBufferSource buffer, int light, FixturePart p, int color) {
        var axis = p.face.getAxis();
        double length = axis == Direction.Axis.Y ? p.h : axis == Direction.Axis.X ? p.w : p.d;
        double cx = p.x + p.w / 2.0, cy = p.y + p.h / 2.0, cz = p.z + p.d / 2.0;
        boolean positive = p.face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        for(int i = 0; i * 2 < length; ++i) {
            double s = i * 2, e = Math.min(length, s + 2);
            double from = positive ? s : length - e, to = positive ? e : length - s;
            boolean turned = (i & 1) == 1;
            double a = turned ? 1.4 : 0.6, b = turned ? 0.6 : 1.4;
            switch(axis) {
                case Y -> box(ms, buffer, light, cx - a / 2, p.y + from, cz - b / 2, cx + a / 2, p.y + to, cz + b / 2, color, 1, FixturePart.Material.METAL);
                case X -> box(ms, buffer, light, p.x + from, cy - a / 2, cz - b / 2, p.x + to, cy + a / 2, cz + b / 2, color, 1, FixturePart.Material.METAL);
                default -> box(ms, buffer, light, cx - a / 2, cy - b / 2, p.z + from, cx + a / 2, cy + b / 2, p.z + to, color, 1, FixturePart.Material.METAL);
            }
        }
    }

    /** The holder as a box, and the bulb standing out of its face. */
    private static void socket(PoseStack ms, MultiBufferSource buffer, int light, int worldLight, FixturePart p, int color, boolean lit, BulbDrawer bulbs) {
        box(ms, buffer, light, p.x, p.y, p.z, p.x + p.w, p.y + p.h, p.z + p.d, color, 1, p.material);
        if(bulbs == null)
            return;
        double cx = p.x + p.w / 2.0, cy = p.y + p.h / 2.0, cz = p.z + p.d / 2.0;
        ms.pushPose();
        // To the face's centre, then turn so the bulb's +y points along the face.
        switch(p.face) {
            case DOWN -> { ms.translate(at(cx), at(p.y), at(cz)); ms.mulPose(Axis.XP.rotationDegrees(180)); }
            case UP -> ms.translate(at(cx), at(p.y + p.h), at(cz));
            case NORTH -> { ms.translate(at(cx), at(cy), at(p.z)); ms.mulPose(Axis.XP.rotationDegrees(-90)); }
            case SOUTH -> { ms.translate(at(cx), at(cy), at(p.z + p.d)); ms.mulPose(Axis.XP.rotationDegrees(90)); }
            case WEST -> { ms.translate(at(p.x), at(cy), at(cz)); ms.mulPose(Axis.ZP.rotationDegrees(90)); }
            case EAST -> { ms.translate(at(p.x + p.w), at(cy), at(cz)); ms.mulPose(Axis.ZP.rotationDegrees(-90)); }
        }
        ms.scale(BULB_SCALE, BULB_SCALE, BULB_SCALE);
        ms.translate(-0.5, 0, -0.5);
        bulbs.draw(ms, buffer, worldLight, lit);
        ms.popPose();
    }

    /**
     * An eight-sided tube along the part's face axis, its cross-section the ellipse that fills the
     * other two dimensions: facets shaded by how much they face up, metal more strongly.
     */
    private static void cylinder(PoseStack ms, MultiBufferSource buffer, int light, FixturePart p, int color, float alpha) {
        VertexConsumer c = buffer.getBuffer(alpha < 1 ? RenderType.entityTranslucent(WHITE) : RenderType.entitySolid(WHITE));
        var pose = ms.last();
        int a = (int) (alpha * 255);
        var axis = p.face.getAxis();
        double cx = p.x + p.w / 2.0, cy = p.y + p.h / 2.0, cz = p.z + p.d / 2.0;
        double ru, rv, n0, n1;
        switch(axis) {
            case X -> { ru = p.h / 2.0; rv = p.d / 2.0; n0 = p.x; n1 = p.x + p.w; }
            case Y -> { ru = p.w / 2.0; rv = p.d / 2.0; n0 = p.y; n1 = p.y + p.h; }
            default -> { ru = p.w / 2.0; rv = p.h / 2.0; n0 = p.z; n1 = p.z + p.d; }
        }
        var up = new org.joml.Vector3f(axis == Direction.Axis.X ? 1 : 0, axis == Direction.Axis.Y ? 1 : 0, axis == Direction.Axis.Z ? 1 : 0);
        var centre0 = ring(axis, cx, cy, cz, 0, 0, n0);
        var centre1 = ring(axis, cx, cy, cz, 0, 0, n1);
        int segments = 8;
        for(int i = 0; i < segments; ++i) {
            double t0 = (i + 0.5) / segments * Math.PI * 2, t1 = (i + 1.5) / segments * Math.PI * 2, tm = (t0 + t1) / 2;
            var normal = dir(axis, Math.cos(tm) / ru, Math.sin(tm) / rv).normalize();   // an ellipse's normal, as a direction
            var p0 = ring(axis, cx, cy, cz, Math.cos(t0) * ru, Math.sin(t0) * rv, n0);
            var p1 = ring(axis, cx, cy, cz, Math.cos(t1) * ru, Math.sin(t1) * rv, n0);
            // Wind the facet the way box() winds a face: along "right" = normal x up, then up.
            var right = new org.joml.Vector3f(normal).cross(up);
            if(new org.joml.Vector3f(p1).sub(p0).dot(right) < 0) { var s = p0; p0 = p1; p1 = s; }
            var q0 = new org.joml.Vector3f(p0).add(new org.joml.Vector3f(up).mul((float) (n1 - n0) / 16));
            var q1 = new org.joml.Vector3f(p1).add(new org.joml.Vector3f(up).mul((float) (n1 - n0) / 16));
            float f = p.material == FixturePart.Material.METAL ? 0.85f + 0.35f * normal.y : 0.9f + 0.1f * normal.y;
            int rgb = shade(color, Math.max(0.7f, f));
            // Both windings: a tube is seen from every side and nothing of it is ever culled away.
            quad(pose, c, light, rgb, a, p0.x, p0.y, p0.z, q0.x, q0.y, q0.z, q1.x, q1.y, q1.z, p1.x, p1.y, p1.z, normal.x, normal.y, normal.z);
            quad(pose, c, light, rgb, a, p1.x, p1.y, p1.z, q1.x, q1.y, q1.z, q0.x, q0.y, q0.z, p0.x, p0.y, p0.z, -normal.x, -normal.y, -normal.z);
            // The two caps, one fan triangle per facet, as degenerate quads, wound to face outward.
            cap(pose, c, light, shade(color, p.material == FixturePart.Material.METAL ? 1.2f : 1), a, centre1, q0, q1, up);
            cap(pose, c, light, shade(color, p.material == FixturePart.Material.METAL ? 0.6f : 0.8f), a, centre0, p0, p1, new org.joml.Vector3f(up).negate());
        }
    }

    /** A direction across the tube's section: (u, v) in the two axes that are not the tube's. */
    private static org.joml.Vector3f dir(Direction.Axis axis, double u, double v) {
        return switch(axis) {
            case X -> new org.joml.Vector3f(0, (float) u, (float) v);
            case Y -> new org.joml.Vector3f((float) u, 0, (float) v);
            case Z -> new org.joml.Vector3f((float) u, (float) v, 0);
        };
    }

    /** A point of the tube in pose units: (u, v) across the section and n along the axis, in design pixels. */
    private static org.joml.Vector3f ring(Direction.Axis axis, double cx, double cy, double cz, double u, double v, double n) {
        return switch(axis) {
            case X -> new org.joml.Vector3f((float) at(n), (float) at(cy + u), (float) at(cz + v));
            case Y -> new org.joml.Vector3f((float) at(cx + u), (float) at(n), (float) at(cz + v));
            case Z -> new org.joml.Vector3f((float) at(cx + u), (float) at(cy + v), (float) at(n));
        };
    }

    private static void cap(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, int a, org.joml.Vector3f centre, org.joml.Vector3f p0, org.joml.Vector3f p1, org.joml.Vector3f normal) {
        var e0 = new org.joml.Vector3f(p0).sub(centre);
        var e1 = new org.joml.Vector3f(p1).sub(centre);
        if(e0.cross(e1).dot(normal) < 0) { var s = p0; p0 = p1; p1 = s; }
        quad(pose, c, light, rgb, a, centre.x, centre.y, centre.z, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, p1.x, p1.y, p1.z, normal.x, normal.y, normal.z);
        quad(pose, c, light, rgb, a, centre.x, centre.y, centre.z, p1.x, p1.y, p1.z, p0.x, p0.y, p0.z, p0.x, p0.y, p0.z, -normal.x, -normal.y, -normal.z);
    }

    // ---- boxes ----

    private static int shade(int rgb, float f) {
        int r = (int) Math.min(255, (rgb >> 16 & 0xFF) * f), g = (int) Math.min(255, (rgb >> 8 & 0xFF) * f), b = (int) Math.min(255, (rgb & 0xFF) * f);
        return r << 16 | g << 8 | b;
    }

    private static int blend(int a, int b, float t) {
        int r = (int) ((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
        int g = (int) ((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    /** A box in design pixels, the pose at the anchor block's corner (pixel 16). Metal gets a brighter top, glass alpha. */
    public static void box(PoseStack ms, MultiBufferSource buffer, int light, double x1, double y1, double z1, double x2, double y2, double z2, int rgb, float alpha, FixturePart.Material material) {
        VertexConsumer c = buffer.getBuffer(alpha < 1 ? RenderType.entityTranslucent(WHITE) : RenderType.entitySolid(WHITE));
        float ax = (float) ((x1 - 16) / 16), ay = (float) ((y1 - 16) / 16), az = (float) ((z1 - 16) / 16);
        float bx = (float) ((x2 - 16) / 16), by = (float) ((y2 - 16) / 16), bz = (float) ((z2 - 16) / 16);
        var pose = ms.last();
        int side = material == FixturePart.Material.METAL ? shade(rgb, 0.85f) : rgb;
        int top = material == FixturePart.Material.METAL ? shade(rgb, 1.2f) : rgb;
        int bottom = shade(rgb, material == FixturePart.Material.METAL ? 0.6f : 0.8f);
        int a = (int) (alpha * 255);
        quad(pose, c, light, side, a, ax, ay, az, ax, by, az, bx, by, az, bx, ay, az, 0, 0, -1);
        quad(pose, c, light, side, a, bx, ay, bz, bx, by, bz, ax, by, bz, ax, ay, bz, 0, 0, 1);
        quad(pose, c, light, side, a, ax, ay, bz, ax, by, bz, ax, by, az, ax, ay, az, -1, 0, 0);
        quad(pose, c, light, side, a, bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz, 1, 0, 0);
        quad(pose, c, light, top, a, ax, by, az, ax, by, bz, bx, by, bz, bx, by, az, 0, 1, 0);
        quad(pose, c, light, bottom, a, ax, ay, bz, ax, ay, az, bx, ay, az, bx, ay, bz, 0, -1, 0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer c, int light, int rgb, int a,
                             float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4,
                             float nx, float ny, float nz) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        vertex(pose, c, light, r, g, b, a, x1, y1, z1, 0, 0, nx, ny, nz);
        vertex(pose, c, light, r, g, b, a, x2, y2, z2, 0, 1, nx, ny, nz);
        vertex(pose, c, light, r, g, b, a, x3, y3, z3, 1, 1, nx, ny, nz);
        vertex(pose, c, light, r, g, b, a, x4, y4, z4, 1, 0, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, int light, int r, int g, int b, int a, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        c.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
