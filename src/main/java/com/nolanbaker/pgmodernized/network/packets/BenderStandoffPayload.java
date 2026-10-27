package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.conduit.ConduitBenderItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Client scrolled with a leg grabbed in the bender: how far off the face the leg should land, in pixels. */
public record BenderStandoffPayload(int standoff) implements CustomPacketPayload {
    public static final Type<BenderStandoffPayload> TYPE = new Type<>(asResource("bender_standoff"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BenderStandoffPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BenderStandoffPayload::standoff,
            BenderStandoffPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BenderStandoffPayload payload, IPayloadContext context) {
        var stack = context.player().getMainHandItem();
        if(stack.getItem() instanceof ConduitBenderItem && ConduitBenderItem.holding(stack))
            ConduitBenderItem.setStandoff(stack, payload.standoff);
    }
}
