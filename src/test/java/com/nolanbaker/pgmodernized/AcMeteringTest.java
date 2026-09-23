package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.device.breaker.BreakerTripCurve;
import com.nolanbaker.pgmodernized.util.AcReadings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.ACVoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.special.LRSeriesWire;
import org.patryk3211.powergrid.electricity.sim.special.WattmeterWire;

/**
 * The AC build's meters against the powergrid-ac solver: a sine source through the same shunt,
 * sense and wattmeter arrangement the CT cabinet, ammeter, voltmeter and breaker panel build.
 */
public class AcMeteringTest extends TestHelper {
    private static final double HZ = 4;
    private static final int SUB_TICKS = 32;        // 160 samples per cycle at 4 Hz
    private static final int SETTLE_TICKS = 80;     // 4 s, eight filter time constants

    private static ACVoltageSourceCoupling ac(Network Net, FloatingNode node, double rms) {
        var source = new ACVoltageSourceCoupling(node, null, 0.001, rms * Math.sqrt(2), HZ);
        source.setSamplingPolicy(SUB_TICKS * 20 / (int) HZ, SUB_TICKS);
        Net.network.addNode(source);
        return source;
    }

    @Test void filteredRmsReadsTheLoadWhereASampleAndOneTickWander() {
        var Net = new Network();
        var live = Net.N();
        ac(Net, live, 120);
        var neutral = Net.V(0);
        var load = Net.N();
        var shunt = Net.SW(0.005f, live, load, true);   // a branch breaker
        Net.W(12, load, neutral);                       // 10 A rms
        var reading = new AcReadings.Filter();

        double minSample = Double.MAX_VALUE, maxSample = 0, minTick = Double.MAX_VALUE, maxTick = 0;
        double minFiltered = Double.MAX_VALUE, maxFiltered = 0;
        for(int t = 0; t < SETTLE_TICKS; ++t) {
            Net.calculate(SUB_TICKS);
            reading.sample(shunt);
            if(t >= SETTLE_TICKS / 2) {
                minSample = Math.min(minSample, Math.abs(shunt.current()));
                maxSample = Math.max(maxSample, Math.abs(shunt.current()));
                minTick = Math.min(minTick, shunt.rmsCurrent());
                maxTick = Math.max(maxTick, shunt.rmsCurrent());
                minFiltered = Math.min(minFiltered, reading.rmsCurrent());
                maxFiltered = Math.max(maxFiltered, reading.rmsCurrent());
            }
        }
        System.out.printf("4 Hz, 10 A rms: sample %.2f..%.2f, one tick %.2f..%.2f, filtered %.2f..%.2f%n",
                minSample, maxSample, minTick, maxTick, minFiltered, maxFiltered);
        Assertions.assertEquals(10, reading.rmsCurrent(), 0.3, "rms of a 120 V sine into 12 ohms");
        Assertions.assertEquals(10, reading.signedRmsCurrent(), 0.3, "symmetric current reads positive");
        Assertions.assertTrue(Math.abs(reading.meanCurrent()) < 1, "no direct component beyond the filter's ripple: " + reading.meanCurrent());
        Assertions.assertTrue(Math.abs(reading.meanCurrent()) < AcReadings.DC_FRACTION * reading.rmsCurrent() / 2, "well clear of the sign threshold");
        Assertions.assertTrue(maxSample - minSample > 3, "the instantaneous sample wanders");
        Assertions.assertTrue(maxTick - minTick > 1, "one tick's rms wanders at 4 Hz");
        Assertions.assertTrue(maxFiltered - minFiltered < 0.6, "the filtered reading holds still");
        Assertions.assertEquals(10, shunt.lastRmsCurrent(), 1.5, "the fork's settled rms, used by the breaker panel");
    }

