package com.nolanbaker.pgmodernized.conduit.tray;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity.Point;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The tray network as a graph: runs are nodes, and two runs join wherever one's end sits on the
 * other's rails. A route from a point on one run to a point on another is the shortest chain of
 * joins, walked along each run's corners; a lane offset then sets the cable beside the centreline
 * and the result becomes the axis-aligned legs a Cat6 run is made of.
 */
public final class TrayGraph {
    private TrayGraph() {}

    /** The runs a route crosses, in order, and its corners from entry to exit along the rails' centreline. */
    public record Route(List<TrayRunEntity> runs, List<Vec3> corners) {}

    private record Join(TrayRunEntity to, Vec3 leaveAt, Vec3 enterAt) {}

    @Nullable
    public static Route route(Level level, TrayRunEntity entryRun, Vec3 entry, TrayRunEntity exitRun, Vec3 exit) {
        if(entryRun == exitRun)
            return new Route(List.of(entryRun), along(entryRun, entry, exit));
        var trays = new LinkedHashSet<TrayRunEntity>();
        trays.addAll(TrayRunEntity.around(level, entry));
        trays.addAll(TrayRunEntity.around(level, exit));
        // Every way out of every run: an end of a on b's rails joins them, usable either way.
        Map<UUID, List<Join>> joins = new HashMap<>();
        for(var a : trays) {
            for(var b : trays) {
                if(a == b)
                    continue;
                for(var end : new Vec3[] {a.position(), a.endPosition()}) {
                    var near = b.nearest(end);
                    if(near.distance2() > TrayRunEntity.JOIN_REACH * TrayRunEntity.JOIN_REACH)
                        continue;
                    joins.computeIfAbsent(a.getUUID(), k -> new ArrayList<>()).add(new Join(b, end, near.point()));
                    joins.computeIfAbsent(b.getUUID(), k -> new ArrayList<>()).add(new Join(a, near.point(), end));
                }
            }
        }
        // Breadth first from the entry run to the exit run.
        Map<UUID, Join> cameBy = new HashMap<>();
        Map<UUID, TrayRunEntity> cameFrom = new HashMap<>();
        var seen = new HashSet<UUID>();
        var queue = new ArrayDeque<TrayRunEntity>();
        queue.add(entryRun);
        seen.add(entryRun.getUUID());
        boolean found = false;
        while(!queue.isEmpty() && !found) {
            var run = queue.poll();
            for(var join : joins.getOrDefault(run.getUUID(), List.of())) {
                if(!seen.add(join.to.getUUID()))
                    continue;
                cameBy.put(join.to.getUUID(), join);
                cameFrom.put(join.to.getUUID(), run);
                if(join.to == exitRun) {
                    found = true;
                    break;
                }
                queue.add(join.to);
            }
        }
        if(!found)
            return null;
        // Walk back from the exit to the entry, then lay the corners forward run by run.
        var hops = new ArrayList<Join>();
        var runs = new ArrayList<TrayRunEntity>();
        var cursor = exitRun;
        while(cursor != entryRun) {
            hops.add(0, cameBy.get(cursor.getUUID()));
            runs.add(0, cursor);
            cursor = cameFrom.get(cursor.getUUID());
        }
        runs.add(0, entryRun);
        var corners = new ArrayList<Vec3>();
        var at = entry;
        var run = entryRun;
        for(var hop : hops) {
            append(corners, along(run, at, hop.leaveAt));
            run = hop.to;
            at = hop.enterAt;
        }
        append(corners, along(run, at, exit));
        return new Route(runs, simplify(corners));
    }

    private static void append(List<Vec3> corners, List<Vec3> more) {
        for(var p : more)
            if(corners.isEmpty() || corners.get(corners.size() - 1).distanceToSqr(p) > 1e-6)
                corners.add(p);
    }

    /** The centreline from one point on a run to another, through the corners between them. */
    private static List<Vec3> along(TrayRunEntity run, Vec3 from, Vec3 to) {
        var a = run.nearest(from);
        var b = run.nearest(to);
        var corners = run.corners();
        var out = new ArrayList<Vec3>();
        out.add(a.point());
        if(a.leg() < b.leg())
            for(int i = a.leg() + 1; i <= b.leg(); ++i)
                out.add(corners.get(i));
        else if(a.leg() > b.leg())
            for(int i = a.leg(); i > b.leg(); --i)
                out.add(corners.get(i));
        out.add(b.point());
        return out;
    }

