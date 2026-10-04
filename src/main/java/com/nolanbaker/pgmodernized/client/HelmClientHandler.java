package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nolanbaker.pgmodernized.device.helm.HelmBlockEntity;
import com.nolanbaker.pgmodernized.network.packets.HelmKeyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * While the player is at a helm, every key and mouse button goes to the helm instead of the game:
 * each press and release is sent by name, the vanilla binding it would have triggered is put back
 * to sleep before the game acts on it, and movement input is zeroed so the player stands still.
 * The view is untouched; the mouse still looks around. Escape, any screen opening, logging out or
 * the server letting go all end it.
 */
public final class HelmClientHandler {
    private static final String KEYBOARD = "key.keyboard.";
    @Nullable
    private static BlockPos active;

    private HelmClientHandler() {}

    public static boolean isActive() {
        return active != null;
    }

    /** From the server: at this helm now, or not any more. */
    public static void onState(BlockPos pos, boolean on) {
        if(on) {
            active = pos;
            var mc = Minecraft.getInstance();
            if(mc.gui != null)
                mc.gui.setOverlayMessage(Component.translatable("powergrid.gui.helm.active"), false);
        } else if(pos.equals(active)) {
            active = null;
        }
    }

    private static void letGo() {
        if(active == null)
            return;
        var pos = active;
        active = null;
        PacketDistributor.sendToServer(new HelmKeyPayload(pos, HelmBlockEntity.RELEASE, false));
    }

    /** The key's name as the controls screen shows it: w, space, left.shift, keypad.1, f3. */
    private static String keyName(int key, int scanCode) {
        var name = InputConstants.getKey(key, scanCode).getName();
        return name.startsWith(KEYBOARD) ? name.substring(KEYBOARD.length()) : name;
    }

    /** Un-press every vanilla binding this key just set, and eat the clicks it queued, before the game ticks. */
    private static void swallow(Minecraft mc, int key, int scanCode) {
        for(var mapping : mc.options.keyMappings) {
            if(!mapping.matches(key, scanCode))
                continue;
            mapping.setDown(false);
            while(mapping.consumeClick()) {}
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if(active == null)
            return;
        int action = event.getAction();
        if(event.getKey() == GLFW.GLFW_KEY_ESCAPE) {
            if(action == GLFW.GLFW_PRESS)
                letGo();
            return;
        }
        var mc = Minecraft.getInstance();
        if(mc.screen != null || action == GLFW.GLFW_REPEAT)
            return;
        swallow(mc, event.getKey(), event.getScanCode());
        PacketDistributor.sendToServer(new HelmKeyPayload(active, keyName(event.getKey(), event.getScanCode()), action == GLFW.GLFW_PRESS));
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if(active == null || Minecraft.getInstance().screen != null || event.getAction() == GLFW.GLFW_REPEAT)
            return;
        event.setCanceled(true);
        String name = switch(event.getButton()) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "mouse.left";
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "mouse.right";
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "mouse.middle";
            default -> "mouse." + event.getButton();
        };
        PacketDistributor.sendToServer(new HelmKeyPayload(active, name, event.getAction() == GLFW.GLFW_PRESS));
    }

    /** A scroll is a key that is pressed and released at once. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if(active == null || Minecraft.getInstance().screen != null)
            return;
        event.setCanceled(true);
        String name = event.getScrollDeltaY() > 0 ? "scroll.up" : "scroll.down";
        PacketDistributor.sendToServer(new HelmKeyPayload(active, name, true));
        PacketDistributor.sendToServer(new HelmKeyPayload(active, name, false));
    }

    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if(active == null)
            return;
        var input = event.getInput();
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if(active == null)
            return;
        var mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null) {
            active = null;
            return;
        }
        if(mc.screen != null)
            letGo();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = null;
    }
}
