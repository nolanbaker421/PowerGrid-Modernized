package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEndpointType;

/**
 * The pending first end of a conduit run, stored on the conduit item between clicks: a hub on a
 * conduit box, or the free end of a run being extended. Kept off Power Grid's own connection
 * component so its wire handler never turns it into an electrical wire.
 */
public final class ConduitConnection {
    private ConduitConnection() {}

    @Nullable
    public static IWireEndpoint get(ItemStack stack) {
        var tag = stack.get(ModDataComponents.CONDUIT_CONNECTION.get());
        return tag == null ? null : WireEndpointType.deserialize(tag);
    }

    public static boolean has(ItemStack stack) {
        return stack.has(ModDataComponents.CONDUIT_CONNECTION.get());
    }

    public static void set(ItemStack stack, IWireEndpoint endpoint) {
        stack.set(ModDataComponents.CONDUIT_CONNECTION.get(), endpoint.serialize());
    }

    public static void clear(ItemStack stack) {
        stack.remove(ModDataComponents.CONDUIT_CONNECTION.get());
    }
}
