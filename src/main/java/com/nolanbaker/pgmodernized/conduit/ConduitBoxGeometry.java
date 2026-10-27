package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

import java.util.function.UnaryOperator;

/**
 * The conduit box in its north frame, in pixels: an 8 x 8 x 3 box against the wall at z = 16 with
 * its cover facing north. A viewer at the front has +x on their left.
 * <ul>
 *   <li>Terminals 0..11: conductor terminals on the cover, a 4 x 3 grid, read left to right, top to bottom.</li>
 *   <li>Terminals 12..23: twelve hubs (knockouts), three along each of the top, right, bottom and left
 *       edges, numbered clockwise from the viewer's top left; one run each.</li>
 *   <li>Terminals 24..167: hidden landing points inside the box, twelve per hub, for the run's conductors.</li>
 * </ul>
 * Mirrored by {@code tools/gen_conduit_assets.py}; change both.
 */
public final class ConduitBoxGeometry {
    public static final int FRONT_COUNT = ConductorColors.COUNT;
    public static final int HUB_COUNT = 12;
    public static final int HUB_BASE = FRONT_COUNT;
    public static final int CONDUCTOR_BASE = HUB_BASE + HUB_COUNT;
    /** The receptacle's face, a socket placement for power cords; only there under a receptacle cover. */
    public static final int TERMINAL_RECEPTACLE = CONDUCTOR_BASE + HUB_COUNT * ConductorColors.COUNT;
    public static final int TERMINAL_COUNT = TERMINAL_RECEPTACLE + 1;

    public static final String[] HUB_KEYS = {"top", "right", "bottom", "left"};

    private static final AABB BODY = new AABB(4, 4, 13, 12, 12, 16);
    /** Hub centres along an edge, in viewer coordinates from the viewer's left (or from the top for the side edges). */
    private static final double[] HUB_ALONG = {5.5, 8, 10.5};
    private static final AABB[] HUBS = new AABB[HUB_COUNT];

    static {
        for(int i = 0; i < 3; ++i) {
            double along = HUB_ALONG[i];
            double x = 16 - along;          // top and bottom edges: left to right as the viewer sees it
            double y = 16 - along;          // side edges: top to bottom
            HUBS[i] = new AABB(x - 1, 12, 13.5, x + 1, 13, 15.5);        // top
            HUBS[3 + i] = new AABB(3, y - 1, 13.5, 4, y + 1, 15.5);      // viewer's right (-x)
            HUBS[6 + i] = new AABB(x - 1, 3, 13.5, x + 1, 4, 15.5);      // bottom
            HUBS[9 + i] = new AABB(12, y - 1, 13.5, 13, y + 1, 15.5);    // viewer's left (+x)
        }
    }
    private static final AABB HIDDEN = new AABB(7.99, 7.99, 14.49, 8.01, 8.01, 14.51);   // a speck: a landing has no outline to show
    private static final double[] COLUMNS_U = {5, 7, 9, 11};
    private static final double[] ROWS_Y = {10, 8, 6};
    private static final double NUB = 0.6, NUB_Z1 = 12.4, NUB_Z2 = 13;

    private ConduitBoxGeometry() {}

    public static boolean isFront(int terminal) {
        return terminal >= 0 && terminal < FRONT_COUNT;
    }

    public static boolean isHub(int terminal) {
        return terminal >= HUB_BASE && terminal < CONDUCTOR_BASE;
    }

    /** Hub index of a hub terminal, or -1. */
    public static int hubOf(int terminal) {
        return isHub(terminal) ? terminal - HUB_BASE : -1;
    }

    public static int hubTerminal(int hub) {
        return HUB_BASE + hub;
    }

    public static int conductorTerminal(int hub, int conductor) {
        return CONDUCTOR_BASE + hub * ConductorColors.COUNT + conductor;
    }

    public static boolean isConductor(int terminal) {
        return terminal >= CONDUCTOR_BASE && terminal < TERMINAL_COUNT;
    }

    /** Hub a hidden conductor terminal belongs to, or -1. */
    public static int conductorHub(int terminal) {
        return isConductor(terminal) ? (terminal - CONDUCTOR_BASE) / ConductorColors.COUNT : -1;
    }

    /** Conductor index of a front or hidden conductor terminal. */
    public static int conductorOf(int terminal) {
        if(isFront(terminal))
            return terminal;
        return isConductor(terminal) ? (terminal - CONDUCTOR_BASE) % ConductorColors.COUNT : -1;
    }

    public static AABB frontNub(int k) {
        double x = 16 - COLUMNS_U[k % 4], y = ROWS_Y[k / 4];
        return new AABB(x - NUB, y - NUB, NUB_Z1, x + NUB, y + NUB, NUB_Z2);
    }

    public static Component hubName(int hub) {
        return Lang.builder().translate("gui.conduit_box.hub." + HUB_KEYS[hub / 3], hub % 3 + 1).style(ChatFormatting.AQUA).component();
    }

    public static TerminalBoundingBox[] terminals() {
        return terminals(UnaryOperator.identity());
    }

    /** The terminals with every box put through {@code frame}, for a box drawn in another frame than north. */
    public static TerminalBoundingBox[] terminals(UnaryOperator<AABB> frame) {
        return terminals(frame, ConduitCover.NODE);
    }

