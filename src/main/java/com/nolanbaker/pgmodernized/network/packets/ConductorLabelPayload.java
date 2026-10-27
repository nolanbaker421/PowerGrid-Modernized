package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Client labels the pulled conductor on a splice host's conductor terminal, and with it every conductor spliced straight through to it; an empty label clears them. */
public record ConductorLabelPayload(BlockPos pos, int terminal, String label) implements CustomPacketPayload {
    public static final int MAX_LENGTH = 32;
    public static final Type<ConductorLabelPayload> TYPE = new Type<>(asResource("conductor_label"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConductorLabelPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConductorLabelPayload::pos,
            ByteBufCodecs.VAR_INT, ConductorLabelPayload::terminal,
            ByteBufCodecs.stringUtf8(MAX_LENGTH * 4), ConductorLabelPayload::label,
            ConductorLabelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConductorLabelPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        if(!(level.getBlockEntity(payload.pos) instanceof ISpliceHost host) || host.splices() == null)
            return;
        var conductor = com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport.conductorAt(host, payload.terminal);
        if(conductor != null)
            com.nolanbaker.pgmodernized.conduit.splice.SpliceSupport.labelChain(level, conductor, payload.label);
    }
}
