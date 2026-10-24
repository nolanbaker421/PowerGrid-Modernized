package com.nolanbaker.pgmodernized.compat.cc;

import com.nolanbaker.pgmodernized.radio.RadioBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** A radio: its link ID, what its antenna makes it, whether it is up, and who it is joined to. */
public class RadioPeripheral implements IPeripheral {
    private final RadioBlockEntity radio;

    public RadioPeripheral(RadioBlockEntity radio) {
        this.radio = radio;
    }

    @LuaFunction
    public String getLinkId() {
        return radio.linkId();
    }

    @LuaFunction(mainThread = true)
    public void setLinkId(String id) {
        radio.setLinkId(id);
    }

    /** bridge: the networks merge over the link; relay: only modem packets cross. */
    @LuaFunction
    public String getMode() {
        return radio.relay() ? "relay" : "bridge";
    }

    @LuaFunction(mainThread = true)
    public void setMode(String mode) {
        radio.setRelay(mode.trim().equalsIgnoreCase("relay"));
    }

    /** dish_antenna, sector_antenna, omni_antenna, or an empty string with no antenna on the coax. */
    @LuaFunction
    public String getRole() {
        return radio.role() == null ? "" : radio.role().id();
    }

    @LuaFunction
    public boolean isUp() {
        return radio.up();
    }

    @LuaFunction
    public boolean isLinked() {
        return !radio.peers().isEmpty();
    }

    @LuaFunction
    public double getVoltage() {
        return radio.volts();
    }

    /** Coax length (blocks) to the antenna and the reach that leaves. */
    @LuaFunction
    public Map<String, Object> getAntenna() {
        var table = new LinkedHashMap<String, Object>();
        var antenna = radio.antenna();
        table.put("kind", getRole());
        table.put("coax", radio.coaxLength());
        table.put("range", radio.range());
        if(antenna != null) {
            table.put("azimuth", antenna.azimuth());
            table.put("elevation", antenna.elevation());
            table.put("beamwidth", antenna.beamwidth());
        }
        return table;
    }

    /** Every joined radio: {id, x, y, z, distance, offAxis}, indexed from 1. */
    @LuaFunction
    public Map<Integer, Map<String, Object>> getPeers() {
        var table = new LinkedHashMap<Integer, Map<String, Object>>();
        int i = 1;
        for(var peer : radio.peers()) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("id", peer.linkId());
            entry.put("x", (double) peer.pos().getX());
            entry.put("y", (double) peer.pos().getY());
            entry.put("z", (double) peer.pos().getZ());
            entry.put("distance", peer.distance());
            entry.put("offAxis", peer.offAxis());
            table.put(i++, entry);
        }
        return table;
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_radio";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof RadioPeripheral that && this.radio == that.radio;
    }

    @Override
    public int hashCode() {
        return radio.hashCode();
    }
}
