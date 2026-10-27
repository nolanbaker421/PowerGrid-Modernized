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
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.List;

/**
 * Placement of conduit runs, a port of Power Grid's block-wire flow (via the Cat6 cable): a hub
 * starts a run, clicks on blocks route it along their surfaces, another hub closes it. Runs never
 * hang and never branch; one run per hub. The held stack is a conduit item or a loaded
 * {@link ConduitBenderItem}; sticks come from it through {@link ConduitSupply} either way.
 */
public final class ConduitPlacement {
    /** Knockouts this close, in blocks, get one straight piece between them instead of a routed run. */
    public static final double DIRECT_MAX = 1.0;

    /**
     * Whether two knockouts get one straight piece: within a metre of each other, or on one line
     * with nothing that has a collision box between them, at any distance.
     */
    public static boolean directBetween(Level level, Vec3 a, Vec3 b) {
        if(a.distanceTo(b) <= DIRECT_MAX + 1e-6)
            return true;
        var d = b.subtract(a);
        int offAxis = 0;
        for(double c : new double[] {d.x, d.y, d.z})
            if(Math.abs(c) > 1 / 32.0)
                ++offAxis;
        if(offAxis != 1)
            return false;
        var start = net.minecraft.core.BlockPos.containing(a);
        var end = net.minecraft.core.BlockPos.containing(b);
        double length = d.length();
        var step = d.scale(0.25 / length);
        var at = a;
        for(double travelled = 0; travelled < length; travelled += 0.25) {
            var pos = net.minecraft.core.BlockPos.containing(at);
            if(!pos.equals(start) && !pos.equals(end) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
                return false;
            at = at.add(step);
        }
        return true;
    }

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
    public static InteractionResult strutClick(UseOnContext context, BlockState strut, Vec3 local, double tubeRadiusPx) {
        var stack = context.getItemInHand();
        var level = context.getLevel();
        var player = context.getPlayer();
        var existing = ConduitConnection.get(stack, level);
        if(FlexConduitItem.isFlex(stack)) {
            message(player, "message.conduit.strut_no_flex", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if(existing == null)
            return startOnStrut(context, strut, local, tubeRadiusPx);
        var pos = context.getClickedPos();
        var facing = strut.getValue(StrutBlock.FACING);
        var strutAxis = StrutBlock.axisFor(facing, strut.getValue(StrutBlock.AXIS));
        // From a clamp on a parallel strut the run carries straight on: the slot nearest the run's own
        // line, not the click, so a rack across several struts has no kink at each one.
        var from = BodySpace.local(level, existing);
        var fromState = existing instanceof BlockWireEntityEndpoint ? level.getBlockState(net.minecraft.core.BlockPos.containing(from)) : null;
        boolean rack = fromState != null && fromState.getBlock() instanceof StrutBlock && fromState.getValue(StrutBlock.FACING) == facing
                && StrutBlock.axisFor(facing, fromState.getValue(StrutBlock.AXIS)) == strutAxis;
        var clampLocal = rack
                ? StrutBlock.clamp(strut, local, tubeRadiusPx, (from.get(strutAxis) - pos.get(strutAxis)) * 16)
                : StrutBlock.clamp(strut, local, tubeRadiusPx);
        var target = new ImaginaryWireEndpoint(new Vec3(pos.getX() + clampLocal.x, pos.getY() + clampLocal.y, pos.getZ() + clampLocal.z));
        var run = layStraight(level, stack, player, existing, target, facing, strutAxis, local);
        if(run.getResult().consumesAction()) {
            var entity = run.getObject();
            if(entity != null)
                ConduitConnection.set(stack, new BlockWireEntityEndpoint(entity, true), level);
            return InteractionResult.SUCCESS;
        }
        return run.getResult();
    }

    /**
     * A click on a strut with no run pending starts one there, ahead of any panel: nothing is laid
     * or spent yet, the clamp point is remembered as the run's start, and the next click lays the
     * run from it, leaving the clamp across the rail.
     */
    private static InteractionResult startOnStrut(UseOnContext context, BlockState strut, Vec3 local, double tubeRadiusPx) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var clampLocal = StrutBlock.clamp(strut, local, tubeRadiusPx);
        var clamp = new Vec3(pos.getX() + clampLocal.x, pos.getY() + clampLocal.y, pos.getZ() + clampLocal.z);
        if(!level.isClientSide) {
            ConduitConnection.set(context.getItemInHand(), new ImaginaryWireEndpoint(clamp), level);
            message(context.getPlayer(), "message.conduit.strut_started", ChatFormatting.GRAY);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * For a run begun on a strut, the way it leaves the clamp: across the rail, towards the target
     * when the target is to one side of it. Null when the start is not on a strut.
     */
    @Nullable
    private static Direction strutStartDirection(Level level, Vec3 start, Vec3 target) {
        var state = level.getBlockState(net.minecraft.core.BlockPos.containing(start));
        if(!(state.getBlock() instanceof StrutBlock))
            return null;
        var facing = state.getValue(StrutBlock.FACING);
        var strutAxis = StrutBlock.axisFor(facing, state.getValue(StrutBlock.AXIS));
        var across = Direction.Axis.values()[3 - facing.getAxis().ordinal() - strutAxis.ordinal()];
        double d = target.get(across) - start.get(across);
        return Direction.fromAxisAndDirection(across, d < -1 / 32.0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
    }

    /** A kick out to a strut's tier starts this far before the rail's centre line, in pixels. */
    private static final int KICK_SETBACK_PX = 6;
    /** The last leg into a clamp crosses the rail from at least this far off its centre line, in pixels. */
    private static final int MIN_CROSS_PX = 6;

    /**
     * Lays the pending run to a point in straight legs, in an order that suits the way it arrives,
     * and always crossing the rail last: a clamp holds a tube that crosses the rail, so the strut
     * forces the run's direction through it. Coming square at the rail: along the strut to the
     * slot, across towards the rail at the depth it has, a kick out to the tier just short of the
     * rail, and across the rail, so the tube hugs the wall on the way. Coming any other way, at
     * the mount up a wall to a ceiling strut, or along the rail: a step off to the side it is
     * coming from if it is near the rail's line, a jog along the strut's line to its slot, both at
     * the start as a box offset, on to the tier's depth, along to the slot, then across the rail.
     * The run stays open at the clamp, pointing across the rail, ready for the next strut or the
     * closing knockout.
     */
    private static InteractionResultHolder<ConduitRunEntity> layStraight(Level level, ItemStack stack, @Nullable Player player,
                                                                         IWireEndpoint from, IWireEndpoint to, Direction mount, Direction.Axis strutAxis, Vec3 clickLocal) {
        var entry = wireEntry(level, stack, player);
        if(entry == null)
            return InteractionResultHolder.fail(null);
        ConduitRunEntity existing = null;
        var last = BodySpace.local(level, from);
        var runStart = last;
        var points = new ArrayList<BlockWireEntity.Point>();
        Direction incoming = null;
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
            incoming = existing.segments.get(existing.segments.size() - 1).direction;
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
                    incoming = exit;
                }
            }
            if(from instanceof ImaginaryWireEndpoint) {
                // Begun on a strut: the first leg crosses that rail, as a knockout's stub leaves its box.
                var across = strutStartDirection(level, last, BodySpace.local(level, to));
                if(across != null) {
                    points.add(new BlockWireEntity.Point(across, MIN_CROSS_PX));
                    last = last.relative(across, MIN_CROSS_PX / 16.0);
                    incoming = across;
                }
            }
        }
        var target = BodySpace.local(level, to);
        if(!SableUtils.sameSubLevel(level, last, target)) {
            message(player, "message.connection_different_bodies", ChatFormatting.RED);
            return InteractionResultHolder.fail(null);
        }
        var depthAxis = mount.getAxis();
        var acrossAxis = Direction.Axis.values()[3 - depthAxis.ordinal() - strutAxis.ordinal()];
        double[] cur = {last.x, last.y, last.z};
        if(incoming == null || incoming.getAxis() != acrossAxis) {
            // Near the rail's line already: step off to the side the click is on, so there is a crossing.
            double acrossPx = (target.get(acrossAxis) - cur[acrossAxis.ordinal()]) * 16;
            if(Math.abs(acrossPx) < MIN_CROSS_PX) {
                double sign = Math.abs(acrossPx) >= 1 ? -Math.signum(acrossPx) : (clickLocal.get(acrossAxis) * 16 < 8 ? -1 : 1);
                leg(points, cur, acrossAxis, cur[acrossAxis.ordinal()] + sign * (MIN_CROSS_PX - Math.abs(acrossPx)) / 16.0);
            }
            // The jog to the slot goes at the start too, a box offset, so the corner is two long legs.
            leg(points, cur, strutAxis, target.get(strutAxis));
            leg(points, cur, depthAxis, target.get(depthAxis));
            leg(points, cur, acrossAxis, target.get(acrossAxis));
        } else {
            leg(points, cur, strutAxis, target.get(strutAxis));
            int depthPx = (int) Math.round((target.get(depthAxis) - cur[depthAxis.ordinal()]) * 16);
            int acrossPx = (int) Math.round((target.get(acrossAxis) - cur[acrossAxis.ordinal()]) * 16);
            // The leg before the kick is only laid when it has room for the offset's elbow as well.
            if(depthPx != 0 && Math.abs(acrossPx) > 2 * KICK_SETBACK_PX + 2)
                leg(points, cur, acrossAxis, cur[acrossAxis.ordinal()] + Math.signum(acrossPx) * (Math.abs(acrossPx) - KICK_SETBACK_PX) / 16.0);
            leg(points, cur, depthAxis, target.get(depthAxis));
            leg(points, cur, acrossAxis, target.get(acrossAxis));
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

    /**
     * The target with each coordinate square to {@code entry} replaced by the line's where the true
     * target lies within a pixel of that line.
     */
    private static Vec3 onLine(Vec3 line, Vec3 target, Vec3 exactTarget, Direction.Axis entry) {
        double[] out = {target.x, target.y, target.z};
        for(var axis : Direction.Axis.values()) {
            if(axis == entry)
                continue;
            if(Math.abs(exactTarget.get(axis) - line.get(axis)) <= 1 / 16.0 + 1e-6)
                out[axis.ordinal()] = line.get(axis);
        }
        return new Vec3(out[0], out[1], out[2]);
    }

    /**
     * One straight leg along an axis to a coordinate, in whole pixels, or nothing when there is
     * nowhere to go. A move under a pixel is not worth a jog: a knockout at 11.3 starts a run on the
     * pixel grid at 10.5, and the renderer draws the leg in its slot anyway.
     */
    private static void leg(List<BlockWireEntity.Point> points, double[] cur, Direction.Axis axis, double to) {
        int i = axis.ordinal();
        double exact = (to - cur[i]) * 16;
        int px = Math.abs(exact) < 1 ? 0 : (int) Math.round(exact);
        if(px == 0)
            return;
        points.add(new BlockWireEntity.Point(Direction.fromAxisAndDirection(axis, px > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE), Math.abs(px)));
        cur[i] += px / 16.0;
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
            // A run begun on a strut has no endpoint at its start: open, like a flipped run's.
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
            if(SableUtils.sameSubLevel(level, runStart, exactTo) && directBetween(level, BodySpace.local(level, from), exactTo)) {
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
        if(existing == null && from instanceof ImaginaryWireEndpoint) {
            // Begun on a strut: the first leg crosses that rail, as a knockout's stub leaves its box.
            var across = strutStartDirection(level, lastPoint, BodySpace.local(level, to));
            if(across != null) {
                stubs.add(new BlockWireEntity.Point(across, MIN_CROSS_PX));
                lastPoint = lastPoint.relative(across, MIN_CROSS_PX / 16.0);
                continueDir = across;
            }
        }
        var targetPoint = BodySpace.local(level, to);
        ITerminalPlacement terminal = to instanceof BlockWireEndpoint hub ? hub.getTerminalPlacement(level) : null;
        Direction entryDir = to instanceof BlockWireEndpoint hub ? Knockouts.exit(level, hub) : null;
        if(entryDir != null) {
            // A knockout within a pixel of the run's line is taken as on it: the grid can put the two
            // a whole pixel apart when their true centres are a fraction apart, and the tube is drawn
            // to the knockout's true centre either way. A one-pixel jog at the box is worse than nothing.
            var exactTarget = targetPoint;
            targetPoint = BlockTrace.alignPosition(targetPoint).relative(entryDir, Knockouts.STUB_PX / 16.0);
            targetPoint = onLine(lastPoint, targetPoint, exactTarget, entryDir.getAxis());
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
