package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.device.helm.HelmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/**
 * The client at a helm reports one key going down or up, by name (w, space, left.shift,
 * mouse.left). An empty name lets go of the helm.
 */
public record HelmKeyPayload(BlockPos pos, String key, boolean down) implements CustomPacketPayload {
    public static final Type<HelmKeyPayload> TYPE = new Type<>(asResource("helm_key"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HelmKeyPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HelmKeyPayload::pos,
            ByteBufCodecs.stringUtf8(64), HelmKeyPayload::key,
            ByteBufCodecs.BOOL, HelmKeyPayload::down,
            HelmKeyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HelmKeyPayload payload, IPayloadContext context) {
        if(!(context.player() instanceof ServerPlayer player))
            return;
        var level = player.level();
        if(level.isLoaded(payload.pos) && level.getBlockEntity(payload.pos) instanceof HelmBlockEntity helm)
            helm.press(player, payload.key, payload.down);
    }
}
