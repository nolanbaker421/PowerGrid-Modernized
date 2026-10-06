package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.network.packets.CellLabelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** A label for one door or station cell, drawn under its device; Enter applies, Escape goes back. */
public class CellLabelScreen extends Screen {
    private static final int PANEL_W = 200, PANEL_H = 64;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFF606068;

    private final BlockPos pos;
    private final int cell;
    private final String current;
    private final boolean station;
    private EditBox field;
    private int panelX, panelY;

    public CellLabelScreen(BlockPos pos, int cell, String current, boolean station) {
        super(Component.translatable("powergrid.gui.controls.label_title"));
        this.pos = pos;
        this.cell = cell;
        this.current = current;
        this.station = station;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        field = new EditBox(font, panelX + 10, panelY + 20, PANEL_W - 20, 16, title);
        field.setMaxLength(CellLabelPayload.MAX_LENGTH);
        field.setValue(current);
        addRenderableWidget(field);
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.splice.label_apply"), b -> apply())
                .bounds(panelX + 10, panelY + 40, 85, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.splice.label_clear"), b -> {
            PacketDistributor.sendToServer(new CellLabelPayload(pos, cell, ""));
            onClose();
        }).bounds(panelX + PANEL_W - 95, panelY + 40, 85, 18).build());
        setInitialFocus(field);
    }

    private void apply() {
        PacketDistributor.sendToServer(new CellLabelPayload(pos, cell, field.getValue().strip()));
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(station ? new StationScreen(pos) : new ControlsCabinetScreen(pos));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(keyCode == 257 || keyCode == 335) {
            apply();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, PANEL_BG);
        graphics.renderOutline(panelX, panelY, PANEL_W, PANEL_H, PANEL_EDGE);
        graphics.drawString(font, title, panelX + 10, panelY + 7, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
