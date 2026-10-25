package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.PlayerUtilities;

/**
 * Where the sticks for a run come from. A conduit item in hand is its own supply, taken from the
 * player's inventory. A loaded {@link ConduitBenderItem} stands in for the conduit it picked up:
 * its sticks are spent first, and the player's own conduit of that size tops it up. Placement code
 * asks here instead of looking at the held item, so the bender lays conduit through the same path.
 */
public final class ConduitSupply {
    private ConduitSupply() {}

    /** The conduit item a run laid with this stack is made of. */
    public static Item item(ItemStack stack) {
        if(stack.getItem() instanceof ConduitBenderItem) {
            var conduit = ConduitBenderItem.conduitItem(stack);
            if(conduit != null)
                return conduit;
        }
        return stack.getItem();
    }

    /** Half the tube of that conduit, in blocks; zero for anything else. */
    public static double tubeRadius(ItemStack stack) {
        var size = ConduitItem.sizeOf(stack);
        return size != null ? size.tubeThickness() / 2 : 0;
    }

    /** How many sticks the stack can supply without reaching into the inventory. */
    public static int count(ItemStack stack) {
        return stack.getItem() instanceof ConduitBenderItem ? ConduitBenderItem.sticks(stack) : stack.getCount();
    }

    public static boolean has(@Nullable Player player, ItemStack stack, int count) {
        if(count <= 0 || player == null || player.isCreative())
            return true;
        if(stack.getItem() instanceof ConduitBenderItem) {
            int sticks = ConduitBenderItem.sticks(stack);
            if(sticks >= count)
                return true;
            var conduit = ConduitBenderItem.conduitItem(stack);
            return conduit != null && PlayerUtilities.hasEnoughItems(player, conduit, count - sticks);
        }
        return PlayerUtilities.hasEnoughItems(player, stack, count);
    }

    public static void take(@Nullable Player player, ItemStack stack, int count) {
        if(count <= 0 || player == null || player.isCreative())
            return;
        if(stack.getItem() instanceof ConduitBenderItem) {
            int sticks = ConduitBenderItem.sticks(stack);
            int fromBender = Math.min(sticks, count);
            ConduitBenderItem.setSticks(stack, sticks - fromBender);
            var conduit = ConduitBenderItem.conduitItem(stack);
            if(count > fromBender && conduit != null)
                PlayerUtilities.removeItems(player, conduit, count - fromBender);
            return;
        }
        PlayerUtilities.removeItems(player, stack, count);
    }

    /** That many sticks of the supplied conduit, for a run entity to carry. */
    public static ItemStack stack(ItemStack stack, int count) {
        return new ItemStack(item(stack), count);
    }
}
