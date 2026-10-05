package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.device.controls.plc.NodeType;
import com.nolanbaker.pgmodernized.device.controls.plc.PlcGraph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One block's settings: its tag, number, pin count, choice, device method or script, as the
 * block type declares them. OK writes them back into the editor's drawing; nothing reaches the
 * cabinet until the editor applies.
 */
public class PlcNodeScreen extends Screen {
    private static final int PADDING = 10, ROW = 24, LABEL_W = 80;
    private static final int PANEL_BG = 0xF0202024, PANEL_EDGE = 0xFF606068, TEXT = 0xFFE8E8E8, DIM = 0xFF9A9A9A, VALUE = 0xFFE0C060;

    private final PlcGraphScreen parent;
    private final PlcGraph.Node node;
    private final List<List<String>> devices;
    private final double[] nums;
    private String text;
    private int deviceIndex = -1, methodIndex;
    private final List<EditBox> numberBoxes = new ArrayList<>();
    private final List<NodeType.Param> numberParams = new ArrayList<>();
    @Nullable
    private EditBox textBox;
    @Nullable
    private MultiLineEditBox scriptBox;
    private final List<Line> lines = new ArrayList<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private int panelX, panelY, panelW, panelH;

    private record Line(String text, int x, int y, int color) {}

    public PlcNodeScreen(PlcGraphScreen parent, PlcGraph.Node node, List<List<String>> devices) {
        super(Component.literal(node.type.title + "  #" + node.id));
        this.parent = parent;
        this.node = node;
        this.devices = devices;
        this.nums = node.nums.clone();
        this.text = node.text;
        if(node.type == NodeType.CALL) {
            int dot = text.lastIndexOf('.');
            String alias = dot > 0 ? text.substring(0, dot) : text;
            String method = dot > 0 ? text.substring(dot + 1) : "";
            for(int i = 0; i < devices.size(); ++i) {
                if(devices.get(i).get(0).equals(alias)) {
                    deviceIndex = i;
                    methodIndex = Math.max(0, devices.get(i).subList(2, devices.get(i).size()).indexOf(method));
                }
            }
            if(deviceIndex < 0 && !devices.isEmpty())
                deviceIndex = 0;
        }
    }

    private boolean hasScript() {
        for(var p : node.type.params)
            if(p.kind() == NodeType.Param.Kind.SCRIPT)
                return true;
        return false;
    }

