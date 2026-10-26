package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.conduit.ConductorColors;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceReadings;
import com.nolanbaker.pgmodernized.conduit.splice.SplicePoint;
import com.nolanbaker.pgmodernized.conduit.trough.ITroughSection;
import com.nolanbaker.pgmodernized.conduit.trough.TroughGeometry;
import com.nolanbaker.pgmodernized.device.breaker.BreakerPanelBlockEntity;
import com.nolanbaker.pgmodernized.device.breaker.BreakerState;
import com.nolanbaker.pgmodernized.device.breaker.PanelLayout;
import com.nolanbaker.pgmodernized.device.controls.CabinetLayout;
import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock;
import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.network.packets.SplicePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Splice editor for any {@link ISpliceHost}, drawn as the panel schematic it is. The knockouts
 * with runs stand in two columns, left and right, each with a pin per pulled conductor. Between
 * them the host's own terminals are laid out the way the box is built: a breaker panel as its
 * main, its two columns of breakers with a lug pin on the outer edge of each space, and a neutral
 * bar on the left and a ground bar on the right with a landing slot per wire; a controls cabinet
 * as its supply and its rail of modules with their channel pins; anything else as a plain column.
 * Wires run orthogonally through a duct down each side and a gutter above and below, every wire
 * in a lane of its own, so nothing crosses that does not have to.
 * <p>
 * Click a pin, then another, to draw a splice; click the pair again to remove it. Right-click a
 * pulled wire's pin to cycle its colour, shift and right-click to label it. Click a knockout's
 * name to land all its pulled conductors on the points of the same number. The block entity is
 * the source of truth; the screen only sends requests.
 */
public class SpliceScreen extends Screen {
    private static final int PIN = 8, PITCH = 11, PADDING = 10, HUB_W = 100, DUCT_W = 46, LANE = 4, GUTTER_H = 30, TITLE_H = 16, HINT_H = 14;
    private static final int COL_W = 42, ROW_H = 12, BUS_GAP = 14, BAR_W = 10, BAR_GAP = 10, MAIN_H = 20, BAR_SLOT = 6;
    private static final int MOD_W = 112, MOD_ROW = 11;
    private static final int PANEL_BG = 0xF0202225, PANEL_BORDER = 0xFF5A5E66, TEXT = 0xFFE8E8E8, DIM = 0xFF9A9A9A, LINE = 0xFFE0C060;
    private static final int GLOW = 0xFFFFF4A0, LABEL = 0xFFE8D060;
    private static final int BOX_FILL = 0xFF2A2D32, BOX_EMPTY = 0xFF232528, BOX_BORDER = 0xFF4A4E56, DUCT_FILL = 0xFF191B1E, BUS_FILL = 0xFF3A3326;
    private static final int ON_FILL = 0xFF2C4A30, OFF_FILL = 0xFF383B40, TRIPPED_FILL = 0xFF5A2A2A;

    /** Which duct a pin's wires use: the one it faces. */
    private enum Side { LEFT, RIGHT }

    private record Pin(int terminal, int x, int y, int w, int h, int rgb, boolean usable, boolean conductor, Component name, Side duct, boolean bar) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int cx() {
            return x + w / 2;
        }

