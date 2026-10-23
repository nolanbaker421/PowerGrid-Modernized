package com.nolanbaker.pgmodernized.radio;

import com.nolanbaker.pgmodernized.network.Cat6Connection;
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
 * Coax: a Power Grid wire item that only hangs between coax ports, radio to antenna, by way of as
 * many hanger clips as the run needs. Electric blocks with a coax port (the radio, the collector)
 * route the click here through their own wire handling; antennas and hangers are handled directly.
 * The pending first end rides on the item in the same component the Cat6 cable uses.
 */
public class CoaxCableItem extends WireItem {
    public CoaxCableItem(Properties properties) {
        super(properties);
    }

    public static boolean isCoax(ItemStack stack) {
        return stack.getItem() instanceof CoaxCableItem;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if(IElectric.getAt(level, pos) != null)
            return InteractionResult.PASS;
        if(level.getBlockEntity(pos) instanceof ICoaxPort port) {
            var local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            return CoaxPlacement.click(context, new CoaxEndpoint(pos, port.coaxPortAt(local)));
        }
        if(Cat6Connection.has(context.getItemInHand())) {
            var player = context.getPlayer();
            if(player != null && !level.isClientSide)
                player.displayClientMessage(Lang.translate("message.coax_needs_port").style(ChatFormatting.RED).component(), true);
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(player.isShiftKeyDown() && Cat6Connection.has(stack)) {
            Cat6Connection.clear(stack);
            if(!level.isClientSide)
                player.displayClientMessage(Lang.translate("message.connection_reset").style(ChatFormatting.GRAY).component(), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || Cat6Connection.has(stack);
    }
}
