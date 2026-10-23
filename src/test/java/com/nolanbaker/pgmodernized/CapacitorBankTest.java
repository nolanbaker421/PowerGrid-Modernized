package com.nolanbaker.pgmodernized;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.special.ACVoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.special.CapacitorWire;

/** Mirrors one leg of CapacitorBankBlockEntity: a capacitor behind a contact draws its reactive current on AC and nothing on DC. */
public class CapacitorBankTest extends TestHelper {
    private static final double HZ = 4.5;
    private static final int SUB_TICKS = 32;

    @Test void aCapacitorDrawsTwoPiFCVoltsOnAlternatingCurrent() {
        var Net = new Network();
        var line = Net.N();
        var neutral = Net.V(0);
        var source = new ACVoltageSourceCoupling(line, null, 0.001, 120 * Math.sqrt(2), HZ);
        source.setSamplingPolicy(64, SUB_TICKS);
        Net.network.addNode(source);
        var mid = Net.N();
        var contact = Net.SW(0.002f, line, mid, true);
        double farads = 0.001;
        Net.network.addWire(new CapacitorWire(farads, mid, neutral));
        double sumSquares = 0;
        int samples = 0;
        for(int t = 0; t < 200; ++t) {
            Net.calculate(SUB_TICKS);
            if(t < 40)
                continue;
            double i = contact.current();
            sumSquares += i * i;
            ++samples;
        }
        double rms = Math.sqrt(sumSquares / samples);
        double expected = 120 * 2 * Math.PI * HZ * farads;
        Assertions.assertEquals(expected, rms, expected * 0.25, "V times 2 pi f C: " + expected + " A, got " + rms);
        contact.setState(false);
        for(int t = 0; t < 20; ++t)
            Net.calculate(SUB_TICKS);
        Assertions.assertTrue(Math.abs(contact.current()) < 1e-3, "off the line nothing flows");
    }

    @Test void aCapacitorOnDirectCurrentChargesAndThenDrawsNothing() {
        var Net = new Network();
        var plus = Net.V(48);
        var ground = Net.V(0);
        var mid = Net.N();
        var contact = Net.SW(0.002f, plus, mid, true);
        Net.network.addWire(new CapacitorWire(0.001, mid, ground));
        for(int t = 0; t < 100; ++t)
            Net.calculate(SUB_TICKS);
        Assertions.assertTrue(Math.abs(contact.current()) < 0.01, "charged: " + contact.current());
    }
}
