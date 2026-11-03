package com.nolanbaker.pgmodernized.device.pole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A crossarm: the timber bolted across the top of a pole that cutouts stand on and pole cans hang
 * from. Placed on a pole's side it is bracketed to the pole, the arm running across the line;
 * placed against the end of an arm it continues that arm, bracketless, as far as it is built.
 * Anything pole-mounted goes on the block above any arm block. A bracketed arm falls when the pole
 * behind it goes; an extension falls when the arm it hangs off goes.
 */
public class CrossarmBlock extends Block {
    /** The way the arm's outward face points: away from the pole. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Bolted to the pole behind it, or an extension continuing an arm beside it. */
    public static final BooleanProperty BRACKET = BooleanProperty.create("bracket");

    private static final VoxelShape ARM_X = box(0, 9, 6, 16, 13, 10), ARM_Z = box(6, 9, 0, 10, 13, 16);
    // One shape per facing, the bracket on the pole side, which is behind the facing.
    private static final VoxelShape FACING_NORTH = Shapes.or(ARM_X, box(6, 8, 10, 10, 14, 16));
    private static final VoxelShape FACING_SOUTH = Shapes.or(ARM_X, box(6, 8, 0, 10, 14, 6));
    private static final VoxelShape FACING_EAST = Shapes.or(ARM_Z, box(0, 8, 6, 6, 14, 10));
    private static final VoxelShape FACING_WEST = Shapes.or(ARM_Z, box(10, 8, 6, 16, 14, 10));

    public CrossarmBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(BRACKET, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BRACKET);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    public static boolean bracketed(BlockState state) {
        return state.getValue(BRACKET);
    }

    /** The axis the arm runs along: across the facing. */
    public static Direction.Axis armAxis(BlockState state) {
        return facing(state).getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if(!bracketed(state))
            return armAxis(state) == Direction.Axis.X ? ARM_X : ARM_Z;
        return switch(facing(state)) {
            case SOUTH -> FACING_SOUTH;
            case EAST -> FACING_EAST;
            case WEST -> FACING_WEST;
            default -> FACING_NORTH;
        };
    }

    /**
     * Against the end of an arm it continues that arm. Otherwise on a pole's side the bracket
     * goes on the pole, and placed on a floor or a top the arm faces the player.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var face = ctx.getClickedFace();
        // getClickedPos is where the block goes; the block under the cursor is one step back through the clicked face.
        var clicked = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(face.getOpposite()));
        if(clicked.getBlock() instanceof CrossarmBlock && face.getAxis() == armAxis(clicked))
            return defaultBlockState().setValue(FACING, facing(clicked)).setValue(BRACKET, false);
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(BRACKET, true);
    }

    /** A bracketed arm needs its pole behind it; an extension needs an arm of the same facing beside it along the arm. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if(bracketed(state))
            return !level.getBlockState(pos.relative(facing(state).getOpposite())).isAir();
        for(var side : Direction.values()) {
            if(side.getAxis() != armAxis(state))
                continue;
            var beside = level.getBlockState(pos.relative(side));
            if(beside.getBlock() instanceof CrossarmBlock && facing(beside) == facing(state))
                return true;
        }
        return false;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        boolean holds = bracketed(state) ? direction == facing(state).getOpposite() : direction.getAxis() == armAxis(state);
        if(holds && !canSurvive(state, level, pos))
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
