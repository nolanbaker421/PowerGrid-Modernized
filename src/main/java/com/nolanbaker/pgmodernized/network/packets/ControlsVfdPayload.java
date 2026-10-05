package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Client sets a VFD module's drive and range: which drive on the Cat6 it commands, and its minimum and maximum setting. */
public record ControlsVfdPayload(BlockPos pos, int slot, boolean hasTarget, BlockPos target, float min, float max) implements CustomPacketPayload {
    public static final Type<ControlsVfdPayload> TYPE = new Type<>(asResource("controls_vfd"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ControlsVfdPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ControlsVfdPayload::pos,
            ByteBufCodecs.VAR_INT, ControlsVfdPayload::slot,
            ByteBufCodecs.BOOL, ControlsVfdPayload::hasTarget,
            BlockPos.STREAM_CODEC, ControlsVfdPayload::target,
            ByteBufCodecs.FLOAT, ControlsVfdPayload::min,
            ByteBufCodecs.FLOAT, ControlsVfdPayload::max,
            ControlsVfdPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ControlsVfdPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        if(level.getBlockEntity(payload.pos) instanceof ControlsCabinetBlockEntity cabinet)
            cabinet.setVfd(payload.slot, payload.hasTarget ? payload.target : null, payload.min, payload.max);
    }
}
