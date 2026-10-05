package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.network.packets.ConductorLabelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** A label for one pulled wire, typed from the splice editor; Enter applies, Escape goes back. */
public class ConductorLabelScreen extends Screen {
    private static final int PANEL_W = 200, PANEL_H = 74;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFF606068;

    private final BlockPos pos;
    private final int terminal;
    private final String current;
    private final Component wire;
    private EditBox field;
    private int panelX, panelY;

    public ConductorLabelScreen(BlockPos pos, int terminal, Component wire, String current) {
        super(Component.translatable("powergrid.gui.splice.label_title"));
        this.pos = pos;
        this.terminal = terminal;
        this.wire = wire;
        this.current = current;
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
                .bounds(panelX + 10, panelY + 50, 85, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.splice.label_clear"), b -> clear())
                .bounds(panelX + PANEL_W - 95, panelY + 50, 85, 18).build());
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
        for(var child : children()) {
            if(child instanceof Button button)
                button.render(graphics, mouseX, mouseY, partialTick);
        }
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
