package com.nolanbaker.pgmodernized.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Registers the wall-view key; mod bus. */
public final class WallViewKeys {
    private WallViewKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(WallView.TOGGLE);
    }
}
