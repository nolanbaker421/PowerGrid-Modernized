package com.nolanbaker.pgmodernized.util;

import com.nolanbaker.pgmodernized.rail.RailShoeItem;
import com.nolanbaker.pgmodernized.chain.CableChainAnchorBlockEntity;
import com.nolanbaker.pgmodernized.chain.CableChainPlacement;
import com.nolanbaker.pgmodernized.chain.CableChainItem;
import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.device.breaker.BusBarItem;
import com.nolanbaker.pgmodernized.network.Cat6CableItem;
import com.nolanbaker.pgmodernized.network.INetworkJack;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.utility.Lang;

/**
 * Power Grid offers every registered wire item to every electrical block, so without this a
 * conduit or a Cat6 cable would land on a wire connector or a power node as if it were a plain
 * wire, and the bus bar could be strung between terminals. Clicks of those items on electrical
 * blocks that have no knockout or jack for them are stopped here, before the block sees them.
 */
public final class WireGuard {
    private WireGuard() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var item = event.getItemStack().getItem();
        if(item instanceof CableChainItem) {
            // Never a wire: on an anchor both of its clicks go to the chain placement; on any other
            // electrical block it is refused, so Power Grid's handler never strings it as a conductor.
            if(event.getLevel().getBlockEntity(event.getPos()) instanceof CableChainAnchorBlockEntity) {
                event.setCanceled(true);
                event.setCancellationResult(CableChainPlacement.click(event.getLevel(), event.getPos(), event.getEntity(), event.getItemStack()));
            } else if(IElectric.getAt(event.getLevel(), event.getPos()) != null) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if(!event.getLevel().isClientSide)
                    event.getEntity().displayClientMessage(Lang.builder().translate("message.cable_chain.anchors_only").style(ChatFormatting.RED).component(), true);
            }
            return;
        }
        boolean conduit = item instanceof ConduitItem;
        boolean cat6 = item instanceof Cat6CableItem;
        boolean busBar = item instanceof BusBarItem || item instanceof RailShoeItem;
        if(!conduit && !cat6 && !busBar)
            return;
        var level = event.getLevel();
        var pos = event.getPos();
        if(IElectric.getAt(level, pos) == null)
            return;
        var be = level.getBlockEntity(pos);
        String message;
        if(conduit) {
            if(be instanceof ISpliceHost)
                return;
            message = "message.conduit.needs_hub";
        } else if(cat6) {
            if(be instanceof INetworkJack)
                return;
            message = "message.cat6.needs_jack";
        } else {
            message = "message.bus_bar.not_a_wire";
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if(!level.isClientSide)
            event.getEntity().displayClientMessage(Lang.builder().translate(message).style(ChatFormatting.RED).component(), true);
    }
}
