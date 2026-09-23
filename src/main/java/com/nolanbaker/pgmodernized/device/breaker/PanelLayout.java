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
 * Wiring enters only through conduit: four knockout hubs on the top edge and four on the bottom.
 * The line, neutral and circuit points are hidden terminals inside the enclosure that pulled
 * conductors are spliced to in the panel's splice editor.
 * <p>
 * Terminals: one per lug, the neutral, one per branch space; then the 8 hubs; then 12 hidden
 * landings per hub. The asset generator {@code tools/gen_breaker_panel_assets.py} carries the same
 * numbers; change both.
 */
public final class PanelLayout {
    public static final int NO_SLOT = -2;
    public static final int MAIN = -1;

    public static final int HUB_COUNT = 8;
    public static final int PER_HUB = ConductorColors.COUNT;

    public static final double FRONT_Z = 10;
    private static final AABB BODY = new AABB(1, 1, 10, 15, 15, 16);
    private static final AABB HIDDEN = new AABB(7.5, 7.5, 11, 8.5, 8.5, 12);
    private static final double[] HUB_U = {3.5, 6.5, 9.5, 12.5};
    private static final double HUB_Z1 = 12.5, HUB_Z2 = 14.5;

    private static final double LEFT_U1 = 1.5, RIGHT_U1 = 8.5, COLUMN_WIDTH = 6, ROW_TOP = 11;
    private static final double MAIN_U1 = 5, MAIN_U2 = 11, MAIN_V1 = 11.5, MAIN_V2 = 14;
    private static final double BREAKER_DEPTH = 1.5, VISUAL_INSET = 0.25;

    private PanelLayout() {}

    // ---- terminal indices ----

    public static int hubBase(PanelSpec spec) {
        return spec.branchFirst() + spec.slots();
    }

    public static int hubTerminal(PanelSpec spec, int hub) {
        return hubBase(spec) + hub;
    }

    /** Hub index of a hub terminal, or -1. */
    public static int hubAt(PanelSpec spec, int terminal) {
        int i = terminal - hubBase(spec);
        return i >= 0 && i < HUB_COUNT ? i : -1;
    }

    public static int conductorBase(PanelSpec spec) {
        return hubBase(spec) + HUB_COUNT;
    }

    public static int conductorTerminal(PanelSpec spec, int hub, int conductor) {
        return conductorBase(spec) + hub * PER_HUB + conductor;
    }

    /** Hub a hidden conductor terminal belongs to, or -1. */
    public static int conductorHub(PanelSpec spec, int terminal) {
        int i = terminal - conductorBase(spec);
        return i >= 0 && i < HUB_COUNT * PER_HUB ? i / PER_HUB : -1;
    }

    public static int conductorOf(PanelSpec spec, int terminal) {
        int i = terminal - conductorBase(spec);
        return i >= 0 ? i % PER_HUB : -1;
    }

    public static int terminalCount(PanelSpec spec) {
        return conductorBase(spec) + HUB_COUNT * PER_HUB;
    }

    public static boolean isPoint(PanelSpec spec, int terminal) {
        return terminal >= 0 && terminal < hubBase(spec);
    }

    /** Names: hubs 0..3 across the top left to right, 4..7 across the bottom. */
    public static Component hubName(int hub) {
        return Lang.builder().translate("gui.breaker_panel.hub." + (hub < 4 ? "top" : "bottom"), hub % 4 + 1).style(ChatFormatting.AQUA).component();
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
        return 9.0 / spec.rows();
    }

    public static double rowHeight(PanelSpec spec) {
        return pitch(spec) - 0.25;
    }

    public static boolean rightColumn(int slot) {
        return slot % 2 == 1;
    }

    public static double[] slotRect(PanelSpec spec, int slot) {
        int row = slot / 2;
        double vTop = ROW_TOP - pitch(spec) * row;
        double vBottom = vTop - rowHeight(spec);
        double u1 = rightColumn(slot) ? RIGHT_U1 : LEFT_U1;
        return new double[] {u1, vBottom, u1 + COLUMN_WIDTH, vTop};
    }

    /** The spaces a breaker of that many poles covers from a head space, as one rectangle down the column. */
    public static double[] spanRect(PanelSpec spec, int slot, int poles) {
        var head = slotRect(spec, slot);
        var last = slotRect(spec, slot + 2 * (poles - 1));
        return new double[] {head[0], last[1], head[2], head[3]};
    }

    public static double[] mainRect() {
        return new double[] {MAIN_U1, MAIN_V1, MAIN_U2, MAIN_V2};
    }

    private static double[] rect(PanelSpec spec, int slot, int poles) {
        return slot == MAIN ? mainRect() : spanRect(spec, slot, poles);
    }

    public static AABB breakerBox(PanelSpec spec, int slot) {
        return breakerBox(spec, slot, 1);
    }

    public static AABB breakerBox(PanelSpec spec, int slot, int poles) {
        var r = rect(spec, slot, poles);
        double u1 = r[0] + VISUAL_INSET, u2 = r[2] - VISUAL_INSET;
        return new AABB(16 - u2, r[1], FRONT_Z - BREAKER_DEPTH, 16 - u1, r[3], FRONT_Z);
    }

    public static Vec3 breakerCenter(PanelSpec spec, int slot, int poles) {
        var box = breakerBox(spec, slot, poles);
        return new Vec3((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, box.minZ);
    }

    // ---- hubs and shape ----

    private static AABB hub(int hub) {
        double x = 16 - HUB_U[hub % 4];
        return hub < 4
                ? new AABB(x - 1, 15, HUB_Z1, x + 1, 16, HUB_Z2)
                : new AABB(x - 1, 0, HUB_Z1, x + 1, 1, HUB_Z2);
    }

    public static VoxelShape shape(PanelSpec spec) {
        var shape = box(BODY);
        for(int h = 0; h < HUB_COUNT; ++h)
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
        for(int slot = 0; slot < spec.slots(); ++slot) {
            var label = Lang.builder().translate("breaker_panel.branch", slot + 1).style(ChatFormatting.GRAY).component();
            terminals[spec.branchTerminal(slot)] = terminal(label, HIDDEN).withColor(IDecoratedTerminal.GRAY);
        }
        for(int h = 0; h < HUB_COUNT; ++h) {
            terminals[hubTerminal(spec, h)] = terminal(hubName(h), hub(h)).withColor(0x2FB8D6);
            for(int k = 0; k < PER_HUB; ++k) {
                var label = Lang.builder().add(hubName(h)).text(" ").add(ConductorColors.name(k)).component();
                terminals[conductorTerminal(spec, h, k)] = terminal(label, HIDDEN).withColor(ConductorColors.rgb(k));
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

    public static int slotAt(PanelSpec spec, Direction facing, Direction hitFace, Vec3 local) {
        if(hitFace != facing)
            return NO_SLOT;
        var north = toNorthFrame(local.scale(16), facing);
        double u = 16 - north.x, v = north.y;
        if(inside(mainRect(), u, v))
            return MAIN;
        for(int slot = 0; slot < spec.slots(); ++slot) {
            if(inside(slotRect(spec, slot), u, v))
                return slot;
        }
        return NO_SLOT;
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
