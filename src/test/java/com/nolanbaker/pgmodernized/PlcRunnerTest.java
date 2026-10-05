package com.nolanbaker.pgmodernized;

import com.nolanbaker.pgmodernized.device.controls.plc.NodeType;
import com.nolanbaker.pgmodernized.device.controls.plc.PlcGraph;
import com.nolanbaker.pgmodernized.device.controls.plc.PlcProgram;
import com.nolanbaker.pgmodernized.device.controls.plc.PlcRunner;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The block-diagram PLC, scanned against a fake cabinet: tags, logic, timers, rungs and Lua. */
class PlcRunnerTest {
    /** A cabinet with inputs X1.1..X1.8, outputs Y1.1..Y1.8 and one device, "meter1", with getValue. */
    static final class FakeIo implements PlcProgram.Io {
        final Map<String, Double> values = new HashMap<>();
        double meter = 42;
        int calls;

        FakeIo() {
            for(int ch = 1; ch <= 8; ++ch) {
                values.put("X1." + ch, 0.0);
                values.put("Y1." + ch, 0.0);
            }
        }

        @Override
        public double read(String name) {
            return values.getOrDefault(name, Double.NaN);
        }

        @Override
        public boolean write(String name, double value) {
            if(!name.startsWith("Y"))
                return false;
            if(!Double.isNaN(value))
                values.put(name, value);
            return true;
        }

        @Override
        public boolean exists(String name) {
            return values.containsKey(name);
        }

        @Override
        public Object call(String alias, String method, List<Object> args) {
            ++calls;
            if(method.equals("getValue"))
                return meter;
            if(method.equals("setValue")) {
                meter = ((Number) args.get(0)).doubleValue();
                return true;
            }
            return null;
        }

        @Override
        public boolean hasDevice(String alias) {
            return alias.equals("meter1");
        }
    }

    private static final List<List<String>> DEVICES = List.of(List.of("meter1", "powergrid_meter", "getValue", "setValue"));

    private static PlcGraph.Node tag(PlcGraph g, NodeType type, String name) {
        var n = g.add(type, 0, 0);
        n.text = name;
        return n;
    }

    @Test
    void andGateDrivesAnOutput() throws Exception {
        var io = new FakeIo();
        var g = new PlcGraph();
        var a = tag(g, NodeType.TAG, "X1.1");
        var b = tag(g, NodeType.TAG, "X1.2");
        var and = g.add(NodeType.AND, 0, 0);
        var y = tag(g, NodeType.SET, "Y1.1");
        g.link(a.id, 0, and.id, 0);
        g.link(b.id, 0, and.id, 1);
        g.link(and.id, 0, y.id, 0);
        var runner = PlcRunner.compile(g, io, () -> DEVICES);
        runner.scan();
        assertEquals(0, io.values.get("Y1.1"));
        io.values.put("X1.1", 1.0);
        io.values.put("X1.2", 1.0);
        runner.scan();
        assertEquals(1, io.values.get("Y1.1"));
        assertEquals("", runner.lastError());
    }

    @Test
    void sinksRunAfterSourcesWhateverTheDrawingOrder() throws Exception {
        var io = new FakeIo();
        var g = new PlcGraph();
        var y = tag(g, NodeType.SET, "Y1.1");    // drawn first, must still see this scan's value
        var not = g.add(NodeType.NOT, 0, 0);
        var x = tag(g, NodeType.TAG, "X1.1");
        g.link(x.id, 0, not.id, 0);
        g.link(not.id, 0, y.id, 0);
        var runner = PlcRunner.compile(g, io, () -> DEVICES);
        runner.scan();
        assertEquals(1, io.values.get("Y1.1"));
    }

    @Test
    void onDelayTimerCountsTicks() throws Exception {
        var io = new FakeIo();
        var g = new PlcGraph();
        var x = tag(g, NodeType.TAG, "X1.1");
        var ton = g.add(NodeType.TON, 0, 0);
        ton.nums[0] = 3;
        var y = tag(g, NodeType.SET, "Y1.1");
        g.link(x.id, 0, ton.id, 0);
        g.link(ton.id, 0, y.id, 0);
        var runner = PlcRunner.compile(g, io, () -> DEVICES);
        io.values.put("X1.1", 1.0);
        runner.scan();
        runner.scan();
        assertEquals(0, io.values.get("Y1.1"));
        runner.scan();
        assertEquals(1, io.values.get("Y1.1"));
        io.values.put("X1.1", 0.0);
        runner.scan();
        assertEquals(0, io.values.get("Y1.1"));
    }

    @Test
    void latchAndNetworkBitsShareOneTable() throws Exception {
        var io = new FakeIo();
        var g = new PlcGraph();
        var rungs = g.add(NodeType.RUNGS, 0, 0);
        rungs.text = "N1 = X1.1\nC1 S= X1.2";
        var n1 = tag(g, NodeType.TAG, "N1");
        var c1 = tag(g, NodeType.TAG, "C1");
        var or = g.add(NodeType.OR, 0, 0);
        var y = tag(g, NodeType.SET, "Y1.1");
        g.link(n1.id, 0, or.id, 0);
        g.link(c1.id, 0, or.id, 1);
        g.link(or.id, 0, y.id, 0);
        var runner = PlcRunner.compile(g, io, () -> DEVICES);
        runner.scan();
        assertEquals(0, io.values.get("Y1.1"));
        io.values.put("X1.2", 1.0);
        runner.scan();
        assertEquals(1, io.values.get("Y1.1"));
        io.values.put("X1.2", 0.0);
        runner.scan();
        assertEquals(1, io.values.get("Y1.1"), "the coil stays set");
        assertEquals(1.0, runner.bit("C1"));
    }

