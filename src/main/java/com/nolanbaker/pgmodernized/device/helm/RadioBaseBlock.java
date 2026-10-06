package com.nolanbaker.pgmodernized.device.helm;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
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

/**
 * The radio base: a box with an antenna and a Cat6 jack on its back. Pair a radio remote to it
 * by right-clicking the base with the remote; from then on using the remote anywhere in the
 * dimension puts the holder's keys on this base, which the computers and the PLC see exactly
 * like a helm. Faces the way you stood when you placed it.
 */
public class RadioBaseBlock extends Block implements IBE<RadioBaseBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShaper SHAPES = VoxelShaper.forHorizontal(
            Shapes.or(box(4, 0, 4, 12, 6, 12), box(7, 6, 7, 9, 16, 9), box(7, 2, 12, 9, 4, 13)), Direction.NORTH);

    public RadioBaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    /** Where a Cat6 cable attaches: the pin on the back of the box. */
    public static Vec3 jackPosition(BlockState state, BlockPos pos) {
        var facing = facing(state);
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getOpposite().getNormal()).scale(4.5 / 16.0)).add(0, -5 / 16.0, 0);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(facing(state));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(facing(state)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(facing(state)));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if(level.getBlockEntity(pos) instanceof RadioBaseBlockEntity be)
            be.networkJack().markNeighboursDirty();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<RadioBaseBlockEntity> getBlockEntityClass() {
        return RadioBaseBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RadioBaseBlockEntity> getBlockEntityType() {
        return ModBlockEntities.RADIO_BASE.get();
    }
}
