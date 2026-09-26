package com.nolanbaker.pgmodernized.registry;

import com.nolanbaker.pgmodernized.conduit.PullBoxBlock;
import com.nolanbaker.pgmodernized.conduit.ConduitSwitchBlock;
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
import com.nolanbaker.pgmodernized.device.drive.ThreePhaseDriveBlock;
import com.nolanbaker.pgmodernized.device.motor.ThreePhaseMotorBlock;
import com.nolanbaker.pgmodernized.device.motor.ThreePhaseMotorBlockEntity;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.world.level.block.Blocks;
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
    public static final BlockEntry<BreakerPanelBlock> BREAKER_PANEL_800_2P = breakerPanel(PanelSpec.SPLIT_800, "800 A Split-Phase Breaker Panel");
    public static final BlockEntry<BreakerPanelBlock> BREAKER_PANEL_200_3P = breakerPanel(PanelSpec.THREE_200, "200 A Three-Phase Breaker Panel");

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

    /** Three-phase induction motor: a Create generator whose speed follows the supply frequency. */
    public static final BlockEntry<ThreePhaseMotorBlock> THREE_PHASE_MOTOR = REGISTRATE.block("three_phase_motor", ThreePhaseMotorBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.CAPACITIES.register(block, () -> ThreePhaseMotorBlockEntity.STRESS_CAPACITY))
            .onRegister(BlockStressValues.setGeneratorSpeed(256, true))
            .lang("Three-Phase Motor")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

    /** Three-phase variable frequency drive. */
    public static final BlockEntry<ThreePhaseDriveBlock> THREE_PHASE_DRIVE = REGISTRATE.block("three_phase_drive", ThreePhaseDriveBlock::new)
            .blockstate(NonNullBiConsumer.noop())
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .transform(pickaxeOnly())
            .lang("Three-Phase Drive")
            .item()
                .model(NonNullBiConsumer.noop())
                .build()
            .register();

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

    /** A light switch on the end of a run: the first two pulled wires land on its poles and the toggle joins them. */
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

    public static void register() {}
}
