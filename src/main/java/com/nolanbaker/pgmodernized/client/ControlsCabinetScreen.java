package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.controls.ControlModule;
import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.PanelDevice;
import com.nolanbaker.pgmodernized.network.packets.ControlsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.CELLS;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.RAIL;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity.CHANNELS;

/**
 * The cabinet with the door open: the rail's modules, the door's devices, and what each device
 * is wired to. Wiring is a pair of arrows that step a device through the channels of every
 * module of the right kind. Items go in by hand on the block and come out with the Remove
 * buttons here (or wire cutters on the door).
 */
public class ControlsCabinetScreen extends Screen {
    private static final int PANEL_W = 330, ROW = 20, PADDING = 10;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFF606068, TEXT = 0xFFE8E8E8, DIM = 0xFF9A9A9A;
    private static final String[] CELL_KEYS = {"top_left", "top_centre", "top_right", "bottom_left", "bottom_centre", "bottom_right"};

    private final BlockPos pos;
    private int panelX, panelY, panelH;
    private long key = -1;
    private final List<Line> lines = new ArrayList<>();

    private record Line(Component text, int y, int color) {}

    public ControlsCabinetScreen(BlockPos pos) {
        super(Component.translatable("powergrid.gui.controls.title"));
        this.pos = pos;
    }

