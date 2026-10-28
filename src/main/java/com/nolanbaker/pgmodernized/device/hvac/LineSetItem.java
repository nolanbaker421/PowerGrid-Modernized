package com.nolanbaker.pgmodernized.device.hvac;

import com.nolanbaker.pgmodernized.conduit.ConduitItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A refrigerant line, laid exactly like conduit: click a line port on a condenser or an air
 * handler, click along the blocks and strut it should follow, finish on the matching port of the
 * other unit. It runs through the same tracer, bender and strut as conduit, but it is a tube of
 * refrigerant, not a raceway: no wire is pulled through it and no knockout takes it.
 */
public class LineSetItem extends ConduitItem {
    private final LineKind kind;

    public LineSetItem(Properties properties, LineKind kind) {
        super(properties, kind.drawnAs());
        this.kind = kind;
    }

    public LineKind kind() {
        return kind;
    }

    /** The line in that stack, or null for anything else, conduit included. */
    @Nullable
    public static LineKind kindOf(ItemStack stack) {
        return stack.getItem() instanceof LineSetItem line ? line.kind : null;
    }

    public static boolean isLine(ItemStack stack) {
        return stack.getItem() instanceof LineSetItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("hvac.line.tooltip." + kind.id()).style(ChatFormatting.GRAY).component());
    }
}
