package com.nolanbaker.pgmodernized.util;

import com.nolanbaker.pgmodernized.device.label.LabelMakerItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What labels a device: a name tag, renamed in an anvil or plain to clear, or the label maker
 * with a label set on it. Everything that took a tag takes either; the tag is used up, the maker
 * is not.
 */
public final class Labels {
    private Labels() {}

    /** A name tag, or a label maker that has a label (even an empty one, which clears) set on it. */
    public static boolean isTag(ItemStack stack) {
        return stack.is(Items.NAME_TAG) || LabelMakerItem.hasLabel(stack);
    }

    /** The text that stack labels with; empty for a plain tag or a cleared maker. */
    public static String text(ItemStack stack) {
        if(stack.getItem() instanceof LabelMakerItem)
            return LabelMakerItem.label(stack);
        return stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : "";
    }

    /** A tag is spent by labelling; a label maker prints as many as you like. */
    public static void consume(Player player, ItemStack stack) {
        if(stack.getItem() instanceof LabelMakerItem || player.isCreative())
            return;
        stack.shrink(1);
    }
}
