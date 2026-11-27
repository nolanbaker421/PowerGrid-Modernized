package com.nolanbaker.pgmodernized.device.fixture;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import net.minecraft.ChatFormatting;
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
 * The fixture designer: holds one design. Sneak-right-click to open it on the design, build
 * fixture items from it there, and sneak-right-click a placed fixture to copy its design onto
 * the designer.
 */
public class FixtureDesignerItem extends Item {
    public FixtureDesignerItem(Properties properties) {
        super(properties);
    }

    /** The designer's design, a fresh pendant when it has none yet. */
    public static FixtureDesign design(ItemStack stack) {
        var design = FixtureDesign.of(stack);
        return design != null ? design : FixtureDesign.pendant();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(!player.isShiftKeyDown())
            return InteractionResultHolder.pass(stack);
        if(level.isClientSide)
            ClientHooks.openFixtureDesigner(hand, design(stack));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        if(player == null || !player.isShiftKeyDown())
            return InteractionResult.PASS;
        if(level.getBlockEntity(context.getClickedPos()) instanceof CustomFixtureBlockEntity fixture) {
            if(!level.isClientSide) {
                fixture.design().putOn(context.getItemInHand());
                player.displayClientMessage(Lang.translate("message.fixture.copied").style(ChatFormatting.GRAY).component(), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(level.isClientSide)
            ClientHooks.openFixtureDesigner(context.getHand(), design(context.getItemInHand()));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        var design = FixtureDesign.of(stack);
        if(design != null)
            tooltip.add(Lang.builder().translate("fixture.tooltip.holding", design.name.isEmpty() ? "?" : design.name, design.parts.size()).style(ChatFormatting.YELLOW).component());
        tooltip.add(Lang.builder().translate("fixture.tooltip.designer").style(ChatFormatting.GRAY).component());
    }
}
