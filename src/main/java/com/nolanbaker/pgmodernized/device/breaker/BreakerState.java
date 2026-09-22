package com.nolanbaker.pgmodernized.device.breaker;

/** Handle position of an installed breaker. Empty spaces have no state. */
public enum BreakerState {
    OFF,
    ON,
    /** Opened by an overcurrent. Like a real breaker it must be switched to OFF before it will close again. */
    TRIPPED;

    public static BreakerState fromOrdinal(int ordinal) {
        var values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OFF;
    }

    public String key() {
        return name().toLowerCase();
    }
}
