package com.nolanbaker.pgmodernized.device.fixture;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
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
                .setValue(CustomFixtureBlock.LIGHT, 0);
        return state.canSurvive(ctx.getLevel(), ctx.getClickedPos()) && ctx.getLevel().isUnobstructed(state, ctx.getClickedPos(), net.minecraft.world.phys.shapes.CollisionContext.empty()) ? state : null;
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
    }
}
