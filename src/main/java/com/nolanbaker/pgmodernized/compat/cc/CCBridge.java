package com.nolanbaker.pgmodernized.compat.cc;

import com.nolanbaker.pgmodernized.compat.cc.ctcabinet.CtCabinetPeripheral;
import com.nolanbaker.pgmodernized.compat.cc.drive.ThreePhaseDrivePeripheral;
import com.nolanbaker.pgmodernized.compat.cc.motor.ThreePhaseMotorPeripheral;
import com.nolanbaker.pgmodernized.device.drive.ThreePhaseDriveBlockEntity;
import com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlockEntity;
import com.nolanbaker.pgmodernized.compat.cc.analogio.AnalogIOPeripheral;
import com.nolanbaker.pgmodernized.compat.cc.meter.ClampMeterPeripheral;
import com.nolanbaker.pgmodernized.compat.cc.meter.LineAmmeterPeripheral;
import com.nolanbaker.pgmodernized.compat.cc.meter.LineVoltmeterPeripheral;
import com.nolanbaker.pgmodernized.compat.cc.network.CCJackLink;
import com.nolanbaker.pgmodernized.compat.cc.vfd.VfdPeripheral;
import com.nolanbaker.pgmodernized.device.analogio.AnalogIOBlockEntity;
import com.nolanbaker.pgmodernized.device.meter.ClampMeterBlockEntity;
import com.nolanbaker.pgmodernized.device.meter.LineAmmeterBlockEntity;
import com.nolanbaker.pgmodernized.device.meter.LineVoltmeterBlockEntity;
import com.nolanbaker.pgmodernized.device.vfd.VfdBlockEntity;
import com.nolanbaker.pgmodernized.network.JackSupport;
import com.nolanbaker.pgmodernized.registry.ModBlockEntities;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredElementCapability;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/** Everything that touches CC: Tweaked classes. Only referenced when the mod is loaded. */
public class CCBridge {
    /** Mod construction: lets every jack join the wired network. */
    public static void init() {
        JackSupport.registerLinkFactory((be, jack) -> new CCJackLink(be, jack, peripheralFor(be)));
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), ModBlockEntities.ANALOG_IO.get(),
                (be, direction) -> new AnalogIOPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlockEntities.VFD.get(),
                (be, direction) -> new VfdPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlockEntities.CLAMP_METER.get(),
                (be, direction) -> new ClampMeterPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlockEntities.LINE_VOLTMETER.get(),
                (be, direction) -> new LineVoltmeterPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlockEntities.LINE_AMMETER.get(),
                (be, direction) -> new LineAmmeterPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlockEntities.CT_CABINET.get(),
                (be, direction) -> new CtCabinetPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlockEntities.THREE_PHASE_MOTOR.get(),
                (be, direction) -> new ThreePhaseMotorPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlockEntities.THREE_PHASE_DRIVE.get(),
                (be, direction) -> new ThreePhaseDrivePeripheral(be));

        // The standalone jack behaves like a piece of CC cable towards its neighbours.
        // Device jacks deliberately do not: a modem next to a device already sees it as a local peripheral.
        event.registerBlockEntity(WiredElementCapability.get(), ModBlockEntities.NETWORK_JACK.get(),
                (be, direction) -> (WiredElement) be.networkJack().link(CCJackLink.KIND));
        event.registerBlockEntity(WiredElementCapability.get(), ModBlockEntities.NETWORK_SWITCH.get(),
                (be, direction) -> (WiredElement) be.networkJack().link(CCJackLink.KIND));
    }

    @Nullable
    private static IPeripheral peripheralFor(BlockEntity be) {
        if(be instanceof VfdBlockEntity vfd)
            return new VfdPeripheral(vfd);
        if(be instanceof AnalogIOBlockEntity io)
            return new AnalogIOPeripheral(io);
        if(be instanceof ClampMeterBlockEntity meter)
            return new ClampMeterPeripheral(meter);
        if(be instanceof LineVoltmeterBlockEntity meter)
            return new LineVoltmeterPeripheral(meter);
        if(be instanceof LineAmmeterBlockEntity meter)
            return new LineAmmeterPeripheral(meter);
        if(be instanceof CtCabinetBlockEntity cabinet)
            return new CtCabinetPeripheral(cabinet);
        if(be instanceof ThreePhaseDriveBlockEntity drive)
            return new ThreePhaseDrivePeripheral(drive);
        return null;
    }
}
