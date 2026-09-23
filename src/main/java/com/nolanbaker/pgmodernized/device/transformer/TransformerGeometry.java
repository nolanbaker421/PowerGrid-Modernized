package com.nolanbaker.pgmodernized.device.transformer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

import java.util.List;

/**
 * Shapes and terminal boxes per mount and kind, in the north-facing frame in pixels: the block's
 * back (the wall or the pole) is the south side, the front faces north, and someone looking at the
 * front has +x on their left. Sized like Create: PowerPlantGrid's transformers: the ground units
 * are two blocks tall (the base block carries the model, a filler block above carries the upper
 * collision), the pole can is half a block wide and a block tall hung on its pole, and the
 * three-phase pole bank is three such cans across three blocks with fillers either side.
 * Mirrored by {@code tools/gen_transformer_assets.py}; change both.
 */
public final class TransformerGeometry {
    /** A cell of the unit beyond the base block. */
    public enum Part implements net.minecraft.util.StringRepresentable {
        ABOVE, LEFT, RIGHT;

        public String id() {
            return name().toLowerCase();
        }

        @Override
        public String getSerializedName() {
            return id();
        }
    }

    private static final double[] HUB_U = {3.5, 6.5, 9.5, 12.5};

    // Dry-type: an indoor cabinet two blocks tall against the wall. Knockouts on the bottom and the front.
    private static final AABB DRY_BODY = new AABB(2, 0, 4, 14, 16, 16);
    private static final AABB DRY_UPPER = new AABB(2, 0, 4, 14, 14, 16);
    private static final AABB DRY_HIDDEN = new AABB(7.5, 7.5, 11, 8.5, 8.5, 12);
    private static final Vec3 DRY_VALUE_BOX = new Vec3(8, 11, 12);

    // Pad-mount: an oil tank on a skid, radiators on both sides, bushings on the lid one block up.
    private static final AABB PAD_SKID = new AABB(0, 0, 0, 16, 1.5, 16);
    private static final AABB PAD_TANK = new AABB(1.5, 1.5, 1.5, 14.5, 16, 14.5);
    private static final AABB PAD_UPPER = new AABB(1.5, 0, 1.5, 14.5, 12, 14.5);
    private static final AABB PAD_HIDDEN = new AABB(7.5, 7.5, 7.5, 8.5, 8.5, 8.5);
    private static final Vec3 PAD_VALUE_BOX = new Vec3(8, 12, 14.5);

    // Pole can: hung on the pole behind it, HV bushings on the lid, LV studs on the front flat.
    private static final AABB CAN = new AABB(3.75, 0, 6, 12.25, 13, 16);
    private static final AABB[] CAN_PRIMARIES = {new AABB(4.75, 13, 10.25, 6.75, 16, 12.25), new AABB(9.25, 13, 10.25, 11.25, 16, 12.25)};
    private static final AABB[] CAN_SECONDARIES = {new AABB(4.5, 4, 5, 6, 5.5, 6), new AABB(7.25, 4, 5, 8.75, 5.5, 6), new AABB(10, 4, 5, 11.5, 5.5, 6)};
    private static final Vec3 CAN_VALUE_BOX = new Vec3(8, 9, 10);

    // Three cans on one crossarm: the middle one carries every terminal, the outer two are the bank's other phases.
    private static final AABB[] BANK_PRIMARIES = {new AABB(4.5, 13, 10.25, 6, 16, 12.25), new AABB(7.25, 13, 10.25, 8.75, 16, 12.25), new AABB(10, 13, 10.25, 11.5, 16, 12.25)};
    private static final AABB[] BANK_SECONDARIES = {new AABB(4.5, 8, 5, 6, 9.5, 6), new AABB(7.25, 8, 5, 8.75, 9.5, 6), new AABB(10, 8, 5, 11.5, 9.5, 6), new AABB(7.25, 3.5, 5, 8.75, 5, 6)};
    private static final Vec3 BANK_VALUE_BOX = new Vec3(8, 11.25, 10);

    private TransformerGeometry() {}

    /** The cells a unit takes beyond its base block. */
    public static List<Part> parts(TransformerMount mount, TransformerKind kind) {
        return switch(mount) {
            case DRY, PAD -> List.of(Part.ABOVE);
            case POLE -> kind == TransformerKind.THREE_PHASE ? List.of(Part.LEFT, Part.RIGHT) : List.of();
        };
    }

