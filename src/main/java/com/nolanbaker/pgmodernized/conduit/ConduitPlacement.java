package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.util.SubLevelStick;
import com.nolanbaker.pgmodernized.util.BodySpace;
import org.patryk3211.powergrid.compat.sable.SableUtils;
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
 * hang and never branch; one run per hub. The held stack is a conduit item or a loaded
 * {@link ConduitBenderItem}; sticks come from it through {@link ConduitSupply} either way.
 */
public final class ConduitPlacement {
    /** Knockouts this close, in blocks, get one straight piece between them instead of a routed run. */
    public static final double DIRECT_MAX = 1.0;

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

    /** A strut was clicked with the pending run: the run goes straight to the clamp, leg by leg, with no tracer. */
    public static InteractionResult strutClick(UseOnContext context, Vec3 clampLocal, Direction.Axis strutAxis) {
        var stack = context.getItemInHand();
        var level = context.getLevel();
        var player = context.getPlayer();
        var existing = ConduitConnection.get(stack, level);
        if(existing == null) {
            message(player, "message.conduit.strut_first", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if(FlexConduitItem.isFlex(stack)) {
            message(player, "message.conduit.strut_no_flex", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        var pos = context.getClickedPos();
        var target = new ImaginaryWireEndpoint(new Vec3(pos.getX() + clampLocal.x, pos.getY() + clampLocal.y, pos.getZ() + clampLocal.z));
        var run = layStraight(level, stack, player, existing, target, strutAxis);
        if(run.getResult().consumesAction()) {
            var entity = run.getObject();
            if(entity != null)
                ConduitConnection.set(stack, new BlockWireEntityEndpoint(entity, true), level);
            return InteractionResult.SUCCESS;
        }
        return run.getResult();
    }

    /**
     * Lays the pending run to a point in straight legs: a knockout's stub first, then the axes across
     * the strut, vertical before horizontal, and along the strut last so the tube arrives along the
     * rail. The run stays open at the clamp, ready for the next strut or the closing knockout.
     */
    private static InteractionResultHolder<ConduitRunEntity> layStraight(Level level, ItemStack stack, @Nullable Player player,
                                                                         IWireEndpoint from, IWireEndpoint to, Direction.Axis strutAxis) {
        var entry = wireEntry(level, stack, player);
        if(entry == null)
            return InteractionResultHolder.fail(null);
        ConduitRunEntity existing = null;
        var last = BodySpace.local(level, from);
        var runStart = last;
        var points = new ArrayList<BlockWireEntity.Point>();
        if(from instanceof BlockWireEntityEndpoint runEnd) {
            existing = runOf(level, runEnd, player);
            if(existing == null)
                return InteractionResultHolder.fail(null);
            if(existing.getItem() != ConduitSupply.item(stack)) {
                message(player, "message.connection_incorrect_wire_type", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            if(!runEnd.getEnd() || existing.segments.isEmpty()) {
                message(player, "message.connection_failed", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
        } else {
            last = BlockTrace.alignPosition(last);
            runStart = last;
            if(from instanceof BlockWireEndpoint hub) {
                if(!hubFree(level, player, hub, stack)) {
                    ConduitConnection.clear(stack);
                    return InteractionResultHolder.fail(null);
                }
                var exit = Knockouts.exit(level, hub);
                if(exit != null) {
                    points.add(new BlockWireEntity.Point(exit, Knockouts.STUB_PX));
                    last = last.relative(exit, Knockouts.STUB_PX / 16.0);
                }
            }
        }
        var target = BodySpace.local(level, to);
        if(!SableUtils.sameSubLevel(level, last, target)) {
            message(player, "message.connection_different_bodies", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        var order = new ArrayList<Direction.Axis>();
        if(strutAxis != Direction.Axis.Y)
            order.add(Direction.Axis.Y);
        for(var axis : Direction.Axis.values())
            if(axis != strutAxis && axis != Direction.Axis.Y)
                order.add(axis);
        order.add(strutAxis);
        double[] cur = {last.x, last.y, last.z};
        for(var axis : order) {
            int i = axis.ordinal();
            int px = (int) Math.round((target.get(axis) - cur[i]) * 16);
            if(px == 0)
                continue;
            points.add(new BlockWireEntity.Point(Direction.fromAxisAndDirection(axis, px > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE), Math.abs(px)));
            cur[i] += px / 16.0;
        }
        points = new ArrayList<>(WallClearance.apply(level, runStart, RunReshape.tidy(points), ConduitSupply.tubeRadius(stack), true, false));
        if(points.isEmpty()) {
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        float addedLength = 0;
        for(var point : points)
            addedLength += point.length();
        if(existing == null) {
            int newItems = Math.max((int) Math.ceil(addedLength * entry.itemsPerMeter()), 1);
            if(!ConduitSupply.has(player, stack, newItems)) {
                message(player, "message.connection_missing_items", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            if(level.isClientSide)
                return InteractionResultHolder.success(null);
            var entity = ConduitRunEntity.create(level, from, ConduitSupply.stack(stack, newItems), points);
            SubLevelStick.stick(entity);
            if(!((ServerLevel) level).tryAddFreshEntityWithPassengers(entity)) {
                PowerGridModernized.LOGGER.error("Failed to spawn conduit run entity");
                message(player, "message.connection_failed", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            ConduitSupply.take(player, stack, newItems);
            return InteractionResultHolder.success(entity);
        }
        int newItems = (int) Math.ceil((existing.getTotalLength() + addedLength) * entry.itemsPerMeter() - existing.getWireCount());
        if(!ConduitSupply.has(player, stack, newItems)) {
            message(player, "message.connection_missing_items", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        if(level.isClientSide)
            return InteractionResultHolder.success(null);
        existing.extend(points, newItems);
        existing.sendExtraData();
        ConduitSupply.take(player, stack, newItems);
        return InteractionResultHolder.success(existing);
    }

    /** A run was clicked with a conduit item (server side): continue from its nearer free end. */
    public static InteractionResult attach(ConduitRunEntity run, Player player, ItemStack stack) {
        var level = run.level();
        if(run.getItem() != ConduitSupply.item(stack)) {
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
        var max = host.maxConduit(index);
        if(max == null) {
            message(player, "message.trough.no_knockout", ChatFormatting.RED);
            return false;
        }
        var size = ConduitItem.sizeOf(stack);
        if(size != null && size.ordinal() > max.ordinal()) {
            if(player != null)
                player.displayClientMessage(Lang.builder().translate("message.conduit.too_big", size.label(), max.label())
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
        if(FlexConduitItem.isFlex(stack))
            return connectFlex(level, stack, player, from, to, entry);
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
        var lastPoint = BodySpace.local(level, from);
        if(from instanceof BlockWireEntityEndpoint runEnd) {
            existing = runOf(level, runEnd, player);
            if(existing == null)
                return InteractionResultHolder.fail(null);
            if(existing.getItem() != ConduitSupply.item(stack)) {
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
        var runStart = lastPoint;
        // Two knockouts within a metre of each other take one straight piece between them, an offset
        // nipple, with no tracer: the legs are the axis-aligned path for hit boxes and wire, the tube is
        // drawn straight from one to the other.
        if(existing == null && from instanceof BlockWireEndpoint && to instanceof BlockWireEndpoint) {
            var exactTo = BodySpace.local(level, to);
            if(SableUtils.sameSubLevel(level, runStart, exactTo) && BodySpace.local(level, from).distanceTo(exactTo) <= DIRECT_MAX + 1e-6) {
                var legs = ConduitRunEntity.manhattan(runStart, BlockTrace.alignPosition(exactTo));
                if(legs.isEmpty()) {
                    message(player, "message.connection_failed", ChatFormatting.RED);
                    return InteractionResultHolder.fail(null);
                }
                int newItems = Math.max((int) Math.ceil(BodySpace.local(level, from).distanceTo(exactTo) * entry.itemsPerMeter()), 1);
                if(!ConduitSupply.has(player, stack, newItems)) {
                    message(player, "message.connection_missing_items", ChatFormatting.RED);
                    return InteractionResultHolder.fail(null);
                }
                if(level.isClientSide)
                    return InteractionResultHolder.success(null);
                var entity = ConduitRunEntity.create(level, from, ConduitSupply.stack(stack, newItems), legs);
                entity.setEndpoint2(to);
                entity.setDirect(true);
                SubLevelStick.stick(entity);
                if(!((ServerLevel) level).tryAddFreshEntityWithPassengers(entity)) {
                    PowerGridModernized.LOGGER.error("Failed to spawn conduit run entity");
                    message(player, "message.connection_failed", ChatFormatting.RED);
                    return InteractionResultHolder.fail(null);
                }
                ConduitSupply.take(player, stack, newItems);
                ConduitBenderItem.onClosed(level, stack, player, entity);
                return InteractionResultHolder.success(null);
            }
        }
        // A run leaves a knockout dead centre and square: a straight stub along the knockout's face
        // normal, and only then the tracer. The same at the far end, in reverse, so the tube enters
        // the target knockout the same way instead of wherever the route happened to arrive.
        var stubs = new ArrayList<BlockWireEntity.Point>();
        if(existing == null && from instanceof BlockWireEndpoint start) {
            var exit = Knockouts.exit(level, start);
            if(exit != null) {
                stubs.add(new BlockWireEntity.Point(exit, Knockouts.STUB_PX));
                lastPoint = lastPoint.relative(exit, Knockouts.STUB_PX / 16.0);
                continueDir = exit;
            }
        }
        var targetPoint = BodySpace.local(level, to);
        ITerminalPlacement terminal = to instanceof BlockWireEndpoint hub ? hub.getTerminalPlacement(level) : null;
        Direction entryDir = to instanceof BlockWireEndpoint hub ? Knockouts.exit(level, hub) : null;
        if(entryDir != null) {
            targetPoint = BlockTrace.alignPosition(targetPoint).relative(entryDir, Knockouts.STUB_PX / 16.0);
            terminal = null;
        }

        if(!SableUtils.sameSubLevel(level, lastPoint, targetPoint)) {
            message(player, "message.connection_different_bodies", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        var path = BlockTrace.findPath(level, lastPoint, targetPoint, terminal, continueDir);
        if(path == null || !path.reachedTarget()) {
            message(player, "message.connection_no_path", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        var assembled = new ArrayList<>(stubs);
        assembled.addAll(path.points());
        if(entryDir != null)
            assembled.add(new BlockWireEntity.Point(entryDir.getOpposite(), Knockouts.STUB_PX));
        // The tracer can overshoot a knockout's stub point and the stub come back through it; folding
        // the legs leaves the run it meant, with no hook at the box.
        boolean closes = to instanceof BlockWireEndpoint;
        // A new run is cleared off its walls here; a run being extended is cleared whole, below, so
        // the join with its old legs is a leg like any other and the kicks land only at the stubs.
        var points = existing == null
                ? WallClearance.apply(level, runStart, RunReshape.tidy(assembled), ConduitSupply.tubeRadius(stack), true, closes)
                : RunReshape.tidy(assembled);
        float addedLength = 0;
        for(var point : points)
            addedLength += point.length();

        if(existing == null) {
            int newItems = Math.max((int) Math.ceil(addedLength * entry.itemsPerMeter()), 1);
            if(!ConduitSupply.has(player, stack, newItems)) {
                message(player, "message.connection_missing_items", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            if(level.isClientSide)
                return InteractionResultHolder.success(null);
            var entity = ConduitRunEntity.create(level, from, ConduitSupply.stack(stack, newItems), points);
            if(closes)
                entity.setEndpoint2(to);
            SubLevelStick.stick(entity);
            if(!((ServerLevel) level).tryAddFreshEntityWithPassengers(entity)) {
                PowerGridModernized.LOGGER.error("Failed to spawn conduit run entity");
                message(player, "message.connection_failed", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            ConduitSupply.take(player, stack, newItems);
            if(closes)
                ConduitBenderItem.onClosed(level, stack, player, entity);
            return InteractionResultHolder.success(closes ? null : entity);
        } else {
            // The whole run, old legs and new, cleared off its walls together.
            var whole = new ArrayList<>(existing.segments);
            whole.addAll(points);
            var cleared = WallClearance.apply(level, BodySpace.local(existing, existing.position()), RunReshape.tidy(whole),
                    ConduitSupply.tubeRadius(stack), existing.getEndpoint1() != null, closes);
            float total = 0;
            for(var point : cleared)
                total += point.length();
            int newItems = (int) Math.ceil(total * entry.itemsPerMeter() - existing.getWireCount());
            if(!ConduitSupply.has(player, stack, newItems)) {
                message(player, "message.connection_missing_items", ChatFormatting.RED);
                return InteractionResultHolder.fail(null);
            }
            if(level.isClientSide)
                return InteractionResultHolder.success(null);
            existing.segments.clear();
            existing.segments.addAll(cleared);
            existing.extend(java.util.List.of(), Math.max(0, newItems));
            if(closes)
                existing.setEndpoint2(to);
            existing.sendExtraData();
            ConduitSupply.take(player, stack, newItems);
            if(closes)
                ConduitBenderItem.onClosed(level, stack, player, existing);
            return InteractionResultHolder.success(closes ? null : existing);
        }
    }

    /**
     * A flexible whip: one knockout straight to another, hung between them, however the two are
     * placed and whether or not they share a body. Never routed, never extended, never merged.
     */
    private static InteractionResultHolder<ConduitRunEntity> connectFlex(Level level, ItemStack stack, @Nullable Player player,
                                                                         IWireEndpoint from, IWireEndpoint to, WireItemEntry entry) {
        if(!(from instanceof BlockWireEndpoint a) || !(to instanceof BlockWireEndpoint b)) {
            message(player, "message.conduit.flex_hubs", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        if(a.equals(b) || !hubFree(level, player, a, stack) || !hubFree(level, player, b, stack))
            return InteractionResultHolder.fail(null);
        float distance = (float) SableUtils.projectedDistance(level, a.getExactPosition(level), b.getExactPosition(level));
        if(distance > entry.maximumLength()) {
            message(player, "message.connection_too_long", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        int items = Math.max(1, (int) Math.ceil(distance * FlexConduitEntity.SLACK * entry.itemsPerMeter()));
        if(!ConduitSupply.has(player, stack, items)) {
            message(player, "message.connection_missing_items", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        if(level.isClientSide)
            return InteractionResultHolder.success(null);
        var entity = FlexConduitEntity.create(level, a, b, ConduitSupply.stack(stack, items));
        if(!((ServerLevel) level).tryAddFreshEntityWithPassengers(entity)) {
            PowerGridModernized.LOGGER.error("Failed to spawn flexible conduit entity");
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        ConduitSupply.take(player, stack, items);
        return InteractionResultHolder.success(null);
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
        if(entity1.getItem() != entity2.getItem() || entity1.getItem() != ConduitSupply.item(stack)) {
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

        var lastPoint = BodySpace.local(entity1, endPosition(entity1));
        var targetPoint = BodySpace.local(entity2, entity2.position());
        var continueDir = entity1.segments.get(entity1.segments.size() - 1).direction;
        if(!SableUtils.sameSubLevel(level, lastPoint, targetPoint)) {
            message(player, "message.connection_different_bodies", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
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
        if(!ConduitSupply.has(player, stack, newItems)) {
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
        ConduitSupply.take(player, stack, newItems);
        if(farEnd != null)
            ConduitBenderItem.onClosed(level, stack, player, entity1);
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
        var entry = WireRegistry.forItem(level, ConduitSupply.item(stack));
        if(entry == null) {
            PowerGridModernized.LOGGER.error("No wire type registered for {}", ConduitSupply.item(stack));
            message(player, "message.connection_failed", ChatFormatting.RED);
        }
        return entry;
    }

    static void message(@Nullable Player player, String key, ChatFormatting style) {
        if(player != null)
            player.displayClientMessage(Lang.translate(key).style(style).component(), true);
    }
}
