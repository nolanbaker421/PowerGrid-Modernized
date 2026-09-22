package com.nolanbaker.pgmodernized.network;

import com.nolanbaker.pgmodernized.PowerGridModernized;
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
 * Two-click placement of Cat6 cables, mirroring Power Grid's wire flow:
 * jack to jack hangs a cable; clicking blocks in between lays it along their surfaces.
 */
public final class Cat6Placement {
    private Cat6Placement() {}

    /** A jack port was clicked: either remember it or finish a connection to it. */
    public static InteractionResult click(UseOnContext context, JackEndpoint clicked) {
        var level = context.getLevel();
        var stack = context.getItemInHand();
        var player = context.getPlayer();
        if(!clicked.isValid(level))
            return InteractionResult.FAIL;

        var existing = Cat6Connection.get(stack);
        if(existing == null) {
            if(!portFree(level, player, clicked))
                return InteractionResult.FAIL;
            Cat6Connection.set(stack, clicked);
            message(player, "message.connection_next", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        var result = connect(level, stack, player, existing, clicked);
        if(result.consumesAction())
            Cat6Connection.clear(stack);
        return result;
    }

    /** A plain block was clicked: route the pending cable along blocks up to the click point. */
    public static InteractionResult groundClick(UseOnContext context) {
        var stack = context.getItemInHand();
        var existing = Cat6Connection.get(stack);
        if(existing == null)
            return InteractionResult.PASS;
        var target = new ImaginaryWireEndpoint(context.getClickLocation().relative(context.getClickedFace(), 1 / 32f));
        var run = connectBlockWire(context.getLevel(), stack, context.getPlayer(), existing, target);
        if(run.getResult().consumesAction()) {
            var entity = run.getObject();
            if(entity != null)
                Cat6Connection.set(stack, new BlockWireEntityEndpoint(entity, true));
            return InteractionResult.SUCCESS;
        }
        return run.getResult();
    }

    /** A laid cable was clicked with the cable item (server side): continue from its nearer free end. */
    public static InteractionResult attach(Cat6BlockWireEntity wire, Player player, ItemStack stack) {
        var level = wire.level();
        var eye = player.getEyePosition();
        var hit = wire.raycast(eye, eye.add(player.getLookAngle().scale(PlayerUtilities.getReachDistance(player) + 1)));
        var reference = hit != null ? hit : eye;
        var start = wire.position();
        var end = endPosition(wire);
        boolean atStart = reference.distanceToSqr(start) < reference.distanceToSqr(end);

        if(atStart) {
            if(wire.getEndpoint1() != null) {
                message(player, "message.cat6_no_junction", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            wire = (Cat6BlockWireEntity) wire.flip();
        } else if(wire.getEndpoint2() != null) {
            message(player, "message.cat6_no_junction", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        var endpoint = new BlockWireEntityEndpoint(wire, true);

        var existing = Cat6Connection.get(stack);
        if(existing == null) {
            Cat6Connection.set(stack, endpoint);
            message(player, "message.connection_next", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        var result = connect(level, stack, player, existing, endpoint);
        if(result.consumesAction())
            Cat6Connection.clear(stack);
        return result;
    }

    /** Jack to jack hangs a cable; anything involving a laid run extends it along blocks. */
    private static InteractionResult connect(Level level, ItemStack stack, @Nullable Player player, IWireEndpoint from, IWireEndpoint to) {
        if(to instanceof JackEndpoint jack && !portFree(level, player, jack))
            return InteractionResult.FAIL;
        if(from instanceof JackEndpoint a && to instanceof JackEndpoint b)
            return hanging(level, stack, player, a, b);
        return connectBlockWire(level, stack, player, from, to).getResult();
    }

    private static boolean portFree(Level level, @Nullable Player player, JackEndpoint endpoint) {
        var jack = endpoint.jack(level);
        if(jack == null)
            return false;
        if(jack.isPortUsed(endpoint.getPort())) {
            message(player, "message.cat6_port_used", ChatFormatting.RED);
            return false;
        }
        return true;
    }

    private static InteractionResult hanging(Level level, ItemStack stack, @Nullable Player player, JackEndpoint origin, JackEndpoint clicked) {
        if(origin.getPos().equals(clicked.getPos())) {
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        var originJack = origin.jack(level);
        if(originJack == null || originJack.isRemoved()) {
            Cat6Connection.clear(stack);
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if(originJack.isPortUsed(origin.getPort())) {
            Cat6Connection.clear(stack);
            message(player, "message.cat6_port_used", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        var entry = wireEntry(level, stack, player);
        if(entry == null)
            return InteractionResult.FAIL;
        float distance = (float) origin.getExactPosition(level).distanceTo(clicked.getExactPosition(level));
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

        var entity = Cat6WireEntity.create(level, origin, clicked, stack.copyWithCount(required));
        if(!((ServerLevel) level).tryAddFreshEntityWithPassengers(entity)) {
            entity.dropWire();
            PowerGridModernized.LOGGER.error("Failed to spawn Cat6 cable entity");
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        takeItems(player, stack, required);
        return InteractionResult.SUCCESS;
    }

    /**
     * Port of Power Grid's block-wire connect for Cat6 entities. {@code from} is a jack or the free end
     * of a laid run; {@code to} is a jack, a free run end, or a point on a block surface (open end).
     */
    private static InteractionResultHolder<Cat6BlockWireEntity> connectBlockWire(Level level, ItemStack stack, @Nullable Player player,
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
        if(from instanceof JackEndpoint jack && !portFree(level, player, jack)) {
            Cat6Connection.clear(stack);
            return InteractionResultHolder.fail(null);
        }

        Cat6BlockWireEntity existing = null;
        Direction continueDir = null;
        var lastPoint = from.getExactPosition(level);
        if(from instanceof BlockWireEntityEndpoint runEnd) {
            existing = cableOf(level, runEnd, player);
            if(existing == null)
                return InteractionResultHolder.fail(null);
            if(!runEnd.getEnd() || existing.segments.isEmpty()) {
                message(player, "message.connection_failed", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            continueDir = existing.segments.get(existing.segments.size() - 1).direction;
        } else {
            lastPoint = BlockTrace.alignPosition(lastPoint);
        }
        var targetPoint = to.getExactPosition(level);
        ITerminalPlacement terminal = to instanceof JackEndpoint jack ? jack.terminalPlacement(level) : null;

        var path = BlockTrace.findPath(level, lastPoint, targetPoint, terminal, continueDir);
        if(path == null || !path.reachedTarget()) {
            message(player, "message.connection_no_path", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        float addedLength = 0;
        for(var point : path.points())
            addedLength += point.length();

        boolean closes = to instanceof JackEndpoint;
        if(existing == null) {
            int newItems = Math.max((int) Math.ceil(addedLength * entry.itemsPerMeter()), 1);
            if(!PlayerUtilities.hasEnoughItems(player, stack, newItems)) {
                message(player, "message.connection_missing_items", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            if(level.isClientSide)
                return InteractionResultHolder.success(null);
            var entity = Cat6BlockWireEntity.create(level, from, stack.copyWithCount(newItems), path.points());
            if(closes)
                entity.setEndpoint2(to);
            if(!((ServerLevel) level).tryAddFreshEntityWithPassengers(entity)) {
                PowerGridModernized.LOGGER.error("Failed to spawn Cat6 block cable entity");
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

    /** Join two laid runs end to end into one cable. */
    private static InteractionResultHolder<Cat6BlockWireEntity> merge(Level level, ItemStack stack, @Nullable Player player,
                                                                     BlockWireEntityEndpoint end1, BlockWireEntityEndpoint end2, WireItemEntry entry) {
        if(level.isClientSide)
            return InteractionResultHolder.success(null);
        var entity1 = cableOf(level, end1, player);
        var entity2 = cableOf(level, end2, player);
        if(entity1 == null || entity2 == null || entity1 == entity2)
            return InteractionResultHolder.fail(null);

        // Orient both runs so entity1 ends where entity2 starts.
        if(!end1.getEnd())
            entity1 = (Cat6BlockWireEntity) entity1.flip();
        if(end2.getEnd())
            entity2 = (Cat6BlockWireEntity) entity2.flip();
        if(entity1.getEndpoint2() != null || entity2.getEndpoint1() != null) {
            message(player, "message.cat6_no_junction", ChatFormatting.RED);
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
    private static Cat6BlockWireEntity cableOf(Level level, BlockWireEntityEndpoint endpoint, @Nullable Player player) {
        var entity = endpoint.getEntity(level);
        if(entity instanceof Cat6BlockWireEntity cable)
            return cable;
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
