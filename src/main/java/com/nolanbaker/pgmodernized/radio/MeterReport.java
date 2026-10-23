package com.nolanbaker.pgmodernized.radio;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One meter antenna's last report: who and where it is, when it spoke, and its CT cabinet's four
 * channels. Watt-hours are cumulative, as a meter's register is.
 */
public record MeterReport(String id, String utility, Vec3 position, long time, int channels,
                          double[] volts, double[] amps, double[] watts, double[] powerFactor, double[] wattHours) {

    public double totalWatts() {
        double total = 0;
        for(double w : watts)
            total += w;
        return total;
    }

    public double totalWattHours() {
        double total = 0;
        for(double wh : wattHours)
            total += wh;
        return total;
    }

    /** As a Lua table: id, utility, x, y, z, time, totals and per-channel tables indexed from 1. */
    public Map<String, Object> toTable(long now) {
        var table = new LinkedHashMap<String, Object>();
        table.put("id", id);
        table.put("utility", utility);
        table.put("x", position.x);
        table.put("y", position.y);
        table.put("z", position.z);
        table.put("time", (double) time);
        table.put("age", (double) Math.max(0, now - time) / 20.0);
        table.put("channels", (double) channels);
        table.put("totalPower", totalWatts());
        table.put("totalEnergy", totalWattHours());
        table.put("voltage", perChannel(volts));
        table.put("current", perChannel(amps));
        table.put("power", perChannel(watts));
        table.put("powerFactor", perChannel(powerFactor));
        table.put("energy", perChannel(wattHours));
        return table;
    }

    private Map<Integer, Double> perChannel(double[] values) {
        var map = new LinkedHashMap<Integer, Double>();
        for(int n = 0; n < values.length; ++n)
            map.put(n + 1, values[n]);
        return map;
    }

    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Utility", utility);
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.y);
        tag.putDouble("Z", position.z);
        tag.putLong("Time", time);
        tag.putInt("Channels", channels);
        putDoubles(tag, "Volts", volts);
        putDoubles(tag, "Amps", amps);
        putDoubles(tag, "Watts", watts);
        putDoubles(tag, "PF", powerFactor);
        putDoubles(tag, "Wh", wattHours);
        return tag;
    }

    public static MeterReport read(CompoundTag tag) {
        int channels = tag.getInt("Channels");
        return new MeterReport(tag.getString("Id"), tag.getString("Utility"),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")), tag.getLong("Time"), channels,
                sized(getDoubles(tag, "Volts"), channels), sized(getDoubles(tag, "Amps"), channels), sized(getDoubles(tag, "Watts"), channels),
                sized(getDoubles(tag, "PF"), channels), sized(getDoubles(tag, "Wh"), channels));
    }

    private static void putDoubles(CompoundTag tag, String key, double[] values) {
        var list = new ListTag();
        for(double v : values)
            list.add(DoubleTag.valueOf(v));
        tag.put(key, list);
    }

    private static double[] getDoubles(CompoundTag tag, String key) {
        var list = tag.getList(key, Tag.TAG_DOUBLE);
        var out = new double[list.size()];
        for(int i = 0; i < out.length; ++i)
            out[i] = list.getDouble(i);
        return out;
    }

    private static double[] sized(double[] values, int n) {
        if(values.length == n)
            return values;
        var out = new double[n];
        System.arraycopy(values, 0, out, 0, Math.min(n, values.length));
        return out;
    }
}
