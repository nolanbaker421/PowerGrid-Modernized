package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.device.hmi.HmiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Client sends an HMI panel's whole layout, as the editor drew it. */
public record HmiLayoutPayload(BlockPos pos, CompoundTag layout) implements CustomPacketPayload {
    public static final Type<HmiLayoutPayload> TYPE = new Type<>(asResource("hmi_layout"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HmiLayoutPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HmiLayoutPayload::pos,
            ByteBufCodecs.COMPOUND_TAG, HmiLayoutPayload::layout,
            HmiLayoutPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HmiLayoutPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        if(level.getBlockEntity(payload.pos) instanceof HmiBlockEntity hmi)
            hmi.setLayout(payload.layout);
    }
}
