package com.nolanbaker.pgmodernized.device.light;

import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The light a strip light throws on the floor: an invisible, walk-through block holding a light
 * level, kept on the first floor below a lit section by that section and removed by its own
 * block entity when no lit section above still claims it. The same device as Power Grid's
 * factory light light, kept separate so each mod's fixtures own their own glows.
 */
public class StripLightGlowBlock extends Block implements IBE<StripLightGlowBlockEntity> {
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;

    public StripLightGlowBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LEVEL, 15));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    public Class<StripLightGlowBlockEntity> getBlockEntityClass() {
        return StripLightGlowBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StripLightGlowBlockEntity> getBlockEntityType() {
        return ModBlockEntities.STRIP_LIGHT_GLOW.get();
    }
}
