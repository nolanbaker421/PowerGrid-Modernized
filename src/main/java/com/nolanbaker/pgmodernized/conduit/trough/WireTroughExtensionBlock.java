package com.nolanbaker.pgmodernized.conduit.trough;

import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.trough.TroughGeometry.Pattern;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A further section of a wire trough row, to the viewer's right of the head or of another
 * extension. It has its own knockout patterns and answers Power Grid for its own knockouts at its
 * own position, numbered in the row's space; everything else, splices, editor, circuit, is the
 * head's. Placed by the trough item when there is a row to its left.
 */
public class WireTroughExtensionBlock extends Block implements IBE<WireTroughExtensionBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Pattern> TOP = EnumProperty.create("top", Pattern.class);
    public static final EnumProperty<Pattern> BOTTOM = EnumProperty.create("bottom", Pattern.class);

    public WireTroughExtensionBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(TOP, Pattern.SIX).setValue(BOTTOM, Pattern.SIX));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TOP, BOTTOM);
    }

    public static Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TroughGeometry.shaper(state.getValue(TOP), state.getValue(BOTTOM)).get(facing(state));
    }

    /** Joining the row: the head counts one section more. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if(level.isClientSide)
            return;
        var headPos = WireTroughBlock.headPos(level, pos);
        if(headPos == null)
            return;
        var headState = level.getBlockState(headPos);
        int sections = pos.distManhattan(headPos) + 1;
        if(sections != WireTroughBlock.sectionsOf(headState) && sections <= TroughGeometry.MAX_SECTIONS)
            level.setBlock(headPos, headState.setValue(WireTroughBlock.SECTIONS, sections), Block.UPDATE_ALL);
    }

    /** Taking a section out shortens the row to the sections before it and drops the ones after. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock()) && !level.isClientSide) {
            var headPos = WireTroughBlock.headPos(level, pos);
            if(headPos != null) {
                var headState = level.getBlockState(headPos);
                int keep = pos.distManhattan(headPos);
                if(keep >= 1 && keep < WireTroughBlock.sectionsOf(headState))
                    level.setBlock(headPos, headState.setValue(WireTroughBlock.SECTIONS, keep), Block.UPDATE_ALL);
            }
            var next = pos.relative(WireTroughBlock.rowDirection(facing(state)));
            if(level.getBlockState(next).getBlock() instanceof WireTroughExtensionBlock)
                level.destroyBlock(next, true);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Orphaned when the section to its left stops being part of the row. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if(level.isClientSide)
            return;
        var left = pos.relative(WireTroughBlock.rowDirection(facing(state)).getOpposite());
        if(fromPos.equals(left) && !WireTroughBlock.isSection(level, left, facing(state)))
            level.destroyBlock(pos, true);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if(ConduitItem.isConduit(stack) && level.getBlockEntity(pos) instanceof WireTroughExtensionBlockEntity be) {
            // Conduit on one of this section's own knockouts; the item would otherwise refuse an electrical block.
            var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            int terminal = be.terminalIndexAt(state, local);
            var result = WireTroughBlock.onWire(pos, terminal, new UseOnContext(player, hand, hit));
            return switch(result) {
                case SUCCESS, CONSUME, CONSUME_PARTIAL -> ItemInteractionResult.sidedSuccess(level.isClientSide);
                case FAIL -> ItemInteractionResult.FAIL;
                default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            };
        }
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        return WireTroughBlock.interact(level, pos, state, player, hit, TOP, BOTTOM);
    }

    @Override
    public Class<WireTroughExtensionBlockEntity> getBlockEntityClass() {
        return WireTroughExtensionBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WireTroughExtensionBlockEntity> getBlockEntityType() {
        return ModBlockEntities.WIRE_TROUGH_EXTENSION.get();
    }
}
