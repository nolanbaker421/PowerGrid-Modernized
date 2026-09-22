package com.nolanbaker.pgmodernized.network;

import net.minecraft.world.phys.Vec3;

/**
 * A block entity that Cat6 cables plug into. Device blocks carry a single jack as one of their terminals,
 * {@link NetworkJackBlockEntity} is a standalone wall jack, and {@link NetworkSwitchBlockEntity} has
 * eight ports. Every port takes one cable; all ports of one block share the same network node.
 */
public interface INetworkJack {
    JackSupport networkJack();

    /** World-space point a cable in the given port attaches to. */
    Vec3 jackPosition(int port);

    default int portCount() {
        return 1;
    }

    /** Which port a click at the given block-local position (in blocks, 0..1) means. */
    default int portAt(Vec3 localHit) {
        return 0;
    }

    /** Index of the jack among the block's Power Grid terminals, or -1 when the block is not electric. */
    default int jackTerminalIndex() {
        return -1;
    }

    /** A cable was plugged in or removed (both sides; check {@code isClientSide} before acting). */
    default void onCablesChanged() {}
}
