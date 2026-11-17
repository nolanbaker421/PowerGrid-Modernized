package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.label.LabelMakerItem;
import com.nolanbaker.pgmodernized.network.packets.LabelMakerPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/** The label maker's keypad: type what it prints; Enter applies, Clear makes it print blanks, Escape leaves it as it was. */
public class LabelMakerScreen extends Screen {
    private static final int PANEL_W = 200, PANEL_H = 64;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFFE8C21E;

    private final InteractionHand hand;
    private final String current;
    private EditBox field;
    private int panelX, panelY;

    public LabelMakerScreen(InteractionHand hand, String current) {
        super(Component.translatable("powergrid.gui.label_maker.title"));
        this.hand = hand;
        this.current = current;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        field = new EditBox(font, panelX + 10, panelY + 20, PANEL_W - 20, 16, title);
        field.setMaxLength(LabelMakerItem.MAX_LENGTH);
        field.setValue(current);
        addRenderableWidget(field);
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.splice.label_apply"), b -> apply())
                .bounds(panelX + 10, panelY + 40, 85, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.splice.label_clear"), b -> {
            PacketDistributor.sendToServer(new LabelMakerPayload(hand == InteractionHand.OFF_HAND, ""));
            onClose();
        }).bounds(panelX + PANEL_W - 95, panelY + 40, 85, 18).build());
        setInitialFocus(field);
    }

    private void apply() {
        PacketDistributor.sendToServer(new LabelMakerPayload(hand == InteractionHand.OFF_HAND, field.getValue().strip()));
        onClose();
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
