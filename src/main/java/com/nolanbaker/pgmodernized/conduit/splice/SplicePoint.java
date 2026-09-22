package com.nolanbaker.pgmodernized.conduit.splice;

import net.minecraft.network.chat.Component;

/** A fixed landing point in a splice host: a cover terminal on a conduit box, or Line, Neutral and a circuit on a panel. */
public record SplicePoint(int terminal, Component name, int rgb) {}
