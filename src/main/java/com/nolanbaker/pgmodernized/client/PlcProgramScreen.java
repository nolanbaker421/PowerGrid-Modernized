package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.network.packets.ControlsProgramPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The PLC's program: a text box on the left, and on the right the names the cabinet offers and
 * the devices found on its internal network with their methods. Apply compiles it on the server;
 * the first problem comes back as a line under the box.
 */
public class PlcProgramScreen extends Screen {
    private static final int PANEL_W = 460, PANEL_H = 250, PADDING = 10, EDITOR_W = 250;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFF606068, TEXT = 0xFFE8E8E8, DIM = 0xFF9A9A9A;

    private final BlockPos pos;
    private MultiLineEditBox editor;
    private int panelX, panelY;
    private String shownError = "";
    private long errorKey = -1;

    public PlcProgramScreen(BlockPos pos) {
        super(Component.translatable("powergrid.gui.controls.program"));
        this.pos = pos;
    }

    @Nullable
    private ControlsCabinetBlockEntity cabinet() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof ControlsCabinetBlockEntity cabinet ? cabinet : null;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        var cabinet = cabinet();
        editor = new MultiLineEditBox(font, panelX + PADDING, panelY + PADDING + 14, EDITOR_W, PANEL_H - PADDING * 2 - 14 - 40,
                Component.translatable("powergrid.gui.controls.program_hint"), title);
        editor.setCharacterLimit(ControlsProgramPayload.MAX_LENGTH);
        if(cabinet != null)
            editor.setValue(cabinet.program());
        addRenderableWidget(editor);
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.apply"), b -> apply())
                .bounds(panelX + PADDING, panelY + PANEL_H - PADDING - 18, 80, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.back"), b -> onClose())
                .bounds(panelX + PADDING + 90, panelY + PANEL_H - PADDING - 18, 80, 18).build());
        setInitialFocus(editor);
    }

    private void apply() {
        PacketDistributor.sendToServer(new ControlsProgramPayload(pos, editor.getValue()));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new ControlsCabinetScreen(pos));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var cabinet = cabinet();
        if(cabinet == null) {
            onClose();
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, PANEL_BG);
        graphics.renderOutline(panelX, panelY, PANEL_W, PANEL_H, PANEL_EDGE);
        graphics.drawString(font, title, panelX + PADDING, panelY + PADDING, 0xFFFFFF);
        editor.render(graphics, mouseX, mouseY, partialTick);
        for(var child : children()) {
            if(child instanceof Button button)
                button.render(graphics, mouseX, mouseY, partialTick);
        }
        var error = cabinet.plcError();
        graphics.drawString(font, error.isEmpty() ? Component.translatable("powergrid.gui.controls.plc_ok") : Component.literal(error),
                panelX + PADDING, panelY + PANEL_H - PADDING - 30, error.isEmpty() ? 0xFF60E060 : 0xFFE06060);

        // The right column: names, then devices and their methods.
        int x = panelX + PADDING + EDITOR_W + 10;
        int y = panelY + PADDING + 14;
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("powergrid.gui.controls.plc_names"));
        for(String key : new String[] {"plc_names_1", "plc_names_2", "plc_names_3", "plc_names_4"})
            lines.add(Component.translatable("powergrid.gui.controls." + key));
        lines.add(Component.empty());
        lines.add(Component.translatable("powergrid.gui.controls.plc_devices"));
        var devices = cabinet.plcDevices();
        if(devices.isEmpty())
            lines.add(Component.translatable("powergrid.gui.controls.plc_no_devices"));
        for(var device : devices) {
            lines.add(Component.literal(device.get(0) + "  (" + device.get(1) + ")"));
            lines.add(Component.literal("  " + joined(device.subList(2, device.size()))));
        }
        int maxWidth = PANEL_W - PADDING - (x - panelX);
        for(var line : lines) {
            if(y > panelY + PANEL_H - PADDING - 10)
                break;
            for(var part : font.split(line, maxWidth)) {
                graphics.drawString(font, part, x, y, line.getString().startsWith("  ") ? DIM : TEXT);
                y += 9;
            }
        }
    }

    private static String joined(List<String> methods) {
        return String.join(", ", methods);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
