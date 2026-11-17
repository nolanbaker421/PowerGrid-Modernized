package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.device.label.LabelMakerItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Client types a label into the label maker in one hand. */
public record LabelMakerPayload(boolean offHand, String text) implements CustomPacketPayload {
    public static final Type<LabelMakerPayload> TYPE = new Type<>(asResource("label_maker"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LabelMakerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, LabelMakerPayload::offHand,
            ByteBufCodecs.stringUtf8(LabelMakerItem.MAX_LENGTH * 4), LabelMakerPayload::text,
            LabelMakerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LabelMakerPayload payload, IPayloadContext context) {
        var stack = context.player().getItemInHand(payload.offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if(stack.getItem() instanceof LabelMakerItem)
            LabelMakerItem.setLabel(stack, payload.text);
    }
}
