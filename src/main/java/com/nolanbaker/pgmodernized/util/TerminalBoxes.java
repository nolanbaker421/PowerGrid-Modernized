package com.nolanbaker.pgmodernized.util;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * Power Grid's {@code TerminalBoundingBox(Component, AABB)} takes its box in blocks and scales it
 * by sixteen; the mod's geometry is kept in sixteenths, so a terminal built from one of those boxes
 * goes through here, which hands the six pixel values over as they are.
 */
public final class TerminalBoxes {
    private TerminalBoxes() {}

    /** A terminal from a box in sixteenths. */
    public static TerminalBoundingBox px(Component name, AABB px) {
        return new TerminalBoundingBox(name, px.minX, px.minY, px.minZ, px.maxX, px.maxY, px.maxZ);
    }
}
