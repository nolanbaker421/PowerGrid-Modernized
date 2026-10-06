package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.network.packets.DriveRatingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Type a drive's rated volts, the hertz they are reached at, and the ramp rate; Enter applies. */
public class DriveRatingScreen extends Screen {
    private static final int PANEL_W = 220, PANEL_H = 120;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFF606068;

    private final BlockPos pos;
    private EditBox voltsField, hzField, rampField;
    private int panelX, panelY;
    private double maxHz = 30, maxVolts = 1400;

    public DriveRatingScreen(BlockPos pos) {
        super(Component.translatable("powergrid.gui.drive.rating_title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        double volts = 0, hz = 0, ramp = 0;
        var level = Minecraft.getInstance().level;
        if(level != null && level.getBlockEntity(pos) instanceof DriveRatingPayload.IDriveRatings drive) {
            volts = drive.ratedVolts();
            hz = drive.ratedHz();
            ramp = drive.rampHzPerSecond();
            maxHz = drive.maxHz();
            maxVolts = drive.maxVolts();
        }
        voltsField = field(panelY + 22, volts);
        hzField = field(panelY + 46, hz);
        rampField = field(panelY + 70, ramp);
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.load_bank.apply"), b -> apply())
                .bounds(panelX + 10, panelY + 94, 95, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.back"), b -> onClose())
                .bounds(panelX + PANEL_W - 105, panelY + 94, 95, 18).build());
        setInitialFocus(voltsField);
    }

    private EditBox field(int y, double value) {
        var box = new EditBox(font, panelX + 120, y, 90, 16, Component.empty());
        box.setFilter(s -> s.matches("[0-9]*\\.?[0-9]*"));
        box.setValue(value > 0 ? trim(value) : "");
        addRenderableWidget(box);
        return box;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.format("%.0f", value) : String.format("%.2f", value).replaceAll("0+$", "");
    }

    private static double parse(String text) {
        try {
            return text.isBlank() ? 0 : Double.parseDouble(text);
        } catch(NumberFormatException e) {
            return 0;
        }
    }

    private void apply() {
        PacketDistributor.sendToServer(new DriveRatingPayload(pos, parse(voltsField.getValue()), parse(hzField.getValue()), parse(rampField.getValue())));
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
        graphics.drawString(font, title, panelX + 10, panelY + 8, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("powergrid.gui.drive.rated_volts", trim(maxVolts)), panelX + 10, panelY + 26, 0xC0C0C0);
        graphics.drawString(font, Component.translatable("powergrid.gui.drive.rated_hz", trim(maxHz)), panelX + 10, panelY + 50, 0xC0C0C0);
        graphics.drawString(font, Component.translatable("powergrid.gui.drive.ramp"), panelX + 10, panelY + 74, 0xC0C0C0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
