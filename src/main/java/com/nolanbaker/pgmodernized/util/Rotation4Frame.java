package com.nolanbaker.pgmodernized.util;

import com.nolanbaker.pgmodernized.conduit.gang.GangFrame;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.Rotation4ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

import java.util.HashMap;
import java.util.Map;

/**
 * The terminal collection of a four-way (facing and quarter turn) block, built from its floor
 * frame. Power Grid's own helper turns the terminals one quarter turn differently from the shapes
 * on floors and ceilings, and the models follow the terminals, so a long box showed its outline
 * across itself. Here terminals and shapes go through the one transform the models were derived
 * from, {@link GangFrame#toWorld}.
 */
public final class Rotation4Frame {
    private Rotation4Frame() {}

    public static BlockStateTerminalCollection collection(Block block, TerminalBoundingBox[] floor, VoxelShape floorShape) {
        Map<Integer, VoxelShape> shapes = new HashMap<>();
        return BlockStateTerminalCollection.builder(block)
                .forAllStates(state -> BlockStateTerminalCollection.each(floor, terminal -> rotate(facing(state), rotation(state), terminal)))
                .withShapeMapper(state -> shapes.computeIfAbsent(facing(state).ordinal() * 4 + rotation(state), k -> shape(facing(state), rotation(state), floorShape)))
                .build();
    }

    private static Direction facing(BlockState state) {
        return state.getValue(DirectionalElectricBlock.FACING);
    }

    private static int rotation(BlockState state) {
        return state.hasProperty(Rotation4ElectricBlock.ROTATION) ? state.getValue(Rotation4ElectricBlock.ROTATION) : 0;
    }

    /** The same turns as {@link GangFrame#toWorld}, innermost first. */
    public static TerminalBoundingBox rotate(Direction facing, int r, TerminalBoundingBox t) {
        int turn = Math.floorMod(90 * r - 90, 360);
        return switch(facing) {
            case DOWN -> t.rotate(Direction.Axis.Y, turn);
            case UP -> t.rotate(Direction.Axis.X, 180).rotate(Direction.Axis.Y, turn);
            case EAST -> t.rotate(Direction.Axis.Z, 90).rotate(Direction.Axis.Y, 180).rotate(Direction.Axis.X, Math.floorMod(-turn, 360));
            case WEST -> t.rotate(Direction.Axis.Z, 90).rotate(Direction.Axis.X, turn);
            case NORTH -> t.rotate(Direction.Axis.Z, 90).rotate(Direction.Axis.Y, 90).rotate(Direction.Axis.Z, turn);
            case SOUTH -> t.rotate(Direction.Axis.Z, 90).rotate(Direction.Axis.Y, 270).rotate(Direction.Axis.Z, Math.floorMod(-turn, 360));
        };
    }

    /** Every box of the floor shape carried through the frame; a rotation keeps boxes boxes. */
    public static VoxelShape shape(Direction facing, int r, VoxelShape floorShape) {
        VoxelShape out = Shapes.empty();
        for(AABB box : floorShape.toAabbs()) {
            var a = GangFrame.toWorld(facing, r, new Vec3(box.minX, box.minY, box.minZ));
            var b = GangFrame.toWorld(facing, r, new Vec3(box.maxX, box.maxY, box.maxZ));
            out = Shapes.or(out, Shapes.create(new AABB(a, b)));
        }
        return out.optimize();
    }
}
