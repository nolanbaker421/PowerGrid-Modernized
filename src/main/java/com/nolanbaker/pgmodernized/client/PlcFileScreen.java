package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.controls.plc.PlcGraph;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Save, load, delete, copy and paste PLC programs for the editor underneath. Programs are files on
 * this computer (see {@link PlcPrograms}); Copy and Paste move the whole program through the
 * clipboard as text, for sharing. Loading replaces the drawing in the editor; Apply there still
 * sends it to the cabinet.
 */
public class PlcFileScreen extends Screen {
    private static final int PANEL_W = 260, ROW = 12, ROWS = 8;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFF606068, ROW_SEL = 0xFF3A3A44, ROW_HOVER = 0xFF2C2C34;

    private final PlcGraphScreen parent;
    private EditBox nameField;
    private List<String> names = List.of();
    private int scroll, selected = -1;
    private int panelX, panelY, panelH, listY;
    private String status = "";
    private boolean error;

    public PlcFileScreen(PlcGraphScreen parent) {
        super(Component.translatable("powergrid.gui.plc_files.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        panelH = 44 + ROWS * ROW + 56;
        panelX = (width - PANEL_W) / 2;
        panelY = Math.max(4, (height - panelH) / 2);
        listY = panelY + 40;
        nameField = new EditBox(font, panelX + 10, panelY + 20, PANEL_W - 20, 16, Component.translatable("powergrid.gui.plc_files.name"));
        nameField.setMaxLength(PlcPrograms.MAX_NAME);
        if(selected >= 0 && selected < names.size())
            nameField.setValue(names.get(selected));
        addRenderableWidget(nameField);
        int y = listY + ROWS * ROW + 6;
        int w = (PANEL_W - 20 - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.plc_files.save"), b -> save()).bounds(panelX + 10, y, w, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.plc_files.load"), b -> load()).bounds(panelX + 14 + w, y, w, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.plc_files.delete"), b -> delete()).bounds(panelX + 18 + 2 * w, y, w, 18).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.plc_files.copy"), b -> copy()).bounds(panelX + 10, y, w, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.plc_files.paste"), b -> paste()).bounds(panelX + 14 + w, y, w, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.plc_files.back"), b -> onClose()).bounds(panelX + 18 + 2 * w, y, w, 18).build());
        refresh();
        setInitialFocus(nameField);
    }

    private void refresh() {
        names = PlcPrograms.list();
        if(selected >= names.size())
            selected = -1;
        scroll = Math.max(0, Math.min(scroll, names.size() - ROWS));
    }

    private void say(String key, boolean bad, Object... args) {
        status = Component.translatable(key, args).getString();
        error = bad;
    }

    private String name() {
        return PlcPrograms.clean(nameField.getValue());
    }

    private void save() {
        var name = name();
        if(name.isEmpty()) {
            say("powergrid.gui.plc_files.bad_name", true);
            return;
        }
        if(PlcPrograms.save(name, parent.graph().save())) {
            say("powergrid.gui.plc_files.saved", false, name);
            refresh();
            selected = names.indexOf(name);
        } else {
            say("powergrid.gui.plc_files.failed", true);
        }
    }

    private void load() {
        var name = name();
        var tag = name.isEmpty() ? null : PlcPrograms.load(name);
        if(tag == null) {
            say("powergrid.gui.plc_files.missing", true, name);
            return;
        }
        parent.replaceGraph(PlcGraph.load(tag));
        say("powergrid.gui.plc_files.loaded", false, name);
        onClose();
    }

    private void delete() {
        var name = name();
        if(!name.isEmpty() && PlcPrograms.delete(name)) {
            say("powergrid.gui.plc_files.deleted", false, name);
            selected = -1;
            refresh();
        } else {
            say("powergrid.gui.plc_files.missing", true, name);
        }
    }

    private void copy() {
        Minecraft.getInstance().keyboardHandler.setClipboard(PlcPrograms.toText(parent.graph().save()));
        say("powergrid.gui.plc_files.copied", false);
    }

    private void paste() {
        var tag = PlcPrograms.fromText(Minecraft.getInstance().keyboardHandler.getClipboard());
        if(tag == null || !tag.contains("Nodes")) {
            say("powergrid.gui.plc_files.bad_text", true);
            return;
        }
        parent.replaceGraph(PlcGraph.load(tag));
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private int rowAt(double mx, double my) {
        if(mx < panelX + 10 || mx >= panelX + PANEL_W - 10 || my < listY || my >= listY + ROWS * ROW)
            return -1;
        int row = (int) ((my - listY) / ROW) + scroll;
        return row < names.size() ? row : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int row = rowAt(mx, my);
        if(row >= 0) {
            selected = row;
            nameField.setValue(names.get(row));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if(rowAt(mx, my) >= 0 || (mx >= panelX && mx < panelX + PANEL_W && my >= listY && my < listY + ROWS * ROW)) {
            scroll = Math.max(0, Math.min(scroll - (int) Math.signum(scrollY), names.size() - ROWS));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(keyCode == 257 || keyCode == 335) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        parent.render(graphics, -1, -1, partialTick);
        graphics.fill(0, 0, width, height, 0x90000000);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, PANEL_BG);
        graphics.renderOutline(panelX, panelY, PANEL_W, panelH, PANEL_EDGE);
        graphics.drawString(font, title, panelX + 10, panelY + 7, 0xFFFFFF);
        graphics.fill(panelX + 10, listY, panelX + PANEL_W - 10, listY + ROWS * ROW, 0xFF15151A);
        if(names.isEmpty()) {
            graphics.drawString(font, Component.translatable("powergrid.gui.plc_files.none"), panelX + 14, listY + 3, 0x808088, false);
        } else {
            int hover = rowAt(mouseX, mouseY);
            for(int i = 0; i < ROWS && i + scroll < names.size(); ++i) {
                int row = i + scroll, y = listY + i * ROW;
                if(row == selected)
                    graphics.fill(panelX + 10, y, panelX + PANEL_W - 10, y + ROW, ROW_SEL);
                else if(row == hover)
                    graphics.fill(panelX + 10, y, panelX + PANEL_W - 10, y + ROW, ROW_HOVER);
                graphics.drawString(font, font.plainSubstrByWidth(names.get(row), PANEL_W - 28), panelX + 14, y + 2, 0xE8E8E8, false);
            }
        }
        int y = listY + ROWS * ROW + 50;
        var line = status.isEmpty() ? Component.translatable("powergrid.gui.plc_files.folder", PlcPrograms.folder().toString()).getString() : status;
        graphics.drawString(font, font.plainSubstrByWidth(line, PANEL_W - 20), panelX + 10, y, status.isEmpty() ? 0x808088 : error ? 0xE06060 : 0x60E060, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
