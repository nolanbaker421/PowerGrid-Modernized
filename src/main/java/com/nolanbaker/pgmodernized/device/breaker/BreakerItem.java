package com.nolanbaker.pgmodernized.device.breaker;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A plug-on breaker, or with rating 0 a blank filler plate. Neither does anything on its own;
 * right-click a panel space with it to install it. A two- or three-pole breaker takes that many
 * adjacent spaces in one column, one per lug, under a single handle that trips all poles together.
 */
public class BreakerItem extends Item {
    public static final int[] RATINGS = {10, 20, 50, 60, 100, 200, 400, 800};
    public static final int[] MULTI_POLES = {2, 3};
    public static final int BLANK = 0;

    private final int rating;
    private final int poles;

    public BreakerItem(Properties properties, int rating) {
        this(properties, rating, 1);
    }

    public BreakerItem(Properties properties, int rating, int poles) {
        super(properties);
        this.rating = rating;
        this.poles = poles;
    }

    /** Rated current in amperes; 0 for a blank. */
    public int rating() {
        return rating;
    }

    /** Spaces the breaker occupies, one per lug it bridges. A blank is always one. */
    public int poles() {
        return poles;
    }

    public boolean isBlank() {
        return rating == BLANK;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if(isBlank())
            tooltip.add(Lang.builder().translate("breaker.blank").style(ChatFormatting.GRAY).component());
        else if(poles == 1)
            tooltip.add(Lang.builder().translate("breaker.rating", rating).style(ChatFormatting.GRAY).component());
        else
            tooltip.add(Lang.builder().translate("breaker.rating_poles", rating, poles).style(ChatFormatting.GRAY).component());
    }
}
