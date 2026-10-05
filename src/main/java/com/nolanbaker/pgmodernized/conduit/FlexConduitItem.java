package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.utility.Lang;

/**
 * Flexible conduit: a whip from one knockout straight to another, hanging between them, that
 * never touches a block in between and does not care whether its two ends are on the same body.
 * For pivots, booms and anything that moves against something else. Pulled wire runs through it
 * like through any run. It is never routed along blocks: a click on the ground only says so.
 */
public class FlexConduitItem extends ConduitItem {
    public FlexConduitItem(Properties properties, ConduitSize size) {
        super(properties, size);
    }

    public static boolean isFlex(ItemStack stack) {
        return stack.getItem() instanceof FlexConduitItem;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if(state.getBlock() instanceof ConduitBoxBlock box)
            return box.onWire(state, context);
        if(IElectric.getAt(level, pos) != null) {
            IElectric.sendMessage(context, Lang.builder().translate("message.conduit.needs_hub").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        if(ConduitConnection.has(context.getItemInHand())) {
            IElectric.sendMessage(context, Lang.builder().translate("message.conduit.flex_hubs").style(ChatFormatting.RED).component());
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
}
