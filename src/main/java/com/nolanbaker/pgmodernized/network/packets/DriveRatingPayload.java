package com.nolanbaker.pgmodernized.network.packets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/**
 * Client types a drive's ratings: the rated line-to-neutral volts, the hertz they are reached at,
 * and the ramp rate. Zero or less leaves that one as it is.
 */
public record DriveRatingPayload(BlockPos pos, double volts, double hz, double ramp) implements CustomPacketPayload {
    public static final Type<DriveRatingPayload> TYPE = new Type<>(asResource("drive_rating"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DriveRatingPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DriveRatingPayload::pos,
            ByteBufCodecs.DOUBLE, DriveRatingPayload::volts,
            ByteBufCodecs.DOUBLE, DriveRatingPayload::hz,
            ByteBufCodecs.DOUBLE, DriveRatingPayload::ramp,
            DriveRatingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DriveRatingPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        if(level.getBlockEntity(payload.pos) instanceof IDriveRatings drive)
            drive.setRatings(payload.volts, payload.hz, payload.ramp);
    }

    /** What a typed rating lands on; kept here so the common packet never names the fork-only drive. */
    public interface IDriveRatings {
        /** Sets whichever of the three is positive. */
        void setRatings(double volts, double hz, double ramp);

        double ratedVolts();

        double ratedHz();

        double rampHzPerSecond();

        double maxHz();

        double maxVolts();
    }
}
