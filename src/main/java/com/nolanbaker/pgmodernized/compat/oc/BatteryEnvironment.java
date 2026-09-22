package com.nolanbaker.pgmodernized.compat.oc;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity;

public class BatteryEnvironment extends ElectricEnvironment<BatteryBlockEntity> {
    public BatteryEnvironment(BatteryBlockEntity battery) {
        super(battery, "powergrid_battery");
    }

    @Callback(direct = true, doc = "function():number -- Stored energy.")
    public Object[] getEnergy(Context context, Arguments args) {
        return result(blockEntity.getEnergy());
    }

    @Callback(direct = true, doc = "function():number -- Total capacity.")
    public Object[] getCapacity(Context context, Arguments args) {
        return result(blockEntity.getCapacity());
    }

    @Callback(direct = true, doc = "function():number -- Charge level as a fraction (0-1).")
    public Object[] getChargePercentage(Context context, Arguments args) {
        double capacity = blockEntity.getCapacity();
        return result(capacity > 0 ? blockEntity.getEnergy() / capacity : 0.0);
    }

    @Callback(direct = true, doc = "function():number -- Power (W) flowing into the battery; negative while discharging.")
    public Object[] getPower(Context context, Arguments args) {
        return result((double) blockEntity.calculatePower());
    }
}
