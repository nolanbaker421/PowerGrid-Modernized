package com.nolanbaker.pgmodernized.device.pole;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * The lineman's hot stick: works a cutout from the ground, a dozen blocks off, and keeps the
 * hand off the live parts. Aim at a pole of a cutout and right-click: a blown link comes out, a
 * link held in the other hand goes in, and otherwise the holder opens or closes. The same by
 * hand means climbing the pole and, on a live line, a shock.
 */
public class HotStickItem extends Item {
    public static final double REACH = 12;

    public HotStickItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(hand != InteractionHand.MAIN_HAND)
            return InteractionResultHolder.pass(stack);
        var hit = player.pick(REACH, 0, false);
        if(!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK)
            return InteractionResultHolder.pass(stack);
        var state = level.getBlockState(block.getBlockPos());
        if(!(state.getBlock() instanceof CutoutBlock cutout)) {
            if(!level.isClientSide)
                player.displayClientMessage(Lang.translate("message.hot_stick.miss").style(ChatFormatting.RED).component(), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if(!level.isClientSide)
            cutout.stick(state, level, block.getBlockPos(), player, block);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Close enough to click the block itself: the block's own handler takes the stick, so nothing here. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("hot_stick.tooltip").style(ChatFormatting.GRAY).component());
    }
}
