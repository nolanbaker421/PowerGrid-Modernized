package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nolanbaker.pgmodernized.conduit.ChasewayBlock;
import com.nolanbaker.pgmodernized.conduit.ChasewayBlockEntity;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * What the goggles see in a chased block: every sleeve as a translucent tube through the block,
 * drawn with no depth test so it shows through the wall it is in. Nothing without goggles; the
 * block's own model is the wall it went through.
 */
public class ChasewayRenderer extends SafeBlockEntityRenderer<ChasewayBlockEntity> {
    private static final float HALF = 1.5f / 16, EPS = 0.002f;

    public ChasewayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(ChasewayBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var player = Minecraft.getInstance().player;
        if(player == null || !GogglesItem.isWearingGoggles(player) || be.sleeveBits() == 0)
            return;
        var consumer = XrayDraw.buffer(buffer);
        var pose = ms.last().pose();
        for(int slot = 0; slot < ChasewayBlock.SLOT_COUNT; ++slot) {
            if(!be.hasSleeve(slot))
                continue;
            var c = ChasewayBlock.centre(slot);   // the sleeve's centre line, in pixels, on the two axes it does not run along
            switch(ChasewayBlock.axisOf(slot)) {
                case X -> XrayDraw.box(pose, consumer, -EPS, c.y / 16 - HALF, c.z / 16 - HALF, 1 + EPS, c.y / 16 + HALF, c.z / 16 + HALF, XrayDraw.CHASE, XrayDraw.ALPHA);
                case Y -> XrayDraw.box(pose, consumer, c.x / 16 - HALF, -EPS, c.z / 16 - HALF, c.x / 16 + HALF, 1 + EPS, c.z / 16 + HALF, XrayDraw.CHASE, XrayDraw.ALPHA);
                case Z -> XrayDraw.box(pose, consumer, c.x / 16 - HALF, c.y / 16 - HALF, -EPS, c.x / 16 + HALF, c.y / 16 + HALF, 1 + EPS, XrayDraw.CHASE, XrayDraw.ALPHA);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ChasewayBlockEntity be) {
        return false;
    }
}
