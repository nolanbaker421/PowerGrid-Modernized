package com.nolanbaker.pgmodernized.registry;

import com.nolanbaker.pgmodernized.client.BreakerPanelRenderer;
import com.nolanbaker.pgmodernized.conduit.ConduitBoxBlockEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSocketBlockEntity;
import com.nolanbaker.pgmodernized.device.analogio.AnalogIOBlockEntity;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlockEntity;
import com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.computer.ComputerBlockEntityFactories;
import com.nolanbaker.pgmodernized.device.meter.ClampMeterBlockEntity;
import com.nolanbaker.pgmodernized.device.meter.LineAmmeterBlockEntity;
import com.nolanbaker.pgmodernized.device.meter.LineVoltmeterBlockEntity;
import com.nolanbaker.pgmodernized.device.drive.ThreePhaseDriveBlockEntity;
import com.nolanbaker.pgmodernized.device.motor.ThreePhaseMotorBlockEntity;
import org.patryk3211.powergrid.kinetics.base.HalfShaftVisual;
import org.patryk3211.powergrid.kinetics.motor.ElectricMotorRenderer;
import com.nolanbaker.pgmodernized.device.transformer.TransformerBlockEntity;
import com.nolanbaker.pgmodernized.device.vfd.VfdBlockEntity;
import com.nolanbaker.pgmodernized.network.NetworkJackBlockEntity;
import com.nolanbaker.pgmodernized.network.NetworkSwitchBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;

import static com.nolanbaker.pgmodernized.PowerGridModernized.REGISTRATE;

public class ModBlockEntities {
    public static final BlockEntityEntry<AnalogIOBlockEntity> ANALOG_IO =
            REGISTRATE.blockEntity("analog_io_module", ComputerBlockEntityFactories.analogIO())
                    .validBlock(ModBlocks.ANALOG_IO)
                    .register();

    public static final BlockEntityEntry<VfdBlockEntity> VFD =
            REGISTRATE.blockEntity("vfd", ComputerBlockEntityFactories.vfd())
                    .validBlock(ModBlocks.VFD)
                    .register();

    public static final BlockEntityEntry<ClampMeterBlockEntity> CLAMP_METER =
            REGISTRATE.blockEntity("clamp_meter", ComputerBlockEntityFactories.clampMeter())
                    .validBlock(ModBlocks.CLAMP_METER)
                    .register();

    public static final BlockEntityEntry<LineVoltmeterBlockEntity> LINE_VOLTMETER =
            REGISTRATE.blockEntity("line_voltmeter", ComputerBlockEntityFactories.lineVoltmeter())
                    .validBlock(ModBlocks.LINE_VOLTMETER)
                    .register();

    public static final BlockEntityEntry<LineAmmeterBlockEntity> LINE_AMMETER =
            REGISTRATE.blockEntity("line_ammeter", ComputerBlockEntityFactories.lineAmmeter())
                    .validBlock(ModBlocks.LINE_AMMETER)
                    .register();

    public static final BlockEntityEntry<NetworkJackBlockEntity> NETWORK_JACK =
            REGISTRATE.blockEntity("network_jack", ComputerBlockEntityFactories.networkJack())
                    .validBlock(ModBlocks.NETWORK_JACK)
                    .register();

    public static final BlockEntityEntry<NetworkSwitchBlockEntity> NETWORK_SWITCH =
            REGISTRATE.blockEntity("network_switch", ComputerBlockEntityFactories.networkSwitch())
                    .validBlock(ModBlocks.NETWORK_SWITCH)
                    .register();

    public static final BlockEntityEntry<BreakerPanelBlockEntity> BREAKER_PANEL =
            REGISTRATE.blockEntity("breaker_panel", BreakerPanelBlockEntity::new)
                    .validBlocks(ModBlocks.BREAKER_PANEL_200, ModBlocks.BREAKER_PANEL_400, ModBlocks.BREAKER_PANEL_800,
                            ModBlocks.BREAKER_PANEL_200_2P, ModBlocks.BREAKER_PANEL_400_2P, ModBlocks.BREAKER_PANEL_400_3P, ModBlocks.BREAKER_PANEL_800_3P)
                    .renderer(() -> BreakerPanelRenderer::new)
                    .register();

    public static final BlockEntityEntry<ConduitBoxBlockEntity> CONDUIT_BOX =
            REGISTRATE.blockEntity("conduit_box", ConduitBoxBlockEntity::new)
                    .validBlock(ModBlocks.CONDUIT_BOX)
                    .register();

    public static final BlockEntityEntry<ConduitSocketBlockEntity> CONDUIT_SOCKET =
            REGISTRATE.blockEntity("conduit_socket", ConduitSocketBlockEntity::new)
                    .validBlock(ModBlocks.CONDUIT_SOCKET)
                    .register();

    public static final BlockEntityEntry<CtCabinetBlockEntity> CT_CABINET =
            REGISTRATE.blockEntity("ct_cabinet", ComputerBlockEntityFactories.ctCabinet())
                    .validBlock(ModBlocks.CT_CABINET)
                    .register();

    @SuppressWarnings("unchecked")
    public static final BlockEntityEntry<TransformerBlockEntity> TRANSFORMER =
            REGISTRATE.blockEntity("transformer", TransformerBlockEntity::new)
                    .validBlocks(ModBlocks.TRANSFORMERS.values().stream().flatMap(m -> m.values().stream()).toArray(BlockEntry[]::new))
                    .register();

    public static final BlockEntityEntry<ThreePhaseMotorBlockEntity> THREE_PHASE_MOTOR =
            REGISTRATE.blockEntity("three_phase_motor", ComputerBlockEntityFactories.threePhaseMotor())
                    .visual(() -> HalfShaftVisual::new)
                    .validBlock(ModBlocks.THREE_PHASE_MOTOR)
                    .renderer(() -> ElectricMotorRenderer::new)
                    .register();

    public static final BlockEntityEntry<ThreePhaseDriveBlockEntity> THREE_PHASE_DRIVE =
            REGISTRATE.blockEntity("three_phase_drive", ComputerBlockEntityFactories.threePhaseDrive())
                    .validBlock(ModBlocks.THREE_PHASE_DRIVE)
                    .register();

    public static void register() {}
}
