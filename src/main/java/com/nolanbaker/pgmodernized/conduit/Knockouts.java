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
        // A box on a ceiling tile sits inside the tile's block: its runs leave upward, over the tiles.
        if(level.getBlockState(endpoint.getPos()).getBlock() instanceof CeilingConduitBoxBlock)
            return Direction.UP;
        return normal(level, endpoint);
    }

    /**
     * How far, and which way, a run must step off the wall behind the knockout so a tube of that
     * radius clears it: the knockout sits a pixel and a half off the mount, so a 2" tube would bury
     * its back half. Null when nothing solid stands behind or the tube already clears it.
     */
    @Nullable
    public static Lift lift(Level level, BlockWireEndpoint endpoint, Direction exit, double tubeRadius) {
        if(!(endpoint.getTerminalPlacement(level) instanceof TerminalBoundingBox box))
            return null;
        var o = box.getOutline();
        double scale = Math.max(Math.abs(o.maxX), Math.max(Math.abs(o.maxY), Math.abs(o.maxZ))) > 2 ? 1 / 16.0 : 1;
        var c = o.getCenter().scale(scale);
        var pos = endpoint.getPos();
        Lift best = null;
        for(var wall : Direction.values()) {
            if(wall.getAxis() == exit.getAxis())
                continue;
            var behind = pos.relative(wall);
            if(!level.getBlockState(behind).isFaceSturdy(level, behind, wall.getOpposite()))
                continue;
            double coordinate = c.get(wall.getAxis());
            double clearance = wall.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 - coordinate : coordinate;
            double need = tubeRadius + 0.5 / 16 - clearance;
            if(need <= 0)
                continue;
            int px = (int) Math.ceil(need * 16);
            if(best == null || px > best.pixels())
                best = new Lift(wall.getOpposite(), px);
        }
        return best;
    }

    /** A step off a wall: which way, and how many pixels. */
    public record Lift(Direction away, int pixels) {}

    @Nullable
    public static Direction normal(Level level, BlockWireEndpoint endpoint) {
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
