package com.nolanbaker.pgmodernized.device.hmi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What an HMI screen shows: widgets on a grid of cells, eight by eight per panel, each tied to a
 * PLC tag. The origin panel holds the layout and the editor works on a copy it sends back whole.
 */
public final class HmiLayout {
    public static final int PANEL = 8, MAX_WIDGETS = 64, MAX_TEXT = 40;
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
        SETPOINT,
        /** A needle over an arc from min to max, the arc in three coloured bands with two thresholds. */
        GAUGE,
        /** A line along the widget, horizontal or vertical by its shape; with a tag it lights while nonzero. */
        LINE,
        /** An outlined rectangle with a title, for grouping. */
        BOX;

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
            return this != LABEL && this != BOX;
        }

        public boolean writes() {
            return this == BUTTON || this == SETPOINT;
        }

        public boolean hasRange() {
            return this == BAR || this == SETPOINT || this == GAUGE;
        }

        public boolean hasDecimals() {
            return this == VALUE || this == SETPOINT || this == GAUGE;
        }

        int defaultWidth() {
            return switch(this) {
                case INDICATOR, BUTTON -> 3;
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
        /** Gauge bands: below band1 the first colour, below band2 the second, above it the third. */
        public double band1 = 60, band2 = 80;
        public int color2 = 3, color3 = 2;

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
                case INDICATOR, BUTTON, GAUGE -> 1;
                case BAR -> 4;
                default -> 0;
            };
            if(kind == Kind.GAUGE)
                h = 2;
            if(kind == Kind.BOX) {
                h = 3;
                text = "Group";
            }
        }

        /** The gauge band a value falls in: 0, 1 or 2. */
        public int band(double value) {
            return Double.isNaN(value) || value < band1 ? 0 : value < band2 ? 1 : 2;
        }

        public int bandColor(int band) {
            int index = band == 0 ? color : band == 1 ? color2 : color3;
            return COLORS[Math.floorMod(index, COLORS.length)];
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
            o.band1 = band1;
            o.band2 = band2;
            o.color2 = color2;
            o.color3 = color3;
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
            t.putDouble("Band1", band1);
            t.putDouble("Band2", band2);
            t.putInt("Color2", color2);
            t.putInt("Color3", color3);
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
            if(t.contains("Band1")) {
                o.band1 = t.getDouble("Band1");
                o.band2 = t.getDouble("Band2");
                o.color2 = t.getInt("Color2");
                o.color3 = t.getInt("Color3");
            }
            return o;
        }

        /** Keeps the widget inside a grid of that size and its settings sane. */
        void clamp(int cols, int rows) {
            w = Math.max(1, Math.min(cols, w));
            h = Math.max(1, Math.min(rows, h));
            col = Math.max(0, Math.min(cols - w, col));
            row = Math.max(0, Math.min(rows - h, row));
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
            color2 = Math.floorMod(color2, COLORS.length);
            color3 = Math.floorMod(color3, COLORS.length);
            if(Double.isNaN(band1) || Double.isInfinite(band1))
                band1 = min;
            if(Double.isNaN(band2) || Double.isInfinite(band2))
                band2 = max;
            if(band2 < band1) {
                double t = band1;
                band1 = band2;
                band2 = t;
            }
        }
    }

    public final List<Widget> widgets = new ArrayList<>();
    private int cols = PANEL, rows = PANEL;

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    /** The grid grows or shrinks with the screen; widgets that no longer fit are dropped. Returns whether anything changed. */
    public boolean resize(int cols, int rows) {
        cols = Math.max(PANEL, cols);
        rows = Math.max(PANEL, rows);
        if(cols == this.cols && rows == this.rows)
            return false;
        this.cols = cols;
        this.rows = rows;
        final int c = cols, r = rows;
        widgets.removeIf(w -> w.col + w.w > c || w.row + w.h > r);
        return true;
    }

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
        if(probe.col < 0 || probe.row < 0 || probe.col + probe.w > cols || probe.row + probe.h > rows)
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
        w.w = Math.min(w.w, cols - col);
        while(w.w > 1 && !free(w, null))
            --w.w;
        if(!free(w, null))
            return null;
        widgets.add(w);
        return w;
    }

    public void clampAll() {
        for(var w : widgets)
            w.clamp(cols, rows);
    }

    public HmiLayout copy() {
        var l = new HmiLayout();
        l.cols = cols;
        l.rows = rows;
        for(var w : widgets)
            l.widgets.add(w.copy());
        return l;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Cols", cols);
        tag.putInt("Rows", rows);
        var list = new ListTag();
        for(var w : widgets)
            list.add(w.save());
        tag.put("Widgets", list);
        return tag;
    }

    public static HmiLayout load(CompoundTag tag) {
        var l = new HmiLayout();
        if(tag.contains("Cols"))
            l.cols = Math.max(PANEL, Math.min(PANEL * HmiGroups.MAX_WIDTH, tag.getInt("Cols")));
        if(tag.contains("Rows"))
            l.rows = Math.max(PANEL, Math.min(PANEL * HmiGroups.MAX_HEIGHT, tag.getInt("Rows")));
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
