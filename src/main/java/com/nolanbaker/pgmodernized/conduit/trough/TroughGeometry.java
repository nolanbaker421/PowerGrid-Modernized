package com.nolanbaker.pgmodernized.conduit.trough;

import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.splice.DeviceHubs;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

import java.util.HashMap;
import java.util.Map;

/**
 * The wire trough in its north frame, in pixels: a gutter 16 wide, 8 tall and 6 deep on the back
 * wall of the block, y 4..12, with its lid to the north. A viewer at the lid has +x on their left.
 * A row of sections is one host: section {@code s} owns terminals {@code s * 450} on, eighteen
 * knockouts (eight slots on top, eight on the bottom, one on each end) each followed by its
 * twenty-four hidden landings. Which top and bottom slots exist, and what size they take, is the
 * face's {@link Pattern}; the numbering never moves. Mirrored by {@code tools/gen_trough_assets.py}.
 */
public final class TroughGeometry {
    public static final int MAX_SECTIONS = 6;
    public static final int FACE_SLOTS = 8;
    public static final int TOP_BASE = 0, BOTTOM_BASE = 8, LEFT_END = 16, RIGHT_END = 17, HUBS_PER_SECTION = 18;
    public static final int LANDINGS = 24;
    public static final int SECTION_TERMINALS = HUBS_PER_SECTION * (1 + LANDINGS);
    public static final int TERMINAL_COUNT = MAX_SECTIONS * SECTION_TERMINALS;
    public static final AABB BODY = new AABB(0, 4, 10, 16, 12, 16);
    private static final AABB HIDDEN = new AABB(7.99, 7.99, 15.9, 8.01, 8.01, 15.92);
    private static final String[] FACE_KEYS = {"top", "bottom", "left", "right"};

    /** What a top or bottom face offers: positions across the face from the viewer's left, rows front to back. */
    public enum Pattern implements StringRepresentable {
        TWO("two", ConduitSize.TWO, new double[] {4, 12}, new double[] {13}, 1.5),
        FOUR("four", ConduitSize.ONE, new double[] {4, 12}, new double[] {11.5, 14.5}, 1.0),
        EIGHT("eight", ConduitSize.HALF, new double[] {2, 6, 10, 14}, new double[] {11.5, 14.5}, 0.75);

        private final String id;
        private final ConduitSize size;
        private final double[] across, rows;
        private final double half;

        Pattern(String id, ConduitSize size, double[] across, double[] rows, double half) {
            this.id = id;
            this.size = size;
            this.across = across;
            this.rows = rows;
            this.half = half;
        }

        public ConduitSize size() {
            return size;
        }

        public int count() {
            return across.length * rows.length;
        }

        /** Viewer coordinate across the face, from the viewer's left. */
        public double u(int slot) {
            return across[slot % across.length];
        }

        public double z(int slot) {
            return rows[slot / across.length];
        }

        public double half() {
            return half;
        }

