package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.device.breaker.BreakerFrame;
import com.nolanbaker.pgmodernized.device.breaker.PanelSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BreakerFrameTest {
    @Test void framesTileTheRatingRange() {
        Assertions.assertEquals(BreakerFrame.F50, BreakerFrame.forRating(20));
        Assertions.assertEquals(BreakerFrame.F50, BreakerFrame.forRating(50));
        Assertions.assertEquals(BreakerFrame.F200, BreakerFrame.forRating(51));
        Assertions.assertEquals(BreakerFrame.F400, BreakerFrame.forRating(400));
        Assertions.assertEquals(BreakerFrame.F800, BreakerFrame.forRating(800));
        Assertions.assertEquals(BreakerFrame.F800, BreakerFrame.forRating(2000), "anything larger lands on the biggest frame");
    }

    @Test void ratingsSnapToTheFrameStep() {
        Assertions.assertEquals(37, BreakerFrame.F50.clamp(37));
        Assertions.assertEquals(1, BreakerFrame.F50.clamp(-5));
        Assertions.assertEquals(50, BreakerFrame.F50.clamp(999));
        Assertions.assertEquals(205, BreakerFrame.F400.clamp(203));
        Assertions.assertEquals(210, BreakerFrame.F400.clamp(208));
        Assertions.assertEquals(600, BreakerFrame.F800.clamp(604));
        Assertions.assertEquals(BreakerFrame.F400.settings(), (400 - 205) / 5 + 1);
        for(var frame : BreakerFrame.values()) {
            Assertions.assertEquals(frame.min(), frame.ratingOf(0));
            Assertions.assertEquals(frame.max(), frame.ratingOf(frame.settings() - 1));
            Assertions.assertEquals(frame.settings() - 1, frame.settingOf(frame.max()));
        }
    }

    @Test void biggerFramesNeedMoreRows() {
        // A 3-pole 401-800 A breaker needs nine rows: too big for any branch column, fine as a main.
        Assertions.assertEquals(9, 3 * BreakerFrame.F800.rows());
        Assertions.assertFalse(PanelSpec.THREE_800.fits(0, 9));
        // A 2-pole 401-800 A breaker takes a whole six-row column of a 12-space panel.
        Assertions.assertTrue(PanelSpec.THREE_800.fits(0, 6));
        Assertions.assertFalse(PanelSpec.THREE_800.fits(2, 6));
        // Rows alternate lugs, so a pole two rows down is on the same lug as its head; the panel
        // therefore assigns pole lugs by pole index, not by row.
        Assertions.assertEquals(PanelSpec.SPLIT_200.leg(0), PanelSpec.SPLIT_200.leg(4));
    }
}
