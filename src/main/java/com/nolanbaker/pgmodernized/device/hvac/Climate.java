package com.nolanbaker.pgmodernized.device.hvac;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The outdoor temperature, from the biome: vanilla keeps one per biome on a scale where snow
 * lies below 0.15 and a desert is 2.0. Read as Fahrenheit, plains come out at seventy-one, a
 * forest in the high sixties, a taiga in the forties, a desert in the mid hundred-and-twenties.
 * With no weather mod that is all the air there is, and what the thermostats work against.
 */
public final class Climate {
    private Climate() {}

    public static float outdoorF(Level level, BlockPos pos) {
        float t = level.getBiome(pos).value().getBaseTemperature();
        return 35 + 45 * t;
    }

    /** One decimal, for readings. */
    public static String degrees(float f) {
        return String.format("%.1f °F", f);
    }
}
