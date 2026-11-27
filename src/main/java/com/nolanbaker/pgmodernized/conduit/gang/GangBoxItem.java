package com.nolanbaker.pgmodernized.conduit.gang;

import com.nolanbaker.pgmodernized.conduit.ChasewayItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * The gang box as an item. Placed as any block it sits on a surface; sneaking, used on a plain
 * solid block, it sets the box into that block instead: the block becomes the gang box, still
 * looking, sounding and dropping like the wall it was, with the plate flush on the face you
 * clicked and the box and its knockouts inside, where a chase in the wall reaches them.
 */
public class GangBoxItem extends BlockItem {
    public GangBoxItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var wall = level.getBlockState(pos);
        if(player == null || !player.isShiftKeyDown() || !ChasewayItem.chaseable(level, pos, wall))
            return super.useOn(context);
        var placed = getBlock().getStateForPlacement(new BlockPlaceContext(context));
        if(placed == null || !placed.hasProperty(GangBoxBlock.INSET))
            return InteractionResult.FAIL;
        if(level.isClientSide)
            return InteractionResult.SUCCESS;
        level.setBlock(pos, placed.setValue(GangBoxBlock.INSET, true), Block.UPDATE_ALL);
        if(level.getBlockEntity(pos) instanceof GangBoxBlockEntity be)
            be.setMaterial(wall);
        var sound = wall.getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
        if(!player.isCreative())
            context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("gang.tooltip.inset").style(ChatFormatting.GRAY).component());
    }
}
