package com.nolanbaker.pgmodernized.rack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A toothed bar laid along a crane runway. It does nothing on its own: a {@link PinionBlock} on a
 * Create Aeronautics body whose rim faces the teeth walks the body along the bar. The bar sits on
 * the face it was placed against ({@code FACING} is the way the teeth point) and runs along
 * {@code AXIS}, which is never the facing axis.
 */
public class RackBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    /** The bar fills the block along its axis and its facing; it is eight wide the third way. */
    private static final VoxelShape THIRD_X = box(4, 0, 0, 12, 16, 16);
    private static final VoxelShape THIRD_Y = box(0, 4, 0, 16, 12, 16);
    private static final VoxelShape THIRD_Z = box(0, 0, 4, 16, 16, 12);

    public RackBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP).setValue(AXIS, Direction.Axis.X));
    }

    public static boolean isRack(BlockState state) {
        return state.getBlock() instanceof RackBlock;
    }

    /** The axis that is neither the bar's run nor its facing. */
    public static Direction.Axis thirdAxis(Direction.Axis a, Direction.Axis b) {
        for(var axis : Direction.Axis.VALUES) {
            if(axis != a && axis != b)
                return axis;
        }
        return Direction.Axis.Y;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AXIS);
    }

    /**
     * The teeth point away from the face it was placed on. On a floor or ceiling the bar runs the
     * way the player faces. Against a wall it runs along the wall, or up the wall when the player
     * sneaks (a climbing rack for a hoist).
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var face = context.getClickedFace();
        Direction.Axis axis;
        if(face.getAxis().isVertical())
            axis = context.getHorizontalDirection().getAxis();
        else if(context.getPlayer() != null && context.getPlayer().isShiftKeyDown())
            axis = Direction.Axis.Y;
        else
            axis = face.getClockWise().getAxis();
        return defaultBlockState().setValue(FACING, face).setValue(AXIS, axis);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch(thirdAxis(state.getValue(AXIS), state.getValue(FACING).getAxis())) {
            case X -> THIRD_X;
            case Y -> THIRD_Y;
            case Z -> THIRD_Z;
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        var facing = rotation.rotate(state.getValue(FACING));
        var axis = state.getValue(AXIS);
        if(axis.isHorizontal() && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90))
            axis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return state.setValue(FACING, facing).setValue(AXIS, axis);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
