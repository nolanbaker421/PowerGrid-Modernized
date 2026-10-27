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
        // The 8 kV regulator: a bigger box with more iron in it, same output stage.
        resistance("vfd_8kv", "output", 0.5);
        thermal("vfd_8kv", 900, 6.0);
        resistance("line_voltmeter", "input", 1e8);
        resistance("line_ammeter", "shunt", 0.005);
        thermal("line_ammeter", 60, 2.0);
        // Breaker panels: contact resistance of the main and of each branch breaker. No thermal
        // model; the breakers themselves are the overcurrent protection.
        for(var spec : PanelSpec.values())
            resistance(spec.id(), "main", 0.002, "branch", 0.005, "bond", 0.001);
        resistance("switchgear", "pole", 0.0005);
        // Switchgear cells: contact resistance of each pole of a breaker, fuse, switch or contactor in a slot.
        resistance("gear_cell", "pole", 0.0005);
        resistance("conduit_switch", "contact", 0.001);
        resistance("conduit_button", "contact", 0.001);
        for(int gangs = 1; gangs <= 4; ++gangs)
            resistance("gang_box_" + gangs, "contact", 0.001);
        resistance("conduit_switch_3way", "contact", 0.001);
        resistance("disconnect", "contact", 0.0005);
        // Conduit boxes: the lamp of a light socket cover, sixty watts at 120 V.
        // A smoke detector's supervisory resistor and alarm contact, and a horn strobe's load, on the 24 V loops.
        resistance("conduit_box", "lamp", 240, "supervisory", 20000, "detector", 1000, "horn", 80);
        resistance("ceiling_tile_conduit_box", "lamp", 240, "supervisory", 20000, "detector", 1000, "horn", 80);
        // The fire alarm panel: a twenty-watt load on its feed, and the source resistance of each loop.
        resistance("fire_alarm_panel", "feed", 720, "loop", 1);
        // Bus duct plug-in: one contact per pole and the neutral through-path.
        resistance("bus_duct_plug_in", "contact", 0.0005);
        resistance("bus_duct_plug_in_breaker", "contact", 0.0005);
        // Rectifier: the rails out to the DC lugs. Capacitor bank: the contact in front of each capacitor.
        resistance("rectifier", "output", 0.002);
        resistance("capacitor_bank", "contact", 0.002);
        // Three-phase pump: one winding set; each stage puts another in parallel. 260 Ω at a power
        // factor of 0.85 is 120 W real at 120 V a phase, which is Power Grid's pump speed cap.
        resistance("three_phase_pump", "winding", 260.0);
        RESISTANCES.put(asResource("three_phase_pump"), 260.0);
        // Three-phase motor: one winding per phase, star connected; thermal limits are its own.
        resistance("three_phase_motor", "winding", 8.0);
        resistance("three_phase_drive", "output", 0.05);
        thermal("three_phase_drive", 600, 5.0);
        // Cam-lock boxes: one contact per pole. Dissipation is sized so about 125 % of the rating on three poles overheats them.
        resistance("cam_lock_box_100", "contact", 0.002);
        thermal("cam_lock_box_100", 95, 3.0);
        resistance("cam_lock_box_400", "contact", 0.0005);
        thermal("cam_lock_box_400", 375, 8.0);
        // FE inverter: the series resistance its power is metered through, and a drive-sized thermal body.
        resistance("fe_inverter", "output", 0.05);
        thermal("fe_inverter", 600, 5.0);
        // Controls cabinet: its relay contacts, and a small thermal body for the power supply.
        resistance("controls_cabinet", "contact", 0.001);
        thermal("controls_cabinet", 200, 3.0);
        // RF connector: the least resistance its loads go down to per phase, so a sagging line cannot be shorted.
        resistance("rf_connector", "load", 0.1);
        // Transformers: winding resistance in series with each secondary leg.
        for(var spec : TransformerSpec.values()) {
            resistance(spec.id(), "winding", 0.02);
            // Full-load losses of a few percent of the rating are the most it can shed; the tank's mass grows with it.
            thermal(spec.id(), Math.max(200, spec.ratedVa() * 0.03), 10 + spec.ratedVa() / 20000.0);
        }
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
