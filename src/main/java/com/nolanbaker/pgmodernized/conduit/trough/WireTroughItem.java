package com.nolanbaker.pgmodernized.conduit.trough;

import com.nolanbaker.pgmodernized.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

/**
 * One item for the whole row. With a trough row ending at the viewer's left of the spot, it places
 * an extension that joins the row; anywhere else it places a new head. A row only grows to the
 * right, so a trough is never placed on the left of a head.
 */
public class WireTroughItem extends BlockItem {
    public WireTroughItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        // A row ending just to the left, in its own facing: join it.
        for(var facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var left = pos.relative(WireTroughBlock.rowDirection(facing).getOpposite());
            if(!WireTroughBlock.isSection(level, left, facing))
                continue;
            var headPos = WireTroughBlock.headPos(level, left);
            if(headPos == null)
                return refuse(context, "message.trough.orphan");
            int sections = WireTroughBlock.sectionsOf(level.getBlockState(headPos));
            if(pos.distManhattan(headPos) != sections)
                return refuse(context, "message.trough.row_end");
            if(sections >= TroughGeometry.MAX_SECTIONS)
                return refuse(context, "message.trough.full");
            var state = ModBlocks.WIRE_TROUGH_EXTENSION.getDefaultState().setValue(WireTroughExtensionBlock.FACING, facing);
            return state.canSurvive(level, pos) ? state : null;
        }
        var head = super.getPlacementState(context);
        if(head == null)
            return null;
        // Nothing may be put on the left of a head: the row's numbering starts there.
        var facing = WireTroughBlock.facing(head);
        if(WireTroughBlock.isSection(level, pos.relative(WireTroughBlock.rowDirection(facing)), facing))
            return refuse(context, "message.trough.grow_right");
        return head;
    }

    @Nullable
    private static BlockState refuse(BlockPlaceContext context, String key) {
        var player = context.getPlayer();
        if(player != null && !context.getLevel().isClientSide)
            player.displayClientMessage(Lang.builder().translate(key).style(ChatFormatting.RED).component(), true);
        return null;
    }
}