    /** Collision of a filler cell, in that cell's own frame. */
    public static VoxelShape partShape(TransformerMount mount, TransformerKind kind, Part part) {
        return switch(mount) {
            case DRY -> box(DRY_UPPER);
            case PAD -> box(PAD_UPPER);
            case POLE -> Shapes.or(box(CAN), box(new AABB(3.75, 13, 10.25, 12.25, 16, 12.25)));
        };
    }

    public static AABB[] hubs(TransformerMount mount) {
        return switch(mount) {
            case DRY -> {
                var hubs = new AABB[8];
                for(int i = 0; i < 4; ++i) {
                    double x = 16 - HUB_U[i];
                    hubs[i] = new AABB(x - 1, 0, 9, x + 1, 1, 11);          // bottom, up through the floor
                    hubs[4 + i] = new AABB(x - 1, 3, 3, x + 1, 5, 4);        // low on the front
                }
                yield hubs;
            }
            case PAD -> {
                var hubs = new AABB[8];
                for(int i = 0; i < 4; ++i) {
                    double x = 16 - HUB_U[i];
                    hubs[i] = new AABB(x - 1, 3, 0.5, x + 1, 5, 1.5);        // front, low
                    hubs[4 + i] = new AABB(x - 1, 8, 0.5, x + 1, 10, 1.5);   // front, high
                }
                yield hubs;
            }
            case POLE -> new AABB[0];
        };
    }

    public static AABB hidden(TransformerMount mount) {
        return mount == TransformerMount.PAD ? PAD_HIDDEN : DRY_HIDDEN;
    }

    /** Where the ratio value box sits, as Create's "south location" in pixels. */
    public static Vec3 valueBox(TransformerMount mount, TransformerKind kind) {
        return switch(mount) {
            case DRY -> DRY_VALUE_BOX;
            case PAD -> PAD_VALUE_BOX;
            case POLE -> kind == TransformerKind.THREE_PHASE ? BANK_VALUE_BOX : CAN_VALUE_BOX;
        };
    }

    public static VoxelShape shape(TransformerMount mount, TransformerKind kind) {
        return switch(mount) {
            case DRY -> box(DRY_BODY);
            case PAD -> Shapes.or(box(PAD_SKID), box(PAD_TANK));
            case POLE -> {
                VoxelShape shape = box(CAN);
                for(var t : exposedTerminals(kind))
                    shape = Shapes.or(shape, box(t));
                yield shape;
            }
        };
    }

    private static AABB[] exposedTerminals(TransformerKind kind) {
        var boxes = new AABB[kind.pointCount()];
        boolean bank = kind == TransformerKind.THREE_PHASE;
        for(int k = 0; k < kind.primaries(); ++k)
            boxes[kind.primaryTerminal(k)] = bank ? BANK_PRIMARIES[k] : CAN_PRIMARIES[k];
        for(int j = 0; j < kind.secondaries(); ++j)
            boxes[kind.secondaryTerminal(j)] = bank ? BANK_SECONDARIES[j] : CAN_SECONDARIES[j];
        return boxes;
    }

    /** The winding terminals: hidden points inside a cabinet or tank, bushings and studs on a pole can. */
    public static TerminalBoundingBox[] terminals(TransformerMount mount, TransformerKind kind) {
        var terminals = new TerminalBoundingBox[kind.pointCount()];
        var boxes = mount == TransformerMount.POLE ? exposedTerminals(kind) : null;
        for(int k = 0; k < kind.primaries(); ++k) {
            int t = kind.primaryTerminal(k);
            var box = boxes == null ? hidden(mount) : boxes[t];
            terminals[t] = terminal(name(kind.primaryKey(k), TransformerKind.PRIMARY_RGB), box).withColor(TransformerKind.PRIMARY_RGB);
        }
        for(int j = 0; j < kind.secondaries(); ++j) {
            int t = kind.secondaryTerminal(j);
            var box = boxes == null ? hidden(mount) : boxes[t];
            terminals[t] = terminal(name(kind.secondaryKey(j), kind.secondaryTextRgb(j)), box).withColor(kind.secondaryRgb(j));
        }
        return terminals;
    }

    private static TerminalBoundingBox terminal(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }

    private static Component name(String key, int rgb) {
        return Component.translatable("powergrid." + key).withStyle(Style.EMPTY.withColor(rgb));
    }

    private static VoxelShape box(AABB px) {
        return Block.box(px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }
}
