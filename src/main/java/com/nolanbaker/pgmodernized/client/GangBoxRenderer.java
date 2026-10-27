package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nolanbaker.pgmodernized.conduit.gang.GangBoxBlock;
import com.nolanbaker.pgmodernized.conduit.gang.GangBoxBlockEntity;
import com.nolanbaker.pgmodernized.conduit.gang.GangFrame;
import com.nolanbaker.pgmodernized.conduit.gang.GangLayout;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Draws the devices in a gang box: a toggle up or down, a button out or in, a receptacle face, a
 * blank. Drawn in the floor frame through {@link GangFrame}, so they sit on the plate however the
 * box is mounted and turned.
 */
public class GangBoxRenderer extends SafeBlockEntityRenderer<GangBoxBlockEntity> {
    private static final int TOGGLE = 0xE0E0E0, TOGGLE_EDGE = 0x9A9A9A, BUTTON = 0xC83030, BUTTON_RING = 0x404040;
    private static final int FACE = 0xE8E4DA, SLOT = 0x2A2A2A, BLANK = 0xD8D8D8;
    private static final int ALARM_RED = 0xB31B1B, HANDLE = 0xF0F0EC, LENS_OFF = 0xC8C8C0, LENS_ON = 0xFFFFF0;

    public GangBoxRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(GangBoxBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof GangBoxBlock box))
            return;
        int gangs = box.gangs();
        ms.pushPose();
        ms.mulPose(GangFrame.pose(state));
        double top = GangLayout.PLATE_Y1;
        for(int slot = 0; slot < gangs; ++slot) {
            var device = be.device(slot);
            if(device == null)
                continue;
            double cx = GangLayout.slotCentreX(gangs, slot);
            boolean on = be.isOn(slot);
            switch(device) {
                case SWITCH, THREE_WAY, FOUR_WAY -> {
                    // A toggle: leans south when off, north when on; the 4-way shows crossed as on.
                    double z1 = on ? 6 : 8.5, z2 = on ? 7.5 : 10;
                    DeviceDraw.box(ms, buffer, light, cx - 1.5, top, 6, cx + 1.5, top + 0.4, 10, TOGGLE_EDGE);
                    DeviceDraw.box(ms, buffer, light, cx - 1, top + 0.4, z1, cx + 1, top + 2.2, z2, TOGGLE);
                }
                case BUTTON -> {
                    DeviceDraw.box(ms, buffer, light, cx - 1.5, top, 6.5, cx + 1.5, top + 0.4, 9.5, BUTTON_RING);
                    DeviceDraw.box(ms, buffer, light, cx - 1, top + 0.4, 7, cx + 1, top + (on ? 0.9 : 1.6), 9, BUTTON);
                }
                case RECEPTACLE -> {
                    DeviceDraw.box(ms, buffer, light, cx - 1.5, top, 5.75, cx + 1.5, top + 0.8, 10.25, FACE);
                    for(double z : new double[] {6.5, 8.75}) {
                        DeviceDraw.box(ms, buffer, light, cx - 0.9, top + 0.8, z, cx - 0.5, top + 0.85, z + 1.1, SLOT);
                        DeviceDraw.box(ms, buffer, light, cx + 0.5, top + 0.8, z, cx + 0.9, top + 0.85, z + 1.1, SLOT);
                        DeviceDraw.box(ms, buffer, light, cx - 0.25, top + 0.8, z + 1.3, cx + 0.25, top + 0.85, z + 1.8, SLOT);
                    }
                }
                case PULL_STATION -> {
                    // A red station with a white handle across it: up when set, down when pulled.
                    DeviceDraw.box(ms, buffer, light, cx - 1.5, top, 5.75, cx + 1.5, top + 1.2, 10.25, ALARM_RED);
                    double z = on ? 8.6 : 6.6;
                    DeviceDraw.box(ms, buffer, light, cx - 1.1, top + 1.2, z, cx + 1.1, top + 1.6, z + 0.8, HANDLE);
                }
                case HORN_STROBE -> {
                    // A red horn with a lens above its grille; the lens flashes full bright while strobing.
                    DeviceDraw.box(ms, buffer, light, cx - 1.5, top, 5.75, cx + 1.5, top + 1.4, 10.25, ALARM_RED);
                    for(double z = 8.2; z < 10; z += 0.6)
                        DeviceDraw.box(ms, buffer, light, cx - 1.1, top + 1.4, z, cx + 1.1, top + 1.5, z + 0.3, 0x601010);
                    boolean strobing = be.hornVolts(slot) >= com.nolanbaker.pgmodernized.conduit.ConduitBoxBlockEntity.STROBE_VOLTS;
                    boolean flash = strobing && be.getLevel() != null && (be.getLevel().getGameTime() / 5) % 4 == 0;
                    DeviceDraw.box(ms, buffer, flash ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light,
                            cx - 1, top + 1.4, 6.2, cx + 1, top + (flash ? 2.0 : 1.8), 7.6, flash ? LENS_ON : LENS_OFF);
                }
                case BLANK -> DeviceDraw.box(ms, buffer, light, cx - 1.6, top, 5.75, cx + 1.6, top + 0.3, 10.25, BLANK);
            }
        }
        ms.popPose();
    }
}
