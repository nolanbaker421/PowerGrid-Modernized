package com.nolanbaker.pgmodernized.device.pole;

/**
 * The voltage classes of fused cutouts, the drop-out fuse holders on a pole or crossarm. The
 * class is the holder's: how much insulator stands between the line and the load lug, and so how
 * much voltage the open gap can hold off. Any fuse link fits any class; the link's amps are its
 * own. A holder below the line's voltage flashes over when its gap opens, and the fault stays on.
 */
public enum CutoutClass {
    /** For 480 V and 1 kV lines: a short holder. */
    KV1("1kv", "1 kV Cutout", 1_000, 2_000, 10, 0xB8B4AC),
    /** The distribution class: 8 kV and 10 kV lines. */
    KV15("15kv", "15 kV Cutout", 15_000, 20_000, 12, 0x8A6A4A),
    /** The 35 kV class. */
    KV38("38kv", "38 kV Cutout", 38_000, 50_000, 14, 0x6E7E8E);

    private final String id;
    private final String label;
    private final int systemVolts;
    private final int withstandVolts;
    private final int top;
    private final int porcelain;

    CutoutClass(String id, String label, int systemVolts, int withstandVolts, int top, int porcelain) {
        this.id = id;
        this.label = label;
        this.systemVolts = systemVolts;
        this.withstandVolts = withstandVolts;
        this.top = top;
        this.porcelain = porcelain;
    }

    public String id() {
        return id;
    }

    /** The item's English name; the generators and lang file mirror it. */
    public String label() {
        return label;
    }

    /** The line voltage the class is made for. */
    public int systemVolts() {
        return systemVolts;
    }

    /** The most the open gap holds off before it arcs across. */
    public int withstandVolts() {
        return withstandVolts;
    }

    /** The top of the insulator, in pixels of the block; the line lug sits on it. */
    public int top() {
        return top;
    }

    /** The insulator's colour: porcelain grey, brown, or the blue-grey of polymer. */
    public int porcelain() {
        return porcelain;
    }

    public static CutoutClass byId(String id) {
        for(var c : values())
            if(c.id.equals(id))
                return c;
        return KV15;
    }
}
