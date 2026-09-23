package com.nolanbaker.pgmodernized.registry;

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

    public static void register() {}
}
