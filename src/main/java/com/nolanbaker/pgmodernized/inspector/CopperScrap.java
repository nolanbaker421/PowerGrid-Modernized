package com.nolanbaker.pgmodernized.inspector;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.patryk3211.powergrid.utility.Lang;

/** Offcuts: pulling wire or making a splice now and then leaves a bit of copper in your pocket. */
public final class CopperScrap {
    private CopperScrap() {}

    public static void roll(Player player, BlockPos where) {
        if(!(player instanceof ServerPlayer server) || player.isCreative())
            return;
        Inspections.noteWork(server, where);
        if(player.getRandom().nextDouble() >= PgmConfig.SCRAP_CHANCE.get())
            return;
        var scrap = new ItemStack(ModItems.COPPER_SCRAP.get());
        if(!player.getInventory().add(scrap))
            player.drop(scrap, false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.4f);
        server.displayClientMessage(Lang.builder().translate("message.copper_scrap").style(ChatFormatting.GOLD).component(), true);
    }
}
