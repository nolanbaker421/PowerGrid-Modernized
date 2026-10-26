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
 * breaker model scaled into its space (a multi-pole breaker into the spaces it spans, under one
 * handle), and right-column breakers are rolled 180 degrees so their handles still point at the
 * centre bus when on.
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
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();

        ms.pushPose();
        // Same rotation the blockstate applies to the panel model (y = 90 for east, and so on).
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        if(be.hasCover())
            cover(be, ms, buffer, light);
        if(be.coverClosed()) {
            ms.popPose();
            return;
        }
        for(int slot = BreakerPanelBlockEntity.MAIN; slot < be.slotCount(); ++slot) {
            var breaker = be.breaker(slot);
            if(!breaker.installed() || breaker.isCovered())
                continue;
            var box = PanelLayout.breakerBox(spec, slot, breaker.rows());
            boolean roll = slot >= 0 && PanelLayout.rightColumn(slot);
            if(breaker.isBlank()) {
                draw(ms, buffer, renderer, BreakerPanelModels.get(BreakerPanelModels.BLANK), box, roll, light, overlay);
                continue;
            }
            draw(ms, buffer, renderer, BreakerPanelModels.get(breaker.state()), box, roll, light, overlay);
            if(breaker.locked())
                draw(ms, buffer, renderer, BreakerPanelModels.get(BreakerPanelModels.LOCK), box, roll, light, overlay);
        }
        ms.popPose();
    }

    private static final int DOOR = 0x7A7E86, DOOR_EDGE = 0x5A5E66, HANDLE = 0xC8CCD0;

    /** The door over the front of the head and its extensions, in the north frame: closed flat on the front, or swung open on a hinge at the viewer's left. */
    private static void cover(BreakerPanelBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        double bottom = 1 - 16 * (be.sections() - 1);
        double front = PanelLayout.FRONT_Z;
        ms.pushPose();
        if(be.coverOpen()) {
            ms.translate(15 / 16.0, 0, front / 16);
            ms.mulPose(Axis.YP.rotationDegrees(-110));
            ms.translate(-15 / 16.0, 0, -front / 16);
        }
        DeviceDraw.box(ms, buffer, light, 1, bottom, front - 0.6, 15, 15, front, DOOR);
        DeviceDraw.box(ms, buffer, light, 1, bottom, front - 0.7, 15, bottom + 0.4, front, DOOR_EDGE);
        DeviceDraw.box(ms, buffer, light, 1, 14.6, front - 0.7, 15, 15, front, DOOR_EDGE);
        DeviceDraw.box(ms, buffer, light, 2.2, 6.5, front - 1.2, 3.2, 9.5, front - 0.6, HANDLE);
        if(!be.coverLabel().isEmpty())
            DeviceDraw.nameplate(ms, buffer, light, be.coverLabel(), 8, 12.3, 11, front - 0.6);
        if(be.coverOpen())
            directory(be, ms, buffer, light, bottom, front);
        ms.popPose();
    }

    private static final int CARD = 0xF0EEE6, INK = 0x202020;

    /**
     * The circuit directory on the inside of an open door: every space's number with the label of
     * the breaker in it, odd on the left and even on the right as the spaces are, the main at the top.
     */
    private static void directory(BreakerPanelBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light, double bottom, double front) {
        var font = Minecraft.getInstance().font;
        float s = 1 / 160f;
        double top = 14, pitch = 1.1, column = 5.2;
        int columnChars = (int) (column / 16 / s);
        DeviceDraw.box(ms, buffer, light, 2.3, bottom + 0.8, front, 13.7, top + 0.3, front + 0.1, CARD);
        var main = be.breaker(BreakerPanelBlockEntity.MAIN);
        String title = net.minecraft.network.chat.Component.translatable("powergrid.gui.breaker_panel.directory_main").getString()
                + (main.label().isEmpty() ? "" : " " + main.label());
        DeviceDraw.text(ms, buffer, light, font.plainSubstrByWidth(title, (int) (11 / 16 / s)), 8, top - 0.7, front + 0.15, s, INK, true, 0.5);
        int rows = (int) ((top - 1.6 - (bottom + 0.8)) / pitch);
        for(int slot = 0; slot < be.slotCount(); ++slot) {
            int row = slot / 2;
            if(row >= rows)
                break;
            // Read from inside the door, left is low x.
            double x = PanelLayout.rightColumn(slot) ? 8.2 : 2.6;
            double y = top - 2.0 - row * pitch;
            String line = (slot + 1) + " " + be.effective(slot).label();
            DeviceDraw.text(ms, buffer, light, font.plainSubstrByWidth(line, columnChars), x, y, front + 0.15, s, INK, true, 0);
        }
    }

    /**
     * The cutout buffer is fetched here, right before use: it is not one of the source's fixed buffers,
     * so solid boxes drawn in between (a panel's door, a cell's bus bars) would end it and the next quad
     * would crash with "Not building!".
     */
    private static void draw(PoseStack ms, MultiBufferSource buffer, ModelBlockRenderer renderer, BakedModel model, AABB box,
                             boolean roll, int light, int overlay) {
        var consumer = buffer.getBuffer(RenderType.cutout());
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
