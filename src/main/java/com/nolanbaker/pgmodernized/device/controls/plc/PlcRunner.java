package com.nolanbaker.pgmodernized.device.controls.plc;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Runs a {@link PlcGraph}: one scan evaluates every block in wire order, sources before sinks,
 * with any feedback loop reading the previous scan's value. Coils, timers and network bits named
 * from rung blocks and tag blocks share one table, so a rung's {@code N1} and a Tag block reading
 * {@code N1} are the same bit.
 */
public final class PlcRunner {
    public static final class CompileError extends Exception {
        public final int node;

        CompileError(int node, String message) {
            super(message);
            this.node = node;
        }
    }

    private static final class State {
        final double[] mem = new double[4];
        double[] lastArgs;
        @Nullable PlcProgram rungs;
        @Nullable LuaBlock lua;
        String message = "";
    }

    private final PlcGraph graph;
    private final PlcProgram.Io io;
    private final List<PlcGraph.Node> order;
    private final Map<Integer, double[]> out = new HashMap<>();
    /** Text a block produced on a pin, beside its number: a text constant, a text tag, a call's string result. */
    private final Map<Integer, Object[]> raw = new HashMap<>();
    private final Map<Integer, State> states = new HashMap<>();
    private final Map<Integer, PlcGraph.Link[]> feeds = new HashMap<>();
    private final Map<String, Double> bits;
    private String error = "";

    private PlcRunner(PlcGraph graph, PlcProgram.Io io, List<PlcGraph.Node> order, Map<String, Double> bits) {
        this.graph = graph;
        this.io = io;
        this.order = order;
        this.bits = bits;
    }

    public Map<String, Double> bits() {
        return bits;
    }

    public double bit(String name) {
        return bits.getOrDefault(name, 0.0);
    }

    public void setBit(String name, double value) {
        bits.put(name, value);
    }

    public String lastError() {
        return error;
    }

    /** Every block's output values, in the graph's block order, for the editor to show. */
    public float[] values() {
        int n = 0;
        for(var node : graph.nodes)
            n += node.outputs();
        var values = new float[n];
        int i = 0;
        for(var node : graph.nodes) {
            var o = out.get(node.id);
            for(int k = 0; k < node.outputs(); ++k)
                values[i++] = o == null || k >= o.length ? 0 : (float) o[k];
        }
        return values;
    }

    /** What each block last printed or failed with, in block order; empty for most. */
    public List<String> messages() {
        var list = new ArrayList<String>(graph.nodes.size());
        for(var node : graph.nodes) {
            var state = states.get(node.id);
            list.add(state == null ? "" : state.message);
        }
        return list;
    }

    // ---- compiling ----

    public static PlcRunner compile(PlcGraph graph, PlcProgram.Io io, Supplier<List<List<String>>> devices) throws CompileError {
        return compile(graph, io, devices, new HashMap<>());
    }

