package com.nolanbaker.pgmodernized.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-only entry points called from common code behind an {@code isClientSide} check. */
public final class ClientHooks {
    private ClientHooks() {}

    /** Opens the splice editor for a conduit box or breaker panel. */
    public static void openSplices(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SpliceScreen(pos));
    }

    /** Opens the typed-setting screen of a load bank. */
    public static void openLoadBank(BlockPos pos) {
        Minecraft.getInstance().setScreen(new LoadBankScreen(pos));
    }
}
