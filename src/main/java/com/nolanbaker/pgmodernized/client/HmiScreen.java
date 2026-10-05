package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.hmi.HmiBlockEntity;
import com.nolanbaker.pgmodernized.device.hmi.HmiLayout;
import com.nolanbaker.pgmodernized.network.packets.HmiPressPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The panel large: the same widgets as on the block face, with live values, buttons that press
 * and setpoints that step. Edit opens the layout editor.
 */
public class HmiScreen extends Screen {
    static final int CELL = 24, PADDING = 10, TITLE = 16;
    static final int PANEL_BG = 0xF0202024, PANEL_EDGE = 0xFF606068, TEXT = 0xFFE8E8E8, DIM = 0xFF9A9A9A, SCREEN_BG = 0xFF0C1014, SEL = 0xFFE0C060;

    private final BlockPos pos;
    private int panelX, panelY, panelW, panelH, gridX, gridY;

    public HmiScreen(BlockPos pos) {
        super(Component.translatable("powergrid.gui.hmi.title"));
        this.pos = pos;
    }

    @Nullable
    private HmiBlockEntity hmi() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof HmiBlockEntity hmi ? hmi : null;
    }

    @Override
    protected void init() {
        super.init();
        panelW = PADDING * 2 + HmiLayout.COLS * CELL;
        panelH = PADDING * 2 + TITLE + HmiLayout.ROWS * CELL + 28;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        gridX = panelX + PADDING;
        gridY = panelY + PADDING + TITLE;
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.hmi.edit"), b -> Minecraft.getInstance().setScreen(new HmiEditorScreen(pos)))
                .bounds(panelX + panelW - PADDING - 60, panelY + panelH - PADDING - 18, 60, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var hmi = hmi();
        if(hmi == null) {
            onClose();
            return;
        }
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        graphics.renderOutline(panelX, panelY, panelW, panelH, PANEL_EDGE);
        graphics.drawString(font, title, panelX + PADDING, panelY + PADDING, 0xFFFFFF);
        drawLayout(graphics, font, hmi.layout(), hmi.values(), gridX, gridY, CELL, -1, false);
        graphics.drawString(font, status(hmi), panelX + PADDING, panelY + panelH - PADDING - 13, hmi.connected() ? DIM : 0xFFE06060);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    static Component status(HmiBlockEntity hmi) {
        var at = hmi.cabinetPos();
        return at == null ? Component.translatable("powergrid.gui.hmi.no_cabinet")
                : Component.translatable("powergrid.gui.hmi.cabinet_at", at.getX(), at.getY(), at.getZ());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        var hmi = hmi();
        if(hmi != null && button == 0 && mx >= gridX && my >= gridY && mx < gridX + HmiLayout.COLS * CELL && my < gridY + HmiLayout.ROWS * CELL) {
            int col = (int) ((mx - gridX) / CELL), row = (int) ((my - gridY) / CELL);
            int index = hmi.layout().indexAt(col, row);
            if(index >= 0) {
                var w = hmi.layout().widgets.get(index);
                if(w.kind == HmiLayout.Kind.BUTTON) {
                    PacketDistributor.sendToServer(new HmiPressPayload(pos, index, HmiBlockEntity.PRESS));
                    return true;
                }
                if(w.kind == HmiLayout.Kind.SETPOINT) {
                    double across = (mx - (gridX + w.col * CELL)) / (w.w * CELL);
                    PacketDistributor.sendToServer(new HmiPressPayload(pos, index, across < 0.5 ? HmiBlockEntity.MINUS : HmiBlockEntity.PLUS));
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** The widgets on a grid of that cell size, as the block face shows them; {@code selected} gets an outline. */
    static void drawLayout(GuiGraphics g, Font font, HmiLayout layout, float[] values, int x0, int y0, int cell, int selected, boolean gridLines) {
        int cols = HmiLayout.COLS, rows = HmiLayout.ROWS;
        g.fill(x0, y0, x0 + cols * cell, y0 + rows * cell, SCREEN_BG);
        if(gridLines) {
            for(int c = 1; c < cols; ++c)
                g.fill(x0 + c * cell, y0, x0 + c * cell + 1, y0 + rows * cell, 0xFF1C2228);
            for(int r = 1; r < rows; ++r)
                g.fill(x0, y0 + r * cell, x0 + cols * cell, y0 + r * cell + 1, 0xFF1C2228);
        }
        for(int i = 0; i < layout.widgets.size(); ++i) {
            var w = layout.widgets.get(i);
            float v = i < values.length ? values[i] : Float.NaN;
            boolean on = !Float.isNaN(v) && v != 0;
            int rgb = 0xFF000000 | HmiLayout.COLORS[Math.floorMod(w.color, HmiLayout.COLORS.length)];
            int x = x0 + w.col * cell, y = y0 + w.row * cell, wd = w.w * cell, ht = w.h * cell;
            int ty = y + ht / 2 - 4;
            switch(w.kind) {
                case LABEL -> g.drawString(font, clip(font, w.text, wd - 6), x + 3, ty, rgb, false);
                case VALUE -> {
                    var value = HmiLayout.format(v, w.decimals);
                    if(!w.text.isEmpty())
                        g.drawString(font, clip(font, w.text, wd / 2), x + 3, ty, 0xFFA0A0A0, false);
                    g.drawString(font, value, x + wd - 3 - font.width(value), ty, rgb, false);
                }
                case INDICATOR -> {
                    int lamp = Math.min(cell - 6, 14);
                    g.fill(x + 4, y + ht / 2 - lamp / 2, x + 4 + lamp, y + ht / 2 + lamp / 2, on ? rgb : dim(rgb, 0.3));
                    g.renderOutline(x + 4, y + ht / 2 - lamp / 2, lamp, lamp, 0xFF505860);
                    g.drawString(font, clip(font, w.text, wd - lamp - 12), x + 8 + lamp, ty, 0xFFE0E0E0, false);
                }
                case BUTTON -> {
                    g.fill(x + 2, y + 2, x + wd - 2, y + ht - 2, on ? rgb : dim(rgb, 0.55));
                    g.renderOutline(x + 2, y + 2, wd - 4, ht - 4, on ? 0xFFFFFFFF : 0xFF707880);
                    var s = clip(font, w.text, wd - 8);
                    g.drawString(font, s, x + wd / 2 - font.width(s) / 2, ty, on ? 0xFF101010 : 0xFFF0F0F0, false);
                }
                case BAR -> {
                    g.fill(x + 2, y + 2, x + wd - 2, y + ht - 2, 0xFF202428);
                    double span = w.max - w.min;
                    double fraction = Float.isNaN(v) || span <= 0 ? 0 : Math.max(0, Math.min(1, (v - w.min) / span));
                    if(fraction > 0)
                        g.fill(x + 3, y + 3, x + 3 + (int) ((wd - 6) * fraction), y + ht - 3, rgb);
                    g.renderOutline(x + 2, y + 2, wd - 4, ht - 4, 0xFF505860);
                    g.drawString(font, clip(font, w.text, wd - 8), x + 5, ty, 0xFFF0F0F0, false);
                }
                case SETPOINT -> {
                    int key = Math.min(cell - 2, wd / 4);
                    g.fill(x + 2, y + 2, x + 2 + key, y + ht - 2, 0xFF383C42);
                    g.fill(x + wd - 2 - key, y + 2, x + wd - 2, y + ht - 2, 0xFF383C42);
                    g.drawString(font, "-", x + 2 + key / 2 - 2, ty, 0xFFF0F0F0, false);
                    g.drawString(font, "+", x + wd - 2 - key / 2 - 2, ty, 0xFFF0F0F0, false);
                    var value = HmiLayout.format(v, w.decimals);
                    var s = clip(font, w.text.isEmpty() ? value : w.text + " " + value, wd - 2 * key - 8);
                    g.drawString(font, s, x + wd / 2 - font.width(s) / 2, ty, rgb, false);
                }
            }
            if(i == selected)
                g.renderOutline(x, y, wd, ht, SEL);
        }
    }

    private static String clip(Font font, String s, int width) {
        return font.plainSubstrByWidth(s, Math.max(1, width));
    }

    private static int dim(int argb, double k) {
        int r = (int) ((argb >> 16 & 0xFF) * k), g = (int) ((argb >> 8 & 0xFF) * k), b = (int) ((argb & 0xFF) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
