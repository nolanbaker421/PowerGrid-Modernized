package com.nolanbaker.pgmodernized.radio;

import net.minecraft.world.phys.Vec3;

/**
 * The geometry of radio links, kept free of Minecraft classes so it can be tested on its own.
 * Azimuth is a compass bearing in degrees: 0 north (-z), 90 east (+x), 180 south, 270 west.
 * Elevation is degrees above the horizon, negative below it.
 */
public final class RadioMath {
    private RadioMath() {}

    /** Unit vector an antenna aimed at that azimuth and elevation points along. */
    public static Vec3 aim(double azimuthDeg, double elevationDeg) {
        double a = Math.toRadians(azimuthDeg), e = Math.toRadians(elevationDeg);
        return new Vec3(Math.sin(a) * Math.cos(e), Math.sin(e), -Math.cos(a) * Math.cos(e));
    }

    /** Compass bearing from {@code from} to {@code to}, 0 to 360. */
    public static double bearing(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        if(dx == 0 && dz == 0)
            return 0;
        return normalize(Math.toDegrees(Math.atan2(dx, -dz)));
    }

    /** Elevation from {@code from} to {@code to}, -90 to 90. */
    public static double elevation(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return Math.toDegrees(Math.atan2(dy, horizontal));
    }

    /** Degrees between where an antenna points and the line to a target, 0 to 180. */
    public static double offAxis(Vec3 from, double azimuthDeg, double elevationDeg, Vec3 to) {
        var line = to.subtract(from);
        if(line.lengthSqr() < 1e-9)
            return 0;
        double cos = aim(azimuthDeg, elevationDeg).dot(line.normalize());
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    /** Horizontal angle between an azimuth and the bearing to a target, 0 to 180: a sector's test. */
    public static double offBearing(Vec3 from, double azimuthDeg, Vec3 to) {
        return Math.abs(difference(bearing(from, to), azimuthDeg));
    }

    /** Signed difference of two bearings, -180 to 180. */
    public static double difference(double a, double b) {
        double d = normalize(a - b);
        return d > 180 ? d - 360 : d;
    }

    public static double normalize(double degrees) {
        double d = degrees % 360;
        return d < 0 ? d + 360 : d;
    }

    /**
     * What a coax run leaves of an antenna's range, 1 up to {@code fullLength} blocks of coax and
     * falling in a straight line to a half at {@code maxLength}; beyond that it keeps falling.
     */
    public static double coaxFactor(double length, double fullLength, double maxLength) {
        if(length <= fullLength)
            return 1;
        if(maxLength <= fullLength)
            return 0.5;
        return Math.max(0, 1 - 0.5 * (length - fullLength) / (maxLength - fullLength));
    }
}
