package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The chaseway as an item: used on the face of a plain solid block, it turns that block into a
 * {@link ChasewayBlock} running through along that face's axis, wearing the block's own look.
 * One is used up each time; breaking the chaseway gives both back.
 */
public class ChasewayItem extends Item {
    public ChasewayItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        var player = context.getPlayer();
        if(!chaseable(level, pos, state)) {
            if(player != null && !level.isClientSide)
                player.displayClientMessage(Lang.builder().translate("message.chaseway.needs_solid").style(ChatFormatting.RED).component(), true);
            return InteractionResult.FAIL;
        }
        if(level.isClientSide)
            return InteractionResult.SUCCESS;
        var axis = context.getClickedFace().getAxis();
        level.setBlock(pos, ModBlocks.CHASEWAY.getDefaultState().setValue(ChasewayBlock.AXIS, axis), Block.UPDATE_ALL);
        if(level.getBlockEntity(pos) instanceof ChasewayBlockEntity be)
            be.setMaterial(state);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
        if(player == null || !player.isCreative())
            context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    /** A full, solid, plain block with no block entity and nothing in it: the kind a sleeve goes through. */
    public static boolean chaseable(Level level, BlockPos pos, BlockState state) {
        return state.isCollisionShapeFullBlock(level, pos)
                && state.canOcclude()
                && !state.hasBlockEntity()
                && state.getFluidState().isEmpty()
                && !(state.getBlock() instanceof ChasewayBlock)
                && state.getDestroySpeed(level, pos) >= 0;
    }
}
