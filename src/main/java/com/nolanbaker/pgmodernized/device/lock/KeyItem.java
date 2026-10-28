package com.nolanbaker.pgmodernized.device.lock;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A key cut to a lock's number: a numbered lock or key with an iron nugget in a crafting grid
 * cuts one, the lock or key staying in the grid. Right-click what the lock holds with it to take
 * the lock off. Keys of one number stack.
 */
public class KeyItem extends Item {
    public KeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        var serial = LockItem.serial(stack);
        if(serial.isEmpty())
            tooltip.add(Lang.builder().translate("key.blank").style(ChatFormatting.DARK_GRAY).component());
        else
            tooltip.add(Lang.builder().translate("lock.serial", serial).style(ChatFormatting.AQUA).component());
        tooltip.add(Lang.builder().translate("key.tooltip").style(ChatFormatting.GRAY).component());
    }
}
