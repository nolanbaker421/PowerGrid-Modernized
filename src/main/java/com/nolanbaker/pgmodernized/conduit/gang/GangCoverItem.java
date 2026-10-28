package com.nolanbaker.pgmodernized.conduit.gang;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/** A weatherproof cover for a gang box: right-click a fitted gang with it. */
public class GangCoverItem extends Item {
    private final GangCover cover;

    public GangCoverItem(Properties properties, GangCover cover) {
        super(properties);
        this.cover = cover;
    }

    public GangCover cover() {
        return cover;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("gang.cover.tooltip." + cover.id()).style(ChatFormatting.GRAY).component());
    }
}
