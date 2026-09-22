package com.nolanbaker.pgmodernized.registry;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PowerGridModernized.MOD_ID);

    /** Serialised wire endpoint of the first end chosen while placing a Cat6 cable. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> CAT6_CONNECTION =
            REGISTER.registerComponentType("cat6_connection", builder -> builder
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    /** Serialised wire endpoint of the first end chosen while laying a conduit run. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> CONDUIT_CONNECTION =
            REGISTER.registerComponentType("conduit_connection", builder -> builder
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG));
}
