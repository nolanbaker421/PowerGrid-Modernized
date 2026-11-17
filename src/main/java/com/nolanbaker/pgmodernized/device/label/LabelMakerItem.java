package com.nolanbaker.pgmodernized.device.label;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * The label maker: sneak-right-click to type the label it prints, then right-click anything that
 * takes a name tag, a breaker space, a panel door, a gear cell, a cabinet, a fire alarm device,
 * and it is labelled with that text, as many times as you like. Cleared in the same screen, it
 * prints a blank, which clears a label the way a plain tag does.
 */
public class LabelMakerItem extends Item {
    public static final int MAX_LENGTH = 32;
    private static final String TEXT = "Text";

    public LabelMakerItem(Properties properties) {
        super(properties);
    }

    /** Whether a label, even a blank one, has been typed into this maker. */
    public static boolean hasLabel(ItemStack stack) {
        return stack.getItem() instanceof LabelMakerItem && stack.has(ModDataComponents.LABEL_MAKER.get());
    }

    public static String label(ItemStack stack) {
        var tag = stack.get(ModDataComponents.LABEL_MAKER.get());
        return tag == null ? "" : tag.getString(TEXT);
    }

    public static void setLabel(ItemStack stack, String text) {
        var tag = new CompoundTag();
        tag.putString(TEXT, text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text);
        stack.set(ModDataComponents.LABEL_MAKER.get(), tag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(!player.isShiftKeyDown())
            return InteractionResultHolder.pass(stack);
        if(level.isClientSide)
            ClientHooks.openLabelMaker(hand, label(stack));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Sneaking on a block types the label; a plain click reaches here only when the block took none. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        if(player == null)
            return InteractionResult.PASS;
        if(player.isShiftKeyDown()) {
            if(level.isClientSide)
                ClientHooks.openLabelMaker(context.getHand(), label(context.getItemInHand()));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(!level.isClientSide) {
            String key = hasLabel(context.getItemInHand()) ? "message.label_maker.nothing_here" : "message.label_maker.no_label";
            player.displayClientMessage(Lang.translate(key).style(ChatFormatting.RED).component(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if(hasLabel(stack)) {
            var text = label(stack);
            tooltip.add(text.isEmpty()
                    ? Lang.builder().translate("label_maker.prints_blank").style(ChatFormatting.GRAY).component()
                    : Lang.builder().translate("label_maker.prints", text).style(ChatFormatting.YELLOW).component());
        }
        tooltip.add(Lang.builder().translate("label_maker.tooltip").style(ChatFormatting.GRAY).component());
    }
}
