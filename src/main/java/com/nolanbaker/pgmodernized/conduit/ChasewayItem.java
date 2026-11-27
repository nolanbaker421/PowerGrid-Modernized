package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The chaseway as an item: used on the face of a plain solid block, it turns that block into a
 * {@link ChasewayBlock} with one sleeve through it at the slot nearest the click, along that
 * face's axis, or sneaking, along the face the way the player looks, through the middle of the
 * block; used on a chaseway, it adds another sleeve there. One is used up each time; breaking the
 * chaseway gives the block and every sleeve back.
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
        var local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        // Plain click: through the block, along the face's axis. Sneaking: along the face, the way the player
        // looks, through the middle of the block, which is how a chase runs inside a wall.
        int slot = player != null && player.isShiftKeyDown()
                ? ChasewayBlock.alongSlot(context.getClickedFace(), local, player.getLookAngle())
                : ChasewayBlock.slotAt(context.getClickedFace(), local);
        if(state.getBlock() instanceof ChasewayBlock) {
            if(!(level.getBlockEntity(pos) instanceof ChasewayBlockEntity be))
                return InteractionResult.FAIL;
            if(be.hasSleeve(slot)) {
                tell(player, level, "message.chaseway.taken");
                return InteractionResult.FAIL;
            }
            if(level.isClientSide)
                return InteractionResult.SUCCESS;
            be.addSleeve(slot);
        } else {
            if(!chaseable(level, pos, state)) {
                tell(player, level, "message.chaseway.needs_solid");
                return InteractionResult.FAIL;
            }
            if(level.isClientSide)
                return InteractionResult.SUCCESS;
            level.setBlock(pos, ModBlocks.CHASEWAY.getDefaultState(), Block.UPDATE_ALL);
            if(level.getBlockEntity(pos) instanceof ChasewayBlockEntity be) {
                be.setMaterial(state);
                be.addSleeve(slot);
            }
        }
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
        if(player == null || !player.isCreative())
            context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    private static void tell(@Nullable Player player, Level level, String key) {
        if(player != null && !level.isClientSide)
            player.displayClientMessage(Lang.builder().translate(key).style(ChatFormatting.RED).component(), true);
    }

    /** A full, solid, plain block with no block entity and nothing in it: the kind a sleeve goes through. */
    public static boolean chaseable(Level level, BlockPos pos, BlockState state) {
        return state.isCollisionShapeFullBlock(level, pos)
                && state.canOcclude()
                && !state.hasBlockEntity()
                && state.getFluidState().isEmpty()
                && state.getDestroySpeed(level, pos) >= 0;
    }
}
