package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock;
import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.PanelDevice;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.CELLS;

/**
 * Draws the door's devices as little coloured boxes standing proud of the door, in the block's
 * north frame turned to its facing: a yellow-collared red mushroom for an E-stop, a rocker, a
 * green cap, a knob with a position bar, a bezelled lens that glows when lit, and a dark window
 * with the display's digits.
 */
public class ControlsCabinetRenderer extends SafeBlockEntityRenderer<ControlsCabinetBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final double DOOR = ControlsCabinetBlock.DOOR_Z;

    public ControlsCabinetRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(ControlsCabinetBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof ControlsCabinetBlock))
            return;
        var facing = ControlsCabinetBlock.facing(state);
        var consumer = buffer.getBuffer(RenderType.entitySolid(WHITE));
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        for(int cell = 0; cell < be.cells(); ++cell) {
            var device = be.device(cell);
            if(device == null)
                continue;
            var c = ControlsCabinetBlock.cellCenter(cell % CELLS);
            double x = c.x, y = c.y - 16 * (cell / CELLS);
            int stateValue = be.deviceState(cell);
            switch(device) {
                case E_STOP -> {
                    box(ms, consumer, light, x - 1.4, y - 1.4, DOOR - 0.4, x + 1.4, y + 1.4, DOOR, 0xE8D020);
                    double head = stateValue != 0 ? DOOR - 0.7 : DOOR - 1.1;
                    box(ms, consumer, light, x - 0.9, y - 0.9, head, x + 0.9, y + 0.9, DOOR - 0.4, 0xD02020);
                }
                case TOGGLE -> {
                    box(ms, consumer, light, x - 1.3, y - 1.3, DOOR - 0.3, x + 1.3, y + 1.3, DOOR, 0x303236);
                    double off = stateValue != 0 ? 0.5 : -0.5;
                    box(ms, consumer, light, x - 0.8, y + off - 0.5, DOOR - 0.7, x + 0.8, y + off + 0.5, DOOR - 0.3, stateValue != 0 ? 0x40D050 : 0x808488);
                }
                case MOMENTARY -> {
                    box(ms, consumer, light, x - 1.2, y - 1.2, DOOR - 0.3, x + 1.2, y + 1.2, DOOR, 0x202224);
                    double cap = stateValue != 0 ? DOOR - 0.5 : DOOR - 0.9;
                    box(ms, consumer, light, x - 0.8, y - 0.8, cap, x + 0.8, y + 0.8, DOOR - 0.3, 0x30C040);
                }
                case SELECTOR -> {
                    box(ms, consumer, light, x - 1.2, y - 1.2, DOOR - 0.3, x + 1.2, y + 1.2, DOOR, 0x202224);
                    box(ms, consumer, light, x - 0.9, y - 0.9, DOOR - 0.6, x + 0.9, y + 0.9, DOOR - 0.3, 0x404448);
                    double bar = (1 - stateValue) * 0.6;   // left position is the viewer's left, which is +x here
                    box(ms, consumer, light, x + bar - 0.25, y - 0.8, DOOR - 0.9, x + bar + 0.25, y + 0.8, DOOR - 0.6, 0xF0F0F0);
                }
                case LED -> {
                    boolean lit = stateValue != 0;
                    int rgb = ControlsCabinetBlockEntity.LED_COLORS[Math.floorMod(be.colorOf(cell), ControlsCabinetBlockEntity.LED_COLORS.length)];
                    box(ms, consumer, light, x - 1.0, y - 1.0, DOOR - 0.3, x + 1.0, y + 1.0, DOOR, 0x202224);
                    box(ms, consumer, lit ? LightTexture.FULL_BRIGHT : light, x - 0.6, y - 0.6, DOOR - 0.6, x + 0.6, y + 0.6, DOOR - 0.3, lit ? rgb : dim(rgb));
                }
                case DIAL -> {
                    box(ms, consumer, light, x - 1.2, y - 1.2, DOOR - 0.3, x + 1.2, y + 1.2, DOOR, 0x202224);
                    box(ms, consumer, light, x - 0.9, y - 0.9, DOOR - 0.7, x + 0.9, y + 0.9, DOOR - 0.3, 0x404448);
                    // The mark sweeps 270 degrees, from the viewer's lower left at 0 to lower right at 100.
                    float angle = 135 - 270 * stateValue / 100f;
                    ms.pushPose();
                    ms.translate(x / 16, y / 16, 0);
                    ms.mulPose(Axis.ZP.rotationDegrees(angle));
                    box(ms, consumer, light, -0.2, 0.25, DOOR - 1.0, 0.2, 0.9, DOOR - 0.7, 0xF0F0F0);
                    ms.popPose();
                }
                case DISPLAY -> {
                    box(ms, consumer, light, x - 1.45, y - 1.0, DOOR - 0.3, x + 1.45, y + 1.0, DOOR, 0x101214);
                    if(be.isPowered())
                        digits(ms, buffer, x, y, stateValue);
                }
            }
        }
        ms.popPose();
    }

    private static int dim(int rgb) {
        int r = (rgb >> 16 & 0xFF) * 3 / 10, g = (rgb >> 8 & 0xFF) * 3 / 10, b = (rgb & 0xFF) * 3 / 10;
        return r << 16 | g << 8 | b;
    }

    private static void digits(PoseStack ms, MultiBufferSource buffer, double x, double y, int value) {
        Font font = Minecraft.getInstance().font;
        String text = String.format("%4d", value);
        ms.pushPose();
        ms.translate(x / 16, y / 16, (DOOR - 0.35) / 16);
        float s = 1 / 128f;
        ms.scale(-s, -s, s);
        font.drawInBatch(text, -font.width(text) / 2f, -4.5f, 0x40FF60, false, ms.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        ms.popPose();
    }

    /** A box in 16ths of the north frame, every face lit flat in one colour. */
    private static void box(PoseStack ms, VertexConsumer c, int light, double x1, double y1, double z1, double x2, double y2, double z2, int rgb) {
        float ax = (float) (x1 / 16), ay = (float) (y1 / 16), az = (float) (z1 / 16);
        float bx = (float) (x2 / 16), by = (float) (y2 / 16), bz = (float) (z2 / 16);
        var pose = ms.last();
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        // north (-z), the face the viewer sees
        quad(pose, c, light, r, g, b, ax, ay, az, ax, by, az, bx, by, az, bx, ay, az, 0, 0, -1);
        // south (+z)
        quad(pose, c, light, r, g, b, bx, ay, bz, bx, by, bz, ax, by, bz, ax, ay, bz, 0, 0, 1);
        // west (-x)
        quad(pose, c, light, r, g, b, ax, ay, bz, ax, by, bz, ax, by, az, ax, ay, az, -1, 0, 0);
        // east (+x)
        quad(pose, c, light, r, g, b, bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz, 1, 0, 0);
        // up (+y)
        quad(pose, c, light, r, g, b, ax, by, az, ax, by, bz, bx, by, bz, bx, by, az, 0, 1, 0);
        // down (-y)
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
