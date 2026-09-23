package com.nolanbaker.pgmodernized.device.transformer;

/** The turns ratios a transformer can be set to, primary : secondary, from a 1:60 step-up to a 60:1 step-down. */
public final class TransformerRatio {
    private static final int[][] PRESETS = {
            {1, 60}, {1, 30}, {1, 20}, {1, 10}, {1, 5}, {1, 4}, {1, 3}, {1, 2},
            {1, 1},
            {2, 1}, {3, 1}, {4, 1}, {5, 1}, {10, 1}, {20, 1}, {30, 1}, {60, 1},
    };
    public static final int COUNT = PRESETS.length;
    /** Index of 1:1. */
    public static final int UNITY = 8;

    private TransformerRatio() {}

    public static int clamp(int index) {
        return Math.max(0, Math.min(COUNT - 1, index));
    }

    /** Secondary volts per primary volt. */
    public static float value(int index) {
        var preset = PRESETS[clamp(index)];
        return (float) preset[1] / preset[0];
    }

    public static int primaryTurns(int index) {
        return PRESETS[clamp(index)][0];
    }

    public static int secondaryTurns(int index) {
        return PRESETS[clamp(index)][1];
    }

    public static String describe(int index) {
        var preset = PRESETS[clamp(index)];
        return preset[0] + " : " + preset[1];
    }
}
