package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.light.StripLightBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.neoforge.client.model.data.ModelData;

/** The bulb in a strip light's socket, hanging from the channel, with its glow model when lit. */
public class StripLightRenderer extends SafeBlockEntityRenderer<StripLightBlockEntity> {
    private static final float SCALE = 0.5f;

    public StripLightRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(StripLightBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var bulb = be.bulb();
        if(bulb == null)
            return;
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        ms.pushPose();
        // The socket is under the channel at the block's centre; the bulb model stands up from its base, so it is turned over to hang.
        ms.translate(0.5, 12 / 16.0, 0.5);
        ms.mulPose(Axis.XP.rotationDegrees(180));
        ms.scale(SCALE, SCALE, SCALE);
        ms.translate(-0.5, 0, -0.5);
        draw(ms, buffer, renderer, bulb.getModel(), light, overlay, RenderType.cutout());
        if(be.lit())
            draw(ms, buffer, renderer, bulb.getLightModel(), LightTexture.FULL_BRIGHT, overlay, RenderType.translucent());
        ms.popPose();
    }

    private static void draw(PoseStack ms, MultiBufferSource buffer, net.minecraft.client.renderer.block.ModelBlockRenderer renderer,
                             PartialModel model, int light, int overlay, RenderType type) {
        if(model == null)
            return;
        var baked = model.get();
        if(baked == null)
            return;
        renderer.renderModel(ms.last(), buffer.getBuffer(type), null, baked, 1, 1, 1, light, overlay, ModelData.EMPTY, type);
    }
}
