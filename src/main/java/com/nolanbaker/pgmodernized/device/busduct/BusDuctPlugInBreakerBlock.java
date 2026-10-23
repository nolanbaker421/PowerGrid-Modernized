package com.nolanbaker.pgmodernized.device.busduct;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.nolanbaker.pgmodernized.util.Rotation4Shapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * The plug-in disconnect with a 3-pole breaker in it: the same unit, stabs, lugs, knockouts and
 * lockout, but the handle is a breaker's that trips on the panel's curve at a rating set with a
 * wrench on the top of the unit. A tripped handle sits half way; a bare hand resets it to off.
 */
public class BusDuctPlugInBreakerBlock extends BusDuctPlugInBlock {
    public static final BooleanProperty TRIPPED = BooleanProperty.create("tripped");
    public static final AABB HANDLE_TRIPPED = new AABB(14, 2.5, 6, 15.5, 6, 8);

    public BusDuctPlugInBreakerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ON, false).setValue(LOCKED, false).setValue(TRIPPED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TRIPPED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var boxes = new ArrayList<AABB>();
        boxes.add(BODY);
        boxes.add(state.getValue(TRIPPED) ? HANDLE_TRIPPED : state.getValue(ON) ? HANDLE_ON : HANDLE_OFF);
        for(int k = 0; k < BusDuctBlock.BUS_COUNT; ++k)
            boxes.add(lug(k));
        boxes.addAll(Arrays.asList(HUBS));
        return Rotation4Shapes.of(state, boxes.toArray(AABB[]::new));
    }

    @Override
    public Class<BusDuctPlugInBlockEntity> getBlockEntityClass() {
        return BusDuctPlugInBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BusDuctPlugInBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BUS_DUCT_PLUG_IN_BREAKER.get();
    }
}
