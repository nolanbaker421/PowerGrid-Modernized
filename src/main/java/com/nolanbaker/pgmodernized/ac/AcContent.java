package com.nolanbaker.pgmodernized.ac;

import com.nolanbaker.pgmodernized.ac.client.SynchroscopeRenderer;
import com.nolanbaker.pgmodernized.ac.drive.ThreePhaseDriveBlock;
import com.nolanbaker.pgmodernized.ac.drive.ThreePhaseDriveBlockEntity;
import com.nolanbaker.pgmodernized.ac.motor.ThreePhaseMotorBlock;
import com.nolanbaker.pgmodernized.ac.motor.ThreePhaseMotorBlockEntity;
import com.nolanbaker.pgmodernized.ac.source.CreativeAcSourceBlock;
import com.nolanbaker.pgmodernized.ac.source.CreativeAcSourceBlockEntity;
import com.nolanbaker.pgmodernized.ac.sync.SynchroscopeBlock;
import com.nolanbaker.pgmodernized.ac.sync.SynchroscopeBlockEntity;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.builders.BlockEntityBuilder.BlockEntityFactory;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import org.patryk3211.powergrid.kinetics.base.HalfShaftVisual;
import org.patryk3211.powergrid.kinetics.motor.ElectricMotorRenderer;

import static com.nolanbaker.pgmodernized.PowerGridModernized.REGISTRATE;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;

/**
 * The blocks that only exist with the powergrid-ac fork installed: the three-phase motor and drive,
 * the synchroscope and the creative AC source. Registered from {@link AcHooks#registerContent()},
 * which only runs when the fork is present, so on stock Power Grid none of these classes load.
 * The OpenComputers bridge swaps the factories for its network-node subclasses before any block
 * entity is created, as {@code ComputerBlockEntityFactories} does for the shared devices.
 */
public final class AcContent {
    public static BlockEntityFactory<ThreePhaseMotorBlockEntity> MOTOR_FACTORY = ThreePhaseMotorBlockEntity::new;
    public static BlockEntityFactory<ThreePhaseDriveBlockEntity> DRIVE_FACTORY = ThreePhaseDriveBlockEntity::new;
    public static BlockEntityFactory<SynchroscopeBlockEntity> SYNCHROSCOPE_FACTORY = SynchroscopeBlockEntity::new;

    public static BlockEntry<ThreePhaseMotorBlock> THREE_PHASE_MOTOR;
    public static BlockEntry<ThreePhaseDriveBlock> THREE_PHASE_DRIVE;
    public static BlockEntry<SynchroscopeBlock> SYNCHROSCOPE;
    public static BlockEntry<CreativeAcSourceBlock> CREATIVE_AC_SOURCE;

    public static BlockEntityEntry<ThreePhaseMotorBlockEntity> THREE_PHASE_MOTOR_BE;
    public static BlockEntityEntry<ThreePhaseDriveBlockEntity> THREE_PHASE_DRIVE_BE;
    public static BlockEntityEntry<SynchroscopeBlockEntity> SYNCHROSCOPE_BE;
    public static BlockEntityEntry<CreativeAcSourceBlockEntity> CREATIVE_AC_SOURCE_BE;

    private AcContent() {}

    public static void register() {
        /* Three-phase induction motor: a Create generator whose speed follows the supply frequency. */
        THREE_PHASE_MOTOR = REGISTRATE.block("three_phase_motor", ThreePhaseMotorBlock::new)
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

        /* Three-phase variable frequency drive. */
        THREE_PHASE_DRIVE = REGISTRATE.block("three_phase_drive", ThreePhaseDriveBlock::new)
                .blockstate(NonNullBiConsumer.noop())
                .initialProperties(SharedProperties::softMetal)
                .properties(p -> p.noOcclusion())
                .transform(pickaxeOnly())
                .lang("Three-Phase Drive")
                .item()
                    .model(NonNullBiConsumer.noop())
                    .build()
                .register();

        /* Synchroscope: phase angle and slip of an incoming machine against the bus. */
        SYNCHROSCOPE = REGISTRATE.block("synchroscope", SynchroscopeBlock::new)
                .blockstate(NonNullBiConsumer.noop())
                .initialProperties(SharedProperties::softMetal)
                .properties(p -> p.noOcclusion())
                .transform(pickaxeOnly())
                .lang("Synchroscope")
                .item()
                    .model(NonNullBiConsumer.noop())
                    .build()
                .register();

        /* Creative-only alternating source at a set voltage and frequency. */
        CREATIVE_AC_SOURCE = REGISTRATE.block("creative_ac_source", CreativeAcSourceBlock::new)
                .blockstate(NonNullBiConsumer.noop())
                .initialProperties(SharedProperties::softMetal)
                .properties(p -> p.noOcclusion())
                .transform(pickaxeOnly())
                .lang("Creative AC Source")
                .item()
                    .model(NonNullBiConsumer.noop())
                    .build()
                .register();

        THREE_PHASE_MOTOR_BE = REGISTRATE.blockEntity("three_phase_motor", (BlockEntityFactory<ThreePhaseMotorBlockEntity>) (type, pos, state) -> MOTOR_FACTORY.create(type, pos, state))
                .visual(() -> HalfShaftVisual::new)
                .validBlock(THREE_PHASE_MOTOR)
                .renderer(() -> ElectricMotorRenderer::new)
                .register();

        THREE_PHASE_DRIVE_BE = REGISTRATE.blockEntity("three_phase_drive", (BlockEntityFactory<ThreePhaseDriveBlockEntity>) (type, pos, state) -> DRIVE_FACTORY.create(type, pos, state))
                .validBlock(THREE_PHASE_DRIVE)
                .register();

        SYNCHROSCOPE_BE = REGISTRATE.blockEntity("synchroscope", (BlockEntityFactory<SynchroscopeBlockEntity>) (type, pos, state) -> SYNCHROSCOPE_FACTORY.create(type, pos, state))
                .validBlock(SYNCHROSCOPE)
                .renderer(() -> SynchroscopeRenderer::new)
                .register();

        CREATIVE_AC_SOURCE_BE = REGISTRATE.blockEntity("creative_ac_source", CreativeAcSourceBlockEntity::new)
                .validBlock(CREATIVE_AC_SOURCE)
                .register();
    }

    public static void registerClient(IEventBus modBus) {
        modBus.register(SynchroscopeRenderer.Models.class);
    }
}
