package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.breaker.SwitchgearBlock;
import com.nolanbaker.pgmodernized.device.breaker.SwitchgearBlockEntity;
import com.nolanbaker.pgmodernized.device.breaker.SwitchgearLayout;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Draws the section's breaker into the door, with the same models the panel uses. */
public class SwitchgearRenderer extends SafeBlockEntityRenderer<SwitchgearBlockEntity> {
    public SwitchgearRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(SwitchgearBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof SwitchgearBlock))
            return;
        var breaker = be.breaker();
        if(!breaker.installed())
            return;
        var facing = SwitchgearBlock.facing(state);
        var consumer = buffer.getBuffer(RenderType.cutout());
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        var box = SwitchgearLayout.BREAKER;
        draw(ms, consumer, renderer, breaker.isBlank() ? BreakerPanelModels.get(BreakerPanelModels.BLANK) : BreakerPanelModels.get(breaker.state()), box, light, overlay);
        if(breaker.locked() && !breaker.isBlank())
            draw(ms, consumer, renderer, BreakerPanelModels.get(BreakerPanelModels.LOCK), box, light, overlay);
        ms.popPose();
    }

    private static void draw(PoseStack ms, com.mojang.blaze3d.vertex.VertexConsumer consumer, net.minecraft.client.renderer.block.ModelBlockRenderer renderer,
                             BakedModel model, net.minecraft.world.phys.AABB box, int light, int overlay) {
        float w = (float) box.getXsize(), h = (float) box.getYsize(), d = (float) box.getZsize();
        ms.pushPose();
        ms.translate(box.minX / 16, box.minY / 16, box.minZ / 16);
        ms.scale(w / 16, h / 16, d / 16);
        renderer.renderModel(ms.last(), consumer, null, model, 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
        ms.popPose();
    }
}
