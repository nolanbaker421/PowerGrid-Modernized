package com.nolanbaker.pgmodernized.network.packets;

import com.nolanbaker.pgmodernized.device.fixture.CustomFixtureItem;
import com.nolanbaker.pgmodernized.device.fixture.FixtureDesign;
import com.nolanbaker.pgmodernized.device.fixture.FixtureDesignerItem;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.patryk3211.powergrid.utility.Lang;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/**
 * Client asks for a fixture built from the design on the designer: a fixture blank from the
 * inventory becomes a fixture item carrying the design. The design is saved to the designer too.
 */
public record FixtureBuildPayload(boolean offHand, CompoundTag design) implements CustomPacketPayload {
    public static final Type<FixtureBuildPayload> TYPE = new Type<>(asResource("fixture_build"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FixtureBuildPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FixtureBuildPayload::offHand,
            ByteBufCodecs.COMPOUND_TAG, FixtureBuildPayload::design,
            FixtureBuildPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FixtureBuildPayload payload, IPayloadContext context) {
        var player = context.player();
        var stack = player.getItemInHand(payload.offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if(!(stack.getItem() instanceof FixtureDesignerItem) && !(stack.getItem() instanceof CustomFixtureItem))
            return;
        var design = FixtureDesign.read(payload.design);
        design.putOn(stack);
        if(design.parts.isEmpty()) {
            player.displayClientMessage(Lang.translate("message.fixture.empty").style(ChatFormatting.RED).component(), true);
            return;
        }
        if(!player.isCreative()) {
            int slot = player.getInventory().findSlotMatchingItem(new ItemStack(ModItems.FIXTURE_BLANK.get()));
            if(slot < 0) {
                player.displayClientMessage(Lang.translate("message.fixture.needs_blank").style(ChatFormatting.RED).component(), true);
                return;
            }
            player.getInventory().removeItem(slot, 1);
        }
        var built = new ItemStack(ModItems.CUSTOM_FIXTURE.get());
        design.putOn(built);
        if(!player.getInventory().add(built))
            player.drop(built, false);
        player.displayClientMessage(Lang.builder().translate("message.fixture.built", design.name.isEmpty() ? "Fixture" : design.name).style(ChatFormatting.GREEN).component(), true);
    }
}
