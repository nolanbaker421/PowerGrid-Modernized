package com.nolanbaker.pgmodernized.radio;

import net.minecraft.world.phys.Vec3;

/**
 * A block entity that coax plugs into: a radio or a collector (one port), an antenna (one port), or
 * a hanger (two per clip). Every port takes one coax.
 */
public interface ICoaxPort {
    CoaxSupport coaxSupport();

    /** World-space point a coax in the given port attaches to. */
    Vec3 coaxPosition(int port);

    default int coaxPortCount() {
        return 1;
    }

    /** Which port a click at the given block-local position (in blocks, 0..1) means. */
    default int coaxPortAt(Vec3 localHit) {
        return 0;
    }

    /** The port a signal entering {@code port} leaves by, or -1 when this block ends the run (a radio, an antenna). */
    default int coaxPassThrough(int port) {
        return -1;
    }
}
