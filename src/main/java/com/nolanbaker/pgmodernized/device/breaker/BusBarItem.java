package com.nolanbaker.pgmodernized.device.breaker;

import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.patryk3211.powergrid.electricity.wire.WireItem;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The copper bus bar: a crafting part, and the wire type of the hidden links between switchgear
 * sections (wire_types/bus_bar.json). It registers as a Power Grid wire so those links can exist,
 * but a player cannot string it between terminals; that is what the sections do by themselves.
 */
public class BusBarItem extends WireItem {
    public BusBarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if(player != null && !context.getLevel().isClientSide)
            player.displayClientMessage(Lang.builder().translate("message.bus_bar.not_a_wire").style(ChatFormatting.RED).component(), true);
        return InteractionResult.FAIL;
    }
}
