package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Conduit fill by the book (NEC Chapter 9, Table 1): the conductors' total cross-section may take
 * 53% of the raceway's area when there is one of them, 31% with two, and 40% with three or more.
 * A run also has a fixed number of numbered slots, which caps small wire in big pipe.
 */
public final class ConduitFill {
    private ConduitFill() {}

    /** Allowed fraction of the raceway area for that many conductors. */
    public static float limit(int count) {
        return count <= 1 ? 0.53f : count == 2 ? 0.31f : 0.40f;
    }

    public static int percentLimit(int count) {
        return Math.round(limit(count) * 100);
    }

    /** Total conductor area of the pulled wires, square inches. */
    public static double used(Level level, List<ConductorEntity> conductors) {
        double area = 0;
        for(var conductor : conductors)
            area += WireGauge.areaOf(level, conductor.getItem());
        return area;
    }

    /** Whether one more conductor of that area fits beside the given ones. */
    public static boolean fits(ConduitSize size, double usedArea, int count, double addedArea) {
        return usedArea + addedArea <= limit(count + 1) * size.areaSqIn() + 1e-9;
    }

    public static int percent(ConduitSize size, double area) {
        return (int) Math.round(100 * area / size.areaSqIn());
    }

    /** How many conductors of one gauge the size takes, slots included. */
    public static int maxCount(ConduitSize size, WireGauge gauge) {
        int n = 0;
        while(n < size.conductors() && (n + 1) * gauge.areaSqIn() <= limit(n + 1) * size.areaSqIn() + 1e-9)
            ++n;
        return n;
    }
}
