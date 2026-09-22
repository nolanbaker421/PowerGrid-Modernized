package com.nolanbaker.pgmodernized.device.computer;

import com.tterrag.registrate.builders.BlockEntityBuilder.BlockEntityFactory;
import com.nolanbaker.pgmodernized.device.analogio.AnalogIOBlockEntity;
import com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.meter.ClampMeterBlockEntity;
import com.nolanbaker.pgmodernized.device.meter.LineAmmeterBlockEntity;
import com.nolanbaker.pgmodernized.device.meter.LineVoltmeterBlockEntity;
import com.nolanbaker.pgmodernized.device.vfd.VfdBlockEntity;
import com.nolanbaker.pgmodernized.network.NetworkJackBlockEntity;
import com.nolanbaker.pgmodernized.network.NetworkSwitchBlockEntity;

/**
 * Factories for the computer-facing block entities. Platform code may replace these
 * (before any block entity is created) with subclasses that attach directly to a
 * computer mod's network, e.g. OpenComputers cables.
 */
public final class ComputerBlockEntityFactories {
    public static BlockEntityFactory<AnalogIOBlockEntity> ANALOG_IO = AnalogIOBlockEntity::new;
    public static BlockEntityFactory<VfdBlockEntity> VFD = VfdBlockEntity::new;
    public static BlockEntityFactory<ClampMeterBlockEntity> CLAMP_METER = ClampMeterBlockEntity::new;
    public static BlockEntityFactory<LineVoltmeterBlockEntity> LINE_VOLTMETER = LineVoltmeterBlockEntity::new;
    public static BlockEntityFactory<LineAmmeterBlockEntity> LINE_AMMETER = LineAmmeterBlockEntity::new;
    public static BlockEntityFactory<NetworkJackBlockEntity> NETWORK_JACK = NetworkJackBlockEntity::new;
    public static BlockEntityFactory<NetworkSwitchBlockEntity> NETWORK_SWITCH = NetworkSwitchBlockEntity::new;
    public static BlockEntityFactory<CtCabinetBlockEntity> CT_CABINET = CtCabinetBlockEntity::new;

    public static BlockEntityFactory<NetworkSwitchBlockEntity> networkSwitch() {
        return (type, pos, state) -> NETWORK_SWITCH.create(type, pos, state);
    }

    public static BlockEntityFactory<LineAmmeterBlockEntity> lineAmmeter() {
        return (type, pos, state) -> LINE_AMMETER.create(type, pos, state);
    }

    public static BlockEntityFactory<NetworkJackBlockEntity> networkJack() {
        return (type, pos, state) -> NETWORK_JACK.create(type, pos, state);
    }

    public static BlockEntityFactory<CtCabinetBlockEntity> ctCabinet() {
        return (type, pos, state) -> CT_CABINET.create(type, pos, state);
    }

    private ComputerBlockEntityFactories() {}

    public static BlockEntityFactory<AnalogIOBlockEntity> analogIO() {
        return (type, pos, state) -> ANALOG_IO.create(type, pos, state);
    }

    public static BlockEntityFactory<VfdBlockEntity> vfd() {
        return (type, pos, state) -> VFD.create(type, pos, state);
    }

    public static BlockEntityFactory<ClampMeterBlockEntity> clampMeter() {
        return (type, pos, state) -> CLAMP_METER.create(type, pos, state);
    }

    public static BlockEntityFactory<LineVoltmeterBlockEntity> lineVoltmeter() {
        return (type, pos, state) -> LINE_VOLTMETER.create(type, pos, state);
    }
}
