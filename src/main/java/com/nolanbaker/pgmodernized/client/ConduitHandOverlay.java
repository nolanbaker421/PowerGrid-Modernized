package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.conduit.ConduitBenderItem;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * With conduit or a bender in hand the goggle tooltip stays off: a panel's or a trough's block
 * of lines sits over the very spot the next click goes to, and laying conduit is aiming.
 */
public final class ConduitHandOverlay {
    private static final ResourceLocation GOGGLE_INFO = ResourceLocation.fromNamespaceAndPath("create", "goggle_info");

    private ConduitHandOverlay() {}

    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Pre event) {
        if(!event.getName().equals(GOGGLE_INFO))
            return;
        var player = Minecraft.getInstance().player;
        if(player == null)
            return;
        var main = player.getMainHandItem();
        var off = player.getOffhandItem();
        if(ConduitItem.isConduit(main) || main.getItem() instanceof ConduitBenderItem
                || ConduitItem.isConduit(off) || off.getItem() instanceof ConduitBenderItem)
            event.setCanceled(true);
    }
}
