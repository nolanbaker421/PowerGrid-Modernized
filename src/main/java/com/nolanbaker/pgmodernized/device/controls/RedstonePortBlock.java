package com.nolanbaker.pgmodernized.device.controls;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A network jack that puts out a redstone signal: the Cat6 side is a controls cabinet, whose PLC
 * (or HMI, or computer) writes the tag {@code RS<n>} with a level 0..15, and the block powers its
 * neighbours and the block it is mounted on with that level, like a lever. For the things that
 * only listen to redstone, such as a ship's wheel. Right-click with an empty hand to count the port
 * number up, sneak to count it down.
 */
public class RedstonePortBlock extends Block implements IBE<RedstonePortBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Plate depth in 16ths; the cable attaches to the outer face of the plate. */
    private static final double DEPTH = 3;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.UP, box(5, 0, 5, 11, DEPTH, 11));
        SHAPES.put(Direction.DOWN, box(5, 16 - DEPTH, 5, 11, 16, 11));
        SHAPES.put(Direction.NORTH, box(5, 5, 16 - DEPTH, 11, 11, 16));
        SHAPES.put(Direction.SOUTH, box(5, 5, 0, 11, 11, DEPTH));
        SHAPES.put(Direction.EAST, box(0, 5, 5, DEPTH, 11, 11));
        SHAPES.put(Direction.WEST, box(16 - DEPTH, 5, 5, 16, 11, 11));
    }

    public RedstonePortBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP).setValue(LIT, false));
    }

    public static Vec3 jackPosition(BlockState state, BlockPos pos) {
        var facing = state.getValue(FACING);
        var offset = DEPTH / 16.0 - 0.5;
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(offset));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---- redstone: weak power on every side, strong power into the block it is mounted on, as a lever ----

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    private int output(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RedstonePortBlockEntity port ? port.output() : 0;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return output(level, pos);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(FACING) == direction ? output(level, pos) : 0;
    }

    /** The output changed: the neighbours and the mounting block re-read their power. */
    public static void notifyOutput(Level level, BlockPos pos, BlockState state) {
        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), state.getBlock());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(!level.isClientSide)
            withBlockEntityDo(level, pos, port -> port.renumber(player, player.isShiftKeyDown() ? -1 : 1));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if(level.getBlockEntity(pos) instanceof RedstonePortBlockEntity port)
            port.networkJack().markNeighboursDirty();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean gone = !state.is(newState.getBlock());
        IBE.onRemove(state, level, pos, newState);
        if(gone)
            notifyOutput(level, pos, state);
    }

    @Override
    public Class<RedstonePortBlockEntity> getBlockEntityClass() {
        return RedstonePortBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RedstonePortBlockEntity> getBlockEntityType() {
        return ModBlockEntities.REDSTONE_PORT.get();
    }
}
