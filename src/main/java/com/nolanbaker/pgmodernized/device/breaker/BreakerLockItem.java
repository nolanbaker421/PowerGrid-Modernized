package com.nolanbaker.pgmodernized.device.breaker;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/** A lockout hasp. Right-click an installed breaker to freeze its handle; shift-click the breaker to take it off. */
public class BreakerLockItem extends Item {
    public BreakerLockItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("breaker.lock").style(ChatFormatting.GRAY).component());
    }
}
