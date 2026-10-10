package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.PanelDevice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Draws one panel device as little coloured boxes standing proud of a face, in a block's north
 * frame: a yellow-collared red mushroom for an E-stop, a rocker, a green cap, a knob with a
 * position bar, a bezelled lens that glows when lit, a dial with a mark, and a dark window with
 * digits. Shared by the cabinet door and the control stations.
 */
public final class DeviceDraw {
    private DeviceDraw() {}

    /**
     * @param x      centre of the device in 16ths of the north frame (+x is the viewer's left)
     * @param y      centre in 16ths
     * @param door   the face the device stands on, in 16ths of z; the device is drawn towards -z
     */
    /**
     * @param backlit -1 for a device with no backlight wired, else 0 or 1 for the wired output's state
     * @param label   text drawn under the device, empty for none, clipped to {@code labelWidth} 16ths
     */
    public static void device(PoseStack ms, MultiBufferSource buffer, int light,
                              PanelDevice device, int stateValue, int colorIndex, boolean powered, int backlit, String label, double labelWidth,
                              double x, double y, double door) {
        int rgb = ControlsCabinetBlockEntity.LED_COLORS[Math.floorMod(colorIndex, ControlsCabinetBlockEntity.LED_COLORS.length)];
        int capLight = backlit == 1 ? LightTexture.FULL_BRIGHT : light;
        int capColor = backlit == 0 ? dim(rgb) : rgb;
        if(!label.isEmpty())
            labelText(ms, buffer, label, x, y - 1.95, labelWidth, door);
        switch(device) {
            case E_STOP -> {
                box(ms, buffer, light, x - 1.4, y - 1.4, door - 0.4, x + 1.4, y + 1.4, door, 0xE8D020);
                double head = stateValue != 0 ? door - 0.7 : door - 1.1;
                box(ms, buffer, light, x - 0.9, y - 0.9, head, x + 0.9, y + 0.9, door - 0.4, 0xD02020);
            }
            case TOGGLE -> {
                box(ms, buffer, light, x - 1.3, y - 1.3, door - 0.3, x + 1.3, y + 1.3, door, 0x303236);
                double off = stateValue != 0 ? 0.5 : -0.5;
                box(ms, buffer, capLight, x - 0.8, y + off - 0.5, door - 0.7, x + 0.8, y + off + 0.5, door - 0.3, stateValue != 0 ? capColor : backlit == 1 ? capColor : 0x808488);
            }
            case MOMENTARY -> {
                box(ms, buffer, light, x - 1.2, y - 1.2, door - 0.3, x + 1.2, y + 1.2, door, 0x202224);
                double cap = stateValue != 0 ? door - 0.5 : door - 0.9;
                box(ms, buffer, capLight, x - 0.8, y - 0.8, cap, x + 0.8, y + 0.8, door - 0.3, capColor);
            }
            case UP, DOWN -> {
                box(ms, buffer, light, x - 1.2, y - 1.2, door - 0.3, x + 1.2, y + 1.2, door, 0x202224);
                double cap = stateValue != 0 ? door - 0.5 : door - 0.9;
                box(ms, buffer, capLight, x - 0.8, y - 0.8, cap, x + 0.8, y + 0.8, door - 0.3, backlit < 0 ? 0x303236 : capColor);
                // The arrow: a stem and a head, white, standing just off the cap.
                double tip = device == PanelDevice.UP ? 1 : -1;
                box(ms, buffer, light, x - 0.12, y - 0.45, cap - 0.08, x + 0.12, y + 0.45, cap, 0xF0F0F0);
                box(ms, buffer, light, x - 0.5, y + tip * 0.2 - 0.12, cap - 0.08, x + 0.5, y + tip * 0.2 + 0.12, cap, 0xF0F0F0);
                box(ms, buffer, light, x - 0.3, y + tip * 0.4 - 0.12, cap - 0.08, x + 0.3, y + tip * 0.4 + 0.12, cap, 0xF0F0F0);
            }
            case BUZZER -> {
                boolean on = stateValue != 0 && (System.currentTimeMillis() / 200) % 2 == 0;
                box(ms, buffer, light, x - 1.3, y - 1.3, door - 0.5, x + 1.3, y + 1.3, door, 0x202224);
                box(ms, buffer, light, x - 1.0, y - 1.0, door - 0.7, x + 1.0, y + 1.0, door - 0.5, 0x404448);
                for(int i = -1; i <= 1; ++i)
                    box(ms, buffer, on ? LightTexture.FULL_BRIGHT : light, x - 0.7, y + i * 0.55 - 0.12, door - 0.8, x + 0.7, y + i * 0.55 + 0.12, door - 0.7, on ? 0xF08030 : 0x101214);
            }
            case SELECTOR -> {
                box(ms, buffer, light, x - 1.2, y - 1.2, door - 0.3, x + 1.2, y + 1.2, door, 0x202224);
                box(ms, buffer, light, x - 0.9, y - 0.9, door - 0.6, x + 0.9, y + 0.9, door - 0.3, 0x404448);
                double bar = (1 - stateValue) * 0.6;   // left position is the viewer's left, which is +x here
                box(ms, buffer, light, x + bar - 0.25, y - 0.8, door - 0.9, x + bar + 0.25, y + 0.8, door - 0.6, 0xF0F0F0);
            }
            case LED -> {
                boolean lit = stateValue != 0;
                box(ms, buffer, light, x - 1.0, y - 1.0, door - 0.3, x + 1.0, y + 1.0, door, 0x202224);
                box(ms, buffer, lit ? LightTexture.FULL_BRIGHT : light, x - 0.6, y - 0.6, door - 0.6, x + 0.6, y + 0.6, door - 0.3, lit ? rgb : dim(rgb));
            }
            case DIAL -> {
                box(ms, buffer, light, x - 1.2, y - 1.2, door - 0.3, x + 1.2, y + 1.2, door, 0x202224);
                box(ms, buffer, light, x - 0.9, y - 0.9, door - 0.7, x + 0.9, y + 0.9, door - 0.3, 0x404448);
                // The mark sweeps 270 degrees, from the viewer's lower left at 0 to lower right at 100.
                float angle = 270 * stateValue / 100f - 135;   // +z rotation reads clockwise to the viewer, who looks along +z
                ms.pushPose();
                ms.translate(x / 16, y / 16, 0);
                ms.mulPose(Axis.ZP.rotationDegrees(angle));
                box(ms, buffer, light, -0.2, 0.25, door - 1.0, 0.2, 0.9, door - 0.7, 0xF0F0F0);
                ms.popPose();
            }
            case DISPLAY -> {
                box(ms, buffer, light, x - 1.45, y - 1.0, door - 0.3, x + 1.45, y + 1.0, door, 0x101214);
                if(powered)
                    digits(ms, buffer, x, y, stateValue, door);
            }
        }
    }

