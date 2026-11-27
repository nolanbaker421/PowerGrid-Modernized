package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nolanbaker.pgmodernized.conduit.ChasewayBlock;
import com.nolanbaker.pgmodernized.conduit.ChasewayBlockEntity;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Matrix4f;

/**
 * What the goggles see in a chased block: every sleeve as a translucent tube through the block,
 * drawn with no depth test so it shows through the wall it is in. Nothing without goggles; the
 * block's own model is the wall it went through.
 */
public class ChasewayRenderer extends SafeBlockEntityRenderer<ChasewayBlockEntity> {
    private static final float HALF = 1.5f / 16, EPS = 0.002f;
    private static final int R = 0x40, G = 0xE0, B = 0xFF, A = 0x70;

    public ChasewayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(ChasewayBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var player = Minecraft.getInstance().player;
        if(player == null || !GogglesItem.isWearingGoggles(player) || be.sleeveBits() == 0)
            return;
        var consumer = buffer.getBuffer(XrayRenderType.XRAY);
        var pose = ms.last().pose();
        for(int slot = 0; slot < ChasewayBlock.SLOT_COUNT; ++slot) {
            if(!be.hasSleeve(slot))
                continue;
            var c = ChasewayBlock.centre(slot);   // the sleeve's centre line, in pixels, on the two axes it does not run along
            switch(ChasewayBlock.axisOf(slot)) {
                case X -> tube(pose, consumer, -EPS, c.y / 16 - HALF, c.z / 16 - HALF, 1 + EPS, c.y / 16 + HALF, c.z / 16 + HALF);
                case Y -> tube(pose, consumer, c.x / 16 - HALF, -EPS, c.z / 16 - HALF, c.x / 16 + HALF, 1 + EPS, c.z / 16 + HALF);
                case Z -> tube(pose, consumer, c.x / 16 - HALF, c.y / 16 - HALF, -EPS, c.x / 16 + HALF, c.y / 16 + HALF, 1 + EPS);
            }
        }
    }

    private static void tube(Matrix4f pose, VertexConsumer c, double x1, double y1, double z1, double x2, double y2, double z2) {
        float a = (float) x1, b = (float) y1, d = (float) z1, e = (float) x2, f = (float) y2, g = (float) z2;
        quad(pose, c, a, b, d, e, b, d, e, f, d, a, f, d);   // north
        quad(pose, c, a, b, g, e, b, g, e, f, g, a, f, g);   // south
        quad(pose, c, a, b, d, a, b, g, a, f, g, a, f, d);   // west
        quad(pose, c, e, b, d, e, b, g, e, f, g, e, f, d);   // east
        quad(pose, c, a, b, d, e, b, d, e, b, g, a, b, g);   // bottom
        quad(pose, c, a, f, d, e, f, d, e, f, g, a, f, g);   // top
    }

    private static void quad(Matrix4f pose, VertexConsumer c, float... v) {
        for(int i = 0; i < 12; i += 3)
            c.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(R, G, B, A);
    }

    @Override
    public boolean shouldRenderOffScreen(ChasewayBlockEntity be) {
        return false;
    }
}
