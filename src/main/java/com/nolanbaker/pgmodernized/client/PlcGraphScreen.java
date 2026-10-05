package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.plc.NodeType;
import com.nolanbaker.pgmodernized.device.controls.plc.PlcGraph;
import com.nolanbaker.pgmodernized.network.packets.ControlsGraphPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The PLC's program as a canvas of blocks and wires. The palette on the left adds blocks; drag a
 * block by its body, drag from an output pin to an input pin to wire them, right-click a block to
 * set it, Delete removes the selected block, drag empty canvas to pan. Live values from the
 * running program show beside each output pin. Apply sends the drawing to the cabinet, which
 * compiles it; the first problem comes back in the top bar and outlines the block in red.
 */
public class PlcGraphScreen extends Screen {
    static final int TOP = 26, PALETTE_W = 100, NODE_W = 104, WIDE_W = 136, HEAD = 12, PIN_ROW = 11, PIN = 5, BODY = 10, PALETTE_ROW = 18;
    private static final int BG = 0xFF1B1B1F, GRID = 0xFF27272C, PANEL = 0xFF232327, EDGE = 0xFF5A5E66, NODE = 0xFF2E2E34;
    private static final int TEXT = 0xFFE8E8E8, DIM = 0xFF9A9A9A, SEL = 0xFFE0C060, ERR = 0xFFE06060, OK = 0xFF60E060;
    private static final int WIRE_OFF = 0xFF70707A, WIRE_ON = 0xFF60E060, PIN_C = 0xFFC0C0C8, PIN_HOT = 0xFFFFF4A0;
    private static final Map<NodeType.Group, Integer> HEAD_COLORS = Map.of(
            NodeType.Group.IO, 0xFF3A5A8A, NodeType.Group.LOGIC, 0xFF4A6A3A, NodeType.Group.TIMER, 0xFF7A5A2A,
            NodeType.Group.MATH, 0xFF5A3A7A, NodeType.Group.DEVICE, 0xFF8A3A3A, NodeType.Group.SCRIPT, 0xFF2A6A6A);

    private final BlockPos pos;
    private PlcGraph graph = new PlcGraph();
    private boolean loaded;
    private int panX = 24, panY = 24;
    private int selected = -1;
    private int dragNode = -1, dragDx, dragDy;
    private boolean panning;
    private double lastMx, lastMy;
    private int linkFrom = -1, linkFromPin;
    private int paletteScroll;
    private final List<Button> paletteButtons = new ArrayList<>();
    private boolean modified;
    private String status = "";
    private int added;

    public PlcGraphScreen(BlockPos pos) {
        super(Component.translatable("powergrid.gui.controls.plc_editor"));
        this.pos = pos;
    }

