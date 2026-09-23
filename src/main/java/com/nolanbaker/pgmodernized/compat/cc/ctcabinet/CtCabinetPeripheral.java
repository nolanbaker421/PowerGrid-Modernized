package com.nolanbaker.pgmodernized.compat.cc.ctcabinet;

import com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Power metering per channel, channels 1 to 4. Energy is in watt-hours. */
public class CtCabinetPeripheral implements IPeripheral {
    private final CtCabinetBlockEntity cabinet;

    public CtCabinetPeripheral(CtCabinetBlockEntity cabinet) {
        this.cabinet = cabinet;
    }

    private int channel(int channel) throws LuaException {
        if(channel < 1 || channel > cabinet.channels())
            throw new LuaException("Channel must be between 1 and " + cabinet.channels());
        return channel - 1;
    }

    @LuaFunction
    public int getChannels() {
        return cabinet.channels();
    }

    @LuaFunction
    public double getVoltage(int channel) throws LuaException {
        return cabinet.voltage(channel(channel));
    }

    @LuaFunction
    public double getCurrent(int channel) throws LuaException {
        return cabinet.current(channel(channel));
    }

    @LuaFunction
    public double getPower(int channel) throws LuaException {
        return cabinet.power(channel(channel));
    }

    @LuaFunction
    public double getPowerFactor(int channel) throws LuaException {
        return cabinet.powerFactor(channel(channel));
    }

    @LuaFunction
    public double getEnergy(int channel) throws LuaException {
        return cabinet.energyWh(channel(channel));
    }

    @LuaFunction
    public double getTotalPower() {
        return cabinet.totalPower();
    }

    @LuaFunction
    public double getTotalEnergy() {
        return cabinet.totalEnergyWh();
    }

    /** Every channel at once: table of {voltage, current, power, powerFactor, energy} indexed 1 to 4. */
    @LuaFunction
    public Map<Integer, Map<String, Double>> getReadings() {
        var result = new LinkedHashMap<Integer, Map<String, Double>>();
        for(int n = 0; n < cabinet.channels(); ++n) {
            var channel = new LinkedHashMap<String, Double>();
            channel.put("voltage", (double) cabinet.voltage(n));
            channel.put("current", (double) cabinet.current(n));
            channel.put("power", (double) cabinet.power(n));
            channel.put("powerFactor", (double) cabinet.powerFactor(n));
            channel.put("energy", cabinet.energyWh(n));
            result.put(n + 1, channel);
        }
        return result;
    }

    @LuaFunction(mainThread = true)
    public void resetEnergy() {
        cabinet.resetEnergy();
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_ct_cabinet";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof CtCabinetPeripheral that && this.cabinet == that.cabinet;
    }

    @Override
    public int hashCode() {
        return cabinet.hashCode();
    }
}
