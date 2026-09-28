package com.nolanbaker.pgmodernized.device.transformer;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The tap changer drive: a gearbox that bolts onto any cell of a transformer with its shaft
 * pointing away, and steps the unit's HV tap towards its target with every turn of the shaft.
 * Placed by clicking the transformer's side; its shaft is on the far face.
 */
public class TapActuatorBlock extends DirectionalKineticBlock implements IBE<TapActuatorBlockEntity> {
    private static final VoxelShape SHAPE = Shapes.or(box(3, 3, 3, 13, 13, 13), box(5, 5, 0, 11, 11, 16));

    public TapActuatorBlock(Properties properties) {
        super(properties);
    }

    /** The transformer this actuator is bolted to: the block behind it, head or filler cell. */
    @Nullable
    public static TransformerBlockEntity transformer(BlockGetter level, BlockPos pos, BlockState state) {
        var at = pos.relative(state.getValue(FACING).getOpposite());
        var target = level.getBlockState(at);
        if(target.getBlock() instanceof TransformerFillerBlock)
            at = TransformerFillerBlock.basePos(at, target);
        return level.getBlockEntity(at) instanceof TransformerBlockEntity be ? be : null;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var clicked = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(ctx.getClickedFace().getOpposite()));
        if(clicked.getBlock() instanceof TransformerBlock || clicked.getBlock() instanceof TransformerFillerBlock)
            return defaultBlockState().setValue(FACING, ctx.getClickedFace());
        return super.getStateForPlacement(ctx);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<TapActuatorBlockEntity> getBlockEntityClass() {
        return TapActuatorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TapActuatorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.TAP_ACTUATOR.get();
    }
}
