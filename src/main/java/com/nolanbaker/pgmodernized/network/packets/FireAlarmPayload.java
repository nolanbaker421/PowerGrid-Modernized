package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Client works the fire alarm panel's screen: a device's zone, a zone's name, Silence, Reset. */
public record FireAlarmPayload(BlockPos pos, BlockPos via, int action, int a, int b, String text) implements CustomPacketPayload {
    public static final int SET_ZONE = 0, ZONE_NAME = 1, SILENCE = 2, RESET = 3, REMOVE = 4;
    public static final int MAX_LENGTH = 24;
    public static final Type<FireAlarmPayload> TYPE = new Type<>(asResource("fire_alarm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FireAlarmPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, FireAlarmPayload::pos,
            BlockPos.STREAM_CODEC, FireAlarmPayload::via,
            ByteBufCodecs.VAR_INT, FireAlarmPayload::action,
            ByteBufCodecs.VAR_INT, FireAlarmPayload::a,
            ByteBufCodecs.VAR_INT, FireAlarmPayload::b,
            ByteBufCodecs.stringUtf8(MAX_LENGTH * 4), FireAlarmPayload::text,
            FireAlarmPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FireAlarmPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos))
            return;
        // At the panel's door, or at an annunciator of its.
        boolean near = player.distanceToSqr(payload.pos.getCenter()) <= 64;
        if(!near && level.isLoaded(payload.via) && player.distanceToSqr(payload.via.getCenter()) <= 64
                && level.getBlockEntity(payload.via) instanceof com.nolanbaker.pgmodernized.conduit.gang.GangBoxBlockEntity box && box.linksTo(payload.pos))
            near = true;
        if(!near || !(level.getBlockEntity(payload.pos) instanceof FireAlarmPanelBlockEntity panel))
            return;
        switch(payload.action) {
            case SET_ZONE -> panel.setZone(payload.a, payload.b);
            case ZONE_NAME -> panel.setZoneName(payload.a, payload.text.length() > MAX_LENGTH ? payload.text.substring(0, MAX_LENGTH) : payload.text);
            case SILENCE -> panel.silence(player);
            case RESET -> panel.reset(player);
            case REMOVE -> panel.forget(payload.a);
            default -> {}
        }
    }
}
