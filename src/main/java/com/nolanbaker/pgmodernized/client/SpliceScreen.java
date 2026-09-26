package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceReadings;
import com.nolanbaker.pgmodernized.network.packets.SplicePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Splice editor for any {@link ISpliceHost}. The first row is the host's fixed points (a box's
 * cover terminals, a panel's line, neutral and circuits); every hub with a run is a row with one
 * pin per conductor slot, filled when a wire is pulled through it. Click a pin, then another, to
 * draw a splice; click the pair again to remove it. Right-click a pulled wire's pin to cycle its
 * colour. Clicking a hub's label lands all its pulled conductors on the points of the same
 * number. The block entity is the source of truth.
 */
public class SpliceScreen extends Screen {
    private static final int PIN = 12, GAP = 4, ROW = 26, LABEL_WIDTH = 100, PADDING = 12;
    private static final int PANEL_BG = 0xF0202225, PANEL_BORDER = 0xFF5A5E66, TEXT = 0xFFE8E8E8, DIM = 0xFF9A9A9A, LINE = 0xFFE0C060;
    private static final int GLOW = 0xFFFFF4A0, LINE_DIM = 0xFF6A5A30;

    private record Pin(int terminal, int x, int y, int rgb, boolean usable, boolean conductor, Component name) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + PIN && my >= y && my < y + PIN;
        }

        int cx() {
            return x + PIN / 2;
        }

        int cy() {
            return y + PIN / 2;
        }
    }

    private record Row(int hub, Component label, int y) {}

    private final BlockPos pos;
    private final List<Pin> pins = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int panelX, panelY, panelW, panelH;
    private int selected = -1;
    private int headerHeight;
    private long layoutKey = -1;

    public SpliceScreen(BlockPos pos) {
        super(Component.translatable("powergrid.gui.splice.title"));
        this.pos = pos;
    }

    @Nullable
    private ISpliceHost host() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof ISpliceHost host ? host : null;
    }

    /** Changes whenever a hub gains or loses a run, or a run's pulled set or colours change. */
    private static long layoutKey(ISpliceHost host) {
        long key = 0;
        for(int h = 0; h < host.hubCount(); ++h) {
            var run = host.hubRun(h);
            int pulled = 0;
            int colours = 0;
            if(run != null) {
                for(var conductor : run.conductors()) {
                    pulled |= 1 << conductor.slot();
                    colours = colours * 13 + conductor.colorIndex() + 1;
                }
            }
            key = key * 65536 + (run == null ? 0 : (run.size().ordinal() + 1) * 4096 + pulled);
            key = key * 31 + colours;
        }
        return key;
    }

    private void layout(ISpliceHost host) {
        pins.clear();
        rows.clear();
        layoutKey = layoutKey(host);
        int maxPins = host.points().size();
        int rowCount = 1;
        for(int h = 0; h < host.hubCount(); ++h) {
            var run = host.hubRun(h);
            if(run != null) {
                ++rowCount;
                maxPins = Math.max(maxPins, run.size().conductors());
            }
        }
        panelW = PADDING * 2 + LABEL_WIDTH + maxPins * (PIN + GAP);
        headerHeight = 0;
        if(host instanceof ISpliceReadings readings) {
            for(var line : readings.readings()) {
                panelW = Math.max(panelW, PADDING * 2 + font.width(line));
                headerHeight += 10;
            }
            if(headerHeight > 0)
                headerHeight += 6;
        }
        panelH = PADDING * 2 + 16 + headerHeight + rowCount * ROW + 24;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int y = panelY + PADDING + 16 + headerHeight;
        rows.add(new Row(-1, Component.translatable("powergrid.gui.splice.points"), y));
        int x = panelX + PADDING + LABEL_WIDTH;
        for(var point : host.points()) {
            pins.add(new Pin(point.terminal(), x, y + (ROW - PIN) / 2, point.rgb(), true, false, point.name()));
            x += PIN + GAP;
        }
        y += ROW;
        for(int h = 0; h < host.hubCount(); ++h) {
            var run = host.hubRun(h);
            if(run == null)
                continue;
            rows.add(new Row(h, host.hubName(h).copy().append(" " + run.size().label()), y));
            x = panelX + PADDING + LABEL_WIDTH;
            for(int k = 0; k < run.size().conductors(); ++k) {
                var conductor = run.conductor(k);
                int colour = conductor == null ? k : conductor.colorIndex();
                Component name = conductor == null
                        ? Component.translatable("powergrid.gui.splice.empty_slot", k + 1)
                        : Component.literal((k + 1) + " ").append(ConductorColors.name(colour)).append(", ").append(conductor.getItem().getDescription());
                pins.add(new Pin(host.conductorTerminal(h, k), x, y + (ROW - PIN) / 2, ConductorColors.rgb(colour), conductor != null, true, name));
                x += PIN + GAP;
            }
            y += ROW;
        }
    }

    /** Every terminal electrically joined to the given one through splices, including itself. */
    private static Set<Integer> group(ISpliceHost host, int terminal) {
        var seen = new HashSet<Integer>();
        var queue = new ArrayDeque<Integer>();
        queue.add(terminal);
        seen.add(terminal);
        while(!queue.isEmpty()) {
            int current = queue.poll();
            for(var splice : host.splices().list()) {
                int other = splice[0] == current ? splice[1] : splice[1] == current ? splice[0] : -1;
                if(other >= 0 && seen.add(other))
                    queue.add(other);
            }
        }
        return seen;
    }

    @Nullable
    private Pin pin(int terminal) {
        for(var pin : pins) {
            if(pin.terminal == terminal)
                return pin;
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();
        var host = host();
        if(host != null)
            layout(host);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var host = host();
        if(host == null) {
            onClose();
            return;
        }
        if(layoutKey != layoutKey(host))
            layout(host);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        graphics.renderOutline(panelX, panelY, panelW, panelH, PANEL_BORDER);
        graphics.drawString(font, title, panelX + PADDING, panelY + PADDING, TEXT);
        if(host instanceof ISpliceReadings readings) {
            int ry = panelY + PADDING + 14;
            for(var line : readings.readings()) {
                graphics.drawString(font, line, panelX + PADDING, ry, TEXT);
                ry += 10;
            }
        }

        for(var row : rows) {
            boolean clickable = row.hub >= 0;
            boolean hot = clickable && labelContains(row, mouseX, mouseY);
            graphics.drawString(font, row.label, panelX + PADDING, row.y + (ROW - 8) / 2, hot ? LINE : clickable ? TEXT : DIM);
        }
        if(rows.size() == 1)
            graphics.drawString(font, Component.translatable("powergrid.gui.splice.no_runs"), panelX + PADDING, rows.get(0).y + ROW + 4, DIM);

        // The selected pin, or failing that the hovered one, lights up together with everything it is joined to.
        int focus = selected;
        if(focus < 0) {
            for(var pin : pins) {
                if(pin.usable && pin.contains(mouseX, mouseY))
                    focus = pin.terminal;
            }
        }
        Set<Integer> lit = focus < 0 ? Set.of() : group(host, focus);

        for(var splice : host.splices().list()) {
            var a = pin(splice[0]);
            var b = pin(splice[1]);
            if(a == null || b == null)
                continue;
            boolean joined = lit.contains(splice[0]);
            line(graphics, a.cx(), a.cy(), b.cx(), b.cy(), lit.isEmpty() ? LINE : joined ? GLOW : LINE_DIM);
        }

        for(var pin : pins) {
            if(pin.usable)
                graphics.fill(pin.x, pin.y, pin.x + PIN, pin.y + PIN, 0xFF000000 | pin.rgb);
            else
                graphics.fill(pin.x + 3, pin.y + 3, pin.x + PIN - 3, pin.y + PIN - 3, 0xFF2A2C30);
            boolean glow = lit.contains(pin.terminal) && pin.terminal != focus;
            boolean hot = pin.usable && pin.contains(mouseX, mouseY);
            int outline = pin.terminal == selected ? LINE : glow ? GLOW : hot ? 0xFFFFFFFF : 0xFF3A3D42;
            graphics.renderOutline(pin.x - 1, pin.y - 1, PIN + 2, PIN + 2, outline);
            if(glow || pin.terminal == selected)
                graphics.renderOutline(pin.x - 2, pin.y - 2, PIN + 4, PIN + 4, outline);
        }

        var hint = Component.translatable(selected < 0 ? "powergrid.gui.splice.hint_pick" : "powergrid.gui.splice.hint_second");
        graphics.drawString(font, hint, panelX + PADDING, panelY + panelH - PADDING - 8, DIM);

        for(var pin : pins) {
            if(pin.contains(mouseX, mouseY)) {
                var lines = new ArrayList<Component>();
                lines.add(pin.name);
                if(pin.usable && pin.conductor)
                    lines.add(Component.translatable("powergrid.gui.splice.recolor_hint"));
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
        for(var row : rows) {
            if(row.hub >= 0 && labelContains(row, mouseX, mouseY))
                graphics.renderTooltip(font, Component.translatable("powergrid.gui.splice.land_hint"), mouseX, mouseY);
        }
    }

    private boolean labelContains(Row row, double mx, double my) {
        return mx >= panelX + PADDING && mx < panelX + PADDING + LABEL_WIDTH && my >= row.y && my < row.y + ROW;
    }

    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if(steps == 0)
            return;
        for(int i = 0; i <= steps; ++i) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(button == 0) {
            for(var pin : pins) {
                if(!pin.contains(mouseX, mouseY))
                    continue;
                if(!pin.usable)
                    return true;
                if(selected < 0) {
                    selected = pin.terminal;
                } else if(selected == pin.terminal) {
                    selected = -1;
                } else {
                    PacketDistributor.sendToServer(new SplicePayload(pos, SplicePayload.TOGGLE, selected, pin.terminal));
                    selected = -1;
                }
                return true;
            }
            for(var row : rows) {
                if(row.hub >= 0 && labelContains(row, mouseX, mouseY)) {
                    PacketDistributor.sendToServer(new SplicePayload(pos, SplicePayload.LAND, row.hub, 0));
                    selected = -1;
                    return true;
                }
            }
            selected = -1;
        } else if(button == 1) {
            // Right-click a pulled wire: next colour round the sequence.
            var host = host();
            for(var pin : pins) {
                if(!pin.contains(mouseX, mouseY) || !pin.usable || !pin.conductor || host == null)
                    continue;
                int hub = host.hubOf(pin.terminal);
                var run = hub < 0 ? null : host.hubRun(hub);
                var conductor = run == null ? null : run.conductor(host.conductorOf(pin.terminal));
                if(conductor != null)
                    PacketDistributor.sendToServer(new SplicePayload(pos, SplicePayload.RECOLOR, pin.terminal, (conductor.colorIndex() + 1) % ConductorColors.COUNT));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
