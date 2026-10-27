package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.conduit.fire.FireAlarmPanelBlockEntity;
import com.nolanbaker.pgmodernized.conduit.fire.FireDevice;
import com.nolanbaker.pgmodernized.network.packets.FireAlarmPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The fire alarm panel's screen: its state and the buttons, every device by address with its
 * kind, name, state and zone, and the zones with their names. A device's zone steps with the
 * arrows beside it; Find outlines the device's box in the world for a while; a zone's name is
 * typed in a small screen of its own.
 */
public class FireAlarmScreen extends Screen {
    private static final int PANEL_W = 470, ROW = 20, PADDING = 10, HEADER = 14, FOOTER = 16;
    private static final int PANEL_BG = 0xE0202024, PANEL_EDGE = 0xFF606068, TEXT = 0xFFE8E8E8, DIM = 0xFF9A9A9A;
    private static final int RED = 0xFFFF5050, YELLOW = 0xFFFFC040, GREEN = 0xFF60E070, AQUA = 0xFF60D0E0;

    private final BlockPos pos, via;
    private int panelX, panelY, panelH;
    private int contentTop, contentH, contentFull, scroll;
    private long key = -1;
    private final List<Line> lines = new ArrayList<>();
    private final Map<Button, Integer> baseY = new HashMap<>();

    private record Line(Component text, int x, int y, int color) {}

    /** {@code via} is where the player is working it: the panel itself, or an annunciator's box. */
    public FireAlarmScreen(BlockPos pos, BlockPos via) {
        super(Component.translatable("powergrid.gui.fire_alarm.title"));
        this.pos = pos;
        this.via = via;
    }

