package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.fixture.CustomFixtureBlock;
import com.nolanbaker.pgmodernized.device.fixture.CustomFixtureBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.neoforge.client.model.data.ModelData;

/** A designed fixture in the world: its parts round the anchor block, turned to its facing, with the real bulb in every socket. */
public class CustomFixtureRenderer extends SafeBlockEntityRenderer<CustomFixtureBlockEntity> {
    /** Sign lettering is drawn out to this many blocks; past it the font draw is spent on nothing readable. */
    private static final double TEXT_RANGE = 48;

    public CustomFixtureRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(CustomFixtureBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof CustomFixtureBlock))
            return;
        var facing = CustomFixtureBlock.facing(state);
        var bulb = be.bulb();
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        FixtureDraw.draw(ms, buffer, light, be.design(), be.lit(), bulb == null ? null : (pose, buf, l, lit) -> {
            draw(pose, buf, renderer, bulb.getModel(), l, overlay, RenderType.cutout());
            if(lit)
                draw(pose, buf, renderer, bulb.getLightModel(), LightTexture.FULL_BRIGHT, overlay, RenderType.translucent());
        }, -1, TEXT_RANGE);
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

    @Override
    public boolean shouldRenderOffScreen(CustomFixtureBlockEntity be) {
        return true;
    }
}
