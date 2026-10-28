package com.nolanbaker.pgmodernized.registry;

import com.nolanbaker.pgmodernized.conduit.StrutBlock;
import com.nolanbaker.pgmodernized.conduit.gang.GangBoxBlock;
import com.nolanbaker.pgmodernized.conduit.ConduitThreeWayBlock;
import com.nolanbaker.pgmodernized.conduit.ConduitButtonBlock;
import com.nolanbaker.pgmodernized.device.helm.RadioBaseBlock;
import com.nolanbaker.pgmodernized.device.controls.StationBlock;
import com.nolanbaker.pgmodernized.device.hmi.HmiBlock;
import com.nolanbaker.pgmodernized.device.controls.ControlsExtensionBlock;
import com.nolanbaker.pgmodernized.device.camlock.CamLockBoxBlock;
import com.nolanbaker.pgmodernized.device.rangefinder.RangefinderBlock;
import com.nolanbaker.pgmodernized.device.helm.HelmBlock;
import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock;
import com.nolanbaker.pgmodernized.device.busduct.BusDuctBlock;
import com.nolanbaker.pgmodernized.device.busduct.BusDuctFeedBlock;
import com.nolanbaker.pgmodernized.device.busduct.BusDuctPlugInBlock;
import com.nolanbaker.pgmodernized.device.busduct.BusDuctPlugInBreakerBlock;
import com.nolanbaker.pgmodernized.device.capacitor.CapacitorBankBlock;
import com.nolanbaker.pgmodernized.device.rectifier.RectifierBlock;
import com.nolanbaker.pgmodernized.radio.AntennaBlock;
import com.nolanbaker.pgmodernized.radio.AntennaKind;
import com.nolanbaker.pgmodernized.radio.CoaxHangerBlock;
import com.nolanbaker.pgmodernized.radio.CollectorBlock;
import com.nolanbaker.pgmodernized.radio.RadioBlock;
import com.nolanbaker.pgmodernized.device.gear.GearCellBlock;
import com.nolanbaker.pgmodernized.rack.RackBlock;
import com.nolanbaker.pgmodernized.rack.PinionBlockEntity;
import com.nolanbaker.pgmodernized.rack.PinionBlock;
import com.nolanbaker.pgmodernized.rail.RailCollectorBlock;
import com.nolanbaker.pgmodernized.rail.RailFeedBlock;
import com.nolanbaker.pgmodernized.rail.ConductorRailBlock;
import com.nolanbaker.pgmodernized.chain.CableChainAnchorBlock;
import com.nolanbaker.pgmodernized.device.transformer.TapActuatorBlockEntity;
import com.nolanbaker.pgmodernized.device.transformer.TapActuatorBlock;
import com.nolanbaker.pgmodernized.device.breaker.PanelExtensionBlock;
import com.nolanbaker.pgmodernized.conduit.PullBoxBlock;
import com.nolanbaker.pgmodernized.conduit.trough.WireTroughBlock;
import com.nolanbaker.pgmodernized.conduit.trough.WireTroughExtensionBlock;
import com.nolanbaker.pgmodernized.conduit.trough.WireTroughItem;
import com.nolanbaker.pgmodernized.conduit.ConduitSwitchBlock;
import com.nolanbaker.pgmodernized.conduit.DisconnectBlock;
import com.nolanbaker.pgmodernized.conduit.CeilingConduitBoxBlock;
import com.nolanbaker.pgmodernized.conduit.ConduitBoxBlock;
import com.nolanbaker.pgmodernized.conduit.ConduitSocketBlock;
import com.nolanbaker.pgmodernized.device.analogio.AnalogIOBlock;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlock;
import com.nolanbaker.pgmodernized.device.breaker.PanelSpec;
import com.nolanbaker.pgmodernized.device.breaker.SwitchgearBlock;
import com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlock;
import com.nolanbaker.pgmodernized.device.meter.ClampMeterBlock;
import com.nolanbaker.pgmodernized.device.meter.LineAmmeterBlock;
import com.nolanbaker.pgmodernized.device.meter.LineVoltmeterBlock;
import com.simibubi.create.api.stress.BlockStressValues;
import com.nolanbaker.pgmodernized.device.transformer.TransformerBlock;
import com.nolanbaker.pgmodernized.device.transformer.TransformerFillerBlock;
import com.nolanbaker.pgmodernized.device.transformer.TransformerSpec;
import com.nolanbaker.pgmodernized.device.vfd.VfdBlock;
import com.nolanbaker.pgmodernized.network.NetworkJackBlock;
import com.nolanbaker.pgmodernized.network.NetworkSwitchBlock;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static com.nolanbaker.pgmodernized.PowerGridModernized.REGISTRATE;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;

