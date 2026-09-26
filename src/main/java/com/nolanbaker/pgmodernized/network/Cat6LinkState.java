package com.nolanbaker.pgmodernized.network;

import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

/**
 * The network side of a Cat6 cable, shared by the hanging and the block-routed entity.
 * Replaces Power Grid's "make wire" step: instead of joining an electrical network the cable
 * links the two jacks' computer network nodes, port to port, and re-checks that link once a
 * second so jacks that unload and reload with fresh nodes are picked up again.
 */
public final class Cat6LinkState {
    private static final int RELINK_INTERVAL = 20;

    private final BaseWireEntity owner;
    private final ICat6Cable cable;
    @Nullable
    private JackSupport linkedA, linkedB;
    private int portA, portB;
    private int relinkTimer;

    public <T extends BaseWireEntity & ICat6Cable> Cat6LinkState(T owner) {
        this.owner = owner;
        this.cable = owner;
    }

    public void makeWire() {
        dropWire();
        if(owner.level().isClientSide)
            return;
        var a = jackAt(owner.getEndpoint1());
        var b = jackAt(owner.getEndpoint2());
        if(a == null || b == null)
            return; // an open end, or the other jack is unloaded
        a.addCable(cable);
        b.addCable(cable);
        linkedA = a;
        linkedB = b;
        portA = port(owner.getEndpoint1());
        portB = port(owner.getEndpoint2());
        a.connect(b, portA, portB);
    }

    public void dropWire() {
        if(linkedA != null && linkedB != null)
            linkedA.disconnect(linkedB, portA, portB);
        linkedA = null;
        linkedB = null;
    }

    /** Server tick. */
    public void tick() {
        if(owner.level().isClientSide || owner.isRemoved())
            return;
        if(++relinkTimer < RELINK_INTERVAL)
            return;
        relinkTimer = 0;

        // A jack block that vanished while its chunk is loaded takes its end of the cable with it.
        if(lost(owner.getEndpoint1()) || lost(owner.getEndpoint2()))
            return;

        var a = jackAt(owner.getEndpoint1());
        var b = jackAt(owner.getEndpoint2());
        if(a == null || b == null)
            return;
        if(a != linkedA || b != linkedB || !a.isLoaded() || !b.isLoaded()) {
            makeWire(); // a jack reloaded with fresh nodes
        } else {
            a.connect(b, portA, portB); // idempotent, survives network rebuilds
        }
    }

    private boolean lost(@Nullable IWireEndpoint endpoint) {
        if(!(endpoint instanceof JackEndpoint jack))
            return false;
        var level = owner.level();
        if(!level.isLoaded(jack.getPos()) || jack.jack(level) != null)
            return false;
        cable.jackLost(jack.getPos());
        return true;
    }

    @Nullable
    private JackSupport jackAt(@Nullable IWireEndpoint endpoint) {
        return endpoint instanceof JackEndpoint jack ? jack.jack(owner.level()) : null;
    }

    private static int port(@Nullable IWireEndpoint endpoint) {
        return endpoint instanceof JackEndpoint jack ? jack.getPort() : 0;
    }
}