    @Nullable
    private ControlsCabinetBlockEntity cabinet() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof ControlsCabinetBlockEntity cabinet ? cabinet : null;
    }

    public PlcGraph graph() {
        return graph;
    }

    /** The properties dialog changed a block. */
    public void edited() {
        graph.prune();
        modified = true;
    }

    @Override
    protected void init() {
        super.init();
        if(!loaded) {
            var cabinet = cabinet();
            graph = cabinet == null ? new PlcGraph() : cabinet.graph().copy();
            loaded = true;
        }
        int x = PALETTE_W + 6;
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.apply"), b -> apply()).bounds(x, 4, 60, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.back"), b -> onClose()).bounds(x + 64, 4, 50, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.plc_edit"), b -> editSelected()).bounds(x + 118, 4, 50, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.plc_delete"), b -> deleteSelected()).bounds(x + 172, 4, 50, 18).build());
        paletteButtons.clear();
        for(var type : NodeType.values()) {
            var button = addRenderableWidget(Button.builder(Component.literal(type.title), b -> addNode(type)).bounds(4, 0, PALETTE_W - 8, 16).build());
            paletteButtons.add(button);
        }
        setPaletteScroll(paletteScroll);
    }

    private void setPaletteScroll(int value) {
        int full = paletteButtons.size() * PALETTE_ROW;
        int visible = height - TOP - 4;
        paletteScroll = Math.max(0, Math.min(Math.max(0, full - visible), value));
        for(int i = 0; i < paletteButtons.size(); ++i) {
            var button = paletteButtons.get(i);
            int y = TOP + 2 + i * PALETTE_ROW - paletteScroll;
            button.visible = y >= TOP && y + 16 <= height;
            button.setY(y);
        }
    }

    private void addNode(NodeType type) {
        int cx = (width - PALETTE_W) / 2 - panX - NODE_W / 2 + (added % 5) * 14;
        int cy = (height - TOP) / 2 - panY - 20 + (added % 5) * 14;
        ++added;
        var node = graph.add(type, cx, cy);
        if(node == null) {
            status = "limit of " + PlcGraph.MAX_NODES + " blocks";
            return;
        }
        selected = node.id;
        modified = true;
    }

    private void deleteSelected() {
        if(selected < 0)
            return;
        graph.remove(selected);
        selected = -1;
        modified = true;
    }

    private void editSelected() {
        var node = graph.node(selected);
        if(node != null)
            Minecraft.getInstance().setScreen(new PlcNodeScreen(this, node, cabinet() == null ? List.of() : cabinet().plcDevices()));
    }

    private void apply() {
        PacketDistributor.sendToServer(new ControlsGraphPayload(pos, graph.save()));
        modified = false;
        status = "";
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new ControlsCabinetScreen(pos));
    }

    // ---- geometry ----

    private int nodeWidth(PlcGraph.Node node) {
        return switch(node.type) {
            case LUA, RUNGS, NOTE, CALL, TAG, SET -> WIDE_W;
            default -> NODE_W;
        };
    }

    private int nodeHeight(PlcGraph.Node node) {
        int rows = Math.max(1, Math.max(node.inputs(), node.outputs()));
        return HEAD + rows * PIN_ROW + (node.type.summary(node).isEmpty() ? 0 : BODY) + 2;
    }

    private int sx(PlcGraph.Node node) {
        return PALETTE_W + panX + node.x;
    }

    private int sy(PlcGraph.Node node) {
        return TOP + panY + node.y;
    }

    private int pinY(PlcGraph.Node node, int pin) {
        return sy(node) + HEAD + pin * PIN_ROW + PIN_ROW / 2;
    }

    @Nullable
    private PlcGraph.Node nodeAt(double mx, double my) {
        for(int i = graph.nodes.size() - 1; i >= 0; --i) {
            var node = graph.nodes.get(i);
            int x = sx(node), y = sy(node);
            if(mx >= x - 3 && mx < x + nodeWidth(node) + 3 && my >= y && my < y + nodeHeight(node))
                return node;
        }
        return null;
    }

    /** {node id, pin} of the output pin under the mouse, or null. */
    private int @Nullable [] outputAt(double mx, double my) {
        for(var node : graph.nodes) {
            int x = sx(node) + nodeWidth(node);
            for(int k = 0; k < node.outputs(); ++k)
                if(Math.abs(mx - x) <= 5 && Math.abs(my - pinY(node, k)) <= 5)
                    return new int[] {node.id, k};
        }
        return null;
    }

    private int @Nullable [] inputAt(double mx, double my) {
        for(var node : graph.nodes) {
            int x = sx(node);
            for(int k = 0; k < node.inputs(); ++k)
                if(Math.abs(mx - x) <= 5 && Math.abs(my - pinY(node, k)) <= 5)
                    return new int[] {node.id, k};
        }
        return null;
    }

    private boolean inCanvas(double mx, double my) {
        return mx >= PALETTE_W && my >= TOP;
    }

    // ---- drawing ----

    private static void wire(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int mid = x2 >= x1 + 16 ? (x1 + x2) / 2 : x1 + 10;
        graphics.fill(Math.min(x1, mid), y1, Math.max(x1, mid) + 2, y1 + 2, color);
        graphics.fill(mid, Math.min(y1, y2), mid + 2, Math.max(y1, y2) + 2, color);
        graphics.fill(Math.min(mid, x2), y2, Math.max(mid, x2) + 2, y2 + 2, color);
    }

    private int errorNode(ControlsCabinetBlockEntity cabinet) {
        var error = cabinet.plcError();
        if(!error.startsWith("#"))
            return -1;
        int end = 1;
        while(end < error.length() && Character.isDigit(error.charAt(end)))
            ++end;
        try {
            return Integer.parseInt(error.substring(1, end));
        } catch(NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var cabinet = cabinet();
        if(cabinet == null) {
            onClose();
            return;
        }
        // Live values and messages, by block id, from the program the cabinet is running.
        var values = new HashMap<Integer, float[]>();
        var messages = new HashMap<Integer, String>();
        var live = cabinet.plcValues();
        var notes = cabinet.plcMessages();
        int at = 0, index = 0;
        for(var node : cabinet.graph().nodes) {
            int n = node.outputs();
            if(at + n <= live.length) {
                var slice = new float[n];
                System.arraycopy(live, at, slice, 0, n);
                values.put(node.id, slice);
            }
            at += n;
            if(index < notes.size() && !notes.get(index).isEmpty())
                messages.put(node.id, notes.get(index));
            ++index;
        }
        int errorNode = errorNode(cabinet);

        graphics.fill(0, 0, width, height, BG);
        graphics.enableScissor(PALETTE_W, TOP, width, height);
        for(int gx = PALETTE_W + Math.floorMod(panX, 24); gx < width; gx += 24)
            for(int gy = TOP + Math.floorMod(panY, 24); gy < height; gy += 24)
                graphics.fill(gx, gy, gx + 1, gy + 1, GRID);

        for(var link : graph.links) {
            var a = graph.node(link.from());
            var b = graph.node(link.to());
            if(a == null || b == null)
                continue;
            var v = values.get(a.id);
            boolean on = v != null && link.fromPin() < v.length && v[link.fromPin()] != 0;
            wire(graphics, sx(a) + nodeWidth(a), pinY(a, link.fromPin()) - 1, sx(b), pinY(b, link.toPin()) - 1, on ? WIRE_ON : WIRE_OFF);
        }
        if(linkFrom >= 0) {
            var a = graph.node(linkFrom);
            if(a != null)
                wire(graphics, sx(a) + nodeWidth(a), pinY(a, linkFromPin) - 1, mouseX, mouseY, SEL);
        }

        var hotOut = outputAt(mouseX, mouseY);
        var hotIn = inputAt(mouseX, mouseY);
        for(var node : graph.nodes) {
            int x = sx(node), y = sy(node), w = nodeWidth(node), h = nodeHeight(node);
            graphics.fill(x, y, x + w, y + h, NODE);
            graphics.fill(x, y, x + w, y + HEAD, HEAD_COLORS.get(node.type.group));
            int outline = node.id == errorNode ? ERR : node.id == selected ? SEL : EDGE;
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, outline);
            graphics.drawString(font, font.plainSubstrByWidth(node.type.title + "  #" + node.id, w - 6), x + 3, y + 2, TEXT, false);
            var in = node.type.inputNames(node);
            for(int k = 0; k < in.length; ++k) {
                int py = pinY(node, k);
                boolean hot = hotIn != null && hotIn[0] == node.id && hotIn[1] == k;
                graphics.fill(x - 2, py - 2, x + 3, py + 3, hot ? PIN_HOT : PIN_C);
                graphics.drawString(font, in[k], x + 6, py - 4, DIM, false);
            }
            var out = node.type.outputNames(node);
            var v = values.get(node.id);
            for(int k = 0; k < out.length; ++k) {
                int py = pinY(node, k);
                boolean hot = hotOut != null && hotOut[0] == node.id && hotOut[1] == k;
                graphics.fill(x + w - 3, py - 2, x + w + 2, py + 3, hot ? PIN_HOT : PIN_C);
                graphics.drawString(font, out[k], x + w - 6 - font.width(out[k]), py - 4, DIM, false);
                if(v != null && k < v.length) {
                    var text = NodeType.format(v[k]);
                    graphics.drawString(font, text, x + w + 6, py - 4, v[k] != 0 ? OK : DIM, false);
                }
            }
            var summary = node.type.summary(node);
            if(!summary.isEmpty())
                graphics.drawString(font, font.plainSubstrByWidth(summary, w - 6), x + 3, y + h - BODY - 1, node.type == NodeType.NOTE ? TEXT : SEL, false);
            var message = messages.get(node.id);
            if(message != null)
                graphics.drawString(font, font.plainSubstrByWidth(message, w * 2), x, y + h + 3, node.id == errorNode ? ERR : DIM, false);
        }
        graphics.disableScissor();

        // Palette and top bar over the canvas.
        graphics.fill(0, TOP, PALETTE_W, height, PANEL);
        graphics.fill(0, 0, width, TOP, PANEL);
        graphics.fill(0, TOP, width, TOP + 1, EDGE);
        graphics.fill(PALETTE_W, TOP, PALETTE_W + 1, height, EDGE);
        graphics.enableScissor(0, TOP, PALETTE_W, height);
        for(var button : paletteButtons)
            button.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
        for(var child : children())
            if(child instanceof Button button && !paletteButtons.contains(button))
                button.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, 6, 9, TEXT, false);

        int textX = PALETTE_W + 236;
        var error = cabinet.plcError();
        String line;
        int color;
        if(!error.isEmpty()) {
            line = error;
            color = ERR;
        } else if(modified) {
            line = Component.translatable("powergrid.gui.controls.plc_modified").getString();
            color = SEL;
        } else if(!status.isEmpty()) {
            line = status;
            color = DIM;
        } else {
            line = Component.translatable(cabinet.graph().isEmpty() ? "powergrid.gui.controls.plc_canvas_hint" : "powergrid.gui.controls.plc_ok").getString();
            color = cabinet.graph().isEmpty() ? DIM : OK;
        }
        graphics.drawString(font, font.plainSubstrByWidth(line, width - textX - 6), textX, 9, color, false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if(!inCanvas(mx, my)) {
            if(my < TOP && mx < PALETTE_W)
                return true;
            return super.mouseClicked(mx, my, button);
        }
        lastMx = mx;
        lastMy = my;
        var out = outputAt(mx, my);
        if(out != null) {
            if(button == 0) {
                linkFrom = out[0];
                linkFromPin = out[1];
            }
            return true;
        }
        var in = inputAt(mx, my);
        if(in != null) {
            var existing = graph.linkInto(in[0], in[1]);
            if(existing != null) {
                graph.unlinkInput(in[0], in[1]);
                modified = true;
                if(button == 0) {
                    linkFrom = existing.from();
                    linkFromPin = existing.fromPin();
                }
            }
            return true;
        }
        var node = nodeAt(mx, my);
        if(node != null) {
            selected = node.id;
            if(button == 1) {
                Minecraft.getInstance().setScreen(new PlcNodeScreen(this, node, cabinet() == null ? List.of() : cabinet().plcDevices()));
            } else {
                dragNode = node.id;
                dragDx = (int) mx - sx(node);
                dragDy = (int) my - sy(node);
                // Bring it to the front.
                graph.nodes.remove(node);
                graph.nodes.add(node);
            }
            return true;
        }
        if(button == 0)
            panning = true;
        selected = -1;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if(dragNode >= 0) {
            var node = graph.node(dragNode);
            if(node != null) {
                node.x = (int) mx - PALETTE_W - panX - dragDx;
                node.y = (int) my - TOP - panY - dragDy;
                modified = true;
            }
            return true;
        }
        if(panning) {
            panX += (int) (mx - lastMx);
            panY += (int) (my - lastMy);
            lastMx = mx;
            lastMy = my;
            return true;
        }
        if(linkFrom >= 0)
            return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if(linkFrom >= 0) {
            var in = inputAt(mx, my);
            if(in != null && graph.link(linkFrom, linkFromPin, in[0], in[1]))
                modified = true;
            linkFrom = -1;
            return true;
        }
        boolean was = dragNode >= 0 || panning;
        dragNode = -1;
        panning = false;
        return was || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if(mx < PALETTE_W && my >= TOP) {
            setPaletteScroll(paletteScroll - (int) Math.signum(scrollY) * PALETTE_ROW);
            return true;
        }
        if(inCanvas(mx, my)) {
            if(hasShiftDown())
                panX += (int) (scrollY * 24);
            else
                panY += (int) (scrollY * 24);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            deleteSelected();
            return true;
        }
        if(keyCode == GLFW.GLFW_KEY_ENTER && selected >= 0) {
            editSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
