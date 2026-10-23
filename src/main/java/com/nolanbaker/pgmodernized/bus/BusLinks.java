package com.nolanbaker.pgmodernized.bus;

import com.nolanbaker.pgmodernized.device.breaker.BusLinkEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/**
 * The hidden bus bars that join buses across block boundaries: between switchgear sections,
 * along a bus duct, from a duct into a feed, a plug-in or a lineup, and between switchgear cells.
 * Each is a one-block Power Grid wire of the bus bar type, rated 2000 A, that a lazy tick keeps in
 * place and that removes itself when either end stops wanting it.
 */
public final class BusLinks {
    public static final int CONDUCTORS = 4;

    private BusLinks() {}

    /** Spawns the link between the two terminals unless one is already there. Server side. */
    public static void ensure(Level level, BlockPos a, int ta, BlockPos b, int tb) {
        if(!(level instanceof ServerLevel server))
            return;
        var mine = new BlockWireEndpoint(a, ta);
        var theirs = new BlockWireEndpoint(b, tb);
        var behaviour = mine.getElectricBehaviour(level);
        if(behaviour == null || theirs.getElectricBehaviour(level) == null)
            return;
        if(exists(behaviour, mine, theirs))
            return;
        server.addFreshEntity(BusLinkEntity.create(level, mine, theirs));
    }

    public static boolean exists(ElectricBehaviour behaviour, BlockWireEndpoint mine, BlockWireEndpoint theirs) {
        var wires = behaviour.getConnections().get(mine);
        if(wires == null)
            return false;
        for(var wire : wires) {
            if(!wire.isAlive() || !(wire instanceof BusLinkEntity))
                continue;
            if(theirs.equals(wire.getEndpoint1()) || theirs.equals(wire.getEndpoint2()))
                return true;
        }
        return false;
    }

    /** The direction from {@code a} to {@code b} when they share a face, else null. */
    @Nullable
    public static Direction between(BlockPos a, BlockPos b) {
        for(var d : Direction.values()) {
            if(a.relative(d).equals(b))
                return d;
        }
        return null;
    }

    /** Links every conductor that {@code mine} at {@code me} and the {@link IBusJoin} on its {@code side} agree on. */
    public static void join(Level level, BlockPos me, IBusJoin mine, Direction side) {
        var other = me.relative(side);
        if(!(level.getBlockEntity(other) instanceof IBusJoin theirs))
            return;
        for(int k = 0; k < CONDUCTORS; ++k) {
            int a = mine.busJoin(side, k);
            int b = theirs.busJoin(side.getOpposite(), k);
            if(a >= 0 && b >= 0)
                ensure(level, me, a, other, b);
        }
    }

    /** Whether two {@link IBusJoin} neighbours still agree that these terminals carry the same conductor. */
    public static boolean joinValid(Level level, BlockPos me, int myTerminal, BlockPos other, int otherTerminal) {
        var side = between(me, other);
        if(side == null)
            return false;
        if(!(level.getBlockEntity(me) instanceof IBusJoin mine) || !(level.getBlockEntity(other) instanceof IBusJoin theirs))
            return false;
        for(int k = 0; k < CONDUCTORS; ++k) {
            if(mine.busJoin(side, k) == myTerminal && theirs.busJoin(side.getOpposite(), k) == otherTerminal)
                return true;
        }
        return false;
    }
}
