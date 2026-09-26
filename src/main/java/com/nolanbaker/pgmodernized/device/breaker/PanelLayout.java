package com.nolanbaker.pgmodernized.device.breaker;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
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
 * Geometry of the panel in its north-facing frame, in pixels. The enclosure stands against the
 * south side of the block with its open front facing north. Someone looking at the front has +x on
 * their left, so a viewer coordinate {@code u} (0 at the viewer's left) is model {@code x = 16 - u}.
 * <p>
 * Wiring enters only through conduit: four knockout hubs on the top edge, four on the bottom, and
 * two on each side. The line, neutral and circuit points are hidden terminals inside the enclosure
 * that pulled conductors are spliced to in the panel's splice editor.
 * <p>
 * A panel grows downward by up to {@link #MAX_EXT} extension blocks, each another set of rows on
 * the same bus with its own bottom and side knockouts. Everything in an extension is described in
 * the head block's frame, one block (16 px) lower per section; the extension block itself only
 * forwards to the head.
 * <p>
 * Terminals: one per lug, the neutral, one per branch space of the head; then the 8 top and bottom
 * hubs and their 12 hidden landings each; then the 4 side hubs and their landings; then, per
 * extension section, its branch spaces, its 8 hubs and their landings. Each group was added later
 * than the one before and sits after it so panels saved earlier keep their splice indices. The
 * asset generator {@code tools/gen_breaker_panel_assets.py} carries the same numbers; change both.
 */
public final class PanelLayout {
    public static final int NO_SLOT = -2;
    public static final int MAIN = -1;

    public static final int EDGE_HUBS = 8;
    public static final int SIDE_HUBS = 4;
    /** Hubs on the head block. */
    public static final int HUB_COUNT = EDGE_HUBS + SIDE_HUBS;
    public static final int PER_HUB = ConductorColors.COUNT;
    /** Extension sections a panel can carry below its head. */
    public static final int MAX_EXT = 2;
    /** Hubs per extension: four along the bottom, two down each side. */
    public static final int EXT_HUBS = 8;

    public static final double FRONT_Z = 10;
    private static final AABB BODY = new AABB(1, 1, 10, 15, 15, 16);
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 11, 8.5, 8.5, 12);
    private static final double[] HUB_U = {3.5, 6.5, 9.5, 12.5};
    private static final double[] SIDE_HUB_Y = {4, 9};
    private static final double HUB_Z1 = 12.5, HUB_Z2 = 14.5;

    private static final double LEFT_U1 = 1.5, RIGHT_U1 = 8.5, COLUMN_WIDTH = 6, ROW_TOP = 11, ROWS_HEIGHT = 9;
    /** An extension has no main, so its rows fill the enclosure. */
    private static final double EXT_ROW_TOP = 14.5, EXT_ROWS_HEIGHT = 13;
    private static final double MAIN_U1 = 5, MAIN_U2 = 11, MAIN_V1 = 11.5, MAIN_V2 = 14;
    private static final double BREAKER_DEPTH = 1.5, VISUAL_INSET = 0.25;

    private PanelLayout() {}

    // ---- sections ----

    /** Branch spaces a panel with every extension would have. */
    public static int maxSlots(PanelSpec spec) {
        return spec.slots() * (1 + MAX_EXT);
    }

    public static int maxHubs() {
        return HUB_COUNT + EXT_HUBS * MAX_EXT;
    }

    /** Section a branch space sits in: 0 the head, 1.. the extensions below. */
    public static int sectionOfSlot(PanelSpec spec, int slot) {
        return slot < 0 ? 0 : slot / spec.slots();
    }

    public static int sectionOfHub(int hub) {
        return hub < HUB_COUNT ? 0 : 1 + (hub - HUB_COUNT) / EXT_HUBS;
    }

    /** Section a terminal belongs to. */
    public static int sectionOf(PanelSpec spec, int terminal) {
        return terminal < extBase(spec) ? 0 : Math.min(MAX_EXT, 1 + (terminal - extBase(spec)) / extSize(spec));
    }

    /** The head's bottom knockouts, which an extension covers. */
    public static boolean isHeadBottomHub(int hub) {
        return hub >= 4 && hub < EDGE_HUBS;
    }

    // ---- terminal indices ----

    public static int hubBase(PanelSpec spec) {
        return spec.branchFirst() + spec.slots();
    }

    private static int edgeConductorBase(PanelSpec spec) {
        return hubBase(spec) + EDGE_HUBS;
    }

    private static int sideHubBase(PanelSpec spec) {
        return edgeConductorBase(spec) + EDGE_HUBS * PER_HUB;
    }

    private static int sideConductorBase(PanelSpec spec) {
        return sideHubBase(spec) + SIDE_HUBS;
    }

    /** First terminal of the extension groups: where the head's terminals used to end. */
    private static int extBase(PanelSpec spec) {
        return sideConductorBase(spec) + SIDE_HUBS * PER_HUB;
    }

    /** Terminals per extension section: its spaces, its hubs, their landings. */
    private static int extSize(PanelSpec spec) {
        return spec.slots() + EXT_HUBS + EXT_HUBS * PER_HUB;
    }

    private static int extStart(PanelSpec spec, int section) {
        return extBase(spec) + (section - 1) * extSize(spec);
    }

    /** Offset of a terminal inside its extension section, or -1 for a head terminal. */
    private static int extLocal(PanelSpec spec, int terminal) {
        if(terminal < extBase(spec) || terminal >= terminalCount(spec))
            return -1;
        return (terminal - extBase(spec)) % extSize(spec);
    }

    /** The circuit point of a branch space, in any section. */
    public static int branchTerminal(PanelSpec spec, int slot) {
        int section = sectionOfSlot(spec, slot);
        return section == 0 ? spec.branchTerminal(slot) : extStart(spec, section) + slot % spec.slots();
    }

    /** Branch space of a circuit point, or -1 for any other terminal. */
    public static int branchSlot(PanelSpec spec, int terminal) {
        int head = terminal - spec.branchFirst();
        if(head >= 0 && head < spec.slots())
            return head;
        int local = extLocal(spec, terminal);
        return local >= 0 && local < spec.slots() ? sectionOf(spec, terminal) * spec.slots() + local : -1;
    }

    public static int hubTerminal(PanelSpec spec, int hub) {
        if(hub < EDGE_HUBS)
            return hubBase(spec) + hub;
        if(hub < HUB_COUNT)
            return sideHubBase(spec) + (hub - EDGE_HUBS);
        int section = sectionOfHub(hub);
        return extStart(spec, section) + spec.slots() + (hub - HUB_COUNT) % EXT_HUBS;
    }

    /** Hub index of a hub terminal, or -1. */
    public static int hubAt(PanelSpec spec, int terminal) {
        int i = terminal - hubBase(spec);
        if(i >= 0 && i < EDGE_HUBS)
            return i;
        int j = terminal - sideHubBase(spec);
        if(j >= 0 && j < SIDE_HUBS)
            return EDGE_HUBS + j;
        int local = extLocal(spec, terminal) - spec.slots();
        if(extLocal(spec, terminal) >= 0 && local >= 0 && local < EXT_HUBS)
            return HUB_COUNT + (sectionOf(spec, terminal) - 1) * EXT_HUBS + local;
        return -1;
    }

    public static int conductorTerminal(PanelSpec spec, int hub, int conductor) {
        if(hub < EDGE_HUBS)
            return edgeConductorBase(spec) + hub * PER_HUB + conductor;
        if(hub < HUB_COUNT)
            return sideConductorBase(spec) + (hub - EDGE_HUBS) * PER_HUB + conductor;
        int section = sectionOfHub(hub);
        return extStart(spec, section) + spec.slots() + EXT_HUBS + ((hub - HUB_COUNT) % EXT_HUBS) * PER_HUB + conductor;
    }

    /** Hub a hidden conductor terminal belongs to, or -1. */
    public static int conductorHub(PanelSpec spec, int terminal) {
        int i = terminal - edgeConductorBase(spec);
        if(i >= 0 && i < EDGE_HUBS * PER_HUB)
            return i / PER_HUB;
        int j = terminal - sideConductorBase(spec);
        if(j >= 0 && j < SIDE_HUBS * PER_HUB)
            return EDGE_HUBS + j / PER_HUB;
        int local = extLocal(spec, terminal);
        int k = local - spec.slots() - EXT_HUBS;
        if(local >= 0 && k >= 0)
            return HUB_COUNT + (sectionOf(spec, terminal) - 1) * EXT_HUBS + k / PER_HUB;
        return -1;
    }

    public static int conductorOf(PanelSpec spec, int terminal) {
        int i = terminal - edgeConductorBase(spec);
        if(i >= 0 && i < EDGE_HUBS * PER_HUB)
            return i % PER_HUB;
        int j = terminal - sideConductorBase(spec);
        if(j >= 0 && j < SIDE_HUBS * PER_HUB)
            return j % PER_HUB;
        int local = extLocal(spec, terminal);
        int k = local - spec.slots() - EXT_HUBS;
        return local >= 0 && k >= 0 ? k % PER_HUB : -1;
    }

    public static int terminalCount(PanelSpec spec) {
        return extBase(spec) + MAX_EXT * extSize(spec);
    }

    public static boolean isPoint(PanelSpec spec, int terminal) {
        return (terminal >= 0 && terminal < hubBase(spec)) || branchSlot(spec, terminal) >= 0;
    }

    /**
     * Names: hubs 0..3 across the top left to right, 4..7 across the bottom, 8..9 down the left side,
     * 10..11 down the right; then per extension four along its bottom, two down its left, two down its right.
     */
    public static Component hubName(int hub) {
        String key;
        int number;
        if(hub >= HUB_COUNT) {
            int section = sectionOfHub(hub);
            int local = (hub - HUB_COUNT) % EXT_HUBS;
            key = local < 4 ? "ext_bottom" : local < 6 ? "ext_left" : "ext_right";
            number = local < 4 ? local + 1 : local < 6 ? local - 4 + 1 : local - 6 + 1;
            return Lang.builder().translate("gui.breaker_panel.hub." + key, section, number).style(ChatFormatting.AQUA).component();
        }
        if(hub < 4) {
            key = "top";
            number = hub + 1;
        } else if(hub < EDGE_HUBS) {
            key = "bottom";
            number = hub - 4 + 1;
        } else if(hub < EDGE_HUBS + 2) {
            key = "left";
            number = hub - EDGE_HUBS + 1;
        } else {
            key = "right";
            number = hub - EDGE_HUBS - 2 + 1;
        }
        return Lang.builder().translate("gui.breaker_panel.hub." + key, number).style(ChatFormatting.AQUA).component();
    }

    // ---- lines ----

    /** "Line" on a one-lug panel; L1, L2, L3 in the US conductor colours on the others. */
    public static Component lineName(PanelSpec spec, int lug) {
        if(spec.lugs() == 1)
            return name("breaker_panel.line", ChatFormatting.RED);
        return Component.translatable("powergrid.breaker_panel.leg", lug + 1).withStyle(Style.EMPTY.withColor(ConductorColors.textRgb(lug)));
    }

    public static int lineRgb(PanelSpec spec, int lug) {
        return spec.lugs() == 1 ? IDecoratedTerminal.RED : ConductorColors.rgb(lug);
    }

    // ---- breaker spaces ----

    public static double pitch(PanelSpec spec) {
        return pitch(spec, 0);
    }

    public static double pitch(PanelSpec spec, int section) {
        return (section == 0 ? ROWS_HEIGHT : EXT_ROWS_HEIGHT) / spec.rows();
    }

    public static double rowHeight(PanelSpec spec, int section) {
        return pitch(spec, section) - 0.25;
    }

    public static boolean rightColumn(int slot) {
        return slot % 2 == 1;
    }

    /** A space's rectangle in the head's frame; extension rows sit a block lower per section. */
    public static double[] slotRect(PanelSpec spec, int slot) {
        int row = slot / 2;
        int section = row / spec.rows();
        int localRow = row % spec.rows();
        double top = section == 0 ? ROW_TOP : EXT_ROW_TOP;
        double vTop = top - pitch(spec, section) * localRow - 16 * section;
        double vBottom = vTop - rowHeight(spec, section);
        double u1 = rightColumn(slot) ? RIGHT_U1 : LEFT_U1;
        return new double[] {u1, vBottom, u1 + COLUMN_WIDTH, vTop};
    }

    /** The spaces a breaker covers from a head space, that many rows down the column, as one rectangle. */
    public static double[] spanRect(PanelSpec spec, int slot, int rows) {
        var head = slotRect(spec, slot);
        var last = slotRect(spec, slot + 2 * (rows - 1));
        return new double[] {head[0], last[1], head[2], head[3]};
    }

    public static double[] mainRect() {
        return new double[] {MAIN_U1, MAIN_V1, MAIN_U2, MAIN_V2};
    }

    private static double[] rect(PanelSpec spec, int slot, int rows) {
        return slot == MAIN ? mainRect() : spanRect(spec, slot, rows);
    }

    public static AABB breakerBox(PanelSpec spec, int slot) {
        return breakerBox(spec, slot, 1);
    }

    /** @param rows spaces the breaker takes down its column (poles times frame rows); ignored for the main */
    public static AABB breakerBox(PanelSpec spec, int slot, int rows) {
        var r = rect(spec, slot, rows);
        double u1 = r[0] + VISUAL_INSET, u2 = r[2] - VISUAL_INSET;
        return new AABB(16 - u2, r[1], FRONT_Z - BREAKER_DEPTH, 16 - u1, r[3], FRONT_Z);
    }

    public static Vec3 breakerCenter(PanelSpec spec, int slot, int rows) {
        var box = breakerBox(spec, slot, rows);
        return new Vec3((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, box.minZ);
    }

    // ---- hubs and shape ----

    /** A head hub in the head's frame; an extension hub the same, a block lower per section. */
    private static AABB hub(int hub) {
        if(hub >= HUB_COUNT) {
            int section = sectionOfHub(hub);
            int local = (hub - HUB_COUNT) % EXT_HUBS;
            var box = local < 4 ? hub(4 + local) : hub(EDGE_HUBS + local - 4);
            return box.move(0, -16 * section, 0);
        }
        if(hub < EDGE_HUBS) {
            double x = 16 - HUB_U[hub % 4];
            return hub < 4
                    ? new AABB(x - 1, 15, HUB_Z1, x + 1, 16, HUB_Z2)
                    : new AABB(x - 1, 0, HUB_Z1, x + 1, 1, HUB_Z2);
        }
        int side = hub - EDGE_HUBS;
        double y = SIDE_HUB_Y[side % 2];
        // Viewer's left is +x.
        return side < 2
                ? new AABB(15, y, HUB_Z1, 16, y + 2, HUB_Z2)
                : new AABB(0, y, HUB_Z1, 1, y + 2, HUB_Z2);
    }

    public static VoxelShape shape(PanelSpec spec) {
        var shape = box(BODY);
        for(int h = 0; h < HUB_COUNT; ++h)
            shape = Shapes.or(shape, box(hub(h)));
        return shape;
    }

    /** An extension block in its own frame: the enclosure with bottom and side knockouts, no top ones. */
    public static VoxelShape extensionShape() {
        var shape = box(BODY);
        for(int h = 4; h < HUB_COUNT; ++h)
            shape = Shapes.or(shape, box(hub(h)));
        return shape;
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    public static TerminalBoundingBox[] terminals(PanelSpec spec) {
        var terminals = new TerminalBoundingBox[terminalCount(spec)];
        for(int lug = 0; lug < spec.lugs(); ++lug)
            terminals[spec.lineTerminal(lug)] = terminal(lineName(spec, lug), HIDDEN).withColor(lineRgb(spec, lug));
        terminals[spec.neutralTerminal()] = terminal(name("breaker_panel.neutral", ChatFormatting.BLUE), HIDDEN).withColor(IDecoratedTerminal.BLUE);
        for(int slot = 0; slot < maxSlots(spec); ++slot) {
            var label = Lang.builder().translate("breaker_panel.branch", slot + 1).style(ChatFormatting.GRAY).component();
            var hidden = HIDDEN.move(0, -16 * sectionOfSlot(spec, slot), 0);
            terminals[branchTerminal(spec, slot)] = terminal(label, hidden).withColor(IDecoratedTerminal.GRAY);
        }
        for(int h = 0; h < maxHubs(); ++h) {
            terminals[hubTerminal(spec, h)] = terminal(hubName(h), hub(h)).withColor(0x2FB8D6);
            var hidden = HIDDEN.move(0, -16 * sectionOfHub(h), 0);
            for(int k = 0; k < PER_HUB; ++k) {
                var label = Lang.builder().add(hubName(h)).text(" ").add(ConductorColors.name(k)).component();
                terminals[conductorTerminal(spec, h, k)] = terminal(label, hidden).withColor(ConductorColors.rgb(k));
            }
        }
        return terminals;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static Component name(String key, ChatFormatting style) {
        return Lang.builder().translate(key).style(style).component();
    }

    // ---- clicks ----

    /** @param slotCount spaces the panel has with its present extensions; {@code local} is relative to the head block */
    public static int slotAt(PanelSpec spec, Direction facing, Direction hitFace, Vec3 local, int slotCount) {
        if(hitFace != facing)
            return NO_SLOT;
        var north = toNorthFrame(local.scale(16), facing);
        double u = 16 - north.x, v = north.y;
        if(inside(mainRect(), u, v))
            return MAIN;
        for(int slot = 0; slot < slotCount; ++slot) {
            if(inside(slotRect(spec, slot), u, v))
                return slot;
        }
        return NO_SLOT;
    }

    public static int slotAt(PanelSpec spec, Direction facing, Direction hitFace, Vec3 local) {
        return slotAt(spec, facing, hitFace, local, spec.slots());
    }

    private static boolean inside(double[] r, double u, double v) {
        return u >= r[0] && u <= r[2] && v >= r[1] && v <= r[3];
    }

    public static Vec3 toNorthFrame(Vec3 p, Direction facing) {
        double size = 16;
        return switch(facing) {
            case EAST -> new Vec3(p.z, p.y, size - p.x);
            case SOUTH -> new Vec3(size - p.x, p.y, size - p.z);
            case WEST -> new Vec3(size - p.z, p.y, p.x);
            default -> p;
        };
    }

    public static Vec3 fromNorthFrame(Vec3 p, Direction facing) {
        double size = 16;
        return switch(facing) {
            case EAST -> new Vec3(size - p.z, p.y, p.x);
            case SOUTH -> new Vec3(size - p.x, p.y, size - p.z);
            case WEST -> new Vec3(p.z, p.y, size - p.x);
            default -> p;
        };
    }
}
