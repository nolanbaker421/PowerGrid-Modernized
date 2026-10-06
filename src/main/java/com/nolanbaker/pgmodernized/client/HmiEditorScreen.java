package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.hmi.HmiBlockEntity;
import com.nolanbaker.pgmodernized.device.hmi.HmiLayout;
import com.nolanbaker.pgmodernized.network.packets.HmiLayoutPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lays the panel out: pick a widget kind at the top and click a free cell to put one there, click
 * a widget to select it and drag it about, and set its text, tag, range, size and colour on the
 * right. Apply sends the layout to the panel; nothing changes on the block until then.
 */
public class HmiEditorScreen extends Screen {
    private static final int PADDING = 10, TITLE = 16, PROPS_W = 190, LABEL_W = 62, PROP_ROWS = 16;
    private int cell = HmiScreen.MAX_CELL;
    /** Row pitch and control height in the settings column, squeezed to fit short windows. */
    private int ROW = 22, boxH = 18;

    private final BlockPos pos;
    private HmiLayout layout = new HmiLayout();
    private boolean loaded;
    private int selected = -1, builtFor = -2;
    @Nullable
    private HmiLayout.Kind placing;
    private int dragging = -1, dragDc, dragDr;
    private boolean modified;
    private int panelX, panelY, panelW, panelH, gridX, gridY, propsX;
    private final List<Line> lines = new ArrayList<>();
    private final List<Button> kindButtons = new ArrayList<>();
    /** Where the width, height and colour rows sit, for the values drawn between their buttons. */
    private int widthY, heightY;
    private final int[] colourY = new int[3];
    private int colourRows;

    private record Line(Component text, int x, int y, int color) {}

    public HmiEditorScreen(BlockPos pos) {
        super(Component.translatable("powergrid.gui.hmi.editor"));
        this.pos = pos;
    }

