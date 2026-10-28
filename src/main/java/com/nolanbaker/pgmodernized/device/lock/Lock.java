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
 * everyone, the one who fitted it included; only a key cut to its number takes it off. Taken off,
 * it is the same numbered lock again as an item.
 */
public record Lock(LockKind kind, String serial, UUID owner, String ownerName) {

    /** The lock in the player's hand goes on: it takes a serial if it has none, and remembers who fitted it. */
    public static Lock fit(ItemStack stack, Player player, Level level) {
        var kind = stack.getItem() instanceof LockItem item ? item.kind() : LockKind.PADLOCK;
        return new Lock(kind, LockItem.stamp(stack, level), player.getUUID(), player.getGameProfile().getName());
    }

    /** Whether that is a key cut to this lock's number. */
    public boolean opensWith(ItemStack stack) {
        return stack.getItem() instanceof KeyItem && LockItem.serial(stack).equals(serial);
    }

    /** The lock back as an item, numbered as before. */
    public ItemStack item() {
        var stack = ModItems.lock(kind);
        LockItem.setSerial(stack, serial);
        return stack;
    }

    /** "Locked: PL-0000000, fitted by Nolan; its key takes it off" in red, for the action bar. */
    public Component held() {
        return Lang.builder().translate("message.lock.held", serial, ownerName).style(ChatFormatting.RED).component();
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
