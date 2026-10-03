package com.nolanbaker.pgmodernized.device.rangefinder;

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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A laser rangefinder on a mounting plate. It points away from the face it is placed on and
 * reports how far along that line the first block, physics body or entity is. A Cat6 jack on the
 * plate puts the reading on the network; a comparator behind it reads it as redstone.
 */
public class RangefinderBlock extends Block implements IBE<RangefinderBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final double PLATE = 2, BARREL = 10;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for(var facing : Direction.values())
            SHAPES.put(facing, shape(facing));
    }

    public RangefinderBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    /** Plate on the mounting face, barrel out along the facing: built for UP and turned. */
    private static VoxelShape shape(Direction facing) {
        var plate = box(4, 0, 4, 12, PLATE, 12);
        var barrel = box(6, PLATE, 6, 10, BARREL, 10);
        var up = Shapes.or(plate, barrel);
        return switch(facing) {
            case UP -> up;
            case DOWN -> Shapes.or(box(4, 16 - PLATE, 4, 12, 16, 12), box(6, 16 - BARREL, 6, 10, 16 - PLATE, 10));
            case NORTH -> Shapes.or(box(4, 4, 16 - PLATE, 12, 12, 16), box(6, 6, 16 - BARREL, 10, 10, 16 - PLATE));
            case SOUTH -> Shapes.or(box(4, 4, 0, 12, 12, PLATE), box(6, 6, PLATE, 10, 10, BARREL));
            case WEST -> Shapes.or(box(16 - PLATE, 4, 4, 16, 12, 12), box(16 - BARREL, 6, 6, 16 - PLATE, 10, 10));
            case EAST -> Shapes.or(box(0, 4, 4, PLATE, 12, 12), box(PLATE, 6, 6, BARREL, 10, 10));
        };
    }

    /** Where a Cat6 cable attaches: the plate's outer face, beside the barrel. */
    public static Vec3 jackPosition(BlockState state, BlockPos pos) {
        var facing = state.getValue(FACING);
        var offset = PLATE / 16.0 - 0.5;
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(offset));
    }

    /** Where the beam leaves: the tip of the barrel. */
    public static Vec3 beamOrigin(BlockState state, BlockPos pos) {
        var facing = state.getValue(FACING);
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(BARREL / 16.0 - 0.5));
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
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Full strength with the target at the barrel, nothing at the range limit or with no target. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RangefinderBlockEntity be ? be.comparatorSignal() : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if(level.getBlockEntity(pos) instanceof RangefinderBlockEntity be)
            be.networkJack().markNeighboursDirty();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<RangefinderBlockEntity> getBlockEntityClass() {
        return RangefinderBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RangefinderBlockEntity> getBlockEntityType() {
        return ModBlockEntities.RANGEFINDER.get();
    }
}
