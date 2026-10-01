package com.nolanbaker.pgmodernized.rack;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import dev.architectury.platform.Platform;

/** Swaps in the body-driving pinion when Sable (Create Aeronautics' physics) is loaded. */
public final class SableHooks {
    private SableHooks() {}

    public static boolean present() {
        return Platform.isModLoaded("sable");
    }

    public static void install() {
        if(!present())
            return;
        PinionBlockEntity.FACTORY = SablePinionBlockEntity::new;
        PowerGridModernized.LOGGER.info("Sable found: the pinion drives physics bodies");
    }
}
