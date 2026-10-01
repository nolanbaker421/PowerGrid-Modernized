package com.nolanbaker.pgmodernized.chain;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.network.JackEndpoint;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.compat.sable.SableUtils;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.PlayerUtilities;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Strings a cable chain: click one anchor, then the other. The second click checks the distance
 * (projected through Sable sub-levels, so an anchor on a crane body measures as it hangs in the
 * world), takes the chain items, and spawns the chain with its four conductors and its network link.
 * The chain is cut {@link #SLACK_ITEMS} items longer than the distance at placement, so string it
 * with the trolley at the far end of its travel.
 */
public final class CableChainPlacement {
    /** Extra chain beyond the placement distance, in items. */
    public static final int SLACK_ITEMS = 2;

    private CableChainPlacement() {}

    public static InteractionResult click(Level level, BlockPos pos, @Nullable Player player, ItemStack stack) {
        if(!(level.getBlockEntity(pos) instanceof CableChainAnchorBlockEntity clicked))
            return InteractionResult.PASS;
        if(clicked.chain() != null) {
            message(player, "message.cable_chain.anchor_used", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        var first = CableChainItem.pending(stack, level);
        if(first == null) {
            CableChainItem.setPending(stack, pos, level);
            message(player, "message.connection_next", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        var result = connect(level, stack, player, first, pos);
        if(result.consumesAction())
            CableChainItem.clearPending(stack);
        return result;
    }

    private static InteractionResult connect(Level level, ItemStack stack, @Nullable Player player, BlockPos fixedPos, BlockPos movingPos) {
        if(fixedPos.equals(movingPos)) {
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if(!(level.getBlockEntity(fixedPos) instanceof CableChainAnchorBlockEntity fixed) || fixed.chain() != null) {
            CableChainItem.clearPending(stack);
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if(!(level.getBlockEntity(movingPos) instanceof CableChainAnchorBlockEntity moving))
            return InteractionResult.FAIL;

        double distance = SableUtils.projectedDistance(level, fixed.mountPosition(), moving.mountPosition());
        int max = PgmConfig.CABLE_CHAIN_MAX_LENGTH.get();
        if(!Double.isFinite(distance) || distance > max) {
            message(player, "message.cable_chain.too_long", ChatFormatting.RED, max);
            return InteractionResult.FAIL;
        }
        int required = Math.max(1, (int) Math.ceil(distance / CableChainItem.METERS_PER_ITEM)) + SLACK_ITEMS;
        if(player != null && !PlayerUtilities.hasEnoughItems(player, stack, required)) {
            message(player, "message.cable_chain.missing_items", ChatFormatting.RED, required);
            return InteractionResult.FAIL;
        }
        if(level.isClientSide)
            return InteractionResult.SUCCESS;

        var server = (ServerLevel) level;
        float chainLength = required * CableChainItem.METERS_PER_ITEM;
        var chain = CableChainEntity.create(level, fixedPos, movingPos, stack.copyWithCount(required), chainLength);
        if(!server.tryAddFreshEntityWithPassengers(chain)) {
            PowerGridModernized.LOGGER.error("Failed to spawn cable chain entity");
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        var conductors = new ArrayList<UUID>();
        for(int terminal = CableChainAnchorBlock.L1; terminal <= CableChainAnchorBlock.N; ++terminal) {
            var conductor = ChainConductorEntity.create(level, chain.getUUID(),
                    new BlockWireEndpoint(fixedPos, terminal), new BlockWireEndpoint(movingPos, terminal));
            if(server.tryAddFreshEntityWithPassengers(conductor))
                conductors.add(conductor.getUUID());
        }
        UUID link = null;
        if(!fixed.networkJack().isPortUsed(0) && !moving.networkJack().isPortUsed(0)) {
            var cat6 = ChainCat6Entity.create(level, chain.getUUID(), new JackEndpoint(fixedPos, 0), new JackEndpoint(movingPos, 0));
            if(server.tryAddFreshEntityWithPassengers(cat6))
                link = cat6.getUUID();
        }
        chain.setChildren(conductors, link);
        fixed.setChain(chain.getUUID());
        moving.setChain(chain.getUUID());
        if(player != null && !player.isCreative())
            PlayerUtilities.removeItems(player, stack, required);
        level.playSound(null, movingPos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
        message(player, "message.cable_chain.placed", ChatFormatting.GRAY, String.format("%.0f", chain.travelLimit()));
        return InteractionResult.SUCCESS;
    }

    static void message(@Nullable Player player, String key, ChatFormatting style, Object... args) {
        if(player != null)
            player.displayClientMessage(Lang.builder().translate(key, args).style(style).component(), true);
    }
}
