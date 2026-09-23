package com.nolanbaker.pgmodernized.device.transformer;

/**
 * Where the transformer lives, which decides how it is wired: the cabinets take conduit on
 * knockouts and are spliced inside, the pole can has bushings that ordinary hanging wire lands on.
 */
public enum TransformerMount {
    /** Indoor dry-type cabinet standing against a wall, next to the panel. */
    DRY("dry", "Dry-Type", true),
    /** Outdoor pad-mount box on the ground, fed from below or the sides. */
    PAD("pad", "Pad-Mount", true),
    /** A can hung on a pole, wired with hanging wire on its bushings. */
    POLE("pole", "Pole-Mount", false);

    private final String id;
    private final String label;
    private final boolean hubs;

    TransformerMount(String id, String label, boolean hubs) {
        this.id = id;
        this.label = label;
        this.hubs = hubs;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Wired through conduit knockouts and a splice editor rather than exposed terminals. */
    public boolean hasHubs() {
        return hubs;
    }
}
