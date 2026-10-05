package com.nolanbaker.pgmodernized.device.controls;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** What clips onto the cabinet's DIN rail. */
public enum ControlModule {
    /** Makes the control bus live from the cabinet's line and neutral; nothing works without one. */
    POWER_SUPPLY("power_supply_module", 0),
    /** Eight inputs the panel's buttons and switches are wired to. */
    DIGITAL_IN("digital_in_module", 8),
    /** Eight outputs the panel's lights are wired to, set by the computer. */
    DIGITAL_OUT("digital_out_module", 8),
    /** Two dry contacts on the cabinet's terminals, closed by the computer. */
    RELAY("relay_module", 2);

    private final String id;
    private final int channels;

    ControlModule(String id, int channels) {
        this.id = id;
        this.channels = channels;
    }

    public String id() {
        return id;
    }

    public int channels() {
        return channels;
    }

    /** Lower-case key the computers see. */
    public String key() {
        return name().toLowerCase();
    }

    public static ControlModule fromOrdinal(int ordinal) {
        var values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    /** The item for a module. */
    public static class ModuleItem extends Item {
        private final ControlModule module;

        public ModuleItem(Properties properties, ControlModule module) {
            super(properties);
            this.module = module;
        }

        public ControlModule module() {
            return module;
        }

        public static ControlModule of(ItemStack stack) {
            return stack.getItem() instanceof ModuleItem item ? item.module : null;
        }
    }
}
