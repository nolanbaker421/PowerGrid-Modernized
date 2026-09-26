package com.nolanbaker.pgmodernized.registry;

import com.nolanbaker.pgmodernized.device.breaker.PanelSpec;
import com.nolanbaker.pgmodernized.device.transformer.TransformerSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.config.ResistanceValues;
import org.patryk3211.powergrid.config.ThermalValues;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/**
 * Electrical constants for this mod's blocks.
 * <p>
 * Power Grid keeps its own values in its server config, but its config transforms refuse blocks from other
 * mods, so we answer the same lookups through the provider hooks it exposes instead. Values match what the
 * blocks used while they lived inside the Power Grid source tree.
 */
public final class ModValues implements ResistanceValues.Provider, ThermalValues.Provider {
    private static final Map<ResourceLocation, Double> RESISTANCES = new HashMap<>();
    private static final Map<ResourceLocation, Double> MAX_POWER = new HashMap<>();
    private static final Map<ResourceLocation, Double> THERMAL_MASS = new HashMap<>();

    static {
        resistance("analog_io_module", "output", 1, "input", 1e6);
        resistance("vfd", "output", 0.5);
        thermal("vfd", 300, 4.0);
        resistance("line_voltmeter", "input", 1e8);
        resistance("line_ammeter", "shunt", 0.005);
        thermal("line_ammeter", 60, 2.0);
        // Breaker panels: contact resistance of the main and of each branch breaker. No thermal
        // model; the breakers themselves are the overcurrent protection.
        for(var spec : PanelSpec.values())
            resistance(spec.id(), "main", 0.002, "branch", 0.005);
        resistance("switchgear", "pole", 0.0005);
        resistance("conduit_switch", "contact", 0.001);
        // Transformers: winding resistance in series with each secondary leg.
        for(var spec : TransformerSpec.values())
            resistance(spec.id(), "winding", 0.02);
    }

    private ModValues() {}

    public static void register() {
        var provider = new ModValues();
        ResistanceValues.register(provider);
        ThermalValues.register(provider);
    }

    /** Arguments come in pairs: terminal suffix, then resistance in ohms. */
    private static void resistance(String block, Object... suffixValuePairs) {
        for(int i = 0; i < suffixValuePairs.length; i += 2) {
            var suffix = (String) suffixValuePairs[i];
            var value = ((Number) suffixValuePairs[i + 1]).doubleValue();
            RESISTANCES.put(asResource(block).withSuffix("." + suffix), value);
        }
    }

    private static void thermal(String block, double maxPowerWatts, double thermalMass) {
        MAX_POWER.put(asResource(block), maxPowerWatts);
        THERMAL_MASS.put(asResource(block), thermalMass);
    }

    @Nullable
    private static DoubleSupplier lookup(Map<ResourceLocation, Double> map, ResourceLocation id) {
        var value = map.get(id);
        return value == null ? null : () -> value;
    }

    private static ResourceLocation idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    @Override
    public @Nullable DoubleSupplier get(Block block) {
        return lookup(RESISTANCES, idOf(block));
    }

    @Override
    public @Nullable DoubleSupplier get(Block block, String suffix) {
        return lookup(RESISTANCES, idOf(block).withSuffix("." + suffix));
    }

    @Override
    public @Nullable DoubleSupplier getPower(Block block) {
        return lookup(MAX_POWER, idOf(block));
    }

    @Override
    public @Nullable DoubleSupplier getMass(Block block) {
        return lookup(THERMAL_MASS, idOf(block));
    }
}
