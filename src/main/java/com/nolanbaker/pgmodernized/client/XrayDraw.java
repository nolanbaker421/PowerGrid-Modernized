package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;

import java.util.List;

/** Translucent boxes drawn through whatever is in front of them, on {@link XrayRenderType#XRAY}: what the goggles see inside a wall. */
public final class XrayDraw {
    /** The blue of a sleeve or a concealed run, and its alpha. */
    public static final int CHASE = 0x40E0FF, ALPHA = 0x70;

    private XrayDraw() {}

    public static VertexConsumer buffer(MultiBufferSource buffer) {
        return buffer.getBuffer(XrayRenderType.XRAY);
    }

    public static void box(Matrix4f pose, VertexConsumer c, double x1, double y1, double z1, double x2, double y2, double z2, int rgb, int alpha) {
        float a = (float) x1, b = (float) y1, d = (float) z1, e = (float) x2, f = (float) y2, g = (float) z2;
        int r = rgb >> 16 & 0xFF, gr = rgb >> 8 & 0xFF, bl = rgb & 0xFF;
        quad(pose, c, r, gr, bl, alpha, a, b, d, e, b, d, e, f, d, a, f, d);   // north
        quad(pose, c, r, gr, bl, alpha, a, b, g, e, b, g, e, f, g, a, f, g);   // south
        quad(pose, c, r, gr, bl, alpha, a, b, d, a, b, g, a, f, g, a, f, d);   // west
        quad(pose, c, r, gr, bl, alpha, e, b, d, e, b, g, e, f, g, e, f, d);   // east
        quad(pose, c, r, gr, bl, alpha, a, b, d, e, b, d, e, b, g, a, b, g);   // bottom
        quad(pose, c, r, gr, bl, alpha, a, f, d, e, f, d, e, f, g, a, f, g);   // top
    }

    /** A run's legs as tubes of half-width {@code h}, from {@code start} on, in whatever space the pose is in. */
    public static void legs(Matrix4f pose, VertexConsumer c, Vec3 start, List<BlockWireEntity.Point> legs, double h, int rgb, int alpha) {
        var p = start;
        for(var leg : legs) {
            var q = p.add(leg.vector());
            box(pose, c, Math.min(p.x, q.x) - h, Math.min(p.y, q.y) - h, Math.min(p.z, q.z) - h,
                    Math.max(p.x, q.x) + h, Math.max(p.y, q.y) + h, Math.max(p.z, q.z) + h, rgb, alpha);
            p = q;
        }
    }

    private static void quad(Matrix4f pose, VertexConsumer c, int r, int g, int b, int a, float... v) {
        for(int i = 0; i < 12; i += 3)
            c.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(r, g, b, a);
    }
}
