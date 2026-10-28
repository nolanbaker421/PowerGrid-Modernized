package com.nolanbaker.pgmodernized.conduit.gang;

/**
 * A weatherproof cover fitted over a device in a gang: the die-cast lid that goes on an outdoor
 * box. The receptacle cover is a hood on a hinge at the top, shut over the receptacle and swung
 * up while a cord is plugged in. The switch cover is a plate with a lever through it that works
 * the toggle underneath, up for on and down for off, so the switch is still flipped by a click.
 */
public enum GangCover {
    RECEPTACLE("weatherproof_receptacle_cover", "Weatherproof Receptacle Cover"),
    SWITCH("weatherproof_switch_cover", "Weatherproof Switch Cover");

    private final String id;
    private final String label;

    GangCover(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    /** The item's English name; the generators and lang file mirror it. */
    public String label() {
        return label;
    }

    /** The devices it goes over: a receptacle, or any toggle switch. */
    public boolean fits(GangDevice device) {
        return switch(this) {
            case RECEPTACLE -> device == GangDevice.RECEPTACLE;
            case SWITCH -> device == GangDevice.SWITCH || device == GangDevice.THREE_WAY || device == GangDevice.FOUR_WAY;
        };
    }

    public static GangCover byId(String id) {
        for(var cover : values())
            if(cover.id.equals(id))
                return cover;
        return null;
    }
}
