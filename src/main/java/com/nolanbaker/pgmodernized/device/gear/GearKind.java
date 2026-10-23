package com.nolanbaker.pgmodernized.device.gear;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * What can go in a slot of a switchgear cell, and which conductors it offers on which sides.
 * Slots connect where both sides offer the same conductor: bars join anything beside them, a
 * breaker takes its lines from the slot above and gives its loads to the slot below (turned with
 * a wrench), and a lug block brings the slot above and beside it out to five lugs.
 * <p>
 * Conductors are bits: 0 L1, 1 L2, 2 L3, 3 N, 4 ground. Sides are {@link GearSide} bits before rotation.
 */
public enum GearKind {
    /** Three copper bars carrying L1, L2, L3 to every side. */
    BUS("bus", 0b00111, all()),
    /** One bar carrying the neutral to every side. */
    NEUTRAL_BAR("neutral_bar", 0b01000, all()),
    /** One bar carrying the ground to every side. */
    GROUND_BAR("ground_bar", 0b10000, all()),
    /** Five lugs on the face, fed from above and from either side; nothing below. */
    LUGS("lugs", 0b11111, GearSide.UP.bit() | GearSide.LEFT.bit() | GearSide.RIGHT.bit()),
    /** A 3-pole breaker: lines above, loads below. */
    BREAKER("breaker", 0b00111, GearSide.UP.bit() | GearSide.DOWN.bit()),
    /** Three fuses: lines above, loads below; a blown set is scrap. */
    FUSE("fuse", 0b00111, GearSide.UP.bit() | GearSide.DOWN.bit()),
    /** A 3-pole switch with a handle and a lockout. */
    SWITCH("switch", 0b00111, GearSide.UP.bit() | GearSide.DOWN.bit()),
    /** A 3-pole contactor whose coil is the cell's redstone signal. */
    CONTACTOR("contactor", 0b00111, GearSide.UP.bit() | GearSide.DOWN.bit()),
    /** A combination starter: a breaker, a redstone contactor and an overload relay on one setting. */
    STARTER("starter", 0b00111, GearSide.UP.bit() | GearSide.DOWN.bit()),
    /** A metering unit: L1, L2, L3 and N straight through, with volts, amps and watts per phase. */
    METER("meter", 0b01111, GearSide.UP.bit() | GearSide.DOWN.bit());

    public static final int PHASES = 0b00111, NEUTRAL = 0b01000, GROUND = 0b10000, ALL_CONDUCTORS = 0b11111;

    private final String id;
    private final int conductors;
    private final int sides;

    GearKind(String id, int conductors, int sides) {
        this.id = id;
        this.conductors = conductors;
        this.sides = sides;
    }

    private static int all() {
        return 0b1111;
    }

    public String id() {
        return id;
    }

    public int conductors() {
        return conductors;
    }

    /** Conductor bits offered on {@code side} by a component turned {@code rotation} quarter turns clockwise. */
    public int portMask(GearSide side, int rotation) {
        var local = side.rotated(-rotation);
        return (sides & local.bit()) != 0 ? conductors : 0;
    }

    /** Has three switched poles between its line side and its load side. */
    public boolean switched() {
        return this == BREAKER || this == FUSE || this == SWITCH || this == CONTACTOR || this == STARTER;
    }

    /** Carries the panel's breaker record: a wrench-set rating, a handle, a lockout. */
    public boolean usesBreaker() {
        return this == BREAKER || this == STARTER;
    }

    /** A wrench turns it; bars look the same every way. */
    public boolean rotatable() {
        return !bar();
    }

    public boolean bar() {
        return this == BUS || this == NEUTRAL_BAR || this == GROUND_BAR;
    }

    public Component displayName() {
        return Component.translatable("powergrid.gear." + id);
    }

    @Nullable
    public static GearKind byId(String id) {
        for(var kind : values()) {
            if(kind.id.equals(id))
                return kind;
        }
        return null;
    }
}
