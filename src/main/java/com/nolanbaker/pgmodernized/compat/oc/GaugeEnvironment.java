package com.nolanbaker.pgmodernized.compat.oc;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import org.patryk3211.powergrid.electricity.gauge.GaugeBlockEntity;

public class GaugeEnvironment extends ElectricEnvironment<GaugeBlockEntity> {
    public GaugeEnvironment(GaugeBlockEntity gauge, String componentName) {
        super(gauge, componentName);
    }

    @Callback(direct = true, doc = "function():number -- Current reading of the gauge in its base unit (V, A or W).")
    public Object[] getValue(Context context, Arguments args) {
        return result((double) blockEntity.getValue());
    }

    @Callback(direct = true, doc = "function():number -- Maximum value of the currently selected range.")
    public Object[] getMaxRange(Context context, Arguments args) {
        return result((double) blockEntity.getMaxValue());
    }

    @Callback(direct = true, doc = "function():number -- Needle position as a fraction of the range (0-1).")
    public Object[] getRangePercentage(Context context, Arguments args) {
        return result((double) blockEntity.getProgress());
    }

    @Callback(direct = true, doc = "function():number -- Voltage (V) measured by this gauge; on a power gauge this is the shunt voltage.")
    public Object[] getVoltage(Context context, Arguments args) {
        return result((double) blockEntity.get("v"));
    }

    @Callback(direct = true, doc = "function():number -- Current (A) measured by this gauge; on a power gauge this is the series current.")
    public Object[] getCurrent(Context context, Arguments args) {
        return result((double) blockEntity.get("i"));
    }
}
