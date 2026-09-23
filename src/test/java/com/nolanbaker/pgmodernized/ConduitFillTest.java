package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.conduit.ConduitFill;
import com.nolanbaker.pgmodernized.conduit.ConduitSize;
import com.nolanbaker.pgmodernized.conduit.WireGauge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Conduit fill against NEC Annex C (EMT, THHN). */
public class ConduitFillTest {
    @Test void fillLimitsByCount() {
        Assertions.assertEquals(0.53f, ConduitFill.limit(1));
        Assertions.assertEquals(0.31f, ConduitFill.limit(2));
        Assertions.assertEquals(0.40f, ConduitFill.limit(3));
        Assertions.assertEquals(0.40f, ConduitFill.limit(12));
    }

    @Test void annexCCounts() {
        Assertions.assertEquals(12, ConduitFill.maxCount(ConduitSize.ONE, WireGauge.AWG_12), "1\" EMT holds 16 x 12 AWG; the slots cap it at 12");
        Assertions.assertEquals(4, ConduitFill.maxCount(ConduitSize.HALF, WireGauge.AWG_12), "1/2\" EMT holds 9 x 12 AWG; the slots cap it at 4");
        Assertions.assertEquals(1, ConduitFill.maxCount(ConduitSize.HALF, WireGauge.AWG_4), "1/2\" EMT takes one 4 AWG");
        Assertions.assertEquals(4, ConduitFill.maxCount(ConduitSize.TWO, WireGauge.AWG_4_0), "2\" EMT takes four 4/0");
        Assertions.assertEquals(5, ConduitFill.maxCount(ConduitSize.THREE, WireGauge.KCMIL_500), "3\" EMT takes five 500 kcmil");
        Assertions.assertEquals(3, ConduitFill.maxCount(ConduitSize.ONE_QUARTER, WireGauge.AWG_1_0), "1-1/4\" EMT takes three 1/0");
        Assertions.assertEquals(0, ConduitFill.maxCount(ConduitSize.HALF, WireGauge.KCMIL_500), "500 kcmil does not go in 1/2\"");
    }

    @Test void foreignWiresTakeTheGaugeOfTheirRating() {
        Assertions.assertEquals(WireGauge.AWG_4, WireGauge.forAmpacity(80), "Power Grid's copper wire, 80 A");
        Assertions.assertEquals(WireGauge.AWG_2_0, WireGauge.forAmpacity(160), "iron and golden wire, 160 A");
        Assertions.assertEquals(WireGauge.AWG_14, WireGauge.forAmpacity(5));
        Assertions.assertEquals(WireGauge.KCMIL_500, WireGauge.forAmpacity(2000), "past the table the largest gauge stands in");
    }

    @Test void fitsAndPercent() {
        double used = 2 * WireGauge.AWG_12.areaSqIn();
        Assertions.assertTrue(ConduitFill.fits(ConduitSize.HALF, used, 2, WireGauge.AWG_12.areaSqIn()), "a third 12 AWG in 1/2\"");
        Assertions.assertFalse(ConduitFill.fits(ConduitSize.HALF, used, 2, WireGauge.AWG_2.areaSqIn()), "not a 2 AWG beside them");
        Assertions.assertEquals(9, ConduitFill.percent(ConduitSize.HALF, used));
        Assertions.assertEquals(53, ConduitFill.percentLimit(1));
    }
}
