package com.nolanbaker.pgmodernized.conduit;

/**
 * EMT trade sizes. A size has an internal area (NEC Chapter 9 Table 4) that the pulled conductors
 * fill by the book ({@link ConduitFill}), a number of numbered slots that caps the count of wires
 * regardless of area, and a drawn tube thickness. The wires themselves are whatever Power Grid wire
 * items the player pulls, each with its own gauge.
 */
public enum ConduitSize {
    HALF("half", "1/2\"", 4, 0.304, 0.09f, 0xF2F2F2),
    THREE_QUARTER("three_quarter", "3/4\"", 8, 0.533, 0.125f, 0xE8C800),
    ONE("one", "1\"", 12, 0.864, 0.16f, 0xD03030),
    ONE_QUARTER("one_quarter", "1-1/4\"", 12, 1.496, 0.17f, 0x2F6FD6),
    ONE_HALF("one_half", "1-1/2\"", 12, 2.036, 0.19f, 0x3AA048),
    TWO("two", "2\"", 12, 3.356, 0.22f, 0xF08020),
    TWO_HALF("two_half", "2-1/2\"", 12, 5.858, 0.25f, 0x9050C0),
    THREE("three", "3\"", 12, 8.846, 0.28f, 0x7A4A24),
    FOUR("four", "4\"", 12, 14.753, 0.32f, 0x202020);

    private final String id;
    private final String label;
    private final int conductors;
    private final double areaSqIn;
    private final float tubeThickness;
    private final int bandColor;

    ConduitSize(String id, String label, int conductors, double areaSqIn, float tubeThickness, int bandColor) {
        this.id = id;
        this.label = label;
        this.conductors = conductors;
        this.areaSqIn = areaSqIn;
        this.tubeThickness = tubeThickness;
        this.bandColor = bandColor;
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

    /**
     * The colour band painted on this size's tube and item, so sizes tell apart at a glance; the
     * same colour labels the size in tooltips. Mirrored in tools/gen_conduit_assets.py.
     */
    public int bandColor() {
        return bandColor;
    }

    /** The size's label in its band colour. */
    public net.minecraft.network.chat.Component coloredLabel() {
        return org.patryk3211.powergrid.utility.Lang.builder().text(label).color(bandColor).component();
    }

    public static ConduitSize fromOrdinal(int ordinal) {
        var values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : HALF;
    }
}
