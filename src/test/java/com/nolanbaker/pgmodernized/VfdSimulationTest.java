package com.nolanbaker.pgmodernized;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.TransformerCoupling;

/** Mirrors VfdBlockEntity's control loop: 2P2S coupling, fixed series resistance, ratio retuned every tick. */
public class VfdSimulationTest extends TestHelper {
    private static final float MAX_RATIO = 500f, MIN_RATIO = 0.001f, SLEW = 0.35f, CAP_MARGIN = 0.98f, CAP_RECOVERY = 1.02f;

    private static class Loop {
        float ratio = MIN_RATIO, ratioCap = MAX_RATIO, inputReference = 0;

        float step(float setpoint, float vin, float iout, float ilimit) {
            float absVin = Math.abs(vin);
            inputReference = Math.abs(ratio) <= MIN_RATIO * 1.5f ? absVin : Math.max(absVin, inputReference);
            boolean sagging = absVin < inputReference * 0.5f, over = iout > ilimit;
            if((over || sagging) && Math.abs(ratio) > MIN_RATIO) {
                float factor = over ? ilimit / iout : absVin / (inputReference * 0.5f);
                ratioCap = Math.max(MIN_RATIO, Math.min(ratioCap, Math.abs(ratio)) * factor * CAP_MARGIN);
            } else
                ratioCap = Math.min(MAX_RATIO, ratioCap * CAP_RECOVERY + MIN_RATIO);
            float target;
            if(setpoint == 0 || Math.abs(vin) < 0.5f) {
                target = Math.copySign(MIN_RATIO, ratio);
            } else {
                float magnitude = Math.max(MIN_RATIO, Math.min(MAX_RATIO, Math.abs(setpoint / vin)));
                magnitude = Math.min(magnitude, ratioCap);
                target = Math.copySign(magnitude, setpoint * vin);
            }
            if(Math.signum(target) != Math.signum(ratio)) return Math.copySign(MIN_RATIO, target);
            if(Math.abs(target) < Math.abs(ratio)) return target;
            return ratio + (target - ratio) * SLEW;
        }
    }

    /** @return final output voltage; source resistance is a real series wire so the loop sees the terminal voltage sag. */
    private double run(String name, float sourceV, float sourceR, float loadR, float setpoint, float ilimit, int ticks, Double expectVout) {
        var Net = new Network();
        var V = Net.V(sourceV);
        var P2 = Net.V(0);
        FloatingNode P1 = Net.N(), S1 = Net.N(), S2 = Net.N();
        Net.W(Math.max(sourceR, 0.01f), V, P1);
        var loop = new Loop();
        TransformerCoupling TR = Net.TR(loop.ratio, 0.5f, P1, P2, S1, S2);
        Net.W(loadR, S1, S2);
        double vout = 0, iout, vin, iin, maxIin = 0, minTail = Double.MAX_VALUE, maxTail = -Double.MAX_VALUE;
        System.out.println("== " + name);
        for(int t = 0; t < ticks; ++t) {
            Net.calculate();
            vin = P1.getVoltage() - P2.getVoltage();
            vout = S1.getVoltage() - S2.getVoltage();
            iout = Math.abs(TR.getStateValue());
            iin = Math.abs(V.getCurrent());
            maxIin = Math.max(maxIin, iin);
            if(t >= ticks - 10) { minTail = Math.min(minTail, vout); maxTail = Math.max(maxTail, vout); }
            float next = loop.step(setpoint, (float) vin, (float) iout, ilimit);
            if(t < 6 || t % 20 == 0 || t == ticks - 1)
                System.out.printf("t=%3d ratio=%9.4f cap=%9.4f vin=%9.3f vout=%9.3f iout=%7.4f iin=%7.4f%n", t, loop.ratio, loop.ratioCap, vin, vout, iout, iin);
            if(Math.abs(next - loop.ratio) > 1e-5f) { loop.ratio = next; TR.setRatio(loop.ratio); }
        }
        Assertions.assertTrue(maxIin <= sourceV / Math.max(sourceR, 0.01f) + 0.01, name + ": input current exceeded source short-circuit current");
        if(expectVout != null) {
            Assertions.assertEquals(expectVout, vout, Math.abs(expectVout) * 0.04 + 0.5, name + ": output voltage");
            Assertions.assertTrue(maxTail - minTail <= Math.abs(expectVout) * 0.06 + 0.5, name + ": output ripple " + (maxTail - minTail) + " V over last 10 ticks");
        }
        return vout;
    }

    @Test void stepUpStiffSource()      { run("120V stiff -> 480V into 200 ohm (2.4A)", 120, 0, 200, 480, 3, 60, 480.0); }
    @Test void stepDownStiffSource()    { run("480V stiff -> 24V into 10 ohm", 480, 0, 10, 24, 3, 60, 24.0); }
    @Test void reverse()                { run("120V -> -480V into 200 ohm", 120, 0, 200, -480, 3, 60, -480.0); }
    @Test void saggySource()            { run("120V with 5 ohm internal -> 480V into 500 ohm (source sags to ~96V)", 120, 5, 500, 480, 3, 150, 480.0); }
    @Test void currentLimited()         { run("120V -> 480V into 20 ohm, 3A limit (needs 24A)", 120, 0, 20, 480, 3, 150, 60.0); }
    @Test void zeroSetpointIsBrake()    { run("120V, setpoint 0", 120, 0, 100, 0, 3, 20, 0.0); }
    @Test void brownoutStaysBounded()   {
        // 5 ohm source can deliver at most 720 W; 480 V into 100 ohm wants 2304 W. Output must simply sag, not run away.
        // Best case is the source's maximum-power point: 60 V in, 12 A, 720 W -> about 268 V into 100 ohm (minus the 0.5 ohm drop).
        run("120V with 5 ohm internal -> 480V into 100 ohm (impossible, source browns out)", 120, 5, 100, 480, 3, 200, 260.0);
    }
}
