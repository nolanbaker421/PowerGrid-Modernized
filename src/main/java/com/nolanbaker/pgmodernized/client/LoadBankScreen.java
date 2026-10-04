package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.network.packets.LoadBankPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Type an exact load and rated voltage into a load bank; blank or 0 hands a field back to its value box. */
public class LoadBankScreen extends Screen {
    private static final int PANEL_W = 180, PANEL_H = 96;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFF606068;

    private final BlockPos pos;
    private EditBox loadField, voltsField;
    private int panelX, panelY;

    public LoadBankScreen(BlockPos pos) {
        super(Component.translatable("powergrid.gui.load_bank.type_title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        double kw = 0, volts = 0;
        if(Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(pos) instanceof LoadBankPayload.ILoadBankSettings bank) {
            kw = bank.exactWatts() / 1000;
            volts = bank.exactVolts();
        }
        loadField = new EditBox(font, panelX + 90, panelY + 22, 80, 16, Component.translatable("powergrid.gui.load_bank.load"));
        loadField.setFilter(s -> s.matches("[0-9]*\\.?[0-9]*"));
        loadField.setValue(kw > 0 ? trim(kw) : "");
        voltsField = new EditBox(font, panelX + 90, panelY + 46, 80, 16, Component.translatable("powergrid.gui.load_bank.rated"));
        voltsField.setFilter(s -> s.matches("[0-9]*\\.?[0-9]*"));
        voltsField.setValue(volts > 0 ? trim(volts) : "");
        addRenderableWidget(loadField);
        addRenderableWidget(voltsField);
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.load_bank.apply"), b -> apply())
                .bounds(panelX + 10, panelY + 70, 75, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.load_bank.clear"), b -> clear())
                .bounds(panelX + 95, panelY + 70, 75, 18).build());
        setInitialFocus(loadField);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.format("%.0f", value) : String.format("%.3f", value).replaceAll("0+$", "");
    }

    private static double parse(String text) {
        try {
            return text.isBlank() ? 0 : Double.parseDouble(text);
        } catch(NumberFormatException e) {
            return 0;
        }
    }

    private void apply() {
        PacketDistributor.sendToServer(new LoadBankPayload(pos, parse(loadField.getValue()) * 1000, parse(voltsField.getValue())));
        onClose();
    }

    private void clear() {
        PacketDistributor.sendToServer(new LoadBankPayload(pos, 0, 0));
        onClose();
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
        graphics.drawString(font, Component.translatable("powergrid.gui.load_bank.load_kw"), panelX + 10, panelY + 26, 0xC0C0C0);
        graphics.drawString(font, Component.translatable("powergrid.gui.load_bank.rated_v"), panelX + 10, panelY + 50, 0xC0C0C0);
        loadField.render(graphics, mouseX, mouseY, partialTick);
        voltsField.render(graphics, mouseX, mouseY, partialTick);
        for(var child : children()) {
            if(child instanceof Button button)
                button.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing: the panel is drawn over the world so the bank stays in view.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
