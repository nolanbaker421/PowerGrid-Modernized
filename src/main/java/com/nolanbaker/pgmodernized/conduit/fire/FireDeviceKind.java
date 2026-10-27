package com.nolanbaker.pgmodernized.conduit.fire;

import net.minecraft.network.chat.Component;
import org.patryk3211.powergrid.utility.Lang;

/** What a fire alarm device is: the initiating kinds sit on the initiating loop, the horn strobe on the notification loop. */
public enum FireDeviceKind {
    SMOKE("smoke", "SMOKE", true),
    HEAT("heat", "HEAT", true),
    PULL("pull", "PULL", true),
    HORN("horn", "HORN", false);

    private final String id, display;
    private final boolean initiating;

    FireDeviceKind(String id, String display, boolean initiating) {
        this.id = id;
        this.display = display;
        this.initiating = initiating;
    }

    public String id() {
        return id;
    }

    /** The short word the panel's display uses. */
    public String display() {
        return display;
    }

    public boolean initiating() {
        return initiating;
    }

    public Component title() {
        return Lang.builder().translate("fire_alarm.kind." + id).component();
    }

    public static FireDeviceKind byId(String id) {
        for(var kind : values())
            if(kind.id.equals(id))
                return kind;
        return SMOKE;
    }
}
