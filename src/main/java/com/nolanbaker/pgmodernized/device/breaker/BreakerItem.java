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
 * right-click a panel space with it to install it.
 */
public class BreakerItem extends Item {
    public static final int[] RATINGS = {10, 20, 50, 60, 100, 200, 400, 800};
    public static final int BLANK = 0;

    private final int rating;

    public BreakerItem(Properties properties, int rating) {
        super(properties);
        this.rating = rating;
    }

    /** Rated current in amperes; 0 for a blank. */
    public int rating() {
        return rating;
    }

    public boolean isBlank() {
        return rating == BLANK;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        var key = isBlank() ? "breaker.blank" : "breaker.rating";
        tooltip.add(Lang.builder().translate(key, rating).style(ChatFormatting.GRAY).component());
    }
}
