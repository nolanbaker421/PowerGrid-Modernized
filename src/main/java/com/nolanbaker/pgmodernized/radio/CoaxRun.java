package com.nolanbaker.pgmodernized.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** Follows coax out of a port, through hanger clips, to the antenna at the far end. */
public final class CoaxRun {
    /** The antenna a run reaches and the coax it took to get there, in blocks. */
    public record Result(AntennaBlockEntity antenna, double length, int spans) {}

    private CoaxRun() {}

    @Nullable
    public static Result trace(Level level, ICoaxPort start, int port) {
        Set<Long> seen = new HashSet<>();
        ICoaxPort here = start;
        int at = port;
        double length = 0;
        int spans = 0;
        for(int hops = 0; hops < 256; ++hops) {
            var cable = here.coaxSupport().cableIn(at);
            if(cable == null)
                return null;
            var far = cable.otherEnd(here.coaxSupport().pos());
            if(far == null)
                return null;
            length += cable.length();
            ++spans;
            var block = far.portBlock(level);
            if(block == null)
                return null;
            if(block instanceof AntennaBlockEntity antenna)
                return new Result(antenna, length, spans);
            int out = block.coaxPassThrough(far.getPort());
            if(out < 0 || !seen.add(key(far.getPos(), out)))
                return null;
            here = block;
            at = out;
        }
        return null;
    }

    private static long key(BlockPos pos, int port) {
        return pos.asLong() * 31 + port;
    }
}
