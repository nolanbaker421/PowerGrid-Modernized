package com.nolanbaker.pgmodernized.ac;

import com.nolanbaker.pgmodernized.ac.compat.cc.AcCcBridge;
import com.nolanbaker.pgmodernized.ac.compat.oc.AcOcBridge;
import com.nolanbaker.pgmodernized.fork.ForkHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.special.WattmeterWire;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;

/** The powergrid-ac fork: RMS accessors, the wattmeter element, and the AC-only blocks. Loaded by name from {@link ForkHooks}. */
public final class AcHooks implements ForkHooks {
    public AcHooks() {}

    @Override
    public boolean present() {
        return true;
    }

    @Override
    public double rmsVoltage(AbstractElectricWire wire) {
        return wire.rmsVoltage();
    }

    @Override
    public double rmsCurrent(AbstractElectricWire wire) {
        return wire.rmsCurrent();
    }

    @Override
    public double meanCurrent(AbstractElectricWire wire) {
        return wire.meanCurrent();
    }

    @Override
    public double lastRmsCurrent(AbstractElectricWire wire) {
        return wire.lastRmsCurrent();
    }

    @Override
    public double heatingCurrent(BaseWireEntity wire) {
        return wire.heatingCurrent();
    }

    @Override
    public ElectricWire shunt(IElectricEntity.CircuitBuilder builder, double resistance, AbstractElectricWire sense, IElectricNode in, IElectricNode out) {
        var shunt = new WattmeterWire(resistance, sense, in, out);
        builder.add(shunt);
        return shunt;
    }

    @Override
    public double drainRealPower(ElectricWire shunt) {
        return shunt instanceof WattmeterWire wattmeter ? wattmeter.drainRealPower() : Double.NaN;
    }

    @Override
    public void registerContent() {
        AcContent.register();
    }

    @Override
    public void registerClient(IEventBus modBus) {
        AcContent.registerClient(modBus);
    }

    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        AcContent.registerCapabilities(event);
    }

    @Override
    public void registerCC(RegisterCapabilitiesEvent event) {
        AcCcBridge.registerCapabilities(event);
    }

    @Override
    public @Nullable Object ccPeripheral(BlockEntity be) {
        return AcCcBridge.peripheral(be);
    }

    @Override
    public void registerOC(RegisterCapabilitiesEvent event) {
        AcOcBridge.registerCapabilities(event);
    }

    @Override
    public void swapOCFactories() {
        AcOcBridge.swapFactories();
    }
}
