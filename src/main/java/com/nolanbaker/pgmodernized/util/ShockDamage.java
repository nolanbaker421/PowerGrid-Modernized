package com.nolanbaker.pgmodernized.util;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;

/**
 * Working on live wiring hurts. Splicing a live terminal or pulling live wire shocks the player
 * with the mod's own damage type, whose death message reminds them about lockout tagout.
 */
public final class ShockDamage {
    public static final ResourceKey<DamageType> LOCKOUT = ResourceKey.create(Registries.DAMAGE_TYPE, PowerGridModernized.asResource("lockout"));

    /** Below this the circuit counts as extra-low voltage and is safe to touch. */
    public static final float SAFE_VOLTS = 50f;
    /** Below this a wire being cut or pulled is not carrying enough to matter. */
    public static final float SAFE_AMPS = 1f;

    private ShockDamage() {}

    public static DamageSource source(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(LOCKOUT));
    }

    /** Highest absolute node voltage across the given terminals, or 0 if none is known. */
    public static float maxVolts(ElectricBlockEntity be, int... terminals) {
        var behaviour = be.getElectricBehaviour();
        if(behaviour == null)
            return 0;
        float max = 0;
        for(int terminal : terminals) {
            var node = behaviour.getTerminal(terminal);
            if(node == null)
                continue;
            double volts = Math.abs(node.getVoltage());
            if(Double.isFinite(volts))
                max = Math.max(max, (float) volts);
        }
        return max;
    }

    /** Shock for touching a live conductor; damage grows with voltage, from one half-heart at 50 V to ten hearts around 800 V. */
    public static boolean shockVolts(Player player, float volts) {
        if(volts < SAFE_VOLTS)
            return false;
        float amount = Math.max(1f, Math.min(20f, volts / 40f));
        return player.hurt(source(player.level()), amount);
    }

    /** Shock for cutting or pulling a wire under load; damage grows with current. */
    public static boolean shockAmps(Player player, float amps) {
        if(amps < SAFE_AMPS)
            return false;
        float amount = Math.max(1f, Math.min(20f, amps / 5f));
        return player.hurt(source(player.level()), amount);
    }
}
