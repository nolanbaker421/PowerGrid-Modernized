package com.nolanbaker.pgmodernized.device.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A component that goes into a slot of a switchgear cell: the neutral bar, the lug block, the
 * switch, the contactor and the fuse sets. Bus bars are the copper bus bar item and breakers are
 * the panel's 3-pole breakers.
 */
public class GearComponentItem extends Item {
    private final GearKind kind;
    private final int rating;

    public GearComponentItem(Properties properties, GearKind kind, int rating) {
        super(properties);
        this.kind = kind;
        this.rating = rating;
    }

    public GearKind kind() {
        return kind;
    }

    /** Amperes, for fuse sets; zero otherwise. */
    public int rating() {
        return rating;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("gear.item." + kind.id()).style(ChatFormatting.GRAY).component());
        if(kind == GearKind.FUSE)
            tooltip.add(Lang.builder().translate("gear.item.fuse_rating", rating).style(ChatFormatting.GRAY).component());
    }
}
