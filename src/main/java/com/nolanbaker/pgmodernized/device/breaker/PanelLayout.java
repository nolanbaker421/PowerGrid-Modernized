package com.nolanbaker.pgmodernized.device.breaker;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
    private static final AABB HIDDEN = new AABB(7.99, 7.99, 11.49, 8.01, 8.01, 11.51);   // a speck: a landing has no outline to show
    /** The original four knockouts across an edge, now the inner four of six, left to right as the viewer sees them. */
    private static final double[] HUB_U = {4.7, 6.9, 9.1, 11.3};
    /** The two added at the outer positions. */
    private static final double[] EXTRA_U = {2.5, 13.5};
    /**
     * Two extra top and two extra bottom knockouts on the head, two extra bottom ones per extension,
     * numbered after everything else, ground included, so saved panels keep every index they had.
     */
    public static final int HEAD_EXTRA = 4, EXT_EXTRA = 2;
    public static final int EXTRA_HUB_BASE = HUB_COUNT + EXT_HUBS * MAX_EXT;
    public static final int ALL_HUBS = EXTRA_HUB_BASE + HEAD_EXTRA + EXT_EXTRA * MAX_EXT;
    private static final int HEAD_EXTRA_SIZE = HEAD_EXTRA * (1 + PER_HUB), EXT_EXTRA_SIZE = EXT_EXTRA * (1 + PER_HUB);
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
        return ALL_HUBS;
    }

    /** Section a branch space sits in: 0 the head, 1.. the extensions below. */
    public static int sectionOfSlot(PanelSpec spec, int slot) {
        return slot < 0 ? 0 : slot / spec.slots();
    }

    public static int sectionOfHub(int hub) {
        if(hub >= EXTRA_HUB_BASE) {
            int e = hub - EXTRA_HUB_BASE;
            return e < HEAD_EXTRA ? 0 : 1 + (e - HEAD_EXTRA) / EXT_EXTRA;
        }
        return hub < HUB_COUNT ? 0 : 1 + (hub - HUB_COUNT) / EXT_HUBS;
    }

    /** Section a terminal belongs to. */
    public static int sectionOf(PanelSpec spec, int terminal) {
        var extra = extraTerminal(spec, terminal);
        if(extra != null)
            return extra[0];
        return terminal < extBase(spec) || terminal >= groundTerminal(spec) ? 0 : Math.min(MAX_EXT, 1 + (terminal - extBase(spec)) / extSize(spec));
    }

    // ---- the extra knockouts ----

    private static int extraBase(PanelSpec spec) {
        return groundTerminal(spec) + 1;
    }

    private static int extraCount(int section) {
        return section == 0 ? HEAD_EXTRA : EXT_EXTRA;
    }

    private static int extraStart(PanelSpec spec, int section) {
        return extraBase(spec) + (section == 0 ? 0 : HEAD_EXTRA_SIZE + (section - 1) * EXT_EXTRA_SIZE);
    }

    /** An extra hub's index within its section's extras, or -1 for an ordinary hub. */
    private static int extraLocal(int hub) {
        if(hub < EXTRA_HUB_BASE)
            return -1;
        int e = hub - EXTRA_HUB_BASE;
        return e < HEAD_EXTRA ? e : (e - HEAD_EXTRA) % EXT_EXTRA;
    }

    private static int extraHub(int section, int local) {
        return EXTRA_HUB_BASE + (section == 0 ? local : HEAD_EXTRA + (section - 1) * EXT_EXTRA + local);
    }

    /** {section, offset within that section's extra group} of a terminal in the extra region, or null. */
    private static int @Nullable [] extraTerminal(PanelSpec spec, int terminal) {
        int e = terminal - extraBase(spec);
        if(e < 0)
            return null;
        if(e < HEAD_EXTRA_SIZE)
            return new int[] {0, e};
        e -= HEAD_EXTRA_SIZE;
        int section = 1 + e / EXT_EXTRA_SIZE;
        return section > MAX_EXT ? null : new int[] {section, e % EXT_EXTRA_SIZE};
    }

    /** The panel's ground point, bonded to its neutral: the last terminal, added after the extensions' so saved panels keep their indices. */
    public static int groundTerminal(PanelSpec spec) {
        return extBase(spec) + MAX_EXT * extSize(spec);
    }

    /** The head's bottom knockouts, which an extension covers. */
    public static boolean isHeadBottomHub(int hub) {
        return (hub >= 4 && hub < EDGE_HUBS) || hub == EXTRA_HUB_BASE + 2 || hub == EXTRA_HUB_BASE + 3;
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
        if(terminal < extBase(spec) || terminal >= groundTerminal(spec))
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
        if(hub >= EXTRA_HUB_BASE)
            return extraStart(spec, sectionOfHub(hub)) + extraLocal(hub);
        if(hub < EDGE_HUBS)
            return hubBase(spec) + hub;
        if(hub < HUB_COUNT)
            return sideHubBase(spec) + (hub - EDGE_HUBS);
        int section = sectionOfHub(hub);
        return extStart(spec, section) + spec.slots() + (hub - HUB_COUNT) % EXT_HUBS;
    }

    /** Hub index of a hub terminal, or -1. */
    public static int hubAt(PanelSpec spec, int terminal) {
        var extra = extraTerminal(spec, terminal);
        if(extra != null)
            return extra[1] < extraCount(extra[0]) ? extraHub(extra[0], extra[1]) : -1;
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
        if(hub >= EXTRA_HUB_BASE) {
            int section = sectionOfHub(hub);
            return extraStart(spec, section) + extraCount(section) + extraLocal(hub) * PER_HUB + conductor;
        }
        if(hub < EDGE_HUBS)
            return edgeConductorBase(spec) + hub * PER_HUB + conductor;
        if(hub < HUB_COUNT)
            return sideConductorBase(spec) + (hub - EDGE_HUBS) * PER_HUB + conductor;
        int section = sectionOfHub(hub);
        return extStart(spec, section) + spec.slots() + EXT_HUBS + ((hub - HUB_COUNT) % EXT_HUBS) * PER_HUB + conductor;
    }

    /** Hub a hidden conductor terminal belongs to, or -1. */
    public static int conductorHub(PanelSpec spec, int terminal) {
        var extra = extraTerminal(spec, terminal);
        if(extra != null) {
            int off = extra[1] - extraCount(extra[0]);
            return off >= 0 ? extraHub(extra[0], off / PER_HUB) : -1;
        }
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
        var extra = extraTerminal(spec, terminal);
        if(extra != null) {
            int off = extra[1] - extraCount(extra[0]);
            return off >= 0 ? off % PER_HUB : -1;
        }
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

    // ---- GFCI load neutrals ----

    /**
     * One load-neutral terminal per space, numbered after everything else so saved panels keep
     * their indices. A GFCI breaker in the space lists it as a point: the circuit's neutral lands
     * on the breaker, which compares it with its hot, instead of on the bar.
     */
    public static int gfciBase(PanelSpec spec) {
        return extraBase(spec) + HEAD_EXTRA_SIZE + MAX_EXT * EXT_EXTRA_SIZE;
    }

    public static int gfciNeutralTerminal(PanelSpec spec, int slot) {
        return gfciBase(spec) + slot;
    }

    /** The space whose GFCI load neutral that terminal is, or -1. */
    public static int gfciSlot(PanelSpec spec, int terminal) {
        int slot = terminal - gfciBase(spec);
        return slot >= 0 && slot < maxSlots(spec) ? slot : -1;
    }

    public static int terminalCount(PanelSpec spec) {
        return gfciBase(spec) + maxSlots(spec);
    }

    public static boolean isPoint(PanelSpec spec, int terminal) {
        return (terminal >= 0 && terminal < hubBase(spec)) || branchSlot(spec, terminal) >= 0 || terminal == groundTerminal(spec) || gfciSlot(spec, terminal) >= 0;
    }

    /**
     * Names: hubs 0..3 across the top left to right, 4..7 across the bottom, 8..9 down the left side,
     * 10..11 down the right; then per extension four along its bottom, two down its left, two down its right.
     */
    public static Component hubName(int hub) {
        String key;
        int number;
        if(hub >= EXTRA_HUB_BASE) {
            // The outer two of six: 1 on the viewer's left, 6 on the right; the original four are 2 to 5.
            int section = sectionOfHub(hub), local = extraLocal(hub);
            number = local % 2 == 0 ? 1 : 6;
            if(section == 0)
                return Lang.builder().translate("gui.breaker_panel.hub." + (local < 2 ? "top" : "bottom"), number).style(ChatFormatting.AQUA).component();
            return Lang.builder().translate("gui.breaker_panel.hub.ext_bottom", section, number).style(ChatFormatting.AQUA).component();
        }
        if(hub >= HUB_COUNT) {
            int section = sectionOfHub(hub);
            int local = (hub - HUB_COUNT) % EXT_HUBS;
            key = local < 4 ? "ext_bottom" : local < 6 ? "ext_left" : "ext_right";
            number = local < 4 ? local + 2 : local < 6 ? local - 4 + 1 : local - 6 + 1;
            return Lang.builder().translate("gui.breaker_panel.hub." + key, section, number).style(ChatFormatting.AQUA).component();
        }
        if(hub < 4) {
            key = "top";
            number = hub + 2;
        } else if(hub < EDGE_HUBS) {
            key = "bottom";
            number = hub - 4 + 2;
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

    /** "Line" on a one-lug panel; L1, L2, L3 on the others, in the line colours: black, red, blue. */
    public static Component lineName(PanelSpec spec, int lug) {
        if(spec.lugs() == 1)
            return ConductorColors.hotLabel("breaker_panel.line", 0);
        return ConductorColors.hotLabel("breaker_panel.leg", lug, lug + 1);
    }

    public static int lineRgb(PanelSpec spec, int lug) {
        return ConductorColors.hotRgb(lug);
    }

    /** A branch space in its row's line colour; a pole row's real lug is only known to the block entity. */
    public static Component branchName(PanelSpec spec, int slot) {
        return ConductorColors.hotLabel("breaker_panel.branch", spec.leg(slot), slot + 1);
    }

    public static int branchRgb(PanelSpec spec, int slot) {
        return ConductorColors.hotRgb(spec.leg(slot));
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
        if(hub >= EXTRA_HUB_BASE) {
            int section = sectionOfHub(hub), local = extraLocal(hub);
            boolean top = section == 0 && local < 2;
            double x = 16 - EXTRA_U[local % 2];
            var box = top ? new AABB(x - 1, 15, HUB_Z1, x + 1, 16, HUB_Z2) : new AABB(x - 1, 0, HUB_Z1, x + 1, 1, HUB_Z2);
            return box.move(0, -16 * section, 0);
        }
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
            shape = Shapes.or(shape, box(com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs.flat(hub(h))));
        for(int h = EXTRA_HUB_BASE; h < EXTRA_HUB_BASE + HEAD_EXTRA; ++h)
            shape = Shapes.or(shape, box(com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs.flat(hub(h))));
        return shape;
    }

    /** An extension block in its own frame: the enclosure with bottom and side knockouts, no top ones. */
    public static VoxelShape extensionShape() {
        // Up to the top of its block: the model continues a pixel into the head to close the seam.
        var shape = box(new AABB(BODY.minX, BODY.minY, BODY.minZ, BODY.maxX, 16, BODY.maxZ));
        for(int h = 4; h < HUB_COUNT; ++h)
            shape = Shapes.or(shape, box(com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs.flat(hub(h))));
        for(int h = EXTRA_HUB_BASE + 2; h < EXTRA_HUB_BASE + HEAD_EXTRA; ++h)
            shape = Shapes.or(shape, box(com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs.flat(hub(h))));
        return shape;
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    public static TerminalBoundingBox[] terminals(PanelSpec spec) {
        var terminals = new TerminalBoundingBox[terminalCount(spec)];
        for(int lug = 0; lug < spec.lugs(); ++lug)
            terminals[spec.lineTerminal(lug)] = terminal(lineName(spec, lug), HIDDEN).withColor(lineRgb(spec, lug));
        terminals[spec.neutralTerminal()] = terminal(ConductorColors.neutralLabel("breaker_panel.neutral"), HIDDEN).withColor(ConductorColors.neutralRgb());
        terminals[groundTerminal(spec)] = terminal(ConductorColors.groundLabel("breaker_panel.ground"), HIDDEN).withColor(ConductorColors.groundRgb());
        for(int slot = 0; slot < maxSlots(spec); ++slot) {
            var hidden = HIDDEN.move(0, -16 * sectionOfSlot(spec, slot), 0);
            terminals[branchTerminal(spec, slot)] = terminal(branchName(spec, slot), hidden).withColor(branchRgb(spec, slot));
            var neutralName = Lang.builder().translate("gui.breaker_panel.gfci_neutral", slot + 1).component();
            terminals[gfciNeutralTerminal(spec, slot)] = terminal(neutralName, hidden).withColor(ConductorColors.neutralRgb());
        }
        for(int h = 0; h < maxHubs(); ++h) {
            terminals[hubTerminal(spec, h)] = terminal(hubName(h), hub(h)).withColor(com.nolanbaker.pgmodernized.conduit.ConduitSize.FOUR.bandColor());
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
