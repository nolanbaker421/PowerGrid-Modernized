package com.nolanbaker.pgmodernized.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;

/**
 * One block of insulated conductor rail: four bars on a bracket. Rails have no circuit of their
 * own; a run is whatever rail blocks touch each other, and it is live when a {@link RailFeedBlock}
 * is part of it. The same facing-and-rotation states as the devices, so it mounts on any face.
 */
public class ConductorRailBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);
    /** Rail blocks searched when looking for the feed of a run. */
    public static final int SEARCH = 1024;

    public ConductorRailBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.DOWN).setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROTATION);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getClickedFace().getOpposite())
                .setValue(ROTATION, context.getHorizontalDirection().get2DDataValue());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch(state.getValue(FACING)) {
            case UP -> box(0, 10, 0, 16, 16, 16);
            case NORTH -> box(0, 0, 0, 16, 16, 6);
            case SOUTH -> box(0, 0, 10, 16, 16, 16);
            case WEST -> box(0, 0, 0, 6, 16, 16);
            case EAST -> box(10, 0, 0, 16, 16, 16);
            default -> box(0, 0, 0, 16, 6, 16);
        };
    }

    public static boolean isRail(BlockState state) {
        return state.getBlock() instanceof ConductorRailBlock || state.getBlock() instanceof RailFeedBlock;
    }

    /** The feed of the run this rail block belongs to, found by walking the touching rail blocks; null when the run is dead. */
    @Nullable
    public static BlockPos findFeed(Level level, BlockPos start) {
        if(!isRail(level.getBlockState(start)))
            return null;
        var seen = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(start);
        seen.add(start);
        while(!queue.isEmpty() && seen.size() <= SEARCH) {
            var pos = queue.poll();
            var state = level.getBlockState(pos);
            if(state.getBlock() instanceof RailFeedBlock)
                return pos;
            for(var direction : Direction.values()) {
                var next = pos.relative(direction);
                if(seen.contains(next) || !level.isLoaded(next) || !isRail(level.getBlockState(next)))
                    continue;
                seen.add(next);
                queue.add(next);
            }
        }
        return null;
    }
}
