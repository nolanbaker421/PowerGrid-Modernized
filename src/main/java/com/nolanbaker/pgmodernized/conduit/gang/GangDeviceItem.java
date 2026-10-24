package com.nolanbaker.pgmodernized.conduit.gang;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/** A device for a gang box: right-click a box's empty gang with it. */
public class GangDeviceItem extends Item {
    private final GangDevice device;

    public GangDeviceItem(Properties properties, GangDevice device) {
        super(properties);
        this.device = device;
    }

    public GangDevice device() {
        return device;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("gang.device.tooltip").style(ChatFormatting.GRAY).component());
        if(device.points() > 0) {
            var names = Lang.builder();
            for(int k = 0; k < device.points(); ++k) {
                if(k > 0)
                    names.text(", ");
                names.add(device.pointName(k).copy());
            }
            tooltip.add(names.style(ChatFormatting.DARK_GRAY).component());
        }
    }
}
