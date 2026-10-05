package com.nolanbaker.pgmodernized.device.hmi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What an HMI panel shows: widgets on an 8 by 8 grid, each tied to a PLC tag. The block entity
 * holds the layout and the editor works on a copy it sends back whole.
 */
public final class HmiLayout {
    public static final int COLS = 8, ROWS = 8, MAX_WIDGETS = 32, MAX_TEXT = 40;
    /** Colours a widget may pick, by index. */
    public static final int[] COLORS = {0xF0F0F0, 0x40D050, 0xE04040, 0xE8C030, 0x40A0F0, 0xF08030, 0xC060E0, 0x40E0D0};

    public enum Kind {
        /** Text only. */
        LABEL,
        /** A tag's number, with a label and decimals. */
        VALUE,
        /** A lamp that lights while the tag is nonzero. */
        INDICATOR,
        /** Writes its tag: 1 while pressed and 0 again, or toggles between 0 and 1. */
        BUTTON,
        /** A bar from min to max. */
        BAR,
        /** A number with minus and plus that writes the tag by a step, kept between min and max. */
        SETPOINT;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static @Nullable Kind byKey(String key) {
            for(var kind : values())
                if(kind.key().equals(key))
                    return kind;
            return null;
        }

        public boolean usesTag() {
            return this != LABEL;
        }

        public boolean writes() {
            return this == BUTTON || this == SETPOINT;
        }

        public boolean hasRange() {
            return this == BAR || this == SETPOINT;
        }

        int defaultWidth() {
            return switch(this) {
                case INDICATOR -> 3;
                case BUTTON -> 3;
                default -> 4;
            };
        }
    }

    public static final class Widget {
        public Kind kind;
        public int col, row, w, h = 1;
        public String text = "";
        public String tag = "";
        public double min = 0, max = 100, step = 1;
        public int decimals;
        public int color;
        public boolean toggle;

        public Widget(Kind kind, int col, int row) {
            this.kind = kind;
            this.col = col;
            this.row = row;
            this.w = kind.defaultWidth();
            this.text = switch(kind) {
                case LABEL -> "Label";
                case BUTTON -> "Start";
                default -> "";
            };
            this.color = switch(kind) {
                case INDICATOR -> 1;
                case BUTTON -> 1;
                case BAR -> 4;
                default -> 0;
            };
        }

        public boolean contains(int c, int r) {
            return c >= col && c < col + w && r >= row && r < row + h;
        }

        public boolean overlaps(Widget other) {
            return col < other.col + other.w && other.col < col + w && row < other.row + other.h && other.row < row + h;
        }

        public Widget copy() {
            var o = new Widget(kind, col, row);
            o.w = w;
            o.h = h;
            o.text = text;
            o.tag = tag;
            o.min = min;
            o.max = max;
            o.step = step;
            o.decimals = decimals;
            o.color = color;
            o.toggle = toggle;
            return o;
        }

        CompoundTag save() {
            var t = new CompoundTag();
            t.putString("Kind", kind.key());
            t.putInt("Col", col);
            t.putInt("Row", row);
            t.putInt("W", w);
            t.putInt("H", h);
            t.putString("Text", text);
            t.putString("Tag", tag);
            t.putDouble("Min", min);
            t.putDouble("Max", max);
            t.putDouble("Step", step);
            t.putInt("Decimals", decimals);
            t.putInt("Color", color);
            t.putBoolean("Toggle", toggle);
            return t;
        }

        static @Nullable Widget load(CompoundTag t) {
            var kind = Kind.byKey(t.getString("Kind"));
            if(kind == null)
                return null;
            var o = new Widget(kind, t.getInt("Col"), t.getInt("Row"));
            o.w = t.getInt("W");
            o.h = t.getInt("H");
            o.text = t.getString("Text");
            o.tag = t.getString("Tag");
            o.min = t.getDouble("Min");
            o.max = t.getDouble("Max");
            o.step = t.getDouble("Step");
            o.decimals = t.getInt("Decimals");
            o.color = t.getInt("Color");
            o.toggle = t.getBoolean("Toggle");
            return o;
        }

        /** Keeps the widget inside the grid and its settings sane. */
        void clamp() {
            w = Math.max(1, Math.min(COLS, w));
            h = Math.max(1, Math.min(ROWS, h));
            col = Math.max(0, Math.min(COLS - w, col));
            row = Math.max(0, Math.min(ROWS - h, row));
            if(text.length() > MAX_TEXT)
                text = text.substring(0, MAX_TEXT);
            if(tag.length() > MAX_TEXT)
                tag = tag.substring(0, MAX_TEXT);
            tag = tag.strip().toUpperCase(Locale.ROOT);
            decimals = Math.max(0, Math.min(3, decimals));
            color = Math.floorMod(color, COLORS.length);
            if(!(step > 0) || Double.isInfinite(step))
                step = 1;
            if(Double.isNaN(min) || Double.isInfinite(min))
                min = 0;
            if(Double.isNaN(max) || Double.isInfinite(max))
                max = 100;
            if(max < min) {
                double t = min;
                min = max;
                max = t;
            }
        }
    }

    public final List<Widget> widgets = new ArrayList<>();

    @Nullable
    public Widget at(int col, int row) {
        for(var w : widgets)
            if(w.contains(col, row))
                return w;
        return null;
    }

    public int indexAt(int col, int row) {
        for(int i = 0; i < widgets.size(); ++i)
            if(widgets.get(i).contains(col, row))
                return i;
        return -1;
    }

    /** Whether a widget could sit there without covering another (ignoring {@code except}). */
    public boolean free(Widget probe, @Nullable Widget except) {
        if(probe.col < 0 || probe.row < 0 || probe.col + probe.w > COLS || probe.row + probe.h > ROWS)
            return false;
        for(var w : widgets)
            if(w != except && w.overlaps(probe))
                return false;
        return true;
    }

    @Nullable
    public Widget add(Kind kind, int col, int row) {
        if(widgets.size() >= MAX_WIDGETS)
            return null;
        var w = new Widget(kind, col, row);
        // Shrink to fit the right edge, then refuse if it still covers something.
        w.w = Math.min(w.w, COLS - col);
        while(w.w > 1 && !free(w, null))
            --w.w;
        if(!free(w, null))
            return null;
        widgets.add(w);
        return w;
    }

    public void clampAll() {
        for(var w : widgets)
            w.clamp();
    }

    public HmiLayout copy() {
        var l = new HmiLayout();
        for(var w : widgets)
            l.widgets.add(w.copy());
        return l;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        var list = new ListTag();
        for(var w : widgets)
            list.add(w.save());
        tag.put("Widgets", list);
        return tag;
    }

    public static HmiLayout load(CompoundTag tag) {
        var l = new HmiLayout();
        var list = tag.getList("Widgets", Tag.TAG_COMPOUND);
        for(int i = 0; i < list.size() && l.widgets.size() < MAX_WIDGETS; ++i) {
            var w = Widget.load(list.getCompound(i));
            if(w != null)
                l.widgets.add(w);
        }
        l.clampAll();
        return l;
    }

    public static String format(double value, int decimals) {
        if(Double.isNaN(value))
            return "?";
        return String.format(Locale.ROOT, "%." + Math.max(0, Math.min(3, decimals)) + "f", value);
    }
}
