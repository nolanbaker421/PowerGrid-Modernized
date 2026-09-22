package com.nolanbaker.pgmodernized.network;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A wall-mounted network jack: the computer end of a Cat6 run. Adjacent ComputerCraft cables and
 * modems (and OpenComputers cables) join its network, so a computer plugs in through a wired modem
 * next to it exactly as it would with stock cable.
 */
public class NetworkJackBlock extends Block implements IBE<NetworkJackBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
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

    public NetworkJackBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    public static Vec3 jackPosition(BlockState state, BlockPos pos) {
        var facing = state.getValue(FACING);
        var offset = DEPTH / 16.0 - 0.5;
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(offset));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if(level.getBlockEntity(pos) instanceof NetworkJackBlockEntity jack)
            jack.networkJack().markNeighboursDirty();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<NetworkJackBlockEntity> getBlockEntityClass() {
        return NetworkJackBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends NetworkJackBlockEntity> getBlockEntityType() {
        return ModBlockEntities.NETWORK_JACK.get();
    }
}
