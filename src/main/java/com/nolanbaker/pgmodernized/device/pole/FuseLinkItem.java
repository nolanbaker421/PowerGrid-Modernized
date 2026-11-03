package com.nolanbaker.pgmodernized.device.pole;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A fuse link for a cutout, by amps: the K-link sizes a lineman carries. It goes into any class
 * of holder, by hand or on a hot stick, and blows on the breaker curve against its rating. A
 * blown link is scrap.
 */
public class FuseLinkItem extends Item {
    public static final int[] RATINGS = {6, 10, 15, 25, 40, 65, 100, 140, 200};

    private final int rating;

    public FuseLinkItem(Properties properties, int rating) {
        super(properties);
        this.rating = rating;
    }

    public int rating() {
        return rating;
    }

    /** The amps of the link in that stack, or 0 for anything else. */
    public static int ratingOf(ItemStack stack) {
        return stack.getItem() instanceof FuseLinkItem link ? link.rating : 0;
    }

    @Nullable
    public static FuseLinkItem of(ItemStack stack) {
        return stack.getItem() instanceof FuseLinkItem link ? link : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("fuse_link.rating", rating).style(ChatFormatting.AQUA).component());
        tooltip.add(Lang.builder().translate("fuse_link.tooltip").style(ChatFormatting.GRAY).component());
    }
}