    /** Compiles over a shared bit table: the coils, timers and network bits live on in it between programs. */
    public static PlcRunner compile(PlcGraph graph, PlcProgram.Io io, Supplier<List<List<String>>> devices, Map<String, Double> bits) throws CompileError {
        graph.sanitise();
        var runner = new PlcRunner(graph, io, order(graph), bits);
        for(var node : graph.nodes) {
            var state = new State();
            runner.states.put(node.id, state);
            runner.out.put(node.id, new double[node.outputs()]);
            runner.raw.put(node.id, new Object[node.outputs()]);
            var into = new PlcGraph.Link[node.inputs()];
            for(var link : graph.links)
                if(link.to() == node.id && link.toPin() < into.length)
                    into[link.toPin()] = link;
            runner.feeds.put(node.id, into);
            switch(node.type) {
                case TAG -> {
                    var name = normalise(node.text);
                    if(!isBit(name) && !isTextName(name) && !io.exists(name))
                        throw new CompileError(node.id, "unknown tag " + node.text);
                }
                case SET -> {
                    var name = normalise(node.text);
                    if(!isNetworkOrCoil(name) && !isTextName(name) && !io.write(name, Double.NaN))
                        throw new CompileError(node.id, "cannot write tag " + node.text);
                }
                case CALL -> {
                    int dot = node.text.lastIndexOf('.');
                    if(dot <= 0 || dot == node.text.length() - 1)
                        throw new CompileError(node.id, "pick a device and method");
                    if(!io.hasDevice(node.text.substring(0, dot)))
                        throw new CompileError(node.id, "no device called " + node.text.substring(0, dot) + " on the internal network");
                }
                case LUA -> {
                    try {
                        state.lua = new LuaBlock(node.text, io, devices, s -> state.message = s);
                    } catch(org.luaj.vm2.LuaError e) {
                        throw new CompileError(node.id, e.getMessage());
                    }
                }
                case RUNGS -> {
                    try {
                        state.rungs = PlcProgram.compile(node.text, io);
                        state.rungs.useBits(runner.bits);
                    } catch(PlcProgram.CompileError e) {
                        throw new CompileError(node.id, "line " + e.line + ": " + e.getMessage());
                    }
                }
                default -> {}
            }
        }
        return runner;
    }

    /** Sources before the blocks they feed; whatever is left in a loop keeps its drawing order. */
    private static List<PlcGraph.Node> order(PlcGraph graph) {
        var indegree = new HashMap<Integer, Integer>();
        for(var node : graph.nodes)
            indegree.put(node.id, 0);
        for(var link : graph.links)
            if(indegree.containsKey(link.from()) && indegree.containsKey(link.to()))
                indegree.merge(link.to(), 1, Integer::sum);
        var ready = new ArrayDeque<PlcGraph.Node>();
        for(var node : graph.nodes)
            if(indegree.get(node.id) == 0)
                ready.add(node);
        var order = new ArrayList<PlcGraph.Node>(graph.nodes.size());
        var placed = new java.util.HashSet<Integer>();
        while(!ready.isEmpty()) {
            var node = ready.poll();
            if(!placed.add(node.id))
                continue;
            order.add(node);
            for(var link : graph.links) {
                if(link.from() != node.id || !indegree.containsKey(link.to()))
                    continue;
                int left = indegree.merge(link.to(), -1, Integer::sum);
                if(left == 0)
                    ready.add(graph.node(link.to()));
            }
        }
        for(var node : graph.nodes)
            if(placed.add(node.id))
                order.add(node);
        return order;
    }

    // ---- scanning ----

    public void scan() {
        String firstError = "";
        for(var node : order) {
            try {
                run(node);
            } catch(Exception e) {
                var message = e.getMessage() == null ? e.toString() : e.getMessage();
                states.get(node.id).message = message;
                if(firstError.isEmpty())
                    firstError = "#" + node.id + " " + node.type.title + ": " + message;
            }
        }
        error = firstError;
    }

    private double in(PlcGraph.Node node, int pin, double unlinked) {
        var links = feeds.get(node.id);
        var link = pin < links.length ? links[pin] : null;
        if(link == null)
            return unlinked;
        var source = out.get(link.from());
        return source == null || link.fromPin() >= source.length ? 0 : source[link.fromPin()];
    }

    private double in(PlcGraph.Node node, int pin) {
        return in(node, pin, 0);
    }

    /** What feeds a pin: a String when the source produced text, otherwise its number as a Double. */
    private Object inRaw(PlcGraph.Node node, int pin) {
        var links = feeds.get(node.id);
        var link = pin < links.length ? links[pin] : null;
        if(link == null)
            return 0.0;
        var text = raw.get(link.from());
        if(text != null && link.fromPin() < text.length && text[link.fromPin()] instanceof String s)
            return s;
        return in(node, pin);
    }