        int cy() {
            return y + h / 2;
        }
    }

    private record Box(int x, int y, int w, int h, @Nullable Component label, int fill, int border, int textColor) {}

    /** A knockout's header in a hub column, clickable to land its wires. */
    private record Header(int hub, int x, int y, int w, int h, Component label) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** A header on the left column is right-aligned to the column and may run left of it; the hit box follows the text. */

    private record Route(int a, int b, int[][] points, int rgb) {}

    /** Local layout of the centre column, before it is placed. */
    private static final class Centre {
        int w, h;
        final List<Pin> pins = new ArrayList<>();
        final List<Box> boxes = new ArrayList<>();

        Pin pin(int terminal, int x, int y, int w, int h, SplicePoint point, Side duct, boolean bar) {
            var pin = new Pin(terminal, x, y, w, h, point.rgb(), true, false, point.name(), duct, bar);
            pins.add(pin);
            return pin;
        }
    }

    private final BlockPos pos;
    private final List<Pin> pins = new ArrayList<>();
    private final Map<Integer, Pin> pinByTerminal = new HashMap<>();
    private final List<Box> boxes = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private final List<Component> columnNotes = new ArrayList<>();
    private List<Route> routes = List.of();
    private int panelX, panelY, panelW, panelH, headerHeight;
    private int ductLX, ductRX, contentTop, contentBottom, gutterTopY, gutterBottomY;
    private int selected = -1;
    private int scroll;
    private long layoutKey = -1, splicesKey = -1;

    public SpliceScreen(BlockPos pos) {
        super(Component.translatable("powergrid.gui.splice.title"));
        this.pos = pos;
    }

    @Nullable
    private ISpliceHost host() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof ISpliceHost host && host.splices() != null ? host : null;
    }

    // ------------------------------------------------------------------ keys

    /** Changes whenever a hub gains or loses a run, a run's pulled set or colours change, or the host's own structure does. */
    private static long layoutKey(ISpliceHost host) {
        long key = host.points().size() * 131L + host.hubCount();
        for(int h = 0; h < host.hubCount(); ++h) {
            var run = host.hubRun(h);
            int pulled = 0;
            int colours = 0;
            if(run != null) {
                for(var conductor : run.conductors()) {
                    pulled |= 1 << conductor.slot();
                    colours = colours * 13 + conductor.colorIndex() + 1 + conductor.label().hashCode();
                }
            }
            // Odd multipliers only: a power of two shifts the early hubs' terms clean out of the
            // 64 bits on a twelve-hub box, and their colour changes never redrew.
            key = key * 1_000_003L + (run == null ? 0 : (run.size().ordinal() + 1) * 4096 + pulled);
            key = key * 31 + colours;
        }
        if(host instanceof BreakerPanelBlockEntity panel) {
            key = key * 31 + panel.main().state().ordinal() * 7 + panel.main().rating();
            for(int slot = 0; slot < panel.slotCount(); ++slot) {
                var breaker = panel.breaker(slot);
                key = key * 31 + (breaker.installed() ? 1 : 0) + breaker.state().ordinal() * 2 + breaker.rating() * 8 + breaker.head() * 1024;
            }
        } else if(host instanceof ControlsCabinetBlockEntity cabinet) {
            for(int slot = 0; slot < cabinet.sections() * ControlsCabinetBlock.RAIL; ++slot) {
                var module = cabinet.module(slot);
                key = key * 31 + (module == null ? 0 : module.ordinal() + 1);
            }
        }
        return key;
    }

    private static long splicesKey(ISpliceHost host) {
        long key = 17;
        for(var splice : host.splices().list())
            key = key * 1_000_003L + splice[0] * 65_537L + splice[1];
        return key;
    }

    // ------------------------------------------------------------------ layout

    private void layout(ISpliceHost host) {
        pins.clear();
        pinByTerminal.clear();
        boxes.clear();
        headers.clear();
        columnNotes.clear();
        layoutKey = layoutKey(host);
        splicesKey = -1;

        headerHeight = 0;
        int readingsWidth = 0;
        if(host instanceof ISpliceReadings readings) {
            for(var line : readings.readings()) {
                readingsWidth = Math.max(readingsWidth, font.width(line));
                headerHeight += 10;
            }
            if(headerHeight > 0)
                headerHeight += 6;
        }

        var left = new ArrayList<Integer>();
        var right = new ArrayList<Integer>();
        for(int h = 0; h < host.hubCount(); ++h) {
            if(host.hubRun(h) != null)
                (side(host, h) == Side.LEFT ? left : right).add(h);
        }
        var points = new HashMap<Integer, SplicePoint>();
        for(var point : host.points())
            points.put(point.terminal(), point);
        var centre = host instanceof BreakerPanelBlockEntity panel ? panelCentre(panel, points)
                : host instanceof ControlsCabinetBlockEntity cabinet ? cabinetCentre(cabinet, points)
                : genericCentre(host, points);

        int contentH = Math.max(Math.max(hubsHeight(host, left), hubsHeight(host, right)), centre.h);
        panelW = Math.max(PADDING * 2 + HUB_W * 2 + DUCT_W * 2 + centre.w, PADDING * 2 + readingsWidth);
        panelH = PADDING * 2 + TITLE_H + headerHeight + GUTTER_H + contentH + GUTTER_H + HINT_H;
        panelX = (width - panelW) / 2;
        panelY = Math.max(4, (height - panelH) / 2);
        scroll = Math.max(0, Math.min(scroll, panelH + 8 - height));

        gutterTopY = panelY + PADDING + TITLE_H + headerHeight;
        contentTop = gutterTopY + GUTTER_H;
        contentBottom = contentTop + contentH;
        gutterBottomY = contentBottom;
        ductLX = panelX + PADDING + HUB_W;
        int centreX = ductLX + DUCT_W;
        ductRX = centreX + centre.w;

        for(var pin : centre.pins)
            addPin(new Pin(pin.terminal, pin.x + centreX, pin.y + contentTop, pin.w, pin.h, pin.rgb, pin.usable, pin.conductor, pin.name, pin.duct, pin.bar));
        for(var box : centre.boxes)
            boxes.add(new Box(box.x + centreX, box.y + contentTop, box.w, box.h, box.label, box.fill, box.border, box.textColor));
        placeHubs(host, left, Side.LEFT, panelX + PADDING, contentTop);
        placeHubs(host, right, Side.RIGHT, ductRX + DUCT_W, contentTop);
        if(left.isEmpty() && right.isEmpty())
            columnNotes.add(Component.translatable("powergrid.gui.splice.no_runs"));
    }

    private void addPin(Pin pin) {
        pins.add(pin);
        pinByTerminal.put(pin.terminal, pin);
    }

    /** Which side of the screen a knockout's column goes: where the knockout is on the box, as the viewer sees it. */
    private static Side side(ISpliceHost host, int hub) {
        if(host instanceof BreakerPanelBlockEntity) {
            if(hub >= PanelLayout.EXTRA_HUB_BASE)
                return hub < PanelLayout.EXTRA_HUB_BASE + 2 ? Side.LEFT : Side.RIGHT;
            if(hub < PanelLayout.HUB_COUNT)
                return hub < 4 || (hub >= PanelLayout.EDGE_HUBS && hub < PanelLayout.EDGE_HUBS + 2) ? Side.LEFT : Side.RIGHT;
            int local = (hub - PanelLayout.HUB_COUNT) % PanelLayout.EXT_HUBS;
            return local == 4 || local == 5 ? Side.LEFT : Side.RIGHT;
        }
        if(host instanceof ControlsCabinetBlockEntity) {
            if(hub < ControlsCabinetBlock.HUB_COUNT)
                return hub < 4 || hub == 8 || hub == 9 ? Side.LEFT : Side.RIGHT;
            int local = (hub - ControlsCabinetBlock.HUB_COUNT) % CabinetLayout.EXT_HUBS;
            return local == 6 || local == 7 ? Side.LEFT : Side.RIGHT;
        }
        if(host instanceof ITroughSection) {
            // Top knockouts and the left end on the left, bottom knockouts and the right end on the right.
            int local = hub % TroughGeometry.HUBS_PER_SECTION;
            return local < TroughGeometry.BOTTOM_BASE || local == TroughGeometry.LEFT_END ? Side.LEFT : Side.RIGHT;
        }
        return hub % 2 == 0 ? Side.LEFT : Side.RIGHT;
    }

    private static int hubsHeight(ISpliceHost host, List<Integer> hubs) {
        int h = 0;
        for(int hub : hubs) {
            var run = host.hubRun(hub);
            if(run != null)
                h += 12 + Math.max(1, run.conductors().size()) * PITCH + 6;
        }
        return h;
    }

    /** A column of knockouts: a clickable name with the run's size and fill, then a pin per pulled wire, pins toward the duct. */
    private void placeHubs(ISpliceHost host, List<Integer> hubs, Side side, int x0, int y0) {
        int y = y0;
        for(int hub : hubs) {
            var run = host.hubRun(hub);
            if(run == null)
                continue;
            var pulled = run.conductors();
            var label = host.hubName(hub).copy().append(Component.literal(" (" + run.size().label() + ", " + pulled.size() + " of " + run.capacity() + ")").withStyle(ChatFormatting.GRAY));
            headers.add(new Header(hub, x0, y, HUB_W + DUCT_W, 10, label));
            y += 12;
            if(pulled.isEmpty()) {
                // Nothing to splice yet; the slot stays so the column reads the same.
                y += PITCH + 6;
                continue;
            }
            pulled.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
            for(var conductor : pulled) {
                int k = conductor.slot();
                int colour = conductor.colorIndex();
                Component name = Component.literal((k + 1) + " ").append(ConductorColors.name(colour)).append(", ").append(conductor.getItem().getDescription());
                if(!conductor.label().isEmpty())
                    name = name.copy().append(Component.literal(" \"" + conductor.label() + "\"").withStyle(ChatFormatting.YELLOW));
                int px = side == Side.LEFT ? x0 + HUB_W - PIN - 2 : x0 + 2;
                addPin(new Pin(host.conductorTerminal(hub, k), px, y + (PITCH - PIN) / 2, PIN, PIN, ConductorColors.rgb(colour), true, true, name, side, false));
                y += PITCH;
            }
            y += 6;
        }
    }

    // ---- centres ----

    /** The breaker panel: main on top, two columns of spaces with lug pins outward, a neutral bar left and a ground bar right. */
    private Centre panelCentre(BreakerPanelBlockEntity panel, Map<Integer, SplicePoint> points) {
        var c = new Centre();
        var spec = panel.spec();
        int mainX = BAR_W + BAR_GAP, mainW = COL_W * 2 + BUS_GAP;
        c.w = mainW + 2 * (BAR_W + BAR_GAP);
        int y = 0;

        var main = panel.main();
        Component mainLabel = main.isBreaker()
                ? Component.translatable("powergrid.gui.splice.main").append(" " + main.rating() + " A")
                : Component.translatable("powergrid.gui.splice.main");
        c.boxes.add(new Box(mainX, y, mainW, MAIN_H, mainLabel, main.isBreaker() ? stateFill(main.state()) : BOX_EMPTY, BOX_BORDER, main.isBreaker() ? TEXT : DIM));
        for(int lug = 0; lug < spec.lugs(); ++lug) {
            var point = points.get(spec.lineTerminal(lug));
            if(point == null)
                continue;
            boolean rightSide = lug % 2 == 1;
            int px = rightSide ? mainX + mainW - PIN - 2 : mainX + 2;
            int py = lug < 2 ? y + 2 : y + MAIN_H - PIN - 2;
            c.pin(spec.lineTerminal(lug), px, py, PIN, PIN, point, rightSide ? Side.RIGHT : Side.LEFT, false);
        }
        y += MAIN_H + 6;

        int slots = panel.slotCount();
        int rows = (slots + 1) / 2;
        int rowsTop = y;
        for(int slot = 0; slot < slots; ++slot) {
            var breaker = panel.branch(slot);
            if(breaker.isCovered())
                continue;
            boolean rightCol = PanelLayout.rightColumn(slot);
            int bx = rightCol ? mainX + COL_W + BUS_GAP : mainX;
            int by = rowsTop + (slot / 2) * ROW_H;
            int span = 1;
            for(int s = slot + 2; s < slots && panel.branch(s).head() == slot; s += 2)
                ++span;
            Component label;
            int fill, text = TEXT;
            if(breaker.isBreaker()) {
                label = Component.literal(breaker.rating() + " A" + (breaker.poles() > 1 ? " " + breaker.poles() + "P" : ""));
                fill = stateFill(breaker.state());
            } else if(breaker.isBlank()) {
                label = Component.literal("-");
                fill = BOX_FILL;
                text = DIM;
            } else {
                label = null;
                fill = BOX_EMPTY;
            }
            c.boxes.add(new Box(bx, by, COL_W, span * ROW_H - 1, label, fill, BOX_BORDER, text));
        }
        // The bus between the columns.
        c.boxes.add(new Box(mainX + COL_W + 4, rowsTop, BUS_GAP - 8, rows * ROW_H - 1, null, BUS_FILL, BUS_FILL, TEXT));
        for(int slot = 0; slot < slots; ++slot) {
            int terminal = PanelLayout.branchTerminal(spec, slot);
            var point = points.get(terminal);
            if(point == null)
                continue;
            boolean rightCol = PanelLayout.rightColumn(slot);
            int bx = rightCol ? mainX + COL_W + BUS_GAP : mainX;
            int by = rowsTop + (slot / 2) * ROW_H;
            c.pin(terminal, rightCol ? bx + COL_W - PIN - 1 : bx + 1, by + (ROW_H - PIN) / 2, PIN, PIN, point, rightCol ? Side.RIGHT : Side.LEFT, false);
        }
        int rowsH = Math.max(ROW_H, rows * ROW_H - 1);
        var neutral = points.get(spec.neutralTerminal());
        if(neutral != null)
            c.pin(spec.neutralTerminal(), 0, rowsTop, BAR_W, rowsH, neutral, Side.LEFT, true);
        var ground = points.get(PanelLayout.groundTerminal(spec));
        if(ground != null)
            c.pin(PanelLayout.groundTerminal(spec), c.w - BAR_W, rowsTop, BAR_W, rowsH, ground, Side.RIGHT, true);
        c.h = rowsTop + rowsH;
        return c;
    }

    private static int stateFill(BreakerState state) {
        return switch(state) {
            case ON -> ON_FILL;
            case OFF -> OFF_FILL;
            case TRIPPED -> TRIPPED_FILL;
        };
    }

    /** The controls cabinet: its supply, then the rail, one box per slot with the module's channel pins. */
    private Centre cabinetCentre(ControlsCabinetBlockEntity cabinet, Map<Integer, SplicePoint> points) {
        var c = new Centre();
        // Wide enough for the longest module name on the rail, so a module reads whole.
        int w = MOD_W;
        for(int slot = 0; slot < cabinet.sections() * ControlsCabinetBlock.RAIL; ++slot) {
            var module = cabinet.module(slot);
            if(module != null)
                w = Math.max(w, font.width((slot + 1) + " ") + font.width(Component.translatable("item.powergrid_modernized." + module.id())) + 2 * (PIN + 8));
        }
        c.w = w;
        int y = 0;
        var placed = new HashSet<Integer>();

        c.boxes.add(new Box(0, y, c.w, MOD_ROW * 2 - 1, Component.translatable("powergrid.gui.splice.supply"), BOX_FILL, BOX_BORDER, TEXT));
        for(int terminal : new int[] {ControlsCabinetBlock.TERMINAL_LINE, ControlsCabinetBlock.TERMINAL_NEUTRAL}) {
            var point = points.get(terminal);
            if(point == null)
                continue;
            c.pin(terminal, 1, y + terminal * MOD_ROW + (MOD_ROW - PIN) / 2, PIN, PIN, point, Side.LEFT, false);
            placed.add(terminal);
        }
        y += MOD_ROW * 2 + 4;

        int slots = cabinet.sections() * ControlsCabinetBlock.RAIL;
        for(int slot = 0; slot < slots; ++slot) {
            var module = cabinet.module(slot);
            int rowCount = module == null ? 1 : switch(module) {
                case RELAY -> ControlsCabinetBlock.RELAY_CHANNELS;
                case DIGITAL_IN, DIGITAL_OUT -> ControlsCabinetBlock.IO_CHANNELS / 2;
                case ANALOG_IN -> ControlsCabinetBlock.ANALOG_CHANNELS / 2;
                default -> 1;
            };
            Component label = module == null
                    ? Component.translatable("powergrid.gui.splice.empty_module", slot + 1)
                    : Component.literal((slot + 1) + " ").append(Component.translatable("item.powergrid_modernized." + module.id()));
            c.boxes.add(new Box(0, y, c.w, rowCount * MOD_ROW - 1, label, module == null ? BOX_EMPTY : BOX_FILL, BOX_BORDER, module == null ? DIM : TEXT));
            if(module != null) {
                switch(module) {
                    case RELAY -> {
                        for(int ch = 0; ch < ControlsCabinetBlock.RELAY_CHANNELS; ++ch) {
                            modulePin(c, points, placed, CabinetLayout.relayTerminal(slot, ch, 0), y + ch * MOD_ROW, Side.LEFT);
                            modulePin(c, points, placed, CabinetLayout.relayTerminal(slot, ch, 1), y + ch * MOD_ROW, Side.RIGHT);
                        }
                    }
                    case DIGITAL_IN, DIGITAL_OUT -> {
                        int half = ControlsCabinetBlock.IO_CHANNELS / 2;
                        for(int ch = 0; ch < ControlsCabinetBlock.IO_CHANNELS; ++ch)
                            modulePin(c, points, placed, CabinetLayout.ioTerminal(slot, ch), y + (ch % half) * MOD_ROW, ch < half ? Side.LEFT : Side.RIGHT);
                    }
                    case ANALOG_IN -> {
                        int half = ControlsCabinetBlock.ANALOG_CHANNELS / 2;
                        for(int ch = 0; ch < ControlsCabinetBlock.ANALOG_CHANNELS; ++ch)
                            modulePin(c, points, placed, CabinetLayout.aiTerminal(slot, ch), y + (ch % half) * MOD_ROW, ch < half ? Side.LEFT : Side.RIGHT);
                    }
                    default -> {}
                }
            }
            y += rowCount * MOD_ROW + 3;
        }
        y = leftovers(c, points, placed, y);
        c.h = y;
        return c;
    }

    private static void modulePin(Centre c, Map<Integer, SplicePoint> points, Set<Integer> placed, int terminal, int rowY, Side duct) {
        var point = points.get(terminal);
        if(point == null)
            return;
        c.pin(terminal, duct == Side.LEFT ? 1 : c.w - PIN - 1, rowY + (MOD_ROW - PIN) / 2, PIN, PIN, point, duct, false);
        placed.add(terminal);
    }

    /** Points the structured layout did not place, so nothing a host offers is ever unreachable. */
    private int leftovers(Centre c, Map<Integer, SplicePoint> points, Set<Integer> placed, int y) {
        var rest = new ArrayList<SplicePoint>();
        for(var point : points.values())
            if(!placed.contains(point.terminal()))
                rest.add(point);
        if(rest.isEmpty())
            return y;
        rest.sort((a, b) -> Integer.compare(a.terminal(), b.terminal()));
        y += 2;
        c.boxes.add(new Box(0, y, c.w, rest.size() * MOD_ROW + MOD_ROW - 1, Component.translatable("powergrid.gui.splice.other"), BOX_EMPTY, BOX_BORDER, DIM));
        y += MOD_ROW;
        for(var point : rest) {
            c.pin(point.terminal(), 1, y + (MOD_ROW - PIN) / 2, PIN, PIN, point, Side.LEFT, false);
            y += MOD_ROW;
        }
        return y;
    }

    /** Any other host: its points in one column, names beside them, pins toward the left duct. */
    private Centre genericCentre(ISpliceHost host, Map<Integer, SplicePoint> points) {
        var c = new Centre();
        int w = 60;
        for(var point : host.points())
            w = Math.max(w, PIN + 6 + font.width(point.name()));
        c.w = Math.min(170, w);
        int y = 0;
        for(var point : host.points()) {
            c.pin(point.terminal(), 0, y + (PITCH - PIN) / 2, PIN, PIN, point, Side.LEFT, false);
            y += PITCH;
        }
        c.h = Math.max(PITCH, y);
        return c;
    }

    // ------------------------------------------------------------------ routing

    /** Every wire leaves its pin toward its duct, from the edge that faces it. */
    private int exitX(Pin pin) {
        int ductX = pin.duct == Side.LEFT ? ductLX : ductRX;
        return pin.cx() < ductX ? pin.x + pin.w : pin.x;
    }

    private record Demand(int route, int lo, int hi) {}

    /** Greedy interval colouring: wires whose spans overlap get different lanes, the rest share. */
    private static Map<Integer, Integer> lanes(List<Demand> demands) {
        demands.sort((a, b) -> Integer.compare(a.lo, b.lo));
        var laneEnd = new ArrayList<Integer>();
        var out = new HashMap<Integer, Integer>();
        for(var d : demands) {
            int lane = -1;
            for(int i = 0; i < laneEnd.size(); ++i) {
                if(laneEnd.get(i) < d.lo - 2) {
                    lane = i;
                    break;
                }
            }
            if(lane < 0) {
                lane = laneEnd.size();
                laneEnd.add(d.hi);
            } else {
                laneEnd.set(lane, d.hi);
            }
            out.put(d.route, lane);
        }
        return out;
    }

    private int ductLane(Side duct, int lane) {
        int lanesAcross = Math.max(1, (DUCT_W - 6) / LANE);
        return (duct == Side.LEFT ? ductLX : ductRX) + 3 + (lane % lanesAcross) * LANE;
    }

    private int gutterLane(boolean top, int lane) {
        int lanesAcross = Math.max(1, (GUTTER_H - 6) / LANE);
        return (top ? gutterTopY : gutterBottomY) + 3 + (lane % lanesAcross) * LANE;
    }

    private void route(ISpliceHost host) {
        splicesKey = splicesKey(host);
        var list = host.splices().list();
        var out = new ArrayList<Route>(list.size());
        var barUse = new HashMap<Integer, Integer>();
        var ductDemands = new ArrayList<List<Demand>>(List.of(new ArrayList<>(), new ArrayList<>()));
        var gutterDemands = new ArrayList<List<Demand>>(List.of(new ArrayList<>(), new ArrayList<>()));
        var ends = new ArrayList<int[]>();   // per route: ya, yb, gutter (0 top, 1 bottom, -1 none)
        var pairs = new ArrayList<Pin[]>();
        int mid = (contentTop + contentBottom) / 2;
        for(var splice : list) {
            var a = pinByTerminal.get(splice[0]);
            var b = pinByTerminal.get(splice[1]);
            if(a == null || b == null)
                continue;
            int i = pairs.size();
            pairs.add(new Pin[] {a, b});
            int ya = a.bar ? barSlotY(a, barUse) : a.cy();
            int yb = b.bar ? barSlotY(b, barUse) : b.cy();
            int gutter = -1;
            if(a.duct == b.duct) {
                ductDemands.get(a.duct.ordinal()).add(new Demand(i, Math.min(ya, yb), Math.max(ya, yb)));
            } else {
                gutter = (ya + yb) / 2 < mid ? 0 : 1;
                int gy = gutter == 0 ? gutterTopY : gutterBottomY + GUTTER_H;
                ductDemands.get(a.duct.ordinal()).add(new Demand(i, Math.min(ya, gy), Math.max(ya, gy)));
                ductDemands.get(b.duct.ordinal()).add(new Demand(i, Math.min(yb, gy), Math.max(yb, gy)));
                gutterDemands.get(gutter).add(new Demand(i, ductLX, ductRX + DUCT_W));
            }
            ends.add(new int[] {ya, yb, gutter});
        }
        var laneL = lanes(ductDemands.get(0));
        var laneR = lanes(ductDemands.get(1));
        var laneTop = lanes(gutterDemands.get(0));
        var laneBottom = lanes(gutterDemands.get(1));
        for(int i = 0; i < pairs.size(); ++i) {
            var a = pairs.get(i)[0];
            var b = pairs.get(i)[1];
            int ya = ends.get(i)[0], yb = ends.get(i)[1], gutter = ends.get(i)[2];
            int rgb = a.conductor ? a.rgb : b.conductor ? b.rgb : a.rgb;
            int[][] points;
            if(gutter < 0) {
                int lane = (a.duct == Side.LEFT ? laneL : laneR).getOrDefault(i, 0);
                int lx = ductLane(a.duct, lane);
                points = new int[][] {{exitX(a), ya}, {lx, ya}, {lx, yb}, {exitX(b), yb}};
            } else {
                int lxa = ductLane(a.duct, (a.duct == Side.LEFT ? laneL : laneR).getOrDefault(i, 0));
                int lxb = ductLane(b.duct, (b.duct == Side.LEFT ? laneL : laneR).getOrDefault(i, 0));
                int gy = gutterLane(gutter == 0, (gutter == 0 ? laneTop : laneBottom).getOrDefault(i, 0));
                points = new int[][] {{exitX(a), ya}, {lxa, ya}, {lxa, gy}, {lxb, gy}, {lxb, yb}, {exitX(b), yb}};
            }
            out.add(new Route(a.terminal, b.terminal, points, rgb));
        }
        routes = out;
    }

    /** The next free landing slot down a bar, in splice order. */
    private static int barSlotY(Pin bar, Map<Integer, Integer> barUse) {
        int n = barUse.merge(bar.terminal, 1, Integer::sum) - 1;
        return Math.min(bar.y + bar.h - 4, bar.y + 10 + n * BAR_SLOT);
    }

    /** Every terminal electrically joined to the given one through splices, including itself. */
    private static Set<Integer> group(ISpliceHost host, int start) {
        var seen = new HashSet<Integer>();
        var queue = new ArrayDeque<Integer>();
        queue.add(start);
        seen.add(start);
        while(!queue.isEmpty()) {
            int t = queue.poll();
            for(var splice : host.splices().list()) {
                int other = splice[0] == t ? splice[1] : splice[1] == t ? splice[0] : -1;
                if(other >= 0 && seen.add(other))
                    queue.add(other);
            }
        }
        return seen;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var host = host();
        if(host == null) {
            onClose();
            return;
        }
        if(layoutKey != layoutKey(host))
            layout(host);
        if(splicesKey != splicesKey(host))
            route(host);
        super.render(graphics, mouseX, mouseY, partialTick);
        int my = mouseY + scroll;

        graphics.pose().pushPose();
        graphics.pose().translate(0, -scroll, 0);
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
        // Ducts and gutters: the raceways the wires run in.
        graphics.fill(ductLX, gutterTopY, ductLX + DUCT_W, gutterBottomY + GUTTER_H, DUCT_FILL);
        graphics.fill(ductRX, gutterTopY, ductRX + DUCT_W, gutterBottomY + GUTTER_H, DUCT_FILL);
        graphics.fill(ductLX, gutterTopY, ductRX + DUCT_W, gutterTopY + GUTTER_H, DUCT_FILL);
        graphics.fill(ductLX, gutterBottomY, ductRX + DUCT_W, gutterBottomY + GUTTER_H, DUCT_FILL);

        for(var box : boxes) {
            graphics.fill(box.x, box.y, box.x + box.w, box.y + box.h, box.fill);
            graphics.renderOutline(box.x, box.y, box.w, box.h, box.border);
            if(box.label != null) {
                var text = font.plainSubstrByWidth(box.label.getString(), Math.max(8, box.w - PIN * 2 - 6));
                int tw = font.width(text);
                graphics.drawString(font, text, box.x + (box.w - tw) / 2, box.y + Math.max(1, (Math.min(box.h, ROW_H) - 8) / 2), box.textColor, false);
            }
        }
        for(var header : headers) {
            boolean hot = header.contains(mouseX, my);
            boolean leftColumn = header.x < ductLX;
            var text = font.plainSubstrByWidth(header.label.getString(), HUB_W + DUCT_W - 4);
            int tx = leftColumn ? Math.max(panelX + PADDING, header.x + HUB_W - font.width(text)) : header.x;
            graphics.drawString(font, text, tx, header.y, hot ? LINE : 0xFF6FD3E8, false);
        }
        for(var note : columnNotes)
            graphics.drawString(font, note, panelX + PADDING, contentTop, DIM);
        if(host instanceof BreakerPanelBlockEntity == false && host instanceof ControlsCabinetBlockEntity == false) {
            // Generic hosts: the point names beside their pins.
            for(var pin : pins) {
                if(!pin.conductor && !pin.bar)
                    graphics.drawString(font, font.plainSubstrByWidth(pin.name.getString(), ductRX - pin.x - PIN - 8), pin.x + PIN + 4, pin.y, TEXT, false);
            }
        }
        // Conductor numbers beside their pins, away from the duct, with the wire's label after the number.
        for(var pin : pins) {
            if(!pin.conductor)
                continue;
            int hub = host.hubOf(pin.terminal);
            var run = hub >= 0 ? host.hubRun(hub) : null;
            var conductor = run == null ? null : run.conductor(host.conductorOf(pin.terminal));
            String number = String.valueOf(host.conductorOf(pin.terminal) + 1);
            String label = conductor == null || conductor.label().isEmpty() ? "" : " " + conductor.label();
            String shown = label.isEmpty() ? number : font.plainSubstrByWidth(number + label, HUB_W - PIN - 8);
            boolean leftColumn = pin.duct == Side.LEFT && pin.cx() < ductLX;
            int tx = leftColumn ? pin.x - 4 - font.width(shown) : pin.x + PIN + 4;
            graphics.drawString(font, number, tx, pin.y, hub >= 0 && pin.usable ? TEXT : DIM, false);
            if(shown.length() > number.length())
                graphics.drawString(font, shown.substring(number.length()), tx + font.width(number), pin.y, LABEL, false);
        }

        // The selected pin, or failing that the hovered one, lights up together with everything it is joined to.
        int focus = selected;
        if(focus < 0) {
            for(var pin : pins) {
                if(pin.usable && pin.contains(mouseX, my))
                    focus = pin.terminal;
            }
        }
        Set<Integer> lit = focus < 0 ? Set.of() : group(host, focus);

        for(var route : routes) {
            boolean joined = lit.contains(route.a);
            int color = lit.isEmpty() ? route.rgb | 0xFF000000 : joined ? GLOW : dim(route.rgb);
            for(int i = 0; i + 1 < route.points.length; ++i)
                line(graphics, route.points[i][0], route.points[i][1], route.points[i + 1][0], route.points[i + 1][1], color);
        }

        for(var pin : pins) {
            boolean glow = lit.contains(pin.terminal) && pin.terminal != focus;
            boolean hot = pin.usable && pin.contains(mouseX, my);
            int outline = pin.terminal == selected ? LINE : glow ? GLOW : hot ? 0xFFFFFFFF : 0xFF3A3D42;
            if(pin.bar) {
                graphics.fill(pin.x, pin.y, pin.x + pin.w, pin.y + pin.h, 0xFF000000 | dim(pin.rgb));
                graphics.fill(pin.x + 2, pin.y + 8, pin.x + pin.w - 2, pin.y + pin.h - 2, 0xFF000000 | pin.rgb);
                graphics.drawString(font, pin.name.getString().substring(0, 1), pin.x + 2, pin.y + 1, 0xFF000000, false);
                graphics.renderOutline(pin.x, pin.y, pin.w, pin.h, outline);
                continue;
            }
            if(pin.usable)
                graphics.fill(pin.x, pin.y, pin.x + pin.w, pin.y + pin.h, 0xFF000000 | pin.rgb);
            else
                graphics.fill(pin.x + 2, pin.y + 2, pin.x + pin.w - 2, pin.y + pin.h - 2, 0xFF2A2C30);
            graphics.renderOutline(pin.x - 1, pin.y - 1, pin.w + 2, pin.h + 2, outline);
            if(glow || pin.terminal == selected)
                graphics.renderOutline(pin.x - 2, pin.y - 2, pin.w + 4, pin.h + 4, outline);
        }

        var hint = Component.translatable(selected < 0 ? "powergrid.gui.splice.hint_pick" : "powergrid.gui.splice.hint_second");
        graphics.drawString(font, hint, panelX + PADDING, panelY + panelH - PADDING - 8, DIM);
        graphics.pose().popPose();

        for(var pin : pins) {
            if(pin.contains(mouseX, my)) {
                var lines = new ArrayList<Component>();
                lines.add(pin.name);
                if(pin.usable && pin.conductor) {
                    lines.add(Component.translatable("powergrid.gui.splice.recolor_hint"));
                    lines.add(Component.translatable("powergrid.gui.splice.label_hint"));
                }
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
        for(var header : headers) {
            if(header.contains(mouseX, my))
                graphics.renderComponentTooltip(font, List.of(header.label, Component.translatable("powergrid.gui.splice.land_hint")), mouseX, mouseY);
        }
    }

    private static int dim(int rgb) {
        int r = (rgb >> 16 & 0xFF) * 2 / 5, g = (rgb >> 8 & 0xFF) * 2 / 5, b = (rgb & 0xFF) * 2 / 5;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** An axis-aligned two-pixel line; anything else falls back to steps. */
    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        if(y1 == y2) {
            graphics.fill(Math.min(x1, x2), y1 - 1, Math.max(x1, x2) + 1, y1 + 1, color);
            return;
        }
        if(x1 == x2) {
            graphics.fill(x1 - 1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2) + 1, color);
            return;
        }
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for(int i = 0; i <= steps; ++i) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double my = mouseY + scroll;
        if(button == 0) {
            for(var pin : pins) {
                if(!pin.contains(mouseX, my))
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
            for(var header : headers) {
                if(header.contains(mouseX, my)) {
                    PacketDistributor.sendToServer(new SplicePayload(pos, SplicePayload.LAND, header.hub, 0));
                    selected = -1;
                    return true;
                }
            }
            selected = -1;
        } else if(button == 1) {
            // Right-click a pulled wire: next colour round the sequence; with shift, a label.
            var host = host();
            for(var pin : pins) {
                if(!pin.contains(mouseX, my) || !pin.usable || !pin.conductor || host == null)
                    continue;
                int hub = host.hubOf(pin.terminal);
                var run = hub < 0 ? null : host.hubRun(hub);
                var conductor = run == null ? null : run.conductor(host.conductorOf(pin.terminal));
                if(conductor != null && hasShiftDown())
                    Minecraft.getInstance().setScreen(new ConductorLabelScreen(pos, pin.terminal, pin.name, conductor.label()));
                else if(conductor != null)
                    PacketDistributor.sendToServer(new SplicePayload(pos, SplicePayload.RECOLOR, pin.terminal, (conductor.colorIndex() + 1) % ConductorColors.COUNT));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, panelH + 8 - height);
        if(max == 0)
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY * 14));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
