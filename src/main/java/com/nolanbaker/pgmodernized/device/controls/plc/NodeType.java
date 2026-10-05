package com.nolanbaker.pgmodernized.device.controls.plc;

import java.util.List;

/**
 * The blocks a PLC program is drawn from: what pins each has, what it can be set to, and how
 * it reads on the canvas. Pins carry numbers; a boolean is 0 or anything else.
 */
public enum NodeType {
    TAG("Tag", Group.IO, List.of(Param.text(0, "Tag", "X1.1"))),
    SET("Set tag", Group.IO, List.of(Param.text(0, "Tag", "Y1.1"))),
    CONST("Constant", Group.IO, List.of(Param.number(0, "Value", 0))),
    AND("And", Group.LOGIC, List.of(Param.count(0, "Inputs", 2, 2, 8))),
    OR("Or", Group.LOGIC, List.of(Param.count(0, "Inputs", 2, 2, 8))),
    NOT("Not", Group.LOGIC, List.of()),
    XOR("Xor", Group.LOGIC, List.of()),
    RISE("Rising edge", Group.LOGIC, List.of()),
    FALL("Falling edge", Group.LOGIC, List.of()),
    LATCH("SR latch", Group.LOGIC, List.of()),
    TOGGLE("Toggle", Group.LOGIC, List.of()),
    SELECT("Select", Group.LOGIC, List.of()),
    TON("On delay", Group.TIMER, List.of(Param.number(0, "Ticks", 20))),
    TOF("Off delay", Group.TIMER, List.of(Param.number(0, "Ticks", 20))),
    PULSE("One-shot", Group.TIMER, List.of(Param.number(0, "Ticks", 10))),
    BLINK("Blink", Group.TIMER, List.of(Param.number(0, "On ticks", 10), Param.number(1, "Off ticks", 10))),
    ADD("Add", Group.MATH, List.of(Param.count(0, "Inputs", 2, 2, 8))),
    SUB("Subtract", Group.MATH, List.of()),
    MUL("Multiply", Group.MATH, List.of(Param.count(0, "Inputs", 2, 2, 8))),
    DIV("Divide", Group.MATH, List.of()),
    COMPARE("Compare", Group.MATH, List.of(Param.choice(0, "Test", Choices.COMPARE_OPS))),
    SCALE("Scale", Group.MATH, List.of(Param.number(0, "In min", 0), Param.number(1, "In max", 100), Param.number(2, "Out min", 0), Param.number(3, "Out max", 1))),
    CLAMP("Clamp", Group.MATH, List.of(Param.number(0, "Min", 0), Param.number(1, "Max", 100))),
    COUNTER("Counter", Group.MATH, List.of(Param.number(0, "Preset", 10))),
    CALL("Device call", Group.DEVICE, List.of(Param.device(0, "Method"), Param.count(0, "Arguments", 0, 0, 6), Param.choice(1, "Call", Choices.CALL_MODES))),
    LUA("Lua", Group.SCRIPT, List.of(Param.script(0, "Script", NodeType.LUA_DEFAULT), Param.count(0, "Inputs", 2, 0, 8), Param.count(1, "Outputs", 1, 0, 8))),
    RUNGS("Rungs", Group.SCRIPT, List.of(Param.script(0, "Rungs", "# one rung per line, e.g.  Y1.1 = X1.1 & !X1.2"))),
    NOTE("Note", Group.SCRIPT, List.of(Param.text(0, "Text", "note")));

    /** Held apart from the enum so its constants may name them. */
    private static final class Choices {
        static final String[] COMPARE_OPS = {"<", "<=", ">", ">=", "==", "!="};
        static final String[] CALL_MODES = {"every scan", "when En rises", "when an argument changes"};
    }

    public static final String[] COMPARE_OPS = Choices.COMPARE_OPS;
    public static final String[] CALL_MODES = Choices.CALL_MODES;
    public static final String LUA_DEFAULT = """
            -- In[1..n] are the input pins as numbers (0 is off), Out[1..m] the outputs.
            -- tag("X1.1") reads a tag, tag("Y1.1", 1) writes one,
            -- call("drive1", "setFrequency", 12) calls a device, print(...) shows under the block.
            Out[1] = In[1] ~= 0 and In[2] ~= 0
            """;

    public enum Group { IO, LOGIC, TIMER, MATH, DEVICE, SCRIPT }

    /** One thing the properties dialog edits: text, a script, a device method, a number, a pin count or a choice. */
    public record Param(Kind kind, int index, String label, double def, double min, double max, String[] choices, String defText) {
        public enum Kind { TEXT, SCRIPT, DEVICE, NUMBER, COUNT, CHOICE }

        static Param text(int index, String label, String def) {
            return new Param(Kind.TEXT, index, label, 0, 0, 0, null, def);
        }

        static Param script(int index, String label, String def) {
            return new Param(Kind.SCRIPT, index, label, 0, 0, 0, null, def);
        }

        static Param device(int index, String label) {
            return new Param(Kind.DEVICE, index, label, 0, 0, 0, null, "");
        }

