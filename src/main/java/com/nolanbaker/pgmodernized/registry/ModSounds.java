package com.nolanbaker.pgmodernized.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.nolanbaker.pgmodernized.PowerGridModernized.MOD_ID;
import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** The mod's own sound events; the files behind them are vanilla's, picked and pitched in sounds.json. */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);

    /** One pulse of a fire alarm horn; the horn strobe plays it in the three-pulse pattern. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_ALARM_HORN =
            REGISTER.register("fire_alarm_horn", () -> SoundEvent.createVariableRangeEvent(asResource("fire_alarm_horn")));
}