    /** A label under a device: tiny white text, centred, clipped to the cell. */
    private static void labelText(PoseStack ms, MultiBufferSource buffer, String label, double x, double y, double width, double door) {
        Font font = Minecraft.getInstance().font;
        float s = 1 / 192f;
        String text = font.plainSubstrByWidth(label, Math.max(1, (int) (width / 16 / s)));
        ms.pushPose();
        ms.translate(x / 16, y / 16, (door - 0.05) / 16);
        ms.scale(-s, -s, s);
        font.drawInBatch(text, -font.width(text) / 2f, -4.5f, 0xF0F0F0, false, ms.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        ms.popPose();
    }

    private static int dim(int rgb) {
        int r = (rgb >> 16 & 0xFF) * 3 / 10, g = (rgb >> 8 & 0xFF) * 3 / 10, b = (rgb & 0xFF) * 3 / 10;
        return r << 16 | g << 8 | b;
    }

    private static void digits(PoseStack ms, MultiBufferSource buffer, double x, double y, int value, double door) {
        Font font = Minecraft.getInstance().font;
        String text = String.format("%4d", value);
        ms.pushPose();
        ms.translate(x / 16, y / 16, (door - 0.35) / 16);
        float s = 1 / 128f;
        ms.scale(-s, -s, s);
        font.drawInBatch(text, -font.width(text) / 2f, -4.5f, 0x40FF60, false, ms.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        ms.popPose();
    }

    private static final net.minecraft.resources.ResourceLocation WHITE = net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /**
     * A box in 16ths of the north frame, every face lit flat in one colour. The buffer is fetched
     * for every box: a text draw in between switches the immediate buffer source to another render
     * type, and a consumer held across that is no longer building.
     */
    public static void box(PoseStack ms, MultiBufferSource buffer, int light, double x1, double y1, double z1, double x2, double y2, double z2, int rgb) {
        VertexConsumer c = buffer.getBuffer(net.minecraft.client.renderer.RenderType.entitySolid(WHITE));
        float ax = (float) (x1 / 16), ay = (float) (y1 / 16), az = (float) (z1 / 16);
        float bx = (float) (x2 / 16), by = (float) (y2 / 16), bz = (float) (z2 / 16);
        var pose = ms.last();
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        quad(pose, c, light, r, g, b, ax, ay, az, ax, by, az, bx, by, az, bx, ay, az, 0, 0, -1);
        quad(pose, c, light, r, g, b, bx, ay, bz, bx, by, bz, ax, by, bz, ax, ay, bz, 0, 0, 1);
        quad(pose, c, light, r, g, b, ax, ay, bz, ax, by, bz, ax, by, az, ax, ay, az, -1, 0, 0);
        quad(pose, c, light, r, g, b, bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz, 1, 0, 0);
        quad(pose, c, light, r, g, b, ax, by, az, ax, by, bz, bx, by, bz, bx, by, az, 0, 1, 0);
        quad(pose, c, light, r, g, b, ax, ay, bz, ax, ay, az, bx, ay, az, bx, ay, bz, 0, -1, 0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer c, int light, int r, int g, int b,
                             float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4,
                             float nx, float ny, float nz) {
        vertex(pose, c, light, r, g, b, x1, y1, z1, 0, 0, nx, ny, nz);
        vertex(pose, c, light, r, g, b, x2, y2, z2, 0, 1, nx, ny, nz);
        vertex(pose, c, light, r, g, b, x3, y3, z3, 1, 1, nx, ny, nz);
        vertex(pose, c, light, r, g, b, x4, y4, z4, 1, 0, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, int light, int r, int g, int b, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        c.addVertex(pose, x, y, z).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
