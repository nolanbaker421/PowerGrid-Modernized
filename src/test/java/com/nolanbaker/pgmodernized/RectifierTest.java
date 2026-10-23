package com.nolanbaker.pgmodernized;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.special.ACVoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.special.CapacitorWire;
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire;

/** Mirrors RectifierBlockEntity.buildCircuit: the diode's polarity, and a six-pulse bridge on a three-phase source. */
public class RectifierTest extends TestHelper {
    private static final double HZ = 4.5;
    private static final int SUB_TICKS = 32;

    private static PNJunctionWire diode(IElectricNode anode, IElectricNode cathode) {
        return new PNJunctionWire(1e-6, 0.002, 22, 1.5, 1000, 1e-6, anode, cathode);
    }

    @Test void aDiodeConductsFromItsFirstNodeToItsSecond() {
        var Net = new Network();
        var plus = Net.V(48);
        var ground = Net.V(0);
        var mid = Net.N();
        var forward = diode(plus, mid);
        Net.network.addWire(forward);
        var load = Net.W(10, mid, ground);
        Net.calculate(5);
        Assertions.assertTrue(Math.abs(load.current()) > 4, "a forward diode passes most of 48 V over 10 ohms: " + load.current());

        var Net2 = new Network();
        var plus2 = Net2.V(48);
        var ground2 = Net2.V(0);
        var mid2 = Net2.N();
        Net2.network.addWire(diode(mid2, plus2));
        var load2 = Net2.W(10, mid2, ground2);
        Net2.calculate(5);
        Assertions.assertTrue(Math.abs(load2.current()) < 0.01, "a reversed diode blocks: " + load2.current());
    }

    /** Three sources 120° apart, a star point, each line through a diode up to the + rail and down from the - rail. */
    private static double[] bridge(Network Net, double farads) {
        var star = Net.V(0);
        var plus = Net.N();
        var minus = Net.N();
        for(int k = 0; k < 3; ++k) {
            var line = Net.N();
            var source = new ACVoltageSourceCoupling(line, null, 0.001, 120 * Math.sqrt(2), HZ);
            source.setPhaseOffset(-2 * Math.PI / 3 * k);
            source.setSamplingPolicy(64, SUB_TICKS);
            Net.network.addNode(source);
            Net.network.addWire(diode(line, plus));
            Net.network.addWire(diode(minus, line));
        }
        if(farads > 0)
            Net.network.addWire(new CapacitorWire(farads, plus, minus));
        var load = Net.W(50, plus, minus);
        double sum = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        int samples = 0;
        for(int t = 0; t < 200; ++t) {
            Net.calculate(SUB_TICKS);
            if(t < 40)
                continue;
            double v = load.potentialDifference();
            sum += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
            ++samples;
        }
        return new double[] {sum / samples, min, max};
    }

    @Test void aThreePhaseBridgeMakesPositiveSixPulseDc() {
        var r = bridge(new Network(), 0);
        // 1.35 times the line-to-line voltage (208 V) is 281 V, less two diode drops.
        Assertions.assertTrue(r[0] > 240 && r[0] < 300, "mean DC " + r[0]);
        Assertions.assertTrue(r[1] > 200, "six-pulse ripple never falls far: min " + r[1]);
    }

    @Test void aSmoothingCapacitorFlattensTheRipple() {
        var bare = bridge(new Network(), 0);
        var smoothed = bridge(new Network(), 0.05);
        Assertions.assertTrue(smoothed[2] - smoothed[1] < bare[2] - bare[1], "less ripple with 50 mF: " + (smoothed[2] - smoothed[1]) + " vs " + (bare[2] - bare[1]));
        Assertions.assertTrue(smoothed[0] > 240, "and the mean holds: " + smoothed[0]);
    }
}
