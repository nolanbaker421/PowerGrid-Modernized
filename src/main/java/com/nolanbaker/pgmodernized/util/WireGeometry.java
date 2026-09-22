package com.nolanbaker.pgmodernized.util;

import net.minecraft.world.phys.AABB;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;

/**
 * Geometry queries on Power Grid wire entities that Power Grid itself does not offer.
 * Everything here reads public state only, so no upstream patch is required.
 */
public final class WireGeometry {
    private WireGeometry() {}

    /** Whether the wire's physical path runs through the given world-space box. */
    public static boolean passesThrough(BaseWireEntity wire, AABB box) {
        if(wire instanceof HangingWireEntity hanging)
            return hangingPassesThrough(hanging, box);
        if(wire instanceof BlockWireEntity block)
            return blockWirePassesThrough(block, box);
        return wire.getBoundingBox().intersects(box);
    }

    private static boolean hangingPassesThrough(HangingWireEntity wire, AABB box) {
        var params = wire.curveParams;
        if(params == null || wire.terminalPos1 == null || wire.terminalPos2 == null)
            return wire.getBoundingBox().intersects(box);
        // Cheap reject: the catenary never leaves the box spanned by its two terminals (plus sag margin).
        if(!new AABB(wire.terminalPos1, wire.terminalPos2).inflate(0.5).intersects(box))
            return false;
        var origin = wire.position();
        int points = Math.max(16, (int) (params.L * 8));
        var hit = new boolean[1];
        params.runForPoints(points, (x, y, z) -> {
            if(!hit[0] && box.contains(origin.x + x, origin.y + y, origin.z + z))
                hit[0] = true;
        });
        return hit[0];
    }

    private static boolean blockWirePassesThrough(BlockWireEntity wire, AABB box) {
        if(wire.boundingBoxes.isEmpty())
            return wire.getBoundingBox().intersects(box);
        var origin = wire.position();
        for(var segment : wire.boundingBoxes) {
            if(segment.move(origin).intersects(box))
                return true;
        }
        return false;
    }
}
