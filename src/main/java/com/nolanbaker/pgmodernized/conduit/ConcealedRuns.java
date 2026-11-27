package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.conduit.splice.ISpliceHost;
import com.nolanbaker.pgmodernized.util.ShockDamage;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.patryk3211.powergrid.collections.ModdedTags;
import org.patryk3211.powergrid.utility.Lang;

/**
 * Working a concealed run from a fitting it ends on, since the run itself is inside the wall and
 * cannot be clicked. Wire cutters on any fitting with a concealed run on a knockout: the last
 * wire out of it, or when it is empty, the run itself; sneaking takes the run out at once, wires
 * and all. Handled on the click event, which fires whether or not the player sneaks; a sneaking
 * player with an item never reaches a block's own click handling.
 */
public final class ConcealedRuns {
    private ConcealedRuns() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        if(!stack.is(ModdedTags.Item.WIRE_CUTTERS.tag) && !stack.is(ModdedTags.Item.BAD_WIRE_CUTTERS.tag))
            return;
        var level = event.getLevel();
        var pos = event.getPos();
        if(!(level.getBlockEntity(pos) instanceof ISpliceHost host))
            return;
        ConduitRunEntity withWire = null, empty = null;
        var state = level.getBlockState(pos);
        if(state.getBlock() instanceof com.nolanbaker.pgmodernized.conduit.gang.GangBoxBlock box && event.getHitVec() != null) {
            // On a gang box the click says which run: the one on the knockout nearest it across the plate.
            var local = event.getHitVec().getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            int hub = box.hubNearest(state, local, h -> host.hubRun(h) != null && host.hubRun(h).isConcealed());
            if(hub >= 0) {
                var run = host.hubRun(hub);
                if(run.conductors().isEmpty())
                    empty = run;
                else
                    withWire = run;
            }
        }
        for(int h = 0; withWire == null && empty == null && h < host.hubCount(); ++h) {
            var run = host.hubRun(h);
            if(run == null || !run.isConcealed())
                continue;
            if(!run.conductors().isEmpty()) {
                if(withWire == null)
                    withWire = run;
            } else if(empty == null) {
                empty = run;
            }
        }
        if(withWire == null && empty == null)
            return;   // nothing concealed here: the click goes on as usual
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if(level.isClientSide)
            return;
        var player = event.getEntity();
        if(!player.isShiftKeyDown() && withWire != null) {
            withWire.pullOut(player);
            return;
        }
        var run = withWire != null ? withWire : empty;
        ShockDamage.shockAmps(player, run.maxAmps());
        run.kill();
        player.displayClientMessage(Lang.translate("message.gang.run_removed").style(ChatFormatting.GRAY).component(), true);
    }
}
