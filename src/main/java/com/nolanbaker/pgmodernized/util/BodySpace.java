package com.nolanbaker.pgmodernized.util;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntityEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

/**
 * Block positions on a Sable body are the body's own ("plot") coordinates, and so are the
 * terminal positions Power Grid derives from them, the paths the block tracer finds, and the
 * click locations. A laid run that {@link SubLevelStick} carries with the body, however, lives
 * in world coordinates. Placement math must happen in one space, so this takes a run's world
 * position back into the body's coordinates before it is compared with block positions.
 */
public final class BodySpace {
    private BodySpace() {}

    /** {@code world} in the coordinates of the body carrying {@code entity}, or unchanged when none does. */
    public static Vec3 local(Entity entity, Vec3 world) {
        var subLevel = SableCompanion.INSTANCE.getTrackingSubLevel(entity);
        return subLevel == null ? world : subLevel.logicalPose().transformPositionInverse(world);
    }

    /** An endpoint's exact position, in body coordinates when it is the end of a run riding a body. */
    public static Vec3 local(Level level, IWireEndpoint endpoint) {
        var position = endpoint.getExactPosition(level);
        if(endpoint instanceof BlockWireEntityEndpoint runEnd) {
            var entity = runEnd.getEntity(level);
            if(entity != null)
                return local(entity, position);
        }
        return position;
    }
}
