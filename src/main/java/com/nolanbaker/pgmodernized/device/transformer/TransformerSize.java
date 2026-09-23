package com.nolanbaker.pgmodernized.device.transformer;

/**
 * Physical sizes, after Create: PowerPlantGrid's transformers. The pole cans stand on the ground
 * or hang on a pole; the tanks stand on a skid with their bushings on the lid, one block up; the
 * dry-type is an indoor cabinet wired through knockouts.
 */
public enum TransformerSize {
    /** A small can, under a block tall. */
    POLE_S("Pole", true, false),
    /** A full-height can. */
    POLE_M("Pole", true, false),
    /** A can nearly a block and a half tall. */
    POLE_L("Pole", true, false),
    /** A tank 1.5 blocks wide and 1.75 tall, bushings on the lid. */
    PAD("Pad", false, false),
    /** A tank two blocks wide and tall with radiators, the 65 kV class. */
    POWER_S("Substation", false, false),
    /** The 100 kV class: two and a half blocks wide. */
    POWER_L("Substation", false, false),
    /** An indoor cabinet two blocks tall, wired through knockouts. */
    DRY("Dry-Type", false, true);

    private final String label;
    private final boolean pole;
    private final boolean hubs;

    TransformerSize(String label, boolean pole, boolean hubs) {
        this.label = label;
        this.pole = pole;
        this.hubs = hubs;
    }

    public String label() {
        return label;
    }

    /** A can that can hang on a pole. */
    public boolean isPole() {
        return pole;
    }

    /** Wired through conduit knockouts and a splice editor rather than bushings. */
    public boolean hasHubs() {
        return hubs;
    }
}
