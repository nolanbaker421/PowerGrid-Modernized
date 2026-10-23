package com.nolanbaker.pgmodernized.bus;

import net.minecraft.core.Direction;

/**
 * A block whose bus can be joined by a bus duct, or by another such block, through hidden bus
 * bars: a duct segment, a duct end feed, a switchgear section (from above) or a switchgear cell.
 * The four bus conductors are numbered 0 L1, 1 L2, 2 L3, 3 N.
 */
public interface IBusJoin {
    /**
     * The terminal that conductor {@code k} of a bus arriving from the block on {@code side} joins,
     * or -1 when nothing joins on that side.
     */
    int busJoin(Direction side, int k);
}
