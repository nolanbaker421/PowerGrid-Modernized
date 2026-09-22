package com.nolanbaker.pgmodernized.compat.cc.vfd;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.nolanbaker.pgmodernized.device.vfd.VfdBlockEntity;

public class VfdPeripheral implements IPeripheral {
    private final VfdBlockEntity drive;

    public VfdPeripheral(VfdBlockEntity drive) {
        this.drive = drive;
    }

    @LuaFunction(mainThread = true)
    public void setVoltage(double volts) {
        drive.setVoltage((float) volts);
    }

    @LuaFunction
    public double getVoltage() {
        return drive.getVoltage();
    }

    @LuaFunction(mainThread = true)
    public void setCurrentLimit(double amps) {
        drive.setCurrentLimit((float) amps);
    }

    @LuaFunction
    public double getCurrentLimit() {
        return drive.getCurrentLimit();
    }

    @LuaFunction(mainThread = true)
    public void setEnabled(boolean enabled) {
        drive.setEnabled(enabled);
    }

    @LuaFunction
    public boolean isEnabled() {
        return drive.isEnabled();
    }

    @LuaFunction
    public double getOutputVoltage() {
        return drive.getOutputVoltage();
    }

    @LuaFunction
    public double getOutputCurrent() {
        return drive.getOutputCurrent();
    }

    @LuaFunction
    public double getInputVoltage() {
        return drive.getInputVoltage();
    }

    @LuaFunction
    public double getInputCurrent() {
        return drive.getInputCurrent();
    }

    @LuaFunction
    public double getPower() {
        return drive.getPower();
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_vfd";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof VfdPeripheral that && this.drive == that.drive;
    }

    @Override
    public int hashCode() {
        return drive.hashCode();
    }
}
