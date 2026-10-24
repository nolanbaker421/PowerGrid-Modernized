package com.nolanbaker.pgmodernized.radio;

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
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A SIM for a computer: in an OpenComputers card slot or on a CC: Tweaked pocket computer it makes
 * the machine a cell client of a carrier, joining the nearest sector that covers it as an omni
 * would. The card carries the carrier ID and the mode. Right-click a radio with it to take that
 * radio's link ID as the carrier; sneak-click the air to switch between relay (network messages
 * only, the default) and bridge (the networks merge, components and peripherals cross).
 */
public class CellCardItem extends Item {
    private static final String CARRIER = "Carrier", RELAY = "Relay";

    public CellCardItem(Properties properties) {
        super(properties);
    }

    // ---- the card's data ----

    private static CompoundTag data(ItemStack stack) {
        var tag = stack.get(ModDataComponents.CELL_CARD.get());
        return tag == null ? new CompoundTag() : tag.copy();
    }

    public static String carrier(ItemStack stack) {
        var tag = stack.get(ModDataComponents.CELL_CARD.get());
        return tag == null ? "" : tag.getString(CARRIER);
    }

    /** Relay unless the card says otherwise: a tablet should not see every component on a tower network by default. */
    public static boolean relay(ItemStack stack) {
        var tag = stack.get(ModDataComponents.CELL_CARD.get());
        return tag == null || !tag.contains(RELAY) || tag.getBoolean(RELAY);
    }

    public static void setCarrier(ItemStack stack, String carrier) {
        var tag = data(stack);
        tag.putString(CARRIER, carrier == null ? "" : carrier.trim());
        stack.set(ModDataComponents.CELL_CARD.get(), tag);
    }

    public static void setRelay(ItemStack stack, boolean relay) {
        var tag = data(stack);
        tag.putBoolean(RELAY, relay);
        stack.set(ModDataComponents.CELL_CARD.get(), tag);
    }

    /** The card's data as a tag, for a pocket computer's upgrade data. */
    public static CompoundTag tag(String carrier, boolean relay) {
        var tag = new CompoundTag();
        tag.putString(CARRIER, carrier == null ? "" : carrier);
        tag.putBoolean(RELAY, relay);
        return tag;
    }

    // ---- use ----

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        if(!(level.getBlockEntity(context.getClickedPos()) instanceof RadioBlockEntity radio))
            return InteractionResult.PASS;
        if(!level.isClientSide) {
            setCarrier(context.getItemInHand(), radio.linkId());
            say(context.getPlayer(), "message.cell_card.carrier", ChatFormatting.GRAY, radio.linkId().isBlank() ? "-" : radio.linkId());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(!player.isShiftKeyDown())
            return super.use(level, player, hand);
        if(!level.isClientSide) {
            boolean relay = !relay(stack);
            setRelay(stack, relay);
            say(player, relay ? "message.cell_card.relay" : "message.cell_card.bridge", ChatFormatting.GRAY);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        var carrier = carrier(stack);
        tooltip.add(Lang.builder().translate("cell_card.carrier", carrier.isBlank() ? "-" : carrier).style(ChatFormatting.AQUA).component());
        tooltip.add(Lang.builder().translate(relay(stack) ? "cell_card.mode_relay" : "cell_card.mode_bridge").style(ChatFormatting.GRAY).component());
        tooltip.add(Lang.builder().translate("cell_card.tooltip").style(ChatFormatting.DARK_GRAY).component());
    }

    private static void say(@Nullable Player player, String key, ChatFormatting style, Object... args) {
        if(player != null)
            player.displayClientMessage(Lang.builder().translate(key, args).style(style).component(), true);
    }
}
