package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Client writes a controls cabinet's PLC program. */
public record ControlsProgramPayload(BlockPos pos, String program) implements CustomPacketPayload {
    public static final int MAX_LENGTH = 16_000;
    public static final Type<ControlsProgramPayload> TYPE = new Type<>(asResource("controls_program"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ControlsProgramPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ControlsProgramPayload::pos,
            ByteBufCodecs.stringUtf8(MAX_LENGTH * 4), ControlsProgramPayload::program,
            ControlsProgramPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ControlsProgramPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        if(level.getBlockEntity(payload.pos) instanceof ControlsCabinetBlockEntity cabinet)
            cabinet.setProgram(payload.program.length() > MAX_LENGTH ? payload.program.substring(0, MAX_LENGTH) : payload.program);
    }
}
