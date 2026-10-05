package com.nolanbaker.pgmodernized.device.controls.plc;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small ladder logic, written as text, one rung per line. Every scan each line runs in order:
 * <pre>
 *   Y1.1 = X1.1 & !X1.2 | C1          coil: output 1.1 follows the expression
 *   C1 S= X1.3                         set the coil while the expression is true
 *   C1 R= X1.4 | E                     reset it while this one is
 *   T1 = TON(X1.5, 40)                 on-delay timer: T1 is true once X1.5 has held 40 ticks
 *   N1 = C1                            a network bit the external port can read
 *   V1.start = ^X1.6                   a VFD module's start, on the rising edge of an input
 *   when C1: drive1.setFrequency(12)   call a device method while the condition holds
 *   when ^X1.7: drive1.setEnabled(true)
 *   Y1.2 = rangefinder1.getDistance() < 5
 * </pre>
 * Names: {@code X<slot>.<ch>} inputs, {@code Y<slot>.<ch>} outputs, {@code R<slot>.<ch>} relays,
 * {@code V<slot>.run/start/stop/reverse/speed}, {@code D<cell>} door device states, {@code E}
 * E-stop, {@code P} power, {@code C<n>} coils, {@code T<n>} timers, {@code N<n>} network bits.
 * Booleans are numbers, 0 false and anything else true; {@code !} not, {@code &} and, {@code |}
 * or, {@code ^} rising edge, comparisons and arithmetic as usual, {@code true}, {@code false},
 * {@code #} starts a comment. A device is called as {@code alias.method(args)}; its first result
 * is the value.
 */
public final class PlcProgram {
    /** What a program reads and writes; the cabinet implements it. */
    public interface Io {
        /** A name's value, booleans as 0 or 1; NaN for a name that does not exist. */
        double read(String name);

        /** Whether the name may be written. */
        boolean write(String name, double value);

        /** Whether the name exists for reading. */
        boolean exists(String name);

        /** A device method call, or null when there is no such device. */
        @Nullable
        Object call(String alias, String method, List<Object> args) throws Exception;

        /** Whether that device alias is known. */
        boolean hasDevice(String alias);
    }

    public static final class CompileError extends Exception {
        public final int line;

        CompileError(int line, String message) {
            super(message);
            this.line = line;
        }
    }

    // ---- syntax tree ----

    private sealed interface Expr permits Num, Ref, Call, Unary, Binary {}

    private record Num(double value) implements Expr {}

    private record Ref(String name) implements Expr {}

    private record Call(String alias, String method, List<Expr> args) implements Expr {}

    private static final class Unary implements Expr {
        final char op;
        final Expr a;

        Unary(char op, Expr a) {
            this.op = op;
            this.a = a;
        }
    }

    private record Binary(String op, Expr a, Expr b) implements Expr {}

    private sealed interface Stmt permits Assign, Timer, When, CallStmt {}

    /** mode 0 is '=', 1 is 'S=', 2 is 'R='. */
    private record Assign(int line, String target, Expr value, int mode) implements Stmt {}

    private record Timer(int line, String target, Expr enable, Expr preset) implements Stmt {}

    private record When(int line, Expr condition, Call call) implements Stmt {}

    private record CallStmt(int line, Call call) implements Stmt {}

    // ---- state ----

    private final List<Stmt> statements;
    private final Map<String, Double> bits = new HashMap<>();      // coils C and network bits N
    private final Map<String, int[]> timers = new HashMap<>();     // T: {elapsed}
    private final Map<Unary, Boolean> edges = new IdentityHashMap<>();
    private final List<String> devicesUsed = new ArrayList<>();
    private String lastError = "";

    private PlcProgram(List<Stmt> statements) {
        this.statements = statements;
    }

    public String lastError() {
        return lastError;
    }

    public int lines() {
        return statements.size();
    }

    /** Device aliases the program names, for the cabinet to show. */
    public List<String> devicesUsed() {
        return devicesUsed;
    }

    public double bit(String name) {
        return bits.getOrDefault(name.toUpperCase(Locale.ROOT), 0.0);
    }

    public void setBit(String name, double value) {
        bits.put(name.toUpperCase(Locale.ROOT), value);
    }

    public Map<String, Double> bits() {
        return bits;
    }

    // ---- scanning ----

    /** One scan: every line in order. A runtime failure stops the scan and is reported. */
    public void scan(Io io) {
        try {
            for(var statement : statements)
                run(statement, io);
            lastError = "";
        } catch(Exception e) {
            lastError = e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    private void run(Stmt statement, Io io) throws Exception {
        switch(statement) {
            case Assign a -> {
                double v = eval(a.value, io);
                if(a.mode == 0)
                    store(a.target, v, io, a.line);
                else if(a.mode == 1 && v != 0)
                    store(a.target, 1, io, a.line);
                else if(a.mode == 2 && v != 0)
                    store(a.target, 0, io, a.line);
            }
            case Timer t -> {
                boolean enable = eval(t.enable, io) != 0;
                int preset = (int) Math.max(0, eval(t.preset, io));
                var state = timers.computeIfAbsent(t.target, k -> new int[1]);
                state[0] = enable ? Math.min(preset + 1, state[0] + 1) : 0;
                bits.put(t.target, enable && state[0] >= preset ? 1.0 : 0.0);
            }
            case When w -> {
                if(eval(w.condition, io) != 0)
                    call(w.call, io, w.line);
            }
            case CallStmt c -> call(c.call, io, c.line);
        }
    }

    private void store(String target, double value, Io io, int line) throws Exception {
        char kind = target.charAt(0);
        if(kind == 'C' || kind == 'N' || kind == 'T') {
            bits.put(target, value);
            return;
        }
        if(!io.write(target, value))
            throw new Exception("line " + line + ": cannot write " + target);
    }

    private Object call(Call call, Io io, int line) throws Exception {
        var args = new ArrayList<Object>(call.args.size());
        for(var arg : call.args) {
            double v = eval(arg, io);
            args.add(v == Math.rint(v) && Math.abs(v) < 1e9 ? (Object) (int) v : (Object) v);
        }
        return io.call(call.alias, call.method, args);
    }

    private double eval(Expr expr, Io io) throws Exception {
        switch(expr) {
            case Num n -> {
                return n.value;
            }
            case Ref r -> {
                char kind = r.name.charAt(0);
                if((kind == 'C' || kind == 'N' || kind == 'T') && isNumbered(r.name))
                    return bits.getOrDefault(r.name, 0.0);
                double v = io.read(r.name);
                if(Double.isNaN(v))
                    throw new Exception("unknown name " + r.name);
                return v;
            }
            case Call c -> {
                var result = call(c, io, 0);
                return toNumber(result);
            }
            case Unary u -> {
                double v = eval(u.a, io);
                return switch(u.op) {
                    case '!' -> v == 0 ? 1 : 0;
                    case '-' -> -v;
                    case '^' -> {
                        boolean now = v != 0;
                        boolean was = edges.getOrDefault(u, false);
                        edges.put(u, now);
                        yield now && !was ? 1 : 0;
                    }
                    default -> v;
                };
            }
            case Binary b -> {
                double x = eval(b.a, io);
                double y = eval(b.b, io);
                return switch(b.op) {
                    case "&" -> x != 0 && y != 0 ? 1 : 0;
                    case "|" -> x != 0 || y != 0 ? 1 : 0;
                    case "<" -> x < y ? 1 : 0;
                    case "<=" -> x <= y ? 1 : 0;
                    case ">" -> x > y ? 1 : 0;
                    case ">=" -> x >= y ? 1 : 0;
                    case "==" -> x == y ? 1 : 0;
                    case "!=" -> x != y ? 1 : 0;
                    case "+" -> x + y;
                    case "-" -> x - y;
                    case "*" -> x * y;
                    case "/" -> y == 0 ? 0 : x / y;
                    default -> 0;
                };
            }
        }
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

    private static boolean isNumbered(String name) {
        if(name.length() < 2)
            return false;
        for(int i = 1; i < name.length(); ++i)
            if(!Character.isDigit(name.charAt(i)))
                return false;
        return true;
    }

    // ---- compiling ----

    /** Parses the program and checks every name against the cabinet; the first problem is the error. */
    public static PlcProgram compile(String text, Io io) throws CompileError {
        var statements = new ArrayList<Stmt>();
        var devices = new ArrayList<String>();
        var lines = text.split("\\r?\\n");
        for(int i = 0; i < lines.length; ++i) {
            String line = lines[i];
            int hash = line.indexOf('#');
            if(hash >= 0)
                line = line.substring(0, hash);
            line = line.strip();
            if(line.isEmpty())
                continue;
            var parser = new Parser(line, i + 1);
            var statement = parser.statement();
            statements.add(statement);
            check(statement, io, i + 1, devices);
        }
        var program = new PlcProgram(statements);
        program.devicesUsed.addAll(devices);
        return program;
    }

    private static void check(Stmt statement, Io io, int line, List<String> devices) throws CompileError {
        switch(statement) {
            case Assign a -> {
                checkTarget(a.target, io, line);
                checkExpr(a.value, io, line, devices);
            }
            case Timer t -> {
                if(!(t.target.charAt(0) == 'T' && isNumbered(t.target)))
                    throw new CompileError(line, "a timer must be named T1, T2, ...");
                checkExpr(t.enable, io, line, devices);
                checkExpr(t.preset, io, line, devices);
            }
            case When w -> {
                checkExpr(w.condition, io, line, devices);
                checkExpr(w.call, io, line, devices);
            }
            case CallStmt c -> checkExpr(c.call, io, line, devices);
        }
    }

    private static void checkTarget(String target, Io io, int line) throws CompileError {
        char kind = target.charAt(0);
        if((kind == 'C' || kind == 'N') && isNumbered(target))
            return;
        if(kind == 'T' && isNumbered(target))
            throw new CompileError(line, target + " is a timer: write it as " + target + " = TON(enable, ticks)");
        if(!io.write(target, Double.NaN))
            throw new CompileError(line, "cannot write " + target);
    }

    private static void checkExpr(Expr expr, Io io, int line, List<String> devices) throws CompileError {
        switch(expr) {
            case Num n -> {}
            case Ref r -> {
                char kind = r.name.charAt(0);
                if((kind == 'C' || kind == 'N' || kind == 'T') && isNumbered(r.name))
                    return;
                if(!io.exists(r.name))
                    throw new CompileError(line, "unknown name " + r.name);
            }
            case Call c -> {
                if(!io.hasDevice(c.alias))
                    throw new CompileError(line, "no device called " + c.alias + " on the internal network");
                if(!devices.contains(c.alias))
                    devices.add(c.alias);
                for(var arg : c.args)
                    checkExpr(arg, io, line, devices);
            }
            case Unary u -> checkExpr(u.a, io, line, devices);
            case Binary b -> {
                checkExpr(b.a, io, line, devices);
                checkExpr(b.b, io, line, devices);
            }
        }
    }

    /** One line's recursive-descent parser. */
    private static final class Parser {
        private final String text;
        private final int line;
        private int at;

        Parser(String text, int line) {
            this.text = text;
            this.line = line;
        }

        private CompileError error(String message) {
            return new CompileError(line, message);
        }

        private void skip() {
            while(at < text.length() && Character.isWhitespace(text.charAt(at)))
                ++at;
        }

        private boolean take(String s) {
            skip();
            if(text.startsWith(s, at)) {
                at += s.length();
                return true;
            }
            return false;
        }

        private boolean peek(String s) {
            skip();
            return text.startsWith(s, at);
        }

        private boolean atEnd() {
            skip();
            return at >= text.length();
        }

        private String word() throws CompileError {
            skip();
            int start = at;
            while(at < text.length() && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '_' || text.charAt(at) == '.'))
                ++at;
            if(start == at)
                throw error("expected a name at column " + (at + 1));
            return text.substring(start, at);
        }

        Stmt statement() throws CompileError {
            if(take("when")) {
                var condition = expression();
                if(!take(":"))
                    throw error("expected ':' after the condition");
                var call = expression();
                if(!(call instanceof Call c))
                    throw error("expected a device call after ':'");
                if(!atEnd())
                    throw error("unexpected text after the call");
                return new When(line, condition, c);
            }
            int save = at;
            var name = word();
            if(peek("(")) {
                at = save;
                var call = expression();
                if(!(call instanceof Call c) || !atEnd())
                    throw error("expected a device call");
                return new CallStmt(line, c);
            }
            int mode;
            if(take("S="))
                mode = 1;
            else if(take("R="))
                mode = 2;
            else if(take("=") && !peek("="))
                mode = 0;
            else
                throw error("expected '=', 'S=' or 'R=' after " + name);
            var target = normalise(name);
            if(take("TON")) {
                if(!take("("))
                    throw error("expected '(' after TON");
                var enable = expression();
                if(!take(","))
                    throw error("expected ',' between the timer's enable and its ticks");
                var preset = expression();
                if(!take(")"))
                    throw error("expected ')' to close TON");
                if(!atEnd())
                    throw error("unexpected text after the timer");
                return new Timer(line, target, enable, preset);
            }
            var value = expression();
            if(!atEnd())
                throw error("unexpected text at column " + (at + 1));
            return new Assign(line, target, value, mode);
        }

        private Expr expression() throws CompileError {
            var a = and();
            while(take("|"))
                a = new Binary("|", a, and());
            return a;
        }

        private Expr and() throws CompileError {
            var a = comparison();
            while(take("&"))
                a = new Binary("&", a, comparison());
            return a;
        }

        private Expr comparison() throws CompileError {
            var a = additive();
            for(String op : new String[] {"<=", ">=", "==", "!=", "<", ">"}) {
                if(take(op))
                    return new Binary(op, a, additive());
            }
            return a;
        }

        private Expr additive() throws CompileError {
            var a = term();
            while(true) {
                if(take("+"))
                    a = new Binary("+", a, term());
                else if(take("-"))
                    a = new Binary("-", a, term());
                else
                    return a;
            }
        }

        private Expr term() throws CompileError {
            var a = unary();
            while(true) {
                if(take("*"))
                    a = new Binary("*", a, unary());
                else if(take("/"))
                    a = new Binary("/", a, unary());
                else
                    return a;
            }
        }

        private Expr unary() throws CompileError {
            if(take("!"))
                return new Unary('!', unary());
            if(take("^"))
                return new Unary('^', unary());
            if(take("-"))
                return new Unary('-', unary());
            return primary();
        }

        private Expr primary() throws CompileError {
            skip();
            if(take("(")) {
                var inner = expression();
                if(!take(")"))
                    throw error("expected ')'");
                return inner;
            }
            if(at < text.length() && (Character.isDigit(text.charAt(at)) || text.charAt(at) == '.')) {
                int start = at;
                while(at < text.length() && (Character.isDigit(text.charAt(at)) || text.charAt(at) == '.'))
                    ++at;
                try {
                    return new Num(Double.parseDouble(text.substring(start, at)));
                } catch(NumberFormatException e) {
                    throw error("bad number " + text.substring(start, at));
                }
            }
            var name = word();
            if(name.equalsIgnoreCase("true"))
                return new Num(1);
            if(name.equalsIgnoreCase("false"))
                return new Num(0);
            if(peek("(")) {
                int dot = name.lastIndexOf('.');
                if(dot <= 0 || dot == name.length() - 1)
                    throw error("a call is alias.method(...): " + name);
                take("(");
                var args = new ArrayList<Expr>();
                if(!take(")")) {
                    do {
                        args.add(expression());
                    } while(take(","));
                    if(!take(")"))
                        throw error("expected ')' to close the call");
                }
                return new Call(name.substring(0, dot), name.substring(dot + 1), args);
            }
            return new Ref(normalise(name));
        }

        /** Cabinet names are case-insensitive and upper-cased; device aliases keep their case. */
        private static String normalise(String name) {
            return name.toUpperCase(Locale.ROOT);
        }
    }
}
