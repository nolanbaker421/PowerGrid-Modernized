package com.nolanbaker.pgmodernized.device.fixture;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import com.nolanbaker.pgmodernized.client.ClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A built fixture, design on board, placed like a block: a ceiling design under a ceiling, a wall
 * design on a wall facing out, a floor design on a floor. Named after its design.
 */
public class CustomFixtureItem extends BlockItem {
    public CustomFixtureItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected @Nullable BlockState getPlacementState(BlockPlaceContext ctx) {
        var design = FixtureDesign.of(ctx.getItemInHand());
        if(design == null)
            return null;
        var face = ctx.getClickedFace();
        Direction facing = design.mount == FixtureDesign.Mount.WALL && face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        var state = getBlock().defaultBlockState()
                .setValue(HorizontalElectricBlock.HORIZONTAL_FACING, facing)
                .setValue(CustomFixtureBlock.MOUNT, design.mount)
                .setValue(CustomFixtureBlock.BOX, design.box)
                .setValue(CustomFixtureBlock.LIGHT, 0);
        return state.canSurvive(ctx.getLevel(), ctx.getClickedPos()) && ctx.getLevel().isUnobstructed(state, ctx.getClickedPos(), net.minecraft.world.phys.shapes.CollisionContext.empty()) ? state : null;
    }

    /** Sneak-right-click, in the air or on a block, opens the fixture's own design in the designer instead of placing it. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(!player.isShiftKeyDown() || FixtureDesign.of(stack) == null)
            return InteractionResultHolder.pass(stack);
        if(level.isClientSide)
            ClientHooks.openFixtureDesigner(hand, FixtureDesign.of(stack));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if(player != null && player.isShiftKeyDown() && FixtureDesign.of(context.getItemInHand()) != null) {
            if(context.getLevel().isClientSide)
                ClientHooks.openFixtureDesigner(context.getHand(), FixtureDesign.of(context.getItemInHand()));
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }

    @Override
    public Component getName(ItemStack stack) {
        var design = FixtureDesign.of(stack);
        return design != null && !design.name.isEmpty() ? Component.literal(design.name) : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        var design = FixtureDesign.of(stack);
        if(design == null) {
            tooltip.add(Lang.builder().translate("fixture.tooltip.blank").style(ChatFormatting.RED).component());
            return;
        }
        tooltip.add(Lang.builder().translate("fixture.tooltip.mount." + design.mount.getSerializedName()).style(ChatFormatting.GRAY).component());
        tooltip.add(Lang.builder().translate("fixture.tooltip.parts", design.parts.size(), design.socketCount()).style(ChatFormatting.GRAY).component());
        tooltip.add(Lang.builder().translate("fixture.tooltip.edit").style(ChatFormatting.GRAY).component());
    }
}
