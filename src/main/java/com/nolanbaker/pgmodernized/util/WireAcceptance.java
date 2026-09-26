package com.nolanbaker.pgmodernized.util;

import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.device.breaker.BusBarItem;
import com.nolanbaker.pgmodernized.network.Cat6CableItem;
import net.minecraft.world.item.ItemStack;

/**
 * Power Grid's right-click handler offers every registered wire item to every electrical block, and
 * a block that accepts "any wire" would also take the Cat6 cable or a conduit as a plain conductor.
 * Blocks in this mod that accept all electrical wires go through here instead.
 */
public final class WireAcceptance {
    private WireAcceptance() {}

    /** True for real conductors; false for the network cable and conduit, which only fit their own ports. */
    public static boolean electrical(ItemStack wireStack) {
        var item = wireStack.getItem();
        return !(item instanceof Cat6CableItem) && !(item instanceof ConduitItem) && !(item instanceof BusBarItem);
    }
}
