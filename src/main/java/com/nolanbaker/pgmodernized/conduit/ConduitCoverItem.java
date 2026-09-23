package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/** A cover plate for a conduit box: right-click an open box with it to fit it, shift-click the box with an empty hand to take it off. */
public class ConduitCoverItem extends Item {
    private final ConduitCover cover;

    public ConduitCoverItem(Properties properties, ConduitCover cover) {
        super(properties);
        this.cover = cover;
    }

    public ConduitCover cover() {
        return cover;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("conduit_cover." + cover.getSerializedName()).style(ChatFormatting.GRAY).component());
    }
}
