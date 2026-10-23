package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.gear.GearCellBlock;
import com.nolanbaker.pgmodernized.device.gear.GearCellBlockEntity;
import com.nolanbaker.pgmodernized.device.gear.GearLayout;
import com.nolanbaker.pgmodernized.device.gear.GearSide;
import com.nolanbaker.pgmodernized.device.gear.GearSlot;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws what is in each slot of a switchgear cell, in the cell's north frame: copper bars that run
 * from a slot's centre to every side something joins them on, a silver neutral bar and a green
 * ground bar the same, lug studs, the panel's breaker models, fuse barrels, a switch with its
 * handle, a contactor with its coil light, a starter (breaker over contactor, with the overload
 * relay's reset button) and a metering unit's display.
 */
public class GearCellRenderer extends SafeBlockEntityRenderer<GearCellBlockEntity> {
    private static final int COPPER = 0xB87333, COPPER_DARK = 0x8E5A28, SILVER = 0xC8CCD0, GREEN = 0x3AAA3A, STUD = 0xB8BCC0;
    private static final int DARK = 0x33363C, BODY = 0x50545C, RED = 0xC82222, GOLD = 0xD0B020;
    private static final int FUSE = 0xD8D4C8, FUSE_BLOWN = 0x3A3A3A, LED_ON = 0x40E060, LED_OFF = 0x702020;
    private static final int DISPLAY_ON = 0x60D890, DISPLAY_OFF = 0x1E3028;
    /** Where bar k runs across a slot, from the slot's top or viewer's-left edge. */
    private static final double[] BAR_AT = {0.5, 1.4, 2.3, 3.2, 4.1};
    private static final double BAR = 0.6, Z_BACK = 1.0, Z_BAR = 1.8;

    public GearCellRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(GearCellBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof GearCellBlock))
            return;
        var facing = GearCellBlock.facing(state);
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        for(int s = 0; s < GearLayout.SLOTS; ++s) {
            var slot = be.slot(s);
            var kind = slot.kind();
            if(kind == null)
                continue;
            var r = GearLayout.slotRect(s);
            switch(kind) {
                case BUS -> bars(be, s, slot, r, ms, buffer, light, 0, 3, COPPER);
                case NEUTRAL_BAR -> bars(be, s, slot, r, ms, buffer, light, 3, 4, SILVER);
                case GROUND_BAR -> bars(be, s, slot, r, ms, buffer, light, 4, 5, GREEN);
                case LUGS -> lugs(be, s, slot, r, ms, buffer, light);
                case BREAKER -> breaker(slot, r, ms, buffer, light, overlay);
                case FUSE -> fuses(slot, r, ms, buffer, light);
                case SWITCH -> switchUnit(slot, r, ms, buffer, light);
                case CONTACTOR -> contactor(slot, r, ms, buffer, light);
                case STARTER -> starter(slot, r, ms, buffer, light, overlay);
                case METER -> meter(slot, r, ms, buffer, light);
            }
        }
        ms.popPose();
    }

    /** Bar k crosses the slot at x = right edge minus its offset (the viewer's left is +x) and y = top minus its offset. */
    private static double barX(double[] r, int k) {
        return r[2] - BAR_AT[k] - BAR;
    }

    private static double barY(double[] r, int k) {
        return r[3] - BAR_AT[k] - BAR;
    }

    private static int conductorRgb(int k) {
        return k == 3 ? SILVER : k == GearLayout.GROUND ? GREEN : COPPER;
    }

    private static void bars(GearCellBlockEntity be, int s, GearSlot slot, double[] r, PoseStack ms, MultiBufferSource buffer, int light, int from, int to, int rgb) {
        for(int k = from; k < to; ++k) {
            double x = barX(r, k), y = barY(r, k);
            DeviceDraw.box(ms, buffer, light, x, y, Z_BACK, x + BAR, y + BAR, Z_BAR, rgb);
            for(var side : GearSide.values()) {
                if(!slot.carries(side, k) || !be.partnerCarriesAcross(s, side, k))
                    continue;
                switch(side) {
                    case UP -> DeviceDraw.box(ms, buffer, light, x, y + BAR, Z_BACK, x + BAR, r[3], Z_BAR, rgb);
                    case DOWN -> DeviceDraw.box(ms, buffer, light, x, r[1], Z_BACK, x + BAR, y, Z_BAR, rgb);
                    case LEFT -> DeviceDraw.box(ms, buffer, light, x + BAR, y, Z_BACK, r[2], y + BAR, Z_BAR, rgb);
                    case RIGHT -> DeviceDraw.box(ms, buffer, light, r[0], y, Z_BACK, x, y + BAR, Z_BAR, rgb);
                }
            }
        }
    }

    private static void lugs(GearCellBlockEntity be, int s, GearSlot slot, double[] r, PoseStack ms, MultiBufferSource buffer, int light) {
        // A terminal block across the bottom of the slot, bars dropping into it from the sides that feed it.
        DeviceDraw.box(ms, buffer, light, r[0] + 0.3, r[1] + 0.2, Z_BACK, r[2] - 0.3, r[1] + 1.6, Z_BAR + 0.2, DARK);
        for(int k = 0; k < GearLayout.CONDUCTORS; ++k) {
            var box = GearLayout.lugBox(s, k);
            DeviceDraw.box(ms, buffer, light, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, STUD);
            int rgb = conductorRgb(k);
            double x = barX(r, k);
            if(slot.carries(GearSide.UP, k) && be.partnerCarriesAcross(s, GearSide.UP, k))
                DeviceDraw.box(ms, buffer, light, x, r[1] + 1.6, Z_BACK, x + BAR, r[3], Z_BAR, rgb);
            double y = barY(r, k);
            if(slot.carries(GearSide.LEFT, k) && be.partnerCarriesAcross(s, GearSide.LEFT, k))
                DeviceDraw.box(ms, buffer, light, x + BAR, y, Z_BACK, r[2], y + BAR, Z_BAR, rgb);
            if(slot.carries(GearSide.RIGHT, k) && be.partnerCarriesAcross(s, GearSide.RIGHT, k))
                DeviceDraw.box(ms, buffer, light, r[0], y, Z_BACK, x, y + BAR, Z_BAR, rgb);
            if(slot.carries(GearSide.UP, k) || slot.carries(GearSide.LEFT, k) || slot.carries(GearSide.RIGHT, k))
                DeviceDraw.box(ms, buffer, light, x, y, Z_BACK, x + BAR, y + BAR, Z_BAR, rgb);
        }
    }

    private static void breaker(GearSlot slot, double[] r, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var box = new AABB(r[0] + 0.4, r[1] + 0.4, 0.6, r[2] - 0.4, r[3] - 0.4, GearLayout.FRONT_Z);
        ms.pushPose();
        turn(slot, r, ms);
        breakerModel(slot, box, ms, buffer, light, overlay);
        ms.popPose();
    }

    private static void breakerModel(GearSlot slot, AABB box, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var consumer = buffer.getBuffer(RenderType.cutout());
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        draw(ms, consumer, renderer, BreakerPanelModels.get(slot.breaker.state()), box, light, overlay);
        if(slot.breaker.locked())
            draw(ms, consumer, renderer, BreakerPanelModels.get(BreakerPanelModels.LOCK), box, light, overlay);
    }

    private static void fuses(GearSlot slot, double[] r, PoseStack ms, MultiBufferSource buffer, int light) {
        ms.pushPose();
        turn(slot, r, ms);
        DeviceDraw.box(ms, buffer, light, r[0] + 0.3, r[1] + 0.3, Z_BACK, r[2] - 0.3, r[3] - 0.3, Z_BACK + 0.4, DARK);
        int body = slot.blown() ? FUSE_BLOWN : FUSE;
        for(int k = 0; k < 3; ++k) {
            double x = barX(r, k);
            DeviceDraw.box(ms, buffer, light, x - 0.1, r[1] + 0.6, Z_BACK + 0.4, x + BAR + 0.1, r[1] + 1.4, Z_BAR + 0.1, COPPER_DARK);
            DeviceDraw.box(ms, buffer, light, x - 0.1, r[3] - 1.4, Z_BACK + 0.4, x + BAR + 0.1, r[3] - 0.6, Z_BAR + 0.1, COPPER_DARK);
            DeviceDraw.box(ms, buffer, light, x, r[1] + 1.4, Z_BACK + 0.4, x + BAR, r[3] - 1.4, Z_BAR, body);
        }
        ms.popPose();
    }

    private static void switchUnit(GearSlot slot, double[] r, PoseStack ms, MultiBufferSource buffer, int light) {
        ms.pushPose();
        turn(slot, r, ms);
        DeviceDraw.box(ms, buffer, light, r[0] + 0.4, r[1] + 0.4, Z_BACK, r[2] - 0.4, r[3] - 0.4, Z_BAR, DARK);
        // The handle on the viewer's right (-x): up is closed.
        double y = slot.isOn() ? r[3] - 2.4 : r[1] + 0.6;
        DeviceDraw.box(ms, buffer, light, r[0] + 0.6, y, 0.6, r[0] + 1.4, y + 1.8, Z_BACK + 0.2, RED);
        if(slot.locked())
            DeviceDraw.box(ms, buffer, light, r[0] + 0.5, r[1] + 0.4, 0.5, r[0] + 1.5, r[1] + 1.2, 1.0, GOLD);
        ms.popPose();
    }

    private static void contactor(GearSlot slot, double[] r, PoseStack ms, MultiBufferSource buffer, int light) {
        ms.pushPose();
        turn(slot, r, ms);
        DeviceDraw.box(ms, buffer, light, r[0] + 0.4, r[1] + 0.4, Z_BACK, r[2] - 0.4, r[3] - 0.4, Z_BAR, BODY);
        DeviceDraw.box(ms, buffer, light, r[0] + 0.8, r[1] + 0.8, Z_BACK + 0.2, r[2] - 0.8, r[3] - 2.0, Z_BAR + 0.1, DARK);
        DeviceDraw.box(ms, buffer, light, r[2] - 1.6, r[3] - 1.4, Z_BAR, r[2] - 0.8, r[3] - 0.6, Z_BAR + 0.3, slot.closed() ? LED_ON : LED_OFF);
        ms.popPose();
    }

    /** The breaker over the contactor, the overload relay's reset button beside the coil light: red while it has tripped. */
    private static void starter(GearSlot slot, double[] r, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        ms.pushPose();
        turn(slot, r, ms);
        var box = new AABB(r[0] + 0.4, r[1] + 2.3, 0.6, r[2] - 0.4, r[3] - 0.3, GearLayout.FRONT_Z);
        breakerModel(slot, box, ms, buffer, light, overlay);
        DeviceDraw.box(ms, buffer, light, r[0] + 0.4, r[1] + 0.3, Z_BACK, r[2] - 0.4, r[1] + 2.1, Z_BAR, BODY);
        DeviceDraw.box(ms, buffer, light, r[0] + 0.7, r[1] + 0.6, Z_BACK + 0.2, r[2] - 2.2, r[1] + 1.8, Z_BAR + 0.1, DARK);
        DeviceDraw.box(ms, buffer, light, r[2] - 1.3, r[1] + 1.3, Z_BAR, r[2] - 0.7, r[1] + 1.9, Z_BAR + 0.3, slot.coil() && slot.breaker.closed() ? LED_ON : LED_OFF);
        DeviceDraw.box(ms, buffer, light, r[2] - 2.0, r[1] + 0.5, Z_BAR, r[2] - 0.7, r[1] + 1.1, Z_BAR + (slot.overload() ? 0.5 : 0.25), slot.overload() ? RED : GOLD);
        ms.popPose();
    }

    /** A dark face with a display that lights while any phase shows a voltage. */
    private static void meter(GearSlot slot, double[] r, PoseStack ms, MultiBufferSource buffer, int light) {
        ms.pushPose();
        turn(slot, r, ms);
        DeviceDraw.box(ms, buffer, light, r[0] + 0.4, r[1] + 0.4, Z_BACK, r[2] - 0.4, r[3] - 0.4, Z_BAR, DARK);
        boolean live = slot.volts(0) > 1 || slot.volts(1) > 1 || slot.volts(2) > 1;
        DeviceDraw.box(ms, buffer, light, r[0] + 0.8, r[3] - 2.4, Z_BAR, r[2] - 0.8, r[3] - 0.8, Z_BAR + 0.15, live ? DISPLAY_ON : DISPLAY_OFF);
        for(int k = 0; k < 3; ++k) {
            double x = barX(r, k);
            DeviceDraw.box(ms, buffer, light, x, r[1] + 0.7, Z_BAR, x + BAR, r[1] + 1.9, Z_BAR + 0.1, COPPER_DARK);
        }
        ms.popPose();
    }

    /** Turns the slot's drawing about its centre by the component's rotation, clockwise as seen from the front. */
    private static void turn(GearSlot slot, double[] r, PoseStack ms) {
        if(slot.rotation() == 0)
            return;
        double cx = (r[0] + r[2]) / 32, cy = (r[1] + r[3]) / 32;
        ms.translate(cx, cy, 0);
        ms.mulPose(Axis.ZP.rotationDegrees(90 * slot.rotation()));
        ms.translate(-cx, -cy, 0);
    }

    private static void draw(PoseStack ms, com.mojang.blaze3d.vertex.VertexConsumer consumer, net.minecraft.client.renderer.block.ModelBlockRenderer renderer,
                             BakedModel model, AABB box, int light, int overlay) {
        float w = (float) box.getXsize(), h = (float) box.getYsize(), d = (float) box.getZsize();
        ms.pushPose();
        ms.translate(box.minX / 16, box.minY / 16, box.minZ / 16);
        ms.scale(w / 16, h / 16, d / 16);
        renderer.renderModel(ms.last(), consumer, null, model, 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
        ms.popPose();
    }
}
