package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.device.fixture.FixtureDesign;
import com.nolanbaker.pgmodernized.device.fixture.FixturePart;
import com.nolanbaker.pgmodernized.network.packets.FixtureBuildPayload;
import com.nolanbaker.pgmodernized.network.packets.FixtureDesignPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.IntConsumer;

/**
 * The fixture designer: a list of parts on the left, the design turning in the middle, the
 * selected part's numbers on the right, and the fixture's mount and name along the bottom.
 * Drag the preview to turn it, scroll to zoom. Done saves the design to the designer; Build
 * saves it and turns a fixture blank into a fixture that carries it.
 */
public class FixtureDesignerScreen extends Screen {
    private static final int PANEL_W = 430, PANEL_H = 238, LIST_ROWS = 12, ROW_H = 10;
    private static final int BG = 0xE0202024, EDGE = 0xFF606068, TEXT = 0xFFE8E8E8, DIM = 0xFF909098, SEL = 0x60FFE040;
    private static final int[] SWATCHES = {0xF2F2EE, 0x3A3C40, 0xB8BCC2, 0xC88A3C, 0x8B5A2B, 0x2E7D6B, 0x2F6FD6, 0xC62828, 0xE0C020, 0x111111};

    private final InteractionHand hand;
    private final FixtureDesign design;
    private int selected = -1, scroll;
    private float yaw = 35, pitch = 22, zoom = 26;
    private int panelX, panelY;
    private int listX, listY, previewX, previewY, previewW, previewH, fieldsX;
    private boolean dragging;
    private EditBox nameField, colorField;

    public FixtureDesignerScreen(InteractionHand hand, FixtureDesign design) {
        super(Component.translatable("powergrid.gui.fixture.title"));
        this.hand = hand;
        this.design = design;
        if(!design.parts.isEmpty())
            selected = 0;
    }

    private FixturePart part() {
        return selected >= 0 && selected < design.parts.size() ? design.parts.get(selected) : null;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 8;
        listY = panelY + 22;
        previewX = panelX + 118;
        previewY = panelY + 22;
        previewW = 150;
        previewH = 176;
        fieldsX = panelX + 276;
        rebuild();
    }

    /** Lays every widget out again; called whenever the selection or the part list changes. */
    private void rebuild() {
        clearWidgets();
        int bx = listX, by = listY + LIST_ROWS * ROW_H + 4;
        String[] kinds = {"Box", "Cone", "Globe", "Chain", "Socket"};
        FixturePart.Kind[] kindValues = FixturePart.Kind.values();
        for(int i = 0; i < kinds.length; ++i) {
            var kind = kindValues[i];
            addRenderableWidget(Button.builder(Component.literal("+" + kinds[i]), b -> add(kind))
                    .bounds(bx + (i % 3) * 36, by + (i / 3) * 14, 34, 13).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Dup"), b -> duplicate()).bounds(bx, by + 30, 34, 13).build());
        addRenderableWidget(Button.builder(Component.literal("Del"), b -> delete()).bounds(bx + 36, by + 30, 34, 13).build());
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> move(-1)).bounds(bx + 72, by + 30, 16, 13).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> move(1)).bounds(bx + 90, by + 30, 16, 13).build());

