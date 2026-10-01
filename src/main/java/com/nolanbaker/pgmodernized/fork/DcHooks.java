package com.nolanbaker.pgmodernized.fork;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;

/** Stock Power Grid: one sample per tick, no wattmeter, no AC devices. */
public final class DcHooks implements ForkHooks {
    @Override
    public boolean present() {
        return false;
    }

    @Override
    public double rmsVoltage(AbstractElectricWire wire) {
        return Math.abs(wire.potentialDifference());
    }

    @Override
    public double rmsCurrent(AbstractElectricWire wire) {
        return Math.abs(wire.current());
    }

    @Override
    public double meanCurrent(AbstractElectricWire wire) {
        return wire.current();
    }

    @Override
    public double lastRmsCurrent(AbstractElectricWire wire) {
        return Math.abs(wire.current());
    }

    @Override
    public double heatingCurrent(BaseWireEntity wire) {
        return Math.abs(wire.current());
    }

    @Override
    public ElectricWire shunt(IElectricEntity.CircuitBuilder builder, double resistance, AbstractElectricWire sense, IElectricNode in, IElectricNode out) {
        return builder.connect((float) resistance, in, out);
    }

    @Override
    public double drainRealPower(ElectricWire shunt) {
        return Double.NaN;
    }

    @Override
    public void registerContent() {}

    @Override
    public void registerClient(IEventBus modBus) {}

    @Override
    public void registerCC(RegisterCapabilitiesEvent event) {}

    @Override
    public @Nullable Object ccPeripheral(BlockEntity be) {
        return null;
    }

    @Override
    public void registerOC(RegisterCapabilitiesEvent event) {}

    @Override
    public void swapOCFactories() {}
}
