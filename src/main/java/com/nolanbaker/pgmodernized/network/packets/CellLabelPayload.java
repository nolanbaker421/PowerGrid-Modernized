package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Client labels a door cell of a controls cabinet or a control station. */
public record CellLabelPayload(BlockPos pos, int cell, String label) implements CustomPacketPayload {
    public static final int MAX_LENGTH = 16;
    public static final Type<CellLabelPayload> TYPE = new Type<>(asResource("cell_label"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CellLabelPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CellLabelPayload::pos,
            ByteBufCodecs.VAR_INT, CellLabelPayload::cell,
            ByteBufCodecs.stringUtf8(MAX_LENGTH * 4), CellLabelPayload::label,
            CellLabelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CellLabelPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        var label = payload.label.length() > MAX_LENGTH ? payload.label.substring(0, MAX_LENGTH) : payload.label;
        if(level.getBlockEntity(payload.pos) instanceof ControlsCabinetBlockEntity cabinet)
            cabinet.setLabel(payload.cell, label);
        else if(level.getBlockEntity(payload.pos) instanceof StationBlockEntity station)
            station.setLabel(payload.cell, label);
    }
}