        static Param number(int index, String label, double def) {
            return new Param(Kind.NUMBER, index, label, def, -1e12, 1e12, null, "");
        }

        static Param count(int index, String label, int def, int min, int max) {
            return new Param(Kind.COUNT, index, label, def, min, max, null, "");
        }

        static Param choice(int index, String label, String[] choices) {
            return new Param(Kind.CHOICE, index, label, 0, 0, choices.length - 1, choices, "");
        }

        public boolean isText() {
            return kind == Kind.TEXT || kind == Kind.SCRIPT || kind == Kind.DEVICE;
        }
    }

    public final String title;
    public final Group group;
    public final List<Param> params;

    NodeType(String title, Group group, List<Param> params) {
        this.title = title;
        this.group = group;
        this.params = params;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static NodeType byKey(String key) {
        for(var type : values())
            if(type.key().equals(key))
                return type;
        return null;
    }

    /** Fresh numeric settings, one slot per numeric parameter index. */
    public double[] defaults() {
        int size = 0;
        for(var p : params)
            if(!p.isText())
                size = Math.max(size, p.index + 1);
        var out = new double[size];
        for(var p : params)
            if(!p.isText())
                out[p.index] = p.def;
        return out;
    }

    public String defaultText() {
        for(var p : params)
            if(p.isText())
                return p.defText;
        return "";
    }

    private static String[] numbered(String prefix, int n) {
        var out = new String[Math.max(0, n)];
        for(int i = 0; i < out.length; ++i)
            out[i] = prefix + (i + 1);
        return out;
    }

    private static int count(PlcGraph.Node node, int index, int fallback) {
        return node.nums.length > index ? (int) Math.max(0, node.nums[index]) : fallback;
    }

    public String[] inputNames(PlcGraph.Node node) {
        return switch(this) {
            case AND, OR, ADD, MUL -> numbered("", count(node, 0, 2));
            case NOT, RISE, FALL, TON, TOF, PULSE, SCALE, CLAMP -> new String[] {"In"};
            case XOR, SUB, DIV, COMPARE -> new String[] {"A", "B"};
            case LATCH -> new String[] {"S", "R"};
            case TOGGLE -> new String[] {"T", "Reset"};
            case SELECT -> new String[] {"Sel", "A", "B"};
            case BLINK -> new String[] {"En"};
            case COUNTER -> new String[] {"Up", "Down", "Reset"};
            case SET -> new String[] {"Value"};
            case CALL -> {
                var names = new String[1 + count(node, 0, 0)];
                names[0] = "En";
                for(int i = 1; i < names.length; ++i)
                    names[i] = "Arg" + i;
                yield names;
            }
            case LUA -> numbered("In", count(node, 0, 2));
            default -> new String[0];
        };
    }

    public String[] outputNames(PlcGraph.Node node) {
        return switch(this) {
            case TAG, CONST -> new String[] {"Value"};
            case AND, OR, NOT, XOR, RISE, FALL, LATCH, TOGGLE, SELECT, PULSE, BLINK, COMPARE -> new String[] {"Q"};
            case TON, TOF -> new String[] {"Q", "Elapsed"};
            case ADD, SUB, MUL, DIV, SCALE, CLAMP -> new String[] {"Out"};
            case COUNTER -> new String[] {"Count", "Done"};
            case CALL -> new String[] {"Result"};
            case LUA -> numbered("Out", count(node, 1, 1));
            default -> new String[0];
        };
    }

    /** The line under a block's title that says how it is set. */
    public String summary(PlcGraph.Node node) {
        return switch(this) {
            case TAG, SET, NOTE -> node.text;
            case CONST -> format(node.nums[0]);
            case COMPARE -> "A " + COMPARE_OPS[(int) Math.max(0, Math.min(COMPARE_OPS.length - 1, node.nums[0]))] + " B";
            case TON, TOF, PULSE -> format(node.nums[0]) + " ticks";
            case BLINK -> format(node.nums[0]) + " / " + format(node.nums[1]);
            case SCALE -> format(node.nums[0]) + ".." + format(node.nums[1]) + " to " + format(node.nums[2]) + ".." + format(node.nums[3]);
            case CLAMP -> format(node.nums[0]) + ".." + format(node.nums[1]);
            case COUNTER -> "to " + format(node.nums[0]);
            case CALL -> node.text;
            case LUA -> {
                for(var line : node.text.split("\n")) {
                    var s = line.strip();
                    if(!s.isEmpty() && !s.startsWith("--"))
                        yield s;
                }
                yield "";
            }
            case RUNGS -> {
                int n = 0;
                for(var line : node.text.split("\n"))
                    if(!line.strip().isEmpty() && !line.strip().startsWith("#"))
                        ++n;
                yield n + " rungs";
            }
            default -> "";
        };
    }

    public static String format(double v) {
        if(v == Math.rint(v) && Math.abs(v) < 1e12)
            return Long.toString((long) v);
        return String.format(java.util.Locale.ROOT, "%.3g", v);
    }
}
