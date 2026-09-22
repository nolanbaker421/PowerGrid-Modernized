package com.nolanbaker.pgmodernized.conduit.splice;

import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A device block entity that carries conduit knockouts; everything is answered by its {@link DeviceSpliceHost}. */
public interface IDeviceSpliceHost extends ISpliceHost {
    DeviceSpliceHost deviceHubs();

    @Override
    default SpliceSupport splices() {
        return deviceHubs().splices();
    }

    @Override
    default List<SplicePoint> points() {
        return deviceHubs().points();
    }

    @Override
    default boolean isPoint(int terminal) {
        return deviceHubs().layout().isPoint(terminal);
    }

    @Override
    default int hubCount() {
        return deviceHubs().layout().hubCount();
    }

    @Override
    default Component hubName(int hub) {
        return DeviceHubs.hubName(hub);
    }

    @Override
    default int hubTerminal(int hub) {
        return deviceHubs().layout().hubTerminal(hub);
    }

    @Override
    default int hubAt(int terminal) {
        return deviceHubs().layout().hubAt(terminal);
    }

    @Override
    default int conductorTerminal(int hub, int conductor) {
        return deviceHubs().layout().conductorTerminal(hub, conductor);
    }

    @Override
    default int hubOf(int terminal) {
        return deviceHubs().layout().hubOf(terminal);
    }

    @Override
    default int conductorOf(int terminal) {
        return deviceHubs().layout().conductorOf(terminal);
    }

    @Override
    default @Nullable ConduitRunEntity hubRun(int hub) {
        return deviceHubs().hubRun(hub);
    }
}
