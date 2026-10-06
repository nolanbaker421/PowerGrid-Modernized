package com.nolanbaker.pgmodernized.device.helm;

import com.tterrag.registrate.builders.BlockEntityBuilder.BlockEntityFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A helm with no wheel: the keys of whoever is holding a remote paired to this base, from
 * anywhere in the dimension. The holder keeps the base while the remote stays in a hand; putting
 * it away, or ~, lets go. Everything else is the helm's.
 */
public class RadioBaseBlockEntity extends HelmBlockEntity {
    /** Replaced by the computer bridges with their network-node subclasses. */
    public static BlockEntityFactory<RadioBaseBlockEntity> FACTORY = RadioBaseBlockEntity::new;

    public RadioBaseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The holder keeps control as long as a remote paired here is in either hand; distance is no matter. */
    @Override
    protected boolean holdsOn(ServerPlayer player) {
        return RadioRemoteItem.pairedTo(player.getItemInHand(InteractionHand.MAIN_HAND), level, worldPosition)
                || RadioRemoteItem.pairedTo(player.getItemInHand(InteractionHand.OFF_HAND), level, worldPosition);
    }

    @Override
    public Vec3 jackPosition(int port) {
        return RadioBaseBlock.jackPosition(getBlockState(), getBlockPos());
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder().translate("gui.radio.title").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if(helmsmanName().isEmpty())
            Lang.builder().translate("gui.radio.free").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        else
            Lang.builder().translate("gui.radio.manned", helmsmanName()).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        return true;
    }
}
