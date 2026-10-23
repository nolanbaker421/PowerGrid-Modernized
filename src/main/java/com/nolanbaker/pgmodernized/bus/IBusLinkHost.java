package com.nolanbaker.pgmodernized.bus;

import net.minecraft.core.BlockPos;

/**
 * A block entity that owns hidden bus-bar links ({@link com.nolanbaker.pgmodernized.device.breaker.BusLinkEntity})
 * and can say whether one should still exist. A link asks both of its ends; either can kill it.
 */
public interface IBusLinkHost {
    /** Whether the link from this block's {@code terminal} to {@code otherTerminal} on the block at {@code other} is still wanted. */
    boolean busLinkValid(int terminal, BlockPos other, int otherTerminal);
}
