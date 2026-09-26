package com.nolanbaker.pgmodernized.device.transformer;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Shapes and terminal boxes per size, in the north-facing frame in pixels: the back (wall or
 * pole) is the south side, the front faces north, and someone looking at the front has +x on their
 * left. Models may reach from -16 to 32 px, so a unit is one base block whose model overhangs into
 * neighbouring cells; those cells get invisible fillers whose collision is the overhang and whose
 * terminals delegate to the base. Sized after Create: PowerPlantGrid's transformers. Mirrored by
 * {@code tools/gen_transformer_assets.py}; change both.
 */
public final class TransformerGeometry {
    private static final double[] HUB_U = {3.5, 6.5, 9.5, 12.5};
    /** A neighbouring cell gets a filler when the unit reaches this far into it, in px. */
    private static final double CELL_MIN = 3;

    private TransformerGeometry() {}

    // ---- boxes per size, standing ----

    public static AABB body(TransformerSize size) {
        return switch(size) {
            case POLE_S -> new AABB(4.5, 0, 3.5, 11.5, 10.5, 11.5);
            case POLE_M -> new AABB(3.75, 0, 2.75, 12.25, 12, 12.25);
            case POLE_L -> new AABB(2.5, 0, 1.5, 13.5, 18, 13.5);
            case PAD -> new AABB(-3, 2, 1, 19, 18, 19);
            case POWER_S -> new AABB(-7.5, 3.5, 1.5, 23.5, 24.5, 22.5);
            case POWER_L -> new AABB(-10, 3.5, 1.5, 26, 24.5, 26.5);
            case DRY -> new AABB(2, 0, 4, 14, 30, 16);
        };
    }

    public static AABB lid(TransformerSize size) {
        return switch(size) {
            case POLE_S -> new AABB(4.25, 10.5, 3.25, 11.75, 11.5, 11.75);
            case POLE_M -> new AABB(3.5, 12, 2.5, 12.5, 13, 12.5);
            case POLE_L -> new AABB(2.25, 18, 1.25, 13.75, 19.5, 13.75);
            case PAD -> new AABB(-4, 18, 0, 20, 19.5, 20);
            case POWER_S -> new AABB(-9, 24.5, 0, 25, 27, 24);
            case POWER_L -> new AABB(-11.5, 24.5, 0, 27.5, 27, 28);
            case DRY -> null;
        };
    }

    public static AABB skid(TransformerSize size) {
        return switch(size) {
            case PAD -> new AABB(-4, 0, 0, 20, 2, 20);
            case POWER_S -> new AABB(-9, 0, 0, 25, 3.5, 24);
            case POWER_L -> new AABB(-11.5, 0, 0, 27.5, 3.5, 28);
            default -> null;
        };
    }

    /** How a pole can moves when hung on the pole behind it: up and back against the wall. */
    public static Vec3 hungShift(TransformerSize size) {
        return switch(size) {
            case POLE_S -> new Vec3(0, 2, 4.5);
            case POLE_M -> new Vec3(0, 0, 3.75);
            case POLE_L -> new Vec3(0, 9, 2.5);
            default -> Vec3.ZERO;
        };
    }

    /** Centre x of the high-side bushings, viewer's left to right. */
    private static double[] hvX(TransformerSize size, int count) {
        return switch(size) {
            case POLE_S -> new double[] {10.25, 5.75};
            case POLE_M -> new double[] {10.25, 5.75};
            case POLE_L -> new double[] {11.5, 4.5};
            case PAD -> count == 2 ? new double[] {12, 4} : new double[] {14, 8, 2};
            case POWER_S -> new double[] {18, 8, -2};
            case POWER_L -> new double[] {20, 8, -4};
            case DRY -> new double[count];
        };
    }

    /** Centre x of the low-side bushings or studs, viewer's left to right. */
    private static double[] lvX(TransformerSize size, int count) {
        return switch(size) {
            case POLE_S -> new double[] {10.25, 8, 5.75};
            case POLE_M -> new double[] {10.75, 8, 5.25};
            case POLE_L -> new double[] {11.5, 8, 4.5};
            case PAD -> count == 3 ? new double[] {14, 8, 2} : new double[] {15.5, 10.5, 5.5, 0.5};
            case POWER_S -> new double[] {19, 11.67, 4.33, -3};
            case POWER_L -> new double[] {21, 12.33, 3.67, -5};
            case DRY -> new double[count];
        };
    }

    /** The whole high-side bushing, from the lid to the cap; the terminal is its cap. */
    public static AABB hvBushing(TransformerSize size, int index, int count) {
        double x = hvX(size, count)[index];
        double top = lid(size).maxY;
        return switch(size) {
            case POLE_S -> new AABB(x - 0.75, top, 8, x + 0.75, top + 2, 9.5);
            case POLE_M -> new AABB(x - 1, top, 8.75, x + 1, top + 3, 10.75);
            case POLE_L -> new AABB(x - 1, top, 9.5, x + 1, top + 3, 11.5);
            case PAD -> new AABB(x - 2.5, top, 12.5, x + 2.5, 28, 17.5);
            case POWER_S -> new AABB(x - 2.5, top, 16.5, x + 2.5, 32, 21.5);
            case POWER_L -> new AABB(x - 2.5, top, 20.5, x + 2.5, 32, 25.5);
            case DRY -> null;
        };
    }

