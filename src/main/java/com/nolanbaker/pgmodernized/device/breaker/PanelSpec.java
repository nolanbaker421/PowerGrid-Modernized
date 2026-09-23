package com.nolanbaker.pgmodernized.device.breaker;

/**
 * The load centre sizes. The rating is both the largest breaker the panel accepts and the bus
 * current the main breaker is meant to protect; the slot count is the number of branch spaces,
 * split into two columns like a US load centre (odd numbers down the left, even down the right).
 * <p>
 * The lug count is how many line conductors feed the panel: one for a plain DC or single-phase
 * panel, two for split-phase (L1, L2 and a neutral), three for three-phase (L1, L2, L3 and a
 * neutral). Each row of spaces sits on the next lug down the panel, both columns of a row on the
 * same one, so a breaker that spans adjacent rows in one column spans as many lugs.
 */
public enum PanelSpec {
    A200("breaker_panel_200", 200, 8, 1),
    A400("breaker_panel_400", 400, 12, 1),
    A800("breaker_panel_800", 800, 12, 1),
    SPLIT_200("breaker_panel_200_2p", 200, 12, 2),
    SPLIT_400("breaker_panel_400_2p", 400, 12, 2),
    THREE_400("breaker_panel_400_3p", 400, 12, 3),
    THREE_800("breaker_panel_800_3p", 800, 12, 3);

    private final String id;
    private final int rating;
    private final int slots;
    private final int lugs;

    PanelSpec(String id, int rating, int slots, int lugs) {
        this.id = id;
        this.rating = rating;
        this.slots = slots;
        this.lugs = lugs;
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

    /** Line conductors: 1, 2 (split-phase) or 3 (three-phase). */
    public int lugs() {
        return lugs;
    }

    public boolean accepts(int breakerRating) {
        return breakerRating > 0 && breakerRating <= rating;
    }

    /** A branch breaker may have as many poles as the panel has lugs. */
    public boolean acceptsPoles(int poles) {
        return poles >= 1 && poles <= lugs;
    }

    /** A breaker occupies its own space and the next {@code poles - 1} rows of the same column. */
    public boolean fits(int slot, int poles) {
        return slot >= 0 && slot < slots && slot + 2 * (poles - 1) < slots;
    }

    // ---- terminal indices: lines, then the neutral, then one per branch space ----

    public int lineTerminal(int lug) {
        return lug;
    }

    public int neutralTerminal() {
        return lugs;
    }

    public int branchFirst() {
        return lugs + 1;
    }

    public int branchTerminal(int slot) {
        return branchFirst() + slot;
    }

    /** Which lug feeds a space: rows alternate down the panel, both columns of a row on the same lug. */
    public int leg(int slot) {
        return (slot / 2) % lugs;
    }

    /** Goggle and editor title key. */
    public String titleKey() {
        return switch(lugs) {
            case 2 -> "gui.breaker_panel.title_split";
            case 3 -> "gui.breaker_panel.title_three";
            default -> "gui.breaker_panel.title";
        };
    }
}
