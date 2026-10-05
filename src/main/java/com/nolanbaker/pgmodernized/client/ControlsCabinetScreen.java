package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.controls.ControlModule;
import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.PanelDevice;
import com.nolanbaker.pgmodernized.network.packets.ControlsPayload;
import com.nolanbaker.pgmodernized.network.packets.ControlsVfdPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.CELLS;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.RAIL;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity.CHANNELS;

/**
 * The cabinet with the door open: the rail's modules, the door's devices, and what each device
 * is wired to. Wiring is a pair of arrows that step a device through the channels it may use.
 * A VFD module gets two more rows: the drive it commands, chosen from those found over the
 * Cat6, and its minimum and maximum setting (shift steps by ten, control by a hundred). Items go
 * in by hand on the block and come out with the Remove buttons here, or wire cutters on the door.
 * The rows scroll with the mouse wheel when they outgrow the window.
 */
public class ControlsCabinetScreen extends Screen {
    private static final int PANEL_W = 400, ROW = 20, PADDING = 10, HEADER = 14, FOOTER = 16;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFF606068, TEXT = 0xFFE8E8E8, DIM = 0xFF9A9A9A, WIRE = 0xFFE0C060;
    private static final String[] CELL_KEYS = {"top_left", "top_centre", "top_right", "bottom_left", "bottom_centre", "bottom_right"};
    private static final String[] VFD_CHANNELS = {"vfd_start", "vfd_stop", "vfd_reverse", "vfd_speed"};

    private final BlockPos pos;
    private int panelX, panelY, panelH;
    private int contentTop, contentH, contentFull, scroll;
    private long key = -1;
    private final List<Line> lines = new ArrayList<>();
    /** Each button's y before scrolling. */
    private final Map<Button, Integer> baseY = new HashMap<>();

