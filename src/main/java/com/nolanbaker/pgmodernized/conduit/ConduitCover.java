package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.util.StringRepresentable;

/**
 * What is on the front of a conduit box. Open and blank boxes are pure splice points where runs
 * cross and join; only the node plate exposes the twelve cover terminals that ordinary wire lands on.
 */
public enum ConduitCover implements StringRepresentable {
    OPEN("open", 0),
    BLANK("blank", 0),
    NODE("node", 12),
    /** A lamp holder on the plate: cover terminals 0 (line) and 1 (neutral) feed the lamp. */
    LIGHT_OFF("light_off", 2),
    LIGHT_ON("light_on", 2),
    /** A receptacle on the plate: cover terminals 0 (line) and 1 (neutral) are its two poles, and power cords plug into it. */
    RECEPTACLE("receptacle", 2);

    private final String name;
    private final int terminals;

    ConduitCover(String name, int terminals) {
        this.name = name;
        this.terminals = terminals;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The cover plate item that produces this state, or null for an open box. */
    public boolean isPlate() {
        return this != OPEN;
    }

    /** Cover terminals this plate exposes, counted from terminal 0. */
    public int terminals() {
        return terminals;
    }

    public boolean hasTerminals() {
        return terminals > 0;
    }

    public boolean exposes(int terminal) {
        return terminal >= 0 && terminal < terminals;
    }

    public boolean isLight() {
        return this == LIGHT_OFF || this == LIGHT_ON;
    }

    /** A plate whose two terminals are a device on the plate (a lamp or a receptacle) rather than bare terminals. */
    public boolean isDevice() {
        return isLight() || this == RECEPTACLE;
    }

    /** The state a plate item fits: a light socket goes on dark. */
    public ConduitCover itemCover() {
        return this == LIGHT_ON ? LIGHT_OFF : this;
    }
}
