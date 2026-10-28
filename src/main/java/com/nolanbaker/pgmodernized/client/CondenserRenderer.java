package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.hvac.CondenserBlock;
import com.nolanbaker.pgmodernized.device.hvac.CondenserBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** The condenser fan under the top grille: three blades on a hub, turning while the compressor runs. */
public class CondenserRenderer extends SafeBlockEntityRenderer<CondenserBlockEntity> {
    private static final int BLADE = 0x2A2C30, HUB = 0x55585C;
    private static final float DEGREES_PER_TICK = 24;

    public CondenserRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(CondenserBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof CondenserBlock))
            return;
        var level = be.getLevel();
        float angle = be.cooling() && level != null ? ((level.getGameTime() % 360) + partialTicks) * DEGREES_PER_TICK : 0;
        ms.pushPose();
        ms.translate(0.5, 13.3 / 16, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(angle));
        DeviceDraw.box(ms, buffer, light, -1.2, -0.3, -1.2, 1.2, 0.9, 1.2, HUB);
        for(int blade = 0; blade < 3; ++blade) {
            ms.pushPose();
            ms.mulPose(Axis.YP.rotationDegrees(blade * 120));
            DeviceDraw.box(ms, buffer, light, 0.8, 0, -0.7, 5.6, 0.4, 0.7, BLADE);
            ms.popPose();
        }
        ms.popPose();
    }
}