    @Override
    protected void init() {
        super.init();
        lines.clear();
        refreshers.clear();
        numberBoxes.clear();
        numberParams.clear();
        textBox = null;
        scriptBox = null;
        int rows = 0;
        for(var p : node.type.params)
            rows += p.kind() == NodeType.Param.Kind.DEVICE ? 2 : p.kind() == NodeType.Param.Kind.SCRIPT ? 0 : 1;
        boolean script = hasScript();
        panelW = script ? Math.min(width - 16, 520) : 360;
        panelH = script ? height - 16 : PADDING * 2 + 18 + rows * ROW + 30;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int left = panelX + PADDING;
        int y = panelY + PADDING + 18;
        int scriptH = panelH - PADDING * 2 - 18 - rows * ROW - 30 - 14;

        for(var p : node.type.params) {
            switch(p.kind()) {
                case SCRIPT -> {
                    scriptBox = new MultiLineEditBox(font, left, y, panelW - PADDING * 2, scriptH, Component.empty(), Component.literal(p.label()));
                    scriptBox.setCharacterLimit(PlcGraph.MAX_TEXT);
                    scriptBox.setValue(text);
                    addRenderableWidget(scriptBox);
                    y += scriptH + 2;
                    lines.add(new Line(Component.translatable(node.type == NodeType.LUA ? "powergrid.gui.controls.plc_lua_hint" : "powergrid.gui.controls.plc_rungs_hint").getString(), left, y, DIM));
                    y += 12;
                }
                case TEXT -> {
                    lines.add(new Line(p.label(), left, y + 5, TEXT));
                    textBox = new EditBox(font, left + LABEL_W, y, panelW - PADDING * 2 - LABEL_W, 18, Component.literal(p.label()));
                    textBox.setMaxLength(PlcGraph.MAX_TEXT);
                    textBox.setValue(text);
                    addRenderableWidget(textBox);
                    y += ROW;
                }
                case DEVICE -> {
                    if(devices.isEmpty()) {
                        lines.add(new Line(p.label(), left, y + 5, TEXT));
                        textBox = new EditBox(font, left + LABEL_W, y, panelW - PADDING * 2 - LABEL_W, 18, Component.literal(p.label()));
                        textBox.setValue(text);
                        addRenderableWidget(textBox);
                        y += ROW;
                        lines.add(new Line(Component.translatable("powergrid.gui.controls.plc_no_devices").getString(), left, y + 5, DIM));
                        y += ROW;
                    } else {
                        lines.add(new Line(Component.translatable("powergrid.gui.controls.plc_device").getString(), left, y + 5, TEXT));
                        int yy = y;
                        addRenderableWidget(Button.builder(Component.literal("<"), b -> stepDevice(-1)).bounds(left + LABEL_W, yy, 14, 18).build());
                        addRenderableWidget(Button.builder(Component.literal(">"), b -> stepDevice(1)).bounds(panelX + panelW - PADDING - 14, yy, 14, 18).build());
                        y += ROW;
                        lines.add(new Line(Component.translatable("powergrid.gui.controls.plc_method").getString(), left, y + 5, TEXT));
                        int y2 = y;
                        addRenderableWidget(Button.builder(Component.literal("<"), b -> stepMethod(-1)).bounds(left + LABEL_W, y2, 14, 18).build());
                        addRenderableWidget(Button.builder(Component.literal(">"), b -> stepMethod(1)).bounds(panelX + panelW - PADDING - 14, y2, 14, 18).build());
                        y += ROW;
                    }
                }
                case NUMBER -> {
                    lines.add(new Line(p.label(), left, y + 5, TEXT));
                    var box = new EditBox(font, left + LABEL_W, y, 120, 18, Component.literal(p.label()));
                    box.setValue(NodeType.format(nums[p.index()]));
                    addRenderableWidget(box);
                    numberBoxes.add(box);
                    numberParams.add(p);
                    y += ROW;
                }
                case COUNT -> {
                    lines.add(new Line(p.label(), left, y + 5, TEXT));
                    int yy = y;
                    addRenderableWidget(Button.builder(Component.literal("-"), b -> stepNum(p, -1)).bounds(left + LABEL_W, yy, 14, 18).build());
                    addRenderableWidget(Button.builder(Component.literal("+"), b -> stepNum(p, 1)).bounds(left + LABEL_W + 44, yy, 14, 18).build());
                    y += ROW;
                }
                case CHOICE -> {
                    lines.add(new Line(p.label(), left, y + 5, TEXT));
                    int yy = y;
                    addRenderableWidget(Button.builder(Component.literal("<"), b -> stepNum(p, -1)).bounds(left + LABEL_W, yy, 14, 18).build());
                    addRenderableWidget(Button.builder(Component.literal(">"), b -> stepNum(p, 1)).bounds(panelX + panelW - PADDING - 14, yy, 14, 18).build());
                    y += ROW;
                }
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.plc_ok_btn"), b -> ok()).bounds(left, panelY + panelH - PADDING - 18, 70, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.controls.plc_cancel"), b -> Minecraft.getInstance().setScreen(parent)).bounds(left + 76, panelY + panelH - PADDING - 18, 70, 18).build());
        if(scriptBox != null)
            setInitialFocus(scriptBox);
        else if(textBox != null)
            setInitialFocus(textBox);
    }

    private void stepNum(NodeType.Param p, int direction) {
        nums[p.index()] = Math.max(p.min(), Math.min(p.max(), nums[p.index()] + direction));
    }

    private void stepDevice(int direction) {
        if(devices.isEmpty())
            return;
        deviceIndex = Math.floorMod(deviceIndex + direction, devices.size());
        methodIndex = 0;
    }

    private void stepMethod(int direction) {
        if(deviceIndex < 0)
            return;
        int methods = devices.get(deviceIndex).size() - 2;
        if(methods > 0)
            methodIndex = Math.floorMod(methodIndex + direction, methods);
    }

    private String deviceText() {
        if(deviceIndex < 0 || deviceIndex >= devices.size())
            return text;
        var device = devices.get(deviceIndex);
        String method = device.size() > 2 + methodIndex ? device.get(2 + methodIndex) : "";
        return device.get(0) + "." + method;
    }

    private void ok() {
        if(scriptBox != null)
            text = scriptBox.getValue();
        else if(textBox != null)
            text = textBox.getValue();
        if(node.type == NodeType.CALL && !devices.isEmpty())
            text = deviceText();
        for(int i = 0; i < numberBoxes.size(); ++i) {
            var p = numberParams.get(i);
            try {
                nums[p.index()] = Math.max(p.min(), Math.min(p.max(), Double.parseDouble(numberBoxes.get(i).getValue().strip().replace(',', '.'))));
            } catch(NumberFormatException ignored) {}
        }
        node.text = node.type == NodeType.TAG || node.type == NodeType.SET ? text.strip().toUpperCase(Locale.ROOT) : text;
        node.nums = nums;
        parent.edited();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        graphics.renderOutline(panelX, panelY, panelW, panelH, PANEL_EDGE);
        graphics.drawString(font, title, panelX + PADDING, panelY + PADDING, 0xFFFFFF);
        for(var line : lines)
            graphics.drawString(font, font.plainSubstrByWidth(line.text, panelW - PADDING * 2), line.x, line.y, line.color);
        // Current values of counts, choices and the device pick, between their arrows.
        int left = panelX + PADDING;
        int y = panelY + PADDING + 18;
        for(var p : node.type.params) {
            switch(p.kind()) {
                case SCRIPT -> y += panelH - PADDING * 2 - 18 - rowsOf() * ROW - 30 - 14 + 14;
                case TEXT, NUMBER -> y += ROW;
                case DEVICE -> {
                    if(devices.isEmpty()) {
                        y += ROW * 2;
                    } else {
                        var device = devices.get(deviceIndex);
                        graphics.drawString(font, font.plainSubstrByWidth(device.get(0) + "  (" + device.get(1) + ")", panelW - LABEL_W - 60), left + LABEL_W + 18, y + 5, VALUE);
                        y += ROW;
                        String method = device.size() > 2 + methodIndex ? device.get(2 + methodIndex) : "-";
                        graphics.drawString(font, method, left + LABEL_W + 18, y + 5, VALUE);
                        y += ROW;
                    }
                }
                case COUNT -> {
                    graphics.drawString(font, NodeType.format(nums[p.index()]), left + LABEL_W + 22, y + 5, VALUE);
                    y += ROW;
                }
                case CHOICE -> {
                    graphics.drawString(font, p.choices()[(int) Math.max(0, Math.min(p.choices().length - 1, nums[p.index()]))], left + LABEL_W + 18, y + 5, VALUE);
                    y += ROW;
                }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int rowsOf() {
        int rows = 0;
        for(var p : node.type.params)
            rows += p.kind() == NodeType.Param.Kind.DEVICE ? 2 : p.kind() == NodeType.Param.Kind.SCRIPT ? 0 : 1;
        return rows;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
