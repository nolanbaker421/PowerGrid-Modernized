package com.nolanbaker.pgmodernized.compat.cc;

import com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlockEntity;
import com.nolanbaker.pgmodernized.conduit.fire.FireDevice;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The fire alarm panel on ComputerCraft, over its Cat6 jack: the panel's state, every device
 * with its status and zone, every zone, and the operator's three keys, Silence, Reset and Drill.
 * Events: {@code fire_alarm} (state, alarm, silenced, drill, powered) when any of those change,
 * and {@code fire_device} (address, status, kind, label, zone) when a device's status does. The
 * PLC's FA tags read and write the same panel; the two do not interfere.
 */
public class FireAlarmPeripheral implements IPeripheral, FireAlarmPanelBlockEntity.Listener {
    private final FireAlarmPanelBlockEntity panel;
    private final Set<IComputerAccess> computers = new HashSet<>();

    public FireAlarmPeripheral(FireAlarmPanelBlockEntity panel) {
        this.panel = panel;
    }

    @Override
    public void attach(@NotNull IComputerAccess computer) {
        synchronized(computers) {
            if(computers.isEmpty())
                panel.addListener(this);
            computers.add(computer);
        }
    }

    @Override
    public void detach(@NotNull IComputerAccess computer) {
        synchronized(computers) {
            computers.remove(computer);
            if(computers.isEmpty())
                panel.removeListener(this);
        }
    }

    private void queue(String event, Object... args) {
        synchronized(computers) {
            for(var computer : computers)
                computer.queueEvent(event, args);
        }
    }

    @Override
    public void panelChanged(FireAlarmPanelBlockEntity.State state, boolean alarm, boolean silenced, boolean drill, boolean powered) {
        queue("fire_alarm", state.name().toLowerCase(Locale.ROOT), alarm, silenced, drill, powered);
    }

    @Override
    public void deviceChanged(FireDevice device) {
        queue("fire_device", device.address(), device.status().name().toLowerCase(Locale.ROOT), device.kind().id(), device.label(), panel.zoneOf(device.address()));
    }

    private int zone(int zone) throws LuaException {
        if(zone < 1 || zone > FireAlarmPanelBlockEntity.MAX_ZONES)
            throw new LuaException("Zone must be between 1 and " + FireAlarmPanelBlockEntity.MAX_ZONES);
        return zone;
    }

    /** normal, alarm or trouble. */
    @LuaFunction
    public String getState() {
        return panel.state().name().toLowerCase(Locale.ROOT);
    }

    @LuaFunction
    public boolean isInAlarm() {
        return panel.inAlarm();
    }

    @LuaFunction
    public boolean isPowered() {
        return panel.powered();
    }

    @LuaFunction
    public boolean isSilenced() {
        return panel.silenced();
    }

    @LuaFunction
    public boolean isDrill() {
        return panel.drill();
    }

    /** The panel at a glance: state, alarm, silenced, drill, powered, feedVoltage, loopCurrent, notificationCurrent, loopOpen, notificationOpen, devices. */
    @LuaFunction
    public Map<String, Object> getStatus() {
        return panel.describe();
    }

    /** Every device by loop address: address, kind, label, status, tripped, zone, zoneName, x, y, z. */
    @LuaFunction
    public Map<Integer, Map<String, Object>> getDevices() {
        return panel.describeDevices();
    }

    /** One device by address, or nil. */
    @LuaFunction
    public @Nullable Map<String, Object> getDevice(int address) {
        return panel.describeDevice(address);
    }

    /** Every zone with a name or a device: zone, name, devices, alarm. */
    @LuaFunction
    public Map<Integer, Map<String, Object>> getZones() {
        return panel.describeZones();
    }

    @LuaFunction
    public Map<String, Object> getZone(int zone) throws LuaException {
        return panel.describeZone(zone(zone));
    }

    @LuaFunction
    public boolean isZoneInAlarm(int zone) throws LuaException {
        return panel.zoneAlarm(zone(zone));
    }

    /** Quiets the horns; the alarm stands. */
    @LuaFunction(mainThread = true)
    public void silence() {
        panel.plcSilence();
    }

    /** Clears the alarm; false while a device still holds the loop. */
    @LuaFunction(mainThread = true)
    public boolean reset() {
        return panel.plcReset();
    }

    /** Starts or ends a drill, an alarm with no device; ending it is a reset. */
    @LuaFunction(mainThread = true)
    public void drill(boolean on) {
        panel.plcDrill(on);
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_fire_alarm";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof FireAlarmPeripheral that && this.panel == that.panel;
    }

    @Override
    public int hashCode() {
        return panel.hashCode();
    }
}
