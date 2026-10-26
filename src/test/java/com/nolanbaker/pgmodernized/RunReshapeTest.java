package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.conduit.RunReshape;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity.Point;

import java.util.List;

public class RunReshapeTest {
    private static Point p(Direction d, int px) {
        return new Point(d, px);
    }

    private static void assertRun(List<Point> actual, Point... expected) {
        Assertions.assertEquals(expected.length, actual.size(), "segment count of " + describe(actual));
        for(int i = 0; i < expected.length; ++i)
            Assertions.assertTrue(RunReshape.same(expected[i], actual.get(i)), "segment " + i + " of " + describe(actual));
    }

    private static String describe(List<Point> points) {
        var sb = new StringBuilder();
        for(var point : points)
            sb.append(point.direction.getName()).append(point.gridLength).append(' ');
        return sb.toString().strip();
    }

    @Test
    public void offsetInAStraightRunMakesTwoCorners() {
        // stub up, long leg east, stub down: pushing the leg north puts a kick before it and a return after it.
        var run = List.of(p(Direction.UP, 4), p(Direction.EAST, 32), p(Direction.DOWN, 4));
        var result = RunReshape.slide(run, 1, Direction.NORTH, 3, true, true);
        Assertions.assertNotNull(result);
        assertRun(result.points(), p(Direction.UP, 4), p(Direction.NORTH, 3), p(Direction.EAST, 32), p(Direction.SOUTH, 3), p(Direction.DOWN, 4));
        Assertions.assertEquals(Vec3i.ZERO, result.startShift());
    }

    @Test
    public void pushingAlongANeighbourLengthensIt() {
        // up 4, east 32, down 4: pushing the leg further up grows the lead and shrinks nothing else badly.
        var run = List.of(p(Direction.UP, 4), p(Direction.EAST, 32), p(Direction.DOWN, 4));
        var result = RunReshape.slide(run, 1, Direction.UP, 2, true, true);
        Assertions.assertNotNull(result);
        assertRun(result.points(), p(Direction.UP, 6), p(Direction.EAST, 32), p(Direction.DOWN, 6));
    }

    @Test
    public void pushingBackCancelsAndThenFlips() {
        var run = List.of(p(Direction.UP, 4), p(Direction.EAST, 32), p(Direction.DOWN, 4));
        var exact = RunReshape.slide(run, 1, Direction.DOWN, 4, true, true);
        Assertions.assertNotNull(exact);
        assertRun(exact.points(), p(Direction.EAST, 32));
        var past = RunReshape.slide(run, 1, Direction.DOWN, 6, true, true);
        Assertions.assertNotNull(past);
        assertRun(past.points(), p(Direction.DOWN, 2), p(Direction.EAST, 32), p(Direction.UP, 2));
    }

    @Test
    public void offsetUndoesItself() {
        var run = List.of(p(Direction.UP, 4), p(Direction.EAST, 32), p(Direction.DOWN, 4));
        var out = RunReshape.slide(run, 1, Direction.NORTH, 3, true, true);
        Assertions.assertNotNull(out);
        var back = RunReshape.slide(out.points(), 2, Direction.SOUTH, 3, true, true);
        Assertions.assertNotNull(back);
        assertRun(back.points(), p(Direction.UP, 4), p(Direction.EAST, 32), p(Direction.DOWN, 4));
    }

    @Test
    public void openEndMovesWithItsSegment() {
        var run = List.of(p(Direction.UP, 4), p(Direction.EAST, 32));
        var result = RunReshape.slide(run, 1, Direction.NORTH, 3, true, false);
        Assertions.assertNotNull(result);
        assertRun(result.points(), p(Direction.UP, 4), p(Direction.NORTH, 3), p(Direction.EAST, 32));
    }

    @Test
    public void looseStartMovesTheOrigin() {
        var run = List.of(p(Direction.EAST, 32), p(Direction.DOWN, 4));
        var result = RunReshape.slide(run, 0, Direction.NORTH, 3, false, true);
        Assertions.assertNotNull(result);
        assertRun(result.points(), p(Direction.EAST, 32), p(Direction.SOUTH, 3), p(Direction.DOWN, 4));
        Assertions.assertEquals(new Vec3i(0, 0, -3), result.startShift());
    }

    @Test
    public void pushAlongTheSegmentIsRefused() {
        var run = List.of(p(Direction.UP, 4), p(Direction.EAST, 32), p(Direction.DOWN, 4));
        Assertions.assertNull(RunReshape.slide(run, 1, Direction.WEST, 1, true, true));
        Assertions.assertNull(RunReshape.slide(run, 1, Direction.NORTH, 0, true, true));
        Assertions.assertNull(RunReshape.slide(run, 7, Direction.NORTH, 1, true, true));
    }

    @Test
    public void rangeSlidesLegsTogether() {
        // A leg along a wall and the leg it turns into move as one; the kick lands at the stubs.
        var run = List.of(p(Direction.EAST, 4), p(Direction.EAST, 20), p(Direction.UP, 10), p(Direction.WEST, 4));
        var result = RunReshape.slideRange(run, 1, 2, Direction.NORTH, 2, true, true);
        Assertions.assertNotNull(result);
        assertRun(result.points(), p(Direction.EAST, 4), p(Direction.NORTH, 2), p(Direction.EAST, 20), p(Direction.UP, 10), p(Direction.SOUTH, 2), p(Direction.WEST, 4));
        // A leg along the push takes it up in its length instead.
        var toWall = List.of(p(Direction.EAST, 4), p(Direction.SOUTH, 10), p(Direction.UP, 10), p(Direction.WEST, 4));
        var lifted = RunReshape.slideRange(toWall, 2, 2, Direction.NORTH, 2, true, true);
        Assertions.assertNotNull(lifted);
        assertRun(lifted.points(), p(Direction.EAST, 4), p(Direction.SOUTH, 8), p(Direction.UP, 10), p(Direction.SOUTH, 2), p(Direction.WEST, 4));
        Assertions.assertNull(RunReshape.slideRange(run, 1, 2, Direction.EAST, 2, true, true));
    }

    @Test
    public void tidyFoldsRunsOfOneAxis() {
        var messy = List.of(p(Direction.EAST, 3), p(Direction.EAST, 2), p(Direction.WEST, 5), p(Direction.UP, 1), p(Direction.UP, 0), p(Direction.NORTH, 2), p(Direction.SOUTH, 1));
        assertRun(RunReshape.tidy(messy), p(Direction.UP, 1), p(Direction.NORTH, 1));
        Assertions.assertEquals(2, RunReshape.totalPixels(RunReshape.tidy(messy)));
    }
}
