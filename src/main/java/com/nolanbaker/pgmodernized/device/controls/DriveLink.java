package com.nolanbaker.pgmodernized.device.controls;

import com.nolanbaker.pgmodernized.device.vfd.VfdBlockEntity;
import com.nolanbaker.pgmodernized.fork.ForkHooks;
import com.nolanbaker.pgmodernized.network.JackEndpoint;
import com.nolanbaker.pgmodernized.network.JackSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * The drives a controls cabinet can command over its Cat6: the fork's Three-Phase Drive, through
 * the fork hooks, and the Digital Voltage Regulator. A VFD module's range is in hertz for the one
 * and volts for the other; reverse swaps the phase sequence of the one and the polarity of the other.
 */
public final class DriveLink {
    /** Jacks walked before giving up: a cabinet is never on a network that big. */
    private static final int LIMIT = 256;

    private DriveLink() {}

    public static boolean isDrive(@Nullable BlockEntity be) {
        return be instanceof VfdBlockEntity || ForkHooks.get().isDrive(be);
    }

    /** Whether the drive's range is in hertz (a three-phase drive) rather than volts (a regulator). */
    public static boolean usesHertz(@Nullable BlockEntity be) {
        return ForkHooks.get().isDrive(be);
    }

    /** The most the range may be set to for this drive. */
    public static float ceiling(@Nullable BlockEntity be) {
        if(be instanceof VfdBlockEntity dvr)
            return dvr.maxVoltage();
        return ForkHooks.get().driveCeiling(be);
    }

    /** Run or stop the drive at the given setting, hertz or volts by its kind. */
    public static void command(BlockEntity be, boolean run, boolean reverse, float setting) {
        if(be instanceof VfdBlockEntity dvr) {
            dvr.setVoltage(run ? (reverse ? -setting : setting) : 0);
            return;
        }
        ForkHooks.get().commandDrive(be, run, reverse, setting);
    }

    /** Every drive reachable from this jack over Cat6 cable, in a stable order by position. */
    /** The nearest block entity of that class reachable over the jack's cables (any port), or null. */
    public static <T extends BlockEntity> @org.jetbrains.annotations.Nullable BlockPos nearest(Level level, JackSupport start, Class<T> type, BlockPos from) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        var visited = new HashSet<BlockPos>();
        var queue = new ArrayDeque<JackSupport>();
        queue.add(start);
        visited.add(start.pos());
        while(!queue.isEmpty() && visited.size() < LIMIT) {
            var jack = queue.poll();
            var owner = jack.owner();
            if(type.isInstance(owner)) {
                double d = owner.getBlockPos().distSqr(from);
                if(d < bestDistance) {
                    bestDistance = d;
                    best = owner.getBlockPos();
                }
            }
            for(var cable : List.copyOf(jack.cables())) {
                var wire = cable.asWireEntity();
                for(IWireEndpoint endpoint : new IWireEndpoint[] {wire.getEndpoint1(), wire.getEndpoint2()}) {
                    if(!(endpoint instanceof JackEndpoint end) || visited.contains(end.getPos()))
                        continue;
                    var other = end.jack(level);
                    if(other == null)
                        continue;
                    visited.add(end.getPos());
                    queue.add(other);
                }
            }
        }
        return best;
    }

    /** Every block entity of that class reachable over the jack's cables, by position. */
    public static <T extends BlockEntity> List<BlockPos> discoverAll(Level level, JackSupport start, Class<T> type) {
        var found = new ArrayList<BlockPos>();
        var visited = new HashSet<BlockPos>();
        var queue = new ArrayDeque<JackSupport>();
        queue.add(start);
        visited.add(start.pos());
        while(!queue.isEmpty() && visited.size() < LIMIT) {
            var jack = queue.poll();
            var owner = jack.owner();
            if(type.isInstance(owner) && !found.contains(owner.getBlockPos()))
                found.add(owner.getBlockPos());
            for(var cable : List.copyOf(jack.cables())) {
                var wire = cable.asWireEntity();
                for(IWireEndpoint endpoint : new IWireEndpoint[] {wire.getEndpoint1(), wire.getEndpoint2()}) {
                    if(!(endpoint instanceof JackEndpoint end) || visited.contains(end.getPos()))
                        continue;
                    var other = end.jack(level);
                    if(other == null)
                        continue;
                    visited.add(end.getPos());
                    queue.add(other);
                }
            }
        }
        found.sort(Comparator.comparingLong(BlockPos::asLong));
        return found;
    }

    public static List<BlockPos> discover(Level level, JackSupport start) {
        var drives = new ArrayList<BlockPos>();
        var visited = new HashSet<BlockPos>();
        var queue = new ArrayDeque<JackSupport>();
        queue.add(start);
        visited.add(start.pos());
        while(!queue.isEmpty() && visited.size() < LIMIT) {
            var jack = queue.poll();
            var owner = jack.owner();
            if(isDrive(owner) && !drives.contains(owner.getBlockPos()))
                drives.add(owner.getBlockPos());
            for(var cable : List.copyOf(jack.cables())) {
                var wire = cable.asWireEntity();
                for(IWireEndpoint endpoint : new IWireEndpoint[] {wire.getEndpoint1(), wire.getEndpoint2()}) {
                    if(!(endpoint instanceof JackEndpoint end) || visited.contains(end.getPos()))
                        continue;
                    var other = end.jack(level);
                    if(other == null)
                        continue;
                    visited.add(end.getPos());
                    queue.add(other);
                }
            }
        }
        drives.sort(Comparator.comparingLong(BlockPos::asLong));
        return drives;
    }
}
