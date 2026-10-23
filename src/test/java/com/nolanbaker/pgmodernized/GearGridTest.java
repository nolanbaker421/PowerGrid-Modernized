package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.device.breaker.PanelLayout;
import com.nolanbaker.pgmodernized.device.gear.GearKind;
import com.nolanbaker.pgmodernized.device.gear.GearLayout;
import com.nolanbaker.pgmodernized.device.gear.GearSide;
import com.nolanbaker.pgmodernized.device.gear.GearSlot;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

/** The slot grid of a switchgear cell: which sides carry what, how terminals are numbered, and where a click lands. */
public class GearGridTest {
    @Test void barsOfferTheirConductorsOnEverySide() {
        for(var side : GearSide.values()) {
            Assertions.assertEquals(GearKind.PHASES, GearKind.BUS.portMask(side, 0));
            Assertions.assertEquals(GearKind.NEUTRAL, GearKind.NEUTRAL_BAR.portMask(side, 0));
        }
    }

    @Test void aBreakerTakesLinesAboveAndGivesLoadsBelowUntilTurned() {
        Assertions.assertEquals(GearKind.PHASES, GearKind.BREAKER.portMask(GearSide.UP, 0));
        Assertions.assertEquals(GearKind.PHASES, GearKind.BREAKER.portMask(GearSide.DOWN, 0));
        Assertions.assertEquals(0, GearKind.BREAKER.portMask(GearSide.LEFT, 0));
        Assertions.assertEquals(0, GearKind.BREAKER.portMask(GearSide.RIGHT, 0));
        // A quarter turn clockwise: the line side is now on the viewer's right, the load side on the left.
        Assertions.assertEquals(GearKind.PHASES, GearKind.BREAKER.portMask(GearSide.RIGHT, 1));
        Assertions.assertEquals(GearKind.PHASES, GearKind.BREAKER.portMask(GearSide.LEFT, 1));
        Assertions.assertEquals(0, GearKind.BREAKER.portMask(GearSide.UP, 1));
        var slot = new GearSlot();
        slot.install(GearKind.SWITCH, 0);
        slot.rotate();
        Assertions.assertEquals(GearSide.RIGHT, slot.lineSide());
        Assertions.assertEquals(GearSide.LEFT, slot.loadSide());
        Assertions.assertTrue(slot.carries(GearSide.RIGHT, 0));
        Assertions.assertFalse(slot.carries(GearSide.RIGHT, 3), "a switch has no neutral pole");
    }

    @Test void lugBlocksHaveNothingBelowThem() {
        Assertions.assertEquals(GearKind.ALL_CONDUCTORS, GearKind.LUGS.portMask(GearSide.UP, 0));
        Assertions.assertEquals(GearKind.ALL_CONDUCTORS, GearKind.LUGS.portMask(GearSide.LEFT, 0));
        Assertions.assertEquals(0, GearKind.LUGS.portMask(GearSide.DOWN, 0));
    }

    @Test void terminalNumberingRoundTripsAndDoesNotOverlap() {
        var seen = new HashSet<Integer>();
        for(var side : GearSide.values()) {
            for(int i = 0; i < GearLayout.COLS; ++i) {
                for(int k = 0; k < GearLayout.CONDUCTORS; ++k) {
                    int t = GearLayout.edge(side, i, k);
                    Assertions.assertTrue(GearLayout.isEdge(t));
                    Assertions.assertEquals(side, GearLayout.edgeSide(t));
                    Assertions.assertEquals(i, GearLayout.edgeIndex(t));
                    Assertions.assertEquals(k, GearLayout.edgeConductor(t));
                    Assertions.assertTrue(seen.add(t));
                }
            }
        }
        for(int slot = 0; slot < GearLayout.SLOTS; ++slot) {
            for(int k = 0; k < GearLayout.CONDUCTORS; ++k) {
                int t = GearLayout.lug(slot, k);
                Assertions.assertTrue(GearLayout.isLug(t));
                Assertions.assertFalse(GearLayout.isEdge(t));
                Assertions.assertEquals(slot, GearLayout.lugSlot(t));
                Assertions.assertEquals(k, GearLayout.lugConductor(t));
                Assertions.assertTrue(seen.add(t));
            }
        }
        for(int h = 0; h < GearLayout.HUB_COUNT; ++h) {
            int t = GearLayout.hubTerminal(h);
            Assertions.assertEquals(h, GearLayout.hubAt(t));
            Assertions.assertTrue(seen.add(t));
            for(int k = 0; k < GearLayout.PER_HUB; ++k) {
                int c = GearLayout.conductorTerminal(h, k);
                Assertions.assertEquals(h, GearLayout.conductorHub(c));
                Assertions.assertEquals(k, GearLayout.conductorOf(c));
                Assertions.assertTrue(seen.add(c));
            }
        }
        Assertions.assertEquals(GearLayout.TERMINAL_COUNT, seen.size());
        Assertions.assertEquals(GearLayout.TERMINAL_COUNT, GearLayout.terminals().length);
    }

