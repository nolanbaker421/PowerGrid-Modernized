package com.nolanbaker.pgmodernized.conduit.tray;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntityEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.ImaginaryWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.registry.WireItemEntry;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;
import org.patryk3211.powergrid.utility.BlockTrace;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.PlayerUtilities;

/**
 * Lays cable tray: the first click on a block is where a run starts, every click after extends it
 * along the surfaces between, and the run stays open at its far end until the player sneaks
 * off. On an existing tray, a click near an end continues that run; a click on its rails starts
 * or ends a branch there.
 */
public final class TrayPlacement {
    private TrayPlacement() {}

    /** How close to a run's end a click counts as continuing it rather than branching off it. */
    private static final double END_REACH = 0.6;

    /** A plain block: start a run here, or extend the pending one to here. */
    public static InteractionResult groundClick(UseOnContext context) {
        var level = context.getLevel();
        var stack = context.getItemInHand();
        var player = context.getPlayer();
        var here = new ImaginaryWireEndpoint(context.getClickLocation().relative(context.getClickedFace(), 1 / 32f));
        var pending = CableTrayItem.pending(stack, level);
        if(pending == null) {
            if(!level.isClientSide) {
                CableTrayItem.setPending(stack, here, level);
                message(player, "message.tray.next", ChatFormatting.GRAY);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return extendTo(level, stack, player, pending, here, false);
    }

    /** A tray was clicked with the tray item (server side). */
    public static InteractionResult attach(TrayRunEntity run, Player player, ItemStack stack) {
        var level = run.level();
        var hit = run.hitBy(player);
        var pending = CableTrayItem.pending(stack, level);
        boolean nearStart = hit.distanceToSqr(run.position()) < END_REACH * END_REACH;
        boolean nearEnd = hit.distanceToSqr(run.endPosition()) < END_REACH * END_REACH;
        if(pending == null && (nearStart || nearEnd)) {
            // Continue from that end: Power Grid runs grow at their end, so a start is flipped round.
            if(nearStart && !nearEnd)
                run = (TrayRunEntity) run.flip();
            CableTrayItem.setPending(stack, new BlockWireEntityEndpoint(run, true), level);
            message(player, "message.tray.next", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        var on = new ImaginaryWireEndpoint(run.nearest(hit).point());
        if(pending == null) {
            CableTrayItem.setPending(stack, on, level);
            message(player, "message.tray.branch", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        return extendTo(level, stack, player, pending, on, true);
    }

    /**
     * Lays tray from the pending end to {@code to}: a new run from a free start, or more legs on a
     * run under way. {@code closes} ends the run there, as a branch does on another run's rails.
     */
    private static InteractionResult extendTo(Level level, ItemStack stack, @Nullable Player player, IWireEndpoint from, IWireEndpoint to, boolean closes) {
        var entry = wireEntry(level, stack, player);
        if(entry == null)
            return InteractionResult.FAIL;
        TrayRunEntity existing = null;
        Direction continueDir = null;
        Vec3 lastPoint;
        if(from instanceof BlockWireEntityEndpoint runEnd) {
            if(!(runEnd.getEntity(level) instanceof TrayRunEntity run) || !runEnd.getEnd() || run.segments.isEmpty()) {
                message(player, "message.connection_failed", ChatFormatting.RED);
                CableTrayItem.clearPending(stack);
                return InteractionResult.FAIL;
            }
            existing = run;
            continueDir = run.segments.get(run.segments.size() - 1).direction;
            lastPoint = run.endPosition();
        } else {
            lastPoint = BlockTrace.alignPosition(from.getExactPosition(level));
        }
        var targetPoint = to.getExactPosition(level);
        var path = BlockTrace.findPath(level, lastPoint, targetPoint, null, continueDir);
        if(path == null || !path.reachedTarget()) {
            message(player, "message.connection_no_path", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        float added = 0;
        for(var point : path.points())
            added += point.length();
        if(existing == null) {
            int items = Math.max((int) Math.ceil(added * entry.itemsPerMeter()), 1);
            if(!PlayerUtilities.hasEnoughItems(player, stack, items)) {
                message(player, "message.connection_missing_items", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            if(level.isClientSide)
                return InteractionResult.SUCCESS;
            var run = TrayRunEntity.create(level, from, stack.copyWithCount(items), path.points());
            if(!((ServerLevel) level).tryAddFreshEntityWithPassengers(run)) {
                PowerGridModernized.LOGGER.error("Failed to spawn cable tray entity");
                message(player, "message.connection_failed", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            takeItems(player, stack, items);
            existing = run;
        } else {
            int items = (int) Math.ceil((existing.getTotalLength() + added) * entry.itemsPerMeter() - existing.getWireCount());
            if(!PlayerUtilities.hasEnoughItems(player, stack, items)) {
                message(player, "message.connection_missing_items", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            if(level.isClientSide)
                return InteractionResult.SUCCESS;
            existing.extend(path.points(), items);
            existing.sendExtraData();
            takeItems(player, stack, items);
        }
        if(closes) {
            CableTrayItem.clearPending(stack);
            message(player, "message.tray.joined", ChatFormatting.GRAY);
        } else {
            CableTrayItem.setPending(stack, new BlockWireEntityEndpoint(existing, true), level);
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    private static WireItemEntry wireEntry(Level level, ItemStack stack, @Nullable Player player) {
        var entry = WireRegistry.forItem(level, stack.getItem());
        if(entry == null) {
            PowerGridModernized.LOGGER.error("No wire type registered for {}", stack.getItem());
            message(player, "message.connection_failed", ChatFormatting.RED);
        }
        return entry;
    }

    private static void takeItems(@Nullable Player player, ItemStack stack, int count) {
        if(player == null || player.isCreative() || count <= 0)
            return;
        PlayerUtilities.removeItems(player, stack, count);
    }

    static void message(@Nullable Player player, String key, ChatFormatting style) {
        if(player != null)
            player.displayClientMessage(Lang.translate(key).style(style).component(), true);
    }
}
