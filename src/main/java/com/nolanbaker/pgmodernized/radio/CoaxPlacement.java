package com.nolanbaker.pgmodernized.radio;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.network.Cat6Connection;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.compat.sable.SableUtils;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.PlayerUtilities;

/** Two-click placement of coax: port to port hangs a span. There is no laid form; a run takes hangers instead. */
public final class CoaxPlacement {
    private CoaxPlacement() {}

    public static InteractionResult click(UseOnContext context, CoaxEndpoint clicked) {
        var level = context.getLevel();
        var stack = context.getItemInHand();
        var player = context.getPlayer();
        if(!clicked.isValid(level))
            return InteractionResult.FAIL;
        if(!portFree(level, player, clicked))
            return InteractionResult.FAIL;
        var pending = pending(stack, level);
        if(pending == null) {
            Cat6Connection.set(stack, clicked, level);
            message(player, "message.connection_next", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        var result = hang(level, stack, player, pending, clicked);
        if(result.consumesAction())
            Cat6Connection.clear(stack);
        return result;
    }

    /** The pending first end as a coax endpoint, or null (a stale one is dropped). */
    @Nullable
    private static CoaxEndpoint pending(ItemStack stack, Level level) {
        // Read raw: the Cat6 component's own check would throw away an end on an antenna, which is no jack.
        IWireEndpoint endpoint = Cat6Connection.get(stack);
        if(endpoint == null)
            return null;
        var coax = CoaxEndpoint.from(endpoint);
        if(coax instanceof CoaxEndpoint c && c.isValid(level))
            return c;
        Cat6Connection.clear(stack);
        return null;
    }

    /** A port takes one coax, and on a hanger a clip port that holds a Cat6 is taken too. */
    static boolean portFree(Level level, @Nullable Player player, CoaxEndpoint endpoint) {
        var block = endpoint.portBlock(level);
        if(block == null)
            return false;
        boolean used = block.coaxSupport().isPortUsed(endpoint.getPort());
        if(!used && block instanceof CoaxHangerBlockEntity hanger && hanger.networkJack().isPortUsed(endpoint.getPort()))
            used = true;
        if(used) {
            message(player, "message.coax_port_used", ChatFormatting.RED);
            return false;
        }
        return true;
    }

    private static InteractionResult hang(Level level, ItemStack stack, @Nullable Player player, CoaxEndpoint origin, CoaxEndpoint clicked) {
        if(origin.equals(clicked) || origin.getPos().equals(clicked.getPos())) {
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if(!portFree(level, player, origin)) {
            Cat6Connection.clear(stack);
            return InteractionResult.FAIL;
        }
        var entry = WireRegistry.forItem(level, stack.getItem());
        if(entry == null) {
            PowerGridModernized.LOGGER.error("No wire type registered for {}", stack.getItem());
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        float distance = (float) SableUtils.projectedDistance(level, origin.getExactPosition(level), clicked.getExactPosition(level));
        if(distance > entry.maximumLength()) {
            message(player, "message.connection_too_long", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        int required = Math.max(Math.round(distance * entry.itemsPerMeter()), 1);
        if(!PlayerUtilities.hasEnoughItems(player, stack, required)) {
            message(player, "message.connection_missing_items", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if(level.isClientSide)
            return InteractionResult.SUCCESS;
        var entity = CoaxWireEntity.create(level, origin, clicked, stack.copyWithCount(required));
        if(!((ServerLevel) level).tryAddFreshEntityWithPassengers(entity)) {
            entity.dropWire();
            PowerGridModernized.LOGGER.error("Failed to spawn coax entity");
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if(player != null && !player.isCreative())
            PlayerUtilities.removeItems(player, stack, required);
        return InteractionResult.SUCCESS;
    }

    static void message(@Nullable Player player, String key, ChatFormatting style) {
        if(player != null)
            player.displayClientMessage(Lang.translate(key).style(style).component(), true);
    }
}
