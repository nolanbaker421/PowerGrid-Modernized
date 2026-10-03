package com.nolanbaker.pgmodernized.compat.cc;

import com.nolanbaker.pgmodernized.device.rangefinder.RangefinderBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Rangefinder readings and range limit. */
public class RangefinderPeripheral implements IPeripheral {
    private final RangefinderBlockEntity rangefinder;

    public RangefinderPeripheral(RangefinderBlockEntity rangefinder) {
        this.rangefinder = rangefinder;
    }

    /** Distance to the target in blocks, or -1 with nothing in range. */
    @LuaFunction
    public double getDistance() {
        return rangefinder.distance();
    }

    /** What the beam hit: none, block, body (a physics body) or entity. */
    @LuaFunction
    public String getTarget() {
        return switch(rangefinder.hitKind()) {
            case RangefinderBlockEntity.HIT_BLOCK -> "block";
            case RangefinderBlockEntity.HIT_BODY -> "body";
            case RangefinderBlockEntity.HIT_ENTITY -> "entity";
            default -> "none";
        };
    }

    @LuaFunction
    public double getRange() {
        return rangefinder.range();
    }

    @LuaFunction(mainThread = true)
    public double setRange(double blocks) {
        rangefinder.setRange(blocks);
        return rangefinder.range();
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_rangefinder";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof RangefinderPeripheral that && this.rangefinder == that.rangefinder;
    }

    @Override
    public int hashCode() {
        return rangefinder.hashCode();
    }
}
