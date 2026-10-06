package com.nolanbaker.pgmodernized.device.hmi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How HMI panels on one wall join into one screen, the way OpenComputers screens do: a placed
 * panel starts as a screen of its own, and whenever two screens with the same facing sit flush
 * along a whole side they merge into one rectangle, again and again. The origin of a screen is its
 * bottom-left panel as the viewer sees it; it holds the layout and the others forward to it.
 * Breaking a panel dissolves its screen into single panels, which then merge back as far as they
 * can.
 */
public final class HmiGroups {
    public static final int MAX_WIDTH = 8, MAX_HEIGHT = 8;

    private HmiGroups() {}

    /** A screen as a rectangle: its origin and size in blocks. */
    public record Rect(BlockPos origin, int width, int height) {}

    /** The viewer's right, for a screen with that facing. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** Column of a position along the viewer's right, in blocks (absolute, for comparing positions on one wall). */
    public static int column(Direction facing, BlockPos pos) {
        var r = right(facing).getNormal();
        return pos.getX() * r.getX() + pos.getZ() * r.getZ();
    }

    /** Depth of a position along the facing, which every panel of one wall shares. */
    private static int depth(Direction facing, BlockPos pos) {
        var n = facing.getNormal();
        return pos.getX() * n.getX() + pos.getZ() * n.getZ();
    }

    /** Where a member panel sits in its screen: {columns from the left, rows from the bottom}. */
    public static int[] offsetOf(Direction facing, BlockPos origin, BlockPos member) {
        return new int[] {column(facing, member) - column(facing, origin), member.getY() - origin.getY()};
    }

    @Nullable
    private static HmiBlockEntity panel(Level level, BlockPos pos, Direction facing) {
        return level.getBlockEntity(pos) instanceof HmiBlockEntity be && HmiBlock.facing(be.getBlockState()) == facing ? be : null;
    }

    @Nullable
    private static Rect rectOf(Level level, BlockPos pos, Direction facing) {
        var be = panel(level, pos, facing);
        return be == null ? null : new Rect(be.origin(), be.width(), be.height());
    }

    private static List<BlockPos> members(Rect rect, Direction facing) {
        var out = new ArrayList<BlockPos>(rect.width * rect.height);
        var r = right(facing);
        for(int dy = 0; dy < rect.height; ++dy)
            for(int dx = 0; dx < rect.width; ++dx)
                out.add(rect.origin.relative(r, dx).above(dy));
        return out;
    }

    /** Sets every panel of the rectangle to it; the origin keeps or resizes its layout, the rest forget theirs. */
    private static void apply(Level level, Rect rect, Direction facing) {
        for(var pos : members(rect, facing)) {
            var be = panel(level, pos, facing);
            if(be != null)
                be.setGroup(rect.origin, rect.width, rect.height);
        }
    }

    /** Server side: a panel was placed and starts as a screen of its own, then merges with whatever it is flush with. */
    public static void onPlaced(Level level, BlockPos pos) {
        var be = level.getBlockEntity(pos) instanceof HmiBlockEntity b ? b : null;
        if(be == null)
            return;
        be.setGroup(pos, 1, 1);
        mergeAround(level, pos, HmiBlock.facing(be.getBlockState()));
    }

    /** Server side, before the block goes: its screen falls apart into single panels, which merge again without it. */
    public static void onRemoved(Level level, BlockPos pos, Direction facing) {
        var rect = rectOf(level, pos, facing);
        if(rect == null)
            return;
        var rest = new ArrayList<BlockPos>();
        for(var member : members(rect, facing)) {
            if(member.equals(pos))
                continue;
            var be = panel(level, member, facing);
            if(be == null)
                continue;
            be.setGroup(member, 1, 1);
            rest.add(member);
        }
        // Bottom row first, left to right, so rows form and then stack.
        rest.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(p -> column(facing, p)));
        for(var member : rest)
            mergeAround(level, member, facing);
    }

    /** Merges the screen at the position with flush neighbours until nothing fits any more. */
    public static void mergeAround(Level level, BlockPos pos, Direction facing) {
        for(int guard = 0; guard < 64; ++guard) {
            var mine = rectOf(level, pos, facing);
            if(mine == null)
                return;
            Rect other = null, merged = null;
            search:
            for(var member : members(mine, facing)) {
                for(var side : new Direction[] {Direction.UP, Direction.DOWN, right(facing), right(facing).getOpposite()}) {
                    var next = member.relative(side);
                    if(depth(facing, next) != depth(facing, pos))
                        continue;
                    var candidate = rectOf(level, next, facing);
                    if(candidate == null || candidate.origin.equals(mine.origin))
                        continue;
                    merged = union(mine, candidate, facing);
                    if(merged != null) {
                        other = candidate;
                        break search;
                    }
                }
            }
            if(merged == null)
                return;
            // The screen whose origin does not become the merged origin hands its widgets over first.
            var absorbed = merged.origin.equals(mine.origin) ? other : mine;
            var survivor = panel(level, merged.origin, facing);
            var absorbedOrigin = panel(level, absorbed.origin, facing);
            if(survivor != null && absorbedOrigin != null)
                survivor.adoptWidgets(absorbedOrigin, absorbed, merged, facing);
            apply(level, merged, facing);
        }
    }

    /** The rectangle two flush, aligned screens make, or null. */
    @Nullable
    private static Rect union(Rect a, Rect b, Direction facing) {
        int ca = column(facing, a.origin), cb = column(facing, b.origin);
        int ya = a.origin.getY(), yb = b.origin.getY();
        // Side by side: same rows, columns touching.
        if(ya == yb && a.height == b.height && (cb == ca + a.width || ca == cb + b.width)) {
            int width = a.width + b.width;
            return width > MAX_WIDTH ? null : new Rect(cb < ca ? b.origin : a.origin, width, a.height);
        }
        // Stacked: same columns, rows touching.
        if(ca == cb && a.width == b.width && (yb == ya + a.height || ya == yb + b.height)) {
            int height = a.height + b.height;
            return height > MAX_HEIGHT ? null : new Rect(yb < ya ? b.origin : a.origin, a.width, height);
        }
        return null;
    }
}
