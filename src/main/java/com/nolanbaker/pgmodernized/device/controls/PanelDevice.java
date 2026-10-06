package com.nolanbaker.pgmodernized.device.controls;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** What mounts in the cabinet door's six cells, and which way it is wired. */
public enum PanelDevice {
    /** Latching mushroom head: press to stop everything, press again to twist it out. */
    E_STOP("estop_button", true, false),
    /** Stays where you put it. */
    TOGGLE("toggle_button", true, false),
    /** Closed for half a second when pressed. */
    MOMENTARY("momentary_button", true, false),
    /** Three positions, left, centre, right: left closes its input, right closes the next one. */
    SELECTOR("selector_switch", true, false),
    /** A pilot light on an output. */
    LED("pilot_light", false, true),
    /** Four digits the computer writes. */
    DISPLAY("number_display", false, false),
    /** A knob from 0 to 100 % in steps of ten, the speed reference of a VFD module. */
    DIAL("speed_dial", true, false),
    /** A momentary button with an up arrow on its cap. */
    UP("up_button", true, false),
    /** A momentary button with a down arrow on its cap. */
    DOWN("down_button", true, false),
    /** A horn on an output: sounds while the output is on. */
    BUZZER("buzzer", false, true);

    private final String id;
    private final boolean input;
    private final boolean output;

    PanelDevice(String id, boolean input, boolean output) {
        this.id = id;
        this.input = input;
        this.output = output;
    }

    public String id() {
        return id;
    }

    /** Wired to an input module's channel. */
    public boolean isInput() {
        return input;
    }

    /** Wired to an output module's channel. */
    public boolean isOutput() {
        return output;
    }

    public String key() {
        return name().toLowerCase();
    }

    public static PanelDevice fromOrdinal(int ordinal) {
        var values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    /** The item for a panel device. */
    public static class DeviceItem extends Item {
        private final PanelDevice device;

        public DeviceItem(Properties properties, PanelDevice device) {
            super(properties);
            this.device = device;
        }

        public PanelDevice device() {
            return device;
        }

        public static PanelDevice of(ItemStack stack) {
            return stack.getItem() instanceof DeviceItem item ? item.device : null;
        }
    }
}
