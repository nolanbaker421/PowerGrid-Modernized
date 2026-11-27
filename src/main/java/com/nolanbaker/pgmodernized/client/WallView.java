package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Whether the goggles show what is inside the walls: concealed runs and chaseway sleeves. On by
 * default while goggles are worn; a key turns the view off and on, since someone who lives in
 * goggles does not want blue tubes in every wall all day. Game bus; the key itself is registered
 * by {@link WallViewKeys} on the mod bus.
 */
public final class WallView {
    public static final KeyMapping TOGGLE = new KeyMapping("key.powergrid_modernized.wall_view", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.powergrid_modernized");
    private static boolean shown = true;

    private WallView() {}

    /** Whether this player sees inside the walls right now: goggles on, and the view not turned off. */
    public static boolean visible(@Nullable Player player) {
        return shown && player != null && GogglesItem.isWearingGoggles(player);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        while(TOGGLE.consumeClick()) {
            shown = !shown;
            if(mc.player != null)
                mc.player.displayClientMessage(Component.translatable(shown ? "powergrid.message.wall_view.shown" : "powergrid.message.wall_view.hidden").withStyle(ChatFormatting.GRAY), true);
        }
    }
}
