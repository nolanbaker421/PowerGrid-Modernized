package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.device.transformer.TransformerGeometry;
import com.nolanbaker.pgmodernized.device.transformer.TransformerKind;
import com.nolanbaker.pgmodernized.device.transformer.TransformerSize;
import com.nolanbaker.pgmodernized.device.transformer.TransformerSpec;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;

/** Mirrors TransformerBlockEntity.buildCircuit: one ideal coupling per secondary leg against the neutral. */
public class TransformerSimulationTest extends TestHelper {
    private static final float WINDING = 0.02f;

    @Test void nameplatesAndTaps() {
        var pole = TransformerSpec.POLE_480V_240V;
        Assertions.assertEquals(0.25f, pole.legRatio(0, 0), 1e-6, "each half of a 480:240 centre-tapped secondary is a quarter");
        Assertions.assertEquals(0.25f * 1.1f, pole.legRatio(0, 4), 1e-5, "LV tap +10%");
        Assertions.assertEquals(0.25f / 0.9f, pole.legRatio(-4, 0), 1e-5, "HV tap -10% raises the output");
        var pad = TransformerSpec.PAD_10KV_208V;
        Assertions.assertEquals(208 / Math.sqrt(3) / 10000.0, pad.legRatio(0, 0), 1e-6, "a 208 V star is 120 V per phase");
        Assertions.assertEquals(1388, pad.ratedAmps(), 5, "500 kVA at 208 V three-phase: 500000 / (1.732 * 208) per line");
        Assertions.assertEquals("10 kV / 208 V", pad.plate());
        Assertions.assertEquals("480 V / 240 V", pole.plate());
    }

    @Test void unitsClaimTheCellsTheyReachInto() {
        Assertions.assertTrue(TransformerGeometry.cells(TransformerSize.POLE_S, TransformerKind.SPLIT_PHASE, false).isEmpty(), "a small can fits its block");
        Assertions.assertTrue(TransformerGeometry.cells(TransformerSize.POLE_M, TransformerKind.SPLIT_PHASE, true).isEmpty(), "a hung can fits its block");
        var tall = TransformerGeometry.cells(TransformerSize.POLE_L, TransformerKind.SPLIT_PHASE, false);
        Assertions.assertEquals(java.util.List.of(new Vec3i(0, 1, 0)), tall, "the tall can only needs the block above");
        var pad = TransformerGeometry.cells(TransformerSize.PAD, TransformerKind.THREE_PHASE, false);
        Assertions.assertTrue(pad.contains(new Vec3i(0, 1, 0)) && pad.contains(new Vec3i(1, 0, 0)) && pad.contains(new Vec3i(-1, 1, 0)), "the tank takes the block above and 4 px either side");
        Assertions.assertFalse(pad.contains(new Vec3i(0, 0, -1)), "nothing stands in front of the base cell");
        Assertions.assertTrue(pad.contains(new Vec3i(0, 0, 1)), "the tank reaches into the cell behind");
        var power = TransformerGeometry.cells(TransformerSize.POWER_L, TransformerKind.THREE_PHASE, false);
        Assertions.assertEquals(11, power.size(), "the biggest unit fills 3 x 2 x 2 cells: beside, above and behind its base");
        Assertions.assertEquals(new Vec3i(-1, 0, 1), TransformerGeometry.rotateCell(new Vec3i(1, 0, 1), net.minecraft.core.Direction.EAST));
        Assertions.assertEquals(new Vec3i(1, 0, 1), TransformerGeometry.unrotateCell(new Vec3i(-1, 0, 1), net.minecraft.core.Direction.EAST));
    }

    @Test void splitPhaseSecondaryIsCentreTapped() {
        var kind = TransformerKind.SPLIT_PHASE;
        var spec = TransformerSpec.POLE_480V_240V;
        var Net = new Network();
        var h1 = Net.V(480);
        var h2 = Net.V(0);
        var neutral = Net.V(0);
        var x1 = Net.N();
        var x2 = Net.N();
        var mid1 = Net.N();
        var mid2 = Net.N();
        float ratio = spec.legRatio(0, 0);
        Net.TR(ratio, WINDING, h1, h2, mid1, neutral);
        Net.TR(ratio, WINDING, h1, h2, neutral, mid2);
        Net.W(0.001f, mid1, x1);
        Net.W(0.001f, mid2, x2);
        Net.W(12, x1, neutral);
        Net.W(24, x1, x2);
        Net.calculate(5);
        Assertions.assertEquals(120, x1.getVoltage(), 2, "X1 sits at half the secondary above neutral");
        Assertions.assertEquals(-120, x2.getVoltage(), 2, "X2 sits at half the secondary below neutral");
        Assertions.assertEquals(240, x1.getVoltage() - x2.getVoltage(), 4, "X1 to X2 is the nameplate");
        Assertions.assertTrue(kind.reversed(1) && !kind.reversed(0));
    }

    @Test void threePhaseIsDeltaPrimaryStarSecondary() {
        var kind = TransformerKind.THREE_PHASE;
        var spec = TransformerSpec.PAD_1KV_208V;
        var Net = new Network();
        FloatingNode[] h = {Net.V(1000), Net.V(0), Net.V(0)};
        var x0 = Net.V(0);
        var x = new FloatingNode[3];
        for(int leg = 0; leg < 3; ++leg) {
            var pair = kind.primaryPair(leg);
            var mid = Net.N();
            x[leg] = Net.N();
            Net.TR(spec.legRatio(0, 0), WINDING, h[pair[0]], h[pair[1]], mid, x0);
            Net.W(0.001f, mid, x[leg]);
            Net.W(50, x[leg], x0);
        }
        Net.calculate(5);
        Assertions.assertEquals(120, x[0].getVoltage(), 2, "X1 follows H1-H2 at 120 V per phase");
        Assertions.assertEquals(0, x[1].getVoltage(), 1, "X2 follows H2-H3, which is zero");
        Assertions.assertEquals(-120, x[2].getVoltage(), 2, "X3 follows H3-H1");
    }
}
