package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.device.hmi.HmiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Client presses a widget on an HMI panel's screen: a button, or a setpoint's minus or plus. */
public record HmiPressPayload(BlockPos pos, int widget, int action) implements CustomPacketPayload {
    public static final Type<HmiPressPayload> TYPE = new Type<>(asResource("hmi_press"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HmiPressPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HmiPressPayload::pos,
            ByteBufCodecs.VAR_INT, HmiPressPayload::widget,
            ByteBufCodecs.VAR_INT, HmiPressPayload::action,
            HmiPressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HmiPressPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        if(level.getBlockEntity(payload.pos) instanceof HmiBlockEntity hmi)
            hmi.press(payload.widget, payload.action, player);
    }
}
