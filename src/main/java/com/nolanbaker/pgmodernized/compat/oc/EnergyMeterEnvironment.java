package com.nolanbaker.pgmodernized.compat.oc;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import org.patryk3211.powergrid.electricity.gauge.EnergyMeterBlockEntity;

public class EnergyMeterEnvironment extends ElectricEnvironment<EnergyMeterBlockEntity> {
    public EnergyMeterEnvironment(EnergyMeterBlockEntity meter) {
        super(meter, "powergrid_energy_meter");
    }

    @Callback(direct = true, doc = "function():number -- Accumulated energy shown on the meter.")
    public Object[] getEnergy(Context context, Arguments args) {
        return result(blockEntity.getEnergy());
    }
}
