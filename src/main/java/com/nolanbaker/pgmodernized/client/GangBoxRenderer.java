package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nolanbaker.pgmodernized.conduit.gang.GangBoxBlock;
import com.nolanbaker.pgmodernized.conduit.gang.GangBoxBlockEntity;
import com.nolanbaker.pgmodernized.conduit.gang.GangCover;
import com.nolanbaker.pgmodernized.conduit.gang.GangFrame;
import com.nolanbaker.pgmodernized.conduit.gang.GangLayout;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Draws the devices in a gang box: a toggle up or down, a button out or in, a receptacle face, a
 * blank. Drawn in the floor frame through {@link GangFrame}, so they sit on the plate however the
 * box is mounted and turned. A weatherproof cover goes over the device: a hood on a hinge at the
 * top over a receptacle, shut, or swung up while a cord is in; a plate with a lever over a
 * switch, the lever up for on and down for off in place of the toggle.
 */
public class GangBoxRenderer extends SafeBlockEntityRenderer<GangBoxBlockEntity> {
    private static final int TOGGLE = 0xE0E0E0, TOGGLE_EDGE = 0x9A9A9A, BUTTON = 0xC83030, BUTTON_RING = 0x404040;
    private static final int FACE = 0xE8E4DA, SLOT = 0x2A2A2A, BLANK = 0xD8D8D8;
    private static final int ALARM_RED = 0xB31B1B, HANDLE = 0xF0F0EC, LENS_OFF = 0xC8C8C0, LENS_ON = 0xFFFFF0;
    private static final int MODULE = 0x9A9A9A, ANNUN_FACE = 0x303034, ANNUN_SCREEN = 0x101418, SILENCE_KEY = 0xC8A000, RESET_KEY = 0x30A050;
    /** Die-cast grey of a weatherproof cover, its hinge and lever a shade darker, the gasket under it black. */
    private static final int COVER = 0x8E9094, COVER_DARK = 0x66686C, GASKET = 0x1C1C1C;
    private static final int BRASS = 0xC8A040, SHACKLE = 0x50524E;
    /** Text drawn in the north frame turned to lie on the plate's top face, read from the floor frame's south. */
    private static final org.joml.Quaternionf FLAT = new org.joml.Quaternionf().rotateAxis((float) Math.PI, 0f, 0.70710677f, -0.70710677f);

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
            var cover = be.cover(slot);
            if(cover == GangCover.SWITCH) {
                switchCover(ms, buffer, light, cx, top, on);
                if(be.lock(slot) != null)
                    padlock(ms, buffer, light, cx, top + 0.55);
                continue;
            }
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
                case MONITOR_MODULE -> {
                    // A grey module with its status light: green at rest, red once tripped; N.O. or N.C. printed under it.
                    DeviceDraw.box(ms, buffer, light, cx - 1.5, top, 5.75, cx + 1.5, top + 0.8, 10.25, MODULE);
                    boolean tripped = be.isOn(slot);
                    DeviceDraw.box(ms, buffer, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, cx - 0.4, top + 0.8, 6.4, cx + 0.4, top + 1.0, 7.2, tripped ? 0xFF2020 : 0x30E050);
                    flat(ms, buffer, light, be.monitorNc(slot) ? "N.C." : "N.O.", cx, top + 0.82, 8.6, 1 / 256f, 0x202020);
                }
                case ANNUNCIATOR -> annunciator(be, slot, gangs, ms, buffer, light);
                case SPAN -> {}
                case BLANK -> DeviceDraw.box(ms, buffer, light, cx - 1.6, top, 5.75, cx + 1.6, top + 0.3, 10.25, BLANK);
            }
            if(cover == GangCover.RECEPTACLE) {
                // A padlocked hood stays shut over a plugged cord, the in-use way.
                receptacleCover(ms, buffer, light, cx, top, be.cordPlugged(slot) && be.lock(slot) == null);
                if(be.lock(slot) != null)
                    padlock(ms, buffer, light, cx, top + 1.85);
            }
        }
        ms.popPose();
    }

    /**
     * The weatherproof receptacle cover: a gasket and frame round the gang, a hinge across the
     * top, and a hood that sits shut over the receptacle, or stands up off the hinge while a cord
     * is plugged in, so the plug shows.
     */
    private static void receptacleCover(PoseStack ms, MultiBufferSource buffer, int light, double cx, double top, boolean open) {
        DeviceDraw.box(ms, buffer, light, cx - 2.0, top, 5.3, cx + 2.0, top + 0.15, 10.7, GASKET);
        DeviceDraw.box(ms, buffer, light, cx - 1.95, top + 0.15, 5.35, cx + 1.95, top + 0.45, 10.65, COVER);
        DeviceDraw.box(ms, buffer, light, cx - 1.7, top + 0.45, 5.35, cx + 1.7, top + 0.95, 5.95, COVER_DARK);   // the hinge
        if(open) {
            // The hood swung up a right angle: its depth becomes its thickness, its length its height.
            DeviceDraw.box(ms, buffer, light, cx - 2.0, top + 0.95, 5.3, cx + 2.0, top + 6.3, 6.7, COVER);
            DeviceDraw.box(ms, buffer, light, cx - 0.4, top + 5.9, 6.7, cx + 0.4, top + 6.3, 7.0, COVER_DARK);   // the latch tab
        } else {
            DeviceDraw.box(ms, buffer, light, cx - 2.0, top + 0.45, 5.95, cx + 2.0, top + 1.85, 10.7, COVER);
            DeviceDraw.box(ms, buffer, light, cx - 0.4, top + 1.85, 9.9, cx + 0.4, top + 2.15, 10.4, COVER_DARK);   // the latch tab
        }
    }

    /** A brass padlock hanging off the cover's tab at the plate's bottom edge, its shackle through the tab at height {@code y}. */
    private static void padlock(PoseStack ms, MultiBufferSource buffer, int light, double cx, double y) {
        DeviceDraw.box(ms, buffer, light, cx - 0.4, y, 10.3, cx + 0.4, y + 0.3, 10.7, COVER_DARK);   // the tab
        DeviceDraw.box(ms, buffer, light, cx - 0.15, y - 0.15, 10.0, cx + 0.15, y + 0.45, 11.3, SHACKLE);   // the shackle through it
        DeviceDraw.box(ms, buffer, light, cx - 0.7, y - 0.5, 11.3, cx + 0.7, y + 0.8, 12.4, BRASS);   // the body below
    }

    /**
     * The weatherproof switch cover: a plate over the gang with a boss in the middle and a lever
     * through it, up for on and down for off, ON and OFF cast beside it.
     */
    private static void switchCover(PoseStack ms, MultiBufferSource buffer, int light, double cx, double top, boolean on) {
        DeviceDraw.box(ms, buffer, light, cx - 2.0, top, 5.3, cx + 2.0, top + 0.15, 10.7, GASKET);
        DeviceDraw.box(ms, buffer, light, cx - 1.95, top + 0.15, 5.35, cx + 1.95, top + 0.55, 10.65, COVER);
        DeviceDraw.box(ms, buffer, light, cx - 0.9, top + 0.55, 7.1, cx + 0.9, top + 1.35, 8.9, COVER_DARK);   // the boss
        double z1 = on ? 5.9 : 8.0, z2 = on ? 8.0 : 10.1;
        DeviceDraw.box(ms, buffer, light, cx - 0.45, top + 0.95, z1, cx + 0.45, top + 1.55, z2, COVER);   // the lever
        double k1 = on ? 5.9 : 9.4, k2 = on ? 6.6 : 10.1;
        DeviceDraw.box(ms, buffer, light, cx - 0.55, top + 0.85, k1, cx + 0.55, top + 1.95, k2, COVER);   // its knob
        flat(ms, buffer, light, "ON", cx + 1.3, top + 0.57, 6.2, 1 / 400f, 0x404448);
        flat(ms, buffer, light, "OFF", cx + 1.3, top + 0.57, 9.9, 1 / 400f, 0x404448);
    }

    /** Text lying on the plate at floor-frame (x, y, z), centred on x, its top toward -z. */
    private static void flat(PoseStack ms, MultiBufferSource buffer, int light, String text, double x, double y, double z, float s, int color) {
        ms.pushPose();
        ms.translate(x / 16, y / 16, z / 16);
        ms.mulPose(FLAT);
        DeviceDraw.text(ms, buffer, light, text, 0, 0, 0, s, color, false, 0.5);
        ms.popPose();
    }

    /**
     * Three gangs wide: the panel's three lights, its two-line display, and Silence and Reset
     * keys. Dark but for a message while no panel's survey has reached it or the panel is not
     * loaded here.
     */
    private static void annunciator(GangBoxBlockEntity be, int slot, int gangs, PoseStack ms, MultiBufferSource buffer, int light) {
        double top = GangLayout.PLATE_Y1;
        double cx = GangLayout.slotCentreX(gangs, slot + 1);
        DeviceDraw.box(ms, buffer, light, cx - 5.8, top, 5.75, cx + 5.8, top + 0.6, 10.25, ANNUN_FACE);
        DeviceDraw.box(ms, buffer, light, cx - 5.2, top + 0.6, 7.0, cx + 5.2, top + 0.7, 9.1, ANNUN_SCREEN);
        DeviceDraw.box(ms, buffer, light, cx - 4.6, top + 0.6, 9.5, cx - 1.4, top + 0.9, 10.0, SILENCE_KEY);
        DeviceDraw.box(ms, buffer, light, cx + 1.4, top + 0.6, 9.5, cx + 4.6, top + 0.9, 10.0, RESET_KEY);
        var level = be.getLevel();
        var panelPos = be.annunciatorPanel(slot);
        com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlockEntity panel = null;
        if(level != null && panelPos != null && be.annunciatorLinked(slot) && level.getBlockEntity(panelPos) instanceof com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlockEntity found)
            panel = found;
        int[] lights = panel == null ? new int[] {0x202020, 0x202020, 0x202020} : FireAlarmPanelRenderer.lights(panel);
        double[] lx = {cx - 3, cx, cx + 3};
        for(int i = 0; i < 3; ++i) {
            int lit = lights[i] == 0x202020 ? light : net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            DeviceDraw.box(ms, buffer, lit, lx[i] - 0.4, top + 0.6, 6.1, lx[i] + 0.4, top + 0.8, 6.7, lights[i]);
        }
        float s = 1 / 192f;
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int chars = (int) (10 / 16 / s);
        String line1, line2;
        int color;
        if(panel == null) {
            line1 = panelPos == null ? "NO PANEL" : "LINK LOST";
            line2 = "";
            color = 0xFFC040;
        } else if(!panel.powered()) {
            line1 = "";
            line2 = "";
            color = 0;
        } else {
            var shown = FireAlarmPanelRenderer.lines(panel);
            line1 = shown.line1();
            line2 = shown.line2();
            color = shown.color();
        }
        if(!line1.isEmpty())
            flat(ms, buffer, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, font.plainSubstrByWidth(line1, chars), cx, top + 0.72, 7.6, s, color);
        if(!line2.isEmpty())
            flat(ms, buffer, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, font.plainSubstrByWidth(line2, chars), cx, top + 0.72, 8.5, s, color);
    }
}
