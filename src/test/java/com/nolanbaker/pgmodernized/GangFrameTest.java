package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.conduit.gang.GangFrame;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GangFrameTest {
    @Test
    public void everyStateIsARotationThatUndoes() {
        var points = new Vec3[] {new Vec3(0.1, 0.2, 0.3), new Vec3(0.7, 0.15, 0.4), new Vec3(0.5, 0.5, 0.5)};
        for(var facing : Direction.values()) {
            for(int r = 0; r < 4; ++r) {
                var o = GangFrame.toWorld(facing, r, Vec3.ZERO);
                var ex = GangFrame.toWorld(facing, r, new Vec3(1, 0, 0)).subtract(o);
                var ey = GangFrame.toWorld(facing, r, new Vec3(0, 1, 0)).subtract(o);
                var ez = GangFrame.toWorld(facing, r, new Vec3(0, 0, 1)).subtract(o);
                Assertions.assertEquals(1, ex.length(), 1e-9);
                Assertions.assertEquals(0, ex.dot(ey), 1e-9, facing + " " + r);
                Assertions.assertEquals(0, ey.dot(ez), 1e-9);
                Assertions.assertEquals(1, ex.cross(ey).dot(ez), 1e-9, "right-handed: " + facing + " " + r);
                for(var p : points) {
                    var w = GangFrame.toWorld(facing, r, p);
                    Assertions.assertTrue(w.x >= -1e-9 && w.x <= 1 + 1e-9 && w.y >= -1e-9 && w.y <= 1 + 1e-9 && w.z >= -1e-9 && w.z <= 1 + 1e-9, "stays in the block");
                    var back = new Vec3(w.subtract(o).dot(ex), w.subtract(o).dot(ey), w.subtract(o).dot(ez));
                    Assertions.assertEquals(0, back.distanceTo(p), 1e-9, "round trip " + facing + " " + r);
                }
            }
        }
        // The floor frame with rotation 1 is the identity, as the table has it.
        Assertions.assertEquals(0, GangFrame.toWorld(Direction.DOWN, 1, new Vec3(0.2, 0.3, 0.4)).distanceTo(new Vec3(0.2, 0.3, 0.4)), 1e-9);
    }
}
