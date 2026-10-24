package com.nolanbaker.pgmodernized.conduit;

/**
 * A momentary button on a conduit run: the switch's body and wiring, but it only closes while
 * pressed, half a second, and lets go by itself. For a lamp pulsed from more than one place, a
 * doorbell, or anything a PLC's rising-edge block should see once per press.
 */
public class ConduitButtonBlock extends ConduitSwitchBlock {
    /** Ticks the contact stays closed after a press. */
    public static final int HOLD_TICKS = 10;

    public ConduitButtonBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean momentary() {
        return true;
    }
}
