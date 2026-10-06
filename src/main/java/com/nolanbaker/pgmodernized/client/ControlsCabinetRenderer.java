package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock;
import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.CELLS;

/**
 * Draws the door's devices, on the head and a block lower per extension, in the block's north
 * frame turned to its facing; the devices themselves are {@link DeviceDraw}'s.
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
            DeviceDraw.device(ms, buffer, consumer, light, device, be.deviceState(cell), be.colorOf(cell), be.isPowered(), be.backlitState(cell), be.labelOf(cell),
                    ControlsCabinetBlock.CELL_W, c.x, c.y - 16 * (cell / CELLS), DOOR);
        }
        ms.popPose();
    }
}
