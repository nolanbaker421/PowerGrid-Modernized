package com.nolanbaker.pgmodernized.compat.cc;

import com.nolanbaker.pgmodernized.radio.CollectorBlockEntity;
import com.nolanbaker.pgmodernized.radio.MeterReport;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The metering head end: every meter it hears, with the last readings, and a "meter_report" event when one comes in. */
public class CollectorPeripheral implements IPeripheral, CollectorBlockEntity.Listener {
    private final CollectorBlockEntity collector;
    private final Set<IComputerAccess> computers = new HashSet<>();

    public CollectorPeripheral(CollectorBlockEntity collector) {
        this.collector = collector;
    }

    private long now() {
        return collector.getLevel() == null ? 0 : collector.getLevel().getGameTime();
    }

    @LuaFunction
    public String getUtility() {
        return collector.utility();
    }

    @LuaFunction(mainThread = true)
    public void setUtility(String id) {
        collector.setUtility(id);
    }

    @LuaFunction
    public double getRange() {
        return collector.range();
    }

    @LuaFunction
    public int getMeterCount() {
        return collector.meters().size();
    }

    /** Every meter heard, nearest first, indexed from 1: id, utility, x, y, z, time, age (seconds), totals, and per-channel tables. */
    @LuaFunction
    public Map<Integer, Map<String, Object>> getMeters() {
        var table = new LinkedHashMap<Integer, Map<String, Object>>();
        int i = 1;
        long now = now();
        for(var report : collector.meters())
            table.put(i++, report.toTable(now));
        return table;
    }

    /** One meter by ID, or nil. */
    @LuaFunction
    public @Nullable Map<String, Object> getMeter(String id) {
        var report = collector.meter(id);
        return report == null ? null : report.toTable(now());
    }

    @Override
    public void attach(@NotNull IComputerAccess computer) {
        synchronized(computers) {
            if(computers.isEmpty())
                collector.addListener(this);
            computers.add(computer);
        }
    }

    @Override
    public void detach(@NotNull IComputerAccess computer) {
        synchronized(computers) {
            computers.remove(computer);
            if(computers.isEmpty())
                collector.removeListener(this);
        }
    }

    @Override
    public void onReport(MeterReport report) {
        synchronized(computers) {
            for(var computer : computers)
                computer.queueEvent("meter_report", report.id(), report.totalWatts(), report.totalWattHours());
        }
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_collector";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof CollectorPeripheral that && this.collector == that.collector;
    }

    @Override
    public int hashCode() {
        return collector.hashCode();
    }
}
