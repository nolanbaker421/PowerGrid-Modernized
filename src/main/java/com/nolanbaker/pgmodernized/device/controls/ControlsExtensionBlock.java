package com.nolanbaker.pgmodernized.device.controls;

import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.ConduitPlacement;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

/**
 * More rail and more door for a controls cabinet: placed directly under a cabinet (or under its
 * first extension) on the same wall, it adds six rail slots and six door cells on the same
 * control bus, with four knockouts along its bottom and two down each side. The head cabinet
 * keeps every module, device, wire and splice; this block forwards clicks and conduit to it.
 */
public class ControlsExtensionBlock extends Block implements IBE<ControlsExtensionBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShaper SHAPES = VoxelShaper.forHorizontal(CabinetLayout.extensionShape(), Direction.NORTH);

    public ControlsExtensionBlock(Properties properties) {
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

    /** The head cabinet above this position, through any extensions in between, or null. */
    @Nullable
    public static BlockPos headPos(BlockGetter level, BlockPos pos) {
        var at = pos;
        for(int i = 0; i <= ControlsCabinetBlock.MAX_EXT; ++i) {
            at = at.above();
            var state = level.getBlockState(at);
            if(state.getBlock() instanceof ControlsCabinetBlock)
                return at;
            if(!(state.getBlock() instanceof ControlsExtensionBlock))
                return null;
        }
        return null;
    }

    @Nullable
    public static ControlsCabinetBlockEntity head(BlockGetter level, BlockPos pos) {
        var headPos = headPos(level, pos);
        return headPos != null && level.getBlockEntity(headPos) instanceof ControlsCabinetBlockEntity be ? be : null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(facing(state));
    }

    // ---- placement: only right under a cabinet that has room ----

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var level = ctx.getLevel();
        var pos = ctx.getClickedPos();
        var above = level.getBlockState(pos.above());
        Direction facing;
        if(above.getBlock() instanceof ControlsCabinetBlock)
            facing = ControlsCabinetBlock.facing(above);
        else if(above.getBlock() instanceof ControlsExtensionBlock)
            facing = facing(above);
        else
            return refuse(ctx, "message.controls_extension.needs_cabinet");
        var headPos = headPos(level, pos);
        if(headPos == null)
            return refuse(ctx, "message.controls_extension.needs_cabinet");
        var headState = level.getBlockState(headPos);
        int sections = ControlsCabinetBlock.sectionsOf(headState);
        if(headPos.getY() - pos.getY() != sections)
            return refuse(ctx, "message.controls_extension.needs_cabinet");
        if(sections > ControlsCabinetBlock.MAX_EXT)
            return refuse(ctx, "message.controls_extension.full");
        if(sections == 1 && level.getBlockEntity(headPos) instanceof ControlsCabinetBlockEntity head && head.bottomHubsUsed())
            return refuse(ctx, "message.controls_extension.bottom_used");
        return defaultBlockState().setValue(FACING, facing);
    }

    @Nullable
    private static BlockState refuse(BlockPlaceContext ctx, String key) {
        var player = ctx.getPlayer();
        if(player != null && !ctx.getLevel().isClientSide)
            player.displayClientMessage(Lang.builder().translate(key).style(ChatFormatting.RED).component(), true);
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if(level.isClientSide)
            return;
        var headPos = headPos(level, pos);
        if(headPos == null)
            return;
        var headState = level.getBlockState(headPos);
        int sections = headPos.getY() - pos.getY() + 1;
        if(sections != ControlsCabinetBlock.sectionsOf(headState) && sections <= 1 + ControlsCabinetBlock.MAX_EXT)
            level.setBlock(headPos, headState.setValue(ControlsCabinetBlock.SECTIONS, sections), Block.UPDATE_ALL);
    }

    /** Taking an extension out shortens the cabinet to the sections above it and drops the ones below. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if(!state.is(newState.getBlock()) && !level.isClientSide) {
            var headPos = headPos(level, pos);
            if(headPos != null) {
                var headState = level.getBlockState(headPos);
                int keep = headPos.getY() - pos.getY();
                if(keep >= 1 && keep < ControlsCabinetBlock.sectionsOf(headState))
                    level.setBlock(headPos, headState.setValue(ControlsCabinetBlock.SECTIONS, keep), Block.UPDATE_ALL);
            }
            var below = pos.below();
            if(level.getBlockState(below).getBlock() instanceof ControlsExtensionBlock)
                level.destroyBlock(below, true);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Orphaned when the block above stops being a cabinet or an extension. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if(level.isClientSide || !fromPos.equals(pos.above()))
            return;
        var above = level.getBlockState(fromPos).getBlock();
        if(!(above instanceof ControlsCabinetBlock) && !(above instanceof ControlsExtensionBlock))
            level.destroyBlock(pos, true);
    }

    // ---- everything else goes to the head ----

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var headPos = headPos(level, pos);
        if(headPos == null || !(level.getBlockState(headPos).getBlock() instanceof ControlsCabinetBlock head))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(ConduitItem.isConduit(stack) && level.getBlockEntity(pos) instanceof ControlsExtensionBlockEntity be) {
            // Conduit on one of this block's own knockouts; the item would otherwise refuse an electrical block.
            var local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            int terminal = be.terminalIndexAt(state, local);
            if(CabinetLayout.hubAt(terminal) < 0)
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            var result = ConduitPlacement.click(new UseOnContext(player, hand, hit), new BlockWireEndpoint(pos, terminal));
            return switch(result) {
                case SUCCESS, CONSUME, CONSUME_PARTIAL -> ItemInteractionResult.sidedSuccess(level.isClientSide);
                case FAIL -> ItemInteractionResult.FAIL;
                default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            };
        }
        return head.interactItem(stack, level.getBlockState(headPos), level, headPos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var headPos = headPos(level, pos);
        if(headPos == null || !(level.getBlockState(headPos).getBlock() instanceof ControlsCabinetBlock head))
            return InteractionResult.PASS;
        return head.interact(level.getBlockState(headPos), level, headPos, player, hit);
    }

    @Override
    public Class<ControlsExtensionBlockEntity> getBlockEntityClass() {
        return ControlsExtensionBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ControlsExtensionBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CONTROLS_EXTENSION.get();
    }
}
