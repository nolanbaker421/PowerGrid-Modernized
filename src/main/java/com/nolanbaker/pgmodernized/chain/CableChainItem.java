package com.nolanbaker.pgmodernized.chain;

import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.wire.WireItem;
import org.patryk3211.powergrid.utility.Lang;

/**
 * The cable chain. A Power Grid wire item so the chain entities can carry it, but it never acts as
 * a wire itself: {@link com.nolanbaker.pgmodernized.util.WireGuard} takes its clicks away from Power
 * Grid's handler and sends them to {@link CableChainPlacement}. One item is {@link #METERS_PER_ITEM}
 * metres of chain.
 */
public class CableChainItem extends WireItem {
    public static final float METERS_PER_ITEM = 2f;
    private static final String LEVEL_KEY = "PgmLevel";

    public CableChainItem(Properties properties) {
        super(properties);
    }

    public static boolean isChain(ItemStack stack) {
        return stack.getItem() instanceof CableChainItem;
    }

    // ---- the pending first anchor, kept on the stack between the two clicks ----

    public static void setPending(ItemStack stack, BlockPos anchor, Level level) {
        var tag = new CompoundTag();
        tag.put("Pos", NbtUtils.writeBlockPos(anchor));
        tag.putString(LEVEL_KEY, level.dimension().location() + "#" + level.getClass().getName());
        stack.set(ModDataComponents.CABLE_CHAIN_CONNECTION.get(), tag);
    }

    @Nullable
    public static BlockPos pending(ItemStack stack, Level level) {
        CompoundTag tag = stack.get(ModDataComponents.CABLE_CHAIN_CONNECTION.get());
        if(tag == null)
            return null;
        if(!tag.getString(LEVEL_KEY).equals(level.dimension().location() + "#" + level.getClass().getName())) {
            clearPending(stack);
            return null;
        }
        return NbtUtils.readBlockPos(tag, "Pos").orElse(null);
    }

    public static boolean hasPending(ItemStack stack) {
        return stack.has(ModDataComponents.CABLE_CHAIN_CONNECTION.get());
    }

    public static void clearPending(ItemStack stack) {
        stack.remove(ModDataComponents.CABLE_CHAIN_CONNECTION.get());
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
}
