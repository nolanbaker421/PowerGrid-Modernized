package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/**
 * Client edits a controls cabinet from its screen.
 * @param action {@link #WIRE} wires cell {@code a} to channel index {@code b} (slot * 8 + channel, or -1);
 *               {@link #COLOR} gives the light in cell {@code a} colour {@code b};
 *               {@link #REMOVE_DEVICE} takes the device out of cell {@code a};
 *               {@link #REMOVE_MODULE} takes the module off rail slot {@code a}.
 */
public record ControlsPayload(BlockPos pos, int action, int a, int b) implements CustomPacketPayload {
    public static final int WIRE = 0, COLOR = 1, REMOVE_DEVICE = 2, REMOVE_MODULE = 3, BACKLIGHT = 4;
    public static final Type<ControlsPayload> TYPE = new Type<>(asResource("controls"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ControlsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ControlsPayload::pos,
            ByteBufCodecs.VAR_INT, ControlsPayload::action,
            ByteBufCodecs.VAR_INT, ControlsPayload::a,
            ByteBufCodecs.VAR_INT, ControlsPayload::b,
            ControlsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ControlsPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        if(!(level.getBlockEntity(payload.pos) instanceof ControlsCabinetBlockEntity cabinet))
            return;
        switch(payload.action) {
            case WIRE -> cabinet.setWire(payload.a, payload.b);
            case COLOR -> cabinet.setColor(payload.a, payload.b);
            case REMOVE_DEVICE -> cabinet.removeDevice(payload.a, player);
            case REMOVE_MODULE -> cabinet.removeModule(payload.a, player);
            case BACKLIGHT -> cabinet.setBacklight(payload.a, payload.b);
            default -> {}
        }
    }
}
