package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of bending a laid run: pure arithmetic on Power Grid's axis-aligned pixel segments,
 * with no entity in sight so it can be unit tested. A run is a polyline of {@link Point}s, each a
 * direction and a whole number of pixels. Sliding one straight sideways is done by splicing a step
 * in before it and the opposite step after it, then tidying: neighbouring segments on one axis
 * merge into their signed sum, and what sums to nothing disappears. Everything a bender can do to
 * an axis-aligned run falls out of that one move: offsets, kicks, saddles, new corners, and corners
 * that straighten back out.
 */
public final class RunReshape {
    private RunReshape() {}

    /** A reshaped run, and how far its first point moved (zero when the start stayed put). */
    public record Result(List<Point> points, Vec3i startShift) {}

    /**
     * Slides segment {@code index} by {@code pixels} in direction {@code push}, which must be
     * perpendicular to that segment. With {@code fixStart} the run's first point stays where it is
     * (a lead-in step is spliced before the segment); otherwise the start moves with the segment.
     * Likewise {@code fixEnd} for the far end. Null when the push is along the segment itself.
     */
    @Nullable
    public static Result slide(List<Point> points, int index, Direction push, int pixels, boolean fixStart, boolean fixEnd) {
        if(index < 0 || index >= points.size() || pixels <= 0)
            return null;
        var segment = points.get(index);
        if(segment.direction.getAxis() == push.getAxis())
            return null;
        var out = new ArrayList<Point>(points.size() + 2);
        var shift = Vec3i.ZERO;
        for(int i = 0; i < index; ++i)
            out.add(points.get(i));
        if(fixStart || index > 0)
            out.add(new Point(push, pixels));
        else
            shift = push.getNormal().multiply(pixels);
        out.add(segment);
        if(fixEnd || index < points.size() - 1)
            out.add(new Point(push.getOpposite(), pixels));
        for(int i = index + 1; i < points.size(); ++i)
            out.add(points.get(i));
        return new Result(tidy(out), shift);
    }

    /**
     * Merges neighbours that share an axis into one signed segment and drops what cancels, until
     * nothing more folds. The result has no zero-length segment and no two neighbours on one axis.
     */
    public static List<Point> tidy(List<Point> points) {
        var out = new ArrayList<Point>(points.size());
        for(var point : points) {
            if(point.gridLength <= 0)
                continue;
            out.add(point);
            // Fold the tail while the last two share an axis.
            while(out.size() >= 2) {
                var b = out.get(out.size() - 1);
                var a = out.get(out.size() - 2);
                if(a.direction.getAxis() != b.direction.getAxis())
                    break;
                int signed = signedLength(a) + signedLength(b);
                out.remove(out.size() - 1);
                out.remove(out.size() - 1);
                if(signed != 0)
                    out.add(new Point(Direction.fromAxisAndDirection(a.direction.getAxis(),
                            signed > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE), Math.abs(signed)));
            }
        }
        return out;
    }

    /** Pixels along the axis, negative for the negative direction. */
    public static int signedLength(Point point) {
        return point.direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? point.gridLength : -point.gridLength;
    }

    /** Whole pixels along a run. */
    public static int totalPixels(List<Point> points) {
        int total = 0;
        for(var point : points)
            total += point.gridLength;
        return total;
    }

    /** Whether two segments are the same direction and length. */
    public static boolean same(Point a, Point b) {
        return a.direction == b.direction && a.gridLength == b.gridLength;
    }
}
