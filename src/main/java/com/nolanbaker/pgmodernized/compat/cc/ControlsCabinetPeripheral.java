package com.nolanbaker.pgmodernized.compat.cc;

import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.PanelDevice;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.CELLS;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.RAIL;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity.CHANNELS;

/**
 * Controls cabinet as peripheral "powergrid_controls". Slots, channels and cells are numbered
 * from 1. Events: 'input_change' (slot, channel, state), 'estop' (active), 'device' (cell, type, state).
 */
public class ControlsCabinetPeripheral implements IPeripheral, ControlsCabinetBlockEntity.Listener {
    private final ControlsCabinetBlockEntity cabinet;
    private final Set<IComputerAccess> computers = new HashSet<>();

    public ControlsCabinetPeripheral(ControlsCabinetBlockEntity cabinet) {
        this.cabinet = cabinet;
    }

    @Override
    public void attach(@NotNull IComputerAccess computer) {
        synchronized(computers) {
            if(computers.isEmpty())
                cabinet.addListener(this);
            computers.add(computer);
        }
    }

    @Override
    public void detach(@NotNull IComputerAccess computer) {
        synchronized(computers) {
            computers.remove(computer);
            if(computers.isEmpty())
                cabinet.removeListener(this);
        }
    }

    private void queue(String event, Object... args) {
        synchronized(computers) {
            for(var computer : computers)
                computer.queueEvent(event, args);
        }
    }

    @Override
    public void input(int slot, int channel, boolean state) {
        queue("input_change", slot + 1, channel + 1, state);
    }

    @Override
    public void estop(boolean active) {
        queue("estop", active);
    }

    @Override
    public void device(int cell, PanelDevice type, int state) {
        queue("device", cell + 1, type.key(), state);
    }

    @LuaFunction
    public boolean isPowered() {
        return cabinet.isPowered();
    }

    @LuaFunction
    public double getVoltage() {
        return cabinet.volts();
    }

    @LuaFunction
    public boolean isEStopped() {
        return cabinet.isEStopped();
    }

    @LuaFunction
    public Map<Integer, String> getModules() {
        Map<Integer, String> map = new HashMap<>();
        for(int slot = 0; slot < RAIL; ++slot)
            if(cabinet.module(slot) != null)
                map.put(slot + 1, cabinet.module(slot).key());
        return map;
    }

    @LuaFunction
    public Map<Integer, String> getDevices() {
        Map<Integer, String> map = new HashMap<>();
        for(int cell = 0; cell < CELLS; ++cell)
            if(cabinet.device(cell) != null)
                map.put(cell + 1, cabinet.device(cell).key());
        return map;
    }

    @LuaFunction
    public boolean getInput(int slot, int channel) {
        return cabinet.input(slot - 1, channel - 1);
    }

    @LuaFunction
    public Map<Integer, Boolean> getInputs(int slot) {
        Map<Integer, Boolean> map = new HashMap<>();
        for(int ch = 0; ch < CHANNELS; ++ch)
            map.put(ch + 1, cabinet.input(slot - 1, ch));
        return map;
    }

    @LuaFunction(mainThread = true)
    public boolean setOutput(int slot, int channel, boolean on) {
        return cabinet.setOutput(slot - 1, channel - 1, on);
    }

    @LuaFunction
    public boolean getOutput(int slot, int channel) {
        return cabinet.output(slot - 1, channel - 1);
    }

    @LuaFunction(mainThread = true)
    public boolean setRelay(int slot, int channel, boolean closed) {
        return cabinet.setRelay(slot - 1, channel - 1, closed);
    }

    @LuaFunction
    public boolean getRelay(int slot, int channel) {
        return cabinet.relay(slot - 1, channel - 1);
    }

    @LuaFunction(mainThread = true)
    public boolean setDisplay(int cell, int value) {
        return cabinet.setDisplay(cell - 1, value);
    }

    @LuaFunction
    public int getDeviceState(int cell) {
        return cell >= 1 && cell <= CELLS ? cabinet.deviceState(cell - 1) : 0;
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_controls";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof ControlsCabinetPeripheral that && this.cabinet == that.cabinet;
    }

    @Override
    public int hashCode() {
        return cabinet.hashCode();
    }
}
