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

    // The arm close against the pole, which is behind the facing, held by a thin plate. Mirrors gen_pole_assets.py.
    private static final VoxelShape FACING_NORTH = Shapes.or(box(0, 9, 11, 16, 13, 15), box(5, 8, 15, 11, 14, 16));
    private static final VoxelShape FACING_SOUTH = Shapes.or(box(0, 9, 1, 16, 13, 5), box(5, 8, 0, 11, 14, 1));
    private static final VoxelShape FACING_EAST = Shapes.or(box(1, 9, 0, 5, 13, 16), box(0, 8, 5, 1, 14, 11));
    private static final VoxelShape FACING_WEST = Shapes.or(box(11, 9, 0, 15, 13, 16), box(15, 8, 5, 16, 14, 11));
    private static final VoxelShape ARM_NORTH = box(0, 9, 11, 16, 13, 15), ARM_SOUTH = box(0, 9, 1, 16, 13, 5), ARM_EAST = box(1, 9, 0, 5, 13, 16), ARM_WEST = box(11, 9, 0, 15, 13, 16);

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
        boolean b = bracketed(state);
        return switch(facing(state)) {
            case SOUTH -> b ? FACING_SOUTH : ARM_SOUTH;
            case EAST -> b ? FACING_EAST : ARM_EAST;
            case WEST -> b ? FACING_WEST : ARM_WEST;
            default -> b ? FACING_NORTH : ARM_NORTH;
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