    /** The low-side bushing on a tank lid, or the stud on a can's front. */
    public static AABB lvBushing(TransformerSize size, int index, int count) {
        double x = lvX(size, count)[index];
        var body = body(size);
        return switch(size) {
            case POLE_S -> new AABB(x - 0.75, 3.5, body.minZ - 1, x + 0.75, 4.75, body.minZ);
            case POLE_M -> new AABB(x - 0.75, 4, body.minZ - 1, x + 0.75, 5.5, body.minZ);
            case POLE_L -> new AABB(x - 0.75, 6, body.minZ - 1, x + 0.75, 7.5, body.minZ);
            case PAD -> new AABB(x - 1.5, 19.5, 2.5, x + 1.5, 24, 5.5);
            case POWER_S -> new AABB(x - 1.5, 27, 2.5, x + 1.5, 30, 5.5);
            case POWER_L -> new AABB(x - 1.5, 27, 2.5, x + 1.5, 30, 5.5);
            case DRY -> null;
        };
    }

    /** The clickable part of a bushing: its top. */
    private static AABB cap(AABB bushing, double height) {
        return new AABB(bushing.minX, bushing.maxY - height, bushing.minZ, bushing.maxX, bushing.maxY, bushing.maxZ);
    }

    public static boolean isPole(TransformerSize size) {
        return size.isPole();
    }

    private static AABB shift(AABB box, Vec3 by) {
        return box.move(by.x, by.y, by.z);
    }

    // ---- knockouts and hidden points (dry-type only) ----

    public static AABB[] hubs(TransformerSize size) {
        if(!size.hasHubs())
            return new AABB[0];
        var hubs = new AABB[8];
        for(int i = 0; i < 4; ++i) {
            double x = 16 - HUB_U[i];
            hubs[i] = new AABB(x - 1, 0, 9, x + 1, 1, 11);
            hubs[4 + i] = new AABB(x - 1, 3, 3, x + 1, 5, 4);
        }
        return hubs;
    }

    public static AABB hidden() {
        return new AABB(7.5, 7.5, 11, 8.5, 8.5, 12);
    }

    // ---- the assembled unit ----

    /** Every box of the unit in the north frame: skid, body, lid, bushings. */
    public static List<AABB> boxes(TransformerSize size, TransformerKind kind, boolean hung) {
        var out = new ArrayList<AABB>();
        var by = hung ? hungShift(size) : Vec3.ZERO;
        var skid = skid(size);
        if(skid != null)
            out.add(skid);
        out.add(shift(body(size), by));
        var lid = lid(size);
        if(lid != null) {
            out.add(shift(lid, by));
            for(int k = 0; k < kind.primaries(); ++k)
                out.add(shift(hvBushing(size, k, kind.primaries()), by));
            for(int j = 0; j < kind.secondaries(); ++j)
                out.add(shift(lvBushing(size, j, kind.secondaries()), by));
        }
        return out;
    }

    /** The unit's extent in the north frame, px. */
    public static AABB bounds(TransformerSize size, TransformerKind kind, boolean hung) {
        AABB all = null;
        for(var box : boxes(size, kind, hung))
            all = all == null ? box : all.minmax(box);
        return all;
    }

    /** The cells beyond the base block the unit reaches into by at least a few pixels, as north-frame offsets. */
    public static List<Vec3i> cells(TransformerSize size, TransformerKind kind, boolean hung) {
        var bounds = bounds(size, kind, hung);
        var cells = new ArrayList<Vec3i>();
        for(int dx = -1; dx <= 1; ++dx) {
            for(int dy = 0; dy <= 1; ++dy) {
                for(int dz = -1; dz <= 1; ++dz) {
                    if(dx == 0 && dy == 0 && dz == 0)
                        continue;
                    double ox = overlap(bounds.minX, bounds.maxX, dx * 16);
                    double oy = overlap(bounds.minY, bounds.maxY, dy * 16);
                    double oz = overlap(bounds.minZ, bounds.maxZ, dz * 16);
                    boolean inX = dx == 0 ? ox > 0 : ox >= CELL_MIN;
                    boolean inZ = dz == 0 ? oz > 0 : oz >= CELL_MIN;
                    boolean inY = dy == 0 ? oy > 0 : oy >= 1;
                    if(inX && inY && inZ)
                        cells.add(new Vec3i(dx, dy, dz));
                }
            }
        }
        return cells;
    }

    private static double overlap(double min, double max, double cellStart) {
        return Math.min(max, cellStart + 16) - Math.max(min, cellStart);
    }

