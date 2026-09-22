package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.device.breaker.BreakerTripCurve;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

/** Mirrors BreakerPanelBlockEntity.buildCircuit: line -> main switch -> bus -> branch switches -> loads back to neutral. */
public class BreakerPanelSimulationTest extends TestHelper {
    private static final float MAIN_R = 0.002f, BRANCH_R = 0.005f;

    private static double amps(SwitchedWire wire) {
        return Math.abs(wire.current());
    }

    @Test void branchCurrentsSumAtTheMain() {
        var Net = new Network();
        var line = Net.V(120);
        var neutral = Net.V(0);
        var bus = Net.N();
        var l1 = Net.N();
        var l2 = Net.N();
        var main = Net.SW(MAIN_R, line, bus, true);
        var b1 = Net.SW(BRANCH_R, bus, l1, true);
        var b2 = Net.SW(BRANCH_R, bus, l2, true);
        Net.W(12, l1, neutral);
        Net.W(24, l2, neutral);

        Net.calculate(5);
        Assertions.assertEquals(10, amps(b1), 0.1);
        Assertions.assertEquals(5, amps(b2), 0.1);
        Assertions.assertEquals(15, amps(main), 0.1);

        b2.setState(false);
        Net.calculate(5);
        Assertions.assertEquals(10, amps(main), 0.1);
        Assertions.assertEquals(0, amps(b2), 0.01);

        main.setState(false);
        Net.calculate(5);
        Assertions.assertEquals(0, amps(main), 0.01);
        Assertions.assertEquals(0, amps(b1), 0.01);
    }

    @Test void overloadedBranchTripsAndTheOthersKeepRunning() {
        var Net = new Network();
        var line = Net.V(120);
        var neutral = Net.V(0);
        var bus = Net.N();
        var l1 = Net.N();
        var l2 = Net.N();
        var main = Net.SW(MAIN_R, line, bus, true);
        var b1 = Net.SW(BRANCH_R, bus, l1, true);
        var b2 = Net.SW(BRANCH_R, bus, l2, true);
        Net.W(6, l1, neutral);   // 20 A on a 10 A breaker
        Net.W(24, l2, neutral);  // 5 A on a 20 A breaker

        float heat1 = 0, heat2 = 0;
        int tripped = -1;
        for(int t = 0; t < 200 && tripped < 0; ++t) {
            Net.calculate();
            heat1 = BreakerTripCurve.step(heat1, (float) amps(b1), 10);
            heat2 = BreakerTripCurve.step(heat2, (float) amps(b2), 20);
            Assertions.assertFalse(BreakerTripCurve.trips(heat2, (float) amps(b2), 20), "healthy branch tripped");
            if(BreakerTripCurve.trips(heat1, (float) amps(b1), 10)) {
                b1.setState(false);
                tripped = t;
            }
        }
        Assertions.assertTrue(tripped > 0, "overloaded branch never tripped");
        Assertions.assertTrue(tripped < 60, "took " + tripped + " ticks to trip at 2x");

        Net.calculate(5);
        Assertions.assertEquals(0, amps(b1), 0.01);
        Assertions.assertEquals(5, amps(b2), 0.1);
        Assertions.assertEquals(5, amps(main), 0.1);
    }
}
