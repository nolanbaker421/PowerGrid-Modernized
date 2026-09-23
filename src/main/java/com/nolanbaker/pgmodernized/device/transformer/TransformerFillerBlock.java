package com.nolanbaker.pgmodernized.device.transformer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The cells of a transformer beyond its base block: invisible, its collision the part of the unit
 * that stands in that cell. It remembers which way the base lies, breaks with it, and gives the
 * base's item when picked.
 */
public class TransformerFillerBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<TransformerGeometry.Part> PART = EnumProperty.create("part", TransformerGeometry.Part.class);

    public TransformerFillerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, TransformerGeometry.Part.ABOVE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** The base block's position for a filler in that state at that position. */
    public static BlockPos basePos(BlockPos pos, BlockState state) {
        var facing = state.getValue(FACING);
        return switch(state.getValue(PART)) {
            case ABOVE -> pos.below();
            // The viewer's left is +x in the north frame, which is the facing's clockwise side.
            case LEFT -> pos.relative(facing.getClockWise().getOpposite());
            case RIGHT -> pos.relative(facing.getClockWise());
        };
    }

    /** Where the filler for a part of a base at that position goes. */
    public static BlockPos partPos(BlockPos base, Direction facing, TransformerGeometry.Part part) {
        return switch(part) {
            case ABOVE -> base.above();
            case LEFT -> base.relative(facing.getClockWise());
            case RIGHT -> base.relative(facing.getClockWise().getOpposite());
        };
    }

    @Nullable
    private static TransformerBlock base(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockState(basePos(pos, state)).getBlock() instanceof TransformerBlock block ? block : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var base = base(level, pos, state);
        if(base == null)
            return box(2, 0, 2, 14, 12, 14);
        var shape = TransformerGeometry.partShape(base.mount(), base.kind(), state.getValue(PART));
        return TransformerBlock.rotateShape(shape, state.getValue(FACING));
    }

    /** Breaking the filler breaks the base, which drops the unit. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        var basePos = basePos(pos, state);
        if(level.getBlockState(basePos).getBlock() instanceof TransformerBlock && !level.isClientSide)
            level.destroyBlock(basePos, !player.isCreative());
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Without its base the filler is nothing. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if(neighborPos.equals(basePos(pos, state)) && !(neighborState.getBlock() instanceof TransformerBlock))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        var base = base(level, pos, state);
        return base == null ? ItemStack.EMPTY : new ItemStack(base);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return getCloneItemStack(level, pos, state);
    }
}
