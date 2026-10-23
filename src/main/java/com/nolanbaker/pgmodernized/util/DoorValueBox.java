package com.nolanbaker.pgmodernized.util;

import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * A value box on the door of a wall cabinet that faces {@code HORIZONTAL_FACING}: {@code x} and
 * {@code y} in sixteenths in the cabinet's north frame (+x is the viewer's left), {@code door}
 * the door's distance in sixteenths from the back of the block.
 */
public class DoorValueBox extends CenteredSideValueBoxTransform {
    private final double x, y, door;
    private final float scale;

    public DoorValueBox(double x, double y, double door, float scale) {
        super((state, dir) -> state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && dir == state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        this.x = x;
        this.y = y;
        this.door = door;
        this.scale = scale;
    }

    @Override
    protected Vec3 getSouthLocation() {
        return new Vec3(x / 16.0, y / 16.0, door / 16.0);
    }

    @Override
    public float getScale() {
        return scale;
    }
}
