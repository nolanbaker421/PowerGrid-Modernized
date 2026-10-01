package com.nolanbaker.pgmodernized.ac.compat.cc;

import com.nolanbaker.pgmodernized.ac.sync.SynchroscopeBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Synchroscope readings, for a script that trims a machine onto the bus and closes the tie. */
public class SynchroscopePeripheral implements IPeripheral {
    private final SynchroscopeBlockEntity scope;

    public SynchroscopePeripheral(SynchroscopeBlockEntity scope) {
        this.scope = scope;
    }

    @LuaFunction
    public double getBusFrequency() {
        return scope.busFrequency();
    }

    @LuaFunction
    public double getIncomingFrequency() {
        return scope.incomingFrequency();
    }

    @LuaFunction
    public double getBusVoltage() {
        return scope.busVoltage();
    }

    @LuaFunction
    public double getIncomingVoltage() {
        return scope.incomingVoltage();
    }

    /** Hertz the incoming runs faster than the bus. */
    @LuaFunction
    public double getSlip() {
        return scope.slip();
    }

    /** Degrees the incoming leads the bus, -180 to 180. */
    @LuaFunction
    public double getPhaseAngle() {
        return scope.angle();
    }

    @LuaFunction
    public boolean isInSync() {
        return scope.inSync();
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_synchroscope";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof SynchroscopePeripheral that && this.scope == that.scope;
    }

    @Override
    public int hashCode() {
        return scope.hashCode();
    }
}
