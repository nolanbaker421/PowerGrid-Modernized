package com.nolanbaker.pgmodernized.conduit;

/**
 * EMT trade sizes. A size has an internal area (NEC Chapter 9 Table 4) that the pulled conductors
 * fill by the book ({@link ConduitFill}), a number of numbered slots that caps the count of wires
 * regardless of area, and a drawn tube thickness. The wires themselves are whatever Power Grid wire
 * items the player pulls, each with its own gauge.
 */
public enum ConduitSize {
    HALF("half", "1/2\"", 4, 0.304, 0.09f),
    THREE_QUARTER("three_quarter", "3/4\"", 8, 0.533, 0.125f),
    ONE("one", "1\"", 12, 0.864, 0.16f),
    ONE_QUARTER("one_quarter", "1-1/4\"", 12, 1.496, 0.17f),
    ONE_HALF("one_half", "1-1/2\"", 12, 2.036, 0.19f),
    TWO("two", "2\"", 12, 3.356, 0.22f),
    TWO_HALF("two_half", "2-1/2\"", 12, 5.858, 0.25f),
    THREE("three", "3\"", 12, 8.846, 0.28f),
    FOUR("four", "4\"", 12, 14.753, 0.32f);

    private final String id;
    private final String label;
    private final int conductors;
    private final double areaSqIn;
    private final float tubeThickness;

    ConduitSize(String id, String label, int conductors, double areaSqIn, float tubeThickness) {
        this.id = id;
        this.label = label;
        this.conductors = conductors;
        this.areaSqIn = areaSqIn;
        this.tubeThickness = tubeThickness;
    }

    /** Suffix used in block, item and texture ids. */
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Numbered slots: the most wires a run can carry whatever their size. */
    public int conductors() {
        return conductors;
    }

    /** Internal cross-section, square inches. */
    public double areaSqIn() {
        return areaSqIn;
    }

    /** Drawn tube thickness, blocks; mirrored in wire_types/conduit_&lt;id&gt;.json. */
    public float tubeThickness() {
        return tubeThickness;
    }

    public static ConduitSize fromOrdinal(int ordinal) {
        var values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : HALF;
    }
}
