package com.nolanbaker.pgmodernized.radio;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlock;
import com.nolanbaker.pgmodernized.device.ctcabinet.CtCabinetBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A smart meter's radio module. Right-click a CT cabinet with it and the cabinet reports its
 * readings to the collectors of its utility from then on, with nothing to wire. Right-click the
 * fitted cabinet with another one to set its meter ID and utility; sneak-click to take the module
 * back out.
 */
public class MeterAntennaItem extends Item {
    public MeterAntennaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        var player = context.getPlayer();
        if(!(state.getBlock() instanceof CtCabinetBlock) || !(level.getBlockEntity(pos) instanceof CtCabinetBlockEntity cabinet))
            return InteractionResult.PASS;
        boolean fitted = state.getValue(CtCabinetBlock.ANTENNA);
        if(player != null && player.isShiftKeyDown()) {
            if(!fitted)
                return InteractionResult.PASS;
            if(!level.isClientSide) {
                level.setBlock(pos, state.setValue(CtCabinetBlock.ANTENNA, false), Block.UPDATE_ALL);
                cabinet.antennaRemoved();
                var back = new ItemStack(this);
                if(!player.getInventory().add(back))
                    player.drop(back, false);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
                say(player, "message.ct_cabinet.antenna_removed", ChatFormatting.GRAY);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(fitted) {
            if(level.isClientSide)
                ClientHooks.openSettings(pos, "powergrid.gui.meter_antenna.title");
            return InteractionResult.SUCCESS;
        }
        if(!level.isClientSide) {
            level.setBlock(pos, state.setValue(CtCabinetBlock.ANTENNA, true), Block.UPDATE_ALL);
            if(!player.isCreative())
                context.getItemInHand().shrink(1);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
            say(player, "message.ct_cabinet.antenna_fitted", ChatFormatting.GRAY);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void say(@Nullable Player player, String key, ChatFormatting style) {
        if(player != null)
            player.displayClientMessage(Lang.builder().translate(key).style(style).component(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("meter_antenna.tooltip").style(ChatFormatting.GRAY).component());
    }
}
