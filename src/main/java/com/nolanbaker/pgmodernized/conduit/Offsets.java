package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The offsets in a run, as the bender grabs them: two legs the same way with a few short legs
 * jogging across between them, which the renderer draws as one smooth S. The bender moves such
 * an offset along the run as one piece, the leg before it growing and the leg after it
 * shrinking by the same amount, so the jog keeps its shape. The grouping here is the renderer's,
 * so what looks like one offset is grabbed as one. Works on both sides.
 */
public final class Offsets {
    private Offsets() {}

    /** How short a sideways jog can be and still be part of an offset instead of two elbows, in pixels. */
    public static final int MAX_PX = 4;
    /** Short legs an offset may run through. */
    public static final int MAX_LEGS = 4;
    /** A tap this close to the joint of a long leg and an offset, in pixels, grabs the offset rather than the leg. */
    public static final int GRAB_PX = 3;

    /** Legs {@code first} and {@code last} run the same way; the legs between jog the run across. */
    public record Offset(int first, int last) {}

    /** Every offset in the legs, in order along the run. */
    public static List<Offset> list(List<BlockWireEntity.Point> legs) {
        var out = new ArrayList<Offset>();
        int n = legs.size();
        for(int i = 0; i + 2 < n; ++i) {
            var a = legs.get(i);
            var along = Vec3.atLowerCornerOf(a.direction.getNormal());
            int span = 0;
            var jog = Vec3.ZERO;
            boolean ok = false;
            for(int k = i + 1; k < n && span < MAX_LEGS; ++k) {
                var leg = legs.get(k);
                if(leg.direction == a.direction) {
                    ok = span > 0;
                    break;
                }
                if(leg.direction == a.direction.getOpposite() || leg.gridLength > MAX_PX)
                    break;
                jog = jog.add(leg.vector());
                ++span;
            }
            if(!ok)
                continue;
            var across = jog.subtract(along.scale(jog.dot(along)));
            if(across.length() < 0.5 / 16)
                continue;
            out.add(new Offset(i, i + span + 1));
            i += span;
        }
        return out;
    }

    /** The offset whose first leg is {@code first}, if the run still has it. */
    @Nullable
    public static Offset find(ConduitRunEntity run, int first, int last) {
        for(var o : list(run.segments))
            if(o.first() == first && o.last() == last)
                return o;
        return null;
    }

    /**
     * The offset a tap {@code px} pixels along leg {@code leg} grabs: a tap on one of the jogs,
     * or on a long leg within {@link #GRAB_PX} of where the jogs begin. Null for a plain leg.
     */
    @Nullable
    public static Offset at(ConduitRunEntity run, int leg, int px) {
        for(var o : list(run.segments)) {
            if(leg > o.first() && leg < o.last())
                return o;
            if(leg == o.first() && px >= run.segments.get(leg).gridLength - GRAB_PX)
                return o;
            if(leg == o.last() && px <= GRAB_PX)
                return o;
        }
        return null;
    }

    /** The box round the jogs, in the level's coordinates. */
    public static AABB box(ConduitRunEntity run, Offset o) {
        AABB box = null;
        for(int i = o.first() + 1; i < o.last() && i < run.boundingBoxes.size(); ++i)
            box = box == null ? run.legBox(i) : box.minmax(run.legBox(i));
        return box == null ? run.legBox(o.first()) : box;
    }

    /**
     * How far along the run, in pixels the way the first leg runs, the offset would slide for a
     * tap at {@code point}: to where the tap landed along the run, on the pixel grid. Not yet
     * clamped to the room the long legs have; see {@link #room}.
     */
    public static int wanted(ConduitRunEntity run, Offset o, Vec3 point) {
        var a = run.segments.get(o.first());
        var axis = a.direction.getAxis();
        var joint = ConduitBenderItem.pointOnLeg(run, o.first(), a.gridLength);
        double want = (Math.round(point.get(axis) * 16 - 0.5) + 0.5) / 16;
        int d = (int) Math.round((want - joint.get(axis)) * 16);
        return a.direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? d : -d;
    }

    /**
     * The least and most the offset may slide, in pixels the way the first leg runs: the leg
     * before keeps at least a pixel, or its knockout stub when it is the first leg, and so does
     * the leg after.
     */
    public static int[] room(ConduitRunEntity run, Offset o) {
        var a = run.segments.get(o.first());
        var c = run.segments.get(o.last());
        int minA = o.first() == 0 && run.getEndpoint1() != null ? Knockouts.STUB_PX : 1;
        int minC = o.last() == run.segments.size() - 1 && run.getEndpoint2() != null ? Knockouts.STUB_PX : 1;
        return new int[] {Math.min(0, minA - a.gridLength), Math.max(0, c.gridLength - minC)};
    }

    /** {@link #wanted} held within {@link #room}. */
    public static int slide(ConduitRunEntity run, Offset o, Vec3 point) {
        var room = room(run, o);
        return Math.max(room[0], Math.min(room[1], wanted(run, o, point)));
    }

    /** The legs with the offset slid {@code d} pixels along the run. */
    public static List<BlockWireEntity.Point> slid(ConduitRunEntity run, Offset o, int d) {
        var legs = new ArrayList<>(run.segments);
        var a = legs.get(o.first());
        var c = legs.get(o.last());
        legs.set(o.first(), new BlockWireEntity.Point(a.direction, a.gridLength + d));
        legs.set(o.last(), new BlockWireEntity.Point(c.direction, c.gridLength - d));
        return legs;
    }
}
