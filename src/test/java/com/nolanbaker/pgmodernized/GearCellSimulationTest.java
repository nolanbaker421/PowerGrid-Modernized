package com.nolanbaker.pgmodernized;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

/**
 * Mirrors GearCellBlockEntity.buildCircuit for a two-cell lineup: feed lugs in the first cell on
 * a bus that runs across the cell edge (a hidden bus bar link) into the second cell, down through
 * a breaker to a lug block, and out to a load back to the neutral bar.
 */
public class GearCellSimulationTest extends TestHelper {
    private static final float BUS_R = 0.00005f, LINK_R = 0.00005f, POLE_R = 0.0005f;

    private static double amps(SwitchedWire wire) {
        return Math.abs(wire.current());
    }

    @Test void aBreakerOnTheBusFeedsTheLugsBelowItAcrossCells() {
        var Net = new Network();
        var feed = Net.V(277);
        var neutral = Net.V(0);
        // Cell A: feed lugs, their top port a bar slot whose right port is the cell's edge terminal.
        var lugA = Net.N();
        var barA = Net.N();
        var edgeA = Net.N();
        Net.W(BUS_R, feed, lugA);
        Net.W(BUS_R, lugA, barA);
        Net.W(BUS_R, barA, edgeA);
        // The hidden bus bar between the cells.
        var edgeB = Net.N();
        Net.W(LINK_R, edgeA, edgeB);
        // Cell B: a bar from the edge to the slot above the breaker, the breaker, a lug block below.
        var barB = Net.N();
        var lugB = Net.N();
        Net.W(BUS_R, edgeB, barB);
        var pole = Net.SW(POLE_R, barB, lugB, true);
        Net.W(27.7f, lugB, neutral);
        Net.calculate(5);
        Assertions.assertEquals(10, amps(pole), 0.1, "277 V over 27.7 ohms through the lineup");
        pole.setState(false);
        Net.calculate(5);
        Assertions.assertEquals(0, amps(pole), 0.01, "an open breaker isolates the lugs");
    }

    @Test void twoBreakersOnOneBusShareTheFeed() {
        var Net = new Network();
        var feed = Net.V(277);
        var neutral = Net.V(0);
        var bus = Net.N();
        var bus2 = Net.N();
        Net.W(BUS_R, feed, bus);
        Net.W(BUS_R, bus, bus2);
        var lug1 = Net.N();
        var lug2 = Net.N();
        var pole1 = Net.SW(POLE_R, bus, lug1, true);
        var pole2 = Net.SW(POLE_R, bus2, lug2, true);
        Net.W(27.7f, lug1, neutral);
        Net.W(55.4f, lug2, neutral);
        Net.calculate(5);
        Assertions.assertEquals(10, amps(pole1), 0.1);
        Assertions.assertEquals(5, amps(pole2), 0.1);
        pole2.setState(false);
        Net.calculate(5);
        Assertions.assertEquals(10, amps(pole1), 0.1, "the other circuit is untouched");
        Assertions.assertEquals(0, amps(pole2), 0.01);
    }
}
