package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.device.controls.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/**
 * Client edits a control station from its screen.
 * @param action {@link #WIRE} wires cell {@code a} to channel index {@code b}; {@link #COLOR} gives the light in cell
 *               {@code a} colour {@code b}; {@link #REMOVE_DEVICE} takes the device out of cell {@code a};
 *               {@link #NUMBER} sets the station number to {@code a}.
 */
public record StationPayload(BlockPos pos, int action, int a, int b) implements CustomPacketPayload {
    public static final int WIRE = 0, COLOR = 1, REMOVE_DEVICE = 2, NUMBER = 3;
    public static final Type<StationPayload> TYPE = new Type<>(asResource("station"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StationPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StationPayload::pos,
            ByteBufCodecs.VAR_INT, StationPayload::action,
            ByteBufCodecs.VAR_INT, StationPayload::a,
            ByteBufCodecs.VAR_INT, StationPayload::b,
            StationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StationPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        if(!(level.getBlockEntity(payload.pos) instanceof StationBlockEntity station))
            return;
        switch(payload.action) {
            case WIRE -> station.setWire(payload.a, payload.b);
            case COLOR -> station.setColor(payload.a, payload.b);
            case REMOVE_DEVICE -> station.removeDevice(payload.a, player);
            case NUMBER -> station.setStation(payload.a);
            default -> {}
        }
    }
}
