package com.nolanbaker.pgmodernized.conduit.splice;

import net.minecraft.network.chat.Component;

import java.util.List;

/** A splice host that also has live readings to show at the top of its splice editor. */
public interface ISpliceReadings {
    List<Component> readings();
}
