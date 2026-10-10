package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.hmi.HmiBlock;
import com.nolanbaker.pgmodernized.device.hmi.HmiBlockEntity;
import com.nolanbaker.pgmodernized.device.hmi.HmiLayout;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the panel's widgets on its screen face, in the block's north frame turned to its facing,
 * full bright like any lit screen: labels, values, lamps, buttons, bars and setpoints, each on
 * its grid cells. The viewer's left is +x in the north frame, so text is drawn mirrored in x.
 */
public class HmiRenderer extends SafeBlockEntityRenderer<HmiBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final double FACE = HmiBlock.FACE_Z, C = HmiBlock.CELL;
    private static final double FLAT_Z1 = FACE - 0.10, FLAT_Z2 = FACE - 0.02, TEXT_Z = FACE - 0.14;
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    public HmiRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(HmiBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof HmiBlock))
            return;
        if(!be.isOrigin())
            return;
        var facing = HmiBlock.facing(state);
        var font = Minecraft.getInstance().font;
        var layout = be.layout();
        // The screen spans this panel and those to the viewer's right (-x here) and above it.
        double top0 = layout.rows() * C;
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        for(int i = 0; i < layout.widgets.size(); ++i) {
            var w = layout.widgets.get(i);
            float v = be.value(i);
            boolean on = !Float.isNaN(v) && v != 0;
            int rgb = HmiLayout.COLORS[Math.floorMod(w.color, HmiLayout.COLORS.length)];
            // The widget's rectangle in the north frame: left is the larger x.
            double left = 16 - w.col * C, right = 16 - (w.col + w.w) * C;
            double top = top0 - w.row * C, bottom = top0 - (w.row + w.h) * C;
            double midY = (top + bottom) / 2;
            switch(w.kind) {
                case LABEL -> text(ms, buffer, font, w.text, left - 0.3, midY, right - left + 0.6, w.h, rgb, Align.LEFT);
                case VALUE -> {
                    var value = be.text(i).isEmpty() ? HmiLayout.format(v, w.decimals) : be.text(i);
                    if(!w.text.isEmpty())
                        text(ms, buffer, font, w.text, left - 0.3, midY, (left - right) * 0.55, w.h, 0xA0A0A0, Align.LEFT);
                    text(ms, buffer, font, value, right + 0.3, midY, (left - right) * (w.text.isEmpty() ? 1 : 0.45), w.h, rgb, Align.RIGHT);
                }
                case INDICATOR -> {
                    double lamp = C * 0.6;
                    box(ms, buffer, left - 0.35, midY - lamp / 2, FLAT_Z1, left - 0.35 - lamp, midY + lamp / 2, FLAT_Z2, on ? rgb : dim(rgb, 0.3));
                    text(ms, buffer, font, w.text, left - 0.5 - lamp, midY, (left - right) - lamp - 0.8, w.h, 0xE0E0E0, Align.LEFT);
                }
                case BUTTON -> {
                    box(ms, buffer, left - 0.25, bottom + 0.25, FLAT_Z1, right + 0.25, top - 0.25, on ? FLAT_Z2 : FLAT_Z2 - 0.04, on ? rgb : dim(rgb, 0.55));
                    text(ms, buffer, font, w.text, (left + right) / 2, midY, (left - right) - 0.8, w.h, on ? 0x101010 : 0xF0F0F0, Align.CENTER);
                }
                case BAR -> {
                    box(ms, buffer, left - 0.25, bottom + 0.25, FLAT_Z1, right + 0.25, top - 0.25, FLAT_Z1 + 0.03, 0x202428);
                    double span = w.max - w.min;
                    double fraction = Float.isNaN(v) || span <= 0 ? 0 : Math.max(0, Math.min(1, (v - w.min) / span));
                    if(fraction > 0)
                        box(ms, buffer, left - 0.35, bottom + 0.35, FLAT_Z1 + 0.03, left - 0.35 - ((left - right) - 0.7) * fraction, top - 0.35, FLAT_Z2, rgb);
                    text(ms, buffer, font, w.text, left - 0.5, midY, (left - right) - 1, w.h, 0xF0F0F0, Align.LEFT);
                }
                case LINE -> {
                    boolean lit = w.tag.isEmpty() || on;
                    int colour = lit ? rgb : dim(rgb, 0.35);
                    double t = 0.35;
                    if(w.w >= w.h)
                        box(ms, buffer, left, midY - t / 2, FLAT_Z1, right, midY + t / 2, FLAT_Z2, colour);
                    else
                        box(ms, buffer, (left + right) / 2 - t / 2, bottom, FLAT_Z1, (left + right) / 2 + t / 2, top, FLAT_Z2, colour);
                }
                case BOX -> {
                    double t = 0.2;
                    box(ms, buffer, left - 0.1, top - 0.1 - t, FLAT_Z1, right + 0.1, top - 0.1, FLAT_Z2, rgb);
                    box(ms, buffer, left - 0.1, bottom + 0.1, FLAT_Z1, right + 0.1, bottom + 0.1 + t, FLAT_Z2, rgb);
                    box(ms, buffer, left - 0.1, bottom + 0.1, FLAT_Z1, left - 0.1 - t, top - 0.1, FLAT_Z2, rgb);
                    box(ms, buffer, right + 0.1 + t, bottom + 0.1, FLAT_Z1, right + 0.1, top - 0.1, FLAT_Z2, rgb);
                    text(ms, buffer, font, w.text, left - 0.5, top - C * 0.5, (left - right) - 1, 1, rgb, Align.LEFT);
                }
                case GAUGE -> {
                    double cx = (left + right) / 2, cy = bottom + C * 0.9;
                    double radius = Math.max(C * 0.5, Math.min((left - right) / 2 - 0.4, top - cy - 0.3));
                    double span = w.max - w.min;
                    int steps = 24;
                    double dot = Math.max(0.12, radius * 0.1);
                    for(int s = 0; s <= steps; ++s) {
                        double t = (double) s / steps;
                        double angle = Math.PI - Math.PI * t;
                        // The viewer's left is +x, so min sits at +x and the arc runs over the top to -x at max.
                        double px = cx - radius * Math.cos(angle), py = cy + radius * Math.sin(angle);
                        box(ms, buffer, px - dot, py - dot, FLAT_Z1, px + dot, py + dot, FLAT_Z2, w.bandColor(w.band(w.min + span * t)));
                    }
                    if(!Float.isNaN(v) && span > 0) {
                        double t = Math.max(0, Math.min(1, (v - w.min) / span));
                        double angle = Math.PI - Math.PI * t;
                        int needle = 8;
                        for(int s = 0; s <= needle; ++s) {
                            double d = (radius - 0.3) * s / needle;
                            double px = cx - d * Math.cos(angle), py = cy + d * Math.sin(angle);
                            box(ms, buffer, px - 0.08, py - 0.08, FLAT_Z2, px + 0.08, py + 0.08, FLAT_Z2 + 0.03, 0xF0F0F0);
                        }
                    }
                    var shown = (w.text.isEmpty() ? "" : w.text + " ") + HmiLayout.format(v, w.decimals);
                    text(ms, buffer, font, shown, cx, bottom + C * 0.4, (left - right) - 0.4, 1, on ? rgb : 0xA0A0A0, Align.CENTER);
                }
                case SETPOINT -> {
                    double key = Math.min(C * 0.9, (left - right) / 4);
                    box(ms, buffer, left - 0.25, bottom + 0.25, FLAT_Z1, left - 0.25 - key, top - 0.25, FLAT_Z2, 0x383C42);
                    box(ms, buffer, right + 0.25 + key, bottom + 0.25, FLAT_Z1, right + 0.25, top - 0.25, FLAT_Z2, 0x383C42);
                    text(ms, buffer, font, "-", left - 0.25 - key / 2, midY, key, w.h, 0xF0F0F0, Align.CENTER);
                    text(ms, buffer, font, "+", right + 0.25 + key / 2, midY, key, w.h, 0xF0F0F0, Align.CENTER);
                    var value = HmiLayout.format(v, w.decimals);
                    var shown = w.text.isEmpty() ? value : w.text + " " + value;
                    text(ms, buffer, font, shown, (left + right) / 2, midY, (left - right) - 2 * key - 0.6, w.h, rgb, Align.CENTER);
                }
            }
        }
        ms.popPose();
    }

    private enum Align { LEFT, CENTER, RIGHT }

    private static int dim(int rgb, double k) {
        int r = (int) ((rgb >> 16 & 0xFF) * k), g = (int) ((rgb >> 8 & 0xFF) * k), b = (int) ((rgb & 0xFF) * k);
        return r << 16 | g << 8 | b;
    }

    /**
     * Text on the face. {@code x} is the anchor in 16ths (the left edge, centre or right edge in
     * the viewer's frame), {@code y} the vertical centre, {@code width} the room in 16ths, and
     * {@code rows} the widget's height in cells, which sets the size.
     */
    private static void text(PoseStack ms, MultiBufferSource buffer, Font font, String s, double x, double y, double width, int rows, int rgb, Align align) {
        if(s.isEmpty() || width <= 0)
            return;
        double heightBlocks = C * (rows >= 2 ? 1.5 : 0.78) / 16;
        float scale = (float) (heightBlocks / 9);
        int budget = (int) (width / 16 / scale);
        s = font.plainSubstrByWidth(s, Math.max(1, budget));
        int w = font.width(s);
        float dx = switch(align) {
            case LEFT -> 0;
            case CENTER -> -w / 2f;
            case RIGHT -> -w;
        };
        ms.pushPose();
        ms.translate(x / 16, y / 16, TEXT_Z / 16);
        ms.scale(-scale, -scale, scale);
        font.drawInBatch(s, dx, -4.5f, rgb | 0xFF000000, false, ms.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LIGHT);
        ms.popPose();
    }

    /** A box in 16ths of the north frame, every face lit flat in one colour; corners may come in any order. */
    private static void box(PoseStack ms, MultiBufferSource buffer, double x1, double y1, double z1, double x2, double y2, double z2, int rgb) {
        VertexConsumer c = buffer.getBuffer(RenderType.entitySolid(WHITE));
        float ax = (float) (Math.min(x1, x2) / 16), ay = (float) (Math.min(y1, y2) / 16), az = (float) (Math.min(z1, z2) / 16);
        float bx = (float) (Math.max(x1, x2) / 16), by = (float) (Math.max(y1, y2) / 16), bz = (float) (Math.max(z1, z2) / 16);
        var pose = ms.last();
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        quad(pose, c, r, g, b, ax, ay, az, ax, by, az, bx, by, az, bx, ay, az, 0, 0, -1);
        quad(pose, c, r, g, b, bx, ay, bz, bx, by, bz, ax, by, bz, ax, ay, bz, 0, 0, 1);
        quad(pose, c, r, g, b, ax, ay, bz, ax, by, bz, ax, by, az, ax, ay, az, -1, 0, 0);
        quad(pose, c, r, g, b, bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz, 1, 0, 0);
        quad(pose, c, r, g, b, ax, by, az, ax, by, bz, bx, by, bz, bx, by, az, 0, 1, 0);
        quad(pose, c, r, g, b, ax, ay, bz, ax, ay, az, bx, ay, az, bx, ay, bz, 0, -1, 0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer c, int r, int g, int b,
                             float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4,
                             float nx, float ny, float nz) {
        vertex(pose, c, r, g, b, x1, y1, z1, 0, 0, nx, ny, nz);
        vertex(pose, c, r, g, b, x2, y2, z2, 0, 1, nx, ny, nz);
        vertex(pose, c, r, g, b, x3, y3, z3, 1, 1, nx, ny, nz);
        vertex(pose, c, r, g, b, x4, y4, z4, 1, 0, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, int r, int g, int b, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        c.addVertex(pose, x, y, z).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(pose, nx, ny, nz);
    }
}
