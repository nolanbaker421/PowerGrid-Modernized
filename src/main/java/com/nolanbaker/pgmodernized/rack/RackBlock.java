package com.nolanbaker.pgmodernized.rack;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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

/**
 * A toothed bar laid along a crane runway. It does nothing on its own: a {@link PinionBlock} on a
 * Create Aeronautics body that sits against it walks the body along the bar.
 */
public class RackBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    private static final VoxelShape X_SHAPE = box(0, 0, 4, 16, 6, 12);
    private static final VoxelShape Z_SHAPE = box(4, 0, 0, 12, 6, 16);
    private static final VoxelShape Y_SHAPE = box(4, 0, 4, 12, 16, 10);

    public RackBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
    }

    public static boolean isRack(BlockState state) {
        return state.getBlock() instanceof RackBlock;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    /**
     * On a floor or ceiling the bar runs the way the player faces. Against a wall it runs along
     * the wall, or up the wall when the player sneaks (a climbing rack for a hoist).
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var face = context.getClickedFace();
        Direction.Axis axis;
        if(face.getAxis().isVertical())
            axis = context.getHorizontalDirection().getAxis();
        else if(context.getPlayer() != null && context.getPlayer().isShiftKeyDown())
            axis = Direction.Axis.Y;
        else
            axis = face.getClockWise().getAxis();
        return defaultBlockState().setValue(AXIS, axis);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch(state.getValue(AXIS)) {
            case X -> X_SHAPE;
            case Z -> Z_SHAPE;
            case Y -> Y_SHAPE;
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return switch(rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> switch(state.getValue(AXIS)) {
                case X -> state.setValue(AXIS, Direction.Axis.Z);
                case Z -> state.setValue(AXIS, Direction.Axis.X);
                default -> state;
            };
            default -> state;
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }
}
