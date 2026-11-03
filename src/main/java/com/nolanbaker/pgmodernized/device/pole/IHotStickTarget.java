package com.nolanbaker.pgmodernized.device.pole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A block the hot stick works from the ground: a cutout, a switch contact, a recloser. Server side. */
public interface IHotStickTarget {
    void stick(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit);
}
