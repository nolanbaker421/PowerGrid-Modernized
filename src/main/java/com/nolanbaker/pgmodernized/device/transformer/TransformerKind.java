package com.nolanbaker.pgmodernized.device.transformer;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;

/**
 * Winding arrangement. Terminals are the primaries first, then the secondaries; the secondaries
 * include the neutral. Each metered <em>leg</em> is one secondary conductor fed by its own ideal
 * coupling against the neutral.
 * <p>
 * Split-phase: one primary (H1, H2), a centre-tapped secondary (X1, N, X2). Each half is a
 * coupling at half the ratio, the second one reversed about the neutral, so X1 and X2 are equal
 * and opposite and X1 to X2 is the full ratio.
 * <p>
 * Three-phase: a delta primary (H1, H2, H3) and a star secondary with neutral (X1, X2, X3, X0).
 * Leg k couples primary line pair (k, k+1) to secondary phase k, which is the standard delta-star
 * distribution bank: secondary phase voltage = ratio × primary line voltage, shifted 30°.
 */
public enum TransformerKind {
    SPLIT_PHASE("split", "Split-Phase", 2,
            new String[] {"x1", "n", "x2"}, 1, new int[] {0, 2}),
    THREE_PHASE("3ph", "Three-Phase", 3,
            new String[] {"x1", "x2", "x3", "x0"}, 3, new int[] {0, 1, 2});

    private final String id;
    private final String label;
    private final int primaries;
    private final String[] secondaryKeys;
    private final int neutralIndex;
    private final int[] legs;

    TransformerKind(String id, String label, int primaries, String[] secondaryKeys, int neutralIndex, int[] legs) {
        this.id = id;
        this.label = label;
        this.primaries = primaries;
        this.secondaryKeys = secondaryKeys;
        this.neutralIndex = neutralIndex;
        this.legs = legs;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public int primaries() {
        return primaries;
    }

    public int secondaries() {
        return secondaryKeys.length;
    }

    public int pointCount() {
        return primaries + secondaries();
    }

    public int primaryTerminal(int k) {
        return k;
    }

    public int secondaryTerminal(int j) {
        return primaries + j;
    }

    public int neutralTerminal() {
        return secondaryTerminal(neutralIndex);
    }

    /** Secondary indices that carry a coupling, in leg order. */
    public int[] legs() {
        return legs;
    }

    /** The primary terminals (indices among the primaries) a leg's coupling is driven from. */
    public int[] primaryPair(int leg) {
        return this == SPLIT_PHASE ? new int[] {0, 1} : new int[] {leg, (leg + 1) % 3};
    }

    /** Each half of a centre-tapped winding carries half the ratio. */
    public float legRatio(int leg) {
        return this == SPLIT_PHASE ? 0.5f : 1f;
    }

    /** The second half of a split-phase secondary is wound the other way about the neutral. */
    public boolean reversed(int leg) {
        return this == SPLIT_PHASE && leg == 1;
    }

    public String primaryKey(int k) {
        return "transformer.h" + (k + 1);
    }

    public String secondaryKey(int j) {
        return "transformer." + secondaryKeys[j];
    }

    public boolean isNeutral(int j) {
        return j == neutralIndex;
    }

    /** Which leg a secondary index is, or -1 for the neutral. */
    private int legOf(int j) {
        for(int i = 0; i < legs.length; ++i) {
            if(legs[i] == j)
                return i;
        }
        return -1;
    }

    /**
     * A secondary's conductor colour for a winding at {@code volts} line-to-line: the legs black,
     * red, blue on a 120/208/240 V winding and brown, orange, yellow on a 277/480 V one, the
     * neutral white or gray to match (NEC 200.6, 210.5(C)).
     */
    public int secondaryColor(int j, float volts) {
        if(isNeutral(j))
            return ConductorColors.neutral(volts);
        int leg = legOf(j);
        return leg >= 0 ? ConductorColors.hot(leg, volts) : ConductorColors.GRAY;
    }

    public int secondaryRgb(int j, float volts) {
        return ConductorColors.rgb(secondaryColor(j, volts));
    }

    public int secondaryTextRgb(int j, float volts) {
        return ConductorColors.textRgb(secondaryColor(j, volts));
    }

    /**
     * A primary's conductor colour by its winding voltage, the same rule as the secondaries. The
     * NEC assigns no colour above 600 V, so a medium-voltage winding borrows the higher-system set.
     */
    public int primaryColor(int k, float volts) {
        return ConductorColors.hot(k, volts);
    }

    public int primaryRgb(int k, float volts) {
        return ConductorColors.rgb(primaryColor(k, volts));
    }

    public int primaryTextRgb(int k, float volts) {
        return ConductorColors.textRgb(primaryColor(k, volts));
    }
}
