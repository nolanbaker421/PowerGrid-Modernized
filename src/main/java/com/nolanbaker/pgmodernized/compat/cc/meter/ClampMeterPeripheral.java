package com.nolanbaker.pgmodernized.compat.cc.meter;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.nolanbaker.pgmodernized.device.meter.ClampMeterBlockEntity;

public class ClampMeterPeripheral implements IPeripheral {
    private final ClampMeterBlockEntity meter;

    public ClampMeterPeripheral(ClampMeterBlockEntity meter) {
        this.meter = meter;
    }

    @LuaFunction
    public double getCurrent() {
        return meter.getCurrent();
    }

    @LuaFunction
    public boolean isClamped() {
        return meter.isClamped();
    }

    @LuaFunction
    public int getWireCount() {
        return meter.getWireCount();
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_clamp_meter";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof ClampMeterPeripheral that && this.meter == that.meter;
    }

    @Override
    public int hashCode() {
        return meter.hashCode();
    }
}
