package com.nolanbaker.pgmodernized.ac.compat.oc;

import com.nolanbaker.pgmodernized.ac.source.FeInverterBlockEntity;
import com.nolanbaker.pgmodernized.compat.oc.OCNodeSupport;
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

/** FE inverter that plugs straight into OpenComputers cables as component "powergrid_inverter". */
public class OCFeInverterBlockEntity extends FeInverterBlockEntity implements Environment {
    private final Node ocNode = OCNodeSupport.create(this, "powergrid_inverter");

    public OCFeInverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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

    @Callback(doc = "function(volts:number) -- Line-to-neutral output voltage; snaps to the nearest nameplate value.")
    public Object[] setVoltage(Context context, Arguments args) {
        setVolts(args.checkDouble(0));
        return result((double) volts());
    }

    @Callback(direct = true, doc = "function():number -- Set line-to-neutral voltage (V).")
    public Object[] getVoltage(Context context, Arguments args) {
        return result((double) volts());
    }

    @Callback(doc = "function(hz:number) -- Output frequency in hertz.")
    public Object[] setFrequency(Context context, Arguments args) {
        setHertz(args.checkDouble(0));
        return result((double) hertz());
    }

    @Callback(direct = true, doc = "function():number -- Output frequency (Hz).")
    public Object[] getFrequency(Context context, Arguments args) {
        return result((double) hertz());
    }

    @Callback(direct = true, doc = "function():number -- Real power being delivered on the lines (W).")
    public Object[] getPower(Context context, Arguments args) {
        return result((double) watts());
    }

    @Callback(direct = true, doc = "function():number -- FE in the buffer.")
    public Object[] getStored(Context context, Arguments args) {
        return result(stored());
    }

    @Callback(direct = true, doc = "function():number -- Size of the FE buffer.")
    public Object[] getCapacity(Context context, Arguments args) {
        return result(capacity());
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the lines are dead because the buffer ran out.")
    public Object[] isBrownedOut(Context context, Arguments args) {
        return result(brownedOut());
    }
}
