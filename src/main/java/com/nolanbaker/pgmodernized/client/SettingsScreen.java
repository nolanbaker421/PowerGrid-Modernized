package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.network.packets.SettingsPayload;
import com.nolanbaker.pgmodernized.radio.ISettingsHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** The settings of a radio, antenna, collector or meter antenna: a short form drawn over the world, Enter applies. */
public class SettingsScreen extends Screen {
    private static final int MIN_panelW = 200, ROW_H = 24, BOX_W = 90;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFF606068;

    private final BlockPos pos;
    private final List<ISettingsHost.Field> fields = new ArrayList<>();
    private final List<EditBox> boxes = new ArrayList<>();
    private int panelX, panelY, panelH, panelW;

    public SettingsScreen(BlockPos pos, Component title) {
        super(title);
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        fields.clear();
        boxes.clear();
        var level = Minecraft.getInstance().level;
        if(level != null && level.getBlockEntity(pos) instanceof ISettingsHost host)
            fields.addAll(host.settingsFields());
        panelH = 40 + ROW_H * fields.size() + 24;
        // The label column fits the widest label; the panel grows if the box would not fit beside it.
        int labelW = 0;
        for(var f : fields)
            labelW = Math.max(labelW, font.width(Component.translatable(f.label())));
        panelW = Math.max(MIN_panelW, 10 + labelW + 8 + BOX_W + 10);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        for(int i = 0; i < fields.size(); ++i) {
            var field = fields.get(i);
            var box = new EditBox(font, panelX + panelW - BOX_W - 10, panelY + 24 + ROW_H * i, BOX_W, 16, Component.translatable(field.label()));
            box.setMaxLength(32);
            if(field.numeric())
                box.setFilter(s -> s.matches("-?[0-9]*\\.?[0-9]*"));
            box.setValue(field.value());
            boxes.add(box);
            addRenderableWidget(box);
        }
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.settings.apply"), b -> apply())
                .bounds(panelX + 10, panelY + panelH - 26, 85, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.settings.cancel"), b -> onClose())
                .bounds(panelX + 105, panelY + panelH - 26, 85, 18).build());
        if(!boxes.isEmpty())
            setInitialFocus(boxes.get(0));
    }

    private void apply() {
        var values = new HashMap<String, String>();
        for(int i = 0; i < fields.size(); ++i)
            values.put(fields.get(i).key(), boxes.get(i).getValue());
        PacketDistributor.sendToServer(new SettingsPayload(pos, values));
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
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        graphics.renderOutline(panelX, panelY, panelW, panelH, PANEL_EDGE);
        graphics.drawString(font, title, panelX + 10, panelY + 8, 0xFFFFFF);
        for(int i = 0; i < fields.size(); ++i)
            graphics.drawString(font, Component.translatable(fields.get(i).label()), panelX + 10, panelY + 28 + ROW_H * i, 0xC0C0C0);
        for(var box : boxes)
            box.render(graphics, mouseX, mouseY, partialTick);
        for(var child : children()) {
            if(child instanceof Button button)
                button.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
