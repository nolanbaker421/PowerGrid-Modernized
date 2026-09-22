package com.nolanbaker.pgmodernized.network;

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
 * The Cat6 cable. It is a Power Grid wire item (so it renders, sags and is picked up like one), but it
 * only ever connects network jacks. Clicks on device blocks reach us through the device's jack terminal
 * via Power Grid's wire handler; this class handles the standalone jack block and plain blocks, where a
 * pending cable is laid along the surface like a Power Grid block wire.
 */
public class Cat6CableItem extends WireItem {
    public Cat6CableItem(Properties properties) {
        super(properties);
    }

    public static boolean isCat6(ItemStack stack) {
        return stack.getItem() instanceof Cat6CableItem;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if(IElectric.getAt(level, pos) != null)
            return InteractionResult.PASS; // devices are handled via their jack terminal
        if(level.getBlockEntity(pos) instanceof INetworkJack jack) {
            var local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            return Cat6Placement.click(context, new JackEndpoint(pos, jack.portAt(local)));
        }
        return Cat6Placement.groundClick(context);
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
