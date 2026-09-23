package com.nolanbaker.pgmodernized.compat.cc.drive;

import com.nolanbaker.pgmodernized.device.drive.ThreePhaseDriveBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Three-phase drive control: frequency, volts per hertz, ramp, direction, and live readings. */
public class ThreePhaseDrivePeripheral implements IPeripheral {
    private final ThreePhaseDriveBlockEntity drive;

    public ThreePhaseDrivePeripheral(ThreePhaseDriveBlockEntity drive) {
        this.drive = drive;
    }

    /** Commanded frequency in hertz; a negative value hands control back to the value box. */
    @LuaFunction(mainThread = true)
    public void setFrequency(double hz) {
        drive.setFrequency((float) hz);
    }

    @LuaFunction
    public double getFrequency() {
        return drive.frequencySetpoint();
    }

    @LuaFunction
    public double getOutputFrequency() {
        return drive.outputFrequency();
    }

    @LuaFunction(mainThread = true)
    public void setRatedVoltage(double volts) {
        drive.setRatedVoltage((float) volts);
    }

    @LuaFunction
    public double getRatedVoltage() {
        return drive.ratedVoltage();
    }

    @LuaFunction(mainThread = true)
    public void setRatedFrequency(double hz) {
        drive.setRatedFrequency((float) hz);
    }

    @LuaFunction
    public double getRatedFrequency() {
        return drive.ratedFrequency();
    }

    @LuaFunction(mainThread = true)
    public void setRampRate(double hzPerSecond) {
        drive.setRampRate((float) hzPerSecond);
    }

    @LuaFunction
    public double getRampRate() {
        return drive.rampRate();
    }

    @LuaFunction(mainThread = true)
    public void setEnabled(boolean enabled) {
        drive.setEnabled(enabled);
    }

    @LuaFunction
    public boolean isEnabled() {
        return drive.isEnabled();
    }

    @LuaFunction(mainThread = true)
    public void setReversed(boolean reversed) {
        drive.setReversed(reversed);
    }

    @LuaFunction
    public boolean isReversed() {
        return drive.isReversed();
    }

    @LuaFunction
    public double getOutputVoltage() {
        return drive.outputVoltage();
    }

    @LuaFunction
    public double getOutputCurrent() {
        return drive.outputCurrent();
    }

    @LuaFunction
    public double getInputVoltage() {
        return drive.inputVoltage();
    }

    @LuaFunction
    public double getInputCurrent() {
        return drive.inputCurrent();
    }

    @LuaFunction
    public double getPower() {
        return drive.power();
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_three_phase_drive";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof ThreePhaseDrivePeripheral that && this.drive == that.drive;
    }

    @Override
    public int hashCode() {
        return drive.hashCode();
    }
}
