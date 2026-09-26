package com.nolanbaker.pgmodernized.device.transformer;

/**
 * The nameplates. Each is one block: a winding kind, a size, and fixed high- and low-side
 * voltages that the taps move by up to ten percent. Three-phase low sides are line-to-line
 * voltages of a star secondary; the phase-to-neutral voltage is that over root three.
 */
public enum TransformerSpec {
    POLE_480V_240V("pole_480v_240v", TransformerKind.SPLIT_PHASE, TransformerSize.POLE_S, 480, 240, 25_000),
    POLE_1KV_240V("pole_1kv_240v", TransformerKind.SPLIT_PHASE, TransformerSize.POLE_M, 1_000, 240, 50_000),
    POLE_10KV_240V("pole_10kv_240v", TransformerKind.SPLIT_PHASE, TransformerSize.POLE_M, 10_000, 240, 100_000),
    POLE_35KV_240V("pole_35kv_240v", TransformerKind.SPLIT_PHASE, TransformerSize.POLE_L, 35_000, 240, 167_000),
    PAD_1KV_240V("pad_1kv_240v", TransformerKind.SPLIT_PHASE, TransformerSize.PAD, 1_000, 240, 100_000),
    PAD_10KV_240V("pad_10kv_240v", TransformerKind.SPLIT_PHASE, TransformerSize.PAD, 10_000, 240, 250_000),
    PAD_1KV_208V("pad_1kv_208v", TransformerKind.THREE_PHASE, TransformerSize.PAD, 1_000, 208, 150_000),
    PAD_10KV_208V("pad_10kv_208v", TransformerKind.THREE_PHASE, TransformerSize.PAD, 10_000, 208, 500_000),
    PAD_10KV_480V("pad_10kv_480v", TransformerKind.THREE_PHASE, TransformerSize.PAD, 10_000, 480, 1_000_000),
    PAD_3500V_480V("pad_3500v_480v", TransformerKind.THREE_PHASE, TransformerSize.PAD, 3_500, 480, 500_000),
    PAD_8KV_480V("pad_8kv_480v", TransformerKind.THREE_PHASE, TransformerSize.PAD, 8_000, 480, 1_000_000),
    SUB_35KV_480V("sub_35kv_480v", TransformerKind.THREE_PHASE, TransformerSize.POWER_S, 35_000, 480, 2_500_000),
    SUB_35KV_10KV("sub_35kv_10kv", TransformerKind.THREE_PHASE, TransformerSize.POWER_S, 35_000, 10_000, 10_000_000),
    SUB_35KV_8KV("sub_35kv_8kv", TransformerKind.THREE_PHASE, TransformerSize.POWER_S, 35_000, 8_000, 10_000_000),
    SUB_100KV_35KV("sub_100kv_35kv", TransformerKind.THREE_PHASE, TransformerSize.POWER_L, 100_000, 35_000, 50_000_000),
    DRY_480V_240V("dry_480v_240v", TransformerKind.SPLIT_PHASE, TransformerSize.DRY, 480, 240, 25_000),
    DRY_480V_208V("dry_480v_208v", TransformerKind.THREE_PHASE, TransformerSize.DRY, 480, 208, 75_000);

    /** Tap steps either side of nominal, each {@link #TAP_STEP} of the winding voltage. */
    public static final int TAP_RANGE = 4;
    public static final float TAP_STEP = 0.025f;

    private final String id;
    private final TransformerKind kind;
    private final TransformerSize size;
    private final float hv;
    private final float lv;
    private final float ratedVa;

    TransformerSpec(String id, TransformerKind kind, TransformerSize size, float hv, float lv, float ratedVa) {
        this.id = id;
        this.kind = kind;
        this.size = size;
        this.hv = hv;
        this.lv = lv;
        this.ratedVa = ratedVa;
    }

    /** Block id: transformer_&lt;id&gt;. */
    public String id() {
        return "transformer_" + id;
    }

    public TransformerKind kind() {
        return kind;
    }

    public TransformerSize size() {
        return size;
    }

    /** Nominal high-side voltage: line-to-line for three-phase. */
    public float hv() {
        return hv;
    }

    /** Nominal low-side voltage: X1 to X2 for split-phase, line-to-line for three-phase. */
    public float lv() {
        return lv;
    }

    public float ratedVa() {
        return ratedVa;
    }

    /** Rated current of the low side, amperes. */
    public float ratedAmps() {
        return kind == TransformerKind.THREE_PHASE ? (float) (ratedVa / (Math.sqrt(3) * lv)) : ratedVa / lv;
    }

    public static float tapFactor(int tap) {
        return 1 + TAP_STEP * Math.max(-TAP_RANGE, Math.min(TAP_RANGE, tap));
    }

    public float hvAt(int tap) {
        return hv * tapFactor(tap);
    }

    public float lvAt(int tap) {
        return lv * tapFactor(tap);
    }

    /**
     * Secondary volts per primary volt for one leg's coupling at those taps: the split-phase
     * secondary is centre-tapped so each half is half the ratio; the three-phase secondary is a
     * star, so each phase is the line-to-line voltage over root three against a delta primary
     * fed line-to-line.
     */
    public float legRatio(int hvTap, int lvTap) {
        float ratio = lvAt(lvTap) / hvAt(hvTap);
        return kind == TransformerKind.THREE_PHASE ? (float) (ratio / Math.sqrt(3)) : ratio * 0.5f;
    }

    public static String volts(float v) {
        if(v < 1000)
            return String.format("%.0f V", v);
        String kv = String.format("%.2f", v / 1000);
        if(kv.contains("."))
            kv = kv.replaceAll("0+$", "").replaceAll("\\.$", "");
        return kv + " kV";
    }

    /** "10 kV / 208 V". */
    public String plate() {
        return volts(hv) + " / " + volts(lv);
    }

    public String displayName() {
        return size.label() + " Transformer (" + plate() + ")";
    }
}
