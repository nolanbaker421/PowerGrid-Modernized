package com.nolanbaker.pgmodernized.rack;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small cogwheel that drives a Create Aeronautics body along a {@link RackBlock}. It takes
 * rotation like any cogwheel, from a shaft in line with its axis or a cog beside it, and when
 * one of its four rim sides faces a rack in the world, the body it rides moves along that rack
 * at the rim speed. Without Create Aeronautics (Sable) it is just a cogwheel.
 */
public class PinionBlock extends RotatedPillarKineticBlock implements ICogWheel, IBE<PinionBlockEntity> {
    private static final VoxelShape Y_SHAPE = Shapes.or(box(0, 6, 0, 16, 10, 16), box(6, 0, 6, 10, 16, 10));
    private static final VoxelShape X_SHAPE = Shapes.or(box(6, 0, 0, 10, 16, 16), box(0, 6, 6, 16, 10, 10));
    private static final VoxelShape Z_SHAPE = Shapes.or(box(0, 0, 6, 16, 16, 10), box(6, 6, 0, 10, 10, 16));

    public PinionBlock(Properties properties) {
        super(properties);
    }

    /** Drawn only by the visual or the block entity renderer, never as a static block as well. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch(state.getValue(AXIS)) {
            case X -> X_SHAPE;
            case Y -> Y_SHAPE;
            case Z -> Z_SHAPE;
        };
    }

    @Override
    public Class<PinionBlockEntity> getBlockEntityClass() {
        return PinionBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PinionBlockEntity> getBlockEntityType() {
        return ModBlockEntities.PINION.get();
    }
}
