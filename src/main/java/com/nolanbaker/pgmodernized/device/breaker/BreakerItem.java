package com.nolanbaker.pgmodernized.device.breaker;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A plug-on breaker of one frame size, or a blank filler plate. Neither does anything on its own;
 * right-click a panel space with it to install it, then set the trip rating with a wrench. A two-
 * or three-pole breaker takes that many poles' worth of rows in one column, one pole per lug,
 * under a single handle that trips all poles together.
 */
public class BreakerItem extends Item {
    public static final int[] MULTI_POLES = {2, 3};

    @Nullable
    private final BreakerFrame frame;
    private final int poles;

    /** A blank. */
    public BreakerItem(Properties properties) {
        this(properties, null, 1);
    }

    public BreakerItem(Properties properties, @Nullable BreakerFrame frame, int poles) {
        super(properties);
        this.frame = frame;
        this.poles = poles;
    }

    /** The frame, or null for a blank. */
    @Nullable
    public BreakerFrame frame() {
        return frame;
    }

    /** The frame's rating in amperes; 0 for a blank. */
    public int rating() {
        return frame == null ? 0 : frame.max();
    }

    /** Poles the breaker has. A blank is always one. */
    public int poles() {
        return poles;
    }

    public boolean isBlank() {
        return frame == null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if(frame == null) {
            tooltip.add(Lang.builder().translate("breaker.blank").style(ChatFormatting.GRAY).component());
            return;
        }
        tooltip.add(Lang.builder().translate("breaker.frame", frame.min(), frame.max()).style(ChatFormatting.GRAY).component());
        if(poles > 1)
            tooltip.add(Lang.builder().translate("breaker.poles", poles, poles * frame.rows()).style(ChatFormatting.GRAY).component());
        else if(frame.rows() > 1)
            tooltip.add(Lang.builder().translate("breaker.rows", frame.rows()).style(ChatFormatting.GRAY).component());
    }
}
