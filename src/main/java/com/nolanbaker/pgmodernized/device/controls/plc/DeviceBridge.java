package com.nolanbaker.pgmodernized.device.controls.plc;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * How a PLC reaches the devices on its cabinet's internal network. The OpenComputers binding
 * supplies one that walks the cabinet's node network and invokes component methods; without a
 * computer mod there are no devices, only the cabinet's own names.
 */
public interface DeviceBridge {
    /** A device on the network: its alias in programs, its component type, its address, and its method names. */
    record Device(String alias, String type, String address, List<String> methods) {}

    List<Device> devices();

    /** Calls a device method; the first returned value, or null for none, an unknown device or a failed call. */
    @Nullable
    Object call(String alias, String method, List<Object> args) throws Exception;
}
