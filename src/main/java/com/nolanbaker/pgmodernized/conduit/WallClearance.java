package com.nolanbaker.pgmodernized.conduit;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a laid run on the wall instead of in it. Power Grid's tracer runs a centre line a pixel
 * and a half off a surface, which buries half of anything thicker than 1-1/2". After tracing,
 * every leg long enough to take it is checked against the solid faces beside it, and a leg that
 * would sink is slid out to where the tube just touches, which puts a kick at each end of the leg:
 * out of a knockout that is a box offset, along a run it is the offset an electrician would bend.
 */
public final class WallClearance {
    /** A leg shorter than this is left alone: there is no room for the kicks. */
    private static final int MIN_LEG_PX = 8;

    private WallClearance() {}

    /**
     * @param origin    where the first point starts, in the level's coordinates
     * @param fixStart  the first leg is a knockout stub, or the end of an existing run, and stays put
     * @param fixEnd    the last leg is a knockout stub and stays put
     */
    public static List<Point> apply(Level level, Vec3 origin, List<Point> points, double tubeRadius, boolean fixStart, boolean fixEnd) {
        if(tubeRadius <= 0 || points.size() < 2)
            return points;
        // A run on a Sable body keeps body coordinates; the level's blocks are not where it is.
        if(SableCompanion.INSTANCE.getContaining(level, BlockPos.containing(origin)) != null)
            return points;
        var legs = new ArrayList<>(points);
        int i = fixStart ? 1 : 0;
        while(i < legs.size() - (fixEnd ? 1 : 0)) {
            var leg = legs.get(i);
            if(leg.gridLength < MIN_LEG_PX) {
                ++i;
                continue;
            }
            var start = origin;
            for(int k = 0; k < i; ++k)
                start = start.add(legs.get(k).vector());
            var mid = start.add(leg.vector().scale(0.5));
            Direction push = null;
            int pixels = 0;
            for(var d : Direction.values()) {
                if(d.getAxis() == leg.direction.getAxis())
                    continue;
                var probe = BlockPos.containing(mid.add(Vec3.atLowerCornerOf(d.getNormal()).scale(tubeRadius)));
                if(!level.getBlockState(probe).isFaceSturdy(level, probe, d.getOpposite()))
                    continue;
                double face = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? probe.get(d.getAxis()) : probe.get(d.getAxis()) + 1;
                double distance = Math.abs(face - mid.get(d.getAxis()));
                int need = (int) Math.ceil((tubeRadius - distance) * 16 - 0.15);
                if(need > pixels) {
                    pixels = need;
                    push = d.getOpposite();
                }
            }
            if(push == null) {
                ++i;
                continue;
            }
            var result = RunReshape.slide(legs, i, push, pixels, fixStart, fixEnd);
            if(result == null || !result.startShift().equals(Vec3i.ZERO)) {
                ++i;
                continue;
            }
            legs = new ArrayList<>(result.points());
            // The slid leg now sits one further along, after the kick in front of it; skip the kick behind it too.
            i += 3;
        }
        return legs;
    }
}
