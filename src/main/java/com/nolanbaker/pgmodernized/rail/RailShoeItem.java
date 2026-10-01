package com.nolanbaker.pgmodernized.rail;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import org.patryk3211.powergrid.electricity.wire.WireItem;

/**
 * A collector shoe: a crafting part for the collector, and the wire type (wire_types/rail_shoe.json)
 * the hidden pickup wires carry. It never acts as a wire in the hand.
 */
public class RailShoeItem extends WireItem {
    public RailShoeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.FAIL;
    }
}
