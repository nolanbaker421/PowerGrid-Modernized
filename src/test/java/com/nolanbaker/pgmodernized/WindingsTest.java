package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.ac.Windings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WindingsTest {
    @Test
    public void inductanceGivesTheNameplatePowerFactor() {
        float r = 260, pf = 0.85f;
        double x = 2 * Math.PI * Windings.NOMINAL_HZ * Windings.inductance(r, pf);
        Assertions.assertEquals(pf, r / Math.hypot(r, x), 1e-3, "R over |Z| is the power factor");
        // 120 V a phase into that winding is 120 W real over three phases: the pump's speed cap.
        double i = 120 / Math.hypot(r, x);
        Assertions.assertEquals(120, 3 * i * i * r, 1);
    }

    @Test
    public void resistanceForMeetsTheDemandWithTheReactanceIn() {
        double x = Windings.reactance(8, 0.9f);
        for(double watts : new double[] {10, 100, 600}) {
            double r = Windings.resistanceFor(120, watts, x);
            double i = 120 / Math.hypot(r, x);
            Assertions.assertEquals(watts, i * i * r, watts * 1e-6, "demand " + watts);
        }
        // Past the maximum power point the winding sits at R = X.
        Assertions.assertEquals(x, Windings.resistanceFor(120, 1e9, x), 1e-9);
    }
}
