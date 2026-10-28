package com.nolanbaker.pgmodernized.conduit.gang;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import net.minecraft.network.chat.Component;
import org.patryk3211.powergrid.utility.Lang;

/**
 * What goes in a gang: nothing, a blank insert, a switch of some kind, a button or a receptacle.
 * Each device owns up to four named points in the splice screen; the wiring between them is the
 * device's own and follows its toggle.
 */
public enum GangDevice {
    BLANK("blank", "Blank Insert"),
    SWITCH("switch", "Single-Pole Switch", "line", "load"),
    THREE_WAY("switch_3way", "3-Way Switch", "common", "traveller_a", "traveller_b"),
    FOUR_WAY("switch_4way", "4-Way Switch", "in_a", "in_b", "out_a", "out_b"),
    BUTTON("button", "Push Button", "line", "load"),
    RECEPTACLE("receptacle", "Duplex Receptacle", "line", "neutral"),
    /** On an air handler's 24 V: closes R to Y for cooling, R to W for heat, R to G for the fan, reading the room the nearest air handler keeps. */
    THERMOSTAT("thermostat", "Thermostat", "tstat_r", "tstat_y", "tstat_w", "tstat_g"),
    /** On the fire alarm panel's initiating loop: pulled by hand, reset with a wrench. */
    PULL_STATION("pull_station", "Pull Station", "loop_plus", "loop_minus"),
    /** On the fire alarm panel's notification loop: sounds and flashes from sixteen volts, flashes from eight. */
    HORN_STROBE("horn_strobe", "Horn Strobe", "loop_plus", "loop_minus"),
    /** On the initiating loop: a dry contact across its two sense points, normally open or normally closed, trips it. */
    MONITOR_MODULE("monitor_module", "Monitor Module", "loop_plus", "loop_minus", "contact_a", "contact_b"),
    /** On the initiating loop, three gangs wide: the panel's lights and display, with Silence and Reset. */
    ANNUNCIATOR("annunciator", "Fire Annunciator", "loop_plus", "loop_minus"),
    /** A gang a wide device covers past its first; never an item. */
    SPAN("span", "Covered");

    public static final int MAX_POINTS = 4;
    private static final int YELLOW = 0xC8A000, WHITE = 0xF0F0F0;

    private final String id;
    private final String label;
    private final String[] points;

    GangDevice(String id, String label, String... points) {
        this.id = id;
        this.label = label;
        this.points = points;
    }

    public String id() {
        return id;
    }

    /** The item's English name; the generators and lang file mirror it. */
    public String label() {
        return label;
    }

    public int points() {
        return points.length;
    }

    public String pointKey(int k) {
        return points[k];
    }

    public Component pointName(int k) {
        return Lang.builder().translate("gang.point." + points[k]).component();
    }

    /** NEC colours by role: the hot sequence for switched legs, white for a neutral. */
    public int pointColor(int k) {
        var key = points[k];
        if(key.equals("neutral"))
            return WHITE;
        if(key.equals("loop_plus"))
            return com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlock.LOOP_PLUS_RGB;
        if(key.equals("loop_minus"))
            return com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlock.LOOP_MINUS_RGB;
        if(key.startsWith("contact_"))
            return YELLOW;
        switch(key) {
            case "tstat_r": return 0xC62828;
            case "tstat_y": return 0xE0C020;
            case "tstat_w": return WHITE;
            case "tstat_g": return 0x2E8B3A;
            default: break;
        }
        return switch(k) {
            case 0 -> ConductorColors.hotRgb(0);
            case 1 -> ConductorColors.hotRgb(1);
            case 2 -> ConductorColors.hotRgb(2);
            default -> YELLOW;
        };
    }

    /** Gangs it takes, side by side; the first holds the device and its points, the rest are {@link #SPAN}. */
    public int width() {
        return this == ANNUNCIATOR ? 3 : 1;
    }

    /** Whether there is an item of it. */
    public boolean isItem() {
        return this != SPAN;
    }

    /** A click flips it. */
    public boolean toggles() {
        return this == SWITCH || this == THREE_WAY || this == FOUR_WAY || this == BUTTON || this == THERMOSTAT;
    }

    /** Closed only while pressed. */
    public boolean momentary() {
        return this == BUTTON;
    }

    /** A detector or pull station's contact across the initiating loop, with its supervisory resistor. */
    public boolean initiating() {
        return this == PULL_STATION || this == MONITOR_MODULE;
    }

    public static GangDevice byId(String id) {
        for(var device : values())
            if(device.id.equals(id))
                return device;
        return BLANK;
    }
}
