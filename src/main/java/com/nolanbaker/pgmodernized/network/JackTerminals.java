package com.nolanbaker.pgmodernized.network;

import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.utility.Lang;

import java.util.function.BiFunction;

/** Helpers for device blocks that carry a built-in network jack as one of their terminals. */
public final class JackTerminals {
    public static final int COLOR = 0x2FBFBF;

    private JackTerminals() {}

    /** A jack terminal box, in the block's local 16ths like the other terminals. */
    public static TerminalBoundingBox jack(double x1, double y1, double z1, double x2, double y2, double z2) {
        var name = Lang.builder().translate("generic.network_jack").style(ChatFormatting.AQUA).component();
        return new TerminalBoundingBox(name, x1, y1, z1, x2, y2, z2).withColor(COLOR);
    }

    /**
     * Routes a wire click: a Cat6 cable must land on the jack, ordinary wires must not.
     * Everything else goes to the block's regular wire handling.
     */
    public static InteractionResult onWire(IElectric block, int jackIndex, BlockState state, UseOnContext context,
                                           BiFunction<BlockState, UseOnContext, InteractionResult> fallback) {
        var pos = context.getClickedPos();
        var local = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        int terminal = block.terminalIndexAt(state, local);
        boolean cat6 = Cat6CableItem.isCat6(context.getItemInHand());
        if(terminal == jackIndex) {
            if(!cat6) {
                IElectric.sendMessage(context, Lang.translate("message.cat6_only").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return Cat6Placement.click(context, new JackEndpoint(pos));
        }
        if(cat6) {
            if(terminal >= 0) {
                IElectric.sendMessage(context, Lang.translate("message.cat6_needs_jack").style(ChatFormatting.RED).component());
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }
        return fallback.apply(state, context);
    }
}
