package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.device.gear.GearCellBlockEntity;
import com.nolanbaker.pgmodernized.device.gear.GearLayout;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/** Switchgear cell that plugs straight into OpenComputers cables as component "powergrid_gear_cell". */
public class OCGearCellBlockEntity extends GearCellBlockEntity implements Environment {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_gear_cell");

    public OCGearCellBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        OCNodeSupport.remove(ocNode);
    }

    @Override
    public void remove() {
        super.remove();
        OCNodeSupport.remove(ocNode);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        OCNodeSupport.load(tag, registries, ocNode, clientPacket);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        OCNodeSupport.save(tag, registries, ocNode, clientPacket);
    }

    @Override public Node node() { return ocNode; }
    @Override public void onConnect(Node node) {}
    @Override public void onDisconnect(Node node) {}
    @Override public void onMessage(Message message) {}

    private static int slotArg(Arguments args) {
        int slot = args.checkInteger(0);
        if(slot < 1 || slot > GearLayout.SLOTS)
            throw new IllegalArgumentException("slot must be between 1 and " + GearLayout.SLOTS);
        return slot - 1;
    }

    private static int phaseArg(Arguments args) {
        int phase = args.checkInteger(1);
        if(phase < 1 || phase > 3)
            throw new IllegalArgumentException("phase must be between 1 and 3");
        return phase - 1;
    }

    private static Object[] result(Object... values) {
        return values;
    }

    @Callback(direct = true, doc = "function():number -- Number of slots, 9.")
    public Object[] getSlotCount(Context context, Arguments args) {
        return result(GearLayout.SLOTS);
    }

    @Callback(direct = true, doc = "function(slot:number):table -- One slot: {kind, label, state, closed, current, rating, voltage, current and power per phase for a metering unit}.")
    public Object[] getSlot(Context context, Arguments args) {
        return result(describe(slotArg(args)));
    }

    @Callback(direct = true, doc = "function():table -- Every slot, indexed 1 to 9; an empty slot is an empty table.")
    public Object[] getSlots(Context context, Arguments args) {
        var table = new LinkedHashMap<Integer, Map<String, Object>>();
        for(int s = 0; s < GearLayout.SLOTS; ++s)
            table.put(s + 1, describe(s));
        return result(table);
    }

    @Callback(direct = true, doc = "function(slot:number):string -- What is in the slot: bus, neutral_bar, ground_bar, lugs, breaker, fuse, switch, contactor, starter, meter, or an empty string.")
    public Object[] getKind(Context context, Arguments args) {
        var kind = slot(slotArg(args)).kind();
        return result(kind == null ? "" : kind.id());
    }

    @Callback(direct = true, doc = "function(slot:number):string -- on, off, tripped, overload, closed, open, intact, blown, ok, or empty.")
    public Object[] getState(Context context, Arguments args) {
        return result(stateName(slotArg(args)));
    }

    @Callback(direct = true, doc = "function(slot:number):number -- Current (A) through the hottest pole or phase.")
    public Object[] getCurrent(Context context, Arguments args) {
        return result((double) currentOf(slotArg(args)));
    }

    @Callback(direct = true, doc = "function(slot:number):number -- Rating (A) of a breaker, starter or fuse set; 0 otherwise.")
    public Object[] getRating(Context context, Arguments args) {
        return result((double) ratingOf(slotArg(args)));
    }

    @Callback(direct = true, doc = "function(slot:number, phase:number):number -- A metering unit's RMS voltage (V) of that phase to the neutral.")
    public Object[] getVoltage(Context context, Arguments args) {
        return result((double) slot(slotArg(args)).volts(phaseArg(args)));
    }

    @Callback(direct = true, doc = "function(slot:number, phase:number):number -- A metering unit's RMS current (A) on that phase.")
    public Object[] getPhaseCurrent(Context context, Arguments args) {
        return result((double) slot(slotArg(args)).amps(phaseArg(args)));
    }

    @Callback(direct = true, doc = "function(slot:number, phase:number):number -- A metering unit's real power (W) on that phase.")
    public Object[] getPower(Context context, Arguments args) {
        return result((double) slot(slotArg(args)).watts(phaseArg(args)));
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the cell has a redstone signal: the coil of its contactors and starters.")
    public Object[] isPowered(Context context, Arguments args) {
        return result(powered());
    }
}
