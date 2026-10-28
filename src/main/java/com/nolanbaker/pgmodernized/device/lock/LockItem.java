package com.nolanbaker.pgmodernized.device.lock;

import com.nolanbaker.pgmodernized.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.patryk3211.powergrid.utility.Lang;

import java.util.List;

/**
 * A lock with a serial number, stamped when the lock is crafted, or when it is first fitted if
 * it came from the creative menu. A padlock keeps its number for good; a utility lock is a seal
 * and takes a new number every time it is fitted, the item showing the number it last had.
 */
public class LockItem extends Item {
    private final LockKind kind;

    public LockItem(Properties properties, LockKind kind) {
        super(properties);
        this.kind = kind;
    }

    public LockKind kind() {
        return kind;
    }

    /** The serial stamped on the lock, or an empty string. */
    public static String serial(ItemStack stack) {
        var tag = stack.get(ModDataComponents.LOCK.get());
        return tag == null ? "" : tag.getString("Serial");
    }

    public static void setSerial(ItemStack stack, String serial) {
        var tag = new CompoundTag();
        tag.putString("Serial", serial);
        stack.set(ModDataComponents.LOCK.get(), tag);
    }

    /** The serial on the lock, stamping one first if it has none. */
    public static String stamp(ItemStack stack, Level level) {
        var serial = serial(stack);
        if(serial.isEmpty() && stack.getItem() instanceof LockItem lock) {
            serial = lock.kind.newSerial(level.getRandom());
            setSerial(stack, serial);
        }
        return serial;
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        if(!level.isClientSide)
            stamp(stack, level);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        var serial = serial(stack);
        if(serial.isEmpty())
            tooltip.add(Lang.builder().translate("lock.unnumbered").style(ChatFormatting.DARK_GRAY).component());
        else
            tooltip.add(Lang.builder().translate("lock.serial", serial).style(ChatFormatting.AQUA).component());
        tooltip.add(Lang.builder().translate("lock.tooltip." + kind.id()).style(ChatFormatting.GRAY).component());
    }
}
