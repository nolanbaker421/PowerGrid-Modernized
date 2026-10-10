package com.nolanbaker.pgmodernized.conduit;

import com.nolanbaker.pgmodernized.registry.ModBlocks;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.patryk3211.powergrid.collections.ModdedBlocks;

/**
 * Lets a Power Grid ceiling tile take a conduit box the way it takes a wire connector: right-click
 * the tile with the box and the tile becomes a tile with the box on top. The tile's own click
 * handler only knows Power Grid's attachments, so this runs ahead of it.
 */
public final class CeilingTileHook {
    private CeilingTileHook() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        var pos = event.getPos();
        var stack = event.getItemStack();
        if(!ModdedBlocks.CEILING_TILE.has(level.getBlockState(pos)) || !ModBlocks.CONDUIT_BOX.isIn(stack))
            return;
        var player = event.getEntity();
        if(player.isShiftKeyDown() || !player.mayBuild())
            return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if(level.isClientSide)
            return;
        if(!player.isCreative())
            stack.shrink(1);
        level.setBlockAndUpdate(pos, ModBlocks.CEILING_TILE_CONDUIT_BOX.getDefaultState());
        level.playSound(null, pos, ModBlocks.CONDUIT_BOX.getDefaultState().getSoundType().getPlaceSound(),
                SoundSource.BLOCKS, 1.0f, level.random.nextFloat() * 0.25f + 1.0f);
    }
}
