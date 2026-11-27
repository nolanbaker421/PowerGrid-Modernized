package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.network.packets.ConductorLabelPayload;
import com.nolanbaker.pgmodernized.network.packets.SplicePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** A label for one pulled wire, typed from the splice editor, with the twelve wire colours under it to pick one outright; Enter applies, Escape goes back. */
public class ConductorLabelScreen extends Screen {
    private static final int PANEL_W = 200, PANEL_H = 104, SWATCH = 12, SWATCH_STEP = 15, SWATCH_Y = 60;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFF606068;

    private final BlockPos pos;
    private final int terminal;
    private final String current;
    private final Component wire;
    private int color;
    private EditBox field;
    private int panelX, panelY;

    public ConductorLabelScreen(BlockPos pos, int terminal, Component wire, String current, int color) {
        super(Component.translatable("powergrid.gui.splice.label_title"));
        this.pos = pos;
        this.terminal = terminal;
        this.wire = wire;
        this.current = current;
        this.color = color;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        field = new EditBox(font, panelX + 10, panelY + 30, PANEL_W - 20, 16, title);
        field.setMaxLength(ConductorLabelPayload.MAX_LENGTH);
        field.setValue(current);
        addRenderableWidget(field);
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.splice.label_apply"), b -> apply())
                .bounds(panelX + 10, panelY + 78, 85, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.splice.label_clear"), b -> clear())
                .bounds(panelX + PANEL_W - 95, panelY + 78, 85, 18).build());
        setInitialFocus(field);
    }

    private void apply() {
        PacketDistributor.sendToServer(new ConductorLabelPayload(pos, terminal, field.getValue().strip()));
        onClose();
    }

    private void clear() {
        PacketDistributor.sendToServer(new ConductorLabelPayload(pos, terminal, ""));
        onClose();
    }

    /** The swatch under a point, or -1. */
    private int swatchAt(double mx, double my) {
        if(my < panelY + SWATCH_Y || my >= panelY + SWATCH_Y + SWATCH)
            return -1;
        for(int i = 0; i < ConductorColors.COUNT; ++i) {
            int x = panelX + 10 + i * SWATCH_STEP;
            if(mx >= x && mx < x + SWATCH)
                return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int swatch = button == 0 ? swatchAt(mx, my) : -1;
        if(swatch >= 0) {
            color = swatch;
            PacketDistributor.sendToServer(new SplicePayload(pos, SplicePayload.RECOLOR, terminal, swatch));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new SpliceScreen(pos));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(keyCode == 257 || keyCode == 335) {   // enter, keypad enter
            apply();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, PANEL_BG);
        graphics.renderOutline(panelX, panelY, PANEL_W, PANEL_H, PANEL_EDGE);
        graphics.drawString(font, title, panelX + 10, panelY + 8, 0xFFFFFF);
        graphics.drawString(font, wire, panelX + 10, panelY + 18, 0xC0C0C0);
        field.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, Component.translatable("powergrid.gui.splice.label_colour"), panelX + 10, panelY + 50, 0xC0C0C0);
        for(int i = 0; i < ConductorColors.COUNT; ++i) {
            int x = panelX + 10 + i * SWATCH_STEP;
            graphics.fill(x, panelY + SWATCH_Y, x + SWATCH, panelY + SWATCH_Y + SWATCH, 0xFF000000 | ConductorColors.rgb(i));
            if(i == color)
                graphics.renderOutline(x - 1, panelY + SWATCH_Y - 1, SWATCH + 2, SWATCH + 2, 0xFFFFE040);
        }
        for(var child : children()) {
            if(child instanceof Button button)
                button.render(graphics, mouseX, mouseY, partialTick);
        }
        int hover = swatchAt(mouseX, mouseY);
        if(hover >= 0)
            graphics.renderTooltip(font, ConductorColors.name(hover), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing: the panel is drawn over the world.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
