package com.nolanbaker.pgmodernized;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.GraphedElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;

/**
 * Mirrors the monitor module's circuit in GangBoxBlockEntity.buildCircuit, on a fire alarm
 * panel's initiating loop: the supervisory resistor and detector contact across Loop + and
 * Loop -, and the sense source across Contact A and B, read for current, with the dry contact a
 * splice-resistance wire across them. The module trips when that current passes a milliamp.
 */
public class MonitorSenseTest extends TestHelper {
    private static final float SENSE_VOLTS = 5, SENSE_OHMS = 1000, SPLICE_OHMS = 0.0005f, SENSE_AMPS = 0.001f;

    private record Probe(double senseAmps, double loopAmps, boolean converged) {}

    private static Probe run(String name, ElectricalNetwork network, boolean closed, boolean reference) {
        network.warmUp(-1);
        // The panel: a 24 V loop source.
        var plus = new FloatingNode();
        var minus = new FloatingNode();
        network.addNode(plus);
        network.addNode(minus);
        var idc = new ProvidedVoltageSourceCoupling(plus, minus, 100);
        idc.setVoltageProvider(() -> 24f);
        network.addNode(idc);
        // The module's loop points, spliced to the loop, with its supervisory resistor and open detector contact.
        var loopPlus = new FloatingNode();
        var loopMinus = new FloatingNode();
        network.addNode(loopPlus);
        network.addNode(loopMinus);
        network.addWire(new ElectricWire(SPLICE_OHMS, plus, loopPlus));
        network.addWire(new ElectricWire(SPLICE_OHMS, minus, loopMinus));
        network.addWire(new ElectricWire(20000, loopPlus, loopMinus));
        network.addWire(new SwitchedWire(1000, loopPlus, loopMinus, false));
        // The sense circuit across Contact A and B.
        var a = new FloatingNode();
        var b = new FloatingNode();
        network.addNode(a);
        network.addNode(b);
        var sense = new ProvidedVoltageSourceCoupling(a, b, SENSE_OHMS);
        sense.setVoltageProvider(() -> SENSE_VOLTS);
        network.addNode(sense);
        if(closed)
            network.addWire(new ElectricWire(SPLICE_OHMS, a, b));
        if(reference)
            network.addWire(new ElectricWire(1e6f, b, loopMinus));
        network.calculate(5);
        var probe = new Probe(sense.getCurrent(), idc.getCurrent(), network.isConverged());
        System.out.println(name + ": " + probe);
        return probe;
    }

    @Test void aJumperAcrossTheContactsDrawsTheSenseCurrent() {
        for(boolean gMin : new boolean[] {true, false}) {
            for(boolean graphed : new boolean[] {false, true}) {
                for(boolean reference : new boolean[] {false, true}) {
                    String tag = (graphed ? "graphed" : "plain") + (gMin ? " gmin" : " nogmin") + (reference ? " ref" : " free");
                    run(tag + " closed", graphed ? new GraphedElectricalNetwork(gMin) : new ElectricalNetwork(gMin), true, reference);
                    run(tag + " open", graphed ? new GraphedElectricalNetwork(gMin) : new ElectricalNetwork(gMin), false, reference);
                }
            }
        }
        var closed = run("final: plain gmin ref closed", new ElectricalNetwork(true), true, true);
        var open = run("final: plain gmin ref open", new ElectricalNetwork(true), false, true);
        Assertions.assertTrue(Math.abs(closed.senseAmps) >= SENSE_AMPS, "closed contact reads " + closed.senseAmps);
        Assertions.assertTrue(Math.abs(open.senseAmps) < SENSE_AMPS, "open contact reads " + open.senseAmps);
    }
}
