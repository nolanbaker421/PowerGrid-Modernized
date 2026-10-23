package com.nolanbaker.pgmodernized.compat.cc;

import com.nolanbaker.pgmodernized.device.gear.GearCellBlockEntity;
import com.nolanbaker.pgmodernized.device.gear.GearLayout;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** A switchgear cell: what is in each of its nine slots, their state and current, and a metering unit's readings. */
public class GearCellPeripheral implements IPeripheral {
    private final GearCellBlockEntity cell;

    public GearCellPeripheral(GearCellBlockEntity cell) {
        this.cell = cell;
    }

    private int slot(int slot) throws LuaException {
        if(slot < 1 || slot > GearLayout.SLOTS)
            throw new LuaException("Slot must be between 1 and " + GearLayout.SLOTS);
        return slot - 1;
    }

    private int phase(int phase) throws LuaException {
        if(phase < 1 || phase > 3)
            throw new LuaException("Phase must be between 1 and 3");
        return phase - 1;
    }

    @LuaFunction
    public int getSlotCount() {
        return GearLayout.SLOTS;
    }

    /** One slot: {kind, label, state, closed, current, rating, and for a metering unit voltage, current and power per phase}. */
    @LuaFunction
    public Map<String, Object> getSlot(int slot) throws LuaException {
        return cell.describe(slot(slot));
    }

    /** Every slot, indexed 1 to 9; an empty slot is an empty table. */
    @LuaFunction
    public Map<Integer, Map<String, Object>> getSlots() {
        var result = new LinkedHashMap<Integer, Map<String, Object>>();
        for(int s = 0; s < GearLayout.SLOTS; ++s)
            result.put(s + 1, cell.describe(s));
        return result;
    }

    @LuaFunction
    public String getKind(int slot) throws LuaException {
        var kind = cell.slot(slot(slot)).kind();
        return kind == null ? "" : kind.id();
    }

    /** on, off, tripped, overload, closed, open, intact, blown, ok, or empty for an empty slot. */
    @LuaFunction
    public String getState(int slot) throws LuaException {
        return cell.stateName(slot(slot));
    }

    /** The hottest pole's current (A) through a switched component, or the hottest phase of a metering unit. */
    @LuaFunction
    public double getCurrent(int slot) throws LuaException {
        return cell.currentOf(slot(slot));
    }

    @LuaFunction
    public double getRating(int slot) throws LuaException {
        return cell.ratingOf(slot(slot));
    }

    /** A metering unit's RMS voltage (V) of that phase to the neutral. */
    @LuaFunction
    public double getVoltage(int slot, int phase) throws LuaException {
        return cell.slot(slot(slot)).volts(phase(phase));
    }

    /** A metering unit's RMS current (A) on that phase. */
    @LuaFunction
    public double getPhaseCurrent(int slot, int phase) throws LuaException {
        return cell.slot(slot(slot)).amps(phase(phase));
    }

    /** A metering unit's real power (W) on that phase. */
    @LuaFunction
    public double getPower(int slot, int phase) throws LuaException {
        return cell.slot(slot(slot)).watts(phase(phase));
    }

    /** Whether the cell has a redstone signal: the coil of its contactors and starters. */
    @LuaFunction
    public boolean isPowered() {
        return cell.powered();
    }

    @Override
    public @NotNull String getType() {
        return "powergrid_gear_cell";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof GearCellPeripheral that && this.cell == that.cell;
    }

    @Override
    public int hashCode() {
        return cell.hashCode();
    }
}