    @Test void breakerTripsOnRms() {
        var Net = new Network();
        var live = Net.N();
        ac(Net, live, 120);
        var neutral = Net.V(0);
        var load = Net.N();
        var breaker = Net.SW(0.005f, live, load, true);
        Net.W(6, load, neutral);                        // 20 A rms on a 10 A breaker

        float heat = 0;
        int tripped = -1;
        for(int t = 0; t < 200 && tripped < 0; ++t) {
            Net.calculate(SUB_TICKS);
            float amps = (float) breaker.lastRmsCurrent();
            heat = BreakerTripCurve.step(heat, amps, 10);
            if(BreakerTripCurve.trips(heat, amps, 10)) {
                breaker.setState(false);
                tripped = t;
            }
        }
        Assertions.assertTrue(tripped > 0, "overloaded branch never tripped");
        Assertions.assertTrue(tripped < 80, "took " + tripped + " ticks to trip at 2x");
        for(int t = 0; t < 80; ++t)   // the settled figure decays with a quarter-second time constant
            Net.calculate(SUB_TICKS);
        Assertions.assertEquals(0, breaker.lastRmsCurrent(), 0.05);
    }

    @Test void wattmeterSeesRealPowerAndPowerFactor() {
        var Net = new Network();
        var live = Net.N();
        ac(Net, live, 120);
        var reference = Net.V(0);
        var out = Net.N();
        // CT cabinet channel: sense from In to Reference, wattmeter shunt from In to Out.
        var sense = Net.W(1_000_000f, live, reference);
        var shunt = new WattmeterWire(0.001, sense, live, out);
        Net.network.addWire(shunt);
        // 12 ohms in series with a reactance of 12 ohms at 4 Hz: |Z| = 16.97, I = 7.07 A, PF = 0.707.
        double inductance = 12 / (2 * Math.PI * HZ);
        Net.network.addWire(new LRSeriesWire(inductance, 12, out, reference));
        var reading = new AcReadings.Filter();

        for(int t = 0; t < SETTLE_TICKS; ++t) {
            Net.calculate(SUB_TICKS);
            reading.sample(sense, shunt, shunt.drainRealPower());
        }
        System.out.printf("coil: %.1f V  %.2f A  %.0f W  pf %.3f%n", reading.signedRmsVoltage(), reading.signedRmsCurrent(), reading.realPower(), reading.powerFactor());
        Assertions.assertEquals(120, reading.signedRmsVoltage(), 2, "rms voltage of the feed");
        Assertions.assertEquals(7.07, reading.signedRmsCurrent(), 0.4, "rms current into the coil");
        Assertions.assertEquals(600, reading.realPower(), 60, "real power is I^2 R");
        Assertions.assertEquals(0.707, reading.powerFactor(), 0.05, "power factor of an equal R and X");
    }

    @Test void directCurrentReadsExactlyAsBefore() {
        var Net = new Network();
        var positive = Net.V(48);
        var negative = Net.V(0);
        var mid = Net.N();
        var shunt = Net.W(0.001f, positive, mid);
        Net.W(4, mid, negative);
        var reversed = Net.W(4, negative, mid);   // a second load, wired the other way round
        var forward = new AcReadings.Filter();
        var backward = new AcReadings.Filter();
        Net.calculate();
        forward.sample(shunt);
        backward.sample(reversed);
        Assertions.assertEquals(24, shunt.current(), 0.05);
        Assertions.assertEquals(shunt.current(), forward.signedRmsCurrent(), 1e-9, "same number, same tick");
        Assertions.assertTrue(reversed.current() < 0, "direction under test");
        Assertions.assertEquals(reversed.current(), backward.signedRmsCurrent(), 1e-9, "sign is kept on dc");
        Assertions.assertEquals(reversed.potentialDifference(), backward.signedRmsVoltage(), 1e-9, "voltage sign is kept on dc");

        // A steady reading follows a change on the very next tick, with no smoothing lag.
        positive.setVoltage(24);
        Net.calculate();
        forward.sample(shunt);
        Assertions.assertEquals(shunt.current(), forward.signedRmsCurrent(), 1e-9, "no lag on dc");
        Assertions.assertEquals(12, forward.signedRmsCurrent(), 0.05);
    }
}
