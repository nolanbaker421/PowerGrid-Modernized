package com.nolanbaker.pgmodernized.registry;

import com.nolanbaker.pgmodernized.client.GearCellRenderer;
import com.nolanbaker.pgmodernized.device.gear.GearCellBlockEntity;
import com.nolanbaker.pgmodernized.device.busduct.BusDuctBlockEntity;
import com.nolanbaker.pgmodernized.device.busduct.BusDuctFeedBlockEntity;
import com.nolanbaker.pgmodernized.device.busduct.BusDuctPlugInBlockEntity;
import com.nolanbaker.pgmodernized.device.busduct.BusDuctPlugInBreakerBlockEntity;
import com.nolanbaker.pgmodernized.device.capacitor.CapacitorBankBlockEntity;
import com.nolanbaker.pgmodernized.device.rectifier.RectifierBlockEntity;
import com.nolanbaker.pgmodernized.radio.AntennaBlockEntity;
import com.nolanbaker.pgmodernized.radio.CoaxHangerBlockEntity;
import com.nolanbaker.pgmodernized.radio.CollectorBlockEntity;
import com.nolanbaker.pgmodernized.radio.RadioBlockEntity;
import com.nolanbaker.pgmodernized.device.helm.RadioBaseBlockEntity;
import com.nolanbaker.pgmodernized.client.StationRenderer;
import com.nolanbaker.pgmodernized.device.controls.StationBlockEntity;
import com.nolanbaker.pgmodernized.client.HmiRenderer;
import com.nolanbaker.pgmodernized.device.hmi.HmiBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.ControlsExtensionBlockEntity;
import com.nolanbaker.pgmodernized.device.camlock.CamLockBoxBlockEntity;
import com.nolanbaker.pgmodernized.device.rangefinder.RangefinderBlockEntity;
import com.nolanbaker.pgmodernized.device.helm.HelmBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.client.ControlsCabinetRenderer;
import com.tterrag.registrate.builders.BlockEntityBuilder.BlockEntityFactory;
import com.nolanbaker.pgmodernized.rack.PinionBlockEntity;
import com.nolanbaker.pgmodernized.client.RailCollectorRenderer;
import com.nolanbaker.pgmodernized.client.PinionRenderer;
import com.nolanbaker.pgmodernized.rail.RailCollectorBlockEntity;
import com.nolanbaker.pgmodernized.rail.RailFeedBlockEntity;
import com.nolanbaker.pgmodernized.chain.CableChainAnchorBlockEntity;
import com.nolanbaker.pgmodernized.device.transformer.TapActuatorBlockEntity;
import com.nolanbaker.pgmodernized.device.breaker.PanelExtensionBlockEntity;
import com.nolanbaker.pgmodernized.conduit.PullBoxBlockEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSwitchBlockEntity;
import com.nolanbaker.pgmodernized.client.AntennaRenderer;
import com.nolanbaker.pgmodernized.client.BreakerPanelRenderer;
import com.nolanbaker.pgmodernized.client.SwitchgearRenderer;
import com.nolanbaker.pgmodernized.device.breaker.SwitchgearBlockEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitBoxBlockEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitSocketBlockEntity;
import com.nolanbaker.pgmodernized.device.analogio.AnalogIOBlockEntity;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlockEntity;
import com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.computer.ComputerBlockEntityFactories;
import com.nolanbaker.pgmodernized.device.meter.ClampMeterBlockEntity;
import com.nolanbaker.pgmodernized.device.meter.LineAmmeterBlockEntity;
import com.nolanbaker.pgmodernized.device.meter.LineVoltmeterBlockEntity;
import org.patryk3211.powergrid.kinetics.base.HalfShaftVisual;
import org.patryk3211.powergrid.kinetics.motor.ElectricMotorRenderer;
import com.nolanbaker.pgmodernized.device.transformer.TransformerBlockEntity;
import com.nolanbaker.pgmodernized.device.transformer.TransformerFillerBlockEntity;
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
                    .validBlocks(ModBlocks.VFD, ModBlocks.VFD_8KV)
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

    public static final BlockEntityEntry<com.nolanbaker.pgmodernized.device.controls.RedstonePortBlockEntity> REDSTONE_PORT =
            REGISTRATE.blockEntity("redstone_port", com.nolanbaker.pgmodernized.device.controls.RedstonePortBlockEntity::new)
                    .validBlock(ModBlocks.REDSTONE_PORT)
                    .register();

    public static final BlockEntityEntry<NetworkSwitchBlockEntity> NETWORK_SWITCH =
            REGISTRATE.blockEntity("network_switch", ComputerBlockEntityFactories.networkSwitch())
                    .validBlock(ModBlocks.NETWORK_SWITCH)
                    .register();

    public static final BlockEntityEntry<BreakerPanelBlockEntity> BREAKER_PANEL =
            REGISTRATE.blockEntity("breaker_panel", BreakerPanelBlockEntity::new)
                    .validBlocks(ModBlocks.BREAKER_PANEL_200, ModBlocks.BREAKER_PANEL_400, ModBlocks.BREAKER_PANEL_800,
                            ModBlocks.BREAKER_PANEL_200_2P, ModBlocks.BREAKER_PANEL_400_2P, ModBlocks.BREAKER_PANEL_400_3P, ModBlocks.BREAKER_PANEL_800_3P,
                            ModBlocks.BREAKER_PANEL_800_2P, ModBlocks.BREAKER_PANEL_200_3P)
                    .renderer(() -> BreakerPanelRenderer::new)
                    .register();

    public static final BlockEntityEntry<SwitchgearBlockEntity> SWITCHGEAR =
            REGISTRATE.blockEntity("switchgear", SwitchgearBlockEntity::new)
                    .validBlock(ModBlocks.SWITCHGEAR)
                    .renderer(() -> SwitchgearRenderer::new)
                    .register();

    public static final BlockEntityEntry<ConduitBoxBlockEntity> CONDUIT_BOX =
            REGISTRATE.blockEntity("conduit_box", ConduitBoxBlockEntity::new)
                    .validBlocks(ModBlocks.CONDUIT_BOX, ModBlocks.CEILING_TILE_CONDUIT_BOX)
                    .register();

    public static final BlockEntityEntry<ConduitSocketBlockEntity> CONDUIT_SOCKET =
            REGISTRATE.blockEntity("conduit_socket", ConduitSocketBlockEntity::new)
                    .validBlock(ModBlocks.CONDUIT_SOCKET)
                    .register();

    public static final BlockEntityEntry<PullBoxBlockEntity> PULL_BOX =
            REGISTRATE.blockEntity("pull_box", PullBoxBlockEntity::new)
                    .validBlocks(ModBlocks.PULL_BOX, ModBlocks.TERMINAL_CABINET)
                    .register();

    public static final BlockEntityEntry<ConduitSwitchBlockEntity> CONDUIT_SWITCH =
            REGISTRATE.blockEntity("conduit_switch", ConduitSwitchBlockEntity::new)
                    .validBlock(ModBlocks.CONDUIT_SWITCH)
                    .register();

    public static final BlockEntityEntry<com.nolanbaker.pgmodernized.conduit.DisconnectBlockEntity> DISCONNECT =
            REGISTRATE.blockEntity("disconnect", com.nolanbaker.pgmodernized.conduit.DisconnectBlockEntity::new)
                    .validBlock(ModBlocks.DISCONNECT)
                    .register();

    public static final BlockEntityEntry<ControlsExtensionBlockEntity> CONTROLS_EXTENSION =
            REGISTRATE.blockEntity("controls_extension", ControlsExtensionBlockEntity::new)
                    .validBlock(ModBlocks.CONTROLS_EXTENSION)
                    .register();

    public static final BlockEntityEntry<ControlsCabinetBlockEntity> CONTROLS_CABINET =
            REGISTRATE.blockEntity("controls_cabinet", (BlockEntityFactory<ControlsCabinetBlockEntity>) (type, pos, state) -> ControlsCabinetBlockEntity.FACTORY.create(type, pos, state))
                    .validBlock(ModBlocks.CONTROLS_CABINET)
                    .renderer(() -> ControlsCabinetRenderer::new)
                    .register();

    public static final BlockEntityEntry<CtCabinetBlockEntity> CT_CABINET =
            REGISTRATE.blockEntity("ct_cabinet", ComputerBlockEntityFactories.ctCabinet())
                    .validBlock(ModBlocks.CT_CABINET)
                    .register();

    @SuppressWarnings("unchecked")
    public static final BlockEntityEntry<TransformerBlockEntity> TRANSFORMER =
            REGISTRATE.blockEntity("transformer", ComputerBlockEntityFactories.transformer())
                    .validBlocks(ModBlocks.TRANSFORMERS.values().toArray(BlockEntry[]::new))
                    .register();

    public static final BlockEntityEntry<TapActuatorBlockEntity> TAP_ACTUATOR =
            REGISTRATE.blockEntity("tap_actuator", TapActuatorBlockEntity::new)
                    .visual(() -> HalfShaftVisual::new)
                    .validBlock(ModBlocks.TAP_ACTUATOR)
                    .renderer(() -> ElectricMotorRenderer::new)
                    .register();

    public static final BlockEntityEntry<TransformerFillerBlockEntity> TRANSFORMER_FILLER =
            REGISTRATE.blockEntity("transformer_filler", TransformerFillerBlockEntity::new)
                    .validBlock(ModBlocks.TRANSFORMER_FILLER)
                    .register();

    public static final BlockEntityEntry<PanelExtensionBlockEntity> PANEL_EXTENSION =
            REGISTRATE.blockEntity("panel_extension", PanelExtensionBlockEntity::new)
                    .validBlock(ModBlocks.PANEL_EXTENSION)
                    .register();

    public static final BlockEntityEntry<CamLockBoxBlockEntity> CAM_LOCK_BOX =
            REGISTRATE.blockEntity("cam_lock_box", CamLockBoxBlockEntity::new)
                    .validBlocks(ModBlocks.CAM_LOCK_BOX_100, ModBlocks.CAM_LOCK_BOX_400)
                    .register();

    public static final BlockEntityEntry<RangefinderBlockEntity> RANGEFINDER =
            REGISTRATE.blockEntity("rangefinder", (BlockEntityFactory<RangefinderBlockEntity>) (type, pos, state) -> RangefinderBlockEntity.FACTORY.create(type, pos, state))
                    .validBlock(ModBlocks.RANGEFINDER)
                    .register();

    public static final BlockEntityEntry<StationBlockEntity> STATION =
            REGISTRATE.blockEntity("control_station", StationBlockEntity::new)
                    .validBlock(ModBlocks.STATION)
                    .renderer(() -> StationRenderer::new)
                    .register();

    public static final BlockEntityEntry<HmiBlockEntity> HMI =
            REGISTRATE.blockEntity("hmi_panel", HmiBlockEntity::new)
                    .validBlock(ModBlocks.HMI)
                    .renderer(() -> HmiRenderer::new)
                    .register();

    public static final BlockEntityEntry<RadioBaseBlockEntity> RADIO_BASE =
            REGISTRATE.blockEntity("radio_base", (BlockEntityFactory<RadioBaseBlockEntity>) (type, pos, state) -> RadioBaseBlockEntity.FACTORY.create(type, pos, state))
                    .validBlock(ModBlocks.RADIO_BASE)
                    .register();

    public static final BlockEntityEntry<HelmBlockEntity> HELM =
            REGISTRATE.blockEntity("helm", (BlockEntityFactory<HelmBlockEntity>) (type, pos, state) -> HelmBlockEntity.FACTORY.create(type, pos, state))
                    .validBlock(ModBlocks.HELM)
                    .register();

    public static final BlockEntityEntry<PinionBlockEntity> PINION =
            REGISTRATE.blockEntity("pinion", (BlockEntityFactory<PinionBlockEntity>) (type, pos, state) -> PinionBlockEntity.FACTORY.create(type, pos, state))
                    .validBlock(ModBlocks.PINION)
                    .renderer(() -> PinionRenderer::new)
                    .register();

    public static final BlockEntityEntry<CableChainAnchorBlockEntity> CABLE_CHAIN_ANCHOR =
            REGISTRATE.blockEntity("cable_chain_anchor", ComputerBlockEntityFactories.cableChainAnchor())
                    .validBlock(ModBlocks.CABLE_CHAIN_ANCHOR)
                    .register();

    public static final BlockEntityEntry<RailFeedBlockEntity> RAIL_FEED =
            REGISTRATE.blockEntity("rail_feed", ComputerBlockEntityFactories.railFeed())
                    .validBlock(ModBlocks.RAIL_FEED)
                    .register();

    public static final BlockEntityEntry<RailCollectorBlockEntity> RAIL_COLLECTOR =
            REGISTRATE.blockEntity("rail_collector", ComputerBlockEntityFactories.railCollector())
                    .validBlock(ModBlocks.RAIL_COLLECTOR)
                    .renderer(() -> RailCollectorRenderer::new)
                    .register();

    public static final BlockEntityEntry<BusDuctBlockEntity> BUS_DUCT =
            REGISTRATE.blockEntity("bus_duct", BusDuctBlockEntity::new)
                    .validBlock(ModBlocks.BUS_DUCT)
                    .register();

    public static final BlockEntityEntry<BusDuctFeedBlockEntity> BUS_DUCT_FEED =
            REGISTRATE.blockEntity("bus_duct_feed", BusDuctFeedBlockEntity::new)
                    .validBlock(ModBlocks.BUS_DUCT_FEED)
                    .register();

    public static final BlockEntityEntry<BusDuctPlugInBlockEntity> BUS_DUCT_PLUG_IN =
            REGISTRATE.blockEntity("bus_duct_plug_in", BusDuctPlugInBlockEntity::new)
                    .validBlock(ModBlocks.BUS_DUCT_PLUG_IN)
                    .register();

    public static final BlockEntityEntry<BusDuctPlugInBreakerBlockEntity> BUS_DUCT_PLUG_IN_BREAKER =
            REGISTRATE.blockEntity("bus_duct_plug_in_breaker", BusDuctPlugInBreakerBlockEntity::new)
                    .validBlock(ModBlocks.BUS_DUCT_PLUG_IN_BREAKER)
                    .register();

    public static final BlockEntityEntry<RectifierBlockEntity> RECTIFIER =
            REGISTRATE.blockEntity("rectifier", RectifierBlockEntity::new)
                    .validBlock(ModBlocks.RECTIFIER)
                    .register();

    public static final BlockEntityEntry<CapacitorBankBlockEntity> CAPACITOR_BANK =
            REGISTRATE.blockEntity("capacitor_bank", CapacitorBankBlockEntity::new)
                    .validBlock(ModBlocks.CAPACITOR_BANK)
                    .register();

    public static final BlockEntityEntry<RadioBlockEntity> RADIO =
            REGISTRATE.blockEntity("radio", ComputerBlockEntityFactories.radio())
                    .validBlock(ModBlocks.RADIO)
                    .register();

    public static final BlockEntityEntry<CollectorBlockEntity> COLLECTOR =
            REGISTRATE.blockEntity("collector", ComputerBlockEntityFactories.collector())
                    .validBlock(ModBlocks.COLLECTOR)
                    .register();

    public static final BlockEntityEntry<AntennaBlockEntity> ANTENNA =
            REGISTRATE.blockEntity("antenna", AntennaBlockEntity::new)
                    .validBlocks(ModBlocks.DISH_ANTENNA, ModBlocks.SECTOR_ANTENNA, ModBlocks.OMNI_ANTENNA)
                    .renderer(() -> AntennaRenderer::new)
                    .register();

    public static final BlockEntityEntry<CoaxHangerBlockEntity> COAX_HANGER =
            REGISTRATE.blockEntity("coax_hanger", ComputerBlockEntityFactories.coaxHanger())
                    .validBlock(ModBlocks.COAX_HANGER)
                    .register();

    public static final BlockEntityEntry<GearCellBlockEntity> GEAR_CELL =
            REGISTRATE.blockEntity("gear_cell", ComputerBlockEntityFactories.gearCell())
                    .validBlock(ModBlocks.GEAR_CELL)
                    .renderer(() -> GearCellRenderer::new)
                    .register();

    public static void register() {}
}
