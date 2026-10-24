package com.nolanbaker.pgmodernized.device.gear;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.device.breaker.PanelLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.nolanbaker.pgmodernized.util.TerminalBoxes;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * One switchgear cell in its north frame, in pixels: a cabinet filling the block from z = 2 to
 * the back, open at the front, with a 3 x 3 grid of 5 px slots behind the opening. Column 0 is
 * on the viewer's left (+x), row 0 at the top. Knockouts: three on top, three underneath, two on
 * each side.
 * <p>
 * Terminals: 0..47 the <em>edge</em> terminals, one per side, per slot along that side, per
 * conductor L1, L2, L3, N, which hidden bus bars join to the next cell's; 48..83 the lugs of every
 * slot (four per slot, live only when a lug block is in it); 84..93 the knockouts; then their
 * landings. The ground, added later, keeps its own ranges after those so saved cells keep their
 * indices: 214..225 the ground edge ports, 226..234 the ground lug of each slot.
 * Mirrored by {@code tools/gen_gear_assets.py}; change both.
 */
public final class GearLayout {
    public static final int COLS = 3, ROWS = 3, SLOTS = COLS * ROWS;
    /** Conductor indices: 0 L1, 1 L2, 2 L3, 3 N, 4 ground. */
    public static final int CONDUCTORS = 5, GROUND = 4;
    /** The conductors in the original terminal ranges. */
    private static final int BASE_CONDUCTORS = 4;
    public static final double PITCH = 5, EDGE = 15.5, FRONT_Z = 2;

    public static final int EDGE_BASE = 0, EDGE_COUNT = 4 * COLS * BASE_CONDUCTORS;
    public static final int LUG_BASE = EDGE_BASE + EDGE_COUNT, LUG_COUNT = SLOTS * BASE_CONDUCTORS;
    public static final int HUB_BASE = LUG_BASE + LUG_COUNT, HUB_COUNT = 10;
    public static final int PER_HUB = ConductorColors.COUNT;
    public static final int CONDUCTOR_BASE = HUB_BASE + HUB_COUNT;
    public static final int GROUND_EDGE_BASE = CONDUCTOR_BASE + HUB_COUNT * PER_HUB, GROUND_EDGE_COUNT = 4 * COLS;
    public static final int GROUND_LUG_BASE = GROUND_EDGE_BASE + GROUND_EDGE_COUNT, GROUND_LUG_COUNT = SLOTS;
    public static final int TERMINAL_COUNT = GROUND_LUG_BASE + GROUND_LUG_COUNT;

    public static final AABB BODY = new AABB(0, 0, FRONT_Z, 16, 16, 16);
    private static final AABB HIDDEN = new AABB(7.99, 7.99, 8.49, 8.01, 8.01, 8.51);   // a speck: a landing has no outline to show
    private static final double[] SIDE_HUB_Y = {4.5, 11.5};

    private GearLayout() {}

    // ---- the grid ----

    public static int slot(int col, int row) {
        return row * COLS + col;
    }

    public static int col(int slot) {
        return slot % COLS;
    }

    public static int row(int slot) {
        return slot / COLS;
    }

    /** The slot on that side of a slot, or -1 past the grid. */
    public static int neighbour(int slot, GearSide side) {
        int c = col(slot) + side.dc, r = row(slot) + side.dr;
        return c < 0 || c >= COLS || r < 0 || r >= ROWS ? -1 : slot(c, r);
    }

    /** Where along the cell's edge a slot sits for that side, or -1 when another slot is in the way. */
    public static int edgeIndexOf(int slot, GearSide side) {
        return switch(side) {
            case UP -> row(slot) == 0 ? col(slot) : -1;
            case DOWN -> row(slot) == ROWS - 1 ? col(slot) : -1;
            case LEFT -> col(slot) == 0 ? row(slot) : -1;
            case RIGHT -> col(slot) == COLS - 1 ? row(slot) : -1;
        };
    }

