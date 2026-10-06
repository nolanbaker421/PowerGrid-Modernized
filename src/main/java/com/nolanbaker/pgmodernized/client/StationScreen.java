package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.PanelDevice;
import com.nolanbaker.pgmodernized.device.controls.StationBlockEntity;
import com.nolanbaker.pgmodernized.network.packets.StationPayload;
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

/**
 * A control station's screen: its number, the cabinet it found, and its four cells with what
 * each is wired to, stepped through the cabinet's module channels with arrows like the cabinet's
 * own door. Wiring needs the cabinet loaded on this side, which it is whenever it is in view.
 */
public class StationScreen extends Screen {
    private static final int PANEL_W = 400, ROW = 20, PADDING = 10;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFF606068, TEXT = 0xFFE8E8E8, DIM = 0xFF9A9A9A, WIRE = 0xFFE0C060;
    private static final String[] CELL_KEYS = {"top_left", "top_right", "bottom_left", "bottom_right"};

    private final BlockPos pos;
    private int panelX, panelY, panelH;
    private long key = -1;
    private final List<Line> lines = new ArrayList<>();

    private record Line(Component text, int x, int y, int color) {}

    public StationScreen(BlockPos pos) {
        super(Component.translatable("powergrid.gui.station.screen"));
        this.pos = pos;
    }

