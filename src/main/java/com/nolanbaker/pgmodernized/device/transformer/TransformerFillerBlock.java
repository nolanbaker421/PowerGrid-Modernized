package com.nolanbaker.pgmodernized.device.transformer;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A cell of a transformer beyond its base block: invisible, its collision the part of the unit
 * that stands in that cell. Its block entity answers for the base's terminals and circuit, so
 * bushings in the cell above a tank take hanging wire. It remembers where the base is, breaks
 * with it, and gives the base's item when picked.
 */
public class TransformerFillerBlock extends Block implements EntityBlock {
    /** World offset of the base from this cell, each stored as offset + 1. */
    public static final IntegerProperty OX = IntegerProperty.create("ox", 0, 2);
    public static final IntegerProperty OY = IntegerProperty.create("oy", 0, 1);
    public static final IntegerProperty OZ = IntegerProperty.create("oz", 0, 2);

    public TransformerFillerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OX, 1).setValue(OY, 1).setValue(OZ, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OX, OY, OZ);
    }

    /** A filler state for the cell at that world offset from its base. */
    public static BlockState forOffset(BlockState filler, Vec3i cellOffset) {
        return filler.setValue(OX, 1 - cellOffset.getX()).setValue(OY, 1 - cellOffset.getY()).setValue(OZ, 1 - cellOffset.getZ());
    }

    /** The base block's position. */
    public static BlockPos basePos(BlockPos pos, BlockState state) {
        return pos.offset(state.getValue(OX) - 1, state.getValue(OY) - 1, state.getValue(OZ) - 1);
    }

    @Nullable
    private static TransformerBlock base(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockState(basePos(pos, state)).getBlock() instanceof TransformerBlock block ? block : null;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.TRANSFORMER_FILLER.create(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var basePos = basePos(pos, state);
        var baseState = level.getBlockState(basePos);
        if(!(baseState.getBlock() instanceof TransformerBlock base))
            return box(2, 0, 2, 14, 12, 14);
        var facing = TransformerBlock.facing(baseState);
        var cell = TransformerGeometry.unrotateCell(pos.subtract(basePos), facing);
        return TransformerGeometry.rotate(TransformerGeometry.cellShape(base.spec().size(), base.spec().kind(), TransformerBlock.hung(baseState), cell), facing);
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
