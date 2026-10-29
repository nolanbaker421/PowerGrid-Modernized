package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.registry.ModBlocks;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * A strut's outline is empty when asked without a context, for Power Grid's tracer, and
 * vanilla's hit particles take the bounds of that empty shape and crash. The strut spawns no hit
 * particles instead. Registered on the mod bus, client side only.
 */
public final class StrutHitEffects {
    private StrutHitEffects() {}

    @SubscribeEvent
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerBlock(new IClientBlockExtensions() {
            @Override
            public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
                return true;
            }
        }, ModBlocks.STRUT.get());
    }
}
