package com.nolanbaker.pgmodernized.util;

import net.minecraft.core.BlockPos;

/**
 * A block entity that answers Power Grid's electrical questions on behalf of another block (a
 * transformer's filler cells, a panel's extensions): its terminals are the head's, moved into its
 * own cell, and its circuit is the head's. Wires landed on it are keyed by its own position, so
 * anything looking for the head's connections must accept positions that delegate to the head.
 */
public interface IElectricDelegate {
    BlockPos headPos();
}
