package com.nolanbaker.pgmodernized.compat.cc.motor;

import com.nolanbaker.pgmodernized.device.motor.ThreePhaseMotorBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Read-only view of a three-phase motor from an adjacent modem. */
public class ThreePhaseMotorPeripheral implements IPeripheral {
    private final ThreePhaseMotorBlockEntity motor;

    public ThreePhaseMotorPeripheral(ThreePhaseMotorBlockEntity motor) {
        this.motor = motor;
    }

    @LuaFunction
    public double getFrequency() {
        return motor.frequency();
    }

    @LuaFunction
    public double getVoltage() {
        return motor.phaseVoltage();
    }

    @LuaFunction
    public double getCurrent() {
        return motor.phaseCurrent();
    }

    /** +1 for U-V-W, -1 for the reverse sequence, 0 with no usable three-phase supply. */
    @LuaFunction
    public int getSequence() {
        return motor.sequence();
    }

    @LuaFunction
    public double getSpeed() {
        return motor.generatedRpm();
    }

    @LuaFunction
    public double getSynchronousSpeed() {
        return motor.synchronousSpeed();
    }

    @LuaFunction
    public int getPolePairs() {
        return motor.polePairs();
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_three_phase_motor";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof ThreePhaseMotorPeripheral that && this.motor == that.motor;
    }

    @Override
    public int hashCode() {
        return motor.hashCode();
    }
}