    @Nullable
    private FireAlarmPanelBlockEntity panel() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof FireAlarmPanelBlockEntity panel ? panel : null;
    }

    private static long keyOf(FireAlarmPanelBlockEntity panel) {
        long k = panel.state().ordinal() + (panel.powered() ? 4 : 0) + (panel.silenced() ? 8 : 0) + (panel.drill() ? 16 : 0);
        for(var device : panel.devices()) {
            k = k * 31 + device.address();
            k = k * 7 + device.status().ordinal();
            k = k * 31 + device.label().hashCode();
            k = k * 17 + panel.zoneOf(device.address());
        }
        for(int z = 1; z <= FireAlarmPanelBlockEntity.MAX_ZONES; ++z)
            k = k * 31 + panel.zoneName(z).hashCode();
        return k;
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(mouseX >= panelX && mouseX < panelX + PANEL_W && (mouseY < contentTop || mouseY >= contentTop + contentH))
            return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static Component statusText(FireDevice.Status status) {
        return Component.translatable("powergrid.gui.fire_alarm.status_" + status.name().toLowerCase());
    }

    private static int statusColor(FireDevice.Status status) {
        return switch(status) {
            case ALARM, SOUNDING, STROBING -> RED;
            case MISSING, NOT_SPLICED -> YELLOW;
            default -> GREEN;
        };
    }

    private Component zoneText(FireAlarmPanelBlockEntity panel, int zone) {
        if(zone <= 0)
            return Component.translatable("powergrid.gui.fire_alarm.no_zone");
        var name = panel.zoneName(zone);
        return name.isEmpty() ? Component.translatable("powergrid.gui.fire_alarm.zone_n", zone) : Component.translatable("powergrid.gui.fire_alarm.zone_named", zone, name);
    }

    /** The zones worth a row: every one with a name or a device, and one empty zone after them for a new assignment. */
    private static List<Integer> zonesShown(FireAlarmPanelBlockEntity panel) {
        var zones = new ArrayList<Integer>();
        int last = 0;
        for(int z = 1; z <= FireAlarmPanelBlockEntity.MAX_ZONES; ++z)
            if(!panel.zoneName(z).isEmpty() || panel.zoneCount(z) > 0)
                last = z;
        for(int z = 1; z <= Math.min(FireAlarmPanelBlockEntity.MAX_ZONES, last + 1); ++z)
            zones.add(z);
        return zones;
    }

    @Override
    protected void init() {
        super.init();
        lines.clear();
        baseY.clear();
        var panel = panel();
        int devices = panel == null ? 0 : panel.devices().size();
        int zones = panel == null ? 1 : zonesShown(panel).size();
        int rows = 2 + 1 + Math.max(1, devices) + 1 + zones;
        contentFull = rows * ROW;
        panelH = Math.min(height - 8, PADDING * 2 + HEADER + contentFull + FOOTER);
        contentH = panelH - PADDING * 2 - HEADER - FOOTER;
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        contentTop = panelY + PADDING + HEADER;
        if(panel == null)
            return;
        key = keyOf(panel);
        int left = panelX + PADDING;
        int right = panelX + PANEL_W - PADDING;
        int y = contentTop;

        // The state, the power, and the two buttons.
        var stateKey = "powergrid.gui.fire_alarm.state_" + panel.state().name().toLowerCase();
        int stateColor = switch(panel.state()) {
            case ALARM -> RED;
            case TROUBLE -> YELLOW;
            default -> GREEN;
        };
        lines.add(new Line(Component.translatable(stateKey), left, y + 6, stateColor));
        lines.add(new Line(Component.translatable(panel.powered() ? "powergrid.gui.fire_alarm.screen_powered" : "powergrid.gui.fire_alarm.unpowered"), left + 80, y + 6, panel.powered() ? DIM : RED));
        if(panel.silenced())
            lines.add(new Line(Component.translatable("powergrid.gui.fire_alarm.silenced"), left + 150, y + 6, YELLOW));
        else if(panel.drill())
            lines.add(new Line(Component.translatable("powergrid.gui.fire_alarm.drill"), left + 150, y + 6, AQUA));
        button(Component.translatable("powergrid.gui.fire_alarm.silence"), right - 130, y, 60, b -> PacketDistributor.sendToServer(new FireAlarmPayload(pos, via, FireAlarmPayload.SILENCE, 0, 0, "")));
        button(Component.translatable("powergrid.gui.fire_alarm.reset"), right - 60, y, 60, b -> PacketDistributor.sendToServer(new FireAlarmPayload(pos, via, FireAlarmPayload.RESET, 0, 0, "")));
        y += ROW;
        y += ROW / 2;

        // The devices.
        lines.add(new Line(Component.translatable("powergrid.gui.fire_alarm.devices_header", devices), left, y + 6, DIM));
        y += ROW;
        if(devices == 0) {
            lines.add(new Line(Component.translatable("powergrid.gui.fire_alarm.no_devices"), left + 12, y + 6, DIM));
            y += ROW;
        }
        for(var device : panel.devices()) {
            var text = Component.literal(String.format("%03d ", device.address())).append(device.kind().title().copy());
            if(!device.label().isEmpty())
                text = text.append("  ").append(Component.literal(device.label()));
            lines.add(new Line(text, left + 12, y + 6, device.status().trouble() ? YELLOW : TEXT));
            lines.add(new Line(statusText(device.status()), left + 170, y + 6, statusColor(device.status())));
            int address = device.address();
            int zone = panel.zoneOf(address);
            int zx = left + 230;
            button(Component.literal("<"), zx, y, 14, b -> PacketDistributor.sendToServer(new FireAlarmPayload(pos, via, FireAlarmPayload.SET_ZONE, address, zone <= 0 ? FireAlarmPanelBlockEntity.MAX_ZONES : zone - 1, "")));
            lines.add(new Line(zoneText(panel, zone), zx + 18, y + 6, zone > 0 ? AQUA : DIM));
            button(Component.literal(">"), zx + 100, y, 14, b -> PacketDistributor.sendToServer(new FireAlarmPayload(pos, via, FireAlarmPayload.SET_ZONE, address, zone >= FireAlarmPanelBlockEntity.MAX_ZONES ? 0 : zone + 1, "")));
            var at = device.pos();
            button(Component.translatable("powergrid.gui.fire_alarm.find"), right - 40, y, 40, b -> FireAlarmHighlight.show(at));
            if(device.status() == FireDevice.Status.MISSING)
                button(Component.translatable("powergrid.gui.fire_alarm.remove"), right - 96, y, 52, b -> PacketDistributor.sendToServer(new FireAlarmPayload(pos, via, FireAlarmPayload.REMOVE, address, 0, "")));
            y += ROW;
        }
        y += ROW / 2;

        // The zones.
        lines.add(new Line(Component.translatable("powergrid.gui.fire_alarm.zones_header"), left, y + 6, DIM));
        y += ROW;
        for(int zone : zonesShown(panel)) {
            boolean inAlarm = panel.zoneAlarm(zone);
            lines.add(new Line(zoneText(panel, zone), left + 12, y + 6, inAlarm ? RED : TEXT));
            lines.add(new Line(Component.translatable("powergrid.gui.fire_alarm.zone_devices", panel.zoneCount(zone)), left + 170, y + 6, DIM));
            if(inAlarm)
                lines.add(new Line(Component.translatable("powergrid.gui.fire_alarm.status_alarm"), left + 250, y + 6, RED));
            int z = zone;
            button(Component.translatable("powergrid.gui.fire_alarm.zone_name"), right - 60, y, 60,
                    b -> Minecraft.getInstance().setScreen(new ZoneNameScreen(pos, via, z, panel.zoneName(z))));
            y += ROW;
        }
        setScroll(scroll);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var panel = panel();
        if(panel == null) {
            onClose();
            return;
        }
        if(keyOf(panel) != key)
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
        graphics.drawString(font, Component.translatable("powergrid.gui.fire_alarm.hint"), panelX + PADDING, panelY + panelH - PADDING - 6, DIM);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A zone's name; Enter applies, Escape goes back to the panel. */
    public static class ZoneNameScreen extends Screen {
        private static final int W = 200, H = 64;
        private final BlockPos pos, via;
        private final int zone;
        private final String current;
        private EditBox field;
        private int x, y;

        public ZoneNameScreen(BlockPos pos, BlockPos via, int zone, String current) {
            super(Component.translatable("powergrid.gui.fire_alarm.zone_name_title", zone));
            this.pos = pos;
            this.via = via;
            this.zone = zone;
            this.current = current;
        }

        @Override
        protected void init() {
            super.init();
            x = (width - W) / 2;
            y = (height - H) / 2;
            field = new EditBox(font, x + 10, y + 20, W - 20, 16, title);
            field.setMaxLength(FireAlarmPayload.MAX_LENGTH);
            field.setValue(current);
            addRenderableWidget(field);
            addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.splice.label_apply"), b -> apply()).bounds(x + 10, y + 40, 85, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.splice.label_clear"), b -> {
                PacketDistributor.sendToServer(new FireAlarmPayload(pos, via, FireAlarmPayload.ZONE_NAME, zone, 0, ""));
                onClose();
            }).bounds(x + W - 95, y + 40, 85, 18).build());
            setInitialFocus(field);
        }

        private void apply() {
            PacketDistributor.sendToServer(new FireAlarmPayload(pos, via, FireAlarmPayload.ZONE_NAME, zone, 0, field.getValue().strip()));
            onClose();
        }

        @Override
        public void onClose() {
            Minecraft.getInstance().setScreen(new FireAlarmScreen(pos, via));
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
            graphics.fill(x, y, x + W, y + H, PANEL_BG);
            graphics.renderOutline(x, y, W, H, PANEL_EDGE);
            graphics.drawString(font, title, x + 10, y + 7, 0xFFFFFF);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
