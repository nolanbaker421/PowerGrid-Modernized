package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import com.nolanbaker.pgmodernized.util.ShockDamage;
import com.nolanbaker.pgmodernized.util.SubLevelStick;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity;
import org.patryk3211.powergrid.electricity.wire.ImaginaryWireEndpoint;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.PlayerUtilities;

import java.util.ArrayList;
import java.util.List;

/**
 * The hand bender. Tap a leg of a laid run to grab it, then tap a block face where it should go:
 * the leg slides so the tube rests on that face, where the click landed along it, on the pixel
 * grid. The legs either side grow or shrink to follow, a leg that shrinks to nothing goes and its
 * corners merge, and a push across a neighbour on another axis puts in a new leg and a new corner,
 * so an offset, a kick or a saddle is a grab and a tap. Tap the grabbed leg again, or sneak-use in
 * the air, to let go. The stubs into the knockouts never move; bend the next leg.
 * <p>
 * Sneak-click a leg to cut the run there into two runs, each open at the cut, a cut within two
 * pixels of a corner made at the corner. The wires pulled through it come out and drop. A mistake
 * costs the bad piece, which the wire cutters take, not the whole run.
 */
public class ConduitBenderItem extends Item {
    private static final String GRAB_ID = "GrabId", GRAB_UUID = "GrabUuid", GRAB_LEG = "GrabLeg";
    /** A cut this close to a corner, in pixels, is made at the corner. */
    private static final int CORNER_SNAP_PX = 2;

    public ConduitBenderItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ the grab

    private static CompoundTag tag(ItemStack stack) {
        var tag = stack.get(ModDataComponents.CONDUIT_BENDER.get());
        return tag == null ? new CompoundTag() : tag.copy();
    }

    private static void store(ItemStack stack, CompoundTag tag) {
        if(tag.isEmpty())
            stack.remove(ModDataComponents.CONDUIT_BENDER.get());
        else
            stack.set(ModDataComponents.CONDUIT_BENDER.get(), tag);
    }

    /** Whether a leg is grabbed. */
    public static boolean holding(ItemStack stack) {
        return stack.getItem() instanceof ConduitBenderItem && tag(stack).contains(GRAB_ID);
    }

    public static int grabbedLeg(ItemStack stack) {
        return tag(stack).getInt(GRAB_LEG);
    }

    /** The run whose leg is grabbed, if it is still there with that leg. Works on both sides. */
    @Nullable
    public static ConduitRunEntity grabbed(Level level, ItemStack stack) {
        var tag = tag(stack);
        if(!tag.contains(GRAB_ID) || !tag.hasUUID(GRAB_UUID))
            return null;
        var entity = level.getEntity(tag.getInt(GRAB_ID));
        if(entity instanceof ConduitRunEntity run && !run.isRemoved() && run.getUUID().equals(tag.getUUID(GRAB_UUID)) && tag.getInt(GRAB_LEG) < run.segments.size())
            return run;
        return null;
    }

    private static void grab(ItemStack stack, ConduitRunEntity run, int leg) {
        var tag = tag(stack);
        tag.putInt(GRAB_ID, run.getId());
        tag.putUUID(GRAB_UUID, run.getUUID());
        tag.putInt(GRAB_LEG, leg);
        store(stack, tag);
    }

    public static void release(ItemStack stack) {
        var tag = tag(stack);
        tag.remove(GRAB_ID);
        tag.remove(GRAB_UUID);
        tag.remove(GRAB_LEG);
        store(stack, tag);
    }

    // ------------------------------------------------------------------ on a run

    /** A tap on a leg: grab it, or let go of it if it is the grabbed one. Server side. */
    public static InteractionResult tap(ConduitRunEntity run, Player player, ItemStack stack) {
        if(run.isNipple())
            return fail(player, "message.conduit.nipple");
        var leg = legUnder(run, player);
        if(leg == null)
            return fail(player, "message.conduit.bend_miss");
        if(grabbed(run.level(), stack) == run && grabbedLeg(stack) == leg.index()) {
            release(stack);
            player.displayClientMessage(Lang.translate("message.conduit.bend_release").style(ChatFormatting.GRAY).component(), true);
            return InteractionResult.SUCCESS;
        }
        grab(stack, run, leg.index());
        player.displayClientMessage(Lang.builder().translate("message.conduit.bend_grab", leg.index() + 1).style(ChatFormatting.GRAY).component(), true);
        return InteractionResult.SUCCESS;
    }

