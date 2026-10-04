package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.client.HelmClientHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Server tells a player they are at this helm now, or no longer. */
public record HelmStatePayload(BlockPos pos, boolean active) implements CustomPacketPayload {
    public static final Type<HelmStatePayload> TYPE = new Type<>(asResource("helm_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HelmStatePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HelmStatePayload::pos,
            ByteBufCodecs.BOOL, HelmStatePayload::active,
            HelmStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client only; the client handler class is never touched on a server. */
    public static void handle(HelmStatePayload payload, IPayloadContext context) {
        HelmClientHandler.onState(payload.pos, payload.active);
    }
}
