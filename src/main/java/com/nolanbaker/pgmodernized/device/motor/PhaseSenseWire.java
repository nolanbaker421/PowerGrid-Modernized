package com.nolanbaker.pgmodernized.device.motor;

import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.solver.IOuterHook;

/**
 * A high-resistance sense branch that times the positive-going zero crossings of the voltage
 * across it, sub-tick by sub-tick, to read the supply frequency. Two of them on two phases give
 * the phase sequence: the second phase's crossing follows the first's by a third of a cycle when
 * the sequence runs first-second-third, and by two thirds when it runs the other way.
 */
public class PhaseSenseWire extends ElectricWire implements IOuterHook {
    /** No crossing for this long (or three periods) and the supply counts as gone. */
    private static final double LOST_AFTER = 2.0;
    private static final double PERIOD_SMOOTHING = 0.3;

    private double clock;
    private double lastRise = -1;
    private double period;
    private double previous;

    public PhaseSenseWire(double resistance, IElectricNode node1, IElectricNode node2) {
        super(resistance, node1, node2);
    }

    @Override
    public void postUpperSolve() {
        if(network == null || !isConverged())
            return;
        double dt = network.getDeltaTime();
        clock += dt;
        double v = potentialDifference();
        if(!Double.isFinite(v))
            v = 0;
        if(previous <= 0 && v > 0) {
            if(lastRise >= 0) {
                double measured = clock - lastRise;
                period = period <= 0 ? measured : period + PERIOD_SMOOTHING * (measured - period);
            }
            lastRise = clock;
        }
        previous = v;
        if(lastRise >= 0 && clock - lastRise > Math.max(LOST_AFTER, 3 * period)) {
            period = 0;
            lastRise = -1;
        }
    }

    /** Hertz, 0 when the voltage is not alternating. */
    public double frequency() {
        return period > 0 ? 1 / period : 0;
    }

    public double period() {
        return period;
    }

    /** Time of the latest positive-going zero crossing on this wire's clock, or -1. */
    public double lastRise() {
        return lastRise;
    }

    /**
     * Phase sequence of two phases: +1 when {@code second} lags {@code first} by a third of a
     * cycle (the sequence runs first, second, third), -1 when by two thirds, 0 when the two are not
     * both alternating at one frequency or the lag is neither.
     */
    public static int sequence(PhaseSenseWire first, PhaseSenseWire second) {
        double period = first.period;
        if(period <= 0 || second.period <= 0 || first.lastRise < 0 || second.lastRise < 0)
            return 0;
        if(Math.abs(second.period - period) > period * 0.2)
            return 0;
        double lag = second.lastRise - first.lastRise;
        double fraction = ((lag % period) + period) % period / period;
        if(fraction > 0.2 && fraction < 0.47)
            return 1;
        if(fraction > 0.53 && fraction < 0.8)
            return -1;
        return 0;
    }
}
