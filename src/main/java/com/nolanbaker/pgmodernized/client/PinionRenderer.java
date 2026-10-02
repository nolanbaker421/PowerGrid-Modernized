package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nolanbaker.pgmodernized.rack.PinionBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Create's kinetic renderer steps aside whenever Flywheel is active, trusting a visual to draw
 * the block. On a Sable body that visual never shows up, so the pinion draws its spinning cog
 * here regardless, the same way Create's renderer would without Flywheel.
 */
public class PinionRenderer extends KineticBlockEntityRenderer<PinionBlockEntity> {
    public PinionRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(PinionBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = getRenderedBlockState(be);
        var consumer = buffer.getBuffer(getRenderType(be, state));
        renderRotatingBuffer(be, getRotatedModel(be, state), ms, consumer, light);
    }
}
