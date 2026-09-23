package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.util.StringRepresentable;

/**
 * What is on the front of a conduit box. Open and blank boxes are pure splice points where runs
 * cross and join; only the node plate exposes the twelve cover terminals that ordinary wire lands on.
 */
public enum ConduitCover implements StringRepresentable {
    OPEN("open"),
    BLANK("blank"),
    NODE("node");

    private final String name;

    ConduitCover(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The cover plate item that produces this state, or null for an open box. */
    public boolean isPlate() {
        return this != OPEN;
    }

    public boolean hasTerminals() {
        return this == NODE;
    }
}
