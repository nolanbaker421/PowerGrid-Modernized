package com.nolanbaker.pgmodernized.registry;

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
import com.nolanbaker.pgmodernized.device.transformer.TransformerBlock;
import com.nolanbaker.pgmodernized.device.transformer.TransformerFillerBlock;
import com.nolanbaker.pgmodernized.device.transformer.TransformerKind;
import com.nolanbaker.pgmodernized.device.transformer.TransformerMount;
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

    public static final BlockEntry<VfdBlock> VFD = REGISTRATE.block("vfd", VfdBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .lang("Variable Frequency Drive")
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

    /** The invisible upper and side cells of the two-block and three-block transformers. */
    public static final BlockEntry<TransformerFillerBlock> TRANSFORMER_FILLER = REGISTRATE.block("transformer_filler", TransformerFillerBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().noLootTable())
            .transform(pickaxeOnly())
            .lang("Transformer")
            .register();

    /** Transformers by mount, then winding kind. */
    public static final Map<TransformerMount, Map<TransformerKind, BlockEntry<TransformerBlock>>> TRANSFORMERS;

    static {
        var byMount = new EnumMap<TransformerMount, Map<TransformerKind, BlockEntry<TransformerBlock>>>(TransformerMount.class);
        for(var mount : TransformerMount.values()) {
            var byKind = new EnumMap<TransformerKind, BlockEntry<TransformerBlock>>(TransformerKind.class);
            for(var kind : TransformerKind.values()) {
                byKind.put(kind, REGISTRATE.block(TransformerBlock.id(mount, kind), p -> new TransformerBlock(p, kind, mount))
                        .blockstate(NonNullBiConsumer.noop())
                        .initialProperties(SharedProperties::softMetal)
                        .properties(p -> p.noOcclusion())
                        .transform(pickaxeOnly())
                        .lang(mount.label() + " Transformer (" + kind.label() + ")")
                        .item()
                            .model(NonNullBiConsumer.noop())
                            .build()
                        .register());
            }
            byMount.put(mount, Collections.unmodifiableMap(byKind));
        }
        TRANSFORMERS = Collections.unmodifiableMap(byMount);
    }

    public static BlockEntry<TransformerBlock> transformer(TransformerMount mount, TransformerKind kind) {
        return TRANSFORMERS.get(mount).get(kind);
    }

    // ---- conduit system ----

    /** The one fitting: ends and splices conduit runs. */
    public static final BlockEntry<ConduitBoxBlock> CONDUIT_BOX = REGISTRATE.block("conduit_box", ConduitBoxBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Conduit Box")
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

    public static void register() {}
}
