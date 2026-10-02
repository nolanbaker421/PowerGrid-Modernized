package com.nolanbaker.pgmodernized.util;

import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;

/** Entry point kept for the placement code: ticks the entity's {@link BodyRide} if it has one. */
public final class SubLevelStick {
    private SubLevelStick() {}

    public static void stick(BlockWireEntity entity) {
        if(entity instanceof BodyRider rider)
            rider.ride().tick(entity);
    }
}
