package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.light.StripLightBlock;
import com.nolanbaker.pgmodernized.device.light.StripLightBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * The two fluorescent tubes under a strip light's channel, drawn whenever a bulb is in it, whatever
 * the bulb: lit white and full-bright when the bulb is, chalky when it is dark, grey when it has
 * burned out. An empty channel shows its holders alone.
 */
public class StripLightRenderer extends SafeBlockEntityRenderer<StripLightBlockEntity> {
    private static final int LIT = 0xF8F8F0, DARK = 0xD8DCD8, BURNED = 0x8E9298, CAP = 0xC8CCC8;

    public StripLightRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(StripLightBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var bulb = be.bulb();
        var state = be.getBlockState();
        if(bulb == null || !(state.getBlock() instanceof StripLightBlock))
            return;
        boolean lit = be.lit();
        int color = lit ? LIT : bulb.isBurned() ? BURNED : DARK;
        int tubeLight = lit ? LightTexture.FULL_BRIGHT : light;
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(StripLightBlock.facing(state).toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        for(double x : new double[] {5.5, 8.5}) {
            DeviceDraw.box(ms, buffer, tubeLight, x, 10, 1.5, x + 2, 12, 14.5, color);
            DeviceDraw.box(ms, buffer, light, x - 0.25, 9.75, 0.5, x + 2.25, 12, 1.5, CAP);
            DeviceDraw.box(ms, buffer, light, x - 0.25, 9.75, 14.5, x + 2.25, 12, 15.5, CAP);
        }
        ms.popPose();
    }
}
