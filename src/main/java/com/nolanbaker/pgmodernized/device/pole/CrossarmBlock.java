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
 * from. Placed on a pole's side it is bolted to the pole and runs straight out from it; placed
 * against the outer end of an arm it continues that arm, bracketless, as far as it is built, so
 * one on each side of the pole plus extensions is a through-arm. Anything pole-mounted goes on
 * the block above any arm block. Each arm block falls when the block behind it, pole or arm, goes.
 */
public class CrossarmBlock extends Block {
    /** The way the arm runs: out from the pole. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Bolted to the pole behind it, or an extension continuing an arm beside it. */
    public static final BooleanProperty BRACKET = BooleanProperty.create("bracket");

    // The arm runs along the facing, through a plate on the pole face behind it. Mirrors gen_pole_assets.py.
    private static final VoxelShape ARM_Z = box(6, 9, 0, 10, 13, 16), ARM_X = box(0, 9, 6, 16, 13, 10);
    private static final VoxelShape FACING_NORTH = Shapes.or(ARM_Z, box(5, 8, 15, 11, 14, 16));
    private static final VoxelShape FACING_SOUTH = Shapes.or(ARM_Z, box(5, 8, 0, 11, 14, 1));
    private static final VoxelShape FACING_EAST = Shapes.or(ARM_X, box(0, 8, 5, 1, 14, 11));
    private static final VoxelShape FACING_WEST = Shapes.or(ARM_X, box(15, 8, 5, 16, 14, 11));

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

    /** The axis the arm runs along: the facing's. */
    public static Direction.Axis armAxis(BlockState state) {
        return facing(state).getAxis();
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
     * Against the outer end of an arm it continues that arm. Otherwise on a pole's side the plate
     * goes on the pole and the arm runs out, and placed on a floor or a top it runs toward the player.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var face = ctx.getClickedFace();
        // getClickedPos is where the block goes; the block under the cursor is one step back through the clicked face.
        var clicked = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(face.getOpposite()));
        if(clicked.getBlock() instanceof CrossarmBlock && face == facing(clicked))
            return defaultBlockState().setValue(FACING, facing(clicked)).setValue(BRACKET, false);
        var facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(BRACKET, true);
    }

    /** A bolted arm needs its pole behind it; an extension needs an arm of the same facing behind it. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        var behind = level.getBlockState(pos.relative(facing(state).getOpposite()));
        if(bracketed(state))
            return !behind.isAir();
        return behind.getBlock() instanceof CrossarmBlock && facing(behind) == facing(state);
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
