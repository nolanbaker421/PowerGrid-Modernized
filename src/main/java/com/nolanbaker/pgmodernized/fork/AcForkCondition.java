package com.nolanbaker.pgmodernized.fork;

import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Data condition {@code {"type": "powergrid_modernized:ac_fork"}}: true when the powergrid-ac fork
 * is the installed Power Grid. The AC-only recipes, advancements and loot tables carry it, so on
 * stock Power Grid they are skipped instead of failing to load against unregistered blocks.
 */
public final class AcForkCondition implements ICondition {
    public static final AcForkCondition INSTANCE = new AcForkCondition();
    public static final MapCodec<AcForkCondition> CODEC = MapCodec.unit(INSTANCE);

    private AcForkCondition() {}

    @Override
    public boolean test(IContext context) {
        return ForkHooks.get().present();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
