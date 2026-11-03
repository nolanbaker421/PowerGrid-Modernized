package com.nolanbaker.pgmodernized.mixin;

import com.nolanbaker.pgmodernized.device.pole.LadderEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sneaking never dismounts a ladder: a climber sneak-clicks the gear they climbed up to, so the
 * ladder makes jumping the way to let go instead. Vanilla decides the sneak dismount in
 * {@code Player.wantsToStopRiding}, which nothing else can override.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void pgm$stayOnLadder(CallbackInfoReturnable<Boolean> cir) {
        if(((Entity) (Object) this).getVehicle() instanceof LadderEntity)
            cir.setReturnValue(false);
    }
}
