package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.pole.SwitchContactBlock;
import com.nolanbaker.pgmodernized.device.pole.SwitchContactBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** The blade of a switch contact: lying from the hinge at the front back into the line contact when closed, raised when open. */
public class SwitchContactRenderer extends SafeBlockEntityRenderer<SwitchContactBlockEntity> {
    private static final int BLADE = 0xB87333, ARC = 0xFFE890;
    private static final float OPEN_DEGREES = -70;

    public SwitchContactRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(SwitchContactBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof SwitchContactBlock))
            return;
        var facing = SwitchContactBlock.facing(state);
        int lit = be.isArcing() ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light;
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        // The hinge: on the front post, at the blade's height.
        ms.translate(0.5, 5.5 / 16, 4.5 / 16);
        if(!be.isClosed())
            ms.mulPose(Axis.XP.rotationDegrees(OPEN_DEGREES));
        DeviceDraw.box(ms, buffer, lit, -0.6, -0.4, 0, 0.6, 0.4, 7, be.isArcing() ? ARC : BLADE);
        ms.popPose();
    }
}
