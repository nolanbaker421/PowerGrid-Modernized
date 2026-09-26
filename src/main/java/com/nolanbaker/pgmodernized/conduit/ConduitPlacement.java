package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntityEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.ImaginaryWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEndpointType;
import org.patryk3211.powergrid.electricity.wire.registry.WireItemEntry;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;
import org.patryk3211.powergrid.utility.BlockTrace;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.PlayerUtilities;

import java.util.ArrayList;

/**
 * Placement of conduit runs, a port of Power Grid's block-wire flow (via the Cat6 cable): a hub
 * starts a run, clicks on blocks route it along their surfaces, another hub closes it. Runs never
 * hang and never branch; one run per hub.
 */
public final class ConduitPlacement {
    private ConduitPlacement() {}

    /** A hub was clicked: remember it, or finish the pending run on it. */
    public static InteractionResult click(UseOnContext context, BlockWireEndpoint hub) {
        var level = context.getLevel();
        var stack = context.getItemInHand();
        var player = context.getPlayer();
        if(!hub.isValid(level))
            return InteractionResult.FAIL;

        var existing = ConduitConnection.get(stack, level);
        if(existing == null) {
            if(!hubFree(level, player, hub, stack))
                return InteractionResult.FAIL;
            ConduitConnection.set(stack, hub, level);
            message(player, "message.connection_next", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        if(existing.equals(hub)) {
            ConduitConnection.clear(stack);
            message(player, "message.connection_reset", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        if(!hubFree(level, player, hub, stack))
            return InteractionResult.FAIL;
        var result = connectBlockWire(level, stack, player, existing, hub).getResult();
        if(result.consumesAction())
            ConduitConnection.clear(stack);
        return result;
    }

    /** A plain block was clicked: route the pending run along blocks up to the click point. */
    public static InteractionResult groundClick(UseOnContext context) {
        var stack = context.getItemInHand();
        var existing = ConduitConnection.get(stack, context.getLevel());
        if(existing == null)
            return InteractionResult.PASS;
        var target = new ImaginaryWireEndpoint(context.getClickLocation().relative(context.getClickedFace(), 1 / 32f));
        var run = connectBlockWire(context.getLevel(), stack, context.getPlayer(), existing, target);
        if(run.getResult().consumesAction()) {
            var entity = run.getObject();
            if(entity != null)
                ConduitConnection.set(stack, new BlockWireEntityEndpoint(entity, true), context.getLevel());
            return InteractionResult.SUCCESS;
        }
        return run.getResult();
    }

    /** A run was clicked with a conduit item (server side): continue from its nearer free end. */
    public static InteractionResult attach(ConduitRunEntity run, Player player, ItemStack stack) {
        var level = run.level();
        if(run.getItem() != stack.getItem()) {
            message(player, "message.connection_incorrect_wire_type", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        var eye = player.getEyePosition();
        var hit = run.raycast(eye, eye.add(player.getLookAngle().scale(PlayerUtilities.getReachDistance(player) + 1)));
        var reference = hit != null ? hit : eye;
        var start = run.position();
        var end = endPosition(run);
        boolean atStart = reference.distanceToSqr(start) < reference.distanceToSqr(end);

        if(atStart) {
            if(run.getEndpoint1() != null) {
                message(player, "message.conduit.no_tee", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            run = (ConduitRunEntity) run.flip();
        } else if(run.getEndpoint2() != null) {
            message(player, "message.conduit.no_tee", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        var endpoint = new BlockWireEntityEndpoint(run, true);

        var existing = ConduitConnection.get(stack, level);
        if(existing == null) {
            ConduitConnection.set(stack, endpoint, level);
            message(player, "message.connection_next", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        var result = connectBlockWire(level, stack, player, existing, endpoint).getResult();
        if(result.consumesAction())
            ConduitConnection.clear(stack);
        return result;
    }

    /** Any splice host (box, socket, breaker panel) can take a run on a free hub. */
    private static boolean hubFree(Level level, @Nullable Player player, BlockWireEndpoint hub, ItemStack stack) {
        if(!(level.getBlockEntity(hub.getPos()) instanceof ISpliceHost host))
            return false;
        int index = host.hubAt(hub.getTerminal());
        if(index < 0)
            return false;
        if(host.hubRun(index) != null) {
            message(player, "message.conduit.hub_used", ChatFormatting.RED);
            return false;
        }
        if(stack.getItem() instanceof ConduitItem conduit && conduit.size().ordinal() > host.maxConduit().ordinal()) {
            if(player != null)
                player.displayClientMessage(Lang.builder().translate("message.conduit.too_big", conduit.size().label(), host.maxConduit().label())
                        .style(ChatFormatting.RED).component(), true);
            return false;
        }
        return true;
    }

    /**
     * {@code from} is a hub or the free end of a run; {@code to} is a hub, a free run end, or a point on
     * a block surface (open end).
     */
    private static InteractionResultHolder<ConduitRunEntity> connectBlockWire(Level level, ItemStack stack, @Nullable Player player,
                                                                              IWireEndpoint from, IWireEndpoint to) {
        var entry = wireEntry(level, stack, player);
        if(entry == null)
            return InteractionResultHolder.fail(null);
        if(from.type() == WireEndpointType.BLOCK_WIRE && to.type() == WireEndpointType.BLOCK_WIRE)
            return merge(level, stack, player, (BlockWireEntityEndpoint) from, (BlockWireEntityEndpoint) to, entry);
        if(!(from instanceof BlockWireEntityEndpoint) && to instanceof BlockWireEntityEndpoint) {
            var swap = from;
            from = to;
            to = swap;
        }
        if(from.equals(to)) {
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        if(from instanceof BlockWireEndpoint hub && !hubFree(level, player, hub, stack)) {
            ConduitConnection.clear(stack);
            return InteractionResultHolder.fail(null);
        }

        ConduitRunEntity existing = null;
        Direction continueDir = null;
        var lastPoint = from.getExactPosition(level);
        if(from instanceof BlockWireEntityEndpoint runEnd) {
            existing = runOf(level, runEnd, player);
            if(existing == null)
                return InteractionResultHolder.fail(null);
            if(existing.getItem() != stack.getItem()) {
                message(player, "message.connection_incorrect_wire_type", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            if(!runEnd.getEnd() || existing.segments.isEmpty()) {
                message(player, "message.connection_failed", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            continueDir = existing.segments.get(existing.segments.size() - 1).direction;
        } else {
            lastPoint = BlockTrace.alignPosition(lastPoint);
        }
        var targetPoint = to.getExactPosition(level);
        ITerminalPlacement terminal = to instanceof BlockWireEndpoint hub ? hub.getTerminalPlacement(level) : null;

        var path = BlockTrace.findPath(level, lastPoint, targetPoint, terminal, continueDir);
        if(path == null || !path.reachedTarget()) {
            message(player, "message.connection_no_path", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        float addedLength = 0;
        for(var point : path.points())
            addedLength += point.length();

        boolean closes = to instanceof BlockWireEndpoint;
        if(existing == null) {
            int newItems = Math.max((int) Math.ceil(addedLength * entry.itemsPerMeter()), 1);
            if(!PlayerUtilities.hasEnoughItems(player, stack, newItems)) {
                message(player, "message.connection_missing_items", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            if(level.isClientSide)
                return InteractionResultHolder.success(null);
            var entity = ConduitRunEntity.create(level, from, stack.copyWithCount(newItems), path.points());
            if(closes)
                entity.setEndpoint2(to);
            if(!((ServerLevel) level).tryAddFreshEntityWithPassengers(entity)) {
                PowerGridModernized.LOGGER.error("Failed to spawn conduit run entity");
                message(player, "message.connection_failed", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            takeItems(player, stack, newItems);
            return InteractionResultHolder.success(closes ? null : entity);
        } else {
            int newItems = (int) Math.ceil((existing.getTotalLength() + addedLength) * entry.itemsPerMeter() - existing.getWireCount());
            if(!PlayerUtilities.hasEnoughItems(player, stack, newItems)) {
                message(player, "message.connection_missing_items", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            if(level.isClientSide)
                return InteractionResultHolder.success(null);
            existing.extend(path.points(), newItems);
            if(closes)
                existing.setEndpoint2(to);
            existing.sendExtraData();
            takeItems(player, stack, newItems);
            return InteractionResultHolder.success(closes ? null : existing);
        }
    }

    /** Join two open runs end to end. */
    private static InteractionResultHolder<ConduitRunEntity> merge(Level level, ItemStack stack, @Nullable Player player,
                                                                   BlockWireEntityEndpoint end1, BlockWireEntityEndpoint end2, WireItemEntry entry) {
        if(level.isClientSide)
            return InteractionResultHolder.success(null);
        var entity1 = runOf(level, end1, player);
        var entity2 = runOf(level, end2, player);
        if(entity1 == null || entity2 == null || entity1 == entity2)
            return InteractionResultHolder.fail(null);
        if(entity1.getItem() != entity2.getItem() || entity1.getItem() != stack.getItem()) {
            message(player, "message.connection_incorrect_wire_type", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }

        if(!end1.getEnd())
            entity1 = (ConduitRunEntity) entity1.flip();
        if(end2.getEnd())
            entity2 = (ConduitRunEntity) entity2.flip();
        if(entity1.getEndpoint2() != null || entity2.getEndpoint1() != null) {
            message(player, "message.conduit.no_tee", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }

        var lastPoint = endPosition(entity1);
        var targetPoint = entity2.position();
        var continueDir = entity1.segments.get(entity1.segments.size() - 1).direction;
        var path = BlockTrace.findPath(level, lastPoint, targetPoint, null, continueDir);
        if(path == null || !path.reachedTarget()) {
            message(player, "message.connection_no_path", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        float addedLength = 0;
        for(var point : path.points())
            addedLength += point.length();
        int newItems = (int) Math.ceil((entity1.getTotalLength() + entity2.getTotalLength() + addedLength) * entry.itemsPerMeter()
                - entity1.getWireCount() - entity2.getWireCount());
        if(!PlayerUtilities.hasEnoughItems(player, stack, newItems)) {
            message(player, "message.connection_missing_items", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }

        entity1.extend(path.points(), newItems, false);
        entity1.extend(new ArrayList<>(entity2.segments), entity2.getWireCount(), false);
        var farEnd = entity2.getEndpoint2();
        entity2.discard();
        if(farEnd != null)
            entity1.setEndpoint2(farEnd);
        entity1.sendExtraData();
        takeItems(player, stack, newItems);
        return InteractionResultHolder.success(farEnd == null ? entity1 : null);
    }

    @Nullable
    private static ConduitRunEntity runOf(Level level, BlockWireEntityEndpoint endpoint, @Nullable Player player) {
        var entity = endpoint.getEntity(level);
        if(entity instanceof ConduitRunEntity run)
            return run;
        message(player, entity == null ? "message.connection_failed" : "message.connection_incorrect_wire_type", ChatFormatting.RED);
        return null;
    }

    static Vec3 endPosition(BlockWireEntity wire) {
        var pos = wire.position();
        for(var segment : wire.segments)
            pos = pos.add(segment.vector());
        return pos;
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