    public static int slotOnEdge(GearSide side, int i) {
        return switch(side) {
            case UP -> slot(i, 0);
            case DOWN -> slot(i, ROWS - 1);
            case LEFT -> slot(0, i);
            case RIGHT -> slot(COLS - 1, i);
        };
    }

    /** {x1, y1, x2, y2} of a slot in the north frame. */
    public static double[] slotRect(int slot) {
        double x2 = EDGE - PITCH * col(slot), y2 = EDGE - PITCH * row(slot);
        return new double[] {x2 - PITCH, y2 - PITCH, x2, y2};
    }

    public static Vec3 slotCenter(int slot) {
        var r = slotRect(slot);
        return new Vec3((r[0] + r[2]) / 2, (r[1] + r[3]) / 2, FRONT_Z);
    }

    /** The slot under a click on the door, or -1. {@code local} is the hit relative to the block. */
    public static int slotAt(Direction facing, Direction hitFace, Vec3 local) {
        if(hitFace != facing)
            return -1;
        var north = PanelLayout.toNorthFrame(local.scale(16), facing);
        double u = EDGE - north.x, v = EDGE - north.y;
        if(u < 0 || v < 0)
            return -1;
        int c = (int) (u / PITCH), r = (int) (v / PITCH);
        return c >= COLS || r >= ROWS ? -1 : slot(c, r);
    }

    // ---- terminals ----

    public static int edge(GearSide side, int i, int k) {
        if(k == GROUND)
            return GROUND_EDGE_BASE + side.ordinal() * COLS + i;
        return EDGE_BASE + (side.ordinal() * COLS + i) * BASE_CONDUCTORS + k;
    }

    private static boolean isGroundEdge(int terminal) {
        return terminal >= GROUND_EDGE_BASE && terminal < GROUND_EDGE_BASE + GROUND_EDGE_COUNT;
    }

    public static boolean isEdge(int terminal) {
        return (terminal >= EDGE_BASE && terminal < EDGE_BASE + EDGE_COUNT) || isGroundEdge(terminal);
    }

    public static GearSide edgeSide(int terminal) {
        if(isGroundEdge(terminal))
            return GearSide.values()[(terminal - GROUND_EDGE_BASE) / COLS];
        return GearSide.values()[(terminal - EDGE_BASE) / (COLS * BASE_CONDUCTORS)];
    }

    public static int edgeIndex(int terminal) {
        if(isGroundEdge(terminal))
            return (terminal - GROUND_EDGE_BASE) % COLS;
        return (terminal - EDGE_BASE) / BASE_CONDUCTORS % COLS;
    }

    public static int edgeConductor(int terminal) {
        return isGroundEdge(terminal) ? GROUND : (terminal - EDGE_BASE) % BASE_CONDUCTORS;
    }

    public static int lug(int slot, int k) {
        return k == GROUND ? GROUND_LUG_BASE + slot : LUG_BASE + slot * BASE_CONDUCTORS + k;
    }

    private static boolean isGroundLug(int terminal) {
        return terminal >= GROUND_LUG_BASE && terminal < GROUND_LUG_BASE + GROUND_LUG_COUNT;
    }

    public static boolean isLug(int terminal) {
        return (terminal >= LUG_BASE && terminal < LUG_BASE + LUG_COUNT) || isGroundLug(terminal);
    }

    public static int lugSlot(int terminal) {
        return isGroundLug(terminal) ? terminal - GROUND_LUG_BASE : (terminal - LUG_BASE) / BASE_CONDUCTORS;
    }

    public static int lugConductor(int terminal) {
        return isGroundLug(terminal) ? GROUND : (terminal - LUG_BASE) % BASE_CONDUCTORS;
    }

