package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.device.breaker.BreakerTripCurve;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BreakerTripCurveTest {
    /** Ticks until a breaker of the given rating trips at a constant current, or -1 if it holds for the whole run. */
    private static int ticksToTrip(float rating, float current, int limit) {
        float heat = 0;
        for(int t = 0; t < limit; ++t) {
            heat = BreakerTripCurve.step(heat, current, rating);
            if(BreakerTripCurve.trips(heat, current, rating))
                return t + 1;
        }
        return -1;
    }

    @Test void holdsAtRatedCurrent() {
        Assertions.assertEquals(-1, ticksToTrip(20, 20, 20 * 60 * 5));
        Assertions.assertEquals(-1, ticksToTrip(20, 19.9f, 20 * 60 * 5));
    }

    @Test void twiceRatingTripsInAboutTwoSeconds() {
        int ticks = ticksToTrip(20, 40, 1000);
        Assertions.assertTrue(ticks >= 30 && ticks <= 50, "tripped after " + ticks + " ticks");
    }

    @Test void slowerAtMildOverload() {
        int mild = ticksToTrip(20, 22, 20000);
        int heavy = ticksToTrip(20, 30, 20000);
        Assertions.assertTrue(mild > heavy, "1.1x (" + mild + ") should take longer than 1.5x (" + heavy + ")");
        Assertions.assertTrue(mild >= 20 * 20, "1.1x should hold for at least 20 s, tripped after " + mild);
    }

    @Test void magneticTripIsInstant() {
        Assertions.assertEquals(1, ticksToTrip(20, 20 * BreakerTripCurve.MAGNETIC_MULTIPLE, 10));
        Assertions.assertEquals(1, ticksToTrip(800, 800 * BreakerTripCurve.MAGNETIC_MULTIPLE + 1, 10));
    }

    @Test void heatBleedsOff() {
        float heat = 0;
        for(int t = 0; t < 20; ++t)
            heat = BreakerTripCurve.step(heat, 40, 20);
        Assertions.assertTrue(heat > 0);
        for(int t = 0; t < 1000; ++t)
            heat = BreakerTripCurve.step(heat, 10, 20);
        Assertions.assertEquals(0, heat);
    }

    @Test void emptySpaceNeverTrips() {
        Assertions.assertFalse(BreakerTripCurve.trips(1e9f, 1e9f, 0));
        Assertions.assertEquals(0, BreakerTripCurve.step(5, 1000, 0));
    }
}
