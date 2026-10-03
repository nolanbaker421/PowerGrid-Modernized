package com.nolanbaker.pgmodernized.ac.compat.cc;

import com.nolanbaker.pgmodernized.ac.source.FeInverterBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** FE inverter control: output voltage and frequency, and live readings of power and buffer. */
public class FeInverterPeripheral implements IPeripheral {
    private final FeInverterBlockEntity inverter;

    public FeInverterPeripheral(FeInverterBlockEntity inverter) {
        this.inverter = inverter;
    }

    /** Line-to-neutral output voltage; snaps to the nearest nameplate value. */
    @LuaFunction(mainThread = true)
    public double setVoltage(double volts) {
        inverter.setVolts(volts);
        return inverter.volts();
    }

    @LuaFunction
    public double getVoltage() {
        return inverter.volts();
    }

    @LuaFunction(mainThread = true)
    public double setFrequency(double hz) {
        inverter.setHertz(hz);
        return inverter.hertz();
    }

    @LuaFunction
    public double getFrequency() {
        return inverter.hertz();
    }

    @LuaFunction
    public double getPower() {
        return inverter.watts();
    }

    @LuaFunction
    public int getStored() {
        return inverter.stored();
    }

    @LuaFunction
    public int getCapacity() {
        return inverter.capacity();
    }

    @LuaFunction
    public boolean isBrownedOut() {
        return inverter.brownedOut();
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_inverter";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof FeInverterPeripheral that && this.inverter == that.inverter;
    }

    @Override
    public int hashCode() {
        return inverter.hashCode();
    }
}
