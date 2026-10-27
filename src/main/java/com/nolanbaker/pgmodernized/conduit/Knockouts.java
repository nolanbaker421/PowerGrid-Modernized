package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/**
 * Which way a knockout faces. A knockout terminal is a thin slab on the edge of its host, so the
 * thin axis of its box, taken away from the block's centre, is the way a conduit should leave it.
 * Runs start and end with a straight stub along that line, so the tube always leaves the knockout
 * dead centre and square, whatever the tracer does after.
 */
public final class Knockouts {
    /** Length of the straight stub out of a knockout, in pixels. Clears the knockout and the box face. */
    public static final int STUB_PX = 4;

    private Knockouts() {}

    /** The way a run should leave that knockout, or null when it is not a knockout we can read. */
    @Nullable
    public static Direction exit(Level level, BlockWireEndpoint endpoint) {
        // A box on a ceiling tile is no different: its knockouts face sideways, a pixel and a half
        // over the tile, which is the height the tracer runs a tube across the tiles at anyway.
        return normal(level, endpoint);
    }

    @Nullable
    public static Direction normal(Level level, BlockWireEndpoint endpoint) {
        // The block may be gone, or not here yet on a client: Power Grid would throw asking it for a terminal.
        if(org.patryk3211.powergrid.electricity.base.IElectric.getAt(level, endpoint.getPos()) == null)
            return null;
        if(!(endpoint.getTerminalPlacement(level) instanceof TerminalBoundingBox box))
            return null;
        var o = box.getOutline();
        // The box is in block units; a collection that kept pixels shows up as coordinates past 2.
        double scale = Math.max(Math.abs(o.maxX), Math.max(Math.abs(o.maxY), Math.abs(o.maxZ))) > 2 ? 1 / 16.0 : 1;
        double[] size = {o.getXsize() * scale, o.getYsize() * scale, o.getZsize() * scale};
        var c = o.getCenter().scale(scale);
        double[] off = {c.x - 0.5, c.y - 0.5, c.z - 0.5};
        int thin = 0;
        for(int i = 1; i < 3; ++i)
            if(size[i] < size[thin] - 1e-6)
                thin = i;
        boolean tie = false;
        for(int i = 0; i < 3; ++i)
            if(i != thin && Math.abs(size[i] - size[thin]) < 1e-6)
                tie = true;
        int axis = thin;
        if(tie || Math.abs(off[axis]) < 0.02) {
            axis = 0;
            for(int i = 1; i < 3; ++i)
                if(Math.abs(off[i]) > Math.abs(off[axis]))
                    axis = i;
        }
        if(Math.abs(off[axis]) < 0.02)
            return null;
        return Direction.fromAxisAndDirection(Direction.Axis.values()[axis], off[axis] > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }
}