    /**
     * The terminals as the box with that cover shows them. Power Grid's wiring overlay draws every
     * terminal in a block's collection, so a cover nub the plate does not expose, and the receptacle
     * face of any other plate, are specks here rather than merely refused by the lookup.
     */
    public static TerminalBoundingBox[] terminals(UnaryOperator<AABB> frame, ConduitCover cover) {
        var terminals = new TerminalBoundingBox[TERMINAL_COUNT];
        for(int k = 0; k < FRONT_COUNT; ++k)
            terminals[k] = terminal(ConductorColors.name(k), frame.apply(cover.exposes(k) ? coverNub(cover, k) : HIDDEN)).withColor(ConductorColors.rgb(k));
        for(int h = 0; h < HUB_COUNT; ++h) {
            terminals[hubTerminal(h)] = terminal(hubName(h), frame.apply(HUBS[h])).withColor(com.nolanbaker.pgmodernized.conduit.ConduitSize.ONE_HALF.bandColor());
            for(int k = 0; k < ConductorColors.COUNT; ++k) {
                var name = Lang.builder().add(hubName(h)).text(" ").add(ConductorColors.name(k)).component();
                terminals[conductorTerminal(h, k)] = terminal(name, frame.apply(HIDDEN)).withColor(ConductorColors.rgb(k));
            }
        }
        terminals[TERMINAL_RECEPTACLE] = terminal(IDecoratedTerminal.SOCKET, frame.apply(cover == ConduitCover.RECEPTACLE ? RECEPTACLE_FACE : HIDDEN));
        return terminals;
    }

    public static VoxelShape shape() {
        return shape(true);
    }

    /** @param nubs with the twelve cover terminals of a node plate */
    public static VoxelShape shape(boolean nubs) {
        return shape(nubs, UnaryOperator.identity());
    }

    public static VoxelShape shape(boolean nubs, UnaryOperator<AABB> frame) {
        return shape(nubs ? ConduitCover.NODE : ConduitCover.BLANK, frame);
    }

    /** The lamp holder and bulb of a light socket cover, standing out from the plate. */
    public static final AABB LAMP_HOLDER = new AABB(6.5, 6.5, 11, 9.5, 9.5, 13);
    /** Where the bulb sits in front of the holder; the renderer draws the fitted bulb here. */
    public static final AABB BULB = new AABB(6, 6, 8.5, 10, 10, 11);
    /** The face of a receptacle cover that a cord plugs into. */
    public static final AABB RECEPTACLE_FACE = new AABB(6.5, 6, 11.5, 9.5, 10, 13);

    /** The two screw terminals beside a light fixture or a receptacle: line on the viewer's right (+x), neutral on the left. */
    public static final AABB SCREW_LINE = new AABB(10, 7, 12, 11, 9, 13), SCREW_NEUTRAL = new AABB(5, 7, 12, 6, 9, 13);

    /** The smoke detector's puck on the plate, and its light; the horn strobe's body and its lens. */
    public static final AABB SMOKE_PUCK = new AABB(5, 5, 11.5, 11, 11, 13), SMOKE_LED = new AABB(7.5, 6, 11.2, 8.5, 7, 11.5);
    public static final AABB HORN_BODY = new AABB(6, 4.5, 11, 10, 11.5, 13), HORN_LENS = new AABB(7, 8.5, 10.5, 9, 10.5, 11);
    /** The pull station's body and its handle, set and pulled down. */
    public static final AABB PULL_BODY = new AABB(6, 5, 11, 10, 11, 13), PULL_HANDLE_SET = new AABB(6.5, 8, 10.3, 9.5, 9, 11), PULL_HANDLE_PULLED = new AABB(6.5, 5.5, 10.3, 9.5, 6.5, 11);

    /** Where a cover's exposed terminal sits: a node on the node plate, a screw beside any device. */
    public static AABB coverNub(ConduitCover cover, int k) {
        if(cover.isDevice())
            return k == 0 ? SCREW_LINE : SCREW_NEUTRAL;
        return frontNub(k);
    }

    /** The box with that cover: its exposed cover terminals as nubs, and the lamp of a light socket. */
    public static VoxelShape shape(ConduitCover cover, UnaryOperator<AABB> frame) {
        var shape = box(frame.apply(BODY));
        for(int k = 0; k < cover.terminals(); ++k)
            shape = Shapes.or(shape, box(frame.apply(coverNub(cover, k))));
        if(cover.isLight())
            shape = Shapes.or(shape, box(frame.apply(LAMP_HOLDER)), box(frame.apply(BULB)));
        if(cover == ConduitCover.RECEPTACLE)
            shape = Shapes.or(shape, box(frame.apply(RECEPTACLE_FACE)));
        if(cover.isDetector())
            shape = Shapes.or(shape, box(frame.apply(SMOKE_PUCK)));
        if(cover.isHorn())
            shape = Shapes.or(shape, box(frame.apply(HORN_BODY)));
        if(cover.isPull())
            shape = Shapes.or(shape, box(frame.apply(PULL_BODY)));
        for(var hub : HUBS)
            shape = Shapes.or(shape, box(frame.apply(com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs.flat(hub))));
        return shape;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }
}
