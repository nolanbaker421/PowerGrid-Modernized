package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Client sends a controls cabinet's whole PLC program, as the editor drew it. */
public record ControlsGraphPayload(BlockPos pos, CompoundTag graph) implements CustomPacketPayload {
    public static final Type<ControlsGraphPayload> TYPE = new Type<>(asResource("controls_graph"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ControlsGraphPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ControlsGraphPayload::pos,
            ByteBufCodecs.COMPOUND_TAG, ControlsGraphPayload::graph,
            ControlsGraphPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ControlsGraphPayload payload, IPayloadContext context) {
        var player = context.player();
        var level = player.level();
        if(!level.isLoaded(payload.pos) || player.distanceToSqr(payload.pos.getCenter()) > 64)
            return;
        if(level.getBlockEntity(payload.pos) instanceof ControlsCabinetBlockEntity cabinet)
            cabinet.setGraph(payload.graph);
    }
}
