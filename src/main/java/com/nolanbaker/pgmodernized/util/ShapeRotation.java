package com.nolanbaker.pgmodernized.util;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Catnip's directional shaper turns a north-facing shape the opposite way for up and down from how
 * Power Grid's terminal boxes turn, so a block facing the floor gets its outline on the ceiling.
 * Power Grid's helper takes an explicit up shape; this builds it with the terminal rotation.
 */
public final class ShapeRotation {
    private ShapeRotation() {}

    /** The north-facing shape rotated to face up, matching {@code TerminalBoundingBox.rotateAroundX(-90)}: (x, y, z) to (x, 1 - z, y). */
    public static VoxelShape northToUp(VoxelShape north) {
        var result = Shapes.empty();
        for(AABB box : north.toAabbs())
            result = Shapes.or(result, Shapes.create(box.minX, 1 - box.maxZ, box.minY, box.maxX, 1 - box.minZ, box.maxY));
        return result;
    }
}