    /** The second tap, on a block face: the grabbed leg goes there. Server side. */
    public static InteractionResult move(Level level, Player player, ItemStack stack, BlockHitResult hit) {
        var run = grabbed(level, stack);
        if(run == null) {
            release(stack);
            return fail(player, "message.conduit.bend_nothing");
        }
        int index = grabbedLeg(stack);
        var shift = targetShift(run, index, hit);
        if(shift == null)
            return fail(player, "message.conduit.bend_miss");
        if(shift.equals(Vec3i.ZERO)) {
            release(stack);
            player.displayClientMessage(Lang.translate("message.conduit.bend_same").style(ChatFormatting.GRAY).component(), true);
            return InteractionResult.SUCCESS;
        }
        var result = applyShift(run, player, index, shift);
        if(result.consumesAction())
            release(stack);
        return result;
    }

    /**
     * How far, in pixels on each axis, the leg would move for a tap on that block face: square to
     * the face it comes to rest on the face, a tube's radius off it on the pixel grid and never
     * into it; the other way across it goes where the click landed, on the grid. Nothing along
     * its own axis. Works on both sides, so the overlay can show the result first.
     */
    @Nullable
    public static Vec3i targetShift(ConduitRunEntity run, int index, BlockHitResult hit) {
        if(index < 0 || index >= run.segments.size())
            return null;
        var seg = run.segments.get(index);
        var start = legStart(run, index);
        var face = hit.getDirection();
        var p = hit.getLocation();
        double radius = run.size().tubeThickness() / 2;
        int[] shift = new int[3];
        for(var axis : Direction.Axis.values()) {
            if(axis == seg.direction.getAxis())
                continue;
            double want;
            if(axis == face.getAxis()) {
                boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
                double plane = hit.getBlockPos().get(axis) + (positive ? 1 : 0);
                double seat = plane + (positive ? radius : -radius);
                // The nearest pixel centre on the far side of the seat from the face.
                long m = positive ? (long) Math.ceil(seat * 16 - 0.5) : (long) Math.floor(seat * 16 - 0.5);
                want = (m + 0.5) / 16;
            } else {
                want = (Math.round(p.get(axis) * 16 - 0.5) + 0.5) / 16;
            }
            shift[axis.ordinal()] = (int) Math.round((want - start.get(axis)) * 16);
        }
        return new Vec3i(shift[0], shift[1], shift[2]);
    }

    /** Where leg {@code index} begins, in the level's coordinates. */
    private static Vec3 legStart(ConduitRunEntity run, int index) {
        var p = run.position();
        for(int k = 0; k < index; ++k)
            p = p.add(run.segments.get(k).vector());
        return p;
    }

    @Nullable
    private static ConduitRunEntity.Hit legUnder(ConduitRunEntity run, Player player) {
        var eye = player.getEyePosition();
        var far = eye.add(player.getLookAngle().scale(PlayerUtilities.getReachDistance(player) + 1));
        return run.segmentAt(eye, far);
    }

