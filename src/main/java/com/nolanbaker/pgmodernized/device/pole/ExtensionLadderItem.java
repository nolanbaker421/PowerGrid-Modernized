package com.nolanbaker.pgmodernized.device.pole;

import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import com.nolanbaker.pgmodernized.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * The extension ladder, placed in two clicks: the ground where the feet go, then the pole or
 * wall face the top leans on. The top must be two to fifteen blocks up, on the open side of
 * that face, with the feet no further out than a quarter of the height, the lineman's one-in-four
 * rule. Sneak-click with the feet chosen to start over.
 */
public class ExtensionLadderItem extends Item {
    private static final String LEVEL_KEY = "PgmLevel";

    public ExtensionLadderItem(Properties properties) {
        super(properties);
    }

    // ---- the chosen feet, kept on the stack between the clicks ----

    private static void setFoot(ItemStack stack, Vec3 foot, Level level) {
        var tag = new CompoundTag();
        tag.putDouble("X", foot.x);
        tag.putDouble("Y", foot.y);
        tag.putDouble("Z", foot.z);
        tag.putString(LEVEL_KEY, level.dimension().location().toString());
        stack.set(ModDataComponents.LADDER_FOOT.get(), tag);
    }

    @Nullable
    private static Vec3 foot(ItemStack stack, Level level) {
        var tag = stack.get(ModDataComponents.LADDER_FOOT.get());
        if(tag == null)
            return null;
        if(!tag.getString(LEVEL_KEY).equals(level.dimension().location().toString())) {
            clearFoot(stack);
            return null;
        }
        return new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
    }

    private static void clearFoot(ItemStack stack) {
        stack.remove(ModDataComponents.LADDER_FOOT.get());
    }

    public static boolean hasFoot(ItemStack stack) {
        return stack.has(ModDataComponents.LADDER_FOOT.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        var level = ctx.getLevel();
        var player = ctx.getPlayer();
        if(level.isClientSide || player == null)
            return InteractionResult.sidedSuccess(level.isClientSide);
        var stack = ctx.getItemInHand();
        var face = ctx.getClickedFace();
        var hit = ctx.getClickLocation();
        var foot = foot(stack, level);
        if(foot == null) {
            if(face != Direction.UP)
                return tell(player, "ladder.need_ground", ChatFormatting.RED);
            setFoot(stack, hit, level);
            return tell(player, "ladder.foot_set", ChatFormatting.GREEN);
        }
        if(!face.getAxis().isHorizontal())
            return tell(player, "ladder.need_face", ChatFormatting.RED);
        double height = hit.y - foot.y;
        if(height < LadderEntity.MIN_HEIGHT)
            return tell(player, "ladder.too_low", ChatFormatting.RED);
        if(height > LadderEntity.MAX_HEIGHT)
            return tell(player, "ladder.too_tall", ChatFormatting.RED);
        var out = new Vec3(foot.x - hit.x, 0, foot.z - hit.z);
        var normal = Vec3.atLowerCornerOf(face.getNormal());
        if(out.dot(normal) < 0.05)
            return tell(player, "ladder.wrong_side", ChatFormatting.RED);
        if(out.length() > height / 4 + 0.3)
            return tell(player, "ladder.too_flat", ChatFormatting.RED);
        var ladder = LadderEntity.place(ModEntities.LADDER.get(), level, foot, hit, face, ctx.getClickedPos());
        level.addFreshEntity(ladder);
        clearFoot(stack);
        if(!player.isCreative())
            stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult tell(Player player, String key, ChatFormatting style) {
        player.displayClientMessage(Lang.translate("message." + key).style(style).component(), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(player.isShiftKeyDown() && hasFoot(stack)) {
            clearFoot(stack);
            if(!level.isClientSide)
                player.displayClientMessage(Lang.translate("message.connection_reset").style(ChatFormatting.GRAY).component(), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || hasFoot(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("ladder.tooltip").style(ChatFormatting.GRAY).component());
    }
}
