package com.nolanbaker.pgmodernized.util;

import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;

/**
 * Meter readings on the AC build of Power Grid.
 *
 * The solver steps an alternating island many times inside one world tick, and a block entity
 * that reads a node voltage or a wire current sees a single sample: whichever point of the
 * waveform the tick happened to end on. Every meter here therefore reads the RMS the wire
 * accumulated over the tick, which is what a real instrument displays.
 *
 * One tick is 50 ms, and at the frequencies Create's machinery reaches (a few hertz) that is a
 * fraction of a cycle, so even the tick's RMS wanders with where the tick fell in the cycle. A
 * {@link Filter} smooths the mean squares over half a second. Direct current is untouched: a
 * reading whose mean is its magnitude is steady, and a steady reading is taken as it is, so on a
 * DC circuit every figure is the same number the main build reports, on the same tick.
 */
public final class AcReadings {
    /** The mean must reach this fraction of the RMS before a reading carries the mean's sign. */
    public static final double DC_FRACTION = 0.3;
    /** The mean at or above this fraction of the RMS means the current is steady, not alternating. */
    public static final double STEADY_FRACTION = 0.95;
    /** Smoothing time constant for alternating readings, seconds: two cycles at 4 Hz. */
    public static final double TAU = 0.5;
    /** Smoothing time constant for the means that decide a reading's sign, seconds. */
    public static final double TAU_SIGN = 1.0;
    private static final double TICK = 0.05;

    private AcReadings() {}

    /** Per-meter state: smoothed mean squares of one voltage branch and one current branch. */
    public static final class Filter {
        private double meanSquareVolts, meanSquareVoltsAmps, meanVoltsAmps;
        private double meanSquareAmps, meanAmps, watts;
        private boolean primed;

        /** One wire supplies both the voltage across it and the current through it. */
        public void sample(AbstractElectricWire wire) {
            sample(wire, wire, Double.NaN);
        }

        /**
         * @param voltageWire branch whose RMS voltage is read; its mean current gives the voltage its sign
         * @param currentWire branch whose RMS current is read
         * @param realPower   mean real power over the tick from a wattmeter, or NaN when not metered
         */
        public void sample(AbstractElectricWire voltageWire, AbstractElectricWire currentWire, double realPower) {
            double rmsV = finite(voltageWire.rmsVoltage());
            double rmsVI = finite(voltageWire.rmsCurrent());
            double meanVI = finite(voltageWire.meanCurrent());
            double rmsI = finite(currentWire.rmsCurrent());
            double meanI = finite(currentWire.meanCurrent());
            // A steady current has a mean equal to its RMS (exactly so when the network is not
            // sub-stepping); take it as is. Only an alternating one is smoothed. One tick is a
            // fraction of a cycle at Create's frequencies, and a slice near the crest also has a
            // mean close to its RMS, so the smoothed figures must agree too: on DC they do from
            // the first tick, on AC the smoothed mean sits near zero and never does.
            boolean tickSteady = Math.abs(meanI) >= STEADY_FRACTION * rmsI && Math.abs(meanVI) >= STEADY_FRACTION * rmsVI;
            boolean smoothedSteady = Math.abs(meanAmps) >= STEADY_FRACTION * Math.sqrt(meanSquareAmps)
                    && Math.abs(meanVoltsAmps) >= STEADY_FRACTION * Math.sqrt(meanSquareVoltsAmps);
            boolean steady = !primed || (tickSteady && smoothedSteady);
            double alpha = steady ? 1 : TICK / (TAU + TICK);
            // The means only decide signs and steadiness, so they can settle more slowly; at 1 Hz
            // a tick's mean swings to most of the crest and needs the longer filter to sit near zero.
            double alphaMean = steady ? 1 : TICK / (TAU_SIGN + TICK);
            meanSquareVolts += alpha * (rmsV * rmsV - meanSquareVolts);
            meanSquareVoltsAmps += alpha * (rmsVI * rmsVI - meanSquareVoltsAmps);
            meanVoltsAmps += alphaMean * (meanVI - meanVoltsAmps);
            meanSquareAmps += alpha * (rmsI * rmsI - meanSquareAmps);
            meanAmps += alphaMean * (meanI - meanAmps);
            if(!Double.isNaN(realPower))
                watts += alpha * (finite(realPower) - watts);
            primed = true;
        }

        public void reset() {
            meanSquareVolts = meanSquareVoltsAmps = meanVoltsAmps = meanSquareAmps = meanAmps = watts = 0;
            primed = false;
        }

        public double rmsVoltage() {
            return Math.sqrt(meanSquareVolts);
        }

        public double rmsCurrent() {
            return Math.sqrt(meanSquareAmps);
        }

        /** Mean current: the direct component. */
        public double meanCurrent() {
            return meanAmps;
        }

        /** RMS current, signed when the current is mostly one-way, positive when it alternates. */
        public double signedRmsCurrent() {
            return sign(rmsCurrent(), meanAmps, rmsCurrent());
        }

        /** RMS voltage, signed from the voltage branch's mean current, which for a resistor is the sign of its voltage. */
        public double signedRmsVoltage() {
            return sign(rmsVoltage(), meanVoltsAmps, Math.sqrt(meanSquareVoltsAmps));
        }

        /** Real power, watts, when sampled with a wattmeter figure. */
        public double realPower() {
            return watts;
        }

        /** Real over apparent power in [-1, 1]; 1 when unloaded. */
        public double powerFactor() {
            double apparent = rmsVoltage() * rmsCurrent();
            if(apparent <= 1e-6)
                return 1;
            return Math.max(-1, Math.min(1, watts / apparent));
        }
    }

    /**
     * Smooths a scalar reading sampled every {@code intervalSeconds} from an instantaneous value
     * and an RMS magnitude, passing steady values through and signing the result like a
     * {@link Filter} does.
     */
    public static final class Smoother {
        private final double alpha;
        private double magnitude, mean;
        private boolean primed;

        public Smoother(double intervalSeconds) {
            alpha = intervalSeconds / (TAU + intervalSeconds);
        }

        public double update(double instantaneous, double rms) {
            instantaneous = finite(instantaneous);
            rms = finite(rms);
            boolean tickSteady = steady(instantaneous, rms);
            boolean smoothedSteady = Math.abs(mean) >= STEADY_FRACTION * magnitude;
            boolean steady = !primed || (tickSteady && smoothedSteady);
            magnitude += (steady ? 1 : alpha) * (rms - magnitude);
            mean += (steady ? 1 : alpha * TAU / TAU_SIGN) * (instantaneous - mean);
            primed = true;
            return sign(magnitude, mean, magnitude);
        }
    }

    /** Signs a magnitude from a mean: only when the mean is a real share of the RMS. */
    public static double sign(double magnitude, double mean, double rms) {
        return Math.abs(mean) > DC_FRACTION * rms ? Math.copySign(magnitude, mean) : magnitude;
    }

    /** A steady reading: its instantaneous sample and its RMS agree, which on DC they do exactly. */
    public static boolean steady(double instantaneous, double rms) {
        return Math.abs(Math.abs(instantaneous) - rms) <= rms * (1 - STEADY_FRACTION);
    }

    public static double finite(double value) {
        return Double.isFinite(value) ? value : 0;
    }
}
