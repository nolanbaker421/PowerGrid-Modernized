package com.nolanbaker.pgmodernized.device.breaker;

/**
 * Breaker frame sizes. A frame is the physical breaker; its trip rating is set once it is in the
 * panel, anywhere in the frame's range in the frame's step. Bigger frames take more rows of the
 * column per pole.
 */
public enum BreakerFrame {
    F50("50a", 1, 50, 1, 1),
    F200("200a", 51, 200, 1, 1),
    F400("400a", 205, 400, 5, 2),
    F800("800a", 410, 800, 10, 3);

    private final String id;
    private final int min;
    private final int max;
    private final int step;
    private final int rows;

    BreakerFrame(String id, int min, int max, int step, int rows) {
        this.id = id;
        this.min = min;
        this.max = max;
        this.step = step;
        this.rows = rows;
    }

    /** Item id suffix, "breaker_&lt;id&gt;". */
    public String id() {
        return id;
    }

    public int min() {
        return min;
    }

    /** The frame's own rating: the largest trip setting, and what a panel must accept. */
    public int max() {
        return max;
    }

    public int step() {
        return step;
    }

    /** Rows of the column each pole takes. */
    public int rows() {
        return rows;
    }

    /** Number of distinct settings. */
    public int settings() {
        return (max - min) / step + 1;
    }

    public int settingOf(int rating) {
        return (clamp(rating) - min) / step;
    }

    public int ratingOf(int setting) {
        return min + Math.max(0, Math.min(settings() - 1, setting)) * step;
    }

    /** Snaps a rating into this frame's range and step. */
    public int clamp(int rating) {
        int bounded = Math.max(min, Math.min(max, rating));
        return min + Math.round((bounded - min) / (float) step) * step;
    }

    /** The smallest frame that can be set to that rating; the largest frame for anything above 800 A. */
    public static BreakerFrame forRating(int rating) {
        for(var frame : values()) {
            if(rating <= frame.max)
                return frame;
        }
        return F800;
    }

    public static BreakerFrame byOrdinal(int ordinal) {
        var values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : F50;
    }

    public String label() {
        return min + "-" + max + " A";
    }
}
