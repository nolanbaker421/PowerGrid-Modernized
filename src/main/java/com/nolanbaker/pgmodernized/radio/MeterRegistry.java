package com.nolanbaker.pgmodernized.radio;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The head end's store: the last report of every meter antenna in a level, kept with the world.
 * A meter in an unloaded chunk cannot speak, so what the collector shows is the last thing it
 * said and how long ago; that is how the real thing works too.
 */
public class MeterRegistry extends SavedData {
    private static final String NAME = "pgm_meters";
    private final Map<String, MeterReport> reports = new LinkedHashMap<>();

    public static MeterRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(MeterRegistry::new, MeterRegistry::load), NAME);
    }

    private static MeterRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        var registry = new MeterRegistry();
        var list = tag.getList("Meters", Tag.TAG_COMPOUND);
        for(int i = 0; i < list.size(); ++i) {
            var report = MeterReport.read(list.getCompound(i));
            registry.reports.put(report.id(), report);
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for(var report : reports.values())
            list.add(report.write());
        tag.put("Meters", list);
        return tag;
    }

    public void put(MeterReport report) {
        reports.put(report.id(), report);
        setDirty();
    }

    public void forget(String id) {
        if(reports.remove(id) != null)
            setDirty();
    }

    @Nullable
    public MeterReport get(String id) {
        return reports.get(id);
    }

    /** Every meter of a utility whose last reported position lies within {@code range} of {@code from}, nearest first. */
    public List<MeterReport> within(String utility, Vec3 from, double range) {
        var list = new ArrayList<MeterReport>();
        for(var report : reports.values()) {
            if(!report.utility().equals(utility))
                continue;
            if(report.position().distanceTo(from) <= range)
                list.add(report);
        }
        list.sort((a, b) -> Double.compare(a.position().distanceTo(from), b.position().distanceTo(from)));
        return list;
    }
}
