package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/**
 * Client types an exact setting into a load bank: the load in watts and the rated line-to-neutral
 * voltage. Zero or less for either means "back to the value box".
 */
public record LoadBankPayload(BlockPos pos, double watts, double volts) implements CustomPacketPayload {
    public static final Type<LoadBankPayload> TYPE = new Type<>(asResource("load_bank"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LoadBankPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LoadBankPayload::pos,
            ByteBufCodecs.DOUBLE, LoadBankPayload::watts,
            ByteBufCodecs.DOUBLE, LoadBankPayload::volts,
            LoadBankPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LoadBankPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        if(level.getBlockEntity(payload.pos) instanceof ISpliceHost host && host instanceof ILoadBankSettings bank)
            bank.setExact(payload.watts, payload.volts);
    }

    /** What a typed setting lands on; kept here so the common packet never names the fork-only block entity. */
    public interface ILoadBankSettings {
        void setExact(double watts, double volts);

        double exactWatts();

        double exactVolts();
    }
}
