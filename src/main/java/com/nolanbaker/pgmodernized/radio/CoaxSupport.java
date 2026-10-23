package com.nolanbaker.pgmodernized.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The coax side of a block entity: which coax entities sit in its ports. The entities register themselves. */
public final class CoaxSupport {
    private final BlockEntity owner;
    private final Set<CoaxWireEntity> cables = new HashSet<>();
    private boolean removed;

    public CoaxSupport(BlockEntity owner) {
        this.owner = owner;
    }

    public BlockPos pos() {
        return owner.getBlockPos();
    }

    public boolean isRemoved() {
        return removed;
    }

    public void add(CoaxWireEntity cable) {
        cables.add(cable);
    }

    public void remove(CoaxWireEntity cable) {
        cables.remove(cable);
    }

    public Collection<CoaxWireEntity> cables() {
        return cables;
    }

    public boolean isPortUsed(int port) {
        var pos = pos();
        for(var cable : cables) {
            if(!cable.isRemoved() && cable.connectsTo(pos, port))
                return true;
        }
        return false;
    }

    /** The coax in that port, or null. */
    @Nullable
    public CoaxWireEntity cableIn(int port) {
        var pos = pos();
        for(var cable : cables) {
            if(!cable.isRemoved() && cable.connectsTo(pos, port))
                return cable;
        }
        return null;
    }

    /** The block is gone: every coax on it falls. Server side. */
    public void remove() {
        removed = true;
        var level = owner.getLevel();
        if(level != null && !level.isClientSide) {
            for(var cable : List.copyOf(cables))
                cable.portLost(pos());
        }
        cables.clear();
    }
}
