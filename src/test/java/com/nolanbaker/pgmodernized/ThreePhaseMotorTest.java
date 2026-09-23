package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.device.motor.PhaseSenseWire;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.ACVoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.special.LRSeriesWire;

/** Mirrors ThreePhaseMotorBlockEntity.buildCircuit on a three-phase source: frequency and sequence from the sense wires. */
public class ThreePhaseMotorTest extends TestHelper {
    private static final double HZ = 4.5;
    private static final int SUB_TICKS = 32;

    /** Three sources 120° apart into star-connected coils, with a sense wire on each phase. */
    private static PhaseSenseWire[] rig(Network Net, boolean reversed) {
        var star = Net.N();
        var senses = new PhaseSenseWire[3];
        for(int k = 0; k < 3; ++k) {
            var line = Net.N();
            var source = new ACVoltageSourceCoupling(line, null, 0.001, 120 * Math.sqrt(2), HZ);
            int order = reversed ? (3 - k) % 3 : k;
            source.setPhaseOffset(-2 * Math.PI / 3 * order);
            source.setSamplingPolicy(64, SUB_TICKS);
            Net.network.addNode(source);
            Net.network.addWire(new LRSeriesWire(0.08, 8, line, star));
            senses[k] = new PhaseSenseWire(1_000_000, line, star);
            Net.network.addWire(senses[k]);
        }
        return senses;
    }

    @Test void readsFrequencyAndForwardSequence() {
        var Net = new Network();
        var senses = rig(Net, false);
        for(int t = 0; t < 60; ++t)
            Net.calculate(SUB_TICKS);
        Assertions.assertEquals(HZ, senses[0].frequency(), 0.2, "supply frequency");
        Assertions.assertEquals(HZ, senses[1].frequency(), 0.2);
        Assertions.assertEquals(1, PhaseSenseWire.sequence(senses[0], senses[1]), "U-V-W");
    }

    @Test void swappedPhasesReadTheReverseSequence() {
        var Net = new Network();
        var senses = rig(Net, true);
        for(int t = 0; t < 60; ++t)
            Net.calculate(SUB_TICKS);
        Assertions.assertEquals(-1, PhaseSenseWire.sequence(senses[0], senses[1]), "U-W-V");
    }

    @Test void directCurrentHasNoFrequencyAndNoSequence() {
        var Net = new Network();
        var star = Net.V(0);
        FloatingNode[] lines = {Net.V(48), Net.V(0), Net.V(0)};
        var senses = new PhaseSenseWire[3];
        for(int k = 0; k < 3; ++k) {
            Net.W(8, lines[k], star);
            senses[k] = new PhaseSenseWire(1_000_000, lines[k], star);
            Net.network.addWire(senses[k]);
        }
        for(int t = 0; t < 20; ++t)
            Net.calculate();
        Assertions.assertEquals(0, senses[0].frequency(), 1e-9);
        Assertions.assertEquals(0, PhaseSenseWire.sequence(senses[0], senses[1]));
    }
}
