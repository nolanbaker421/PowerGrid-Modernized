package com.nolanbaker.pgmodernized.compat.oc;

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
import com.nolanbaker.pgmodernized.device.analogio.AnalogIOBlock;
import com.nolanbaker.pgmodernized.device.analogio.AnalogIOBlockEntity;

import static com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport.result;

/** Analog I/O module that plugs straight into OpenComputers cables as component "powergrid_analog_io". */
public class OCAnalogIOBlockEntity extends AnalogIOBlockEntity implements Environment {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_analog_io");
    private final float[] reportedInputs = new float[AnalogIOBlock.CHANNELS];
    private float changeThreshold = 0.1f;

    public OCAnalogIOBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        OCNodeSupport.tick(this, ocNode);
        if(level == null || level.isClientSide || changeThreshold <= 0 || ocNode.network() == null)
            return;
        for(int i = 0; i < AnalogIOBlock.CHANNELS; ++i) {
            float value = getInput(i);
            if(Math.abs(value - reportedInputs[i]) >= changeThreshold) {
                reportedInputs[i] = value;
                ocNode.sendToReachable("computer.signal", "analog_change", i + 1, (double) value);
            }
        }
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
        if(!clientPacket && tag.contains("ChangeThreshold"))
            changeThreshold = tag.getFloat("ChangeThreshold");
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        OCNodeSupport.save(tag, registries, ocNode, clientPacket);
        if(!clientPacket)
            tag.putFloat("ChangeThreshold", changeThreshold);
    }

    // --- OpenComputers Environment ---

    @Override
    public Node node() {
        return ocNode;
    }

    @Override
    public void onConnect(Node node) {}

    @Override
    public void onDisconnect(Node node) {}

    @Override
    public void onMessage(Message message) {}

    // --- Lua API ---

    private static int channel(Arguments args, int index) {
        int channel = args.checkInteger(index);
        if(channel < 1 || channel > AnalogIOBlock.CHANNELS)
            throw new IllegalArgumentException("channel must be between 1 and " + AnalogIOBlock.CHANNELS);
        return channel - 1;
    }

    @Callback(doc = "function(channel:number, volts:number) -- Set an output channel (1-4) to a voltage between -24 and +24 V.")
    public Object[] setOutput(Context context, Arguments args) {
        setOutput(channel(args, 0), (float) args.checkDouble(1));
        return result();
    }

    @Callback(direct = true, doc = "function(channel:number):number -- Commanded voltage of an output channel.")
    public Object[] getOutput(Context context, Arguments args) {
        return result((double) getOutput(channel(args, 0)));
    }

    @Callback(direct = true, doc = "function(channel:number):number -- Actual voltage applied on an output channel (lower than commanded if current-limited).")
    public Object[] getAppliedOutput(Context context, Arguments args) {
        return result((double) getAppliedOutput(channel(args, 0)));
    }

    @Callback(direct = true, doc = "function(channel:number):number -- Current (A) flowing out of an output channel.")
    public Object[] getOutputCurrent(Context context, Arguments args) {
        return result((double) getOutputCurrent(channel(args, 0)));
    }

    @Callback(direct = true, doc = "function():table -- Commanded voltages of all output channels.")
    public Object[] getOutputs(Context context, Arguments args) {
        var values = new Double[AnalogIOBlock.CHANNELS];
        for(int i = 0; i < values.length; ++i)
            values[i] = (double) getOutput(i);
        return result((Object) values);
    }

    @Callback(direct = true, doc = "function(channel:number):number -- Voltage (V) sensed on an input channel (1-4), relative to the common terminal.")
    public Object[] getInput(Context context, Arguments args) {
        return result((double) getInput(channel(args, 0)));
    }

    @Callback(direct = true, doc = "function():table -- Voltages of all input channels.")
    public Object[] getInputs(Context context, Arguments args) {
        var values = new Double[AnalogIOBlock.CHANNELS];
        for(int i = 0; i < values.length; ++i)
            values[i] = (double) getInput(i);
        return result((Object) values);
    }

    @Callback(direct = true, doc = "function():number -- Maximum output voltage magnitude.")
    public Object[] getRange(Context context, Arguments args) {
        return result((double) MAX_VOLTAGE);
    }

    @Callback(direct = true, doc = "function():number -- Number of channels.")
    public Object[] getChannels(Context context, Arguments args) {
        return result(AnalogIOBlock.CHANNELS);
    }

    @Callback(doc = "function(volts:number) -- Minimum input change that raises an 'analog_change' signal; 0 disables signals.")
    public Object[] setChangeThreshold(Context context, Arguments args) {
        changeThreshold = (float) Math.max(0, args.checkDouble(0));
        setChanged();
        return result();
    }
}
