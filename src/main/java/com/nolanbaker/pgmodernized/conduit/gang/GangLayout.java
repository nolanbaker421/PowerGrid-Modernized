package com.nolanbaker.pgmodernized.conduit.gang;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

/**
 * Geometry and terminal numbering of the gang boxes, in the floor frame: body on y = 0, devices on
 * the plate on top, knockouts on the north and south edges and, on the 3-gang, the east and west
 * edges too. Four pixels a gang, centred on x = 8, so the 4-gang spans the block.
 * <p>
 * Terminals: 0 and 1 are the cord poles Power Grid's power cord expects (wired to the first
 * receptacle), then four points a gang, then four knockouts each followed by its hidden landings.
 */
public final class GangLayout {
    public static final int MAX_GANGS = 4, MAX_HUBS = 4;
    public static final int CORD_A = 0, CORD_B = 1, POINT_BASE = 2;
    public static final int HUB_BASE = POINT_BASE + MAX_GANGS * GangDevice.MAX_POINTS;
    public static final int HUB_STRIDE = 1 + ConductorColors.COUNT;
    public static final int TERMINAL_COUNT = HUB_BASE + MAX_HUBS * HUB_STRIDE;
    public static final String[] HUB_KEYS = {"conduit_socket.hub", "conduit_socket.hub2", "gang.hub_left", "gang.hub_right"};

    public static final double BODY_Y1 = 3, PLATE_Y1 = 3.5;
    private static final AABB HIDDEN = new AABB(7.99, 1.49, 7.99, 8.01, 1.51, 8.01);

    private GangLayout() {}

    public static double left(int gangs) {
        return 8 - 2.0 * gangs;
    }

    public static AABB body(int gangs) {
        return new AABB(left(gangs), 0, 5, 16 - left(gangs), BODY_Y1, 11);
    }

    public static AABB plate(int gangs) {
        return new AABB(left(gangs) + 0.5, BODY_Y1, 5.5, 16 - left(gangs) - 0.5, PLATE_Y1, 10.5);
    }

    /** The plate area of one gang. */
    public static AABB slot(int gangs, int slot) {
        double x = left(gangs) + 4 * slot;
        return new AABB(x, BODY_Y1, 5.5, x + 4, PLATE_Y1, 10.5);
    }

    public static double slotCentreX(int gangs, int slot) {
        return left(gangs) + 4 * slot + 2;
    }

    /** Knockouts per size: north and south always; east and west on the 3-gang, the only one with room beside its body. */
    public static int hubCount(int gangs) {
        return gangs == 3 ? 4 : 2;
    }

    public static AABB hub(int gangs, int hub) {
        double l = left(gangs);
        return switch(hub) {
            case 0 -> new AABB(7, 0.5, 4, 9, 2.5, 5);
            case 1 -> new AABB(7, 0.5, 11, 9, 2.5, 12);
            case 2 -> new AABB(l - 1, 0.5, 7, l, 2.5, 9);
            default -> new AABB(16 - l, 0.5, 7, 17 - l, 2.5, 9);
        };
    }

    public static VoxelShape shape(int gangs) {
        var shape = Shapes.or(box(body(gangs)), box(plate(gangs)));
        for(int h = 0; h < hubCount(gangs); ++h)
            shape = Shapes.or(shape, box(hub(gangs, h)));
        return shape;
    }

    // ---- numbering ----

    public static int point(int slot, int k) {
        return POINT_BASE + slot * GangDevice.MAX_POINTS + k;
    }

    public static boolean isPoint(int terminal) {
        return terminal >= POINT_BASE && terminal < HUB_BASE;
    }

    public static int slotOfPoint(int terminal) {
        return (terminal - POINT_BASE) / GangDevice.MAX_POINTS;
    }

    public static int indexOfPoint(int terminal) {
        return (terminal - POINT_BASE) % GangDevice.MAX_POINTS;
    }

    public static int hubTerminal(int hub) {
        return HUB_BASE + hub * HUB_STRIDE;
    }

    /** Hub index of a hub terminal, or -1. */
    public static int hubAt(int terminal) {
        if(terminal < HUB_BASE || terminal >= TERMINAL_COUNT)
            return -1;
        return (terminal - HUB_BASE) % HUB_STRIDE == 0 ? (terminal - HUB_BASE) / HUB_STRIDE : -1;
    }

    public static int conductorTerminal(int hub, int conductor) {
        return hubTerminal(hub) + 1 + conductor;
    }

    /** Hub a landing terminal belongs to, or -1 for anything else. */
    public static int hubOf(int terminal) {
        if(terminal < HUB_BASE || terminal >= TERMINAL_COUNT)
            return -1;
        return (terminal - HUB_BASE) % HUB_STRIDE == 0 ? -1 : (terminal - HUB_BASE) / HUB_STRIDE;
    }

    public static int conductorOf(int terminal) {
        return (terminal - HUB_BASE) % HUB_STRIDE - 1;
    }

    public static Component hubName(int hub) {
        return Lang.builder().translate(HUB_KEYS[hub]).style(ChatFormatting.AQUA).component();
    }

    /** The collection for a size: real knockouts where the size has them, specks everywhere else. */
    public static TerminalBoundingBox[] terminals(int gangs) {
        var terminals = new TerminalBoundingBox[TERMINAL_COUNT];
        terminals[CORD_A] = terminal(name("conduit_socket.pole_a", ChatFormatting.RED), HIDDEN).withColor(IDecoratedTerminal.RED);
        terminals[CORD_B] = terminal(name("conduit_socket.pole_b", ChatFormatting.BLUE), HIDDEN).withColor(IDecoratedTerminal.BLUE);
        for(int slot = 0; slot < MAX_GANGS; ++slot)
            for(int k = 0; k < GangDevice.MAX_POINTS; ++k)
                terminals[point(slot, k)] = terminal(Lang.builder().translate("gang.point", slot + 1, k + 1).component(), HIDDEN);
        for(int h = 0; h < MAX_HUBS; ++h) {
            boolean present = h < hubCount(gangs);
            terminals[hubTerminal(h)] = terminal(hubName(h), present ? hub(gangs, h) : HIDDEN).withColor(ConduitSize.ONE.bandColor());
            for(int k = 0; k < ConductorColors.COUNT; ++k)
                terminals[conductorTerminal(h, k)] = terminal(ConductorColors.name(k), HIDDEN).withColor(ConductorColors.rgb(k));
        }
        return terminals;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static Component name(String key, ChatFormatting style) {
        return Lang.builder().translate(key).style(style).component();
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }
}
