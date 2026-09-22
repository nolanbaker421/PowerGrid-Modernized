package com.nolanbaker.pgmodernized.compat.cc.meter;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.nolanbaker.pgmodernized.device.meter.LineAmmeterBlockEntity;

public class LineAmmeterPeripheral implements IPeripheral {
    private final LineAmmeterBlockEntity meter;

    public LineAmmeterPeripheral(LineAmmeterBlockEntity meter) {
        this.meter = meter;
    }

    @LuaFunction
    public double getCurrent() {
        return meter.getCurrent();
    }

    @LuaFunction
    public double getPower() {
        return meter.getPower();
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_ammeter";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof LineAmmeterPeripheral that && this.meter == that.meter;
    }

    @Override
    public int hashCode() {
        return meter.hashCode();
    }
}