    @Nullable
    private StationBlockEntity station() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof StationBlockEntity station ? station : null;
    }

    @Nullable
    private static ControlsCabinetBlockEntity cabinetOf(StationBlockEntity station) {
        var level = Minecraft.getInstance().level;
        var at = station.cabinetPos();
        return level != null && at != null && level.getBlockEntity(at) instanceof ControlsCabinetBlockEntity cabinet ? cabinet : null;
    }

    private static long keyOf(StationBlockEntity station) {
        long k = station.station();
        var at = station.cabinetPos();
        k = k * 31 + (at == null ? 0 : at.asLong());
        var cabinet = cabinetOf(station);
        k = k * 31 + (cabinet == null ? 0 : cabinet.slots() * 2 + (cabinet.isPowered() ? 1 : 0));
        for(int cell = 0; cell < StationBlockEntity.CELLS; ++cell) {
            k = k * 9 + (station.device(cell) == null ? 0 : station.device(cell).ordinal() + 1);
            k = k * 67 + station.wireOf(cell) + 2;
            k = k * 11 + station.colorOf(cell);
            k = k * 67 + station.backlightOf(cell) + 2;
            k = k * 31 + station.labelOf(cell).hashCode();
        }
        return k;
    }

    private void step(StationBlockEntity station, ControlsCabinetBlockEntity cabinet, int cell, int direction) {
        var device = station.device(cell);
        if(device == null)
            return;
        var targets = ControlsCabinetScreen.targetsFor(cabinet, device);
        if(targets.isEmpty())
            return;
        int at = targets.indexOf(station.wireOf(cell));
        int next = Math.floorMod((at < 0 ? targets.size() - 1 : at) + direction, targets.size());
        PacketDistributor.sendToServer(new StationPayload(pos, StationPayload.WIRE, cell, targets.get(next)));
    }

    private void stepBacklight(StationBlockEntity station, ControlsCabinetBlockEntity cabinet, int cell, int direction) {
        var targets = ControlsCabinetScreen.targetsFor(cabinet, PanelDevice.LED);
        int at = targets.indexOf(station.backlightOf(cell));
        int next = Math.floorMod((at < 0 ? targets.size() - 1 : at) + direction, targets.size());
        PacketDistributor.sendToServer(new StationPayload(pos, StationPayload.BACKLIGHT, cell, targets.get(next)));
    }

    private Button button(Component label, int x, int y, int w, Button.OnPress press) {
        return addRenderableWidget(Button.builder(label, press).bounds(x, y, w, 18).build());
    }

    @Override
    protected void init() {
        super.init();
        lines.clear();
        var fittedStation = station();
        int fitted = 0;
        if(fittedStation != null)
            for(int cell = 0; cell < StationBlockEntity.CELLS; ++cell)
                if(fittedStation.device(cell) != null)
                    ++fitted;
        int rows = 3 + StationBlockEntity.CELLS + fitted;
        panelH = PADDING * 2 + 14 + rows * ROW + 10;
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        var station = station();
        if(station == null)
            return;
        key = keyOf(station);
        var cabinet = cabinetOf(station);
        int left = panelX + PADDING;
        int right = panelX + PANEL_W - PADDING;
        int y = panelY + PADDING + 14;
        lines.add(new Line(Component.translatable("powergrid.gui.station.number"), left, y + 6, TEXT));
        button(Component.literal("-"), left + 90, y, 18, b -> PacketDistributor.sendToServer(new StationPayload(pos, StationPayload.NUMBER, station.station() - 1, 0)));
        lines.add(new Line(Component.literal(Integer.toString(station.station())), left + 114, y + 6, WIRE));
        button(Component.literal("+"), left + 134, y, 18, b -> PacketDistributor.sendToServer(new StationPayload(pos, StationPayload.NUMBER, station.station() + 1, 0)));
        lines.add(new Line(Component.translatable("powergrid.gui.station.tags", station.station()), left + 170, y + 6, DIM));
        y += ROW;
        var at = station.cabinetPos();
        Component status;
        int statusColor;
        if(at == null) {
            status = Component.translatable("powergrid.gui.station.unconnected");
            statusColor = 0xFFE06060;
        } else if(cabinet == null) {
            status = Component.translatable("powergrid.gui.station.far", at.getX(), at.getY(), at.getZ());
            statusColor = DIM;
        } else {
            status = Component.translatable(cabinet.isPowered() ? "powergrid.gui.station.connected_live" : "powergrid.gui.station.connected_dead", at.getX(), at.getY(), at.getZ());
            statusColor = cabinet.isPowered() ? 0xFF60E060 : DIM;
        }
        lines.add(new Line(status, left, y + 6, statusColor));
        y += ROW;
        lines.add(new Line(Component.translatable("powergrid.gui.station.cells"), left, y + 6, DIM));
        y += ROW;
        for(int cell = 0; cell < StationBlockEntity.CELLS; ++cell) {
            var device = station.device(cell);
            var text = Component.translatable("powergrid.gui.controls." + CELL_KEYS[cell]).append(": ")
                    .append(device == null ? Component.translatable("powergrid.gui.controls.empty") : Component.translatable("item.powergrid_modernized." + device.id()));
            lines.add(new Line(text, left, y + 6, device == null ? DIM : TEXT));
            if(device != null) {
                int c = cell;
                int x = left + 130;
                if(cabinet != null) {
                    button(Component.literal("<"), x, y, 14, b -> step(station, cabinet, c, -1));
                    lines.add(new Line(ControlsCabinetScreen.wireNameFor(cabinet, device, station.wireOf(cell)), x + 18, y + 6, station.wireOf(cell) < 0 ? DIM : WIRE));
                    button(Component.literal(">"), x + 110, y, 14, b -> step(station, cabinet, c, 1));
                } else {
                    lines.add(new Line(Component.translatable("powergrid.gui.station.no_wiring"), x + 18, y + 6, DIM));
                }
                button(Component.translatable("powergrid.gui.controls.remove"), right - 60, y, 60,
                        b -> PacketDistributor.sendToServer(new StationPayload(pos, StationPayload.REMOVE_DEVICE, c, 0)));
                y += ROW;
                var label = station.labelOf(cell);
                lines.add(new Line(Component.translatable("powergrid.gui.controls.label_is", label.isEmpty() ? "-" : label), left + 12, y + 6, DIM));
                button(Component.translatable("powergrid.gui.controls.label"), left + 110, y, 44,
                        b -> Minecraft.getInstance().setScreen(new CellLabelScreen(pos, c, station.labelOf(c), true)));
                if(device != PanelDevice.DISPLAY)
                    button(Component.translatable("powergrid.gui.controls.colour"), left + 158, y, 44,
                            b -> PacketDistributor.sendToServer(new StationPayload(pos, StationPayload.COLOR, c, station.colorOf(c) + 1)));
                if(ControlsCabinetScreen.isButton(device) && cabinet != null) {
                    button(Component.literal("<"), right - 150, y, 14, b -> stepBacklight(station, cabinet, c, -1));
                    lines.add(new Line(ControlsCabinetScreen.backlightName(cabinet, station.backlightOf(cell)), right - 132, y + 6, station.backlightOf(cell) < 0 ? DIM : WIRE));
                    button(Component.literal(">"), right - 14, y, 14, b -> stepBacklight(station, cabinet, c, 1));
                }
            }
            y += ROW;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var station = station();
        if(station == null) {
            onClose();
            return;
        }
        if(keyOf(station) != key)
            rebuildWidgets();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, PANEL_BG);
        graphics.renderOutline(panelX, panelY, PANEL_W, panelH, PANEL_EDGE);
        graphics.drawString(font, title, panelX + PADDING, panelY + PADDING, 0xFFFFFF);
        for(var line : lines)
            graphics.drawString(font, line.text, line.x, line.y, line.color);
        for(var child : children()) {
            if(child instanceof Button button)
                button.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.drawString(font, Component.translatable("powergrid.gui.station.hint"), panelX + PADDING, panelY + panelH - PADDING - 6, DIM);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
