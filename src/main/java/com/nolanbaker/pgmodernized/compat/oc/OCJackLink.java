package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.network.JackSupport;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;

/**
 * A jack's presence on the OpenComputers network. The block entity already owns its OC nodes (the
 * OC device subclasses, {@link OCNetworkJackBlockEntity} and {@link OCNetworkSwitchBlockEntity});
 * this just adds and removes edges between them. Most jacks answer every port with the same
 * node; a switch in relay mode has one per port.
 */
public final class OCJackLink implements JackSupport.Link {
    public static final String KIND = "opencomputers";

    private final IntFunction<Node> nodeForPort;

    public OCJackLink(Environment environment) {
        this(port -> environment.node());
    }

    public OCJackLink(IntFunction<Node> nodeForPort) {
        this.nodeForPort = nodeForPort;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Nullable
    private Node node(int port) {
        return nodeForPort.apply(port);
    }

    @Override
    public void connect(JackSupport.Link other) {
        connect(other, 0, 0);
    }

    @Override
    public void connect(JackSupport.Link other, int localPort, int remotePort) {
        if(!(other instanceof OCJackLink remote))
            return;
        var a = node(localPort);
        var b = remote.node(remotePort);
        // Both ends must already sit in a network (their own tick joins or creates one); the cable retries otherwise.
        if(a == null || b == null || a.network() == null || b.network() == null)
            return;
        try {
            a.connect(b);
        } catch(RuntimeException e) {
            PowerGridModernized.LOGGER.debug("OpenComputers refused a Cat6 link", e);
        }
    }

    @Override
    public void disconnect(JackSupport.Link other) {
        disconnect(other, 0, 0);
    }

    @Override
    public void disconnect(JackSupport.Link other, int localPort, int remotePort) {
        if(!(other instanceof OCJackLink remote))
            return;
        var a = node(localPort);
        var b = remote.node(remotePort);
        if(a == null || b == null || a.network() == null || a.network() != b.network())
            return;
        try {
            a.disconnect(b);
        } catch(RuntimeException e) {
            PowerGridModernized.LOGGER.debug("OpenComputers refused a Cat6 unlink", e);
        }
    }

    @Override
    public void remove() {
        // The block entity owns the nodes and removes them itself.
    }
}
