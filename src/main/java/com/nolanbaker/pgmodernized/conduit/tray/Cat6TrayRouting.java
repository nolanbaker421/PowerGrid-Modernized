package com.nolanbaker.pgmodernized.conduit.tray;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.network.Cat6BlockWireEntity;
import com.nolanbaker.pgmodernized.network.Cat6Connection;
import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import com.nolanbaker.pgmodernized.util.SubLevelStick;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity.Point;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntityEndpoint;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;
import org.patryk3211.powergrid.utility.BlockTrace;
import org.patryk3211.powergrid.utility.Lang;
import org.patryk3211.powergrid.utility.PlayerUtilities;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Cat6 through cable tray. With a cable under way from a jack or a laid run's free end, a click
 * on a tray marks where the cable enters it; the next click on a tray, the same or one joined to
 * it, is where it leaves. The cable is then laid along the blocks to the entry, along the trays'
 * rails in a free lane to the exit, and left open there for the next click, a jack or more tray.
 */
public final class Cat6TrayRouting {
    private Cat6TrayRouting() {}

    private static final String RUN_KEY = "TrayRun", X = "TrayX", Y = "TrayY", Z = "TrayZ";

    public static InteractionResult click(TrayRunEntity tray, Player player, ItemStack stack) {
        var level = tray.level();
        var hit = tray.hitBy(player);
        var near = tray.nearest(hit);
        var pending = Cat6Connection.get(stack, level);
        if(pending == null) {
            message(player, "message.tray.start_at_jack", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        var entry = entry(stack);
        if(entry == null) {
            setEntry(stack, tray.getUUID(), near.point());
            message(player, "message.tray.entry_set", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        TrayRunEntity entryRun = null;
        for(var run : TrayRunEntity.around(level, entry.point()))
            if(run.getUUID().equals(entry.run()))
                entryRun = run;
        if(entryRun == null) {
            clearEntry(stack);
            message(player, "message.tray.entry_lost", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        var route = TrayGraph.route(level, entryRun, entry.point(), tray, near.point());
        if(route == null) {
            message(player, "message.tray.no_route", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        int lane = TrayGraph.freeLane(route);
        if(lane < 0) {
            message(player, "message.tray.full", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        var inTray = TrayGraph.offset(level, route.corners(), lane);

        // The cable so far: a jack, or the open end of a laid run to carry on from.
        var wireEntry = WireRegistry.forItem(level, stack.getItem());
        if(wireEntry == null) {
            message(player, "message.connection_failed", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        Cat6BlockWireEntity existing = null;
        Direction continueDir = null;
        Vec3 lastPoint;
        if(pending instanceof BlockWireEntityEndpoint runEnd) {
            if(!(runEnd.getEntity(level) instanceof Cat6BlockWireEntity cable) || !runEnd.getEnd() || cable.segments.isEmpty()) {
                message(player, "message.connection_failed", ChatFormatting.RED);
                Cat6Connection.clear(stack);
                return InteractionResult.FAIL;
            }
            existing = cable;
            continueDir = cable.segments.get(cable.segments.size() - 1).direction;
            lastPoint = TrayGraph.end(cable.position(), cable.segments);
        } else {
            lastPoint = BlockTrace.alignPosition(pending.getExactPosition(level));
        }
        var toEntry = BlockTrace.findPath(level, lastPoint, inTray.get(0), null, continueDir);
        if(toEntry == null || !toEntry.reachedTarget()) {
            message(player, "message.connection_no_path", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        var points = new ArrayList<Point>(toEntry.points());
        // The trace ends where it can; a last jog brings it onto the lane before the rails take over.
        points.addAll(TrayGraph.jog(TrayGraph.end(lastPoint, points), inTray.get(0)));
        points.addAll(TrayGraph.toPoints(inTray));
        float added = TrayGraph.length(points);

        if(existing == null) {
            int items = Math.max((int) Math.ceil(added * wireEntry.itemsPerMeter()), 1);
            if(!PlayerUtilities.hasEnoughItems(player, stack, items)) {
                message(player, "message.connection_missing_items", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            var cable = Cat6BlockWireEntity.create(level, pending, stack.copyWithCount(items), points);
            SubLevelStick.stick(cable);
            if(!((ServerLevel) level).tryAddFreshEntityWithPassengers(cable)) {
                PowerGridModernized.LOGGER.error("Failed to spawn Cat6 cable through tray");
                message(player, "message.connection_failed", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            takeItems(player, stack, items);
            existing = cable;
        } else {
            int items = (int) Math.ceil((existing.getTotalLength() + added) * wireEntry.itemsPerMeter() - existing.getWireCount());
            if(!PlayerUtilities.hasEnoughItems(player, stack, items)) {
                message(player, "message.connection_missing_items", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            existing.extend(points, items);
            existing.sendExtraData();
            takeItems(player, stack, items);
        }
        for(var run : route.runs())
            run.takeLane(lane, existing.getUUID());
        Cat6Connection.set(stack, new BlockWireEntityEndpoint(existing, true), level);
        message(player, "message.tray.exit_set", ChatFormatting.GRAY);
        return InteractionResult.SUCCESS;
    }

    // ---- the entry, kept beside the pending end on the cable item ----

    public record Entry(UUID run, Vec3 point) {}

    @Nullable
    public static Entry entry(ItemStack stack) {
        var tag = stack.get(ModDataComponents.CAT6_CONNECTION.get());
        if(tag == null || !tag.hasUUID(RUN_KEY))
            return null;
        return new Entry(tag.getUUID(RUN_KEY), new Vec3(tag.getDouble(X), tag.getDouble(Y), tag.getDouble(Z)));
    }

    private static void setEntry(ItemStack stack, UUID run, Vec3 point) {
        var tag = stack.get(ModDataComponents.CAT6_CONNECTION.get());
        if(tag == null)
            return;
        tag = tag.copy();
        tag.putUUID(RUN_KEY, run);
        tag.putDouble(X, point.x);
        tag.putDouble(Y, point.y);
        tag.putDouble(Z, point.z);
        stack.set(ModDataComponents.CAT6_CONNECTION.get(), tag);
    }

    private static void clearEntry(ItemStack stack) {
        var tag = stack.get(ModDataComponents.CAT6_CONNECTION.get());
        if(tag == null)
            return;
        tag = tag.copy();
        tag.remove(RUN_KEY);
        tag.remove(X);
        tag.remove(Y);
        tag.remove(Z);
        stack.set(ModDataComponents.CAT6_CONNECTION.get(), tag);
    }

    private static void takeItems(@Nullable Player player, ItemStack stack, int count) {
        if(player == null || player.isCreative() || count <= 0)
            return;
        PlayerUtilities.removeItems(player, stack, count);
    }

    private static void message(@Nullable Player player, String key, ChatFormatting style) {
        if(player != null)
            player.displayClientMessage(Lang.translate(key).style(style).component(), true);
    }
}
