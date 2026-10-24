package com.nolanbaker.pgmodernized.conduit.gang;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;

/**
 * The transform Power Grid's four-way blocks apply to their floor frame for each state, as worked
 * out in {@code tools/rotation4_table.py}: the same quarter turns it gives the terminal boxes, so a
 * renderer or a click test done through here lands exactly where the terminals are. Points are in
 * blocks, 0 to 1.
 */
public final class GangFrame {
    private GangFrame() {}

    private static Vec3 cw(Direction.Axis axis, Vec3 p) {
        return switch(axis) {
            case X -> new Vec3(p.x, p.z, 1 - p.y);
            case Y -> new Vec3(1 - p.z, p.y, p.x);
            case Z -> new Vec3(p.y, 1 - p.x, p.z);
        };
    }

    private static Vec3 rot(Direction.Axis axis, int degrees, Vec3 p) {
        int steps = Math.floorMod(degrees / 90, 4);
        for(int i = 0; i < steps; ++i)
            p = cw(axis, p);
        return p;
    }

    /** Floor-frame point to the block's local space for that facing and rotation. */
    public static Vec3 toWorld(Direction facing, int r, Vec3 p) {
        int turn = 90 * r - 90;
        return switch(facing) {
            case DOWN -> rot(Direction.Axis.Y, turn, p);
            case UP -> rot(Direction.Axis.Y, turn, rot(Direction.Axis.X, 180, p));
            case EAST -> rot(Direction.Axis.X, -turn, rot(Direction.Axis.Y, 180, rot(Direction.Axis.Z, 90, p)));
            case WEST -> rot(Direction.Axis.X, turn, rot(Direction.Axis.Z, 90, p));
            case NORTH -> rot(Direction.Axis.Z, turn, rot(Direction.Axis.Y, 90, rot(Direction.Axis.Z, 90, p)));
            case SOUTH -> rot(Direction.Axis.Z, -turn, rot(Direction.Axis.Y, -90, rot(Direction.Axis.Z, 90, p)));
        };
    }

    public static Vec3 toWorld(BlockState state, Vec3 p) {
        return toWorld(state.getValue(DirectionalElectricBlock.FACING), state.getValue(Rotation4ElectricBlock.ROTATION), p);
    }

    /** Block-local point back into the floor frame: the transform is a rotation, so its transpose undoes it. */
    public static Vec3 toFrame(BlockState state, Vec3 local) {
        var o = toWorld(state, Vec3.ZERO);
        var ex = toWorld(state, new Vec3(1, 0, 0)).subtract(o);
        var ey = toWorld(state, new Vec3(0, 1, 0)).subtract(o);
        var ez = toWorld(state, new Vec3(0, 0, 1)).subtract(o);
        var d = local.subtract(o);
        return new Vec3(d.dot(ex), d.dot(ey), d.dot(ez));
    }

    /** A floor-frame pixel box as a block-local box in blocks. */
    public static AABB worldBox(BlockState state, AABB px) {
        var a = toWorld(state, new Vec3(px.minX / 16, px.minY / 16, px.minZ / 16));
        var b = toWorld(state, new Vec3(px.maxX / 16, px.maxY / 16, px.maxZ / 16));
        return new AABB(a, b);
    }

    /** The frame as a pose: columns are the frame's axes and origin in block-local space, for drawing in floor-frame coordinates. */
    public static Matrix4f pose(BlockState state) {
        var o = toWorld(state, Vec3.ZERO);
        var ex = toWorld(state, new Vec3(1, 0, 0)).subtract(o);
        var ey = toWorld(state, new Vec3(0, 1, 0)).subtract(o);
        var ez = toWorld(state, new Vec3(0, 0, 1)).subtract(o);
        return new Matrix4f(
                (float) ex.x, (float) ex.y, (float) ex.z, 0,
                (float) ey.x, (float) ey.y, (float) ey.z, 0,
                (float) ez.x, (float) ez.y, (float) ez.z, 0,
                (float) o.x, (float) o.y, (float) o.z, 1);
    }
}
