package com.nolanbaker.pgmodernized.util;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.compat.sable.SableUtils;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;

/**
 * Carries a block-laid wire entity (a conduit run, a Cat6 route, a pulled conductor) with the
 * Sable body it was laid on. Power Grid's hanging wires do this for themselves by re-projecting
 * their ends every tick; a block wire never moves on its own, and merely telling Sable to track it
 * did not move it either, so this remembers where the run was laid in the body's own coordinates
 * and puts the entity at that spot's current world position every tick, on both sides. The
 * body's rotation is not followed, only its position: a trolley on a straight runway does not turn.
 */
public final class BodyRide {
    @Nullable
    private Vec3 origin;
    private boolean tracked;

    /** Call every tick, and once right after the entity is created so its recorded block position is the world one. */
    public void tick(BlockWireEntity entity) {
        var level = entity.level();
        if(origin == null) {
            if(SableCompanion.INSTANCE.getContaining(level, entity.position()) == null)
                return;
            origin = entity.position();
        }
        var subLevel = SableCompanion.INSTANCE.getContaining(level, origin);
        if(subLevel == null)
            return; // the body is unloaded or gone: stay where we are
        var world = SableCompanion.INSTANCE.projectOutOfSubLevel(level, origin);
        if(world == null)
            return;
        if(world.distanceToSqr(entity.position()) > 1e-8) {
            entity.setPos(world);
            entity.bakeBoundingBoxes();
        }
        if(!tracked) {
            entity.setOldPosAndRot();
            SableUtils.PROXY.setSubLevelTracking(entity, subLevel);
            tracked = true;
        }
    }

    public boolean riding() {
        return origin != null;
    }

    public void save(CompoundTag tag) {
        if(origin == null)
            return;
        tag.putDouble("PlotX", origin.x);
        tag.putDouble("PlotY", origin.y);
        tag.putDouble("PlotZ", origin.z);
    }

    public void load(CompoundTag tag) {
        origin = tag.contains("PlotX") ? new Vec3(tag.getDouble("PlotX"), tag.getDouble("PlotY"), tag.getDouble("PlotZ")) : null;
        tracked = false;
    }
}
