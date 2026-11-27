package com.nolanbaker.pgmodernized.conduit.fire;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.AABB;

/**
 * What a detector senses, shared by the conduit box covers and the gang devices: fire and lava
 * within range, and for smoke, lit campfires, anything burning, and Advanced Chimneys' smoke
 * pouring out below it when that mod is there.
 */
public final class FireSensing {
    private FireSensing() {}

    public static boolean nearby(Level level, BlockPos at, int range, boolean burningThings) {
        var from = at.offset(-range, -range, -range);
        var to = at.offset(range, range, range);
        for(var pos : BlockPos.betweenClosed(from, to)) {
            var state = level.getBlockState(pos);
            if(state.is(BlockTags.FIRE) || state.is(Blocks.LAVA)
                    || (burningThings && state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)))
                return true;
        }
        if(!burningThings)
            return false;
        var box = new AABB(at).inflate(range);
        if(!level.getEntitiesOfClass(Entity.class, box, Entity::isOnFire).isEmpty())
            return true;
        return com.nolanbaker.pgmodernized.compat.adchimneys.ChimneySmoke.smokeBelow(level, at);
    }
}
