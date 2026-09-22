package com.nolanbaker.pgmodernized.compat.cc.analogio;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.nolanbaker.pgmodernized.device.analogio.AnalogIOBlock;
import com.nolanbaker.pgmodernized.device.analogio.AnalogIOBlockEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public class AnalogIOPeripheral implements IPeripheral {
    private final AnalogIOBlockEntity module;

    public AnalogIOPeripheral(AnalogIOBlockEntity module) {
        this.module = module;
    }

    private static int channel(int lua) throws LuaException {
        if(lua < 1 || lua > AnalogIOBlock.CHANNELS)
            throw new LuaException("Channel must be between 1 and " + AnalogIOBlock.CHANNELS);
        return lua - 1;
    }

    @LuaFunction(mainThread = true)
    public void setOutput(int channel, double volts) throws LuaException {
        module.setOutput(channel(channel), (float) volts);
    }

    @LuaFunction
    public double getOutput(int channel) throws LuaException {
        return module.getOutput(channel(channel));
    }

    @LuaFunction
    public double getOutputCurrent(int channel) throws LuaException {
        return module.getOutputCurrent(channel(channel));
    }

    @LuaFunction
    public double getInput(int channel) throws LuaException {
        return module.getInput(channel(channel));
    }

    @LuaFunction
    public Map<Integer, Double> getInputs() {
        var result = new LinkedHashMap<Integer, Double>();
        for(int i = 0; i < AnalogIOBlock.CHANNELS; ++i)
            result.put(i + 1, (double) module.getInput(i));
        return result;
    }

    @LuaFunction
    public Map<Integer, Double> getOutputs() {
        var result = new LinkedHashMap<Integer, Double>();
        for(int i = 0; i < AnalogIOBlock.CHANNELS; ++i)
            result.put(i + 1, (double) module.getOutput(i));
        return result;
    }

    @LuaFunction
    public double getRange() {
        return AnalogIOBlockEntity.MAX_VOLTAGE;
    }

    @LuaFunction
    public int getChannels() {
        return AnalogIOBlock.CHANNELS;
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_analog_io";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof AnalogIOPeripheral that && this.module == that.module;
    }

    @Override
    public int hashCode() {
        return module.hashCode();
    }
}
