package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.conduit.ConduitBenderItem;
import com.nolanbaker.pgmodernized.conduit.RunReshape;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntity.Point;

import java.util.List;

public class BenderPlanTest {
    private static Point p(Direction d, int px) {
        return new Point(d, px);
    }

    private static void assertRun(List<Point> actual, Point... expected) {
        var sb = new StringBuilder();
        for(var point : actual)
            sb.append(point.direction.getName()).append(point.gridLength).append(' ');
        Assertions.assertEquals(expected.length, actual.size(), "segment count of " + sb);
        for(int i = 0; i < expected.length; ++i)
            Assertions.assertTrue(RunReshape.same(expected[i], actual.get(i)), "segment " + i + " of " + sb);
    }

    @Test
    public void legVectorIsInBlocks() {
        Assertions.assertEquals(new Vec3(0, 1, 0), p(Direction.UP, 16).vector());
    }

    @Test
    public void pushAlongANeighbourKeepsTheLegFindable() {
        var plan = ConduitBenderItem.plan(List.of(p(Direction.UP, 24), p(Direction.EAST, 10), p(Direction.DOWN, 4)), 1, new Vec3i(0, 3, 0), true, true);
        Assertions.assertNotNull(plan);
        assertRun(plan.points(), p(Direction.UP, 27), p(Direction.EAST, 10), p(Direction.DOWN, 7));
        Assertions.assertEquals(1, plan.index());
        Assertions.assertEquals(Vec3i.ZERO, plan.startShift());
    }

    @Test
    public void pushAcrossSplicesStepsAndFindsTheLeg() {
        var plan = ConduitBenderItem.plan(List.of(p(Direction.UP, 24), p(Direction.EAST, 10), p(Direction.DOWN, 4)), 1, new Vec3i(0, 0, 3), true, true);
        Assertions.assertNotNull(plan);
        assertRun(plan.points(), p(Direction.UP, 24), p(Direction.SOUTH, 3), p(Direction.EAST, 10), p(Direction.NORTH, 3), p(Direction.DOWN, 4));
        Assertions.assertEquals(2, plan.index());
    }

    @Test
    public void pushOnTwoAxesAtOnce() {
        var plan = ConduitBenderItem.plan(List.of(p(Direction.UP, 24), p(Direction.EAST, 10), p(Direction.DOWN, 4)), 1, new Vec3i(0, 3, 3), true, true);
        Assertions.assertNotNull(plan);
        assertRun(plan.points(), p(Direction.UP, 27), p(Direction.SOUTH, 3), p(Direction.EAST, 10), p(Direction.NORTH, 3), p(Direction.DOWN, 7));
        Assertions.assertEquals(2, plan.index());
    }

    @Test
    public void freeStartMovesWithItsFirstLeg() {
        var plan = ConduitBenderItem.plan(List.of(p(Direction.EAST, 10), p(Direction.DOWN, 4)), 0, new Vec3i(0, 3, 0), false, true);
        Assertions.assertNotNull(plan);
        assertRun(plan.points(), p(Direction.EAST, 10), p(Direction.DOWN, 7));
        Assertions.assertEquals(0, plan.index());
        Assertions.assertEquals(new Vec3i(0, 3, 0), plan.startShift());
    }
}
