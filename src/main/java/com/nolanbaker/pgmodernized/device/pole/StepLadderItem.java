package com.nolanbaker.pgmodernized.device.pole;

import com.nolanbaker.pgmodernized.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * An A-frame ladder of a fixed height, two to four blocks, stood with one click on the ground.
 * It faces the player: the rungs are on the near side, the back legs lean away. Climbed, picked
 * up and dropped like the extension ladder, which it shares its entity with.
 */
public class StepLadderItem extends Item {
    /** How far each foot stands out from under the apex, per block of height. */
    public static final double SPREAD = 0.28;

    private final int height;

    public StepLadderItem(Properties properties, int height) {
        super(properties);
        this.height = height;
    }

    public int height() {
        return height;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        var level = ctx.getLevel();
        var player = ctx.getPlayer();
        if(level.isClientSide || player == null)
            return InteractionResult.sidedSuccess(level.isClientSide);
        if(ctx.getClickedFace() != Direction.UP) {
            player.displayClientMessage(Lang.translate("message.ladder.need_ground").style(ChatFormatting.RED).component(), true);
            return InteractionResult.CONSUME;
        }
        var centre = ctx.getClickLocation();
        var toward = ctx.getHorizontalDirection().getOpposite();
        var out = Vec3.atLowerCornerOf(toward.getNormal()).scale(SPREAD * height);
        var ladder = LadderEntity.place(ModEntities.LADDER.get(), level, centre.add(out), centre.add(0, height, 0), toward, ctx.getClickedPos(), height);
        level.addFreshEntity(ladder);
        if(!player.isCreative())
            ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("step_ladder.tooltip").style(ChatFormatting.GRAY).component());
    }
}
