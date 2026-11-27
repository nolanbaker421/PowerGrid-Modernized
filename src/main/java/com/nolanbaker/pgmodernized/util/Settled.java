package com.nolanbaker.pgmodernized.util;

import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;

/**
 * Whether a wire's readings mean anything this tick. Power Grid's {@code isConverged()} is false
 * while a network settles, a tick or two, and also for good once the wire has no network behind
 * it any more, or one with nothing left to solve, as happens to the island a device is left on
 * when something between it and its source is broken: its readings then freeze at the last
 * solve. A device that only reads while converged keeps showing the last state it saw, a light
 * staying lit after its switch box is broken. So after a short grace without convergence the
 * wire counts as dead and its readings as zero. One of these per wire a device reads.
 */
public final class Settled {
    /** Ticks without convergence before a wire counts as dead; a live network settles well inside this. */
    public static final int GRACE = 10;

    public enum State {
        /** Converged: the readings are good. */
        LIVE,
        /** No network behind the wire, or none that will settle: it carries nothing. */
        DEAD,
        /** Settling: keep the last reading. */
        WAIT
    }

    private int unsettled;

    public State of(@Nullable AbstractElectricWire wire) {
        if(wire == null || wire.getNetwork() == null) {
            unsettled = 0;
            return State.DEAD;
        }
        if(wire.isConverged()) {
            unsettled = 0;
            return State.LIVE;
        }
        return ++unsettled > GRACE ? State.DEAD : State.WAIT;
    }

    /** Feeds a readings filter: a sample while live, a reset to zero once dead, nothing while settling. */
    public void sample(@Nullable AbstractElectricWire wire, AcReadings.Filter filter) {
        switch(of(wire)) {
            case LIVE -> filter.sample(wire);
            case DEAD -> filter.reset();
            case WAIT -> {}
        }
    }
}