    /**
     * Slides leg {@code index} by {@code shift} pixels, one axis after the other, the legs beside
     * it following; the knockout stubs stay put. Sticks come from, or go back to, the player.
     */
    private static InteractionResult applyShift(ConduitRunEntity run, Player player, int index, Vec3i shift) {
        boolean fixStart = run.getEndpoint1() != null, fixEnd = run.getEndpoint2() != null;
        // Power Grid merges a knockout's stub into a straight first or last leg. Bending that leg
        // keeps the stub's pixels where they are and bends the rest, so the split is made here.
        var legs = new ArrayList<>(run.segments);
        int stubStart = Math.min(Knockouts.STUB_PX, legs.get(0).gridLength);
        int stubEnd = Math.min(Knockouts.STUB_PX, legs.get(legs.size() - 1).gridLength);
        if(fixStart && index == 0 && legs.get(0).gridLength > Knockouts.STUB_PX) {
            var first = legs.get(0);
            legs.set(0, new BlockWireEntity.Point(first.direction, Knockouts.STUB_PX));
            legs.add(1, new BlockWireEntity.Point(first.direction, first.gridLength - Knockouts.STUB_PX));
            index = 1;
        }
        if(fixEnd && index == legs.size() - 1 && legs.get(index).gridLength > Knockouts.STUB_PX) {
            var last = legs.get(index);
            legs.set(index, new BlockWireEntity.Point(last.direction, last.gridLength - Knockouts.STUB_PX));
            legs.add(new BlockWireEntity.Point(last.direction, Knockouts.STUB_PX));
        }
        var moving = legs.get(index);
        var origin = run.position();
        var legStart = origin;
        for(int k = 0; k < index; ++k)
            legStart = legStart.add(legs.get(k).vector());
        List<BlockWireEntity.Point> points = legs;
        var total = Vec3i.ZERO;
        var applied = Vec3.ZERO;
        for(var axis : Direction.Axis.values()) {
            int px = shift.get(axis);
            if(px == 0)
                continue;
            var push = Direction.fromAxisAndDirection(axis, px > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            var result = RunReshape.slide(points, index, push, Math.abs(px), fixStart, fixEnd);
            if(result == null || result.points().isEmpty())
                return fail(player, "message.conduit.bend_miss");
            total = total.offset(result.startShift());
            applied = applied.add(Vec3.atLowerCornerOf(push.getNormal()).scale(Math.abs(px) / 16.0));
            points = result.points();
            index = findLeg(points, origin.add(total.getX() / 16.0, total.getY() / 16.0, total.getZ() / 16.0), legStart.add(applied), moving);
            if(index < 0)
                return fail(player, "message.conduit.bend_miss");
        }
        var first = points.get(0);
        var last = points.get(points.size() - 1);
        if(fixStart && (first.direction != run.segments.get(0).direction || first.gridLength < stubStart))
            return fail(player, "message.conduit.bend_stub");
        if(fixEnd && (last.direction != run.segments.get(run.segments.size() - 1).direction || last.gridLength < stubEnd))
            return fail(player, "message.conduit.bend_stub");

        // The run grows or shrinks: sticks come from, or go back to, the player's pockets.
        var entry = run.getWireEntry();
        int wanted = entry == null ? run.getWireCount() : Math.max(1, (int) Math.ceil(RunReshape.totalPixels(points) / 16f * entry.itemsPerMeter()));
        int delta = wanted - run.getWireCount();
        if(delta > 0 && !player.isCreative() && !PlayerUtilities.hasEnoughItems(player, run.getItem(), delta))
            return fail(player, "message.connection_missing_items");

        var pulled = run.conductors();
        run.reshape(new RunReshape.Result(points, total));
        for(var conductor : pulled)
            conductor.follow(run);
        if(!player.isCreative()) {
            if(delta > 0)
                PlayerUtilities.removeItems(player, run.getItem(), delta);
            else if(delta < 0)
                player.getInventory().placeItemBackInInventory(new ItemStack(run.getItem(), -delta));
        }
        run.incrementWireCount(delta);
        var at = legStart.add(applied);
        run.level().playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5f, 1.6f);
        int moved = Math.abs(shift.getX()) + Math.abs(shift.getY()) + Math.abs(shift.getZ());
        player.displayClientMessage(Lang.builder().translate("message.conduit.bend_moved", moved).style(ChatFormatting.GRAY).component(), true);
        return InteractionResult.SUCCESS;
    }

    /** The leg in {@code points} that is {@code moving}, now starting at {@code expectedStart}; -1 when it is gone. */
    private static int findLeg(List<BlockWireEntity.Point> points, Vec3 origin, Vec3 expectedStart, BlockWireEntity.Point moving) {
        var at = origin;
        for(int i = 0; i < points.size(); ++i) {
            var point = points.get(i);
            if(point.direction == moving.direction && point.gridLength == moving.gridLength && at.distanceToSqr(expectedStart) < 1e-6)
                return i;
            at = at.add(point.vector());
        }
        return -1;
    }

