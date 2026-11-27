package com.nolanbaker.pgmodernized.compat.adchimneys;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Advanced Chimneys' smoke, for the smoke detectors, when the mod is there. It keeps, per level,
 * the outlets its smoke pours from: a chimney top, or the block over a furnace, stove or fire
 * with no chimney. Smoke rises from an outlet and collects under the ceiling, so a detector
 * trips on an outlet below it, within {@link #RADIUS} blocks sideways and {@link #RISE} down,
 * when nothing solid stands between the outlet and the detector's height: a fire on the floor
 * of a tall room reaches the detector on its ceiling. Read by reflection, so nothing here needs
 * the mod to build; any failure switches the hook off for the session.
 */
public final class ChimneySmoke {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "adchimneys";
    public static final int RADIUS = 6, RISE = 16;

    private static Boolean present;
    private static Method getData, getSmokeLocations, getCombinedSmoke, getAmount;
    private static Field smokeOutlets;

    private ChimneySmoke() {}

    private static boolean ready() {
        if(present != null)
            return present;
        present = false;
        if(!ModList.get().isLoaded(MOD_ID))
            return false;
        try {
            var worldData = Class.forName("com.endertech.minecraft.mods.adchimneys.world.WorldData");
            var locations = Class.forName("com.endertech.minecraft.mods.adchimneys.world.SmokeLocations");
            var outlet = Class.forName("com.endertech.minecraft.mods.adchimneys.world.SmokeLocations$SmokeOutlet");
            var smoke = Class.forName("com.endertech.minecraft.mods.adchimneys.smoke.Smoke");
            getData = worldData.getMethod("getData", LevelAccessor.class);
            getSmokeLocations = worldData.getMethod("getSmokeLocations");
            smokeOutlets = locations.getDeclaredField("smokeOutlets");
            smokeOutlets.setAccessible(true);
            getCombinedSmoke = outlet.getDeclaredMethod("getCombinedSmoke");
            getCombinedSmoke.setAccessible(true);
            getAmount = smoke.getMethod("getAmount");
            present = true;
        } catch(ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Advanced Chimneys is loaded but its smoke could not be read; smoke detectors will not see it", e);
        }
        return present;
    }

    /** Whether chimney smoke pours out below the detector, near enough and with a clear rise to its height. Server side. */
    public static boolean smokeBelow(Level level, BlockPos detector) {
        if(level.isClientSide || !ready())
            return false;
        try {
            var data = getData.invoke(null, level);
            var locations = data == null ? null : getSmokeLocations.invoke(data);
            if(!(locations instanceof Object holder) || !(smokeOutlets.get(holder) instanceof Map<?, ?> outlets))
                return false;
            for(var entry : outlets.entrySet()) {
                if(!(entry.getKey() instanceof BlockPos pos))
                    continue;
                int dx = pos.getX() - detector.getX(), dz = pos.getZ() - detector.getZ(), dy = detector.getY() - pos.getY();
                if(dx * dx + dz * dz > RADIUS * RADIUS || dy < 0 || dy > RISE)
                    continue;
                var smoke = getCombinedSmoke.invoke(entry.getValue());
                if(smoke == null || !(getAmount.invoke(smoke) instanceof Number amount) || amount.floatValue() <= 0)
                    continue;
                if(rises(level, pos, detector.getY()))
                    return true;
            }
        } catch(ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Reading Advanced Chimneys smoke failed; smoke detectors will not see it from now on", e);
            present = false;
        }
        return false;
    }

    /** Nothing solid in the column from the outlet up to the given height: the smoke gets there. */
    private static boolean rises(Level level, BlockPos outlet, int toY) {
        var cursor = outlet.mutable();
        for(int y = outlet.getY(); y < toY; ++y) {
            cursor.setY(y);
            if(level.getBlockState(cursor).isCollisionShapeFullBlock(level, cursor))
                return false;
        }
        return true;
    }
}