    @Nullable
    private ControlsCabinetBlockEntity cabinet() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof ControlsCabinetBlockEntity cabinet ? cabinet : null;
    }

    private static long keyOf(ControlsCabinetBlockEntity cabinet) {
        long k = cabinet.isPowered() ? 1 : 0;
        for(int slot = 0; slot < RAIL; ++slot)
            k = k * 7 + (cabinet.module(slot) == null ? 0 : cabinet.module(slot).ordinal() + 1);
        for(int cell = 0; cell < CELLS; ++cell) {
            k = k * 9 + (cabinet.device(cell) == null ? 0 : cabinet.device(cell).ordinal() + 1);
            k = k * 67 + cabinet.wireOf(cell) + 2;
            k = k * 11 + cabinet.colorOf(cell);
        }
        return k;
    }

    private static Component moduleName(ControlModule module) {
        return Component.translatable("item.powergrid_modernized." + module.id());
    }

    private static Component deviceName(PanelDevice device) {
        return Component.translatable("item.powergrid_modernized." + device.id());
    }

    /** Every channel index a device in that cell may be wired to, in rail order, then "nothing". */
    private static List<Integer> targets(ControlsCabinetBlockEntity cabinet, int cell) {
        var out = new ArrayList<Integer>();
        var device = cabinet.device(cell);
        if(device == null || !(device.isInput() || device.isOutput()))
            return out;
        var want = device.isInput() ? ControlModule.DIGITAL_IN : ControlModule.DIGITAL_OUT;
        for(int slot = 0; slot < RAIL; ++slot) {
            if(cabinet.module(slot) != want)
                continue;
            for(int ch = 0; ch < CHANNELS; ++ch)
                out.add(slot * CHANNELS + ch);
        }
        out.add(-1);
        return out;
    }

    private static Component wireName(ControlsCabinetBlockEntity cabinet, int cell) {
        var device = cabinet.device(cell);
        int target = cabinet.wireOf(cell);
        if(device == PanelDevice.DISPLAY)
            return Component.translatable("powergrid.gui.controls.by_computer");
        if(device == null || target < 0)
            return Component.translatable("powergrid.gui.controls.not_wired");
        int slot = target / CHANNELS + 1, ch = target % CHANNELS + 1;
        if(device == PanelDevice.SELECTOR)
            return Component.translatable("powergrid.gui.controls.wire_selector", slot, ch, Math.min(CHANNELS, ch + 1));
        return Component.translatable(device.isInput() ? "powergrid.gui.controls.wire_in" : "powergrid.gui.controls.wire_out", slot, ch);
    }

    private void step(ControlsCabinetBlockEntity cabinet, int cell, int direction) {
        var targets = targets(cabinet, cell);
        if(targets.isEmpty())
            return;
        int at = targets.indexOf(cabinet.wireOf(cell));
        int next = Math.floorMod((at < 0 ? targets.size() - 1 : at) + direction, targets.size());
        PacketDistributor.sendToServer(new ControlsPayload(pos, ControlsPayload.WIRE, cell, targets.get(next)));
    }

    @Override
    protected void init() {
        super.init();
        lines.clear();
        var cabinet = cabinet();
        int rows = 2 + RAIL + 1 + CELLS;
        panelH = PADDING * 2 + 14 + rows * ROW;
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        if(cabinet == null)
            return;
        key = keyOf(cabinet);
        int y = panelY + PADDING + 14;
        lines.add(new Line(Component.translatable(cabinet.isPowered() ? "powergrid.gui.controls.powered" : "powergrid.gui.controls.unpowered", String.format("%.0f", cabinet.volts())),
                y + 6, cabinet.isPowered() ? 0xFF60E060 : 0xFFE06060));
        y += ROW;
        lines.add(new Line(Component.translatable("powergrid.gui.controls.rail"), y + 6, DIM));
        y += ROW;
        for(int slot = 0; slot < RAIL; ++slot) {
            var module = cabinet.module(slot);
            var text = Component.translatable("powergrid.gui.controls.slot", slot + 1).append(": ")
                    .append(module == null ? Component.translatable("powergrid.gui.controls.empty") : moduleName(module));
            lines.add(new Line(text, y + 6, module == null ? DIM : TEXT));
            if(module != null) {
                int s = slot;
                addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.remove"),
                                b -> PacketDistributor.sendToServer(new ControlsPayload(pos, ControlsPayload.REMOVE_MODULE, s, 0)))
                        .bounds(panelX + PANEL_W - PADDING - 60, y, 60, 18).build());
            }
            y += ROW;
        }
        lines.add(new Line(Component.translatable("powergrid.gui.controls.door"), y + 6, DIM));
        y += ROW;
        for(int cell = 0; cell < CELLS; ++cell) {
            var device = cabinet.device(cell);
            var text = Component.translatable("powergrid.gui.controls." + CELL_KEYS[cell]).append(": ")
                    .append(device == null ? Component.translatable("powergrid.gui.controls.empty") : deviceName(device));
            lines.add(new Line(text, y + 6, device == null ? DIM : TEXT));
            if(device != null) {
                int c = cell;
                int x = panelX + PADDING + 150;
                if(device.isInput() || device.isOutput()) {
                    addRenderableWidget(Button.builder(Component.literal("<"), b -> step(cabinet, c, -1)).bounds(x, y, 14, 18).build());
                    lines.add(new Line(wireName(cabinet, cell), y + 6, 0xFFE0C060));
                    addRenderableWidget(Button.builder(Component.literal(">"), b -> step(cabinet, c, 1)).bounds(x + 92, y, 14, 18).build());
                } else {
                    lines.add(new Line(wireName(cabinet, cell), y + 6, DIM));
                }
                if(device == PanelDevice.LED) {
                    addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.colour"),
                                    b -> PacketDistributor.sendToServer(new ControlsPayload(pos, ControlsPayload.COLOR, c, cabinet.colorOf(c) + 1)))
                            .bounds(panelX + PANEL_W - PADDING - 108, y, 44, 18).build());
                }
                addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.remove"),
                                b -> PacketDistributor.sendToServer(new ControlsPayload(pos, ControlsPayload.REMOVE_DEVICE, c, 0)))
                        .bounds(panelX + PANEL_W - PADDING - 60, y, 60, 18).build());
            }
            y += ROW;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var cabinet = cabinet();
        if(cabinet == null) {
            onClose();
            return;
        }
        if(keyOf(cabinet) != key)
            rebuildWidgets();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, PANEL_BG);
        graphics.renderOutline(panelX, panelY, PANEL_W, panelH, PANEL_EDGE);
        graphics.drawString(font, title, panelX + PADDING, panelY + PADDING, 0xFFFFFF);
        int wireX = panelX + PADDING + 166;
        for(var line : lines) {
            boolean wired = line.color == 0xFFE0C060 || (line.color == DIM && line.text.getString().length() < 24 && line.y > panelY + PADDING + 14 + ROW * (RAIL + 3));
            int x = isWireLine(line) ? wireX : panelX + PADDING;
            graphics.drawString(font, line.text, x, line.y, line.color);
        }
        for(var child : children()) {
            if(child instanceof Button button)
                button.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.drawString(font, Component.translatable("powergrid.gui.controls.hint"), panelX + PADDING, panelY + panelH - PADDING - 6, DIM);
    }

    /** Wire descriptions sit in the middle column; everything else is a row label on the left. */
    private boolean isWireLine(Line line) {
        var s = line.text.getString();
        return line.color == 0xFFE0C060 || s.equals(Component.translatable("powergrid.gui.controls.not_wired").getString())
                || s.equals(Component.translatable("powergrid.gui.controls.by_computer").getString());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing: the panel is drawn over the world so the cabinet stays in view.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
