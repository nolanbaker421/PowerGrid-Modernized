package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.WireItem;
import org.patryk3211.powergrid.utility.Lang;

/**
 * Conduit in one trade size, placed like Power Grid block wire: click a hub on a conduit box, click
 * along the blocks it should follow, and finish on another hub. Each run ships with its conductors.
 * Clicks on a conduit box reach {@link ConduitBoxBlock#onWire} through Power Grid's wire handler;
 * this class handles plain blocks and refuses every other electrical block.
 */
public class ConduitItem extends WireItem {
    private final ConduitSize size;

    public ConduitItem(Properties properties, ConduitSize size) {
        super(properties);
        this.size = size;
    }

    public ConduitSize size() {
        return size;
    }

    public static boolean isConduit(ItemStack stack) {
        return stack.getItem() instanceof ConduitItem;
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
        return ConduitPlacement.groundClick(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(player.isShiftKeyDown() && ConduitConnection.has(stack)) {
            ConduitConnection.clear(stack);
            if(!level.isClientSide)
                player.displayClientMessage(Lang.translate("message.connection_reset").style(ChatFormatting.GRAY).component(), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || ConduitConnection.has(stack);
    }
}
