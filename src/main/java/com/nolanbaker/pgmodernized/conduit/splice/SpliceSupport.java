package com.nolanbaker.pgmodernized.conduit.splice;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.List;

/** The splice list of an {@link ISpliceHost}, its persistence, and its wires in the circuit. */
public final class SpliceSupport {
    public static final float SPLICE_RESISTANCE = 0.0005f;

    private final ElectricBlockEntity be;
    private final ISpliceHost host;
    private final List<int[]> splices = new ArrayList<>();

    public <T extends ElectricBlockEntity & ISpliceHost> SpliceSupport(T be) {
        this.be = be;
        this.host = be;
    }

    /** Live list; do not modify. Each entry is {terminalA, terminalB}. */
    public List<int[]> list() {
        return splices;
    }

    /** Adds the splice wires; call from buildCircuit after the terminal count is set. */
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        for(var splice : splices)
            builder.connect(SPLICE_RESISTANCE, builder.terminalNode(splice[0]), builder.terminalNode(splice[1]));
    }

    public boolean terminalUsable(int terminal) {
        if(host.isPoint(terminal))
            return true;
        int hub = host.hubOf(terminal);
        if(hub < 0)
            return false;
        var run = host.hubRun(hub);
        return run != null && run.conductor(host.conductorOf(terminal)) != null;
    }

    /** Adds the splice if absent, removes it if present. Server side. */
    public void toggle(int a, int b) {
        if(a == b || !terminalUsable(a) || !terminalUsable(b))
            return;
        if(!splices.removeIf(s -> (s[0] == a && s[1] == b) || (s[0] == b && s[1] == a)))
            splices.add(new int[] {Math.min(a, b), Math.max(a, b)});
        changed();
    }

    /** Lands every pulled conductor of the run on that hub on the point of the same number. Server side. */
    public void land(int hub) {
        var run = host.hubRun(hub);
        if(run == null)
            return;
        var points = host.points();
        for(int k = 0; k < run.size().conductors() && k < points.size(); ++k) {
            if(run.conductor(k) == null)
                continue;
            int point = points.get(k).terminal();
            int conductor = host.conductorTerminal(hub, k);
            splices.removeIf(s -> s[0] == point || s[1] == point || s[0] == conductor || s[1] == conductor);
            splices.add(new int[] {Math.min(point, conductor), Math.max(point, conductor)});
        }
        changed();
    }

    /** Drops splices whose conductor is gone. Call from the server lazy tick. */
    public void prune() {
        if(splices.removeIf(s -> !terminalUsable(s[0]) || !terminalUsable(s[1])))
            changed();
    }

    private void changed() {
        var behaviour = be.getElectricBehaviour();
        if(behaviour != null)
            behaviour.rebuildCircuit(false);
        be.setChanged();
        be.notifyUpdate();
    }

    public void write(CompoundTag tag) {
        var flat = new int[splices.size() * 2];
        for(int i = 0; i < splices.size(); ++i) {
            flat[i * 2] = splices.get(i)[0];
            flat[i * 2 + 1] = splices.get(i)[1];
        }
        tag.putIntArray("Splices", flat);
    }

    /** @return whether the list changed, in which case the circuit was rebuilt */
    public boolean read(CompoundTag tag) {
        var flat = tag.getIntArray("Splices");
        boolean changed = flat.length != splices.size() * 2;
        var fresh = new ArrayList<int[]>(flat.length / 2);
        for(int i = 0; i + 1 < flat.length; i += 2) {
            fresh.add(new int[] {flat[i], flat[i + 1]});
            if(!changed && (splices.get(i / 2)[0] != flat[i] || splices.get(i / 2)[1] != flat[i + 1]))
                changed = true;
        }
        if(changed) {
            splices.clear();
            splices.addAll(fresh);
            var behaviour = be.getElectricBehaviour();
            if(behaviour != null)
                behaviour.rebuildCircuit(false);
        }
        return changed;
    }

    // ---- shared helpers ----

    public Component terminalName(int terminal) {
        if(host.isPoint(terminal)) {
            for(var point : host.points()) {
                if(point.terminal() == terminal)
                    return point.name();
            }
        }
        int hub = host.hubOf(terminal);
        if(hub >= 0)
            return Lang.builder().add(host.hubName(hub)).text(" ").add(ConductorColors.name(host.conductorOf(terminal))).component();
        return Component.literal("#" + terminal);
    }

    public void addGoggleLines(List<Component> tooltip) {
        for(int h = 0; h < host.hubCount(); ++h) {
            var run = host.hubRun(h);
            if(run == null)
                continue;
            var pulled = run.conductors();
            Lang.builder().add(host.hubName(h)).text(": ")
                    .add(Lang.builder().translate("gui.conduit.pulled", run.size().label(), pulled.size(), run.size().conductors()).style(ChatFormatting.WHITE))
                    .forGoggles(tooltip, 1);
        }
        if(splices.isEmpty())
            Lang.builder().translate("gui.splice.none").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.splice.count", splices.size()).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
    }

    /** The conduit run whose end sits on the given hub terminal of the block entity, or null. Works on both sides. */
    @Nullable
    public static ConduitRunEntity runAt(ElectricBlockEntity be, int hubTerminal) {
        Level level = be.getLevel();
        if(level == null)
            return null;
        BlockPos pos = be.getBlockPos();
        var endpoint = new BlockWireEndpoint(pos, hubTerminal);
        var behaviour = be.getElectricBehaviour();
        if(behaviour != null) {
            var wires = behaviour.getConnections().get(endpoint);
            if(wires != null) {
                for(var wire : wires) {
                    if(wire instanceof ConduitRunEntity run && !run.isRemoved())
                        return run;
                }
            }
        }
        // The client may not have the connection registered yet; look for the entity itself.
        for(var run : level.getEntitiesOfClass(ConduitRunEntity.class, new AABB(pos).inflate(1.5))) {
            if(!run.isRemoved() && run.isConnectedTo(pos, hubTerminal))
                return run;
        }
        return null;
    }
}
