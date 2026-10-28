package com.nolanbaker.pgmodernized.device.lock;

import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.utility.Lang;

import java.util.UUID;

/**
 * A lock fitted to something: which kind, its serial, and who fitted it. It holds against
 * everyone, the one who fitted it included. A padlock comes off with any key and keeps its
 * number. A utility lock is a seal: it takes a new number every time it goes on, anyone breaks it
 * by hand, and what it sealed remembers the number and who broke it.
 */
public record Lock(LockKind kind, String serial, UUID owner, String ownerName) {

    /**
     * The lock in the player's hand goes on, remembering who fitted it. A padlock keeps its number,
     * taking one if it has none; a utility seal takes a new number every time, so no seal is ever
     * the number of the one before it.
     */
    public static Lock fit(ItemStack stack, Player player, Level level) {
        var kind = stack.getItem() instanceof LockItem item ? item.kind() : LockKind.PADLOCK;
        var serial = kind == LockKind.UTILITY ? kind.newSerial(level.getRandom()) : LockItem.stamp(stack, level);
        return new Lock(kind, serial, player.getUUID(), player.getGameProfile().getName());
    }

    /** Whether that takes this lock off: any key, on a padlock. A seal is broken by hand. */
    public boolean opensWith(ItemStack stack) {
        return kind == LockKind.PADLOCK && stack.getItem() instanceof KeyItem;
    }

    /** The lock back as an item, numbered as before. */
    public ItemStack item() {
        var stack = ModItems.lock(kind);
        LockItem.setSerial(stack, serial);
        return stack;
    }

    /** What the lock says to a click, in red, for the action bar: the number, who fitted it, and what takes it off. */
    public Component held() {
        return Lang.builder().translate("message.lock.held_" + kind.id(), serial, ownerName).style(ChatFormatting.RED).component();
    }

    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putString("Kind", kind.id());
        tag.putString("Serial", serial);
        tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        return tag;
    }

    @Nullable
    public static Lock read(CompoundTag tag) {
        if(tag == null || !tag.contains("Serial") || !tag.hasUUID("Owner"))
            return null;
        return new Lock(LockKind.byId(tag.getString("Kind")), tag.getString("Serial"), tag.getUUID("Owner"), tag.getString("OwnerName"));
    }
}
