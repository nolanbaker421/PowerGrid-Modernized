package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlock;
import com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * The three lights on the panel's door: power steady green, alarm blinking red and steady once
 * silenced, trouble blinking yellow. All dark with no power. Drawn in the north frame a hair
 * proud of the door, where the texture has their holes.
 */
public class FireAlarmPanelRenderer extends SafeBlockEntityRenderer<FireAlarmPanelBlockEntity> {
    private static final int GREEN = 0x30E050, RED = 0xFF2020, YELLOW = 0xFFC020, DARK = 0x202020;
    /** Light positions in 16ths of the north frame, viewer's left to right: power, alarm, trouble. */
    private static final double[] LIGHT_X = {11, 8, 5};
    private static final double LIGHT_Y = 12.5;

    public FireAlarmPanelRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(FireAlarmPanelBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof FireAlarmPanelBlock) || be.getLevel() == null)
            return;
        var facing = FireAlarmPanelBlock.facing(state);
        boolean blink = (be.getLevel().getGameTime() / 10) % 2 == 0;
        boolean powered = be.powered();
        int power = powered ? GREEN : DARK;
        int alarm = powered && be.state() == FireAlarmPanelBlockEntity.State.ALARM && (blink || be.silenced()) ? RED : DARK;
        int trouble = powered && be.state() == FireAlarmPanelBlockEntity.State.TROUBLE && blink ? YELLOW : DARK;
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        double z = FireAlarmPanelBlock.FRONT_Z;
        led(ms, buffer, light, LIGHT_X[0], power, z);
        led(ms, buffer, light, LIGHT_X[1], alarm, z);
        led(ms, buffer, light, LIGHT_X[2], trouble, z);
        display(be, ms, buffer, powered, z);
        ms.popPose();
    }

    /** The two-line display under the lights: the state, and the first device that matters, as a panel prints it. */
    private static void display(FireAlarmPanelBlockEntity be, PoseStack ms, MultiBufferSource buffer, boolean powered, double z) {
        if(!powered)
            return;
        float s = 1 / 192f;
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int chars = (int) (7.5 / 16 / s);
        String line1, line2 = "";
        int color;
        switch(be.state()) {
            case ALARM -> {
                var first = be.first(st -> st == com.nolanbaker.pgmodernized.conduit.fire.FireDevice.Status.ALARM);
                int more = be.count(st -> st == com.nolanbaker.pgmodernized.conduit.fire.FireDevice.Status.ALARM) - 1;
                line1 = be.silenced() ? "ALARM  SILENCED" : "ALARM";
                line2 = first == null ? (be.drill() ? "DRILL" : "LOOP CURRENT") : first.display() + (more > 0 ? " +" + more : "");
                color = 0xFF4040;
            }
            case TROUBLE -> {
                var first = be.first(com.nolanbaker.pgmodernized.conduit.fire.FireDevice.Status::trouble);
                line1 = "TROUBLE";
                line2 = first == null ? "LOOP OPEN" : first.display() + " " + (first.status() == com.nolanbaker.pgmodernized.conduit.fire.FireDevice.Status.MISSING ? "MISSING" : "UNWIRED");
                color = 0xFFC040;
            }
            default -> {
                line1 = "NORMAL";
                line2 = be.devices().size() + " DEVICES";
                color = 0x40E060;
            }
        }
        DeviceDraw.text(ms, buffer, LightTexture.FULL_BRIGHT, font.plainSubstrByWidth(line1, chars), 8, 10.6, z - 0.05, s, color, false, 0.5);
        DeviceDraw.text(ms, buffer, LightTexture.FULL_BRIGHT, font.plainSubstrByWidth(line2, chars), 8, 9.3, z - 0.05, s, color, false, 0.5);
    }

    private static void led(PoseStack ms, MultiBufferSource buffer, int light, double x, int rgb, double z) {
        int lit = rgb == DARK ? light : LightTexture.FULL_BRIGHT;
        DeviceDraw.box(ms, buffer, lit, x - 0.5, LIGHT_Y - 0.5, z - 0.2, x + 0.5, LIGHT_Y + 0.5, z, rgb);
    }
}
