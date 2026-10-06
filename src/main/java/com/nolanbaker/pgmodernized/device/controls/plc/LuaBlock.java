package com.nolanbaker.pgmodernized.device.controls.plc;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One Lua block: a script compiled once and run every scan in a sandboxed LuaJ with a budget of
 * instructions per scan. The script sees {@code In[i]} and fills {@code Out[i]}, and reaches the
 * cabinet through {@code tag}, {@code call}, {@code devices} and {@code print}. Its globals
 * persist between scans, so it can keep state in them.
 */
public final class LuaBlock {
    public static final int BUDGET = 200_000;
    private static final int MAX_STRING = 100_000;

    private final Globals globals = new Globals();
    private final LuaValue chunk;
    private int used;
    private long tick;

    /** Keeps the per-scan instruction budget; nothing else of the debug library is exposed. */
    private final class Budget extends DebugLib {
        @Override
        public void onInstruction(int pc, Varargs v, int top) {
            super.onInstruction(pc, v, top);
            if(++used > BUDGET)
                throw new LuaError("over " + BUDGET + " instructions in one scan");
        }
    }

    public LuaBlock(String script, PlcProgram.Io io, Supplier<List<List<String>>> devices, Consumer<String> print) throws LuaError {
        globals.load(new JseBaseLib());
        globals.load(new PackageLib());   // the other libraries register through package.loaded; require goes away below
        globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new JseMathLib());
        LoadState.install(globals);
        LuaC.install(globals);
        globals.load(new Budget());
        for(String name : new String[] {"debug", "dofile", "loadfile", "load", "loadstring", "collectgarbage", "require", "package"})
            globals.set(name, LuaValue.NIL);
        globals.finder = name -> null;

        var string = globals.get("string");
        var rep = string.get("rep");
        string.set("rep", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                long length = (long) args.checkjstring(1).length() * Math.max(0, args.optint(2, 0));
                if(length > MAX_STRING)
                    throw new LuaError("string too long");
                return rep.invoke(args);
            }
        });
        globals.set("print", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                var sb = new StringBuilder();
                for(int i = 1; i <= args.narg(); ++i) {
                    if(i > 1)
                        sb.append(' ');
                    sb.append(args.arg(i).tojstring());
                }
                print.accept(sb.length() > 200 ? sb.substring(0, 200) : sb.toString());
                return LuaValue.NONE;
            }
        });
        globals.set("tag", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.checkjstring(1);
                if(args.narg() >= 2) {
                    double v = args.arg(2).isboolean() ? (args.arg(2).toboolean() ? 1 : 0) : args.checkdouble(2);
                    return LuaValue.valueOf(io.write(name.toUpperCase(java.util.Locale.ROOT), v));
                }
                double v = io.read(name.toUpperCase(java.util.Locale.ROOT));
                return Double.isNaN(v) ? LuaValue.NIL : LuaValue.valueOf(v);
            }
        });
        globals.set("call", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String alias = args.checkjstring(1);
                String method = args.checkjstring(2);
                var list = new ArrayList<Object>();
                for(int i = 3; i <= args.narg(); ++i)
                    list.add(toJava(args.arg(i)));
                try {
                    if(!io.hasDevice(alias))
                        throw new LuaError("no device called " + alias);
                    return toLua(io.call(alias, method, list));
                } catch(LuaError e) {
                    throw e;
                } catch(Exception e) {
                    throw new LuaError(alias + "." + method + ": " + (e.getMessage() == null ? e.toString() : e.getMessage()));
                }
            }
        });
        globals.set("devices", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                var table = new LuaTable();
                for(var device : devices.get()) {
                    var entry = new LuaTable();
                    entry.set("type", device.get(1));
                    var methods = new LuaTable();
                    for(int i = 2; i < device.size(); ++i)
                        methods.set(i - 1, device.get(i));
                    entry.set("methods", methods);
                    table.set(device.get(0), entry);
                }
                return table;
            }
        });
        globals.set("In", new LuaTable());
        globals.set("Out", new LuaTable());
        globals.set("tick", LuaValue.valueOf(0));
        chunk = globals.load(script, "lua");
    }

    /** Runs the script once with those pin values (numbers, or text) and returns what it put in Out, each a Double or a String. */
    public Object[] run(Object[] inputs, int outputs) throws LuaError {
        used = 0;
        var in = new LuaTable();
        for(int i = 0; i < inputs.length; ++i)
            in.set(i + 1, inputs[i] instanceof String s ? LuaValue.valueOf(s) : LuaValue.valueOf(((Number) inputs[i]).doubleValue()));
        var out = new LuaTable();
        globals.set("In", in);
        globals.set("Out", out);
        globals.set("tick", LuaValue.valueOf(tick++));
        chunk.call();
        var result = new Object[outputs];
        for(int i = 0; i < outputs; ++i) {
            var v = out.get(i + 1);
            result[i] = v.type() == LuaValue.TSTRING ? v.tojstring() : (Object) toNumber(v);
        }
        return result;
    }

    private static double toNumber(LuaValue v) {
        if(v.isboolean())
            return v.toboolean() ? 1 : 0;
        if(v.isnumber())
            return v.todouble();
        if(v.isstring()) {
            try {
                return Double.parseDouble(v.tojstring());
            } catch(NumberFormatException e) {
                return v.tojstring().isEmpty() ? 0 : 1;
            }
        }
        return v.isnil() ? 0 : 1;
    }

    private static Object toJava(LuaValue v) {
        if(v.isboolean())
            return v.toboolean();
        if(v.isinttype() || (v.isnumber() && v.todouble() == Math.rint(v.todouble()) && Math.abs(v.todouble()) < 1e9))
            return v.toint();
        if(v.isnumber())
            return v.todouble();
        if(v.isnil())
            return null;
        return v.tojstring();
    }

    @SuppressWarnings("unchecked")
    private static LuaValue toLua(Object o) {
        if(o == null)
            return LuaValue.NIL;
        if(o instanceof Boolean b)
            return LuaValue.valueOf(b);
        if(o instanceof Number n)
            return LuaValue.valueOf(n.doubleValue());
        if(o instanceof String s)
            return LuaValue.valueOf(s);
        if(o instanceof byte[] bytes)
            return LuaValue.valueOf(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        if(o instanceof Map<?, ?> map) {
            var table = new LuaTable();
            for(var entry : map.entrySet())
                table.set(toLua(entry.getKey()), toLua(entry.getValue()));
            return table;
        }
        if(o instanceof Object[] array) {
            var table = new LuaTable();
            for(int i = 0; i < array.length; ++i)
                table.set(i + 1, toLua(array[i]));
            return table;
        }
        if(o instanceof List<?> list) {
            var table = new LuaTable();
            for(int i = 0; i < list.size(); ++i)
                table.set(i + 1, toLua(list.get(i)));
            return table;
        }
        return LuaValue.valueOf(o.toString());
    }
}
