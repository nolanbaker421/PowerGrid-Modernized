package com.nolanbaker.pgmodernized.device.transformer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * Shapes and terminal boxes per mount and kind, in the north-facing frame in pixels: the block's
 * back (the wall or the pole) is the south side, the front faces north, and someone looking at the
 * front has +x on their left. Mirrored by {@code tools/gen_transformer_assets.py}; change both.
 */
public final class TransformerGeometry {
    private static final double[] HUB_U = {3.5, 6.5, 9.5, 12.5};

    // Dry-type cabinet: floor standing against the wall. Knockouts on top and on both sides.
    private static final AABB DRY_BODY = new AABB(2, 0, 8, 14, 14, 16);
    private static final AABB DRY_HIDDEN = new AABB(7.5, 7.5, 11, 8.5, 8.5, 12);
    private static final Vec3 DRY_VALUE_BOX = new Vec3(8, 9, 8);

    // Pad-mount: a low wide box on the ground. Knockouts on the bottom and on both sides.
    private static final AABB PAD_BODY = new AABB(1, 0, 3, 15, 11, 15);
    private static final AABB PAD_HIDDEN = new AABB(7.5, 5, 7, 8.5, 6, 8);
    private static final Vec3 PAD_VALUE_BOX = new Vec3(8, 6, 13);

    // Pole can: hangs on the pole behind it. Primary bushings on top, secondaries on the front.
    private static final AABB CAN = new AABB(5, 1, 9, 11, 13, 16);
    private static final AABB[] CAN_PRIMARIES = {new AABB(6, 13, 11, 7.5, 15.5, 12.5), new AABB(8.5, 13, 11, 10, 15.5, 12.5)};
    private static final AABB[] CAN_SECONDARIES = {new AABB(5.5, 8, 7.5, 7, 9.5, 9), new AABB(7.25, 5.5, 7.5, 8.75, 7, 9), new AABB(9, 8, 7.5, 10.5, 9.5, 9)};
    private static final Vec3 CAN_VALUE_BOX = new Vec3(8, 11, 7);

    // Three cans side by side: phase A on the viewer's left (+x).
    private static final double[] BANK_CX = {12.75, 7.75, 2.75};
    private static final Vec3 BANK_VALUE_BOX = new Vec3(8, 11.5, 6);

    private TransformerGeometry() {}

    public static AABB[] hubs(TransformerMount mount) {
        return switch(mount) {
            case DRY -> {
                var hubs = new AABB[8];
                for(int i = 0; i < 4; ++i) {
                    double x = 16 - HUB_U[i];
                    hubs[i] = new AABB(x - 1, 14, 11, x + 1, 15, 13);
                }
                hubs[4] = new AABB(14, 4, 11, 15, 6, 13);
                hubs[5] = new AABB(14, 8, 11, 15, 10, 13);
                hubs[6] = new AABB(1, 4, 11, 2, 6, 13);
                hubs[7] = new AABB(1, 8, 11, 2, 10, 13);
                yield hubs;
            }
            case PAD -> {
                var hubs = new AABB[8];
                for(int i = 0; i < 4; ++i) {
                    double x = 16 - HUB_U[i];
                    hubs[i] = new AABB(x - 1, 0, 8, x + 1, 1, 10);
                }
                hubs[4] = new AABB(15, 3, 5, 16, 5, 7);
                hubs[5] = new AABB(15, 3, 11, 16, 5, 13);
                hubs[6] = new AABB(0, 3, 5, 1, 5, 7);
                hubs[7] = new AABB(0, 3, 11, 1, 5, 13);
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

    private static AABB can(int index) {
        double cx = BANK_CX[index];
        return new AABB(cx - 2.25, 2, 10, cx + 2.25, 13, 16);
    }

    private static AABB bankPrimary(int k) {
        double cx = BANK_CX[k];
        return new AABB(cx - 0.75, 13, 12.25, cx + 0.75, 15.5, 13.75);
    }

    private static AABB bankSecondary(int j) {
        if(j == 3)
            return new AABB(7, 4.5, 8.5, 8.5, 6, 10);
        double cx = BANK_CX[j];
        return new AABB(cx - 0.75, 8, 8.5, cx + 0.75, 9.5, 10);
    }

    public static VoxelShape shape(TransformerMount mount, TransformerKind kind) {
        return switch(mount) {
            case DRY -> box(DRY_BODY);
            case PAD -> box(PAD_BODY);
            case POLE -> {
                VoxelShape shape = Shapes.empty();
                for(var t : exposedTerminals(kind))
                    shape = Shapes.or(shape, box(t));
                if(kind == TransformerKind.THREE_PHASE) {
                    for(int i = 0; i < 3; ++i)
                        shape = Shapes.or(shape, box(can(i)));
                } else {
                    shape = Shapes.or(shape, box(CAN));
                }
                yield shape;
            }
        };
    }

    private static AABB[] exposedTerminals(TransformerKind kind) {
        var boxes = new AABB[kind.pointCount()];
        for(int k = 0; k < kind.primaries(); ++k)
            boxes[kind.primaryTerminal(k)] = kind == TransformerKind.THREE_PHASE ? bankPrimary(k) : CAN_PRIMARIES[k];
        for(int j = 0; j < kind.secondaries(); ++j)
            boxes[kind.secondaryTerminal(j)] = kind == TransformerKind.THREE_PHASE ? bankSecondary(j) : CAN_SECONDARIES[j];
        return boxes;
    }

    /** The winding terminals: hidden points inside a cabinet, bushings on a pole can. */
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
