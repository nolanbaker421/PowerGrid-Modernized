package com.nolanbaker.pgmodernized.device.pole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A crossarm: the timber bolted across the top of a pole that cutouts stand on and pole cans hang
 * from. Placed on a pole's side, its bracket against the pole and the arm running across the
 * line. Cutouts go on the block above it; a pole can placed against its outward face hangs
 * from it the way one hangs from a pole. It falls when the pole behind it goes.
 */
public class CrossarmBlock extends Block {
    /** The way the arm's outward face points: away from the pole. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // One shape per facing, the bracket on the pole side, which is behind the facing.
    private static final VoxelShape FACING_NORTH = Shapes.or(box(0, 9, 6, 16, 13, 10), box(6, 8, 10, 10, 14, 16));
    private static final VoxelShape FACING_SOUTH = Shapes.or(box(0, 9, 6, 16, 13, 10), box(6, 8, 0, 10, 14, 6));
    private static final VoxelShape FACING_EAST = Shapes.or(box(6, 9, 0, 10, 13, 16), box(0, 8, 6, 6, 14, 10));
    private static final VoxelShape FACING_WEST = Shapes.or(box(6, 9, 0, 10, 13, 16), box(10, 8, 6, 16, 14, 10));

    public CrossarmBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch(facing(state)) {
            case SOUTH -> FACING_SOUTH;
            case EAST -> FACING_EAST;
            case WEST -> FACING_WEST;
            default -> FACING_NORTH;
        };
    }

    /** On a pole's side the bracket goes on the pole; placed on a floor or a top, the arm faces the player. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var face = ctx.getClickedFace();
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    /** The pole behind the bracket must be there. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        var behind = pos.relative(facing(state).getOpposite());
        return !level.getBlockState(behind).isAir();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if(direction == facing(state).getOpposite() && !canSurvive(state, level, pos))
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(facing(state)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(facing(state)));
    }
}