    /** S1, S2, ...: the text tags. */
    static boolean isTextName(String name) {
        if(name.length() < 2 || name.charAt(0) != 'S')
            return false;
        for(int i = 1; i < name.length(); ++i)
            if(!Character.isDigit(name.charAt(i)))
                return false;
        return true;
    }

    private static String stringOf(Object v) {
        return v instanceof String s ? s : NodeType.format(((Number) v).doubleValue());
    }

    private static boolean on(double v) {
        return v != 0;
    }

    private void run(PlcGraph.Node node) throws Exception {
        var o = out.get(node.id);
        var r = raw.get(node.id);
        var s = states.get(node.id);
        var m = s.mem;
        switch(node.type) {
            case TAG -> {
                var name = normalise(node.text);
                if(isTextName(name)) {
                    var text = io.readText(name);
                    r[0] = text == null ? "" : text;
                    o[0] = text == null || text.isEmpty() ? 0 : 1;
                } else {
                    o[0] = readTag(name);
                }
            }
            case SET -> {
                var name = normalise(node.text);
                if(isTextName(name))
                    io.writeText(name, stringOf(inRaw(node, 0)));
                else
                    writeTag(name, in(node, 0));
            }
            case CONST -> {
                if(node.text.isEmpty()) {
                    o[0] = node.num(0);
                } else {
                    r[0] = node.text;
                    o[0] = toNumber(node.text);
                }
            }
            case AND -> {
                boolean q = true;
                for(int i = 0; i < node.inputs(); ++i)
                    q &= on(in(node, i));
                o[0] = q ? 1 : 0;
            }
            case OR -> {
                boolean q = false;
                for(int i = 0; i < node.inputs(); ++i)
                    q |= on(in(node, i));
                o[0] = q ? 1 : 0;
            }
            case NOT -> o[0] = on(in(node, 0)) ? 0 : 1;
            case XOR -> o[0] = on(in(node, 0)) ^ on(in(node, 1)) ? 1 : 0;
            case RISE -> {
                boolean now = on(in(node, 0));
                o[0] = now && !on(m[0]) ? 1 : 0;
                m[0] = now ? 1 : 0;
            }
            case FALL -> {
                boolean now = on(in(node, 0));
                o[0] = !now && on(m[0]) ? 1 : 0;
                m[0] = now ? 1 : 0;
            }
            case LATCH -> {
                if(on(in(node, 1)))
                    m[0] = 0;
                else if(on(in(node, 0)))
                    m[0] = 1;
                o[0] = m[0];
            }
            case TOGGLE -> {
                boolean t = on(in(node, 0));
                if(on(in(node, 1)))
                    m[0] = 0;
                else if(t && !on(m[1]))
                    m[0] = on(m[0]) ? 0 : 1;
                m[1] = t ? 1 : 0;
                o[0] = m[0];
            }
            case SELECT -> o[0] = on(in(node, 0)) ? in(node, 2) : in(node, 1);
            case TON -> {
                int preset = (int) Math.max(0, node.num(0));
                boolean enable = on(in(node, 0));
                m[0] = enable ? Math.min(preset, m[0] + 1) : 0;
                o[0] = enable && m[0] >= preset ? 1 : 0;
                o[1] = m[0];
            }
            case TOF -> {
                int preset = (int) Math.max(0, node.num(0));
                if(on(in(node, 0))) {
                    m[0] = 0;
                    o[0] = 1;
                } else {
                    m[0] = Math.min(preset, m[0] + 1);
                    o[0] = m[0] < preset ? 1 : 0;
                }
                o[1] = m[0];
            }
            case PULSE -> {
                boolean now = on(in(node, 0));
                if(now && !on(m[1]))
                    m[0] = Math.max(0, node.num(0));
                m[1] = now ? 1 : 0;
                if(m[0] > 0) {
                    o[0] = 1;
                    m[0]--;
                } else {
                    o[0] = 0;
                }
            }
            case BLINK -> {
                int onTicks = (int) Math.max(1, node.num(0)), offTicks = (int) Math.max(1, node.num(1));
                if(!on(in(node, 0))) {
                    m[0] = 0;
                    o[0] = 0;
                } else {
                    o[0] = ((int) m[0]) % (onTicks + offTicks) < onTicks ? 1 : 0;
                    m[0] = (m[0] + 1) % (onTicks + offTicks);
                }
            }
            case ADD -> {
                double sum = 0;
                for(int i = 0; i < node.inputs(); ++i)
                    sum += in(node, i);
                o[0] = sum;
            }
            case SUB -> o[0] = in(node, 0) - in(node, 1);
            case MUL -> {
                double product = 1;
                for(int i = 0; i < node.inputs(); ++i)
                    product *= in(node, i);
                o[0] = product;
            }
            case DIV -> {
                double b = in(node, 1);
                o[0] = b == 0 ? 0 : in(node, 0) / b;
            }
            case COMPARE -> {
                var ra = inRaw(node, 0);
                var rb = inRaw(node, 1);
                int op = (int) node.num(0);
                if((ra instanceof String || rb instanceof String) && (op == 4 || op == 5)) {
                    boolean same = stringOf(ra).equals(stringOf(rb));
                    o[0] = (op == 4) == same ? 1 : 0;
                    break;
                }
                double a = in(node, 0), b = in(node, 1);
                o[0] = switch(op) {
                    case 0 -> a < b;
                    case 1 -> a <= b;
                    case 2 -> a > b;
                    case 3 -> a >= b;
                    case 4 -> a == b;
                    default -> a != b;
                } ? 1 : 0;
            }
            case SCALE -> {
                double inMin = node.num(0), inMax = node.num(1), outMin = node.num(2), outMax = node.num(3);
                double span = inMax - inMin;
                o[0] = span == 0 ? outMin : outMin + (in(node, 0) - inMin) / span * (outMax - outMin);
            }
            case CLAMP -> o[0] = Math.max(node.num(0), Math.min(node.num(1), in(node, 0)));
            case THROTTLE -> {
                double step = node.num(0), min = node.num(1), max = node.num(2), start = node.num(3);
                boolean perPress = node.num(4) != 0, springReturn = node.num(5) != 0;
                if(m[3] == 0) {
                    m[0] = start;
                    m[3] = 1;
                }
                boolean up = on(in(node, 0)), down = on(in(node, 1));
                if(on(in(node, 2))) {
                    m[0] = start;
                } else {
                    if(perPress ? up && !on(m[1]) : up)
                        m[0] += step;
                    if(perPress ? down && !on(m[2]) : down)
                        m[0] -= step;
                    if(springReturn && !up && !down) {
                        if(m[0] > start)
                            m[0] = Math.max(start, m[0] - step);
                        else if(m[0] < start)
                            m[0] = Math.min(start, m[0] + step);
                    }
                }
                m[1] = up ? 1 : 0;
                m[2] = down ? 1 : 0;
                m[0] = Math.max(min, Math.min(max, m[0]));
                o[0] = m[0];
            }
            case HYSTERESIS -> {
                double v = in(node, 0);
                if(v >= node.num(0))
                    m[0] = 1;
                else if(v < node.num(1))
                    m[0] = 0;
                o[0] = m[0];
            }
            case SMOOTH -> {
                double k = Math.max(0, Math.min(1, node.num(0)));
                if(m[1] == 0) {
                    m[0] = in(node, 0);
                    m[1] = 1;
                } else {
                    m[0] += (in(node, 0) - m[0]) * k;
                }
                o[0] = m[0];
            }
            case COUNTER -> {
                boolean up = on(in(node, 0)), down = on(in(node, 1));
                if(on(in(node, 2)))
                    m[0] = 0;
                else {
                    if(up && !on(m[1]))
                        m[0]++;
                    if(down && !on(m[2]))
                        m[0]--;
                }
                m[1] = up ? 1 : 0;
                m[2] = down ? 1 : 0;
                o[0] = m[0];
                o[1] = m[0] >= node.num(0) ? 1 : 0;
            }
            case CALL -> {
                boolean enable = on(in(node, 0, 1));
                int argCount = node.inputs() - 1;
                var args = new double[argCount];
                for(int i = 0; i < argCount; ++i)
                    args[i] = in(node, i + 1);
                boolean due = switch((int) node.num(1)) {
                    case 1 -> enable && !on(m[0]);
                    case 2 -> enable && (s.lastArgs == null || !java.util.Arrays.equals(s.lastArgs, args) || !on(m[0]));
                    default -> enable;
                };
                m[0] = enable ? 1 : 0;
                if(due) {
                    s.lastArgs = args;
                    int dot = node.text.lastIndexOf('.');
                    var list = new ArrayList<Object>(argCount);
                    for(int i = 0; i < argCount; ++i) {
                        var rv = inRaw(node, i + 1);
                        double v = args[i];
                        list.add(rv instanceof String str ? str : v == Math.rint(v) && Math.abs(v) < 1e9 ? (Object) (int) v : (Object) v);
                    }
                    if((int) node.num(2) == 2)
                        for(int i = 0; i < list.size(); ++i)
                            if(list.get(i) instanceof Number n)
                                list.set(i, n.doubleValue() != 0);
                    var result = io.call(node.text.substring(0, dot), node.text.substring(dot + 1), list);
                    o[0] = toNumber(result);
                    r[0] = result instanceof String str ? str : result instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : null;
                    s.message = result == null ? "" : String.valueOf(result);
                }
            }
            case LUA -> {
                var inputs = new Object[node.inputs()];
                for(int i = 0; i < inputs.length; ++i)
                    inputs[i] = inRaw(node, i);
                var results = s.lua.run(inputs, node.outputs());
                for(int i = 0; i < Math.min(results.length, o.length); ++i) {
                    if(results[i] instanceof String str) {
                        r[i] = str;
                        o[i] = toNumber(str);
                    } else {
                        r[i] = null;
                        o[i] = ((Number) results[i]).doubleValue();
                    }
                }
            }
            case RUNGS -> {
                s.rungs.scan(io);
                if(!s.rungs.lastError().isEmpty())
                    throw new Exception(s.rungs.lastError());
            }
            default -> {}
        }
    }

