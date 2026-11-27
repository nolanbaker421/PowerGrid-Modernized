package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.device.fixture.FixtureDesign;
import com.nolanbaker.pgmodernized.device.fixture.FixtureDesignerItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Client saves the design it has been editing onto the designer in one hand. */
public record FixtureDesignPayload(boolean offHand, CompoundTag design) implements CustomPacketPayload {
    public static final Type<FixtureDesignPayload> TYPE = new Type<>(asResource("fixture_design"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FixtureDesignPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FixtureDesignPayload::offHand,
            ByteBufCodecs.COMPOUND_TAG, FixtureDesignPayload::design,
            FixtureDesignPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FixtureDesignPayload payload, IPayloadContext context) {
        var stack = context.player().getItemInHand(payload.offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if(stack.getItem() instanceof FixtureDesignerItem)
            FixtureDesign.read(payload.design).putOn(stack);   // read and rewritten: clamped, capped, nothing else taken on trust
    }
}
