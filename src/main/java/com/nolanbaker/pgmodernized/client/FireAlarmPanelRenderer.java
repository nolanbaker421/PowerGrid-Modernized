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
        ms.popPose();
    }

    private static void led(PoseStack ms, MultiBufferSource buffer, int light, double x, int rgb, double z) {
        int lit = rgb == DARK ? light : LightTexture.FULL_BRIGHT;
        DeviceDraw.box(ms, buffer, lit, x - 0.5, LIGHT_Y - 0.5, z - 0.2, x + 0.5, LIGHT_Y + 0.5, z, rgb);
    }
}