    @Test void edgeSlotsAndTheirIndicesAgree() {
        for(var side : GearSide.values()) {
            for(int i = 0; i < GearLayout.COLS; ++i) {
                int slot = GearLayout.slotOnEdge(side, i);
                Assertions.assertEquals(i, GearLayout.edgeIndexOf(slot, side));
                Assertions.assertEquals(-1, GearLayout.neighbour(slot, side), "an edge slot has no neighbour past the edge");
            }
        }
        Assertions.assertEquals(-1, GearLayout.edgeIndexOf(GearLayout.slot(1, 1), GearSide.UP), "the middle slot is on no edge");
        Assertions.assertEquals(GearLayout.slot(1, 0), GearLayout.neighbour(GearLayout.slot(1, 1), GearSide.UP));
        Assertions.assertEquals(GearLayout.slot(2, 1), GearLayout.neighbour(GearLayout.slot(1, 1), GearSide.RIGHT));
    }

    @Test void aClickOnTheDoorFindsItsSlotWhicheverWayTheCellFaces() {
        for(var facing : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            for(int slot = 0; slot < GearLayout.SLOTS; ++slot) {
                var center = GearLayout.slotCenter(slot);
                var local = PanelLayout.fromNorthFrame(center, facing).scale(1 / 16.0);
                Assertions.assertEquals(slot, GearLayout.slotAt(facing, facing, local), "facing " + facing + " slot " + slot);
                Assertions.assertEquals(-1, GearLayout.slotAt(facing, facing.getOpposite(), local), "the back has no slots");
            }
        }
        // Column 0 is on the viewer's left: +x in the north frame.
        var leftTop = GearLayout.slotRect(GearLayout.slot(0, 0));
        Assertions.assertTrue(leftTop[0] > 10 && leftTop[3] > 15);
        Assertions.assertEquals(-1, GearLayout.slotAt(Direction.NORTH, Direction.NORTH, new Vec3(0.99, 0.99, 0.01)), "the frame around the grid is no slot");
    }

    @Test void theSidesOfACellMapToWorldDirections() {
        Assertions.assertEquals(Direction.EAST, GearSide.LEFT.world(Direction.NORTH), "someone looking at a north door has east on their left");
        Assertions.assertEquals(Direction.WEST, GearSide.RIGHT.world(Direction.NORTH));
        Assertions.assertEquals(Direction.UP, GearSide.UP.world(Direction.SOUTH));
        for(var facing : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            for(var side : GearSide.values())
                Assertions.assertEquals(side, GearSide.of(side.world(facing), facing));
            Assertions.assertNull(GearSide.of(facing, facing));
        }
    }

    @Test void slotsRoundTripThroughNbt() {
        var fuse = new GearSlot();
        fuse.install(GearKind.FUSE, 400);
        fuse.rotate();
        fuse.setLabel("Pump");
        var copy = new GearSlot();
        copy.read(fuse.write(false), false);
        Assertions.assertEquals(GearKind.FUSE, copy.kind());
        Assertions.assertEquals(400, copy.fuseRating());
        Assertions.assertEquals(1, copy.rotation());
        Assertions.assertEquals("Pump", copy.label());
        Assertions.assertTrue(copy.closed(), "fresh fuses conduct");

        var sw = new GearSlot();
        sw.install(GearKind.SWITCH, 0);
        sw.setOn(true);
        copy.read(sw.write(false), false);
        Assertions.assertTrue(copy.isOn());
        Assertions.assertFalse(copy.blown());

        var empty = new GearSlot();
        copy.read(empty.write(false), false);
        Assertions.assertFalse(copy.installed());
        Assertions.assertEquals(0, copy.portMask(GearSide.UP));
    }

    @Test void fusesBlowOnAnOverloadAndStayBlown() {
        var slot = new GearSlot();
        slot.install(GearKind.FUSE, 100);
        for(int i = 0; i < 20 && !slot.blown(); ++i)
            slot.tickFuse(50);
        Assertions.assertFalse(slot.blown(), "half the rating never blows them");
        boolean blew = false;
        for(int i = 0; i < 200 && !blew; ++i)
            blew = slot.tickFuse(1000);
        Assertions.assertTrue(blew);
        Assertions.assertTrue(slot.blown());
        Assertions.assertFalse(slot.closed());
        Assertions.assertFalse(slot.tickFuse(1000), "a blown set does not blow again");
    }
}
