package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.conduit.ConduitBenderItem;
import com.nolanbaker.pgmodernized.network.packets.BenderStandoffPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.patryk3211.powergrid.utility.Lang;

/**
 * With a leg grabbed in the bender, the scroll wheel sets how far off the face the leg will land,
 * a pixel a notch, instead of changing the hotbar slot. The green outline follows at once; the
 * server is told so the tap lands the same.
 */
public final class BenderScroll {
    private BenderScroll() {}

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if(player == null || mc.screen != null)
            return;
        var stack = player.getMainHandItem();
        if(!(stack.getItem() instanceof ConduitBenderItem) || !ConduitBenderItem.holding(stack))
            return;
        event.setCanceled(true);
        int standoff = ConduitBenderItem.standoff(stack) + (event.getScrollDeltaY() > 0 ? 1 : -1);
        standoff = Math.max(0, Math.min(ConduitBenderItem.MAX_STANDOFF, standoff));
        ConduitBenderItem.setStandoff(stack, standoff);
        PacketDistributor.sendToServer(new BenderStandoffPayload(standoff));
        player.displayClientMessage(Lang.builder().translate("message.conduit.bend_standoff", standoff).style(ChatFormatting.GRAY).component(), true);
    }
}
