package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The pull box in its north frame, in pixels: a full block with its door facing north and ten
 * knockouts flush with its other faces, two each on the top, bottom, left, right and back. A
 * viewer at the door has +x on their left. Terminals 0..9 are the hubs, 10 on are the hidden
 * landings, twelve per hub. There are no cover terminals: it is a pull box, wires are spliced
 * inside. Mirrored by {@code tools/gen_conduit_assets.py}; change both.
 */
public final class PullBoxGeometry {
    public static final int HUB_COUNT = 10;
    public static final int HUB_BASE = 0;
    public static final int CONDUCTOR_BASE = HUB_BASE + HUB_COUNT;
    /** The terminal cabinet's twelve door lugs come after everything else. */
    public static final int FRONT_BASE = CONDUCTOR_BASE + HUB_COUNT * ConductorColors.COUNT;
    public static final int FRONT_COUNT = ConductorColors.COUNT;
    public static final int TERMINAL_COUNT = FRONT_BASE + FRONT_COUNT;
    public static final String[] HUB_KEYS = {"top", "bottom", "left", "right", "back"};
    /** Knockout centres along a face, in viewer coordinates from the viewer's left (or top). */
    private static final double[] ALONG = {5, 11};
    private static final double HALF = 1.5, DEPTH = 0.5;
    private static final AABB[] HUBS = new AABB[HUB_COUNT];
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 7.5, 8.5, 8.5, 8.5);
    private static final double[] LUG_U = {3.5, 6.5, 9.5, 12.5};
    private static final double[] LUG_Y = {11.5, 8, 4.5};

    static {
        for(int i = 0; i < 2; ++i) {
            double x = 16 - ALONG[i];   // top, bottom and back: left to right as the viewer sees it
            double y = 16 - ALONG[i];   // sides: top to bottom
            HUBS[i] = new AABB(x - HALF, 16 - DEPTH, 8 - HALF, x + HALF, 16, 8 + HALF);            // top
            HUBS[2 + i] = new AABB(x - HALF, 0, 8 - HALF, x + HALF, DEPTH, 8 + HALF);               // bottom
            HUBS[4 + i] = new AABB(16 - DEPTH, y - HALF, 8 - HALF, 16, y + HALF, 8 + HALF);         // viewer's left (+x)
            HUBS[6 + i] = new AABB(0, y - HALF, 8 - HALF, DEPTH, y + HALF, 8 + HALF);               // viewer's right (-x)
            HUBS[8 + i] = new AABB(x - HALF, 8 - HALF, 16 - DEPTH, x + HALF, 8 + HALF, 16);         // back, against the wall
        }
    }

    private PullBoxGeometry() {}

    /** Hubs 0..1 top, 2..3 bottom, 4..5 left, 6..7 right, 8..9 back. */
    public static int[] hubsOn(String key) {
        for(int f = 0; f < HUB_KEYS.length; ++f) {
            if(HUB_KEYS[f].equals(key))
                return new int[] {f * 2, f * 2 + 1};
        }
        return new int[0];
    }

    public static boolean isHub(int terminal) {
        return terminal >= HUB_BASE && terminal < CONDUCTOR_BASE;
    }

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

    public static int conductorHub(int terminal) {
        return isConductor(terminal) ? (terminal - CONDUCTOR_BASE) / ConductorColors.COUNT : -1;
    }

    public static int conductorOf(int terminal) {
        return isConductor(terminal) ? (terminal - CONDUCTOR_BASE) % ConductorColors.COUNT : -1;
    }

    public static boolean isFront(int terminal) {
        return terminal >= FRONT_BASE && terminal < TERMINAL_COUNT;
    }

    /** A door lug of the terminal cabinet, a 4 x 3 grid read left to right, top to bottom, standing a pixel proud of the door. */
    public static AABB frontLug(int k) {
        double x = 16 - LUG_U[k % 4], y = LUG_Y[k / 4];
        return new AABB(x - 1, y - 1, -1, x + 1, y + 1, 0);
    }

    public static Component hubName(int hub) {
        return Lang.builder().translate("gui.pull_box.hub." + HUB_KEYS[hub / 2], hub % 2 + 1).style(ChatFormatting.AQUA).component();
    }

    /** @param lugs with the terminal cabinet's door lugs; a plain pull box leaves them out */
    public static TerminalBoundingBox[] terminals(boolean lugs) {
        var terminals = new TerminalBoundingBox[lugs ? TERMINAL_COUNT : FRONT_BASE];
        if(lugs) for(int k = 0; k < FRONT_COUNT; ++k)
            terminals[FRONT_BASE + k] = terminal(ConductorColors.name(k), frontLug(k)).withColor(ConductorColors.rgb(k));
        for(int h = 0; h < HUB_COUNT; ++h) {
            terminals[hubTerminal(h)] = terminal(hubName(h), HUBS[h]).withColor(0x2FB8D6);
            for(int k = 0; k < ConductorColors.COUNT; ++k) {
                var name = Lang.builder().add(hubName(h)).text(" ").add(ConductorColors.name(k)).component();
                terminals[conductorTerminal(h, k)] = terminal(name, HIDDEN).withColor(ConductorColors.rgb(k));
            }
        }
        return terminals;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }
}
