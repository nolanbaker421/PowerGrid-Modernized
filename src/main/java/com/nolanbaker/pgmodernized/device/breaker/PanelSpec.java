package com.nolanbaker.pgmodernized.device.breaker;

/**
 * The three load centre sizes. The rating is both the largest breaker the panel accepts and the
 * bus current the main breaker is meant to protect; the slot count is the number of branch spaces,
 * split into two columns like a US load centre (odd numbers down the left, even down the right).
 */
public enum PanelSpec {
    A200("breaker_panel_200", 200, 8),
    A400("breaker_panel_400", 400, 12),
    A800("breaker_panel_800", 800, 12);

    private final String id;
    private final int rating;
    private final int slots;

    PanelSpec(String id, int rating, int slots) {
        this.id = id;
        this.rating = rating;
        this.slots = slots;
    }

    /** Block id inside this mod's namespace. */
    public String id() {
        return id;
    }

    /** Rated bus current in amperes; also the largest breaker that fits any space. */
    public int rating() {
        return rating;
    }

    /** Number of branch spaces. */
    public int slots() {
        return slots;
    }

    /** Rows per column. */
    public int rows() {
        return slots / 2;
    }

    public boolean accepts(int breakerRating) {
        return breakerRating > 0 && breakerRating <= rating;
    }
}
