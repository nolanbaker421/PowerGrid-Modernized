package com.nolanbaker.pgmodernized.device.gear;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The four sides of a slot in a cell's grid, as the viewer at the door sees them, clockwise from
 * the top. Columns run from the viewer's left, rows from the top.
 */
public enum GearSide {
    UP(0, -1),
    RIGHT(1, 0),
    DOWN(0, 1),
    LEFT(-1, 0);

    /** Grid step to the slot on this side. */
    public final int dc, dr;

    GearSide(int dc, int dr) {
        this.dc = dc;
        this.dr = dr;
    }

    public GearSide opposite() {
        return rotated(2);
    }

    /** This side turned clockwise by that many quarter turns (negative for anticlockwise). */
    public GearSide rotated(int quarterTurns) {
        return values()[Math.floorMod(ordinal() + quarterTurns, 4)];
    }

    public int bit() {
        return 1 << ordinal();
    }

    public boolean vertical() {
        return this == UP || this == DOWN;
    }

    /** The world direction of this side on a cell whose door faces {@code facing}: the viewer's left is the door's clockwise side. */
    public Direction world(Direction facing) {
        return switch(this) {
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
            case LEFT -> facing.getClockWise();
            case RIGHT -> facing.getCounterClockWise();
        };
    }

    /** The side of a cell facing {@code facing} that the world direction {@code direction} leaves through, or null for its front and back. */
    @Nullable
    public static GearSide of(Direction direction, Direction facing) {
        for(var side : values()) {
            if(side.world(facing) == direction)
                return side;
        }
        return null;
    }
}
