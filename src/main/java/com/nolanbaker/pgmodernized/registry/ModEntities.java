package com.nolanbaker.pgmodernized.registry;

import com.nolanbaker.pgmodernized.rail.RailCat6Entity;
import com.nolanbaker.pgmodernized.rail.RailPickupEntity;
import com.nolanbaker.pgmodernized.client.CableChainRenderer;
import com.nolanbaker.pgmodernized.chain.ChainCat6Entity;
import com.nolanbaker.pgmodernized.chain.CableChainEntity;
import com.nolanbaker.pgmodernized.PgmConfig;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.SpawnPlacementTypes;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.nolanbaker.pgmodernized.mob.PushBroomEntity;
import com.nolanbaker.pgmodernized.inspector.ElectricalInspectorEntity;
import com.nolanbaker.pgmodernized.client.PushBroomRenderer;
import com.nolanbaker.pgmodernized.client.ElectricalInspectorRenderer;
import com.nolanbaker.pgmodernized.client.NoopEntityRenderer;
import com.nolanbaker.pgmodernized.conduit.ConductorEntity;
import com.nolanbaker.pgmodernized.conduit.ConduitRunEntity;
import com.nolanbaker.pgmodernized.device.breaker.BusLinkEntity;
import com.nolanbaker.pgmodernized.network.Cat6BlockWireEntity;
import com.nolanbaker.pgmodernized.network.Cat6WireEntity;
import com.tterrag.registrate.util.entry.EntityEntry;
import net.minecraft.world.entity.MobCategory;
import org.patryk3211.powergrid.electricity.wire.BlockWireRenderer;
import org.patryk3211.powergrid.electricity.wire.HangingWireRenderer;

import static com.nolanbaker.pgmodernized.PowerGridModernized.REGISTRATE;

public class ModEntities {
    /** Cat6 strung between two jacks. */
    public static final EntityEntry<Cat6WireEntity> CAT6_CABLE =
            REGISTRATE.entity("cat6_cable", Cat6WireEntity::new, MobCategory.MISC)
                    .renderer(() -> HangingWireRenderer::new)
                    .register();

    /** Cat6 laid along block surfaces. */
    public static final EntityEntry<Cat6BlockWireEntity> CAT6_BLOCK_CABLE =
            REGISTRATE.entity("cat6_block_cable", Cat6BlockWireEntity::new, MobCategory.MISC)
                    .renderer(() -> BlockWireRenderer::new)
                    .register();

    /** Invisible conductor inside conduit or a flex whip. */
    public static final EntityEntry<ConductorEntity> CONDUCTOR =
            REGISTRATE.entity("conductor", ConductorEntity::new, MobCategory.MISC)
                    .renderer(() -> NoopEntityRenderer::new)
                    .register();

    /** Visible conduit run laid along block surfaces between two box hubs. */
    public static final EntityEntry<ConduitRunEntity> CONDUIT_RUN =
            REGISTRATE.entity("conduit_run", ConduitRunEntity::new, MobCategory.MISC)
                    .renderer(() -> BlockWireRenderer::new)
                    .register();

    /** Invisible bus bar between two adjacent switchgear sections. */
    public static final EntityEntry<BusLinkEntity> BUS_LINK =
            REGISTRATE.entity("bus_link", BusLinkEntity::new, MobCategory.MISC)
                    .renderer(() -> NoopEntityRenderer::new)
                    .register();

    /** The cable chain: a 4" conduit run between two anchors that re-lays itself toward a moving body. */
    public static final EntityEntry<CableChainEntity> CABLE_CHAIN =
            REGISTRATE.entity("cable_chain", CableChainEntity::new, MobCategory.MISC)
                    .renderer(() -> CableChainRenderer::new)
                    .register();

    /** The hidden Cat6 pair a cable chain carries between its anchors' jacks. */
    public static final EntityEntry<ChainCat6Entity> CHAIN_CAT6 =
            REGISTRATE.entity("chain_cat6", ChainCat6Entity::new, MobCategory.MISC)
                    .renderer(() -> NoopEntityRenderer::new)
                    .register();

    /** A collector shoe on a rail bar: hidden hanging wire from collector stud to feed stud. */
    public static final EntityEntry<RailPickupEntity> RAIL_PICKUP =
            REGISTRATE.entity("rail_pickup", RailPickupEntity::new, MobCategory.MISC)
                    .renderer(() -> NoopEntityRenderer::new)
                    .register();

    /** The rail's data channel for one collector: hidden Cat6 from collector jack to feed jack. */
    public static final EntityEntry<RailCat6Entity> RAIL_CAT6 =
            REGISTRATE.entity("rail_cat6", RailCat6Entity::new, MobCategory.MISC)
                    .renderer(() -> NoopEntityRenderer::new)
                    .register();

    /** The electrical inspector: sent after players who wire without a license. */
    public static final EntityEntry<ElectricalInspectorEntity> ELECTRICAL_INSPECTOR =
            REGISTRATE.entity("electrical_inspector", ElectricalInspectorEntity::new, MobCategory.MISC)
                    .properties(b -> b.sized(0.6f, 1.95f).clientTrackingRange(10))
                    .attributes(ElectricalInspectorEntity::attributes)
                    .renderer(() -> ElectricalInspectorRenderer::new)
                    .lang("Electrical Inspector")
                    .spawnEgg(0xF27A14, 0xF5F5F5)
                        .model(NonNullBiConsumer.noop())
                        .lang("Electrical Inspector Spawn Egg")
                        .build()
                    .register();

    /** The push broom: a night-time monster of the overworld (spawns via data/neoforge/biome_modifier/push_broom.json). */
    public static final EntityEntry<PushBroomEntity> PUSH_BROOM =
            REGISTRATE.entity("push_broom", PushBroomEntity::new, MobCategory.MONSTER)
                    .properties(b -> b.sized(0.8f, 1.9f).clientTrackingRange(8))
                    .attributes(PushBroomEntity::attributes)
                    .renderer(() -> PushBroomRenderer::new)
                    .spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            (type, level, spawnType, pos, random) -> PgmConfig.PUSH_BROOM_SPAWNS.get()
                                    && Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random),
                            RegisterSpawnPlacementsEvent.Operation.REPLACE)
                    .lang("Push Broom")
                    .spawnEgg(0xAA7D46, 0xDEBE5A)
                        .model(NonNullBiConsumer.noop())
                        .lang("Push Broom Spawn Egg")
                        .build()
                    .register();

    public static void register() {}
}
