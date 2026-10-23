package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.radio.AntennaBlock;
import com.nolanbaker.pgmodernized.radio.AntennaBlockEntity;
import com.nolanbaker.pgmodernized.radio.AntennaKind;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Draws a dish or a sector panel on its mount, turned to its aim: the compass azimuth about the
 * mast and the elevation about the pivot, so what you see is where it points. The omni whip is
 * part of the block model and needs nothing here.
 */
public class AntennaRenderer extends SafeBlockEntityRenderer<AntennaBlockEntity> {
    private static final int WHITE = 0xE4E6E8, WHITE_DARK = 0xB8BABE, GREY = 0x5A5E66, PANEL = 0xD8D8D4, PANEL_DARK = 0xA0A09C, DARK = 0x2A2C30;

    public AntennaRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(AntennaBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof AntennaBlock block) || !block.kind().aimed())
            return;
        var pivot = AntennaBlock.pivot(state);
        ms.pushPose();
        ms.translate(pivot.x, pivot.y, pivot.z);
        // Azimuth is a compass bearing: turning east is a negative turn about +y. Elevation tips the north-facing head up.
        ms.mulPose(Axis.YP.rotationDegrees((float) -be.azimuth()));
        ms.mulPose(Axis.XP.rotationDegrees((float) be.elevation()));
        if(block.kind() == AntennaKind.DISH)
            dish(ms, buffer, light);
        else
            sector(ms, buffer, light);
        ms.popPose();
    }

    /** The head faces -z at rest, about the origin at the pivot; coordinates in sixteenths. */
    private static void dish(PoseStack ms, MultiBufferSource buffer, int light) {
        DeviceDraw.box(ms, buffer, light, -1, -1, -1.5, 1, 1, 0.5, DARK);                 // the yoke
        DeviceDraw.box(ms, buffer, light, -7, -6, -2, 7, 6, -1, WHITE);                  // the reflector, a slab
        DeviceDraw.box(ms, buffer, light, -5, -7, -2.5, 5, 7, -1.5, WHITE);               // taller middle to round it off
        DeviceDraw.box(ms, buffer, light, -8, -4, -2.5, 8, 4, -1.5, WHITE_DARK);
        DeviceDraw.box(ms, buffer, light, -0.5, -4, -5, 0.5, -2, -2, GREY);             // the feed arm up from the rim
        DeviceDraw.box(ms, buffer, light, -1, -1, -6, 1, 1, -4.5, GREY);                 // the feed horn at the focus
    }

    private static void sector(PoseStack ms, MultiBufferSource buffer, int light) {
        DeviceDraw.box(ms, buffer, light, -1, -1, -1.5, 1, 1, 0.5, DARK);
        DeviceDraw.box(ms, buffer, light, -5, -7, -3, 5, 7, -1, PANEL);                  // the panel
        DeviceDraw.box(ms, buffer, light, -4, -6, -3.2, 4, 6, -3, PANEL_DARK);           // its radome face
    }
}