    /** Sneak-click: the run is cut at the leg under the crosshair into two runs. Server side. */
    public static InteractionResult cut(ConduitRunEntity run, Player player, ItemStack stack) {
        if(run.isNipple())
            return fail(player, "message.conduit.nipple");
        var leg = legUnder(run, player);
        if(leg == null)
            return fail(player, "message.conduit.bend_miss");
        var segs = run.segments;
        int i = leg.index();
        var seg = segs.get(i);
        var start = legStart(run, i);
        var along = Vec3.atLowerCornerOf(seg.direction.getNormal());
        int t = (int) Math.round(leg.point().subtract(start).dot(along) * 16);
        t = Math.max(0, Math.min(seg.gridLength, t));
        if(t <= CORNER_SNAP_PX)
            t = 0;
        else if(t >= seg.gridLength - CORNER_SNAP_PX)
            t = seg.gridLength;
        if((i == 0 && t == 0) || (i == segs.size() - 1 && t == seg.gridLength))
            return fail(player, "message.conduit.cut_end");
        int before = t, total = 0;
        for(int k = 0; k < segs.size(); ++k) {
            total += segs.get(k).gridLength;
            if(k < i)
                before += segs.get(k).gridLength;
        }
        if((run.getEndpoint1() != null && before < Knockouts.STUB_PX) || (run.getEndpoint2() != null && total - before < Knockouts.STUB_PX))
            return fail(player, "message.conduit.cut_stub");

        var a = new ArrayList<BlockWireEntity.Point>(segs.subList(0, i));
        if(t > 0)
            a.add(new BlockWireEntity.Point(seg.direction, t));
        var b = new ArrayList<BlockWireEntity.Point>();
        if(t < seg.gridLength)
            b.add(new BlockWireEntity.Point(seg.direction, seg.gridLength - t));
        b.addAll(segs.subList(i + 1, segs.size()));
        var cutPoint = start.add(along.scale(t / 16.0));

        // The wires in it come out: a wire cannot be cut and still reach its terminals.
        var conductors = run.conductors();
        ShockDamage.shockAmps(player, run.maxAmps());
        for(var conductor : conductors)
            conductor.kill();

        var entry = run.getWireEntry();
        double perMeter = entry != null ? entry.itemsPerMeter() : 1;
        int count = run.getWireCount();
        int countA = Math.max(1, Math.min(Math.max(1, count - 1), (int) Math.ceil(before / 16.0 * perMeter)));
        int countB = Math.max(1, count - countA);
        var e1 = run.getEndpoint1();
        var e2 = run.getEndpoint2();
        int color = run.getColor();
        var item = run.getItem();
        var origin = run.position();
        var level = run.level();
        run.discard();
        var first = e1 != null && !(e1 instanceof ImaginaryWireEndpoint)
                ? ConduitRunEntity.create(level, e1, new ItemStack(item, countA), a)
                : ConduitRunEntity.create(level, origin, new ItemStack(item, countA), a);
        var second = ConduitRunEntity.create(level, cutPoint, new ItemStack(item, countB), b);
        if(e2 != null && !(e2 instanceof ImaginaryWireEndpoint))
            second.setEndpoint2(e2);
        first.setColor(color);
        second.setColor(color);
        SubLevelStick.stick(first);
        SubLevelStick.stick(second);
        ((ServerLevel) level).tryAddFreshEntityWithPassengers(first);
        ((ServerLevel) level).tryAddFreshEntityWithPassengers(second);
        ConduitConnection.clear(stack);
        release(stack);
        level.playSound(null, cutPoint.x, cutPoint.y, cutPoint.z, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6f, 1.2f);
        player.displayClientMessage(Lang.builder().translate(conductors.isEmpty() ? "message.conduit.cut_done" : "message.conduit.cut_wires", conductors.size())
                .style(ChatFormatting.GRAY).component(), true);
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ in hand

    /** With a leg grabbed, a tap on a block face moves the leg there. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        var stack = context.getItemInHand();
        if(!holding(stack))
            return InteractionResult.PASS;
        var level = context.getLevel();
        if(level.isClientSide)
            return InteractionResult.SUCCESS;
        var hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        return move(level, context.getPlayer(), stack, hit);
    }

    /** Sneak-use in the air lets go of a grabbed leg. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(!player.isShiftKeyDown() || !holding(stack))
            return InteractionResultHolder.pass(stack);
        if(!level.isClientSide) {
            release(stack);
            player.displayClientMessage(Lang.translate("message.conduit.bend_release").style(ChatFormatting.GRAY).component(), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if(holding(stack))
            tooltip.add(Lang.builder().translate("bender.holding", grabbedLeg(stack) + 1).style(ChatFormatting.AQUA).component());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || holding(stack);
    }

    private static InteractionResult fail(Player player, String key) {
        player.displayClientMessage(Lang.translate(key).style(ChatFormatting.RED).component(), true);
        return InteractionResult.FAIL;
    }
}
