package com.nolanbaker.pgmodernized.registry;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.device.lock.KeyCuttingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own recipe serializers: so far the one that cuts keys to a lock's number. */
public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> REGISTER = DeferredRegister.create(Registries.RECIPE_SERIALIZER, PowerGridModernized.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<KeyCuttingRecipe>> KEY_CUTTING =
            REGISTER.register("key_cutting", () -> new SimpleCraftingRecipeSerializer<>(KeyCuttingRecipe::new));
}
