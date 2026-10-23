package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.radio.RadioMath;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Bearings, aims and the coax loss, as the radios use them. */
class RadioMathTest {
    @Test void bearingsAreCompassBearings() {
        var o = new Vec3(0, 64, 0);
        assertEquals(0, RadioMath.bearing(o, new Vec3(0, 64, -100)), 1e-9, "north is -z");
        assertEquals(90, RadioMath.bearing(o, new Vec3(100, 64, 0)), 1e-9, "east is +x");
        assertEquals(180, RadioMath.bearing(o, new Vec3(0, 64, 100)), 1e-9);
        assertEquals(270, RadioMath.bearing(o, new Vec3(-100, 64, 0)), 1e-9);
        assertEquals(45, RadioMath.elevation(o, new Vec3(100, 164, 0)), 1e-9);
    }

    @Test void anAimedDishSeesItsTargetAndMissesOneADegreeOff() {
        var a = new Vec3(0, 64, 0);
        var b = new Vec3(1500, 80, -1000);
        double az = RadioMath.bearing(a, b), el = RadioMath.elevation(a, b);
        assertEquals(0, RadioMath.offAxis(a, az, el, b), 1e-6);
        assertEquals(1.5, RadioMath.offAxis(a, az + 1.5, el, b), 0.01, "turning the dish turns the error");
        assertTrue(RadioMath.offAxis(a, az + 180, el, b) > 170, "pointing away");
        assertEquals(1500 > 0 ? RadioMath.bearing(b, a) : 0, RadioMath.normalize(az + 180), 1e-6, "the way back");
    }

    @Test void aSectorCoversItsBeamAndNothingBehindIt() {
        var tower = new Vec3(0, 80, 0);
        assertTrue(RadioMath.offBearing(tower, 90, new Vec3(500, 64, 200)) < 60, "inside a 120 degree beam facing east");
        assertTrue(RadioMath.offBearing(tower, 90, new Vec3(-500, 64, 0)) > 60, "behind it");
        assertEquals(-179, RadioMath.difference(1, 180), 1e-9);
        assertEquals(2, RadioMath.difference(1, 359), 1e-9, "wraps round north");
    }

    @Test void coaxLossIsFreeUpToTheFullLengthAndHalfAtTheLimit() {
        assertEquals(1, RadioMath.coaxFactor(10, 16, 64), 1e-9);
        assertEquals(1, RadioMath.coaxFactor(16, 16, 64), 1e-9);
        assertEquals(0.75, RadioMath.coaxFactor(40, 16, 64), 1e-9);
        assertEquals(0.5, RadioMath.coaxFactor(64, 16, 64), 1e-9);
        assertTrue(RadioMath.coaxFactor(100, 16, 64) < 0.5);
    }
}