        public Pattern next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public Component label() {
            return Lang.builder().translate("gui.trough.pattern." + id).component();
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    private TroughGeometry() {}

    // ---- numbering ----

    public static int sectionOf(int terminal) {
        return terminal / SECTION_TERMINALS;
    }

    public static int localOf(int terminal) {
        return terminal % SECTION_TERMINALS;
    }

    public static boolean isHub(int terminal) {
        return terminal >= 0 && terminal < TERMINAL_COUNT && localOf(terminal) < HUBS_PER_SECTION;
    }

    public static int hubTerminal(int hub) {
        return hub / HUBS_PER_SECTION * SECTION_TERMINALS + hub % HUBS_PER_SECTION;
    }

    public static int hubAt(int terminal) {
        if(terminal < 0 || terminal >= TERMINAL_COUNT)
            return -1;
        int local = localOf(terminal);
        return local < HUBS_PER_SECTION ? sectionOf(terminal) * HUBS_PER_SECTION + local : -1;
    }

    public static int conductorTerminal(int hub, int conductor) {
        return hub / HUBS_PER_SECTION * SECTION_TERMINALS + HUBS_PER_SECTION + hub % HUBS_PER_SECTION * LANDINGS + conductor;
    }

    public static int hubOf(int terminal) {
        if(terminal < 0 || terminal >= TERMINAL_COUNT)
            return -1;
        int local = localOf(terminal);
        return local < HUBS_PER_SECTION ? -1 : sectionOf(terminal) * HUBS_PER_SECTION + (local - HUBS_PER_SECTION) / LANDINGS;
    }

    public static int conductorOf(int terminal) {
        if(terminal < 0 || terminal >= TERMINAL_COUNT)
            return -1;
        int local = localOf(terminal);
        return local < HUBS_PER_SECTION ? -1 : (local - HUBS_PER_SECTION) % LANDINGS;
    }

    /** 0 top, 1 bottom, 2 the viewer's left end, 3 the right end. */
    public static int face(int localHub) {
        return localHub < BOTTOM_BASE ? 0 : localHub < LEFT_END ? 1 : localHub == LEFT_END ? 2 : 3;
    }

    public static int slot(int localHub) {
        return localHub < BOTTOM_BASE ? localHub : localHub < LEFT_END ? localHub - BOTTOM_BASE : 0;
    }

    /** The local knockouts on a face: top, bottom, or an end. */
    public static int[] hubsOn(int face) {
        return switch(face) {
            case 0 -> range(TOP_BASE, FACE_SLOTS);
            case 1 -> range(BOTTOM_BASE, FACE_SLOTS);
            case 2 -> new int[] {LEFT_END};
            default -> new int[] {RIGHT_END};
        };
    }

    private static int[] range(int from, int count) {
        var out = new int[count];
        for(int i = 0; i < count; ++i)
            out[i] = from + i;
        return out;
    }

    // ---- geometry ----

    /** The knockout mark in the north frame, standing a pixel on its face, or null when the pattern has no such slot. */
    @Nullable
    public static AABB hubBox(Pattern top, Pattern bottom, int localHub) {
        int face = face(localHub), slot = slot(localHub);
        switch(face) {
            case 0 -> {
                if(slot >= top.count())
                    return null;
                double x = 16 - top.u(slot), z = top.z(slot), w = top.half();
                return new AABB(x - w, 12, z - w, x + w, 13, z + w);
            }
            case 1 -> {
                if(slot >= bottom.count())
                    return null;
                double x = 16 - bottom.u(slot), z = bottom.z(slot), w = bottom.half();
                return new AABB(x - w, 3, z - w, x + w, 4, z + w);
            }
            case 2 -> {
                return new AABB(15, 6.5, 11.5, 16, 9.5, 14.5);
            }
            default -> {
                return new AABB(0, 6.5, 11.5, 1, 9.5, 14.5);
            }
        }
    }

    /** The largest conduit a local knockout takes, or null where the pattern has none. */
    @Nullable
    public static ConduitSize maxConduit(Pattern top, Pattern bottom, int localHub) {
        return switch(face(localHub)) {
            case 0 -> slot(localHub) < top.count() ? top.size() : null;
            case 1 -> slot(localHub) < bottom.count() ? bottom.size() : null;
            default -> ConduitSize.FOUR;
        };
    }

    /** Viewer coordinate across the block of a top or bottom knockout, for lining nipples up with a panel's. */
    public static double u(Pattern pattern, int slot) {
        return pattern.u(slot);
    }

    public static Component hubName(int hub) {
        int section = hub / HUBS_PER_SECTION, local = hub % HUBS_PER_SECTION;
        return Lang.builder().translate("gui.trough.hub." + FACE_KEYS[face(local)], slot(local) + 1, section + 1).style(ChatFormatting.AQUA).component();
    }

    private static final Map<Long, TerminalBoundingBox[]> NORTH = new HashMap<>();
    private static final Map<Long, TerminalBoundingBox[]> ROTATED = new HashMap<>();
    private static final Map<Long, VoxelShaper> SHAPES = new HashMap<>();

    private static long key(Pattern top, Pattern bottom, int extra) {
        return top.ordinal() * 100L + bottom.ordinal() * 10L + extra;
    }

    /** A section's own terminals in the north frame, numbered as section 0; absent knockouts and every landing are specks. */
    public static synchronized TerminalBoundingBox[] terminals(Pattern top, Pattern bottom) {
        return NORTH.computeIfAbsent(key(top, bottom, 0), k -> {
            var terminals = new TerminalBoundingBox[SECTION_TERMINALS];
            for(int h = 0; h < HUBS_PER_SECTION; ++h) {
                var box = hubBox(top, bottom, h);
                var size = maxConduit(top, bottom, h);
                terminals[h] = terminal(hubName(h), box == null ? HIDDEN : box).withColor((size == null ? ConduitSize.TWO : size).bandColor());
                for(int c = 0; c < LANDINGS; ++c) {
                    var name = Lang.builder().add(hubName(h)).text(" ").add(com.nolanbaker.pgmodernized.conduit.ConductorColors.name(c)).component();
                    terminals[conductorTerminal(h, c)] = terminal(name, HIDDEN).withColor(com.nolanbaker.pgmodernized.conduit.ConductorColors.rgb(c));
                }
            }
            return terminals;
        });
    }

    /** The same, turned for a facing: the quarter turns Power Grid's horizontal collections make. */
    public static synchronized TerminalBoundingBox[] terminals(Direction facing, Pattern top, Pattern bottom) {
        return ROTATED.computeIfAbsent(key(top, bottom, 1 + facing.get2DDataValue()), k -> {
            var north = terminals(top, bottom);
            var out = new TerminalBoundingBox[north.length];
            for(int i = 0; i < north.length; ++i) {
                out[i] = switch(facing) {
                    case SOUTH -> north[i].rotateAroundY(180);
                    case EAST -> north[i].rotateAroundY(90);
                    case WEST -> north[i].rotateAroundY(-90);
                    default -> north[i];
                };
            }
            return out;
        });
    }

    public static synchronized VoxelShaper shaper(Pattern top, Pattern bottom) {
        return SHAPES.computeIfAbsent(key(top, bottom, 0), k -> {
            var shape = box(BODY);
            for(int h = 0; h < HUBS_PER_SECTION; ++h) {
                var hub = hubBox(top, bottom, h);
                if(hub != null)
                    shape = Shapes.or(shape, box(DeviceHubs.flat(hub)));
            }
            return VoxelShaper.forHorizontal(shape, Direction.NORTH);
        });
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }
}