    @Nullable
    private HmiBlockEntity hmi() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof HmiBlockEntity hmi ? hmi.head() : null;
    }

    @Nullable
    private HmiLayout.Widget selectedWidget() {
        return selected >= 0 && selected < layout.widgets.size() ? layout.widgets.get(selected) : null;
    }

    @Override
    protected void init() {
        super.init();
        if(!loaded) {
            var hmi = hmi();
            layout = hmi == null ? new HmiLayout() : hmi.layout().copy();
            loaded = true;
        }
        lines.clear();
        kindButtons.clear();
        ROW = Math.max(13, Math.min(22, (height - 8 - PADDING * 2 - TITLE - 22 - 40) / PROP_ROWS));
        boxH = Math.min(18, ROW - 1);
        cell = HmiScreen.cellSize(layout.cols(), layout.rows(), width, height, PADDING * 3 + PROPS_W + 8, PADDING * 2 + TITLE + 22 + 48);
        int gridW = Math.max(layout.cols() * cell, 6 * 64);
        panelW = PADDING * 3 + gridW + PROPS_W;
        panelH = PADDING * 2 + TITLE + 22 + Math.max(layout.rows() * cell, PROP_ROWS * ROW) + 40;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        gridX = panelX + PADDING;
        gridY = panelY + PADDING + TITLE + 22;
        propsX = gridX + gridW + PADDING;

        // The kinds to place, across the top.
        int x = panelX + PADDING;
        for(var kind : HmiLayout.Kind.values()) {
            var button = addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.hmi.kind." + kind.key()), b -> {
                placing = placing == kind ? null : kind;
                selected = -1;
                rebuildWidgets();
            }).bounds(x, panelY + PADDING + TITLE, 62, 18).build());
            kindButtons.add(button);
            x += 64;
        }

        // The selected widget's settings.
        var w = selectedWidget();
        builtFor = selected;
        int y = gridY;
        if(w != null) {
            lines.add(new Line(Component.translatable("powergrid.gui.hmi.kind." + w.kind.key()), propsX, y + 5, HmiScreen.SEL));
            y += ROW;
            y = textRow(y, "powergrid.gui.hmi.text", w.text, s -> w.text = s.length() > HmiLayout.MAX_TEXT ? s.substring(0, HmiLayout.MAX_TEXT) : s);
            if(w.kind.usesTag())
                y = textRow(y, "powergrid.gui.hmi.tag", w.tag, s -> w.tag = s.strip().toUpperCase(Locale.ROOT));
            if(w.kind.hasRange()) {
                y = numberRow(y, "powergrid.gui.hmi.min", w.min, v -> w.min = v);
                y = numberRow(y, "powergrid.gui.hmi.max", w.max, v -> w.max = v);
            }
            if(w.kind == HmiLayout.Kind.SETPOINT)
                y = numberRow(y, "powergrid.gui.hmi.step", w.step, v -> w.step = v > 0 ? v : 1);
            if(w.kind.hasDecimals())
                y = numberRow(y, "powergrid.gui.hmi.decimals", w.decimals, v -> w.decimals = (int) Math.max(0, Math.min(3, v)));
            if(w.kind == HmiLayout.Kind.GAUGE) {
                y = numberRow(y, "powergrid.gui.hmi.band1", w.band1, v -> w.band1 = v);
                y = numberRow(y, "powergrid.gui.hmi.band2", w.band2, v -> w.band2 = v);
            }
            widthY = y;
            y = stepRow(y, "powergrid.gui.hmi.width", () -> resize(w, w.w - 1, w.h), () -> resize(w, w.w + 1, w.h));
            heightY = y;
            y = stepRow(y, "powergrid.gui.hmi.height", () -> resize(w, w.w, w.h - 1), () -> resize(w, w.w, w.h + 1));
            colourRows = w.kind == HmiLayout.Kind.GAUGE ? 3 : 1;
            colourY[0] = y;
            y = stepRow(y, colourRows == 1 ? "powergrid.gui.hmi.colour" : "powergrid.gui.hmi.colour1",
                    () -> { w.color = Math.floorMod(w.color - 1, HmiLayout.COLORS.length); modified = true; },
                    () -> { w.color = Math.floorMod(w.color + 1, HmiLayout.COLORS.length); modified = true; });
            if(colourRows == 3) {
                colourY[1] = y;
                y = stepRow(y, "powergrid.gui.hmi.colour2", () -> { w.color2 = Math.floorMod(w.color2 - 1, HmiLayout.COLORS.length); modified = true; },
                        () -> { w.color2 = Math.floorMod(w.color2 + 1, HmiLayout.COLORS.length); modified = true; });
                colourY[2] = y;
                y = stepRow(y, "powergrid.gui.hmi.colour3", () -> { w.color3 = Math.floorMod(w.color3 - 1, HmiLayout.COLORS.length); modified = true; },
                        () -> { w.color3 = Math.floorMod(w.color3 + 1, HmiLayout.COLORS.length); modified = true; });
            }
            if(w.kind == HmiLayout.Kind.BUTTON) {
                lines.add(new Line(Component.translatable("powergrid.gui.hmi.mode"), propsX, y + 5, HmiScreen.TEXT));
                int yy = y;
                addRenderableWidget(Button.builder(Component.translatable(w.toggle ? "powergrid.gui.hmi.toggle" : "powergrid.gui.hmi.momentary"), b -> {
                    w.toggle = !w.toggle;
                    modified = true;
                    rebuildWidgets();
                }).bounds(propsX + LABEL_W, yy, PROPS_W - LABEL_W, boxH).build());
                y += ROW;
            }
            int yy = y + 4;
            addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.hmi.delete"), b -> {
                layout.widgets.remove(selected);
                selected = -1;
                modified = true;
                rebuildWidgets();
            }).bounds(propsX, yy, 70, boxH).build());
        } else {
            lines.add(new Line(Component.translatable(placing == null ? "powergrid.gui.hmi.hint_select" : "powergrid.gui.hmi.hint_place"), propsX, y + 5, HmiScreen.DIM));
        }

        int bottom = panelY + panelH - PADDING - 18;
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.apply"), b -> apply()).bounds(gridX, bottom, 60, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.back"), b -> onClose()).bounds(gridX + 64, bottom, 60, 18).build());
    }

    private interface TextSink { void accept(String s); }

    private interface NumberSink { void accept(double v); }

    private int textRow(int y, String key, String value, TextSink sink) {
        lines.add(new Line(Component.translatable(key), propsX, y + 5, HmiScreen.TEXT));
        var box = new EditBox(font, propsX + LABEL_W, y, PROPS_W - LABEL_W, boxH, Component.translatable(key));
        box.setMaxLength(HmiLayout.MAX_TEXT);
        box.setValue(value);
        box.setResponder(s -> {
            sink.accept(s);
            modified = true;
        });
        addRenderableWidget(box);
        return y + ROW;
    }

    private int numberRow(int y, String key, double value, NumberSink sink) {
        lines.add(new Line(Component.translatable(key), propsX, y + 5, HmiScreen.TEXT));
        var box = new EditBox(font, propsX + LABEL_W, y, 80, boxH, Component.translatable(key));
        box.setValue(value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.3f", value));
        box.setResponder(s -> {
            try {
                sink.accept(Double.parseDouble(s.strip().replace(',', '.')));
                modified = true;
            } catch(NumberFormatException ignored) {}
        });
        addRenderableWidget(box);
        return y + ROW;
    }

    private int stepRow(int y, String key, Runnable minus, Runnable plus) {
        lines.add(new Line(Component.translatable(key), propsX, y + 5, HmiScreen.TEXT));
        addRenderableWidget(Button.builder(Component.literal("-"), b -> minus.run()).bounds(propsX + LABEL_W, y, 18, boxH).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> plus.run()).bounds(propsX + LABEL_W + 50, y, 18, boxH).build());
        return y + ROW;
    }

    private void resize(HmiLayout.Widget w, int newW, int newH) {
        if(newW < 1 || newH < 1)
            return;
        var probe = w.copy();
        probe.w = newW;
        probe.h = newH;
        if(layout.free(probe, w)) {
            w.w = newW;
            w.h = newH;
            modified = true;
        }
    }

    private void apply() {
        layout.clampAll();
        PacketDistributor.sendToServer(new HmiLayoutPayload(pos, layout.save()));
        modified = false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new HmiScreen(pos));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var hmi = hmi();
        if(hmi == null) {
            onClose();
            return;
        }
        if(builtFor != selected)
            rebuildWidgets();
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, HmiScreen.PANEL_BG);
        graphics.renderOutline(panelX, panelY, panelW, panelH, HmiScreen.PANEL_EDGE);
        graphics.drawString(font, title, panelX + PADDING, panelY + PADDING, 0xFFFFFF);
        // Live values line up with the panel's layout only while the two agree in length.
        var values = hmi.values().length == layout.widgets.size() ? hmi.values() : new float[0];
        HmiScreen.drawLayout(graphics, font, layout, values, gridX, gridY, cell, selected, true);
        for(var line : lines)
            graphics.drawString(font, line.text, line.x, line.y, line.color);
        // The values the selected widget's -/+ rows show.
        var w = selectedWidget();
        if(w != null) {
            graphics.drawString(font, Integer.toString(w.w), propsX + LABEL_W + 26, widthY + 5, HmiScreen.SEL);
            graphics.drawString(font, Integer.toString(w.h), propsX + LABEL_W + 26, heightY + 5, HmiScreen.SEL);
            for(int i = 0; i < colourRows; ++i)
                graphics.fill(propsX + LABEL_W + 22, colourY[i] + 2, propsX + LABEL_W + 46, colourY[i] + 16, 0xFF000000 | w.bandColor(i));
        }
        for(int i = 0; i < kindButtons.size(); ++i)
            if(placing == HmiLayout.Kind.values()[i])
                graphics.renderOutline(kindButtons.get(i).getX() - 1, kindButtons.get(i).getY() - 1, kindButtons.get(i).getWidth() + 2, kindButtons.get(i).getHeight() + 2, HmiScreen.SEL);
        int statusY = panelY + panelH - PADDING - 13;
        graphics.drawString(font, HmiScreen.status(hmi), gridX + 130, statusY + 2, hmi.connected() ? HmiScreen.DIM : 0xFFE06060);
        if(modified)
            graphics.drawString(font, Component.translatable("powergrid.gui.controls.plc_modified"), propsX, statusY + 2, HmiScreen.SEL);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("powergrid.gui.hmi.tags_hint").getString(), panelW - PADDING * 2), panelX + PADDING, panelY + panelH - PADDING - 2 - 9 - 14, HmiScreen.DIM);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int @Nullable [] cellAt(double mx, double my) {
        if(mx < gridX || my < gridY || mx >= gridX + layout.cols() * cell || my >= gridY + layout.rows() * cell)
            return null;
        return new int[] {(int) ((mx - gridX) / cell), (int) ((my - gridY) / cell)};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        var cell = cellAt(mx, my);
        if(cell == null)
            return super.mouseClicked(mx, my, button);
        setFocused(null);
        int index = layout.indexAt(cell[0], cell[1]);
        if(button == 1) {
            if(index >= 0) {
                layout.widgets.remove(index);
                selected = -1;
                modified = true;
            }
            return true;
        }
        if(index < 0 && placing != null) {
            var added = layout.add(placing, cell[0], cell[1]);
            if(added != null) {
                selected = layout.widgets.indexOf(added);
                placing = null;
                modified = true;
            }
            return true;
        }
        selected = index;
        if(index >= 0) {
            var w = layout.widgets.get(index);
            dragging = index;
            dragDc = cell[0] - w.col;
            dragDr = cell[1] - w.row;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if(dragging >= 0 && dragging < layout.widgets.size()) {
            var cell = cellAt(mx, my);
            if(cell != null) {
                var w = layout.widgets.get(dragging);
                var probe = w.copy();
                probe.col = cell[0] - dragDc;
                probe.row = cell[1] - dragDr;
                if((probe.col != w.col || probe.row != w.row) && layout.free(probe, w)) {
                    w.col = probe.col;
                    w.row = probe.row;
                    modified = true;
                }
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = -1;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(keyCode == GLFW.GLFW_KEY_DELETE && selected >= 0 && !(getFocused() instanceof EditBox)) {
            layout.widgets.remove(selected);
            selected = -1;
            modified = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
