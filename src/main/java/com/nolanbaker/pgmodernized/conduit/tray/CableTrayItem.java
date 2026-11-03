package com.nolanbaker.pgmodernized.conduit.tray;

import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEndpointType;
import org.patryk3211.powergrid.electricity.wire.WireItem;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * Cable tray, laid like conduit but free at both ends: click where it starts, click along the
 * blocks it follows, sneak-click the air to stop. Clicking an existing tray continues it from an
 * end or branches off its rails. A Power Grid wire item so the runs can carry it; it is never a
 * conductor, and {@link com.nolanbaker.pgmodernized.util.WireGuard} keeps Power Grid's handler
 * off it on electrical blocks.
 */
public class CableTrayItem extends WireItem {
    private static final String LEVEL_KEY = "PgmLevel";

    public CableTrayItem(Properties properties) {
        super(properties);
    }

    public static boolean isTray(ItemStack stack) {
        return stack.getItem() instanceof CableTrayItem;
    }

    // ---- the pending end, kept on the stack between clicks ----

    public static void setPending(ItemStack stack, IWireEndpoint endpoint, Level level) {
        var tag = endpoint.serialize();
        tag.putString(LEVEL_KEY, level.dimension().location().toString());
        stack.set(ModDataComponents.TRAY_CONNECTION.get(), tag);
    }

    @Nullable
    public static IWireEndpoint pending(ItemStack stack, Level level) {
        var tag = stack.get(ModDataComponents.TRAY_CONNECTION.get());
        if(tag == null)
            return null;
        if(!tag.getString(LEVEL_KEY).equals(level.dimension().location().toString())) {
            clearPending(stack);
            return null;
        }
        var endpoint = WireEndpointType.deserialize(tag);
        if(endpoint == null || !endpoint.isValid(level)) {
            clearPending(stack);
            return null;
        }
        return endpoint;
    }

    public static boolean hasPending(ItemStack stack) {
        return stack.has(ModDataComponents.TRAY_CONNECTION.get());
    }

    public static void clearPending(ItemStack stack) {
        stack.remove(ModDataComponents.TRAY_CONNECTION.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return TrayPlacement.groundClick(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(player.isShiftKeyDown() && hasPending(stack)) {
            clearPending(stack);
            if(!level.isClientSide)
                player.displayClientMessage(Lang.translate("message.connection_reset").style(ChatFormatting.GRAY).component(), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || hasPending(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("tray.tooltip").style(ChatFormatting.GRAY).component());
    }
}