    // ---- tags ----

    private static String normalise(String name) {
        return name.strip().toUpperCase(Locale.ROOT);
    }

    /** C, N and T names live in the runner, shared with rung blocks. */
    private static boolean isBit(String name) {
        if(name.length() < 2)
            return false;
        char kind = name.charAt(0);
        if(kind != 'C' && kind != 'N' && kind != 'T')
            return false;
        for(int i = 1; i < name.length(); ++i)
            if(!Character.isDigit(name.charAt(i)))
                return false;
        return true;
    }

    private static boolean isNetworkOrCoil(String name) {
        return isBit(name) && name.charAt(0) != 'T';
    }

    private double readTag(String name) throws Exception {
        if(isBit(name))
            return bits.getOrDefault(name, 0.0);
        double v = io.read(name);
        if(Double.isNaN(v))
            throw new Exception("unknown tag " + name);
        return v;
    }

    private void writeTag(String name, double value) throws Exception {
        if(isNetworkOrCoil(name)) {
            bits.put(name, value);
            return;
        }
        if(!io.write(name, value))
            throw new Exception("cannot write " + name);
    }

    private static double toNumber(@Nullable Object value) {
        if(value instanceof Boolean b)
            return b ? 1 : 0;
        if(value instanceof Number n)
            return n.doubleValue();
        if(value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch(NumberFormatException e) {
                return s.isEmpty() ? 0 : 1;
            }
        }
        return value == null ? 0 : 1;
    }
}
