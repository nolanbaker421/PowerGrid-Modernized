package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
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

    public static void draw(PoseStack ms, MultiBufferSource buffer, int light, FixtureDesign design, boolean lit, BulbDrawer bulbs, int selected) {
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
            case DOWN -> { ms.translate(cx / 16, p.y / 16.0, cz / 16); ms.mulPose(Axis.XP.rotationDegrees(180)); }
            case UP -> ms.translate(cx / 16, (p.y + p.h) / 16.0, cz / 16);
            case NORTH -> { ms.translate(cx / 16, cy / 16, p.z / 16.0); ms.mulPose(Axis.XP.rotationDegrees(-90)); }
            case SOUTH -> { ms.translate(cx / 16, cy / 16, (p.z + p.d) / 16.0); ms.mulPose(Axis.XP.rotationDegrees(90)); }
            case WEST -> { ms.translate(p.x / 16.0, cy / 16, cz / 16); ms.mulPose(Axis.ZP.rotationDegrees(90)); }
            case EAST -> { ms.translate((p.x + p.w) / 16.0, cy / 16, cz / 16); ms.mulPose(Axis.ZP.rotationDegrees(-90)); }
        }
        ms.scale(BULB_SCALE, BULB_SCALE, BULB_SCALE);
        ms.translate(-0.5, 0, -0.5);
        bulbs.draw(ms, buffer, worldLight, lit);
        ms.popPose();
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
