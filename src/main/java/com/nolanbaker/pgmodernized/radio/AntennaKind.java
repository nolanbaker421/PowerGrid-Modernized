package com.nolanbaker.pgmodernized.radio;

/** The three antennas a radio can drive, and what each one makes the radio. */
public enum AntennaKind {
    /** Point to point: a narrow beam that must meet another dish's beam. */
    DISH("dish_antenna"),
    /** Point to multipoint: a base station's panel with an aimed, wide beam. */
    SECTOR("sector_antenna"),
    /** The client end: all directions, short reach, roams between sectors. */
    OMNI("omni_antenna");

    private final String id;

    AntennaKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean aimed() {
        return this != OMNI;
    }
}