/**
 * Block registrations. Blockstates, models, loot tables, recipes and tags are shipped as plain JSON
 * under src/main/resources rather than generated, so the datagen hooks are no-ops here.
 * Resistances and thermal limits live in {@link ModValues}.
 */
public class ModBlocks {
    public static final BlockEntry<AnalogIOBlock> ANALOG_IO = REGISTRATE.block("analog_io_module", AnalogIOBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .lang("Analog I/O Module")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** The digital voltage regulators: the block id "vfd" is kept so old worlds and scripts still load. */
    public static final BlockEntry<VfdBlock> VFD = REGISTRATE.block("vfd", VfdBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .lang("Digital Voltage Regulator (2 kV)")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static final BlockEntry<VfdBlock> VFD_8KV = REGISTRATE.block("vfd_8kv", p -> new VfdBlock(p, 8000f))
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .lang("Digital Voltage Regulator (8 kV)")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static final BlockEntry<ClampMeterBlock> CLAMP_METER = REGISTRATE.block("clamp_meter", ClampMeterBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .lang("Clamp Meter")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static final BlockEntry<LineVoltmeterBlock> LINE_VOLTMETER = REGISTRATE.block("line_voltmeter", LineVoltmeterBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .lang("Line Voltmeter")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static final BlockEntry<LineAmmeterBlock> LINE_AMMETER = REGISTRATE.block("line_ammeter", LineAmmeterBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .lang("Line Ammeter")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** Laser rangefinder: distance to the first block, body or entity along its facing, on the network and a comparator. */
    public static final BlockEntry<RangefinderBlock> RANGEFINDER = REGISTRATE.block("rangefinder", RangefinderBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Laser Rangefinder")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** Helm: right-click and the keyboard goes to the computers on its Cat6 jack while the view stays free. */
    public static final BlockEntry<StationBlock> STATION = REGISTRATE.block("control_station", StationBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Control Station")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static final BlockEntry<HmiBlock> HMI = REGISTRATE.block("hmi_panel", HmiBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("HMI Panel")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static final BlockEntry<RadioBaseBlock> RADIO_BASE = REGISTRATE.block("radio_base", RadioBaseBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Radio Base")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static final BlockEntry<HelmBlock> HELM = REGISTRATE.block("helm", HelmBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Helm")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static final BlockEntry<NetworkJackBlock> NETWORK_JACK = REGISTRATE.block("network_jack", NetworkJackBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Network Jack")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** A jack that puts out the redstone level a controls cabinet writes to it. */
    public static final BlockEntry<com.nolanbaker.pgmodernized.device.controls.RedstonePortBlock> REDSTONE_PORT =
            REGISTRATE.block("redstone_port", com.nolanbaker.pgmodernized.device.controls.RedstonePortBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Redstone Port")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static final BlockEntry<NetworkSwitchBlock> NETWORK_SWITCH = REGISTRATE.block("network_switch", NetworkSwitchBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Network Switch")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static final BlockEntry<BreakerPanelBlock> BREAKER_PANEL_200 = breakerPanel(PanelSpec.A200, "200 A Breaker Panel");
    public static final BlockEntry<BreakerPanelBlock> BREAKER_PANEL_400 = breakerPanel(PanelSpec.A400, "400 A Breaker Panel");
    public static final BlockEntry<BreakerPanelBlock> BREAKER_PANEL_800 = breakerPanel(PanelSpec.A800, "800 A Breaker Panel");
    public static final BlockEntry<BreakerPanelBlock> BREAKER_PANEL_200_2P = breakerPanel(PanelSpec.SPLIT_200, "200 A Split-Phase Breaker Panel");
    public static final BlockEntry<BreakerPanelBlock> BREAKER_PANEL_400_2P = breakerPanel(PanelSpec.SPLIT_400, "400 A Split-Phase Breaker Panel");
    public static final BlockEntry<BreakerPanelBlock> BREAKER_PANEL_400_3P = breakerPanel(PanelSpec.THREE_400, "400 A Three-Phase Breaker Panel");
    public static final BlockEntry<BreakerPanelBlock> BREAKER_PANEL_800_3P = breakerPanel(PanelSpec.THREE_800, "800 A Three-Phase Breaker Panel");
    public static final BlockEntry<BreakerPanelBlock> BREAKER_PANEL_800_2P = breakerPanel(PanelSpec.SPLIT_800, "800 A Split-Phase Breaker Panel");
    public static final BlockEntry<BreakerPanelBlock> BREAKER_PANEL_200_3P = breakerPanel(PanelSpec.THREE_200, "200 A Three-Phase Breaker Panel");

    /** More rows under a panel, on the same bus, with its own knockouts. */
    public static final BlockEntry<PanelExtensionBlock> PANEL_EXTENSION = REGISTRATE.block("panel_extension", PanelExtensionBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Panel Extension")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    private static BlockEntry<BreakerPanelBlock> breakerPanel(PanelSpec spec, String name) {
        return REGISTRATE.block(spec.id(), p -> new BreakerPanelBlock(p, spec))
                .blockstate(NonNullBiConsumer.noop())
                .initialProperties(SharedProperties::softMetal)
                .properties(p -> p.noOcclusion())
                .transform(pickaxeOnly())
                .lang(name)
                .item()
                    .model(NonNullBiConsumer.noop())
                    .build()
                .register();
    }

    /** One section of a switchgear lineup. */
    public static final BlockEntry<SwitchgearBlock> SWITCHGEAR = REGISTRATE.block("switchgear", SwitchgearBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Switchgear Section")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** Controls cabinet: a DIN rail of modules inside, buttons and lights on the door, one jack on the network. */
    public static final BlockEntry<ControlsExtensionBlock> CONTROLS_EXTENSION = REGISTRATE.block("controls_extension", ControlsExtensionBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Controls Cabinet Extension")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static final BlockEntry<ControlsCabinetBlock> CONTROLS_CABINET = REGISTRATE.block("controls_cabinet", ControlsCabinetBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Controls Cabinet")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static final BlockEntry<CtCabinetBlock> CT_CABINET = REGISTRATE.block("ct_cabinet", CtCabinetBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("CT Cabinet")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** Split-system air conditioning: the outdoor condenser and the indoor air handler, joined by refrigerant lines laid like conduit. */
    public static final BlockEntry<com.nolanbaker.pgmodernized.device.hvac.CondenserBlock> CONDENSER = REGISTRATE.block("condenser", com.nolanbaker.pgmodernized.device.hvac.CondenserBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Condenser")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<com.nolanbaker.pgmodernized.device.hvac.AirHandlerBlock> AIR_HANDLER = REGISTRATE.block("air_handler", com.nolanbaker.pgmodernized.device.hvac.AirHandlerBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Air Handler")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** The invisible cells of a transformer beyond its base block. */
    public static final BlockEntry<TransformerFillerBlock> TRANSFORMER_FILLER = REGISTRATE.block("transformer_filler", TransformerFillerBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().noLootTable())
            .transform(pickaxeOnly())
            .lang("Transformer")
            .register();

    /** One block per nameplate. */
    public static final Map<TransformerSpec, BlockEntry<TransformerBlock>> TRANSFORMERS;

    static {
        var map = new EnumMap<TransformerSpec, BlockEntry<TransformerBlock>>(TransformerSpec.class);
        for(var spec : TransformerSpec.values()) {
            map.put(spec, REGISTRATE.block(spec.id(), p -> new TransformerBlock(p, spec))
                    .blockstate(NonNullBiConsumer.noop())
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.noOcclusion())
                    .transform(pickaxeOnly())
                    .lang(spec.displayName())
                    .item()
                        .model(NonNullBiConsumer.noop())
                        .build()
                    .register());
        }
        TRANSFORMERS = Collections.unmodifiableMap(map);
    }

    /** The tap changer drive: bolts onto a transformer, one turn of its shaft per tap step. */
    public static final BlockEntry<TapActuatorBlock> TAP_ACTUATOR = REGISTRATE.block("tap_actuator", TapActuatorBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> (double) TapActuatorBlockEntity.STRESS))
            .lang("Tap Changer Drive")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** The toothed bar a pinion walks along; a plain block in the world. */
    public static final BlockEntry<RackBlock> RACK = REGISTRATE.block("rack", RackBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Rack")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** A small cogwheel that drives a Create Aeronautics body along a rack. */
    public static final BlockEntry<PinionBlock> PINION = REGISTRATE.block("pinion", PinionBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> (double) PinionBlockEntity.STRESS))
            .lang("Pinion")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** Either end of a cable chain: four studs and a jack. */
    public static final BlockEntry<CableChainAnchorBlock> CABLE_CHAIN_ANCHOR = REGISTRATE.block("cable_chain_anchor", CableChainAnchorBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Cable Chain Anchor")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** Insulated conductor rail: four bars on a bracket, live when a feed box is in the run. */
    public static final BlockEntry<ConductorRailBlock> CONDUCTOR_RAIL = REGISTRATE.block("conductor_rail", ConductorRailBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Conductor Rail")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** Cam-lock connector boxes: five receptacles for ordinary wire and two knockouts, at two ratings. */
    public static final BlockEntry<CamLockBoxBlock> CAM_LOCK_BOX_100 = REGISTRATE.block("cam_lock_box_100", p -> new CamLockBoxBlock(p, 100))
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Cam-Lock Box (100 A)")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<CamLockBoxBlock> CAM_LOCK_BOX_400 = REGISTRATE.block("cam_lock_box_400", p -> new CamLockBoxBlock(p, 400))
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Cam-Lock Box (400 A)")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** The feed box of a rail run. */
    public static final BlockEntry<RailFeedBlock> RAIL_FEED = REGISTRATE.block("rail_feed", RailFeedBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Rail Feed Box")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** The collector trolley that rides a rail. */
    public static final BlockEntry<RailCollectorBlock> RAIL_COLLECTOR = REGISTRATE.block("rail_collector", RailCollectorBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Rail Collector")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    // ---- conduit system ----

    /** The one fitting: ends and splices conduit runs. */
    public static final BlockEntry<ConduitBoxBlock> CONDUIT_BOX = REGISTRATE.block("conduit_box", ConduitBoxBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().lightLevel(ConduitBoxBlock::lightOf))
            .transform(pickaxeOnly())
            .lang("Conduit Box")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** The box fitted on a Power Grid ceiling tile; no item, a tile takes the ordinary box. */
    public static final BlockEntry<CeilingConduitBoxBlock> CEILING_TILE_CONDUIT_BOX = REGISTRATE.block("ceiling_tile_conduit_box", CeilingConduitBoxBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().lightLevel(ConduitBoxBlock::lightOf))
            .transform(pickaxeOnly())
            .lang("Ceiling Tile Conduit Box")
            .register();

    /** The fire alarm control panel: 120 V in, a detector loop and a horn strobe loop out, all through conduit. */
    public static final BlockEntry<com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlock> FIRE_ALARM_PANEL =
            REGISTRATE.block("fire_alarm_panel", com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Fire Alarm Panel")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** Ends one run in a cord socket; the equipment end of a run. */
    public static final BlockEntry<ConduitSocketBlock> CONDUIT_SOCKET = REGISTRATE.block("conduit_socket", ConduitSocketBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Conduit Socket")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** A full-block pull box: ten knockouts that take conduit of any size, spliced inside; links to a panel above or below by itself. */
    public static final BlockEntry<PullBoxBlock> PULL_BOX = REGISTRATE.block("pull_box", PullBoxBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .lang("Pull Box")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** A gutter on the wall that conduit lands on from above, below and both ends; sections to the right join its row. */
    public static final BlockEntry<WireTroughBlock> WIRE_TROUGH = REGISTRATE.block("wire_trough", WireTroughBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Wire Trough")
            .item(WireTroughItem::new)
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** A further section of a trough row; placed by the trough item, never on its own. */
    public static final BlockEntry<WireTroughExtensionBlock> WIRE_TROUGH_EXTENSION = REGISTRATE.block("wire_trough_extension", WireTroughExtensionBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Wire Trough")
            .register();

    /** The pull box with twelve lugs on its door: the place big wire meets big conduit. */
    public static final BlockEntry<PullBoxBlock> TERMINAL_CABINET = REGISTRATE.block("terminal_cabinet", p -> new PullBoxBlock(p, true))
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Terminal Cabinet")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** A light switch on the end of a run: the first two pulled wires land on its poles and the toggle joins them. */
    /** Strut channel: a rail with clamp positions that conduit runs clip to in straight legs. */
    public static final BlockEntry<StrutBlock> STRUT = REGISTRATE.block("strut", StrutBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Strut Channel")
            .item()
            .model(NonNullBiConsumer.noop())
            .build()
            .register();

    /** Device boxes of one to four gangs; the devices are items. */
    public static final BlockEntry<GangBoxBlock> GANG_BOX_1 = gangBox(1);
    public static final BlockEntry<GangBoxBlock> GANG_BOX_2 = gangBox(2);
    public static final BlockEntry<GangBoxBlock> GANG_BOX_3 = gangBox(3);
    public static final BlockEntry<GangBoxBlock> GANG_BOX_4 = gangBox(4);

    private static BlockEntry<GangBoxBlock> gangBox(int gangs) {
        return REGISTRATE.block("gang_box_" + gangs, p -> new GangBoxBlock(p, gangs))
                .blockstate(NonNullBiConsumer.noop())
                .initialProperties(SharedProperties::softMetal)
                .properties(p -> p.noOcclusion())
                .transform(pickaxeOnly())
                .lang(gangs + "-Gang Box")
                .item()
                .model(NonNullBiConsumer.noop())
                .build()
                .register();
    }

    /** A momentary button on a run: the switch that lets go. */
    public static final BlockEntry<ConduitButtonBlock> CONDUIT_BUTTON = REGISTRATE.block("conduit_button", ConduitButtonBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Conduit Button")
            .item()
            .model(NonNullBiConsumer.noop())
            .build()
            .register();

    /** A 3-way switch: common and two travellers. */
    public static final BlockEntry<ConduitThreeWayBlock> CONDUIT_SWITCH_3WAY = REGISTRATE.block("conduit_switch_3way", ConduitThreeWayBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("3-Way Conduit Switch")
            .item()
            .model(NonNullBiConsumer.noop())
            .build()
            .register();

    public static final BlockEntry<ConduitSwitchBlock> CONDUIT_SWITCH = REGISTRATE.block("conduit_switch", ConduitSwitchBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Conduit Switch")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** A three-pole disconnect with a lockable handle, fed by conduit or hanging wire. */
    public static final BlockEntry<DisconnectBlock> DISCONNECT = REGISTRATE.block("disconnect", DisconnectBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Disconnect")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** Bus duct: a segment, the end feed that puts wire on a run, and the plug-in disconnect that taps one. */
    public static final BlockEntry<BusDuctBlock> BUS_DUCT = REGISTRATE.block("bus_duct", BusDuctBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Bus Duct")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<BusDuctFeedBlock> BUS_DUCT_FEED = REGISTRATE.block("bus_duct_feed", BusDuctFeedBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Bus Duct End Feed")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<BusDuctPlugInBlock> BUS_DUCT_PLUG_IN = REGISTRATE.block("bus_duct_plug_in", BusDuctPlugInBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Bus Duct Plug-In Disconnect")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** Custom switchgear: a cell with a 3 x 3 grid of slots for bars, breakers, fuses, switches, contactors and lugs. */
    public static final BlockEntry<BusDuctPlugInBreakerBlock> BUS_DUCT_PLUG_IN_BREAKER = REGISTRATE.block("bus_duct_plug_in_breaker", BusDuctPlugInBreakerBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Bus Duct Plug-In Breaker")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<RectifierBlock> RECTIFIER = REGISTRATE.block("rectifier", RectifierBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Rectifier")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<CapacitorBankBlock> CAPACITOR_BANK = REGISTRATE.block("capacitor_bank", CapacitorBankBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Capacitor Bank")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<RadioBlock> RADIO = REGISTRATE.block("radio", RadioBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Radio")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<CollectorBlock> COLLECTOR = REGISTRATE.block("collector", CollectorBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Meter Collector")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<AntennaBlock> DISH_ANTENNA = REGISTRATE.block("dish_antenna", p -> new AntennaBlock(p, AntennaKind.DISH))
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Dish Antenna")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<AntennaBlock> SECTOR_ANTENNA = REGISTRATE.block("sector_antenna", p -> new AntennaBlock(p, AntennaKind.SECTOR))
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Sector Antenna")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<AntennaBlock> OMNI_ANTENNA = REGISTRATE.block("omni_antenna", p -> new AntennaBlock(p, AntennaKind.OMNI))
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Omni Antenna")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<CoaxHangerBlock> COAX_HANGER = REGISTRATE.block("coax_hanger", CoaxHangerBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Cable Hanger")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();
    public static final BlockEntry<GearCellBlock> GEAR_CELL = REGISTRATE.block("gear_cell", GearCellBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Switchgear Cell")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    public static void register() {}
}
