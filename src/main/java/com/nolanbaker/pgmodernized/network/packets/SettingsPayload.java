package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.radio.ISettingsHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Client sends the edited settings of a radio, an antenna, a collector or a meter antenna. */
public record SettingsPayload(BlockPos pos, Map<String, String> values) implements CustomPacketPayload {
    public static final Type<SettingsPayload> TYPE = new Type<>(asResource("settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SettingsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SettingsPayload::pos,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8, 16), SettingsPayload::values,
            SettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SettingsPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        if(level.getBlockEntity(payload.pos) instanceof ISettingsHost host) {
            var clean = new HashMap<String, String>();
            for(var entry : payload.values.entrySet()) {
                var value = entry.getValue();
                clean.put(entry.getKey(), value.length() > 32 ? value.substring(0, 32) : value);
            }
            host.applySettings(clean);
        }
    }
}
