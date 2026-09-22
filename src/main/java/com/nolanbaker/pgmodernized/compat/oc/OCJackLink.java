package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.network.JackSupport;
import li.cil.oc.api.network.Environment;

/**
 * A jack's presence on the OpenComputers network. The block entity already owns an OC node (the OC
 * device subclasses and {@link OCNetworkJackBlockEntity}); this just adds and removes edges to it.
 */
public final class OCJackLink implements JackSupport.Link {
    public static final String KIND = "opencomputers";

    private final Environment environment;

    public OCJackLink(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public void connect(JackSupport.Link other) {
        if(!(other instanceof OCJackLink remote))
            return;
        var a = environment.node();
        var b = remote.environment.node();
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
        if(!(other instanceof OCJackLink remote))
            return;
        var a = environment.node();
        var b = remote.environment.node();
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
        // The block entity owns the node and removes it itself.
    }
}
