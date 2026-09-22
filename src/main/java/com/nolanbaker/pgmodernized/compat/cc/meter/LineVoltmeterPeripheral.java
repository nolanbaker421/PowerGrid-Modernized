package com.nolanbaker.pgmodernized.compat.cc.meter;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.nolanbaker.pgmodernized.device.meter.LineVoltmeterBlockEntity;

public class LineVoltmeterPeripheral implements IPeripheral {
    private final LineVoltmeterBlockEntity meter;

    public LineVoltmeterPeripheral(LineVoltmeterBlockEntity meter) {
        this.meter = meter;
    }

    @LuaFunction
    public double getVoltage() {
        return meter.getVoltage();
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_voltmeter";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof LineVoltmeterPeripheral that && this.meter == that.meter;
    }

    @Override
    public int hashCode() {
        return meter.hashCode();
    }
}