    @Test
    void deviceCallOnRisingEdgeOnly() throws Exception {
        var io = new FakeIo();
        var g = new PlcGraph();
        var x = tag(g, NodeType.TAG, "X1.1");
        var call = g.add(NodeType.CALL, 0, 0);
        call.text = "meter1.setValue";
        call.nums[0] = 1;   // one argument
        call.nums[1] = 1;   // when En rises
        var k = g.add(NodeType.CONST, 0, 0);
        k.nums[0] = 7;
        g.link(x.id, 0, call.id, 0);
        g.link(k.id, 0, call.id, 1);
        var runner = PlcRunner.compile(g, io, () -> DEVICES);
        runner.scan();
        assertEquals(0, io.calls);
        io.values.put("X1.1", 1.0);
        runner.scan();
        runner.scan();
        assertEquals(1, io.calls, "called once on the edge, not every scan");
        assertEquals(7, io.meter);
    }

    @Test
    void luaBlockReadsPinsTagsAndDevices() throws Exception {
        var io = new FakeIo();
        var g = new PlcGraph();
        var x = tag(g, NodeType.TAG, "X1.1");
        var lua = g.add(NodeType.LUA, 0, 0);
        lua.nums[0] = 1;
        lua.nums[1] = 2;
        lua.text = "count = (count or 0) + 1\n"
                + "Out[1] = In[1] ~= 0 and tag('X1.2') == 1\n"
                + "Out[2] = call('meter1', 'getValue') + count\n"
                + "tag('Y1.3', In[1])\n";
        var y = tag(g, NodeType.SET, "Y1.1");
        g.link(x.id, 0, lua.id, 0);
        g.link(lua.id, 0, y.id, 0);
        var runner = PlcRunner.compile(g, io, () -> DEVICES);
        io.values.put("X1.1", 1.0);
        io.values.put("X1.2", 1.0);
        runner.scan();
        assertEquals("", runner.lastError());
        assertEquals(1, io.values.get("Y1.1"));
        assertEquals(1, io.values.get("Y1.3"));
        runner.scan();
        assertEquals(44f, runner.values()[2], "the second Lua output is meter + scan count, and globals persist");
    }

    @Test
    void luaRuntimeErrorsAreReportedNotFatal() throws Exception {
        var io = new FakeIo();
        var g = new PlcGraph();
        var lua = g.add(NodeType.LUA, 0, 0);
        lua.text = "Out[1] = nil + 1";
        var runner = PlcRunner.compile(g, io, () -> DEVICES);
        runner.scan();
        assertTrue(runner.lastError().startsWith("#" + lua.id + " Lua:"), runner.lastError());
        runner.scan();   // still scanning
    }

    @Test
    void luaInstructionBudgetStopsRunawayLoops() throws Exception {
        var io = new FakeIo();
        var g = new PlcGraph();
        var lua = g.add(NodeType.LUA, 0, 0);
        lua.text = "while true do end";
        var runner = PlcRunner.compile(g, io, () -> DEVICES);
        runner.scan();
        assertTrue(runner.lastError().contains("instructions"), runner.lastError());
    }

    @Test
    void luaSandboxHasNoFilesOrLoading() throws Exception {
        var io = new FakeIo();
        var g = new PlcGraph();
        var lua = g.add(NodeType.LUA, 0, 0);
        lua.nums[1] = 4;
        lua.text = "Out[1] = io == nil and os == nil\nOut[2] = load == nil and dofile == nil and require == nil\nOut[3] = debug == nil\nOut[4] = pcall(function() return ('x'):rep(10000000) end) == false";
        var runner = PlcRunner.compile(g, io, () -> DEVICES);
        runner.scan();
        assertEquals("", runner.lastError());
        var v = runner.values();
        assertArrayEquals(new float[] {1, 1, 1, 1}, v);
    }

    @Test
    void unknownTagIsACompileErrorNamingTheBlock() {
        var io = new FakeIo();
        var g = new PlcGraph();
        var bad = tag(g, NodeType.TAG, "X9.1");
        var e = assertThrows(PlcRunner.CompileError.class, () -> PlcRunner.compile(g, io, () -> DEVICES));
        assertEquals(bad.id, e.node);
    }

    @Test
    void graphSurvivesSaveAndLoad() {
        var g = new PlcGraph();
        var x = tag(g, NodeType.TAG, "X1.1");
        var lua = g.add(NodeType.LUA, 10, 20);
        lua.text = "Out[1] = In[1]\n";
        lua.nums[0] = 3;
        g.link(x.id, 0, lua.id, 2);
        var copy = PlcGraph.load(g.save());
        assertEquals(2, copy.nodes.size());
        assertEquals(1, copy.links.size());
        assertEquals(3, copy.node(lua.id).inputs());
        assertEquals(lua.text, copy.node(lua.id).text);
        assertEquals(g, copy);
    }
}
