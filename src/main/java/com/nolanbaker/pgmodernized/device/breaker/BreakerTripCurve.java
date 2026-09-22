package com.nolanbaker.pgmodernized.device.breaker;

/**
 * Thermal-magnetic trip characteristic shared by every breaker, evaluated once per electrical tick.
 * <p>
 * The magnetic element opens the breaker on the first tick the current reaches
 * {@link #MAGNETIC_MULTIPLE} times the rating. The thermal element integrates the overload:
 * every tick above the rating adds {@code (I/In)^2 - 1} to a heat figure and the breaker opens
 * when the figure reaches {@link #THERMAL_TRIP}. At twice the rating that takes about two seconds,
 * at 1.5x about five seconds, at 1.1x roughly half a minute, and a breaker at or below its rating
 * never trips. Below the rating the heat bleeds off at {@link #COOLING} per tick.
 * <p>
 * Pure arithmetic, no Minecraft types, so it is unit tested directly.
 */
public final class BreakerTripCurve {
    public static final float MAGNETIC_MULTIPLE = 8f;
    public static final float THERMAL_TRIP = 120f;
    public static final float COOLING = 0.5f;

    private BreakerTripCurve() {}

    /** Heat after one tick at the given current. */
    public static float step(float heat, float current, float rating) {
        if(rating <= 0)
            return 0;
        float ratio = Math.abs(current) / rating;
        float overload = ratio * ratio - 1;
        if(overload > 0)
            return heat + overload;
        return cool(heat);
    }

    /** Heat after one tick with the breaker carrying no overload. */
    public static float cool(float heat) {
        return Math.max(0, heat - COOLING);
    }

    /** Whether a breaker with this heat figure and instantaneous current should open. */
    public static boolean trips(float heat, float current, float rating) {
        if(rating <= 0)
            return false;
        return Math.abs(current) >= rating * MAGNETIC_MULTIPLE || heat >= THERMAL_TRIP;
    }
}
