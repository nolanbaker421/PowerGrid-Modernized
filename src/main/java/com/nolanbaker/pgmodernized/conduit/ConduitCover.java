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
    RECEPTACLE("receptacle", 2),
    /** A smoke detector on the plate: cover terminals 0 (+) and 1 (-) are the fire alarm panel's initiating loop. */
    SMOKE("smoke", 2),
    SMOKE_ALARM("smoke_alarm", 2),
    /** A horn strobe on the plate: cover terminals 0 (+) and 1 (-) are the fire alarm panel's notification loop. */
    HORN_OFF("horn_off", 2),
    HORN_ON("horn_on", 2),
    /** A heat detector on the plate, on the initiating loop: trips on fire or lava against the block beside it. */
    HEAT("heat", 2),
    HEAT_ALARM("heat_alarm", 2),
    /** A pull station on the plate, on the initiating loop: pulled by hand, reset with a wrench. */
    PULL("pull", 2),
    PULL_PULLED("pull_pulled", 2);

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

    public boolean isSmoke() {
        return this == SMOKE || this == SMOKE_ALARM;
    }

    public boolean isHorn() {
        return this == HORN_OFF || this == HORN_ON;
    }

    public boolean isHeat() {
        return this == HEAT || this == HEAT_ALARM;
    }

    /** A smoke or heat detector: trips by itself. */
    public boolean isDetector() {
        return isSmoke() || isHeat();
    }

    public boolean isPull() {
        return this == PULL || this == PULL_PULLED;
    }

    /** A detector or a pull station: a contact across the initiating loop, with a supervisory resistor. */
    public boolean isInitiating() {
        return isDetector() || isPull();
    }

    /** A plate whose two terminals are a device on the plate rather than bare terminals. */
    public boolean isDevice() {
        return isLight() || this == RECEPTACLE || isInitiating() || isHorn();
    }

    /** The state a plate item fits: a light socket goes on dark, a detector quiet, a pull station set, a horn strobe off. */
    public ConduitCover itemCover() {
        return switch(this) {
            case LIGHT_ON -> LIGHT_OFF;
            case SMOKE_ALARM -> SMOKE;
            case HEAT_ALARM -> HEAT;
            case PULL_PULLED -> PULL;
            case HORN_ON -> HORN_OFF;
            default -> this;
        };
    }
}
