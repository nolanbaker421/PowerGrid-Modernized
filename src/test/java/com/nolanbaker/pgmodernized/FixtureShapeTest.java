package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.device.fixture.FixtureDesign;
import com.nolanbaker.pgmodernized.device.fixture.FixturePart;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A design's click shape is only what falls inside the anchor block, whatever the design reaches and however it is turned. */
public class FixtureShapeTest {
    private static FixtureDesign wide() {
        var d = new FixtureDesign();
        d.parts.add(new FixturePart(FixturePart.Kind.BOX, -16, 20, 20, 80, 4, 8, Direction.DOWN, 0xE0C020, FixturePart.Material.GLASS));   // the whole span
        d.parts.add(new FixturePart(FixturePart.Kind.BOX, 40, 28, 20, 8, 4, 8, Direction.DOWN, 0x111111, FixturePart.Material.MATTE));       // wholly outside
        d.parts.add(new FixturePart(FixturePart.Kind.CHAIN, 23, 0, 23, 2, 40, 2, Direction.DOWN, 0x8E9298, FixturePart.Material.METAL));     // through the anchor
        return d;
    }

    @Test
    void anchorShapeStaysInsideTheBlock() {
        var shape = wide().anchorShape();
        assertFalse(shape.isEmpty());
        for(var box : shape.toAabbs()) {
            assertTrue(box.minX >= -1e-9 && box.maxX <= 1 + 1e-9, "x " + box);
            assertTrue(box.minY >= -1e-9 && box.maxY <= 1 + 1e-9, "y " + box);
            assertTrue(box.minZ >= -1e-9 && box.maxZ <= 1 + 1e-9, "z " + box);
        }
    }

    @Test
    void turnedPartsClipToo() {
        var d = wide();
        for(var p : d.parts) {
            p.rx = 30;
            p.ry = 45;
        }
        for(var box : d.anchorShape().toAabbs()) {
            assertTrue(box.minX >= -1e-6 && box.maxX <= 1 + 1e-6, "x " + box);
            assertTrue(box.minY >= -1e-6 && box.maxY <= 1 + 1e-6, "y " + box);
            assertTrue(box.minZ >= -1e-6 && box.maxZ <= 1 + 1e-6, "z " + box);
        }
    }
}