        var p = part();
        int fx = fieldsX, fy = panelY + 32;
        if(p != null) {
            number(fx, fy, "X", p.x, v -> p.x = v);
            number(fx + 50, fy, "Y", p.y, v -> p.y = v);
            number(fx + 100, fy, "Z", p.z, v -> p.z = v);
            number(fx, fy + 26, "W", p.w, v -> p.w = v);
            number(fx + 50, fy + 26, "H", p.h, v -> p.h = v);
            number(fx + 100, fy + 26, "D", p.d, v -> p.d = v);
            addRenderableWidget(Button.builder(Component.literal("Face: " + p.face.getSerializedName()), b -> {
                p.face = Direction.values()[(p.face.ordinal() + 1) % 6];
                b.setMessage(Component.literal("Face: " + p.face.getSerializedName()));
            }).bounds(fx, fy + 52, 72, 13).build());
            addRenderableWidget(Button.builder(Component.literal(p.material.name()), b -> {
                p.material = FixturePart.Material.values()[(p.material.ordinal() + 1) % FixturePart.Material.values().length];
                b.setMessage(Component.literal(p.material.name()));
            }).bounds(fx + 76, fy + 52, 70, 13).build());
            if(p.kind == FixturePart.Kind.CONE)
                number(fx, fy + 70, "Taper %", p.taper, v -> p.taper = v);
            colorField = new EditBox(font, fx + 76, fy + 70, 70, 13, Component.literal("colour"));
            colorField.setMaxLength(6);
            colorField.setValue(String.format("%06X", p.color));
            colorField.setResponder(s -> {
                try {
                    if(s.length() == 6) {
                        p.color = Integer.parseInt(s, 16);
                        p.clamp();
                    }
                } catch(NumberFormatException ignored) {}
            });
            addRenderableWidget(colorField);
        }
        int my = panelY + PANEL_H - 24;
        addRenderableWidget(Button.builder(Component.literal("Mount: " + design.mount.getSerializedName()), b -> {
            design.mount = FixtureDesign.Mount.values()[(design.mount.ordinal() + 1) % 3];
            b.setMessage(Component.literal("Mount: " + design.mount.getSerializedName()));
        }).bounds(panelX + 8, my, 96, 14).build());
        nameField = new EditBox(font, panelX + 110, my, 120, 14, Component.literal("name"));
        nameField.setMaxLength(FixtureDesign.NAME_LENGTH);
        nameField.setValue(design.name);
        nameField.setResponder(s -> design.name = s);
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.fixture.build"), b -> {
            PacketDistributor.sendToServer(new FixtureBuildPayload(hand == InteractionHand.OFF_HAND, design.write()));
        }).bounds(panelX + PANEL_W - 160, my, 70, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("powergrid.gui.fixture.done"), b -> onClose())
                .bounds(panelX + PANEL_W - 84, my, 76, 14).build());
    }

    private void number(int x, int y, String label, int value, IntConsumer apply) {
        var box = new EditBox(font, x, y + 10, 44, 12, Component.literal(label));
        box.setMaxLength(3);
        box.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
        box.setValue(Integer.toString(value));
        box.setResponder(s -> {
            if(s.isEmpty())
                return;
            try {
                apply.accept(Integer.parseInt(s));
                var p = part();
                if(p != null)
                    p.clamp();
            } catch(NumberFormatException ignored) {}
        });
        addRenderableWidget(box);
        labels.add(new int[] {x, y});
        labelText.add(label);
    }

    private final java.util.List<int[]> labels = new java.util.ArrayList<>();
    private final java.util.List<String> labelText = new java.util.ArrayList<>();

    @Override
    protected void clearWidgets() {
        super.clearWidgets();
        labels.clear();
        labelText.clear();
    }

    // ---- part list edits ----

    private void add(FixturePart.Kind kind) {
        if(design.parts.size() >= FixtureDesign.MAX_PARTS)
            return;
        var p = new FixturePart();
        p.kind = kind;
        switch(kind) {
            case CHAIN -> { p.w = 2; p.d = 2; p.h = 12; p.x = 23; p.z = 23; p.y = 18; p.material = FixturePart.Material.METAL; p.color = 0x8E9298; }
            case SOCKET -> { p.w = 4; p.h = 4; p.d = 4; p.x = 22; p.z = 22; p.y = 14; p.color = 0x3A3C40; }
            case GLOBE -> { p.w = 10; p.h = 10; p.d = 10; p.x = 19; p.z = 19; p.y = 8; p.material = FixturePart.Material.GLASS; p.color = 0xF2F2EE; }
            case CONE -> { p.w = 14; p.h = 8; p.d = 14; p.x = 17; p.z = 17; p.y = 10; }
            default -> {}
        }
        if(kind == FixturePart.Kind.SOCKET && design.socketCount() >= FixtureDesign.MAX_SOCKETS)
            return;
        design.parts.add(p);
        selected = design.parts.size() - 1;
        rebuild();
    }

    private void duplicate() {
        var p = part();
        if(p == null || design.parts.size() >= FixtureDesign.MAX_PARTS)
            return;
        var c = p.copy();
        c.x = Math.min(FixturePart.CUBE - 1, c.x + 2);
        design.parts.add(selected + 1, c);
        selected += 1;
        rebuild();
    }

    private void delete() {
        if(part() == null)
            return;
        design.parts.remove(selected);
        selected = Math.min(selected, design.parts.size() - 1);
        rebuild();
    }

    private void move(int by) {
        var p = part();
        int to = selected + by;
        if(p == null || to < 0 || to >= design.parts.size())
            return;
        design.parts.remove(selected);
        design.parts.add(to, p);
        selected = to;
        rebuild();
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if(button == 0 && mx >= listX && mx < listX + 104 && my >= listY && my < listY + LIST_ROWS * ROW_H) {
            int row = (int) ((my - listY) / ROW_H) + scroll;
            if(row >= 0 && row < design.parts.size()) {
                selected = row;
                rebuild();
                return true;
            }
        }
        var p = part();
        if(button == 0 && p != null && my >= panelY + 120 && my < panelY + 132 && mx >= fieldsX && mx < fieldsX + SWATCHES.length * 14) {
            p.color = SWATCHES[(int) ((mx - fieldsX) / 14)];
            if(colorField != null)
                colorField.setValue(String.format("%06X", p.color));
            return true;
        }
        if(mx >= previewX && mx < previewX + previewW && my >= previewY && my < previewY + previewH) {
            dragging = true;
            setFocused(null);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if(dragging) {
            yaw += (float) dx * 1.5f;
            pitch = Math.max(-89, Math.min(89, pitch + (float) dy * 1.5f));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if(mx >= previewX && mx < previewX + previewW && my >= previewY && my < previewY + previewH) {
            zoom = Math.max(10, Math.min(60, zoom + (float) sy * 3));
            return true;
        }
        if(mx >= listX && mx < listX + 104 && my >= listY && my < listY + LIST_ROWS * ROW_H) {
            scroll = Math.max(0, Math.min(Math.max(0, design.parts.size() - LIST_ROWS), scroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if((keyCode == 257 || keyCode == 335) && getFocused() instanceof EditBox) {
            setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new FixtureDesignPayload(hand == InteractionHand.OFF_HAND, design.write()));
        super.onClose();
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG);
        graphics.renderOutline(panelX, panelY, PANEL_W, PANEL_H, EDGE);
        graphics.drawString(font, title, panelX + 8, panelY + 7, TEXT);
        graphics.drawString(font, Component.translatable("powergrid.gui.fixture.parts", design.parts.size(), FixtureDesign.MAX_PARTS), panelX + 118, panelY + 7, DIM, false);
        // The part list.
        graphics.fill(listX - 2, listY - 2, listX + 104, listY + LIST_ROWS * ROW_H + 2, 0x40000000);
        for(int i = 0; i < LIST_ROWS; ++i) {
            int idx = i + scroll;
            if(idx >= design.parts.size())
                break;
            var p = design.parts.get(idx);
            int y = listY + i * ROW_H;
            if(idx == selected)
                graphics.fill(listX - 1, y - 1, listX + 103, y + ROW_H - 1, SEL);
            graphics.fill(listX, y, listX + 7, y + 7, 0xFF000000 | p.color);
            graphics.drawString(font, (idx + 1) + " " + p.kind.name().charAt(0) + p.kind.name().substring(1).toLowerCase() + " " + p.w + "x" + p.h + "x" + p.d, listX + 10, y, TEXT, false);
        }
        // The fields' labels and the swatches.
        for(int i = 0; i < labels.size(); ++i)
            graphics.drawString(font, labelText.get(i), labels.get(i)[0], labels.get(i)[1], DIM, false);
        var p = part();
        if(p != null) {
            graphics.drawString(font, Component.translatable("powergrid.gui.fixture.colour"), fieldsX, panelY + 110, DIM, false);
            for(int i = 0; i < SWATCHES.length; ++i) {
                int x = fieldsX + i * 14;
                graphics.fill(x, panelY + 120, x + 12, panelY + 132, 0xFF000000 | SWATCHES[i]);
                if(SWATCHES[i] == p.color)
                    graphics.renderOutline(x - 1, panelY + 119, 14, 14, 0xFFFFE040);
            }
            graphics.drawString(font, Component.translatable("powergrid.gui.fixture.hint"), fieldsX, panelY + 140, DIM, false);
        } else {
            graphics.drawString(font, Component.translatable("powergrid.gui.fixture.none"), fieldsX, panelY + 32, DIM, false);
        }
        // The preview.
        graphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF101014);
        graphics.renderOutline(previewX, previewY, previewW, previewH, EDGE);
        graphics.enableScissor(previewX, previewY, previewX + previewW, previewY + previewH);
        preview(graphics);
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void preview(GuiGraphics graphics) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(previewX + previewW / 2.0, previewY + previewH / 2.0 + 8, 300);
        pose.scale(zoom, -zoom, zoom);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-0.5, -0.5, -0.5);
        Lighting.setupForFlatItems();
        var buffers = graphics.bufferSource();
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        var bulbModel = BreakerPanelModels.get(BreakerPanelModels.BULB_ON);
        FixtureDraw.draw(pose, buffers, LightTexture.FULL_BRIGHT, design, true, (ms, buf, light, lit) ->
                renderer.renderModel(ms.last(), buf.getBuffer(RenderType.cutout()), null, bulbModel, 1, 1, 1, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout()), selected);
        // The anchor block, as a ghost, so the design can be placed round it.
        FixtureDraw.box(pose, buffers, LightTexture.FULL_BRIGHT, 16, 16, 16, 32, 32, 32, 0x80A0C0, 0.16f, FixturePart.Material.GLASS);
        buffers.endBatch();
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
