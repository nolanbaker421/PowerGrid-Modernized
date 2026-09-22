package com.nolanbaker.pgmodernized.conduit.splice;

import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a device block entity needs to be a splice host, so the device keeps its own class
 * hierarchy and only delegates through {@link IDeviceSpliceHost}. Points are the device's own
 * terminals, named and coloured as the block declares them.
 */
public final class DeviceSpliceHost {
    private final ElectricBlockEntity be;
    private final DeviceHubs.Layout layout;
    private final SpliceSupport splices;
    private List<SplicePoint> points;

    public <T extends ElectricBlockEntity & ISpliceHost> DeviceSpliceHost(T be, DeviceHubs.Layout layout) {
        this.be = be;
        this.layout = layout;
        this.splices = new SpliceSupport(be);
    }

    public DeviceHubs.Layout layout() {
        return layout;
    }

    public SpliceSupport splices() {
        return splices;
    }

    public List<SplicePoint> points() {
        if(points == null) {
            var list = new ArrayList<SplicePoint>();
            var state = be.getBlockState();
            if(state.getBlock() instanceof IElectric electric) {
                for(int terminal : layout.points()) {
                    var placement = electric.terminal(state, terminal);
                    Component name = placement instanceof IDecoratedTerminal decorated && decorated.getName() != null
                            ? decorated.getName() : Component.literal("#" + terminal);
                    int rgb = placement instanceof IDecoratedTerminal decorated ? decorated.getColor() : 0xAAAAAA;
                    list.add(new SplicePoint(terminal, name, rgb));
                }
            }
            points = list;
        }
        return points;
    }

    @Nullable
    public ConduitRunEntity hubRun(int hub) {
        return hub < 0 || hub >= layout.hubCount() ? null : SpliceSupport.runAt(be, layout.hubTerminal(hub));
    }

    // ---- hooks the device calls from its own overrides ----

    /** Call from buildCircuit before the device's own wiring; sets the terminal count. */
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(layout.terminalCount());
        splices.buildCircuit(builder);
    }

    /** Call from the server lazy tick. */
    public void lazyTick() {
        var level = be.getLevel();
        if(level != null && !level.isClientSide)
            splices.prune();
    }

    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        splices.write(tag);
    }

    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        splices.read(tag);
    }

    public void addGoggleLines(List<Component> tooltip) {
        splices.addGoggleLines(tooltip);
    }
}
