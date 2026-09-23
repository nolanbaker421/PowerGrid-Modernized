package com.nolanbaker.pgmodernized.compat.oc;

import com.nolanbaker.pgmodernized.device.drive.ThreePhaseDriveBlockEntity;
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

import static com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport.result;

/** Three-phase drive that plugs straight into OpenComputers cables as component "powergrid_three_phase_drive". */
public class OCThreePhaseDriveBlockEntity extends ThreePhaseDriveBlockEntity implements Environment {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_three_phase_drive");

    public OCThreePhaseDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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

    @Callback(doc = "function(hz:number) -- Commanded output frequency; a negative value hands control back to the value box.")
    public Object[] setFrequency(Context context, Arguments args) {
        setFrequency((float) args.checkDouble(0));
        return result();
    }

    @Callback(direct = true, doc = "function():number -- Commanded frequency (Hz).")
    public Object[] getFrequency(Context context, Arguments args) {
        return result((double) frequencySetpoint());
    }

    @Callback(direct = true, doc = "function():number -- Frequency the output is running at (Hz), following the ramp.")
    public Object[] getOutputFrequency(Context context, Arguments args) {
        return result((double) outputFrequency());
    }

    @Callback(doc = "function(volts:number) -- Rated output voltage (RMS per phase), reached at the rated frequency.")
    public Object[] setRatedVoltage(Context context, Arguments args) {
        setRatedVoltage((float) args.checkDouble(0));
        return result();
    }

    @Callback(direct = true, doc = "function():number -- Rated output voltage (V).")
    public Object[] getRatedVoltage(Context context, Arguments args) {
        return result((double) ratedVoltage());
    }

    @Callback(doc = "function(hz:number) -- Rated frequency (Hz): the volts-per-hertz base.")
    public Object[] setRatedFrequency(Context context, Arguments args) {
        setRatedFrequency((float) args.checkDouble(0));
        return result();
    }

    @Callback(direct = true, doc = "function():number -- Rated frequency (Hz).")
    public Object[] getRatedFrequency(Context context, Arguments args) {
        return result((double) ratedFrequency());
    }

    @Callback(doc = "function(hzPerSecond:number) -- Acceleration and deceleration ramp.")
    public Object[] setRampRate(Context context, Arguments args) {
        setRampRate((float) args.checkDouble(0));
        return result();
    }

    @Callback(direct = true, doc = "function():number -- Ramp rate (Hz/s).")
    public Object[] getRampRate(Context context, Arguments args) {
        return result((double) rampRate());
    }

    @Callback(doc = "function(enabled:boolean) -- Run or coast to a stop.")
    public Object[] setEnabled(Context context, Arguments args) {
        setEnabled(args.checkBoolean(0));
        return result();
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the drive is enabled.")
    public Object[] isEnabled(Context context, Arguments args) {
        return result(isEnabled());
    }

    @Callback(doc = "function(reversed:boolean) -- Swap the phase sequence, reversing a connected motor.")
    public Object[] setReversed(Context context, Arguments args) {
        setReversed(args.checkBoolean(0));
        return result();
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the phase sequence is reversed.")
    public Object[] isReversed(Context context, Arguments args) {
        return result(isReversed());
    }

    @Callback(direct = true, doc = "function():number -- Output voltage (V RMS per phase).")
    public Object[] getOutputVoltage(Context context, Arguments args) {
        return result((double) outputVoltage());
    }

    @Callback(direct = true, doc = "function():number -- Output current (A RMS per phase).")
    public Object[] getOutputCurrent(Context context, Arguments args) {
        return result((double) outputCurrent());
    }

    @Callback(direct = true, doc = "function():number -- Input line voltage (V RMS).")
    public Object[] getInputVoltage(Context context, Arguments args) {
        return result((double) inputVoltage());
    }

    @Callback(direct = true, doc = "function():number -- Input line current (A RMS).")
    public Object[] getInputCurrent(Context context, Arguments args) {
        return result((double) inputCurrent());
    }

    @Callback(direct = true, doc = "function():number -- Real power delivered (W).")
    public Object[] getPower(Context context, Arguments args) {
        return result((double) power());
    }
}
