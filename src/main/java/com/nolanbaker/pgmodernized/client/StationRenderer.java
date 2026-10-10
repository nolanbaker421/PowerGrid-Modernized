package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.controls.StationBlock;
import com.nolanbaker.pgmodernized.device.controls.StationBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Draws a control station's four devices on its face, the same way the cabinet door's are drawn. */
public class StationRenderer extends SafeBlockEntityRenderer<StationBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public StationRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(StationBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof StationBlock))
            return;
        var facing = StationBlock.facing(state);
        var cabinet = be.cabinet();
        boolean powered = cabinet != null && cabinet.isPowered();
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        for(int cell = 0; cell < StationBlockEntity.CELLS; ++cell) {
            var device = be.device(cell);
            if(device == null)
                continue;
            var c = StationBlock.cellCenter(cell);
            DeviceDraw.device(ms, buffer, light, device, be.deviceState(cell), be.colorOf(cell), powered, be.backlitState(cell), be.labelOf(cell), 4, c.x, c.y, StationBlock.FACE_Z);
        }
        ms.popPose();
    }
}
