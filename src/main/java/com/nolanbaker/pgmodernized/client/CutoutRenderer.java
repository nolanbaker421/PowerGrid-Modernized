package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.pole.CutoutBlock;
import com.nolanbaker.pgmodernized.device.pole.CutoutBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * The fuse tubes of a cutout: standing closed between the lugs, or dropped out on the hinge at
 * the foot, hanging below the load lug the way a blown or opened cutout hangs on a pole. An empty
 * holder has no tube. A blown tube is scorched; an arcing gap glows.
 */
public class CutoutRenderer extends SafeBlockEntityRenderer<CutoutBlockEntity> {
    private static final int TUBE = 0xC8C4BC, TUBE_CAP = 0x8A8580, SCORCHED = 0x4A4440, ARC = 0xFFE890;
    private static final float DROP_DEGREES = -112;

    public CutoutRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(CutoutBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof CutoutBlock block))
            return;
        var cls = block.cutoutClass();
        var facing = CutoutBlock.facing(state);
        int poles = be.poles();
        var xs = CutoutBlock.centres(poles);
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        for(int k = 0; k < poles; ++k) {
            if(!be.hasLink(k))
                continue;
            double x = xs[k];
            double length = cls.top() - 3;
            int body = be.isBlown(k) ? SCORCHED : TUBE;
            int lit = be.isArcing(k) ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light;
            ms.pushPose();
            // The hinge, at the foot of the tube just above the load lug.
            ms.translate(x / 16, 2.5 / 16, 5.25 / 16);
            if(be.isOpen(k))
                ms.mulPose(Axis.XP.rotationDegrees(DROP_DEGREES));
            DeviceDraw.box(ms, buffer, lit, -0.75, 0, -0.75, 0.75, length, 0.75, be.isArcing(k) ? ARC : body);
            DeviceDraw.box(ms, buffer, lit, -0.9, length - 0.6, -0.9, 0.9, length, 0.9, TUBE_CAP);
            DeviceDraw.box(ms, buffer, lit, -0.9, 0, -0.9, 0.9, 0.6, 0.9, TUBE_CAP);
            ms.popPose();
        }
        ms.popPose();
    }
}
