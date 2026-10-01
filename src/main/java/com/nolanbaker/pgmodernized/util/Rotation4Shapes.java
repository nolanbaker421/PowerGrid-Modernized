package com.nolanbaker.pgmodernized.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Block shapes for Power Grid's four-rotation blocks that follow the terminals exactly. Power Grid
 * turns a block's terminal boxes from the floor frame ("facing=down") with the steps below; its
 * shape helper uses a different convention, so an outline built with it sits a quarter turn off
 * the model and the terminals. The models are built with tools/rotation4_table.py from the same
 * steps, so a shape built here matches both.
 */
public final class Rotation4Shapes {
    private static final Map<BlockState, VoxelShape> CACHE = new ConcurrentHashMap<>();

    private Rotation4Shapes() {}

    /** The shape of {@code state} from floor-frame boxes in sixteenths. */
    public static VoxelShape of(BlockState state, AABB... boxes) {
        return CACHE.computeIfAbsent(state, s -> build(s, boxes));
    }

    private static VoxelShape build(BlockState state, AABB[] boxes) {
        Direction facing = state.getValue(BlockStateProperties.FACING);
        IntegerProperty property = state.hasProperty(Rotation4ElectricBlock.ROTATION) ? Rotation4ElectricBlock.ROTATION : null;
        int rotation = property == null ? rotationOf(state) : state.getValue(property);
        VoxelShape shape = Shapes.empty();
        for(var box : boxes) {
            double[] min = {1, 1, 1}, max = {0, 0, 0};
            for(int corner = 0; corner < 8; ++corner) {
                double[] p = {
                        ((corner & 1) == 0 ? box.minX : box.maxX) / 16.0,
                        ((corner & 2) == 0 ? box.minY : box.maxY) / 16.0,
                        ((corner & 4) == 0 ? box.minZ : box.maxZ) / 16.0};
                p = transform(facing, rotation, p);
                for(int k = 0; k < 3; ++k) {
                    min[k] = Math.min(min[k], p[k]);
                    max[k] = Math.max(max[k], p[k]);
                }
            }
            shape = Shapes.or(shape, Shapes.box(min[0], min[1], min[2], max[0], max[1], max[2]));
        }
        return shape.optimize();
    }

    /** A block that is not Power Grid's but carries the same "rotation" property. */
    private static int rotationOf(BlockState state) {
        for(var property : state.getProperties()) {
            if(property.getName().equals("rotation") && property instanceof IntegerProperty integer)
                return state.getValue(integer);
        }
        return 0;
    }

    /** The terminal transform Rotation4ElectricBlock applies for a state, from the floor frame. */
    static double[] transform(Direction facing, int r, double[] p) {
        return switch(facing) {
            case DOWN -> rot('y', 90 * r - 90, p);
            case UP -> rot('y', 90 * r - 90, rot('x', 180, p));
            case EAST -> rot('x', -(90 * r - 90), rot('y', 180, rot('z', 90, p)));
            case WEST -> rot('x', 90 * r - 90, rot('z', 90, p));
            case NORTH -> rot('z', 90 * r - 90, rot('y', 90, rot('z', 90, p)));
            case SOUTH -> rot('z', -(90 * r - 90), rot('y', -90, rot('z', 90, p)));
        };
    }

    private static double[] rot(char axis, int angle, double[] p) {
        int steps = Math.floorMod(angle / 90, 4);
        for(int i = 0; i < steps; ++i)
            p = cw(axis, p);
        return p;
    }

    /** One clockwise quarter turn about an axis, in unit block coordinates. */
    private static double[] cw(char axis, double[] p) {
        double x = p[0], y = p[1], z = p[2];
        return switch(axis) {
            case 'x' -> new double[] {x, z, 1 - y};
            case 'y' -> new double[] {1 - z, y, x};
            default -> new double[] {y, 1 - x, z};
        };
    }
}
