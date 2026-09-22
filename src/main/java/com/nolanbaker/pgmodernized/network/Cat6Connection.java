package com.nolanbaker.pgmodernized.network;

import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEndpointType;

/**
 * The pending first end of a Cat6 connection, stored on the cable item between the two clicks.
 * Kept separate from Power Grid's own connection component so its wire handler never sees it.
 */
public final class Cat6Connection {
    private Cat6Connection() {}

    @Nullable
    public static IWireEndpoint get(ItemStack stack) {
        var tag = stack.get(ModDataComponents.CAT6_CONNECTION.get());
        if(tag == null)
            return null;
        return JackEndpoint.from(WireEndpointType.deserialize(tag));
    }

    public static boolean has(ItemStack stack) {
        return stack.has(ModDataComponents.CAT6_CONNECTION.get());
    }

    public static void set(ItemStack stack, IWireEndpoint endpoint) {
        stack.set(ModDataComponents.CAT6_CONNECTION.get(), endpoint.serialize());
    }

    public static void clear(ItemStack stack) {
        stack.remove(ModDataComponents.CAT6_CONNECTION.get());
    }
}
