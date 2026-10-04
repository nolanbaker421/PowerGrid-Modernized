package com.nolanbaker.pgmodernized.device.helm;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A helm: a pedestal with a console and a wheel, and a Cat6 jack on the back. Right-click it and
 * your keyboard goes to the computers on that network, key by key, while your view stays your own;
 * the ~ key, walking away or opening any screen gives it back. The block itself is dumb: it only
 * carries the jack and knows who is at it. Faces the way you stood when you placed it, wheel
 * towards you.
 */
public class HelmBlock extends Block implements IBE<HelmBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for(var facing : Direction.Plane.HORIZONTAL)
            SHAPES.put(facing, shape(facing));
    }

    public HelmBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /** Pedestal, console on top, wheel on the front: built facing north and turned. */
    private static VoxelShape shape(Direction facing) {
        var pedestal = box(5, 0, 5, 11, 9, 11);
        var console = box(2, 9, 4, 14, 14, 13);
        var wheel = box(3, 8, 2, 13, 16, 4);
        var north = Shapes.or(pedestal, console, wheel);
        return switch(facing) {
            case SOUTH -> Shapes.or(pedestal, box(2, 9, 3, 14, 14, 12), box(3, 8, 12, 13, 16, 14));
            case WEST -> Shapes.or(pedestal, box(4, 9, 2, 13, 14, 14), box(2, 8, 3, 4, 16, 13));
            case EAST -> Shapes.or(pedestal, box(3, 9, 2, 12, 14, 14), box(12, 8, 3, 14, 16, 13));
            default -> north;
        };
    }

    /** Where a Cat6 cable attaches: the back of the console. */
    public static Vec3 jackPosition(BlockState state, BlockPos pos) {
        var facing = state.getValue(FACING);
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getOpposite().getNormal()).scale(5 / 16.0)).add(0, 3.5 / 16.0, 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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

    /** Take the helm. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(level.isClientSide)
            return InteractionResult.SUCCESS;
        if(player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof HelmBlockEntity helm)
            helm.take(serverPlayer);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if(level.getBlockEntity(pos) instanceof HelmBlockEntity be)
            be.networkJack().markNeighboursDirty();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<HelmBlockEntity> getBlockEntityClass() {
        return HelmBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HelmBlockEntity> getBlockEntityType() {
        return ModBlockEntities.HELM.get();
    }
}
