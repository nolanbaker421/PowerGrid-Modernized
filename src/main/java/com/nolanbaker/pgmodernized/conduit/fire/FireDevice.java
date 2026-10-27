package com.nolanbaker.pgmodernized.conduit.fire;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/**
 * One device as the panel knows it: where it is, which slot, its address and kind, the name it
 * was given, its state at the last look, and when it was last reached along the conduit.
 */
public record FireDevice(BlockPos pos, int slot, int address, FireDeviceKind kind, String label, Status status, long lastSeen) {
    public enum Status {
        NORMAL, ALARM, MISSING, NOT_SPLICED, STROBING, SOUNDING;

        /** Something the panel should show as trouble. */
        public boolean trouble() {
            return this == MISSING || this == NOT_SPLICED;
        }
    }

    public FireDevice with(Status status, long seen) {
        return new FireDevice(pos, slot, address, kind, label, status, seen);
    }

    public FireDevice withAddress(int address) {
        return new FireDevice(pos, slot, address, kind, label, status, lastSeen);
    }

    /** The display's line for the device: address, kind and name, the way a panel prints it. */
    public String display() {
        var sb = new StringBuilder(String.format("%03d %s", address, kind.display()));
        if(!label.isEmpty())
            sb.append(' ').append(label.toUpperCase());
        return sb.toString();
    }

    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.put("Pos", NbtUtils.writeBlockPos(pos));
        tag.putInt("Slot", slot);
        tag.putInt("Address", address);
        tag.putString("Kind", kind.id());
        tag.putString("Label", label);
        tag.putInt("Status", status.ordinal());
        tag.putLong("Seen", lastSeen);
        return tag;
    }

    public static FireDevice read(CompoundTag tag) {
        var pos = NbtUtils.readBlockPos(tag, "Pos").orElse(BlockPos.ZERO);
        int s = tag.getInt("Status");
        var status = s >= 0 && s < Status.values().length ? Status.values()[s] : Status.NORMAL;
        return new FireDevice(pos, tag.getInt("Slot"), tag.getInt("Address"), FireDeviceKind.byId(tag.getString("Kind")), tag.getString("Label"), status, tag.getLong("Seen"));
    }
}
