package com.nolanbaker.pgmodernized.util;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * A block whose unit spans more than one cell: the base block carries the model and the wiring,
 * the other cells are transformer fillers that answer for it. Offsets are in world space, from
 * the base's position, for the base's current state.
 */
public interface IFillerBase {
    /** The cells beyond the base, as world offsets from it. */
    List<Vec3i> fillerCells(BlockState baseState);

    /** The part of the unit standing in that cell, rotated for the base's facing, in the cell's own coordinates. */
    VoxelShape fillerShape(BlockState baseState, Vec3i worldOffset);
}