    private record Line(Component text, int x, int y, int color) {}

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
        for(int slot = 0; slot < RAIL; ++slot) {
            var module = cabinet.module(slot);
            k = k * 7 + (module == null ? 0 : module.ordinal() + 1);
            if(module == ControlModule.VFD) {
                var target = cabinet.vfdTarget(slot);
                k = k * 31 + (target == null ? 0 : target.asLong());
                k = k * 31 + Math.round(cabinet.vfdMin(slot) * 10) + Math.round(cabinet.vfdMax(slot) * 10) * 1_000_003L;
            }
        }
        for(int cell = 0; cell < CELLS; ++cell) {
            k = k * 9 + (cabinet.device(cell) == null ? 0 : cabinet.device(cell).ordinal() + 1);
            k = k * 67 + cabinet.wireOf(cell) + 2;
            k = k * 11 + cabinet.colorOf(cell);
        }
        for(var drive : cabinet.drives())
            k = k * 31 + drive.asLong();
        k = k * 31 + cabinet.plcError().hashCode();
        k = k * 31 + cabinet.plcRevision();
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
        for(int slot = 0; slot < RAIL; ++slot)
            for(int ch = 0; ch < CHANNELS; ++ch)
                if(cabinet.canWire(cell, slot * CHANNELS + ch))
                    out.add(slot * CHANNELS + ch);
        if(!out.isEmpty() || cabinet.device(cell) != null)
            out.add(-1);
        return out;
    }

    private static Component wireName(ControlsCabinetBlockEntity cabinet, int cell) {
        var device = cabinet.device(cell);
        int target = cabinet.wireOf(cell);
        if(device == PanelDevice.DISPLAY && target < 0)
            return Component.translatable("powergrid.gui.controls.by_computer");
        if(device == null || target < 0)
            return Component.translatable("powergrid.gui.controls.not_wired");
        int slot = target / CHANNELS, ch = target % CHANNELS;
        if(cabinet.module(slot) == ControlModule.ANALOG_IN)
            return Component.translatable("powergrid.gui.controls.wire_ai", slot + 1, ch + 1);
        if(cabinet.module(slot) == ControlModule.ANALOG_OUT)
            return Component.translatable("powergrid.gui.controls.wire_ao", slot + 1, ch + 1);
        if(cabinet.module(slot) == ControlModule.VFD)
            return Component.translatable("powergrid.gui.controls.wire_vfd", slot + 1, Component.translatable("powergrid.gui.controls." + VFD_CHANNELS[Math.min(ch, 3)]));
        if(device == PanelDevice.SELECTOR)
            return Component.translatable("powergrid.gui.controls.wire_selector", slot + 1, ch + 1, Math.min(CHANNELS, ch + 2));
        return Component.translatable(device.isInput() ? "powergrid.gui.controls.wire_in" : "powergrid.gui.controls.wire_out", slot + 1, ch + 1);
    }

    private void step(ControlsCabinetBlockEntity cabinet, int cell, int direction) {
        var targets = targets(cabinet, cell);
        if(targets.isEmpty())
            return;
        int at = targets.indexOf(cabinet.wireOf(cell));
        int next = Math.floorMod((at < 0 ? targets.size() - 1 : at) + direction, targets.size());
        PacketDistributor.sendToServer(new ControlsPayload(pos, ControlsPayload.WIRE, cell, targets.get(next)));
    }

    private void stepDrive(ControlsCabinetBlockEntity cabinet, int slot, int direction) {
        var drives = cabinet.drives();
        int at = cabinet.vfdTarget(slot) == null ? drives.size() : drives.indexOf(cabinet.vfdTarget(slot));
        int next = Math.floorMod(at + direction, drives.size() + 1);
        var target = next < drives.size() ? drives.get(next) : null;
        PacketDistributor.sendToServer(new ControlsVfdPayload(pos, slot, target != null, target == null ? BlockPos.ZERO : target, cabinet.vfdMin(slot), cabinet.vfdMax(slot)));
    }

    private void stepRange(ControlsCabinetBlockEntity cabinet, int slot, boolean maximum, int direction) {
        float step = hasControlDown() ? 100 : hasShiftDown() ? 10 : 1;
        float min = cabinet.vfdMin(slot), max = cabinet.vfdMax(slot);
        if(maximum)
            max = Math.max(0, max + direction * step);
        else
            min = Math.max(0, min + direction * step);
        var target = cabinet.vfdTarget(slot);
        PacketDistributor.sendToServer(new ControlsVfdPayload(pos, slot, target != null, target == null ? BlockPos.ZERO : target, min, max));
    }

    private Component driveName(ControlsCabinetBlockEntity cabinet, int slot) {
        var target = cabinet.vfdTarget(slot);
        if(target == null)
            return Component.translatable(cabinet.drives().isEmpty() ? "powergrid.gui.controls.drive_none_found" : "powergrid.gui.controls.drive_none");
        int index = cabinet.drives().indexOf(target);
        boolean hertz = index >= 0 && cabinet.driveUsesHertz(index);
        return Component.translatable(hertz ? "powergrid.gui.controls.drive_hz" : "powergrid.gui.controls.drive_v", target.getX(), target.getY(), target.getZ());
    }

    private String unit(ControlsCabinetBlockEntity cabinet, int slot) {
        var target = cabinet.vfdTarget(slot);
        int index = target == null ? -1 : cabinet.drives().indexOf(target);
        return index >= 0 && !cabinet.driveUsesHertz(index) ? "V" : "Hz";
    }

    private Button button(Component label, int x, int y, int w, Button.OnPress press) {
        var button = addRenderableWidget(Button.builder(label, press).bounds(x, y - scroll, w, 18).build());
        baseY.put(button, y);
        return button;
    }

    private void setScroll(int value) {
        scroll = Math.max(0, Math.min(Math.max(0, contentFull - contentH), value));
        for(var entry : baseY.entrySet())
            entry.getKey().setY(entry.getValue() - scroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(contentFull > contentH) {
            setScroll(scroll - (int) Math.signum(scrollY) * ROW);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Buttons scrolled out of the window must not take clicks through the title or the hint. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(mouseX >= panelX && mouseX < panelX + PANEL_W && (mouseY < contentTop || mouseY >= contentTop + contentH))
            return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void init() {
        super.init();
        lines.clear();
        var cabinet = cabinet();
        int vfds = 0, plcs = 0;
        if(cabinet != null) {
            for(int slot = 0; slot < RAIL; ++slot) {
                if(cabinet.module(slot) == ControlModule.VFD)
                    ++vfds;
                if(cabinet.module(slot) == ControlModule.PLC)
                    ++plcs;
            }
        }
        int rows = 2 + RAIL + vfds * 2 + plcs + 1 + CELLS;
        contentFull = rows * ROW;
        panelH = Math.min(height - 8, PADDING * 2 + HEADER + contentFull + FOOTER);
        contentH = panelH - PADDING * 2 - HEADER - FOOTER;
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        contentTop = panelY + PADDING + HEADER;
        baseY.clear();
        if(cabinet == null)
            return;
        key = keyOf(cabinet);
        int left = panelX + PADDING;
        int right = panelX + PANEL_W - PADDING;
        int y = contentTop;
        lines.add(new Line(Component.translatable(cabinet.isPowered() ? "powergrid.gui.controls.powered" : "powergrid.gui.controls.unpowered", String.format("%.0f", cabinet.volts())),
                left, y + 6, cabinet.isPowered() ? 0xFF60E060 : 0xFFE06060));
        y += ROW;
        lines.add(new Line(Component.translatable("powergrid.gui.controls.rail"), left, y + 6, DIM));
        y += ROW;
        for(int slot = 0; slot < RAIL; ++slot) {
            var module = cabinet.module(slot);
            var text = Component.translatable("powergrid.gui.controls.slot", slot + 1).append(": ")
                    .append(module == null ? Component.translatable("powergrid.gui.controls.empty") : moduleName(module));
            lines.add(new Line(text, left, y + 6, module == null ? DIM : TEXT));
            if(module != null) {
                int s = slot;
                button(Component.translatable("powergrid.gui.controls.remove"), right - 60, y, 60,
                        b -> PacketDistributor.sendToServer(new ControlsPayload(pos, ControlsPayload.REMOVE_MODULE, s, 0)));
            }
            y += ROW;
            if(module == ControlModule.PLC) {
                var error = cabinet.plcError();
                lines.add(new Line(error.isEmpty() ? Component.translatable(cabinet.graphEmpty() ? "powergrid.gui.controls.plc_empty" : "powergrid.gui.controls.plc_ok") : Component.literal(error),
                        left + 12, y + 6, error.isEmpty() ? 0xFF60E060 : 0xFFE06060));
                button(Component.translatable("powergrid.gui.controls.program"), right - 80, y, 80, b -> Minecraft.getInstance().setScreen(new PlcGraphScreen(pos)));
                y += ROW;
            }
            if(module == ControlModule.VFD) {
                int s = slot;
                lines.add(new Line(Component.translatable("powergrid.gui.controls.drive"), left + 12, y + 6, DIM));
                button(Component.literal("<"), left + 50, y, 14, b -> stepDrive(cabinet, s, -1));
                lines.add(new Line(driveName(cabinet, slot), left + 68, y + 6, cabinet.vfdTarget(slot) == null ? DIM : WIRE));
                button(Component.literal(">"), right - 14, y, 14, b -> stepDrive(cabinet, s, 1));
                y += ROW;
                String unit = unit(cabinet, slot);
                lines.add(new Line(Component.translatable("powergrid.gui.controls.min", String.format("%.0f", cabinet.vfdMin(slot)), unit), left + 12, y + 6, TEXT));
                button(Component.literal("-"), left + 100, y, 14, b -> stepRange(cabinet, s, false, -1));
                button(Component.literal("+"), left + 116, y, 14, b -> stepRange(cabinet, s, false, 1));
                lines.add(new Line(Component.translatable("powergrid.gui.controls.max", String.format("%.0f", cabinet.vfdMax(slot)), unit), left + 150, y + 6, TEXT));
                button(Component.literal("-"), left + 240, y, 14, b -> stepRange(cabinet, s, true, -1));
                button(Component.literal("+"), left + 256, y, 14, b -> stepRange(cabinet, s, true, 1));
                lines.add(new Line(Component.translatable(cabinet.vfdRunning(slot) ? "powergrid.gui.controls.running" : "powergrid.gui.controls.stopped"),
                        right - 60, y + 6, cabinet.vfdRunning(slot) ? 0xFF60E060 : DIM));
                y += ROW;
            }
        }
        lines.add(new Line(Component.translatable("powergrid.gui.controls.door"), left, y + 6, DIM));
        y += ROW;
        for(int cell = 0; cell < CELLS; ++cell) {
            var device = cabinet.device(cell);
            var text = Component.translatable("powergrid.gui.controls." + CELL_KEYS[cell]).append(": ")
                    .append(device == null ? Component.translatable("powergrid.gui.controls.empty") : deviceName(device));
            lines.add(new Line(text, left, y + 6, device == null ? DIM : TEXT));
            if(device != null) {
                int c = cell;
                int x = left + 130;
                if(device.isInput() || device.isOutput() || device == PanelDevice.DISPLAY) {
                    button(Component.literal("<"), x, y, 14, b -> step(cabinet, c, -1));
                    lines.add(new Line(wireName(cabinet, cell), x + 18, y + 6, cabinet.wireOf(cell) < 0 ? DIM : WIRE));
                    button(Component.literal(">"), x + 110, y, 14, b -> step(cabinet, c, 1));
                } else {
                    lines.add(new Line(wireName(cabinet, cell), x + 18, y + 6, DIM));
                }
                if(device == PanelDevice.LED) {
                    button(Component.translatable("powergrid.gui.controls.colour"), right - 108, y, 44,
                            b -> PacketDistributor.sendToServer(new ControlsPayload(pos, ControlsPayload.COLOR, c, cabinet.colorOf(c) + 1)));
                }
                button(Component.translatable("powergrid.gui.controls.remove"), right - 60, y, 60,
                        b -> PacketDistributor.sendToServer(new ControlsPayload(pos, ControlsPayload.REMOVE_DEVICE, c, 0)));
            }
            y += ROW;
        }
        setScroll(scroll);
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
        graphics.enableScissor(panelX, contentTop, panelX + PANEL_W, contentTop + contentH);
        for(var line : lines)
            graphics.drawString(font, line.text, line.x, line.y - scroll, line.color);
        boolean inside = mouseY >= contentTop && mouseY < contentTop + contentH;
        for(var child : children()) {
            if(child instanceof Button button)
                button.render(graphics, inside ? mouseX : -1, inside ? mouseY : -1, partialTick);
        }
        graphics.disableScissor();
        if(contentFull > contentH) {
            int track = contentH, thumb = Math.max(10, track * contentH / contentFull);
            int thumbY = contentTop + (track - thumb) * scroll / Math.max(1, contentFull - contentH);
            graphics.fill(panelX + PANEL_W - 5, contentTop, panelX + PANEL_W - 2, contentTop + track, 0xFF303034);
            graphics.fill(panelX + PANEL_W - 5, thumbY, panelX + PANEL_W - 2, thumbY + thumb, 0xFF8A8A92);
        }
        graphics.drawString(font, Component.translatable("powergrid.gui.controls.hint"), panelX + PADDING, panelY + panelH - PADDING - 6, DIM);
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
