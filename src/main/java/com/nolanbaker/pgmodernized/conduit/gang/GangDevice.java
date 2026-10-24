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
    RECEPTACLE("receptacle", "Duplex Receptacle", "line", "neutral");

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
        return switch(k) {
            case 0 -> ConductorColors.hotRgb(0);
            case 1 -> ConductorColors.hotRgb(1);
            case 2 -> ConductorColors.hotRgb(2);
            default -> YELLOW;
        };
    }

    /** A click flips it. */
    public boolean toggles() {
        return this == SWITCH || this == THREE_WAY || this == FOUR_WAY || this == BUTTON;
    }

    /** Closed only while pressed. */
    public boolean momentary() {
        return this == BUTTON;
    }

    public static GangDevice byId(String id) {
        for(var device : values())
            if(device.id.equals(id))
                return device;
        return BLANK;
    }
}
