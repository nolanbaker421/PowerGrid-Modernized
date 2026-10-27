package com.nolanbaker.pgmodernized.conduit.splice;

import com.nolanbaker.pgmodernized.util.IElectricDelegate;
import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.ConduitFill;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;

import java.util.ArrayList;
import java.util.List;

/** The splice list of an {@link ISpliceHost}, its persistence, and its wires in the circuit. */
public final class SpliceSupport {
    public static final float SPLICE_RESISTANCE = 0.0005f;

    private final SmartBlockEntity be;
    private final ISpliceHost host;
    private final List<int[]> splices = new ArrayList<>();
    /** Prune calls skipped after loading: a run's entities arrive after the block that holds them. */
    private static final int PRUNE_GRACE = 6;
    private int pruneCalls;

    public <T extends SmartBlockEntity & ISpliceHost> SpliceSupport(T be) {
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

    /**
     * The run on that hub changed: lands each pulled conductor that has no splice yet on the point of
     * the same number, if that point is free, and leaves every splice the player made alone. Server side.
     */
    public void landNew(int hub) {
        var run = host.hubRun(hub);
        if(run == null)
            return;
        var points = host.points();
        boolean changed = false;
        for(int k = 0; k < run.capacity() && k < points.size(); ++k) {
            if(run.conductor(k) == null)
                continue;
            int point = points.get(k).terminal();
            int conductor = host.conductorTerminal(hub, k);
            boolean conductorSpliced = splices.stream().anyMatch(s -> s[0] == conductor || s[1] == conductor);
            boolean pointUsed = splices.stream().anyMatch(s -> s[0] == point || s[1] == point);
            if(conductorSpliced || pointUsed)
                continue;
            splices.add(new int[] {Math.min(point, conductor), Math.max(point, conductor)});
            changed = true;
        }
        if(changed)
            changed();
    }

    /** Lands every pulled conductor of the run on that hub on the point of the same number. Server side. */
    public void land(int hub) {
        var run = host.hubRun(hub);
        if(run == null)
            return;
        var points = host.points();
        for(int k = 0; k < run.capacity() && k < points.size(); ++k) {
            if(run.conductor(k) == null)
                continue;
            int point = points.get(k).terminal();
            int conductor = host.conductorTerminal(hub, k);
            splices.removeIf(s -> s[0] == point || s[1] == point || s[0] == conductor || s[1] == conductor);
            splices.add(new int[] {Math.min(point, conductor), Math.max(point, conductor)});
        }
        changed();
    }

    /**
     * Drops splices whose conductor is gone. Call from the server lazy tick. A conductor counts
     * as gone only when its run is here and no longer carries it: a run that is not found may be
     * in a chunk that has not loaded, and the first calls after loading are skipped altogether
     * because entities arrive after the blocks of their chunk.
     */
    public void prune() {
        if(pruneCalls < PRUNE_GRACE) {
            ++pruneCalls;
            return;
        }
        if(splices.removeIf(s -> gone(s[0]) || gone(s[1])))
            changed();
    }

    private boolean gone(int terminal) {
        if(host.isPoint(terminal))
            return false;
        int hub = host.hubOf(terminal);
        if(hub < 0)
            return true;
        var run = host.hubRun(hub);
        return run != null && run.conductor(host.conductorOf(terminal)) == null;
    }

    private void changed() {
        var behaviour = be.getBehaviour(ElectricBehaviour.TYPE);
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
            var behaviour = be.getBehaviour(ElectricBehaviour.TYPE);
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
        if(hub >= 0) {
            var run = host.hubRun(hub);
            var conductor = run == null ? null : run.conductor(host.conductorOf(terminal));
            int colour = conductor == null ? host.conductorOf(terminal) : conductor.colorIndex();
            var name = Lang.builder().add(host.hubName(hub)).text(" ").add(ConductorColors.name(colour));
            if(conductor != null && !conductor.label().isEmpty())
                name = name.text(" \"" + conductor.label() + "\"");
            return name.component();
        }
        return Component.literal("#" + terminal);
    }

    /**
     * The conduit on the knockouts and the splice count. Without {@code detail} the runs are one
     * line, so a panel's overlay does not bury the panel; sneaking lists every run.
     */
    public void addGoggleLines(List<Component> tooltip, boolean detail) {
        int runs = 0;
        for(int h = 0; h < host.hubCount(); ++h) {
            var run = host.hubRun(h);
            if(run == null)
                continue;
            ++runs;
            if(!detail)
                continue;
            var pulled = run.conductors();
            double used = ConduitFill.used(be.getLevel(), pulled);
            Lang.builder().add(host.hubName(h)).text(": ")
                    .add(run.size().coloredLabel()).text(" ")
                    .add(Lang.builder().translate("gui.conduit.pulled_wires", pulled.size(), run.capacity(),
                            ConduitFill.percent(run.size(), used), ConduitFill.percentLimit(Math.max(1, pulled.size()))).style(ChatFormatting.WHITE))
                    .forGoggles(tooltip, 1);
        }
        if(runs > 0 && !detail)
            Lang.builder().translate("gui.conduit.runs", runs).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        if(detail)
            Lang.builder().translate("gui.conduit.max_size").style(ChatFormatting.DARK_GRAY).text(" ").add(host.maxConduit().coloredLabel()).forGoggles(tooltip, 1);
        if(splices.isEmpty())
            Lang.builder().translate("gui.splice.none").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.splice.count", splices.size()).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
    }

    /** The conduit run whose end sits on the given hub terminal of the block entity, or null. Works on both sides. */
    @Nullable
    public static ConduitRunEntity runAt(SmartBlockEntity be, int hubTerminal) {
        return runAt(be, hubTerminal, be.getBlockPos());
    }

    /** The same, looking for the run itself around {@code near}: a row's far sections sit well away from its head. */
    @Nullable
    public static ConduitRunEntity runAt(SmartBlockEntity be, int hubTerminal, BlockPos near) {
        Level level = be.getLevel();
        if(level == null)
            return null;
        BlockPos pos = be.getBlockPos();
        var behaviour = be.getBehaviour(ElectricBehaviour.TYPE);
        if(behaviour != null) {
            // A run on a delegate cell (a transformer filler, a panel extension) is keyed by that cell's position.
            for(var entry : behaviour.getConnections().entrySet()) {
                var key = entry.getKey();
                if(key.getTerminal() != hubTerminal || !delegatesTo(level, key.getPos(), pos))
                    continue;
                for(var wire : entry.getValue()) {
                    if(wire instanceof ConduitRunEntity run && !run.isRemoved())
                        return run;
                }
            }
        }
        // The client may not have the connection registered yet; look for the entity itself.
        for(var run : level.getEntitiesOfClass(ConduitRunEntity.class, new AABB(near).inflate(3.5))) {
            if(run.isRemoved())
                continue;
            if(endsOn(level, run.getEndpoint1(), pos, hubTerminal) || endsOn(level, run.getEndpoint2(), pos, hubTerminal))
                return run;
        }
        return null;
    }

    /**
     * The far ends of every wire landed on the given terminal of the block entity: a pulled
     * conductor's other landing, or a bare wire's. Mid-air junctions are left out. Server side.
     */
    public static List<BlockWireEndpoint> farEnds(SmartBlockEntity be, int terminal) {
        var out = new java.util.ArrayList<BlockWireEndpoint>();
        Level level = be.getLevel();
        var behaviour = be.getBehaviour(ElectricBehaviour.TYPE);
        if(level == null || behaviour == null)
            return out;
        BlockPos pos = be.getBlockPos();
        for(var entry : behaviour.getConnections().entrySet()) {
            var key = entry.getKey();
            if(key.getTerminal() != terminal || !delegatesTo(level, key.getPos(), pos))
                continue;
            for(var wire : entry.getValue()) {
                if(wire.isRemoved())
                    continue;
                for(var endpoint : new org.patryk3211.powergrid.electricity.wire.IWireEndpoint[] {wire.getEndpoint1(), wire.getEndpoint2()}) {
                    if(endpoint instanceof BlockWireEndpoint block && !(block.getTerminal() == terminal && delegatesTo(level, block.getPos(), pos)))
                        out.add(block);
                }
            }
        }
        return out;
    }

    /** The pulled conductor landed on a host's hidden conductor terminal, or null. Works on both sides. */
    @Nullable
    public static com.nolanbaker.pgmodernized.conduit.ConductorEntity conductorAt(ISpliceHost host, int terminal) {
        int hub = host.hubOf(terminal);
        var run = hub < 0 ? null : host.hubRun(hub);
        return run == null ? null : run.conductor(host.conductorOf(terminal));
    }

    /**
     * Every conductor wired straight through to this one: the chain across splices that join one
     * pulled wire to another, box after box. A splice onto a point, a bar, a device or a breaker
     * ends the chain there. Server side.
     */
    public static List<com.nolanbaker.pgmodernized.conduit.ConductorEntity> chain(Level level, com.nolanbaker.pgmodernized.conduit.ConductorEntity start) {
        var out = new ArrayList<com.nolanbaker.pgmodernized.conduit.ConductorEntity>();
        var seen = new java.util.HashSet<java.util.UUID>();
        var queue = new java.util.ArrayDeque<com.nolanbaker.pgmodernized.conduit.ConductorEntity>();
        queue.add(start);
        seen.add(start.getUUID());
        while(!queue.isEmpty() && out.size() < 256) {
            var conductor = queue.poll();
            out.add(conductor);
            for(var endpoint : new org.patryk3211.powergrid.electricity.wire.IWireEndpoint[] {conductor.getEndpoint1(), conductor.getEndpoint2()}) {
                if(!(endpoint instanceof BlockWireEndpoint block) || !(level.getBlockEntity(block.getPos()) instanceof ISpliceHost host) || host.splices() == null)
                    continue;
                int terminal = block.getTerminal();
                if(host.hubOf(terminal) < 0)
                    continue;
                for(var splice : host.splices().list()) {
                    int other = splice[0] == terminal ? splice[1] : splice[1] == terminal ? splice[0] : -1;
                    if(other < 0 || host.hubOf(other) < 0)
                        continue;
                    var next = conductorAt(host, other);
                    if(next != null && seen.add(next.getUUID()))
                        queue.add(next);
                }
            }
        }
        return out;
    }

    /** Labels every conductor in the chain. Server side. */
    public static void labelChain(Level level, com.nolanbaker.pgmodernized.conduit.ConductorEntity start, String label) {
        for(var conductor : chain(level, start))
            conductor.setLabel(label);
    }

    /** Tapes every conductor in the chain the same colour; a negative index takes them all back to their slot colours. Server side. */
    public static void colorChain(Level level, com.nolanbaker.pgmodernized.conduit.ConductorEntity start, int colorIndex) {
        for(var conductor : chain(level, start))
            conductor.setColorIndex(colorIndex);
    }

    /**
     * Two conductors were just spliced together: the name and the tape colour either side already
     * carries go onto every conductor in the joined chain that has none. Server side.
     */
    public static void carryOver(Level level, com.nolanbaker.pgmodernized.conduit.ConductorEntity a, com.nolanbaker.pgmodernized.conduit.ConductorEntity b) {
        var chain = chain(level, a);
        String label = !a.label().isEmpty() ? a.label() : !b.label().isEmpty() ? b.label() : "";
        int color = a.hasChosenColor() ? a.colorIndex() : b.hasChosenColor() ? b.colorIndex() : -1;
        for(var conductor : chain) {
            if(label.isEmpty() && !conductor.label().isEmpty())
                label = conductor.label();
            if(color < 0 && conductor.hasChosenColor())
                color = conductor.colorIndex();
        }
        for(var conductor : chain) {
            if(!label.isEmpty() && conductor.label().isEmpty())
                conductor.setLabel(label);
            if(color >= 0 && !conductor.hasChosenColor())
                conductor.setColorIndex(color);
        }
    }

    /** The position itself, or a cell whose block entity answers for the block at {@code head}. */
    private static boolean delegatesTo(Level level, BlockPos at, BlockPos head) {
        return at.equals(head) || (level.getBlockEntity(at) instanceof IElectricDelegate delegate && delegate.headPos().equals(head));
    }

    private static boolean endsOn(Level level, @Nullable org.patryk3211.powergrid.electricity.wire.IWireEndpoint endpoint, BlockPos head, int terminal) {
        return endpoint instanceof BlockWireEndpoint block && block.getTerminal() == terminal && delegatesTo(level, block.getPos(), head);
    }
}
