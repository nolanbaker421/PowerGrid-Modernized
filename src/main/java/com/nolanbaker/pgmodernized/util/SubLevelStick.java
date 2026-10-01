package com.nolanbaker.pgmodernized.util;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.world.entity.Entity;
import org.patryk3211.powergrid.compat.sable.SableUtils;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;

/**
 * Power Grid asks Sable to carry a wire entity with a body only for hanging wires whose two ends
 * are on the same body. A block-laid wire (a Cat6 cable along the deck, a conduit run, a pulled
 * conductor) is created at the body's own coordinates and then left there, so the body drives off
 * without it. This does for them what Power Grid does for hanging wires: move the entity to where
 * the body currently shows it, and hand it to Sable to keep it there. Without Sable both calls are
 * no-ops. The body's rotation is not followed, only its position; a trolley on a straight runway
 * does not rotate.
 */
public final class SubLevelStick {
    private SubLevelStick() {}

    /** Call every tick; it only does anything while the entity still sits at body coordinates. */
    public static void stick(BlockWireEntity entity) {
        var level = entity.level();
        var position = entity.position();
        var subLevel = SableCompanion.INSTANCE.getContaining(level, position);
        if(subLevel == null)
            return;
        var world = SableCompanion.INSTANCE.projectOutOfSubLevel(level, position);
        if(world == null || world.distanceToSqr(position) < 1e-6)
            return;
        entity.setPos(world);
        entity.setOldPosAndRot();
        entity.bakeBoundingBoxes();
        SableUtils.PROXY.setSubLevelTracking(entity, subLevel);
    }
}
