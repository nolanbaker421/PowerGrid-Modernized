package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.ponder.PgmPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;

/** Registers the mod's Ponder plugin. Called from the client-only part of mod construction. */
public final class PonderSetup {
    private PonderSetup() {}

    public static void init() {
        PonderIndex.addPlugin(new PgmPonderPlugin());
    }
}
