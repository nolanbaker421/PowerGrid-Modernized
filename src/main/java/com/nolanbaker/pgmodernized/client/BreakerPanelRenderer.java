package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlock;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlockEntity;
import com.nolanbaker.pgmodernized.device.breaker.PanelLayout;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the installed breakers. The panel model itself is static; each breaker is the unit-cube
 * breaker model scaled into its space, and right-column breakers are rolled 180 degrees so their
 * handles still point at the centre bus when on.
 */
public class BreakerPanelRenderer extends SafeBlockEntityRenderer<BreakerPanelBlockEntity> {
    public BreakerPanelRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(BreakerPanelBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof BreakerPanelBlock))
            return;
        var spec = be.spec();
        var facing = BreakerPanelBlock.facing(state);
        var consumer = buffer.getBuffer(RenderType.cutout());
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();

        ms.pushPose();
        // Same rotation the blockstate applies to the panel model (y = 90 for east, and so on).
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);

        for(int slot = BreakerPanelBlockEntity.MAIN; slot < spec.slots(); ++slot) {
            var breaker = be.breaker(slot);
            if(!breaker.installed())
                continue;
            var box = PanelLayout.breakerBox(spec, slot);
            boolean roll = slot >= 0 && PanelLayout.rightColumn(slot);
            if(breaker.isBlank()) {
                draw(ms, consumer, renderer, BreakerPanelModels.get(BreakerPanelModels.BLANK), box, roll, light, overlay);
                continue;
            }
            draw(ms, consumer, renderer, BreakerPanelModels.get(breaker.state()), box, roll, light, overlay);
            if(breaker.locked())
                draw(ms, consumer, renderer, BreakerPanelModels.get(BreakerPanelModels.LOCK), box, roll, light, overlay);
        }
        ms.popPose();
    }

    private static void draw(PoseStack ms, VertexConsumer consumer, ModelBlockRenderer renderer, BakedModel model, AABB box,
                             boolean roll, int light, int overlay) {
        float w = (float) box.getXsize(), h = (float) box.getYsize(), d = (float) box.getZsize();
        ms.pushPose();
        ms.translate(box.minX / 16, box.minY / 16, box.minZ / 16);
        if(roll) {
            ms.translate(w / 32, h / 32, d / 32);
            ms.mulPose(Axis.ZP.rotationDegrees(180));
            ms.translate(-w / 32, -h / 32, -d / 32);
        }
        ms.scale(w / 16, h / 16, d / 16);
        renderer.renderModel(ms.last(), consumer, null, model, 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
        ms.popPose();
    }
}
