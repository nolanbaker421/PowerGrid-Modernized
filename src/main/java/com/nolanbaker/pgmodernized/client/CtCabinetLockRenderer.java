package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlock;
import com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * The utility's barrel lock on a sealed CT cabinet: a hasp plate low on the door, the viewer's
 * right, with the lock's barrel through it and its brass face showing. Nothing is drawn on an
 * unsealed cabinet.
 */
public class CtCabinetLockRenderer extends SafeBlockEntityRenderer<CtCabinetBlockEntity> {
    private static final int BARREL = 0x4A4C50, HASP = 0x8E9094, FACE = 0xB8A050;

    public CtCabinetLockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(CtCabinetBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if(be.lock() == null)
            return;
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof CtCabinetBlock))
            return;
        var facing = CtCabinetBlock.facing(state);
        ms.pushPose();
        // The turn the blockstate gives the cabinet model: into the north frame, the door's front at z = 10, +x the viewer's left.
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        DeviceDraw.box(ms, buffer, light, 2.8, 4.0, 9.6, 5.4, 6.6, 9.98, HASP);
        DeviceDraw.box(ms, buffer, light, 3.3, 4.5, 8.9, 4.9, 6.1, 9.6, BARREL);
        DeviceDraw.box(ms, buffer, light, 3.7, 4.9, 8.85, 4.5, 5.7, 8.9, FACE);
        ms.popPose();
    }
}
