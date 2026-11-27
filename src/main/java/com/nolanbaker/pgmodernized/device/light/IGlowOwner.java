package com.nolanbaker.pgmodernized.device.light;

import net.minecraft.core.BlockPos;

/** A lamp that keeps a {@link StripLightGlowBlock} on the floor below it while it is lit: strip lights and designed fixtures. */
public interface IGlowOwner {
    /** Whether this lamp placed the glow at that position and still lights it. */
    boolean ownsGlow(BlockPos glow);
}
