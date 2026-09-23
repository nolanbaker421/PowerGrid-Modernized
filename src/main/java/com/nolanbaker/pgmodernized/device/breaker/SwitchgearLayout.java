package com.nolanbaker.pgmodernized.device.breaker;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * One switchgear section in its north frame, in pixels: a floor-standing cabinet filling the block
 * (back against z = 16, door at z = 2), knockouts across the top and the bottom, and a single
 * 3-pole breaker space in the door. Sections stand side by side; the three-phase bus and neutral of
 * each run to hidden coupler terminals on both sides that hidden bus-bar wires join to the next
 * section's. Someone looking at the door has +x on their left.
 * <p>
 * Terminals: 0..3 bus L1, L2, L3, N; 4..6 load L1, L2, L3 (the breaker's load side); 7..10 the
 * couplers on the viewer's left (+x) side and 11..14 on the right; then 8 hubs and their landings.
 * Mirrored by {@code tools/gen_switchgear_assets.py}; change both.
 */
public final class SwitchgearLayout {
    public static final int BUS_L1 = 0, BUS_L2 = 1, BUS_L3 = 2, BUS_N = 3;
    public static final int LOAD_L1 = 4, LOAD_L2 = 5, LOAD_L3 = 6;
    public static final int POINT_COUNT = 7;
    public static final int COUPLER_LEFT = 7;
    public static final int COUPLER_RIGHT = 11;
    public static final int COUPLERS = 4;
    public static final int HUB_BASE = 15;
    public static final int HUB_COUNT = 8;
    public static final int PER_HUB = ConductorColors.COUNT;
    public static final int CONDUCTOR_BASE = HUB_BASE + HUB_COUNT;
    public static final int TERMINAL_COUNT = CONDUCTOR_BASE + HUB_COUNT * PER_HUB;

    /** Bus current the couplers carry before they burn. */
    public static final int BUS_AMPS = 2000;

    public static final double FRONT_Z = 2;
    private static final AABB BODY = new AABB(0, 1, 2, 16, 15, 16);
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 8, 8.5, 8.5, 9);
    private static final double[] HUB_U = {3.5, 6.5, 9.5, 12.5};
    private static final double[] COUPLER_Y = {3, 5.5, 8, 10.5};
    /** The breaker's face in the door. */
    public static final AABB BREAKER = new AABB(4.5, 6, 0.5, 11.5, 10.5, 2);
    /** Create's "south location" of the rating value box, on the door. */
    public static final Vec3 VALUE_BOX = new Vec3(8, 12.5, 14);

    private SwitchgearLayout() {}

    public static boolean isPoint(int terminal) {
        return terminal >= 0 && terminal < POINT_COUNT;
    }

    public static boolean isCoupler(int terminal) {
        return terminal >= COUPLER_LEFT && terminal < HUB_BASE;
    }

    public static int hubTerminal(int hub) {
        return HUB_BASE + hub;
    }

    public static int hubAt(int terminal) {
        int i = terminal - HUB_BASE;
        return i >= 0 && i < HUB_COUNT ? i : -1;
    }

    public static int conductorTerminal(int hub, int conductor) {
        return CONDUCTOR_BASE + hub * PER_HUB + conductor;
    }

    public static int conductorHub(int terminal) {
        int i = terminal - CONDUCTOR_BASE;
        return i >= 0 && i < HUB_COUNT * PER_HUB ? i / PER_HUB : -1;
    }

    public static int conductorOf(int terminal) {
        int i = terminal - CONDUCTOR_BASE;
        return i >= 0 && i < HUB_COUNT * PER_HUB ? i % PER_HUB : -1;
    }

    public static Component hubName(int hub) {
        return Lang.builder().translate("gui.breaker_panel.hub." + (hub < 4 ? "top" : "bottom"), hub % 4 + 1).style(ChatFormatting.AQUA).component();
    }

    public static Component busName(int k) {
        if(k == 3)
            return Lang.builder().translate("switchgear.bus_n").style(ChatFormatting.BLUE).component();
        return Component.translatable("powergrid.switchgear.bus", k + 1).withStyle(Style.EMPTY.withColor(ConductorColors.textRgb(k)));
    }

    public static Component loadName(int k) {
        return Component.translatable("powergrid.switchgear.load", k + 1).withStyle(Style.EMPTY.withColor(ConductorColors.textRgb(k)));
    }

    public static int phaseRgb(int k) {
        return k == 3 ? IDecoratedTerminal.BLUE : ConductorColors.rgb(k);
    }

    private static AABB hub(int hub) {
        double x = 16 - HUB_U[hub % 4];
        return hub < 4
                ? new AABB(x - 1, 15, 8, x + 1, 16, 10)
                : new AABB(x - 1, 0, 8, x + 1, 1, 10);
    }

    /** Coupler k on the viewer's left (+x) or right (-x) side. */
    private static AABB coupler(boolean left, int k) {
        double y = COUPLER_Y[k];
        return left ? new AABB(15.75, y, 12, 16, y + 1, 13) : new AABB(0, y, 12, 0.25, y + 1, 13);
    }

    public static VoxelShape shape() {
        var shape = Shapes.or(box(BODY), box(BREAKER));
        for(int h = 0; h < HUB_COUNT; ++h)
            shape = Shapes.or(shape, box(hub(h)));
        return shape;
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    public static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[TERMINAL_COUNT];
        for(int k = 0; k < 4; ++k)
            terminals[BUS_L1 + k] = terminal(busName(k), HIDDEN).withColor(phaseRgb(k));
        for(int k = 0; k < 3; ++k)
            terminals[LOAD_L1 + k] = terminal(loadName(k), HIDDEN).withColor(ConductorColors.rgb(k));
        for(int k = 0; k < COUPLERS; ++k) {
            terminals[COUPLER_LEFT + k] = terminal(busName(k), coupler(true, k)).withColor(phaseRgb(k));
            terminals[COUPLER_RIGHT + k] = terminal(busName(k), coupler(false, k)).withColor(phaseRgb(k));
        }
        for(int h = 0; h < HUB_COUNT; ++h) {
            terminals[hubTerminal(h)] = terminal(hubName(h), hub(h)).withColor(0x2FB8D6);
            for(int k = 0; k < PER_HUB; ++k) {
                var label = Lang.builder().add(hubName(h)).text(" ").add(ConductorColors.name(k)).component();
                terminals[conductorTerminal(h, k)] = terminal(label, HIDDEN).withColor(ConductorColors.rgb(k));
            }
        }
        return terminals;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    /** Whether a click on the front lands on the breaker face. */
    public static boolean onBreaker(Vec3 north) {
        double x = north.x, y = north.y;
        return x >= BREAKER.minX && x <= BREAKER.maxX && y >= BREAKER.minY && y <= BREAKER.maxY;
    }
}