    /** The unit's collision inside one cell (the base at offset zero), in that cell's own frame, north-facing. */
    public static VoxelShape cellShape(TransformerSize size, TransformerKind kind, boolean hung, Vec3i cell) {
        VoxelShape shape = Shapes.empty();
        var cellBox = new AABB(cell.getX() * 16, cell.getY() * 16, cell.getZ() * 16, cell.getX() * 16 + 16, cell.getY() * 16 + 16, cell.getZ() * 16 + 16);
        for(var box : boxes(size, kind, hung)) {
            if(!box.intersects(cellBox))
                continue;
            var part = box.intersect(cellBox).move(-cell.getX() * 16, -cell.getY() * 16, -cell.getZ() * 16);
            if(part.getXsize() <= 0 || part.getYsize() <= 0 || part.getZsize() <= 0)
                continue;
            shape = Shapes.joinUnoptimized(shape, px(part), BooleanOp.OR);
        }
        return shape.optimize();
    }

    /** The base block's collision: its own cell, plus the knockouts of a cabinet. */
    public static VoxelShape shape(TransformerSize size, TransformerKind kind, boolean hung) {
        var shape = cellShape(size, kind, hung, Vec3i.ZERO);
        for(var hub : hubs(size))
            shape = Shapes.or(shape, px(hub));
        return shape;
    }

    /** A north-frame shape turned to face that way, as Power Grid turns the terminals. */
    public static VoxelShape rotate(VoxelShape north, Direction facing) {
        int turns = switch(facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        var shape = north;
        for(int i = 0; i < turns; ++i) {
            VoxelShape[] out = {Shapes.empty()};
            shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                    out[0] = Shapes.joinUnoptimized(out[0], Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2), BooleanOp.OR));
            shape = out[0].optimize();
        }
        return shape;
    }

    /** A north-frame cell offset turned to world for that facing. */
    public static Vec3i rotateCell(Vec3i cell, Direction facing) {
        int x = cell.getX(), z = cell.getZ();
        return switch(facing) {
            case EAST -> new Vec3i(-z, cell.getY(), x);
            case SOUTH -> new Vec3i(-x, cell.getY(), -z);
            case WEST -> new Vec3i(z, cell.getY(), -x);
            default -> cell;
        };
    }

    /** The inverse of {@link #rotateCell}. */
    public static Vec3i unrotateCell(Vec3i world, Direction facing) {
        int x = world.getX(), z = world.getZ();
        return switch(facing) {
            case EAST -> new Vec3i(z, world.getY(), -x);
            case SOUTH -> new Vec3i(-x, world.getY(), -z);
            case WEST -> new Vec3i(-z, world.getY(), x);
            default -> world;
        };
    }

    // ---- terminals ----

    /** The winding terminals in the north frame: bushing caps and studs, or hidden points inside a cabinet. */
    public static TerminalBoundingBox[] terminals(TransformerSpec spec, boolean hung) {
        var kind = spec.kind();
        var size = spec.size();
        var by = hung ? hungShift(size) : Vec3.ZERO;
        var terminals = new TerminalBoundingBox[kind.pointCount()];
        for(int k = 0; k < kind.primaries(); ++k) {
            var box = size.hasHubs() ? hidden() : shift(cap(hvBushing(size, k, kind.primaries()), size.isPole() ? 1 : 2), by);
            terminals[kind.primaryTerminal(k)] = terminal(name(kind.primaryKey(k), TransformerKind.PRIMARY_RGB), box).withColor(TransformerKind.PRIMARY_RGB);
        }
        for(int j = 0; j < kind.secondaries(); ++j) {
            var box = size.hasHubs() ? hidden() : shift(size.isPole() ? lvBushing(size, j, kind.secondaries()) : cap(lvBushing(size, j, kind.secondaries()), 1.5), by);
            terminals[kind.secondaryTerminal(j)] = terminal(name(kind.secondaryKey(j), kind.secondaryTextRgb(j)), box).withColor(kind.secondaryRgb(j));
        }
        return terminals;
    }

    /** Create "south locations" of the two tap value boxes on the front of the base cell: HV on the viewer's left, LV on the right. */
    public static Vec3 tapBox(TransformerSize size, boolean hung, boolean highSide) {
        double x = highSide ? 12 : 4;
        double y, frontZ;
        if(size.isPole()) {
            var body = shift(body(size), hung ? hungShift(size) : Vec3.ZERO);
            y = body.minY + body.getYsize() * 0.6;
            frontZ = body.minZ;
        } else if(size == TransformerSize.DRY) {
            y = 11;
            frontZ = 4;
        } else {
            y = 9;
            frontZ = 0;
        }
        return new Vec3(x, y, 16 - frontZ);
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static Component name(String key, int rgb) {
        return Component.translatable("powergrid." + key).withStyle(Style.EMPTY.withColor(rgb));
    }

    private static VoxelShape px(AABB box) {
        return Block.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
}
