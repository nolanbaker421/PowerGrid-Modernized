package com.nolanbaker.pgmodernized.ac;

/**
 * The reactive side of a machine winding. A winding is modelled as resistance in series with an
 * inductance; the inductance is chosen for a nameplate power factor at the nominal line frequency
 * rather than from Power Grid's DC motor time constant, which at 60 Hz would leave a 360 Ω winding
 * with 1.3 kΩ of reactance and a power factor of 0.26.
 */
public final class Windings {
    /** The line frequency the power factor is quoted at. */
    public static final float NOMINAL_HZ = 60;

    private Windings() {}

    /** Reactance of a winding of resistance {@code r} at power factor {@code pf}: X = R tan(arccos pf). */
    public static float reactance(float r, float pf) {
        return (float) (r * Math.tan(Math.acos(Math.max(0.05, Math.min(1, pf)))));
    }

    /** Inductance giving that reactance at the nominal frequency. */
    public static float inductance(float r, float pf) {
        return (float) (reactance(r, pf) / (2 * Math.PI * NOMINAL_HZ));
    }

    /**
     * The series resistance that makes a winding of fixed reactance {@code x} take {@code watts} of
     * real power at {@code volts}: I²R with I = V / |R + jX|. Past the winding's maximum power point
     * (R = X) the demand cannot be met and that point is returned.
     */
    public static double resistanceFor(double volts, double watts, double x) {
        double a = volts * volts / Math.max(1e-9, watts);
        double discriminant = a * a - 4 * x * x;
        return discriminant < 0 ? x : (a + Math.sqrt(discriminant)) / 2;
    }
}
