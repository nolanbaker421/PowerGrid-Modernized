package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.conduit.CeilingConduitBoxBlock;
import com.nolanbaker.pgmodernized.conduit.ConduitBoxBlock;
import com.nolanbaker.pgmodernized.conduit.ConduitBoxBlockEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitBoxGeometry;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the bulb in a light socket cover: Power Grid's own bulb models, so a standard bulb, an LV
 * bulb, a grow lamp or a neon tube look like themselves, and the lit model on top while powered.
 * The bulb model is made for a 16 px fixture; it is scaled to the socket's 4 px bulb space and sat
 * in the lamp holder, in the box's frame.
 */
public class ConduitBoxRenderer extends SafeBlockEntityRenderer<ConduitBoxBlockEntity> {
    private static final float SCALE = 0.4f;

    public ConduitBoxRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(ConduitBoxBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var bulb = be.bulb();
        var state = be.getBlockState();
        if(bulb == null || !(state.getBlock() instanceof ConduitBoxBlock) || !ConduitBoxBlock.cover(state).isLight())
            return;
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        ms.pushPose();
        // Into the box's frame: the same turns the blockstate gives the static model.
        boolean onTile = state.getBlock() instanceof CeilingConduitBoxBlock;
        if(onTile)
            ms.translate(0, CeilingConduitBoxBlock.LIFT / 16.0, 0);   // the box sits on the tile, lifted by its thickness
        ms.translate(0.5, 0.5, 0.5);
        if(onTile) {
            ms.mulPose(Axis.XP.rotationDegrees(90));   // the on-tile frame: the box's front points up
        } else {
            switch(state.getValue(ConduitBoxBlock.FACING)) {
                case SOUTH -> ms.mulPose(Axis.YP.rotationDegrees(180));
                case EAST -> ms.mulPose(Axis.YP.rotationDegrees(-90));
                case WEST -> ms.mulPose(Axis.YP.rotationDegrees(90));
                case UP -> ms.mulPose(Axis.XP.rotationDegrees(90));
                case DOWN -> ms.mulPose(Axis.XP.rotationDegrees(-90));
                default -> {}
            }
        }
        ms.translate(-0.5, -0.5, -0.5);
        // The bulb space in front of the holder, pointing out of the box (north in the frame).
        var space = ConduitBoxGeometry.BULB;
        double cx = (space.minX + space.maxX) / 32, cy = (space.minY + space.maxY) / 32, back = space.maxZ / 16;
        ms.translate(cx, cy, back);
        ms.mulPose(Axis.XP.rotationDegrees(-90));   // the bulb model stands up (+y) from its base at y = 0; ours points out of the box (-z)
        ms.scale(SCALE, SCALE, SCALE);
        ms.translate(-0.5, 0, -0.5);                 // base on the holder's face, bulb out into its space
        draw(ms, buffer, renderer, bulb.getModel(), light, overlay, RenderType.cutout());
        if(be.getPowerLevel() > 0 && !bulb.isBurned())
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

    @Override
    public boolean shouldRenderOffScreen(ConduitBoxBlockEntity be) {
        return false;
    }
}
