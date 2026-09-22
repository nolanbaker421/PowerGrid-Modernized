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
import com.nolanbaker.pgmodernized.device.vfd.VfdBlockEntity;

import static com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport.result;

/** Variable frequency drive that plugs straight into OpenComputers cables as component "powergrid_vfd". */
public class OCVfdBlockEntity extends VfdBlockEntity implements Environment {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_vfd");

    public OCVfdBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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

    @Callback(doc = "function(volts:number) -- Set the output voltage, -2000 to +2000 V (negative reverses polarity).")
    public Object[] setVoltage(Context context, Arguments args) {
        setVoltage((float) args.checkDouble(0));
        return result();
    }

    @Callback(direct = true, doc = "function():number -- Commanded output voltage.")
    public Object[] getVoltage(Context context, Arguments args) {
        return result((double) getVoltage());
    }

    @Callback(doc = "function(amps:number) -- Set the output current limit, 0 to 3 A.")
    public Object[] setCurrentLimit(Context context, Arguments args) {
        setCurrentLimit((float) args.checkDouble(0));
        return result();
    }

    @Callback(direct = true, doc = "function():number -- Output current limit.")
    public Object[] getCurrentLimit(Context context, Arguments args) {
        return result((double) getCurrentLimit());
    }

    @Callback(doc = "function(enabled:boolean) -- Enable or disable the output.")
    public Object[] setEnabled(Context context, Arguments args) {
        setEnabled(args.checkBoolean(0));
        return result();
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the output is enabled.")
    public Object[] isEnabled(Context context, Arguments args) {
        return result(isEnabled());
    }

    @Callback(direct = true, doc = "function():number -- Measured output voltage (V), signed.")
    public Object[] getOutputVoltage(Context context, Arguments args) {
        return result((double) getOutputVoltage());
    }

    @Callback(direct = true, doc = "function():number -- Measured output current (A).")
    public Object[] getOutputCurrent(Context context, Arguments args) {
        return result((double) getOutputCurrent());
    }

    @Callback(direct = true, doc = "function():number -- Measured input voltage (V).")
    public Object[] getInputVoltage(Context context, Arguments args) {
        return result((double) getInputVoltage());
    }

    @Callback(direct = true, doc = "function():number -- Current drawn from the input (A).")
    public Object[] getInputCurrent(Context context, Arguments args) {
        return result((double) getInputCurrent());
    }

    @Callback(direct = true, doc = "function():number -- Output power (W).")
    public Object[] getPower(Context context, Arguments args) {
        return result((double) getPower());
    }

    @Callback(direct = true, doc = "function():number, number -- Maximum output voltage and current.")
    public Object[] getLimits(Context context, Arguments args) {
        return result((double) MAX_VOLTAGE, (double) MAX_CURRENT);
    }
}
