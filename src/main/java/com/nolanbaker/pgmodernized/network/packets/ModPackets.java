package com.nolanbaker.pgmodernized.network.packets;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ModPackets {
    private ModPackets() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SplicePayload.TYPE, SplicePayload.STREAM_CODEC, SplicePayload::handle);
    }
}
