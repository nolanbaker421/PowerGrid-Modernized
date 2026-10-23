package com.nolanbaker.pgmodernized.radio;

import com.nolanbaker.pgmodernized.client.ClientHooks;
import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
 * The installer's boresight scope. Clip it on one antenna (click it), walk to the other and clip it
 * on that one: both are sighted in on each other, azimuth and elevation to a tenth of a degree, as
 * two riggers with scopes and a radio would do it. Sneak-click a single dish and it is sighted in
 * on the nearest dish with its radio's link ID, and any antenna sneak-clicked has its coverage drawn
 * in the world for a minute ({@link com.nolanbaker.pgmodernized.client.CoverageSurvey}). Sneak-click
 * the air to put the scope away.
 */
public class AlignmentScopeItem extends Item {
    private static final String KEY = "ScopeFrom";

    public AlignmentScopeItem(Properties properties) {
        super(properties);
    }

    @Nullable
    private static BlockPos from(ItemStack stack) {
        var tag = stack.get(ModDataComponents.CAT6_CONNECTION.get());
        return tag != null && tag.contains(KEY) ? BlockPos.of(tag.getLong(KEY)) : null;
    }

    private static void setFrom(ItemStack stack, @Nullable BlockPos pos) {
        if(pos == null) {
            stack.remove(ModDataComponents.CAT6_CONNECTION.get());
            return;
        }
        var tag = new CompoundTag();
        tag.putLong(KEY, pos.asLong());
        stack.set(ModDataComponents.CAT6_CONNECTION.get(), tag);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var player = context.getPlayer();
        var stack = context.getItemInHand();
        if(!(level.getBlockEntity(pos) instanceof AntennaBlockEntity antenna))
            return InteractionResult.PASS;
        if(player != null && player.isShiftKeyDown()) {
            // Survey: the client draws this antenna's coverage for a minute, wherever the player goes.
            if(level.isClientSide) {
                ClientHooks.survey(pos);
                return InteractionResult.SUCCESS;
            }
            if(antenna.kind() == AntennaKind.DISH) {
                // And a dish is sighted in on its radio's nearest same-ID dish.
                var radio = antenna.radioEntity();
                var target = radio == null ? null : radio.nearest(false);
                if(target == null || target.antenna() == null) {
                    say(player, "message.scope.no_target", ChatFormatting.RED);
                    return InteractionResult.SUCCESS;
                }
                aim(antenna, target.antenna());
                say(player, "message.scope.sighted", ChatFormatting.GREEN, target.linkId(), degrees(antenna.azimuth()), degrees(antenna.elevation()));
                return InteractionResult.SUCCESS;
            }
            double reach = antenna.reach();
            if(reach <= 0)
                say(player, "message.scope.survey_down", ChatFormatting.YELLOW);
            else if(antenna.kind() == AntennaKind.SECTOR)
                say(player, "message.scope.survey_sector", ChatFormatting.GRAY, String.format("%.0f", reach), String.format("%.0f", antenna.beamwidth()), degrees(antenna.elevation()));
            else
                say(player, "message.scope.survey_omni", ChatFormatting.GRAY, String.format("%.0f", reach));
            return InteractionResult.SUCCESS;
        }
        if(!antenna.kind().aimed())
            return InteractionResult.PASS;
        var first = from(stack);
        if(first == null || first.equals(pos)) {
            if(!level.isClientSide) {
                setFrom(stack, pos);
                say(player, "message.scope.clipped", ChatFormatting.GRAY);
            }
            return InteractionResult.SUCCESS;
        }
        if(level.isClientSide)
            return InteractionResult.SUCCESS;
        if(!(level.getBlockEntity(first) instanceof AntennaBlockEntity other) || !other.kind().aimed()) {
            setFrom(stack, pos);
            say(player, "message.scope.lost", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        aim(antenna, other);
        aim(other, antenna);
        setFrom(stack, null);
        say(player, "message.scope.aligned", ChatFormatting.GREEN, degrees(antenna.azimuth()), degrees(antenna.elevation()),
                degrees(other.azimuth()), degrees(other.elevation()), String.format("%.0f", antenna.livePosition().distanceTo(other.livePosition())));
        return InteractionResult.SUCCESS;
    }

    /** Points one antenna at the other: a dish fully, a sector by azimuth with its downtilt left as set. */
    private static void aim(AntennaBlockEntity antenna, AntennaBlockEntity target) {
        var here = antenna.livePosition();
        var there = target.livePosition();
        double azimuth = RadioMath.bearing(here, there);
        double elevation = antenna.kind() == AntennaKind.DISH ? RadioMath.elevation(here, there) : antenna.elevation();
        antenna.setAim(azimuth, elevation);
    }

    private static String degrees(double value) {
        return String.format("%.1f", value);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(player.isShiftKeyDown() && level.isClientSide)
            ClientHooks.clearSurvey();
        if(player.isShiftKeyDown() && from(stack) != null) {
            setFrom(stack, null);
            if(!level.isClientSide)
                say(player, "message.scope.cleared", ChatFormatting.GRAY);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || from(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Lang.builder().translate("scope.tooltip").style(ChatFormatting.GRAY).component());
        var first = from(stack);
        if(first != null)
            tooltip.add(Lang.builder().translate("scope.clipped_at", first.getX(), first.getY(), first.getZ()).style(ChatFormatting.AQUA).component());
    }

    private static void say(@Nullable Player player, String key, ChatFormatting style, Object... args) {
        if(player != null)
            player.displayClientMessage(Lang.builder().translate(key, args).style(style).component(), true);
    }
}
