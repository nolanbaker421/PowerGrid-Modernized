package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.device.transformer.TransformerKind;
import com.nolanbaker.pgmodernized.device.transformer.TransformerRatio;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;

/** Mirrors TransformerBlockEntity.buildCircuit: one ideal coupling per secondary leg against the neutral. */
public class TransformerSimulationTest extends TestHelper {
    private static final float WINDING = 0.02f;

    @Test void ratioPresetsAreOrderedAndSymmetric() {
        Assertions.assertEquals(1f, TransformerRatio.value(TransformerRatio.UNITY));
        Assertions.assertEquals("1 : 1", TransformerRatio.describe(TransformerRatio.UNITY));
        for(int i = 1; i < TransformerRatio.COUNT; ++i)
            Assertions.assertTrue(TransformerRatio.value(i) < TransformerRatio.value(i - 1), "step-up first, step-down last");
        Assertions.assertEquals(60f, TransformerRatio.value(0), 1e-6);
        Assertions.assertEquals(1 / 60f, TransformerRatio.value(TransformerRatio.COUNT - 1), 1e-6);
    }

    @Test void splitPhaseSecondaryIsCentreTapped() {
        var kind = TransformerKind.SPLIT_PHASE;
        var Net = new Network();
        var h1 = Net.V(120);
        var h2 = Net.V(0);
        var neutral = Net.V(0);
        var x1 = Net.N();
        var x2 = Net.N();
        float ratio = 2f;   // 1 : 2 across the whole secondary, so 120 V in gives 240 V X1 to X2
        // Leg X1: mid to neutral; leg X2: neutral to mid (reversed).
        var mid1 = Net.N();
        var mid2 = Net.N();
        Net.TR(ratio * kind.legRatio(0), WINDING, h1, h2, mid1, neutral);
        Net.TR(ratio * kind.legRatio(1), WINDING, h1, h2, neutral, mid2);
        Net.W(0.001f, mid1, x1);
        Net.W(0.001f, mid2, x2);
        Net.W(12, x1, neutral);      // 10 A on X1
        Net.W(24, x1, x2);           // a 240 V load across both legs
        Net.calculate(5);
        Assertions.assertEquals(120, x1.getVoltage(), 2, "X1 sits at half the secondary above neutral");
        Assertions.assertEquals(-120, x2.getVoltage(), 2, "X2 sits at half the secondary below neutral");
        Assertions.assertEquals(240, x1.getVoltage() - x2.getVoltage(), 4, "X1 to X2 is the full ratio");
        Assertions.assertTrue(kind.reversed(1) && !kind.reversed(0));
    }

    @Test void threePhaseIsDeltaPrimaryStarSecondary() {
        var kind = TransformerKind.THREE_PHASE;
        var Net = new Network();
        // A steady test: one primary line high, the others at zero, so each delta pair is easy to read.
        FloatingNode[] h = {Net.V(100), Net.V(0), Net.V(0)};
        var x0 = Net.V(0);
        var x = new FloatingNode[3];
        for(int leg = 0; leg < 3; ++leg) {
            var pair = kind.primaryPair(leg);
            var mid = Net.N();
            x[leg] = Net.N();
            Net.TR(0.5f * kind.legRatio(leg), WINDING, h[pair[0]], h[pair[1]], mid, x0);
            Net.W(0.001f, mid, x[leg]);
            Net.W(50, x[leg], x0);
        }
        Net.calculate(5);
        Assertions.assertEquals(50, x[0].getVoltage(), 1, "X1 follows H1-H2");
        Assertions.assertEquals(0, x[1].getVoltage(), 1, "X2 follows H2-H3, which is zero");
        Assertions.assertEquals(-50, x[2].getVoltage(), 1, "X3 follows H3-H1");
        Assertions.assertArrayEquals(new int[] {0, 1}, kind.primaryPair(0));
        Assertions.assertArrayEquals(new int[] {2, 0}, kind.primaryPair(2));
    }
}
