package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;

/**
 * Building wire sizes, THHN copper, with the numbers conduit fill is figured from: the insulated
 * conductor's cross-section (NEC Chapter 9 Table 5), its ampacity at 75 °C (Table 310.16) and its
 * DC resistance. Wires from Power Grid or other addons carry no area of their own, so they are
 * taken as the smallest gauge whose ampacity covers their rated current.
 */
public enum WireGauge {
    AWG_14("14awg", "14 AWG", 0.0097, 20, 8.28, 0.04f),
    AWG_12("12awg", "12 AWG", 0.0133, 25, 5.21, 0.045f),
    AWG_10("10awg", "10 AWG", 0.0211, 35, 3.28, 0.05f),
    AWG_8("8awg", "8 AWG", 0.0366, 50, 2.06, 0.06f),
    AWG_6("6awg", "6 AWG", 0.0507, 65, 1.30, 0.07f),
    AWG_4("4awg", "4 AWG", 0.0824, 85, 0.815, 0.08f),
    AWG_2("2awg", "2 AWG", 0.1158, 115, 0.513, 0.09f),
    AWG_1_0("1_0awg", "1/0 AWG", 0.1855, 150, 0.322, 0.10f),
    AWG_2_0("2_0awg", "2/0 AWG", 0.2223, 175, 0.256, 0.11f),
    AWG_4_0("4_0awg", "4/0 AWG", 0.3237, 230, 0.161, 0.12f),
    KCMIL_250("250kcmil", "250 kcmil", 0.3970, 255, 0.136, 0.13f),
    KCMIL_350("350kcmil", "350 kcmil", 0.5242, 310, 0.0971, 0.14f),
    KCMIL_500("500kcmil", "500 kcmil", 0.7073, 380, 0.068, 0.15f);

    private final String id;
    private final String label;
    private final double areaSqIn;
    private final float ampacity;
    private final double ohmsPerKm;
    private final float thickness;

    WireGauge(String id, String label, double areaSqIn, float ampacity, double ohmsPerKm, float thickness) {
        this.id = id;
        this.label = label;
        this.areaSqIn = areaSqIn;
        this.ampacity = ampacity;
        this.ohmsPerKm = ohmsPerKm;
        this.thickness = thickness;
    }

    /** Item id: wire_&lt;id&gt;. */
    public String id() {
        return "wire_" + id;
    }

    public String label() {
        return label;
    }

    /** Cross-section of the insulated conductor, square inches. */
    public double areaSqIn() {
        return areaSqIn;
    }

    /** Continuous current at 75 °C, amperes; the wire burns out past it. */
    public float ampacity() {
        return ampacity;
    }

    public double ohmsPerKm() {
        return ohmsPerKm;
    }

    /** Ohms per metre of one conductor, what one item of wire is. */
    public double ohmsPerMetre() {
        return ohmsPerKm / 1000;
    }

    /** Drawn thickness of a hanging span, blocks. */
    public float thickness() {
        return thickness;
    }

    /** The smallest gauge whose ampacity covers that current, or the largest gauge. */
    public static WireGauge forAmpacity(float amps) {
        for(var gauge : values()) {
            if(gauge.ampacity >= amps)
                return gauge;
        }
        return KCMIL_500;
    }

    /** Conductor area of any wire item: its own gauge, or the gauge its rated current implies. */
    public static double areaOf(Level level, Item item) {
        if(item instanceof BuildingWireItem wire)
            return wire.gauge().areaSqIn();
        var entry = WireRegistry.forItem(level, item);
        return entry == null ? 0 : forAmpacity(entry.maximumCurrent()).areaSqIn();
    }

    /** The gauge shown for any wire item. */
    public static WireGauge of(Level level, Item item) {
        if(item instanceof BuildingWireItem wire)
            return wire.gauge();
        var entry = WireRegistry.forItem(level, item);
        return entry == null ? AWG_14 : forAmpacity(entry.maximumCurrent());
    }
}
