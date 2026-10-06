package com.nolanbaker.pgmodernized.device.helm;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A handheld radio remote: pair it by right-clicking a radio base, then right-click with it
 * anywhere in that dimension to put your keys on the base, as if you stood at a helm. The base
 * has to be loaded, which on a vehicle it is while anyone is near it. ~ or putting the remote
 * away lets go.
 */
public class RadioRemoteItem extends Item {
    public RadioRemoteItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** The base this remote is paired to, or null. */
    @Nullable
    public static BlockPos baseOf(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("Base") ? BlockPos.of(tag.getLong("Base")) : null;
    }

    @Nullable
    public static ResourceLocation dimensionOf(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("Dim") ? ResourceLocation.tryParse(tag.getString("Dim")) : null;
    }

    /** Whether the stack is a remote paired to that base in that level. */
    public static boolean pairedTo(ItemStack stack, @Nullable Level level, BlockPos base) {
        if(level == null || !(stack.getItem() instanceof RadioRemoteItem) || !base.equals(baseOf(stack)))
            return false;
        var dim = dimensionOf(stack);
        return dim != null && dim.equals(level.dimension().location());
    }

    private static void pair(ItemStack stack, Level level, BlockPos base) {
        var tag = new CompoundTag();
        tag.putLong("Base", base.asLong());
        tag.putString("Dim", level.dimension().location().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** On a radio base: pair to it. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if(!(level.getBlockEntity(pos) instanceof RadioBaseBlockEntity))
            return InteractionResult.PASS;
        if(!level.isClientSide) {
            pair(context.getItemInHand(), level, pos);
            if(context.getPlayer() != null)
                context.getPlayer().displayClientMessage(Lang.builder().translate("message.radio.paired", pos.getX(), pos.getY(), pos.getZ()).style(ChatFormatting.GREEN).component(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** In the air: take the paired base. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        var base = baseOf(stack);
        if(base == null) {
            if(!level.isClientSide)
                player.displayClientMessage(Lang.builder().translate("message.radio.unpaired").style(ChatFormatting.RED).component(), true);
            return InteractionResultHolder.fail(stack);
        }
        if(level.isClientSide)
            return InteractionResultHolder.success(stack);
        if(!pairedTo(stack, level, base)) {
            player.displayClientMessage(Lang.builder().translate("message.radio.wrong_dimension").style(ChatFormatting.RED).component(), true);
            return InteractionResultHolder.fail(stack);
        }
        if(!level.isLoaded(base) || !(level.getBlockEntity(base) instanceof RadioBaseBlockEntity radio)) {
            player.displayClientMessage(Lang.builder().translate("message.radio.no_base").style(ChatFormatting.RED).component(), true);
            return InteractionResultHolder.fail(stack);
        }
        if(player instanceof ServerPlayer serverPlayer)
            radio.take(serverPlayer);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var base = baseOf(stack);
        if(base == null)
            tooltip.add(Component.translatable("powergrid.gui.radio.tooltip_unpaired").withStyle(ChatFormatting.GRAY));
        else
            tooltip.add(Component.translatable("powergrid.gui.radio.tooltip_paired", base.getX(), base.getY(), base.getZ()).withStyle(ChatFormatting.AQUA));
    }
}
