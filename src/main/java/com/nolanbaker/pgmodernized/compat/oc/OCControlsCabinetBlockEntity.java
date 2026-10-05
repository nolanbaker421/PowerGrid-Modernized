package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity;
import com.nolanbaker.pgmodernized.device.controls.PanelDevice;
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

import java.util.HashMap;
import java.util.Map;

import static com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport.result;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.CELLS;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.RAIL;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlock.RELAY_CHANNELS;
import static com.nolanbaker.pgmodernized.device.controls.ControlsCabinetBlockEntity.CHANNELS;

/**
 * Controls cabinet as OpenComputers component "powergrid_controls". Slots, channels and cells
 * are numbered from 1. Signals: 'input_change' (slot, channel, state), 'estop' (active),
 * 'device' (cell, type, state) when someone works a button or switch on the door.
 */
public class OCControlsCabinetBlockEntity extends ControlsCabinetBlockEntity implements Environment, ControlsCabinetBlockEntity.Listener {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_controls");

    public OCControlsCabinetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        addListener(this);
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

    // ---- signals ----

    @Override
    public void input(int slot, int channel, boolean state) {
        if(ocNode.network() != null)
            ocNode.sendToReachable("computer.signal", "input_change", slot + 1, channel + 1, state);
    }

    @Override
    public void estop(boolean active) {
        if(ocNode.network() != null)
            ocNode.sendToReachable("computer.signal", "estop", active);
    }

    @Override
    public void device(int cell, PanelDevice type, int state) {
        if(ocNode.network() != null)
            ocNode.sendToReachable("computer.signal", "device", cell + 1, type.key(), state);
    }

    // ---- callbacks ----

    @Callback(direct = true, doc = "function():boolean -- Whether the control bus is live: a power supply module with at least 50 V on line and neutral.")
    public Object[] isPowered(Context context, Arguments args) {
        return result(isPowered());
    }

    @Callback(direct = true, doc = "function():number -- Volts between line and neutral.")
    public Object[] getVoltage(Context context, Arguments args) {
        return result((double) volts());
    }

    @Callback(direct = true, doc = "function():boolean -- Whether any E-stop on the door is pressed.")
    public Object[] isEStopped(Context context, Arguments args) {
        return result(isEStopped());
    }

    @Callback(direct = true, doc = "function():table -- Module in each rail slot, 1 to 6: power_supply, digital_in, digital_out, relay, or nil.")
    public Object[] getModules(Context context, Arguments args) {
        Map<Integer, String> map = new HashMap<>();
        for(int slot = 0; slot < RAIL; ++slot)
            if(module(slot) != null)
                map.put(slot + 1, module(slot).key());
        return result(map);
    }

    @Callback(direct = true, doc = "function():table -- Device in each door cell, 1 to 6 (top left to bottom right): e_stop, toggle, momentary, selector, led, display, or nil.")
    public Object[] getDevices(Context context, Arguments args) {
        Map<Integer, String> map = new HashMap<>();
        for(int cell = 0; cell < CELLS; ++cell)
            if(device(cell) != null)
                map.put(cell + 1, device(cell).key());
        return result(map);
    }

    @Callback(direct = true, doc = "function(slot:number, channel:number):boolean -- An input module's channel.")
    public Object[] getInput(Context context, Arguments args) {
        return result(input(args.checkInteger(0) - 1, args.checkInteger(1) - 1));
    }

    @Callback(direct = true, doc = "function(slot:number):table -- All eight inputs of a module.")
    public Object[] getInputs(Context context, Arguments args) {
        int slot = args.checkInteger(0) - 1;
        Map<Integer, Boolean> map = new HashMap<>();
        for(int ch = 0; ch < CHANNELS; ++ch)
            map.put(ch + 1, input(slot, ch));
        return result(map);
    }

    @Callback(doc = "function(slot:number, channel:number, on:boolean):boolean -- Set an output module's channel; false if that slot holds no output module.")
    public Object[] setOutput(Context context, Arguments args) {
        return result(setOutput(args.checkInteger(0) - 1, args.checkInteger(1) - 1, args.checkBoolean(2)));
    }

    @Callback(direct = true, doc = "function(slot:number, channel:number):boolean -- An output module's channel.")
    public Object[] getOutput(Context context, Arguments args) {
        return result(output(args.checkInteger(0) - 1, args.checkInteger(1) - 1));
    }

    @Callback(doc = "function(slot:number, channel:number, closed:boolean):boolean -- Close or open a relay module's contact (channels 1 and 2); false if that slot holds no relay module.")
    public Object[] setRelay(Context context, Arguments args) {
        return result(setRelay(args.checkInteger(0) - 1, args.checkInteger(1) - 1, args.checkBoolean(2)));
    }

    @Callback(direct = true, doc = "function(slot:number, channel:number):boolean -- Whether a relay contact is commanded closed.")
    public Object[] getRelay(Context context, Arguments args) {
        return result(relay(args.checkInteger(0) - 1, args.checkInteger(1) - 1));
    }

    @Callback(doc = "function(cell:number, value:number):boolean -- Write a number display on the door, -999 to 9999; false if that cell holds no display.")
    public Object[] setDisplay(Context context, Arguments args) {
        return result(setDisplay(args.checkInteger(0) - 1, args.checkInteger(1)));
    }

    @Callback(direct = true, doc = "function(cell:number):number -- What the device in a cell is doing: 0 or 1 for buttons and lights, 0 to 2 for a selector (left, centre, right), the value of a display.")
    public Object[] getDeviceState(Context context, Arguments args) {
        int cell = args.checkInteger(0) - 1;
        return result(cell >= 0 && cell < CELLS ? deviceState(cell) : 0);
    }

    @Callback(direct = true, doc = "function():number, number, number -- Rail slots, channels per input or output module, channels per relay module.")
    public Object[] getLimits(Context context, Arguments args) {
        return result(RAIL, CHANNELS, RELAY_CHANNELS);
    }
}
