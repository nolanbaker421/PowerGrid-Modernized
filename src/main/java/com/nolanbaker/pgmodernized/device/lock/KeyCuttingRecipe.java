package com.nolanbaker.pgmodernized.device.lock;

import com.nolanbaker.pgmodernized.registry.ModItems;
import com.nolanbaker.pgmodernized.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;

/**
 * Cutting a key: a numbered lock or key and one iron nugget anywhere in the grid give a key of
 * that number, and the lock or key stays in the grid. A lock from the creative menu has no number
 * until it is fitted, so it cuts nothing.
 */
public class KeyCuttingRecipe extends CustomRecipe {
    public KeyCuttingRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** The numbered lock or key in the grid, when there is exactly one of those and exactly one nugget and nothing else. */
    private static ItemStack pattern(CraftingInput input) {
        ItemStack pattern = ItemStack.EMPTY;
        int nuggets = 0;
        for(int i = 0; i < input.size(); ++i) {
            var stack = input.getItem(i);
            if(stack.isEmpty())
                continue;
            if((stack.getItem() instanceof LockItem || stack.getItem() instanceof KeyItem) && !LockItem.serial(stack).isEmpty()) {
                if(!pattern.isEmpty())
                    return ItemStack.EMPTY;
                pattern = stack;
            } else if(stack.is(Tags.Items.NUGGETS_IRON)) {
                ++nuggets;
            } else {
                return ItemStack.EMPTY;
            }
        }
        return nuggets == 1 ? pattern : ItemStack.EMPTY;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !pattern(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        var pattern = pattern(input);
        if(pattern.isEmpty())
            return ItemStack.EMPTY;
        var key = ModItems.key();
        LockItem.setSerial(key, LockItem.serial(pattern));
        return key;
    }

    /** The lock or key is a pattern, not an ingredient: it stays in the grid. */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        var out = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for(int i = 0; i < input.size(); ++i) {
            var stack = input.getItem(i);
            if(stack.getItem() instanceof LockItem || stack.getItem() instanceof KeyItem)
                out.set(i, stack.copyWithCount(1));
        }
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.KEY_CUTTING.get();
    }
}
