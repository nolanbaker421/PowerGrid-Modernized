package com.nolanbaker.pgmodernized.ac.pump;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;

/**
 * One centrifugal stage bolted onto a three-phase pump. Stages stack in straight lines out from
 * the pump's sides, square to its axis, and share the axis: each stage adds the pump's range
 * again and draws its power again, for the same flow. The pump counts them itself; a stage has
 * no block entity.
 */
public class PumpStageBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    private static final VoxelShape Z = Block.box(1, 1, 2, 15, 15, 14);
    private static final VoxelShape X = Block.box(2, 1, 1, 14, 15, 15);
    private static final VoxelShape Y = Block.box(1, 2, 1, 15, 14, 15);

    public PumpStageBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    public static Direction.Axis axis(BlockState state) {
        return state.getValue(AXIS);
    }

    /** Whether a stage with that axis stands at {@code pos}. */
    public static boolean isStage(LevelReader level, BlockPos pos, Direction.Axis axis) {
        var state = level.getBlockState(pos);
        return state.getBlock() instanceof PumpStageBlock && axis(state) == axis;
    }

    /** Takes the axis of the pump or stage it is placed against, else the way the player looks. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var level = ctx.getLevel();
        var pos = ctx.getClickedPos();
        for(var side : Direction.values()) {
            var neighbour = level.getBlockState(pos.relative(side));
            if(neighbour.getBlock() instanceof PumpStageBlock)
                return defaultBlockState().setValue(AXIS, axis(neighbour));
            if(neighbour.getBlock() instanceof ThreePhasePumpBlock && side.getAxis() != neighbour.getValue(DirectionalElectricBlock.FACING).getAxis())
                return defaultBlockState().setValue(AXIS, neighbour.getValue(DirectionalElectricBlock.FACING).getAxis());
        }
        return defaultBlockState().setValue(AXIS, ctx.getNearestLookingDirection().getAxis());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch(axis(state)) {
            case X -> X;
            case Y -> Y;
            default -> Z;
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        if(rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            var axis = axis(state);
            if(axis == Direction.Axis.X)
                return state.setValue(AXIS, Direction.Axis.Z);
            if(axis == Direction.Axis.Z)
                return state.setValue(AXIS, Direction.Axis.X);
        }
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }
}
