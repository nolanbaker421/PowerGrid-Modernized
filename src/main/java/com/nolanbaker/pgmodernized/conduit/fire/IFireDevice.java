package com.nolanbaker.pgmodernized.conduit.fire;

import org.jetbrains.annotations.Nullable;

/**
 * A block entity carrying fire alarm devices in numbered slots: a conduit box has one, under its
 * cover; a gang box has one per gang. The panel finds them along the conduit, gives each an
 * address, and reads their state.
 */
public interface IFireDevice {
    /** Slots a device could be in; empty slots answer null below. */
    int fireSlots();

    @Nullable
    FireDeviceKind fireKind(int slot);

    /** The loop address, 1 to 159, or 0 before a panel has given it one. */
    int fireAddress(int slot);

    void setFireAddress(int slot, int address);

    /** The room or device name a name tag gave it, or empty. */
    String fireLabel(int slot);

    /** A detector in alarm or a station pulled. */
    boolean fireTripped(int slot);

    /** The volts across a horn strobe's load; zero for anything else. */
    float fireHornVolts(int slot);

    /** Whether either of the device's two points has a splice on it. */
    boolean fireSpliced(int slot);

    /** The device's own terminals: the two points its leads land on. The panel's survey must reach one of them along the wiring. */
    int[] fireTerminals(int slot);

    /** The panel whose loop reached this device, told at every survey; an annunciator mirrors it. Server side. */
    default void setFirePanel(int slot, net.minecraft.core.BlockPos panel, long now) {}
}