    /** Five studs along the bottom of a slot's face. */
    public static AABB lugBox(int slot, int k) {
        var r = slotRect(slot);
        double x = r[2] - 0.5 - 0.9 * k - 0.7;
        return new AABB(x, r[1] + 0.3, 0.6, x + 0.7, r[1] + 1.1, FRONT_Z);
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

    /** Knockouts 0..2 on top over each column, 3..5 underneath, 6..7 on the viewer's left (+x), 8..9 on the right. */
    public static AABB hub(int hub) {
        if(hub < 6) {
            double cx = EDGE - PITCH * (hub % 3) - PITCH / 2;
            return hub < 3 ? new AABB(cx - 1, 15, 8, cx + 1, 16, 10) : new AABB(cx - 1, 0, 8, cx + 1, 1, 10);
        }
        double y = SIDE_HUB_Y[hub % 2];
        return hub < 8 ? new AABB(15, y - 1, 8, 16, y + 1, 10) : new AABB(0, y - 1, 8, 1, y + 1, 10);
    }

    public static Component hubName(int hub) {
        String key = hub < 3 ? "top" : hub < 6 ? "bottom" : hub < 8 ? "left" : "right";
        int number = hub < 6 ? hub % 3 + 1 : hub % 2 + 1;
        return Lang.builder().translate("gui.gear_cell.hub." + key, number).style(ChatFormatting.AQUA).component();
    }

    public static Component edgeName(int terminal) {
        var side = edgeSide(terminal);
        return Lang.builder().translate("gear_cell.edge", side.name().toLowerCase(), edgeIndex(terminal) + 1).text(" ")
                .add(conductorName(edgeConductor(terminal))).style(ChatFormatting.DARK_GRAY).component();
    }

    public static Component conductorName(int k) {
        if(k == GROUND)
            return ConductorColors.groundLabel("gear_cell.ground");
        return k == 3 ? ConductorColors.neutralLabel("gear_cell.neutral") : ConductorColors.hotLabel("gear_cell.line", k, k + 1);
    }

    public static int conductorRgb(int k) {
        if(k == GROUND)
            return ConductorColors.groundRgb();
        return k == 3 ? ConductorColors.neutralRgb() : ConductorColors.hotRgb(k);
    }

    public static Component lugName(int slot, int k) {
        return Lang.builder().translate("gear_cell.lug", slot + 1).text(" ").add(conductorName(k)).component();
    }

    public static TerminalBoundingBox[] terminals() {
        var terminals = new TerminalBoundingBox[TERMINAL_COUNT];
        for(var side : GearSide.values()) {
            for(int i = 0; i < COLS; ++i) {
                for(int k = 0; k < CONDUCTORS; ++k) {
                    int t = edge(side, i, k);
                    terminals[t] = TerminalBoxes.px(edgeName(t), HIDDEN).withColor(conductorRgb(k));
                }
            }
        }
        for(int slot = 0; slot < SLOTS; ++slot) {
            for(int k = 0; k < CONDUCTORS; ++k)
                terminals[lug(slot, k)] = TerminalBoxes.px(lugName(slot, k), lugBox(slot, k)).withColor(conductorRgb(k));
        }
        for(int h = 0; h < HUB_COUNT; ++h) {
            terminals[hubTerminal(h)] = TerminalBoxes.px(hubName(h), hub(h)).withColor(com.nolanbaker.pgmodernized.conduit.ConduitSize.FOUR.bandColor());
            for(int k = 0; k < PER_HUB; ++k) {
                var name = Lang.builder().add(hubName(h)).text(" ").add(ConductorColors.name(k)).component();
                terminals[conductorTerminal(h, k)] = TerminalBoxes.px(name, HIDDEN).withColor(ConductorColors.rgb(k));
            }
        }
        return terminals;
    }

    public static VoxelShape shape() {
        var shape = box(BODY);
        for(int h = 0; h < HUB_COUNT; ++h)
            shape = Shapes.or(shape, box(hub(h)));
        return shape;
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }
}
