package com.nolanbaker.pgmodernized.network.packets;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ModPackets {
    private ModPackets() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SplicePayload.TYPE, SplicePayload.STREAM_CODEC, SplicePayload::handle);
        registrar.playToServer(LoadBankPayload.TYPE, LoadBankPayload.STREAM_CODEC, LoadBankPayload::handle);
        registrar.playToServer(HelmKeyPayload.TYPE, HelmKeyPayload.STREAM_CODEC, HelmKeyPayload::handle);
        registrar.playToServer(ConductorLabelPayload.TYPE, ConductorLabelPayload.STREAM_CODEC, ConductorLabelPayload::handle);
        registrar.playToServer(ControlsPayload.TYPE, ControlsPayload.STREAM_CODEC, ControlsPayload::handle);
        registrar.playToServer(ControlsVfdPayload.TYPE, ControlsVfdPayload.STREAM_CODEC, ControlsVfdPayload::handle);
        registrar.playToServer(ControlsProgramPayload.TYPE, ControlsProgramPayload.STREAM_CODEC, ControlsProgramPayload::handle);
        registrar.playToClient(HelmStatePayload.TYPE, HelmStatePayload.STREAM_CODEC, HelmStatePayload::handle);
    }
}
