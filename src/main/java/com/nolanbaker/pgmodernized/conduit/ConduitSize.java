package com.nolanbaker.pgmodernized.conduit;

/**
 * Trade sizes. The size sets how many wires can be pulled through a run and how fat the tube is drawn.
 * The wires themselves are whatever Power Grid wire items the player pulls, each with its own gauge.
 */
public enum ConduitSize {
    HALF("half", "1/2\"", 4, 2.5f),
    THREE_QUARTER("three_quarter", "3/4\"", 8, 3f),
    ONE("one", "1\"", 12, 3.5f);

    private final String id;
    private final String label;
    private final int conductors;
    private final float apothemPx;

    ConduitSize(String id, String label, int conductors, float apothemPx) {
        this.id = id;
        this.label = label;
        this.conductors = conductors;
        this.apothemPx = apothemPx;
    }

    /** Suffix used in block, item and texture ids. */
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public int conductors() {
        return conductors;
    }

    /** Half the tube width, in pixels. */
    public float apothemPx() {
        return apothemPx;
    }

    /** Terminal grid columns on an end cap face. */
    public int columns() {
        return this == HALF ? 2 : 4;
    }

    public int rows() {
        return conductors / columns();
    }

    public static ConduitSize fromOrdinal(int ordinal) {
        var values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : HALF;
    }
}
