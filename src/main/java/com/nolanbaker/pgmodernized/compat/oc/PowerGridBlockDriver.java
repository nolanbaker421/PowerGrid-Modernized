package com.nolanbaker.pgmodernized.compat.oc;

import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity;
import org.patryk3211.powergrid.electricity.battery.MultiBlockBatteryEntity;
import org.patryk3211.powergrid.electricity.gauge.CurrentGaugeBlockEntity;
import org.patryk3211.powergrid.electricity.gauge.EnergyMeterBlockEntity;
import org.patryk3211.powergrid.electricity.gauge.PowerGaugeBlockEntity;
import org.patryk3211.powergrid.electricity.gauge.VoltageGaugeBlockEntity;

public class PowerGridBlockDriver implements DriverBlock {
    @Override
    public boolean worksWith(Level level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        // Blocks that are network nodes themselves connect directly and must not also get an adapter environment.
        return be instanceof ElectricBlockEntity && !(be instanceof li.cil.oc.api.network.Environment);
    }

    @Override
    public ManagedEnvironment createEnvironment(Level level, BlockPos pos, Direction side) {
        var be = level.getBlockEntity(pos);
        if(be instanceof VoltageGaugeBlockEntity gauge)
            return new GaugeEnvironment(gauge, "powergrid_voltage_gauge");
        if(be instanceof CurrentGaugeBlockEntity gauge)
            return new GaugeEnvironment(gauge, "powergrid_current_gauge");
        if(be instanceof PowerGaugeBlockEntity gauge)
            return new GaugeEnvironment(gauge, "powergrid_power_gauge");
        if(be instanceof EnergyMeterBlockEntity meter)
            return new EnergyMeterEnvironment(meter);
        if(be instanceof MultiBlockBatteryEntity battery) {
            var controller = battery.getControllerBE();
            return new BatteryEnvironment(controller != null ? controller : battery);
        }
        if(be instanceof BatteryBlockEntity battery)
            return new BatteryEnvironment(battery);
        if(be instanceof ElectricBlockEntity electric)
            return new ElectricEnvironment<>(electric, "powergrid_electric");
        return null;
    }
}