    /** Drops repeated and collinear corners. */
    private static List<Vec3> simplify(List<Vec3> corners) {
        var out = new ArrayList<Vec3>();
        for(var p : corners) {
            if(!out.isEmpty() && out.get(out.size() - 1).distanceToSqr(p) < 1e-6)
                continue;
            if(out.size() >= 2) {
                var a = out.get(out.size() - 2);
                var b = out.get(out.size() - 1);
                if(b.subtract(a).normalize().subtract(p.subtract(b).normalize()).lengthSqr() < 1e-6) {
                    out.set(out.size() - 1, p);
                    continue;
                }
            }
            out.add(p);
        }
        return out;
    }

    // ---- lanes ----

    /** The first lane free on every run of a route, or -1 when some run is full. */
    public static int freeLane(Route route) {
        for(int lane = 0; lane < TrayRunEntity.LANES; ++lane) {
            boolean free = true;
            for(var run : route.runs())
                free &= run.laneFree(lane);
            if(free)
                return lane;
        }
        return -1;
    }

    /**
     * The route's corners moved sideways into a lane, and up a layer for the second five. Each leg
     * shifts within its own surface; where two legs meet, the corner takes the next leg's shift
     * and whatever of the last leg's shift still lies along the next leg.
     */
    public static List<Vec3> offset(Level level, List<Vec3> corners, int lane) {
        int n = corners.size() - 1;
        if(n < 1)
            return corners;
        double lateral = TrayRunEntity.LANE_PX[lane % TrayRunEntity.LANE_PX.length] / 16.0;
        double lift = (lane / TrayRunEntity.LANE_PX.length) * TrayRunEntity.LAYER_LIFT_PX / 16.0;
        var shift = new Vec3[n];
        var side = new Vec3[n];
        var normal = new Vec3[n];
        for(int i = 0; i < n; ++i) {
            var d = corners.get(i + 1).subtract(corners.get(i));
            var dir = Direction.getNearest(d.x, d.y, d.z);
            var nrm = surfaceNormal(level, corners.get(i).add(corners.get(i + 1)).scale(0.5), dir);
            normal[i] = Vec3.atLowerCornerOf(nrm.getNormal());
            side[i] = Vec3.atLowerCornerOf(dir.getNormal()).cross(normal[i]);
            shift[i] = side[i].scale(lateral).add(normal[i].scale(lift));
        }
        var out = new ArrayList<Vec3>();
        out.add(corners.get(0).add(shift[0]));
        for(int k = 1; k < n; ++k) {
            var carry = shift[k - 1];
            carry = carry.subtract(side[k].scale(carry.dot(side[k]))).subtract(normal[k].scale(carry.dot(normal[k])));
            out.add(corners.get(k).add(shift[k]).add(carry));
        }
        out.add(corners.get(n).add(shift[n - 1]));
        return out;
    }

    /** The face a leg lies on, probing just past the tray's depth on each side of its middle. */
    public static Direction surfaceNormal(Level level, Vec3 mid, Direction along) {
        for(var d : Direction.values()) {
            if(d.getAxis() == along.getAxis())
                continue;
            var probe = mid.relative(d, 0.1875 / 2 + 1 / 32.0);
            var pos = BlockPos.containing(probe);
            if(!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
                return d.getOpposite();
        }
        return Direction.UP;
    }

    /** Axis-aligned legs from corner to corner, the longest component of each step first. */
    public static List<Point> toPoints(List<Vec3> corners) {
        var out = new ArrayList<Point>();
        for(int i = 0; i + 1 < corners.size(); ++i) {
            var d = corners.get(i + 1).subtract(corners.get(i));
            int[] px = {(int) Math.round(d.x * 16), (int) Math.round(d.y * 16), (int) Math.round(d.z * 16)};
            Direction.Axis[] axes = {Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z};
            Integer[] order = {0, 1, 2};
            java.util.Arrays.sort(order, (a, b) -> Integer.compare(Math.abs(px[b]), Math.abs(px[a])));
            for(int k : order) {
                if(px[k] == 0)
                    continue;
                var dir = Direction.fromAxisAndDirection(axes[k], px[k] > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
                out.add(new Point(dir, Math.abs(px[k])));
            }
        }
        return out;
    }

    public static float length(List<Point> points) {
        float total = 0;
        for(var p : points)
            total += p.length();
        return total;
    }

    /** Where a list of legs ends, starting from {@code start}. */
    public static Vec3 end(Vec3 start, List<Point> points) {
        var pos = start;
        for(var p : points)
            pos = pos.add(p.vector());
        return pos;
    }

    /** Legs that take one from {@code from} to {@code to}, a straight jog in each axis that differs. */
    public static List<Point> jog(Vec3 from, Vec3 to) {
        return toPoints(List.of(from, to));
    }

    public static Set<UUID> ids(List<TrayRunEntity> runs) {
        var out = new HashSet<UUID>();
        for(var run : runs)
            out.add(run.getUUID());
        return out;
    }
}
