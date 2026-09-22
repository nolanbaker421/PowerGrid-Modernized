package com.nolanbaker.pgmodernized.conduit.splice;

import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A block entity with conduit hubs and an editable set of splices: the conduit box and the breaker
 * panel. Terminals are split into fixed <em>points</em> (cover terminals, or a panel's line, neutral
 * and circuits), one terminal per <em>hub</em> (where a run lands), and hidden per-hub
 * <em>conductor</em> terminals the pulled wires land on. Splices are near-zero-ohm wires between any
 * usable pair.
 */
public interface ISpliceHost {
    SpliceSupport splices();

    List<SplicePoint> points();

    boolean isPoint(int terminal);

    int hubCount();

    Component hubName(int hub);

    int hubTerminal(int hub);

    /** Hub index of a hub terminal, or -1. */
    int hubAt(int terminal);

    int conductorTerminal(int hub, int conductor);

    /** Hub a hidden conductor terminal belongs to, or -1 for anything else. */
    int hubOf(int terminal);

    /** Conductor index of a hidden conductor terminal. */
    int conductorOf(int terminal);

    /** The run on that hub, or null. Works on both sides. */
    @Nullable
    ConduitRunEntity hubRun(int hub);
}
