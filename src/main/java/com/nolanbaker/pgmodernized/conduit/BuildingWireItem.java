package com.nolanbaker.pgmodernized.conduit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.patryk3211.powergrid.electricity.wire.WireItem;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * THHN building wire in one gauge: an ordinary Power Grid wire item (its electrical numbers live in
 * wire_types/wire_&lt;gauge&gt;.json) that also knows its conductor area, so a conduit can be filled
 * by the book. It hangs like any other wire and pulls through conduit like any other wire.
 */
public class BuildingWireItem extends WireItem {
    private final WireGauge gauge;

    public BuildingWireItem(Properties properties, WireGauge gauge) {
        super(properties);
        this.gauge = gauge;
    }

    public WireGauge gauge() {
        return gauge;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("gui.wire.gauge", gauge.label(), String.format("%.0f", gauge.ampacity()),
                String.format("%.4f", gauge.areaSqIn()), String.format("%.3g", gauge.ohmsPerKm())).style(ChatFormatting.GRAY).component());
    }
}
