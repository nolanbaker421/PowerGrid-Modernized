package com.nolanbaker.pgmodernized.network;

import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

    private static final String LEVEL_KEY = "PgmLevel";

    /** Remembers the level too, so the second click cannot pair ends from different worlds or sub-levels. */
    public static void set(ItemStack stack, IWireEndpoint endpoint, Level level) {
        var tag = endpoint.serialize();
        tag.putString(LEVEL_KEY, levelKey(level));
        stack.set(ModDataComponents.CAT6_CONNECTION.get(), tag);
    }

    /**
     * The pending end if it belongs to this level and still exists here; otherwise it is dropped
     * and null comes back. A stale or foreign end must never reach the path finder.
     */
    @Nullable
    public static IWireEndpoint get(ItemStack stack, Level level) {
        var tag = stack.get(ModDataComponents.CAT6_CONNECTION.get());
        if(tag == null)
            return null;
        if(tag.contains(LEVEL_KEY) && !tag.getString(LEVEL_KEY).equals(levelKey(level))) {
            clear(stack);
            return null;
        }
        var endpoint = JackEndpoint.from(WireEndpointType.deserialize(tag));
        if(endpoint == null || !endpoint.isValid(level)) {
            clear(stack);
            return null;
        }
        return endpoint;
    }

    /** The level's dimension plus its identity, which tells a sub-level apart from the world it sits in. */
    static String levelKey(Level level) {
        return level.dimension().location() + "#" + level.getClass().getName();
    }

    public static void clear(ItemStack stack) {
        stack.remove(ModDataComponents.CAT6_CONNECTION.get());
    }
}
